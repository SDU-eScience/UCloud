package kubevirt

import (
	"context"
	"crypto/tls"
	_ "embed"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"time"

	ws "github.com/gorilla/websocket"
	k8sadmission "k8s.io/api/admission/v1"
	k8score "k8s.io/api/core/v1"
	k8serrors "k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/api/resource"
	k8smeta "k8s.io/apimachinery/pkg/apis/meta/v1"
	kvcore "kubevirt.io/api/core/v1"
	kvclient "kubevirt.io/client-go/kubecli"
	kvapi "kubevirt.io/client-go/kubevirt/typed/core/v1"
	cfg "ucloud.dk/pkg/config"
	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/integrations/k8s/shared"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var ServiceConfig *cfg.ServicesConfigurationKubernetes
var KubevirtClient kvclient.KubevirtClient
var Namespace string
var Enabled = false

//go:embed vmagent.service
var vmAgentSystemdFile []byte

func Init() ctrl.JobsService {
	// Create a number of aliases for use in this package. These are all static by the time this function is called.
	_ = shared.K8sClient
	ServiceConfig = shared.ServiceConfig
	KubevirtClient = shared.KubevirtClient

	Namespace = ServiceConfig.Compute.Namespace

	vms := &ServiceConfig.Compute.VirtualMachines
	if vms.Enabled {
		if util.DevelopmentModeEnabled() {
			Enabled = true // run in stand-alone mode
		} else {
			go vmiFsMutator()
		}

		switch vms.Storage.Type {
		case cfg.KubernetesVmVolHostPath:
			initDisks()

		case cfg.KubernetesVmVolCdi:
			log.Fatal("Not supported yet")
			panic("Not supported yet")
		}

		initAgentServer()
	}

	return ctrl.JobsService{
		Terminate:                terminate,
		Extend:                   nil,
		RetrieveProducts:         nil, // handled by main instance
		Follow:                   follow,
		HandleShell:              handleShell,
		ServerFindIngress:        nil,
		OpenWebSession:           openWebSession,
		RequestDynamicParameters: requestDynamicParameters,
		Suspend:                  suspend,
		Unsuspend:                unsuspend,
		HandleBuiltInVnc:         handleVnc,
	}
}

func VmiStandaloneMutator() {
	shared.InitClients()
	KubevirtClient = shared.KubevirtClient
	Namespace = "ucloud-apps" // TODO

	vmiFsMutator()
}

func vmiFsMutator() {
	certFile := "/etc/ucloud/webhook.crt"
	keyFile := "/etc/ucloud/webhook.key"

	cert, err := tls.LoadX509KeyPair(certFile, keyFile)
	if err != nil {
		// NOTE(Dan): The VMI FS mutator must run to actually enforce the correct subpaths. Without it, it would
		// literally mount the entire filesystem into each VM.
		log.Info("No VMI FS mutator hook will run (/etc/ucloud/webhook.crt not found) - VMs have been disabled")
		return
	}

	server := &http.Server{
		Addr: "0.0.0.0:59231",
		Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			body, err := io.ReadAll(r.Body)
			if err != nil {
				http.Error(w, "Could not read request", http.StatusInternalServerError)
				return
			}

			var review k8sadmission.AdmissionReview
			if err := json.Unmarshal(body, &review); err != nil {
				http.Error(w, "Could not decode request", http.StatusBadRequest)
				return
			}

			pod := k8score.Pod{}
			err = json.Unmarshal(review.Request.Object.Raw, &pod)
			if err != nil {
				http.Error(w, "Could not decode request", http.StatusBadRequest)
				return
			}

			type jsonPatchOp struct {
				Op    string `json:"op,omitempty"`
				Path  string `json:"path,omitempty"`
				Value any    `json:"value,omitempty"`
			}
			var ops []jsonPatchOp
			allowed := true

			labels := pod.Labels
			if labels == nil {
				labels = make(map[string]string)
			}
			name, ok := labels["vm.kubevirt.io/name"]
			if !ok {
				log.Info("Rejecting %s because no label annotation is present", pod.Name)
				allowed = false
			}

			vm, err := KubevirtClient.VirtualMachine(Namespace).Get(context.Background(), name, k8smeta.GetOptions{})
			if err != nil {
				log.Info("Rejecting %s because no VM could be found: %s", pod.Name, err)
				allowed = false
			}

			var annotations map[string]string
			if vm != nil && vm.Annotations != nil {
				annotations = vm.Annotations
			} else {
				annotations = make(map[string]string)
			}

			for cIdx, container := range pod.Spec.Containers {
				if len(container.Command) == 1 && container.Command[0] == "/usr/libexec/virtiofsd" {
					ops = append(ops, []jsonPatchOp{
						// The following are needed to ensure that the virtiofs daemon are able to read the entire filesystem (from containers).
						{Op: "add", Path: fmt.Sprintf("/spec/containers/%d/securityContext/runAsUser", cIdx), Value: 0},
						{Op: "add", Path: fmt.Sprintf("/spec/containers/%d/securityContext/runAsGroup", cIdx), Value: 0},
						{Op: "add", Path: fmt.Sprintf("/spec/containers/%d/securityContext/runAsNonRoot", cIdx), Value: false},
						{Op: "add", Path: fmt.Sprintf("/spec/containers/%d/securityContext/allowPrivilegeEscalation", cIdx), Value: true},

						// The following is needed to allow the alternative sandboxing mode that the virtiofs daemon will go into when running as root.
						{Op: "remove", Path: fmt.Sprintf("/spec/containers/%d/securityContext/capabilities/drop", cIdx)},
					}...)
				}

				for mountIdx, mount := range container.VolumeMounts {
					if strings.HasPrefix(mount.Name, "ucloud-") {
						volPath, ok1 := annotations[fmt.Sprintf("ucloud.dk/vmVolPath-%s", mount.Name)]
						readOnly, ok2 := annotations[fmt.Sprintf("ucloud.dk/vmVolReadOnly-%s", mount.Name)]
						if !ok1 && !ok2 {
							log.Info("Rejecting %s because annotations are not present on VM: %s, %s", volPath, readOnly)
							allowed = false
						} else {
							ops = append(ops, jsonPatchOp{
								Op:    "add",
								Path:  fmt.Sprintf("/spec/containers/%d/volumeMounts/%d/subPath", cIdx, mountIdx),
								Value: volPath,
							})

							ops = append(ops, jsonPatchOp{
								Op:    "add",
								Path:  fmt.Sprintf("/spec/containers/%d/volumeMounts/%d/readOnly", cIdx, mountIdx),
								Value: readOnly == "true",
							})

							// Point all volumes to the first one since the Volume is shared amongst all UCloud mounts
							ops = append(ops, jsonPatchOp{
								Op:    "add",
								Path:  fmt.Sprintf("/spec/containers/%d/volumeMounts/%d/name", cIdx, mountIdx),
								Value: "ucloud-0",
							})
						}
					}
				}
			}

			// NOTE(Dan): Element indexes are resolved just-in-time for this reason we must keep track of what the
			// index is going to be after any removals we have already done.
			// NOTE(Dan): This snippet ensures that we do not have duplicate volume definitions (which are not
			// allowed). We do this by simply keeping the first one, we have already remapped all mounts to point
			// to this volume.
			volumesRemoved := 0
			for volIdx, volume := range pod.Spec.Volumes {
				if strings.HasPrefix(volume.Name, "ucloud-") && volume.Name != "ucloud-0" {
					ops = append(ops, jsonPatchOp{
						Op:   "remove",
						Path: fmt.Sprintf("/spec/volumes/%d", volIdx-volumesRemoved),
					})

					volumesRemoved++
				}
			}

			patch, _ := json.Marshal(ops)

			response := k8sadmission.AdmissionReview{
				TypeMeta: review.TypeMeta,
				Response: &k8sadmission.AdmissionResponse{
					UID:     review.Request.UID,
					Allowed: allowed,
					Patch:   patch,
					PatchType: func() *k8sadmission.PatchType {
						pt := k8sadmission.PatchTypeJSONPatch
						return &pt
					}(),
				},
			}

			respBytes, _ := json.Marshal(response)
			_, _ = w.Write(respBytes)
		}),
		TLSConfig: &tls.Config{
			Certificates: []tls.Certificate{cert},
		},
	}

	Enabled = true
	if err := server.ListenAndServeTLS("", ""); err != nil {
		panic(err)
	}
}

