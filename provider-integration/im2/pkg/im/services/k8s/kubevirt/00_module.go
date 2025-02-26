package kubevirt

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	ws "github.com/gorilla/websocket"
	"io"
	admission "k8s.io/api/admission/v1"
	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	kvcore "kubevirt.io/api/core/v1"
	kvclient "kubevirt.io/client-go/kubecli"
	kvapi "kubevirt.io/client-go/kubevirt/typed/core/v1"
	kvcdi "kubevirt.io/containerized-data-importer-api/pkg/apis/core/v1beta1"
	"net/http"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"time"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/filesystem"
	"ucloud.dk/pkg/im/services/k8s/shared"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var ServiceConfig *cfg.ServicesConfigurationKubernetes
var KubevirtClient kvclient.KubevirtClient
var Namespace string
var Enabled = false

func Init() ctrl.JobsService {
	// Create a number of aliases for use in this package. These are all static by the time this function is called.
	_ = shared.K8sClient
	ServiceConfig = shared.ServiceConfig
	KubevirtClient = shared.KubevirtClient

	Namespace = ServiceConfig.Compute.Namespace

	go vmiFsMutator()

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

			var review admission.AdmissionReview
			if err := json.Unmarshal(body, &review); err != nil {
				http.Error(w, "Could not decode request", http.StatusBadRequest)
				return
			}

			pod := corev1.Pod{}
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

			vm, err := KubevirtClient.VirtualMachine(Namespace).Get(context.Background(), name, metav1.GetOptions{})
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

			response := admission.AdmissionReview{
				TypeMeta: review.TypeMeta,
				Response: &admission.AdmissionResponse{
					UID:     review.Request.UID,
					Allowed: allowed,
					Patch:   patch,
					PatchType: func() *admission.PatchType {
						pt := admission.PatchTypeJSONPatch
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
	Bootcmd    []string        `json:"bootcmd"`
	Mounts     [][]string      `json:"mounts"`
	RunCommand []string        `json:"runcmd"`
}

func follow(session *ctrl.FollowJobSession) {
	// Keep alive to make the Core happy.
	for *session.Alive {
		time.Sleep(100 * time.Millisecond)
	}
}

func handleShell(session *ctrl.ShellSession, cols int, rows int) {
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

func terminate(request ctrl.JobTerminateRequest) error {
	name := vmName(request.Job.Id, 0)
	err := KubevirtClient.VirtualMachine(Namespace).Delete(context.TODO(), name, metav1.DeleteOptions{})
	if err != nil {
		log.Info("Failed to delete VM: %v", err)
		return util.ServerHttpError("Failed to delete VM")
	}
	return nil
}

func unsuspend(request ctrl.JobUnsuspendRequest) error {
	shared.RequestSchedule(request.Job)
	return nil
}

func suspend(request ctrl.JobSuspendRequest) error {
	name := vmName(request.Job.Id, 0)
	err := KubevirtClient.VirtualMachine(Namespace).Stop(context.TODO(), name, &kvcore.StopOptions{})
	if err != nil {
		log.Info("Failed to shutdown VM: %v", err)
		return util.ServerHttpError("Failed to shutdown VM")
	}
	return nil
}

func requestDynamicParameters(owner orc.ResourceOwner, app *orc.Application) []orc.ApplicationParameter {
	return nil
}

func openWebSession(job *orc.Job, rank int, target util.Option[string]) (ctrl.ConfiguredWebSession, error) {
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

func StartScheduledJob(job *orc.Job, rank int, node string) {
	if !Enabled {
		return
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
	cinit.Bootcmd = []string{}
	cinit.RunCommand = []string{
		fmt.Sprintf("usermod -u %d ucloud", filesystem.DefaultUid),
		fmt.Sprintf("groupmod -g %d ucloud", filesystem.DefaultUid),
	}

	machine := &job.Status.ResolvedProduct

	nameOfVm := vmName(job.Id, rank)
	existingVm, err := KubevirtClient.VirtualMachine(Namespace).Get(context.TODO(), nameOfVm, metav1.GetOptions{})
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

	image := job.Status.ResolvedApplication.Invocation.Tool.Tool.Description.Image
	baseImageSource := &kvcdi.DataVolumeSource{}
	if strings.HasPrefix(image, "http://") || strings.HasPrefix(image, "https://") {
		baseImageSource.HTTP = &kvcdi.DataVolumeSourceHTTP{
			URL: image,
		}
	} else {
		// TODO
		baseImageSource.PVC = &kvcdi.DataVolumeSourcePVC{
			Namespace: Namespace,
			Name:      image,
		}
	}

	vm.Spec.DataVolumeTemplates = []kvcore.DataVolumeTemplateSpec{
		{
			ObjectMeta: metav1.ObjectMeta{
				Name: vm.Name,
			},
			Spec: kvcdi.DataVolumeSpec{
				Source: baseImageSource,
				Storage: &kvcdi.StorageSpec{
					AccessModes: []corev1.PersistentVolumeAccessMode{corev1.ReadWriteOnce},
					Resources: corev1.ResourceRequirements{
						Requests: corev1.ResourceList{
							corev1.ResourceStorage: *resource.NewScaledQuantity(5, resource.Giga),
						},
					},
					StorageClassName: shared.ServiceConfig.Compute.VirtualMachineStorageClass.GetPtrOrNil(),
				},
			},
		},
	}

	vm.Spec.Template = &kvcore.VirtualMachineInstanceTemplateSpec{
		Spec: kvcore.VirtualMachineInstanceSpec{
			// TODO Cluster DNS doesn't work (NetworkPolicy?)
			DNSPolicy: corev1.DNSNone,
			DNSConfig: &corev1.PodDNSConfig{
				Nameservers: []string{
					"1.1.1.1",
				},
			},
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
						DataVolume: &kvcore.DataVolumeSource{
							Name: vm.Name,
						},
					},
				},
			},
		},
	}

	tplSpec := &vm.Spec.Template.Spec

	type mountEntry struct {
		volName string
		subpath string
	}
	mountsByName := map[string][]mountEntry{}

	fsIdx := 0
	for _, param := range job.Specification.Resources {
		if param.Type == orc.AppParameterValueTypeFile {
			internalPath, ok := filesystem.UCloudToInternal(param.Path)
			if !ok {
				continue
			}

			subpath, ok := strings.CutPrefix(internalPath, filepath.Clean(ServiceConfig.FileSystem.MountPoint)+"/")
			if !ok {
				continue
			}

			volName := fmt.Sprintf("ucloud-%d", fsIdx)
			tplSpec.Volumes = append(tplSpec.Volumes, kvcore.Volume{
				Name: volName,
				VolumeSource: kvcore.VolumeSource{
					PersistentVolumeClaim: &kvcore.PersistentVolumeClaimVolumeSource{
						PersistentVolumeClaimVolumeSource: corev1.PersistentVolumeClaimVolumeSource{
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
			{
				comps := util.Components(param.Path)
				compsLen := len(comps)

				if compsLen == 1 {
					drive, ok := filesystem.ResolveDrive(comps[0])
					if ok {
						title = strings.ReplaceAll(drive.Specification.Title, "Members' Files: ", "")
					}
				} else {
					title = comps[compsLen-1]
				}
			}

			bucket, _ := mountsByName[title]
			bucket = append(bucket, mountEntry{volName, subpath})
			mountsByName[title] = bucket

			fsIdx++
		}
	}

	for requestedTitle, bucket := range mountsByName {
		useSuffix := len(bucket) > 1

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

			cinit.Mounts = append(cinit.Mounts, []string{
				item.volName, fmt.Sprintf("/work/%s", title), "virtiofs", "defaults", "0", "0",
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
		_, err = KubevirtClient.VirtualMachine(Namespace).Update(context.TODO(), vm, metav1.UpdateOptions{})
	} else {
		_, err = KubevirtClient.VirtualMachine(Namespace).Create(context.TODO(), vm, metav1.CreateOptions{})
	}
	if err != nil {
		// TODO
		// TODO
		// TODO
		log.Info("Failed to create VM: %v", err)
	}
}