func vmName(jobId string, rank int) string {
	return fmt.Sprintf("vm-%v-%v", jobId, rank)
}

func vmNameToJobIdAndRank(name string) (jobId string, rank int, ok bool) {
	if !strings.HasPrefix(name, "vm-") {
		return "", 0, false
	}

	toks := strings.Split(name, "-")
	if len(toks) != 3 {
		return "", 0, false
	}

	parsedRank, err := strconv.ParseInt(toks[2], 10, 64)
	if err != nil {
		return "", 0, false
	}

	return toks[1], int(parsedRank), true
}

type cloudInitUser struct {
	Name         string   `json:"name"`
	Uid          int      `json:"uid"`
	Sudo         []string `json:"sudo"`
	Password     string   `json:"hashed_passwd"`
	LockPassword bool     `json:"lock_passwd"`
	Shell        string   `json:"shell"`
}

type cloudInit struct {
	Users      []cloudInitUser `json:"users"`
	Mounts     [][]string      `json:"mounts"`
	RunCommand []string        `json:"runcmd"`
}

func follow(session *ctrl.FollowJobSession) {
	// Keep alive to make the Core happy.
	for *session.Alive {
		time.Sleep(100 * time.Millisecond)
	}
}

func handleShell(session *ctrl.ShellSession, _ int, _ int) {
	stdinReader, stdinWriter := io.Pipe()
	stdoutReader, stdoutWriter := io.Pipe()

	startChannel := make(chan error)

	streamStop := make(chan error)
	writeStop := make(chan error)
	readStop := make(chan error)

	go func() {
		stream, err := KubevirtClient.
			VirtualMachineInstance(Namespace).
			SerialConsole(
				vmName(session.Job.Id, session.Rank),
				&kvapi.SerialConsoleOptions{},
			)

		startChannel <- err

		if err != nil {
			return
		}

		streamStop <- stream.Stream(kvapi.StreamOptions{
			In:  stdinReader,
			Out: stdoutWriter,
		})
	}()

	err := <-startChannel
	if err != nil {
		log.Info("Failed to open serial console to VM '%v': %v", session.Job.Id, err)
		return
	}

	go func() {
		defer close(readStop)
		buf := make([]byte, 1024*4)

		for session.Alive {
			n, err := stdoutReader.Read(buf)
			if err != nil {
				break
			}

			session.EmitData(buf[:n])
		}
	}()

	go func() {
		defer close(writeStop)

		for util.IsAlive && session.Alive {
			select {
			case event := <-session.InputEvents:
				switch event.Type {
				case ctrl.ShellEventTypeInput:
					_, err = stdinWriter.Write([]byte(event.Data))
					if err != nil {
						session.Alive = false
						log.Info("Error while writing to master: %v", err)
						break
					}

				case ctrl.ShellEventTypeResize:
					// Do nothing
				}

			case _ = <-time.After(1 * time.Second):
				continue
			}
		}
	}()

	select {
	case <-streamStop:
	case <-writeStop:
	case <-readStop:
	}
}

func terminate(request ctrl.JobTerminateRequest) *util.HttpError {
	name := vmName(request.Job.Id, 0)
	err := KubevirtClient.VirtualMachine(Namespace).Delete(context.Background(), name, k8smeta.DeleteOptions{})
	if err != nil && !k8serrors.IsNotFound(err) {
		log.Info("Failed to delete VM: %v", err)
		return util.ServerHttpError("Failed to delete VM")
	}
	diskCleanup(request.Job)
	return nil
}

func unsuspend(job orc.Job) *util.HttpError {
	shared.RequestSchedule(&job)
	return nil
}

func suspend(job orc.Job) *util.HttpError {
	name := vmName(job.Id, 0)
	err := KubevirtClient.VirtualMachine(Namespace).Stop(context.Background(), name, &kvcore.StopOptions{})
	if err != nil {
		log.Info("Failed to shutdown VM: %v", err)
		return util.ServerHttpError("Failed to shutdown VM")
	}
	return nil
}

func requestDynamicParameters(_ orc.ResourceOwner, _ *orc.Application) []orc.ApplicationParameter {
	return []orc.ApplicationParameter{
		{
			Type:     orc.ApplicationParameterTypeInteger,
			Name:     vmDiskSizeParameter,
			Optional: false,
			Title:    "Disk size (GiB)",
			Description: "Size of the primary disk in the machine, used for the operating system and additional " +
				"software. You can use folders from UCloud if you need to store more data. Must be between " +
				"15GiB and 2000GiB.",
			MinValue:     15,
			MaxValue:     2000,
			Step:         1,
			UnitName:     "GiB",
			DefaultValue: json.RawMessage("50"),
		},
	}
}

func openWebSession(_ *orc.Job, _ int, _ util.Option[string]) (ctrl.ConfiguredWebSession, *util.HttpError) {
	return ctrl.ConfiguredWebSession{
		Flags: ctrl.RegisteredIngressFlagsVnc | ctrl.RegisteredIngressFlagsNoGatewayConfig,
	}, nil
}

func handleVnc(job *orc.Job, rank int, conn *ws.Conn) {
	stdinReader, stdinWriter := io.Pipe()
	stdoutReader, stdoutWriter := io.Pipe()

	startChannel := make(chan error)

	streamStop := make(chan error)
	writeStop := make(chan error)
	readStop := make(chan error)

	go func() {
		stream, err := KubevirtClient.
			VirtualMachineInstance(Namespace).
			VNC(vmName(job.Id, rank))

		startChannel <- err

		if err != nil {
			return
		}

		streamStop <- stream.Stream(kvapi.StreamOptions{
			In:  stdinReader,
			Out: stdoutWriter,
		})
	}()

	startErr := <-startChannel
	if startErr != nil {
		log.Info("Failed to open VNC connection to VM '%v': %v", job.Id, startErr)
		return
	}

	go func() {
		defer close(readStop)
		buf := make([]byte, 1024*4)

		for util.IsAlive {
			n, err := stdoutReader.Read(buf)
			if err != nil {
				break
			}

			err = conn.WriteMessage(ws.BinaryMessage, buf[:n])
			if err != nil {
				break
			}
		}
	}()

	go func() {
		defer close(writeStop)

		for util.IsAlive {
			mType, data, err := conn.ReadMessage()
			if err != nil {
				break
			}

			if mType == ws.BinaryMessage {
				_, err := stdinWriter.Write(data)
				if err != nil {
					log.Info("Failed to write: %v", err)
					break
				}
			}
		}
	}()

	select {
	case <-streamStop:
	case <-writeStop:
	case <-readStop:
	}
}

func StartScheduledJob(job *orc.Job, rank int, node string) *util.HttpError {
	if !Enabled {
		return util.HttpErr(http.StatusForbidden, "this system is not capable of running VMs at the moment")
	}

	cinit := cloudInit{}
	cinit.Users = append(cinit.Users, cloudInitUser{
		// Username: ucloud Password: ucloud
		// TODO Configure a better system for this
		Name:         "ucloud",
		Password:     "$6$rounds=4096$fzCn1bIp2KpPC3a4$FPFj6AozYQXucfCYmnLep/RS3kyNGcPWpY8MTP1zm6TyxMqgxttrYszwAAdIojC.MUZs9JI566FBQxprKraUA0",
		Uid:          filesystem.DefaultUid,
		Sudo:         []string{"ALL=(ALL) NOPASSWD:ALL"},
		LockPassword: false,
		Shell:        "/bin/bash",
	})
	cinit.RunCommand = []string{
		fmt.Sprintf("usermod -u %d ucloud", filesystem.DefaultUid),
		fmt.Sprintf("groupmod -g %d ucloud", filesystem.DefaultUid),
		"systemctl daemon-reload",
		"systemctl enable --now /etc/ucloud/vmagent.service",
	}

	machine := &job.Status.ResolvedProduct.Value

	nameOfVm := vmName(job.Id, rank)
	existingVm, err := KubevirtClient.VirtualMachine(Namespace).Get(context.Background(), nameOfVm, k8smeta.GetOptions{})
	hasExistingVm := err == nil

	vm := &kvcore.VirtualMachine{}
	vm.Annotations = make(map[string]string)
	if err != nil {
		vm.Name = vmName(job.Id, rank)
		vm.Namespace = Namespace
	} else {
		vm = existingVm
	}

	strategy := kvcore.RunStrategyAlways
	vm.Spec.RunStrategy = &strategy

	primaryDiskClaimName := nameOfVm

	app := &job.Status.ResolvedApplication.Value
	parametersAndValues := ctrl.JobFindParamAndValues(
		job,
		&app.Invocation,
		requestDynamicParameters(job.Owner, app),
	)
	diskSizeParam, ok := parametersAndValues[vmDiskSizeParameter]
	diskSize := 50
	if ok {
		intVal, ok := diskSizeParam.Value.Value.(float64)
		if ok {
			diskSize = int(intVal)
		}
	}

	diskSize = min(max(15, diskSize), 2000)

	vm.Spec.Template = &kvcore.VirtualMachineInstanceTemplateSpec{
		Spec: kvcore.VirtualMachineInstanceSpec{
			// TODO Cluster DNS didn't work on dev (previously - not tested recently. NetworkPolicy?)
			NodeSelector: map[string]string{
				"kubernetes.io/hostname": node,
			},
			Domain: kvcore.DomainSpec{
				CPU: &kvcore.CPU{
					Cores:   max(1, uint32(machine.Cpu/2)),
					Threads: max(1, uint32(machine.Cpu)),
				},
				Memory: &kvcore.Memory{
					Guest: resource.NewScaledQuantity(int64(machine.MemoryInGigs), resource.Giga),
				},
				Devices: kvcore.Devices{
					Disks: []kvcore.Disk{
						{
							Name: "base",
							DiskDevice: kvcore.DiskDevice{
								Disk: &kvcore.DiskTarget{
									Bus: kvcore.DiskBusVirtio,
								},
							},
							BootOrder: util.UintPointer(1),
						},
						{
							Name: "cloudinitdisk",
							DiskDevice: kvcore.DiskDevice{
								Disk: &kvcore.DiskTarget{
									Bus: kvcore.DiskBusVirtio,
								},
							},
							BootOrder: util.UintPointer(3),
						},
					},
					Interfaces: []kvcore.Interface{
						{
							Name: "default",
							InterfaceBindingMethod: kvcore.InterfaceBindingMethod{
								Masquerade: &kvcore.InterfaceMasquerade{},
							},
						},
					},
				},
			},
			Networks: []kvcore.Network{
				{
					Name: "default",
					NetworkSource: kvcore.NetworkSource{
						Pod: &kvcore.PodNetwork{},
					},
				},
			},
			Volumes: []kvcore.Volume{
				{
					Name: "base",
					VolumeSource: kvcore.VolumeSource{
						PersistentVolumeClaim: &kvcore.PersistentVolumeClaimVolumeSource{
							PersistentVolumeClaimVolumeSource: k8score.PersistentVolumeClaimVolumeSource{
								ClaimName: primaryDiskClaimName,
							},
						},
					},
				},
			},
		},
	}

	tplSpec := &vm.Spec.Template.Spec

	type mountEntry struct {
		volName     string
		subpath     string
		mountFolder string
		title       string
	}
	mountsByName := map[string][]mountEntry{}

	type unpreparedMount struct {
		SubPath     string
		ReadOnly    bool
		UCloudPath  string
		Title       string
		MountFolder string
	}
	var unpreparedMounts []unpreparedMount
	for _, param := range job.Specification.Resources {
		if param.Type == orc.AppParameterValueTypeFile {
			internalPath, ok, _ := filesystem.UCloudToInternal(param.Path)
			if !ok {
				continue
			}

			subpath, ok := strings.CutPrefix(internalPath, filepath.Clean(ServiceConfig.FileSystem.MountPoint)+"/")
			if !ok {
				continue
			}

			unpreparedMounts = append(unpreparedMounts, unpreparedMount{
				SubPath:    subpath,
				ReadOnly:   param.ReadOnly,
				UCloudPath: param.Path,
			})
		}
	}

	unpreparedMounts = append(unpreparedMounts, unpreparedMount{
		SubPath:     shared.ExecutablesDir,
		ReadOnly:    true,
		Title:       "ucloud",
		MountFolder: "/opt",
	})

	jobFolder, herr := JobFolder(job)
	if herr != nil {
		log.Warn("Could not find job folder: %v %s", job.Id, err)
		return util.HttpErr(http.StatusInternalServerError, "internal error")
	} else {
		confDir := filepath.Join(jobFolder, "config")
		err = os.MkdirAll(confDir, 0700)
		if err != nil {
			log.Warn("Could not create job folder: %v %s", job.Id, err)
			return util.HttpErr(http.StatusInternalServerError, "internal error")
		}

		err = os.Chown(confDir, filesystem.DefaultUid, filesystem.DefaultUid)
		if err != nil {
			log.Warn("Could not create chown folder: %v %s", job.Id, err)
			return util.HttpErr(http.StatusInternalServerError, "internal error")
		}

		agentTok := util.SecureToken()
		srvTok := util.SecureToken()
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					insert into k8s.vmagents(job_id, agent_token, srv_token)
					values (:job_id, :agent_token, :srv_token)
			    `,
				db.Params{
					"job_id":      job.Id,
					"agent_token": agentTok,
					"srv_token":   srvTok,
				},
			)
		})

		tokPath := filepath.Join(confDir, "token")
		err = os.WriteFile(tokPath, []byte(fmt.Sprintf("%s\n%s", agentTok, srvTok)), 0600)
		if err != nil {
			log.Warn("Could not create write token: %v %s", job.Id, err)
			return util.HttpErr(http.StatusInternalServerError, "internal error")
		}

		err = os.Chown(tokPath, filesystem.DefaultUid, filesystem.DefaultUid)
		if err != nil {
			log.Warn("Could not chown token: %v %s", job.Id, err)
			return util.HttpErr(http.StatusInternalServerError, "internal error")
		}

		subpath, ok := strings.CutPrefix(jobFolder, filepath.Clean(ServiceConfig.FileSystem.MountPoint)+"/")
		if !ok {
			log.Warn("sub path to folder is invalid: %v %s", job.Id, err)
			return util.HttpErr(http.StatusInternalServerError, "internal error")
		}

		systemdPath := filepath.Join(confDir, "vmagent.service")
		err = os.WriteFile(systemdPath, vmAgentSystemdFile, 0600)
		if err != nil {
			log.Warn("Could not create write systemd file: %v %s", job.Id, err)
			return util.HttpErr(http.StatusInternalServerError, "internal error")
		}

		err = os.Chown(systemdPath, filesystem.DefaultUid, filesystem.DefaultUid)
		if err != nil {
			log.Warn("Could not chown systemd file: %v %s", job.Id, err)
			return util.HttpErr(http.StatusInternalServerError, "internal error")
		}

		unpreparedMounts = append(unpreparedMounts, unpreparedMount{
			SubPath:     filepath.Join(subpath, "config"),
			ReadOnly:    true,
			Title:       "ucloud",
			MountFolder: "/etc",
		})
	}

	fsIdx := 0
	for _, param := range unpreparedMounts {
		subpath := param.SubPath

		volName := fmt.Sprintf("ucloud-%d", fsIdx)
		tplSpec.Volumes = append(tplSpec.Volumes, kvcore.Volume{
			Name: volName,
			VolumeSource: kvcore.VolumeSource{
				PersistentVolumeClaim: &kvcore.PersistentVolumeClaimVolumeSource{
					PersistentVolumeClaimVolumeSource: k8score.PersistentVolumeClaimVolumeSource{
						ClaimName: shared.ServiceConfig.FileSystem.ClaimName,
					},
				},
			},
		})

		tplSpec.Domain.Devices.Filesystems = append(tplSpec.Domain.Devices.Filesystems, kvcore.Filesystem{
			Name:     volName,
			Virtiofs: &kvcore.FilesystemVirtiofs{},
		})

		vm.Annotations[fmt.Sprintf("ucloud.dk/vmVolPath-%s", volName)] = subpath
		vm.Annotations[fmt.Sprintf("ucloud.dk/vmVolReadOnly-%s", volName)] = fmt.Sprint(param.ReadOnly)

		title := volName
		if param.UCloudPath != "" {
			comps := util.Components(param.UCloudPath)
			compsLen := len(comps)

			if compsLen == 1 {
				drive, ok := filesystem.ResolveDrive(comps[0])
				if ok {
					title = strings.ReplaceAll(drive.Specification.Title, "Members' Files: ", "")
				}
			} else {
				title = comps[compsLen-1]
			}
		} else if param.Title != "" {
			title = param.Title
		}

		bucket, _ := mountsByName[title]
		bucket = append(bucket, mountEntry{volName: volName, subpath: subpath, mountFolder: param.MountFolder, title: title})
		mountsByName[param.MountFolder+title] = bucket

		fsIdx++
	}

	for _, bucket := range mountsByName {
		useSuffix := len(bucket) > 1
		requestedTitle := bucket[0].title

		// NOTE(Dan): Must remain consistent with container mount logic for overall consistency
		slices.SortFunc(bucket, func(a, b mountEntry) int {
			return strings.Compare(a.subpath, b.subpath)
		})

		for i, item := range bucket {
			var title string
			if useSuffix {
				title = fmt.Sprintf("%s-%d", requestedTitle, i)
			} else {
				title = requestedTitle
			}

			mountPath := ""
			if bucket[0].mountFolder == "" {
				mountPath = filepath.Join("/work", title)
			} else {
				mountPath = filepath.Join(bucket[0].mountFolder, title)
			}
			cinit.Mounts = append(cinit.Mounts, []string{
				item.volName, mountPath, "virtiofs", "defaults", "0", "0",
			})
		}
	}

	cinitRawData, _ := json.Marshal(cinit)
	cinitData := "#cloud-config\n" + string(cinitRawData)

	tplSpec.Volumes = append(tplSpec.Volumes,
		kvcore.Volume{
			Name: "cloudinitdisk",
			VolumeSource: kvcore.VolumeSource{
				CloudInitNoCloud: &kvcore.CloudInitNoCloudSource{
					UserData: cinitData,
				},
			},
		},
	)

	if hasExistingVm {
		_, err = KubevirtClient.VirtualMachine(Namespace).Update(context.Background(), vm, k8smeta.UpdateOptions{})
	} else {
		vm, localErr := KubevirtClient.VirtualMachine(Namespace).Create(context.Background(), vm, k8smeta.CreateOptions{})
		err = localErr
		if err == nil {
			ownerReference := k8smeta.OwnerReference{
				APIVersion: "kubevirt.io/v1",
				Kind:       "VirtualMachine",
				Name:       vm.Name,
				UID:        vm.UID,
			}

			herr := diskPrepareForJob(job, primaryDiskClaimName, ownerReference, diskSize)
			if herr != nil {
				err = herr.AsError()
			}
		}
	}
	if err != nil {
		log.Warn("Failed to create VM: %v", err)
		return util.HttpErr(http.StatusInternalServerError, "failed to create VM")
	}
	return nil
}

const vmDiskSizeParameter = "diskSize"

func JobFolder(job *orc.Job) (string, *util.HttpError) {
	internalMemberFiles, _, herr := filesystem.InitializeMemberFiles(job.Owner.CreatedBy, job.Owner.Project)
	if herr != nil {
		return "", herr
	}

	return filepath.Join(internalMemberFiles, "Jobs", "VirtualMachines", job.Id), nil
}
