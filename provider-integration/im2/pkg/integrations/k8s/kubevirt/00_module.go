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
	"golang.org/x/sys/unix"
	"gopkg.in/yaml.v3"
	k8sadmission "k8s.io/api/admission/v1"
	k8score "k8s.io/api/core/v1"
	k8snetwork "k8s.io/api/networking/v1"
	k8serrors "k8s.io/apimachinery/pkg/api/errors"
	k8sresource "k8s.io/apimachinery/pkg/api/resource"
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

//go:embed ucloud-vmagent.service
var vmAgentSystemdFile []byte

//go:embed ucloud-metrics.service
var ucmetricsSystemdFile []byte

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
		OpenWebSession:           openWebSession,
		RequestDynamicParameters: requestDynamicParameters,
		Suspend:                  suspend,
		Unsuspend:                unsuspend,
		HandleBuiltInVnc:         handleVnc,
		AttachResource:           attachResource,
		DetachResource:           detachResource,
	}
}

func VmiStandaloneMutator() {
	shared.InitClients()
	KubevirtClient = shared.KubevirtClient
	Namespace = "ucloud-apps" // TODO
	startStandaloneMutatorUpdateWatcher(5 * time.Second)

	log.Info("Starting VMI standalone mutator")
	vmiFsMutator()
}

func startStandaloneMutatorUpdateWatcher(interval time.Duration) {
	exePath, err := os.Executable()
	if err != nil {
		log.Warn("Unable to resolve executable path: %v", err)
		return
	}

	initialModTime, err := executableModTime(exePath)
	if err != nil {
		log.Warn("Unable to read shared mutator executable timestamp (%s): %v", exePath, err)
		return
	}

	checkForUpdate := func() {
		currentModTime, err := executableModTime(exePath)
		if err != nil {
			log.Warn("Unable to read shared mutator executable timestamp (%s): %v", exePath, err)
			return
		}

		if !currentModTime.Equal(initialModTime) {
			log.Info("Standalone mutator update detected (%s). Exiting for restart.", exePath)
			os.Exit(0)
		}
	}

	checkForUpdate()

	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()

		for range ticker.C {
			checkForUpdate()
		}
	}()
}

func executableModTime(path string) (time.Time, error) {
	info, err := os.Stat(path)
	if err != nil {
		return time.Time{}, err
	}

	return info.ModTime(), nil
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
			managedVolumeNames := map[string]bool{}
			sharedVolumeName := ""

			labels := pod.Labels
			if labels == nil {
				labels = make(map[string]string)
			}
			name, ok := labels["ucloud.dk/vmName"]
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
					volPath, ok1 := annotations[fmt.Sprintf("ucloud.dk/vmVolPath-%s", mount.Name)]
					readOnly, ok2 := annotations[fmt.Sprintf("ucloud.dk/vmVolReadOnly-%s", mount.Name)]
					if !ok1 && !ok2 {
						continue
					}

					if !ok1 || !ok2 {
						log.Info("Rejecting %s because required annotations are not present on VM: hasPath=%v hasReadOnly=%v", mount.Name, ok1, ok2)
						allowed = false
						continue
					}

					if sharedVolumeName == "" {
						sharedVolumeName = mount.Name
					}
					managedVolumeNames[mount.Name] = true

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

					// Point all managed volumes to a single backing Volume since the Volume is shared amongst all UCloud mounts
					ops = append(ops, jsonPatchOp{
						Op:    "add",
						Path:  fmt.Sprintf("/spec/containers/%d/volumeMounts/%d/name", cIdx, mountIdx),
						Value: sharedVolumeName,
					})
				}
			}

			// NOTE(Dan): Element indexes are resolved just-in-time for this reason we must keep track of what the
			// index is going to be after any removals we have already done.
			// NOTE(Dan): This snippet ensures that we do not have duplicate volume definitions (which are not
			// allowed). We do this by simply keeping the first one, we have already remapped all mounts to point
			// to this volume.
			if sharedVolumeName != "" {
				volumesRemoved := 0
				for volIdx, volume := range pod.Spec.Volumes {
					if managedVolumeNames[volume.Name] && volume.Name != sharedVolumeName {
						ops = append(ops, jsonPatchOp{
							Op:   "remove",
							Path: fmt.Sprintf("/spec/volumes/%d", volIdx-volumesRemoved),
						})

						volumesRemoved++
					}
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
	Network    any             `json:"network"`
}

func follow(session *ctrl.FollowJobSession) {
	type trackedLogFile struct {
		Rank    int
		Stdout  bool
		Channel util.Option[string]
		File    *os.File
	}

	logFiles := map[string]trackedLogFile{}

	job, ok := ctrl.JobRetrieve(session.Job.Id)
	if !ok {
		return
	}

	jobFolder, err := JobFolder(job)
	if err != nil {
		return
	}

	logsFolder := filepath.Join(jobFolder, "logs")

	trackFile := func(baseName string, file trackedLogFile) {
		_, exists := logFiles[baseName]

		if !exists {
			stdout, ok1 := filesystem.OpenFile(filepath.Join(logsFolder, baseName), unix.O_RDONLY, 0)
			if ok1 {
				sinfo, err := stdout.Stat()
				if err == nil {
					if sinfo.Size() > 1024*256 {
						_, _ = stdout.Seek(sinfo.Size()-1024*256, io.SeekStart)
					}
				}
				file.File = stdout
				logFiles[baseName] = file
			}
		}
	}

	trackAllFiles := func() {
		trackFile(".ucviz-ui", trackedLogFile{
			Rank:    0,
			Stdout:  true,
			Channel: util.OptValue("ui"),
		})
	}

	utilizationChannel := make(chan []float64)
	utilizationDataTracked := false
	var utilizationData *util.FsRingReader[[]float64]
	utilSerializer := util.FsRingSerializer[[]float64]{
		Deserialize: func(buf *util.UBufferReader) []float64 {
			result := make([]float64, 64) // NOTE(Dan): change ucmetrics if changing this
			for i := 0; i < 64; i++ {
				result[i] = buf.ReadF64()
			}
			return result
		},
	}

	for util.IsAlive && *session.Alive {
		job, ok := ctrl.JobRetrieve(session.Job.Id)
		if !ok {
			break
		}

		if job.Status.State != orc.JobStateRunning {
			time.Sleep(1 * time.Second)
			continue
		}

		break
	}

	readBuffer := make([]byte, 1024*4)
	kvStatsPath := filepath.Join(logsFolder, ".ucmetrics-stats")
	var kvStatsLastMtime int64
	var kvStatsLastSize int64
	kvStatsLastContent := ""

	// Watch log files
	for util.IsAlive && *session.Alive {
		job, ok := ctrl.JobRetrieve(session.Job.Id)
		if !ok {
			break
		}

		if job.Status.State != orc.JobStateRunning {
			break
		}

		trackAllFiles()
		if !utilizationDataTracked {
			path := filepath.Join(logsFolder, ".ucviz-utilization-data")
			ring, err := util.FsRingOpen(path, utilSerializer)
			if err == nil {
				utilizationData = ring
				utilizationDataTracked = true

				go func() {
					_ = ring.Follow(context.Background(), utilizationChannel, 256)
					util.SilentClose(ring)
				}()
			}
		}

		for _, logFile := range logFiles {
			now := time.Now()
			deadline := now.Add(5 * time.Microsecond)
			_ = logFile.File.SetReadDeadline(deadline)

			bytesRead, _ := logFile.File.Read(readBuffer)
			if bytesRead > 0 {
				message := string(readBuffer[:bytesRead])
				var stdout util.Option[string]
				var stderr util.Option[string]
				if logFile.Stdout {
					stdout.Set(message)
				} else {
					stderr.Set(message)
				}

				session.EmitLogs(logFile.Rank, stdout, stderr, logFile.Channel)
			}
		}

		if finfo, err := os.Stat(kvStatsPath); err == nil {
			currentMtime := finfo.ModTime().UnixNano()
			currentSize := finfo.Size()
			if currentMtime != kvStatsLastMtime || currentSize != kvStatsLastSize {
				if f, ok := filesystem.OpenFile(kvStatsPath, unix.O_RDONLY, 0); ok {
					data, readErr := io.ReadAll(f)
					util.SilentClose(f)
					if readErr == nil {
						content := string(data)
						if content != kvStatsLastContent {
							session.EmitLogs(0, util.OptValue(content), util.OptNone[string](), util.OptValue("kv"))
							kvStatsLastContent = content
						}

						kvStatsLastMtime = currentMtime
						kvStatsLastSize = currentSize
					}
				}
			}
		} else {
			kvStatsLastMtime = 0
			kvStatsLastSize = 0
		}

		if utilizationData != nil {
		loop:
			for {
				select {
				case row := <-utilizationChannel:
					b := strings.Builder{}
					for i, elem := range row {
						if i > 0 {
							b.WriteString(",")
						}
						b.WriteString(fmt.Sprint(elem))
					}

					session.EmitLogs(0, util.OptValue(b.String()), util.OptNone[string](), util.OptValue("utilization-data"))

				default:
					break loop
				}
			}
		}

		time.Sleep(15 * time.Millisecond)
	}

	if utilizationData != nil {
		util.SilentClose(utilizationData)
	}
}

func handleShell(session *ctrl.ShellSession, cols int, rows int) {
	clearScreen := []byte("\033[2J\033[H")
	spinnerFrames := []string{"[    ]", "[=   ]", "[==  ]", "[=== ]", "[ ===]", "[  ==]", "[   =]", "[    ]"}

	session.EmitData(clearScreen)

	ttyConnChannel := make(chan *ws.Conn, 1)
	go func() {
		ttyConnChannel <- vmaRequestTty(session.Job.Id)
	}()

	ticker := time.NewTicker(120 * time.Millisecond)
	defer ticker.Stop()

	waitCount := 0
	var ttyConn *ws.Conn
waitForTty:
	for util.IsAlive && session.Alive {
		select {
		case ttyConn = <-ttyConnChannel:
			break waitForTty

		case <-ticker.C:
			frame := spinnerFrames[waitCount%len(spinnerFrames)]
			dots := strings.Repeat(".", (waitCount%3)+1)
			session.EmitData([]byte(fmt.Sprintf("\r%s Connecting to VM terminal%s", frame, dots)))
			waitCount++
		}
	}

	session.EmitData(clearScreen)

	if !util.IsAlive || !session.Alive {
		return
	}

	if ttyConn != nil {
		handleShellSessionViaAgentTty(session, ttyConn, cols, rows)
		return
	}

	session.EmitData([]byte("Using serial console fallback. You may need to press Enter before output appears.\r\n"))

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

func handleShellSessionViaAgentTty(session *ctrl.ShellSession, conn *ws.Conn, cols int, rows int) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	defer util.SilentClose(conn)

	socketMessages := make(chan []byte, 1)
	go func() {
		defer close(socketMessages)

		for {
			_, message, err := conn.ReadMessage()
			if err != nil {
				return
			}

			select {
			case socketMessages <- message:
			case <-ctx.Done():
				return
			}
		}
	}()

	sendEvent := func(ev ctrl.ShellEvent) bool {
		data, _ := json.Marshal(ev)
		return conn.WriteMessage(ws.TextMessage, data) == nil
	}

	if !sendEvent(ctrl.ShellEvent{
		Type: ctrl.ShellEventTypeInit,
		ShellEventResize: ctrl.ShellEventResize{
			Cols: cols,
			Rows: rows,
		},
	}) {
		return
	}

	ticker := time.NewTicker(1 * time.Second)
	defer ticker.Stop()

	for {
		if !util.IsAlive || !session.Alive {
			return
		}

		select {
		case <-ctx.Done():
			return

		case msg, ok := <-socketMessages:
			if !ok {
				return
			}
			session.EmitData(msg)

		case ev, ok := <-session.InputEvents:
			if !ok {
				return
			}

			if !sendEvent(ev) {
				return
			}

			if ev.Type == ctrl.ShellEventTypeTerminate {
				return
			}

		case <-ticker.C:
			// Keep loop responsive to session/aliveness changes.
		}
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

func openWebSession(job *orc.Job, sessionType orc.InteractiveSessionType, rank int, suffix util.Option[string]) (ctrl.ConfiguredWebSessionResult, *util.HttpError) {
	switch sessionType {
	case orc.InteractiveSessionTypeWeb:
		result := ctrl.ConfiguredWebSessionResult{
			Endpoints: []ctrl.ConfiguredWebEndpoint{},
		}

		for _, resource := range job.Specification.Resources {
			if resource.Type == orc.AppParameterValueTypeIngress {
				if resource.Port == 0 {
					continue
				}

				ingress := ctrl.LinkRetrieve(resource.Id)
				flags := ctrl.RegisteredIngressFlagsWeb

				address := cfg.HostInfo{
					Address: shared.JobHostName(job, rank),
					Port:    int(resource.Port),
				}

				if !shared.K8sInCluster {
					address.Address = "127.0.0.1"
					address.Port = shared.EstablishTunnel(vmName(job.Id, rank), int(resource.Port))
					flags |= ctrl.RegisteredIngressFlagsNoPersist
				}

				result.Endpoints = append(result.Endpoints, ctrl.ConfiguredWebEndpoint{
					Host:         address,
					TargetDomain: ingress.Specification.Domain,
					Flags:        flags,
					IsPublic:     true,
				})
			}
		}

		return result, nil

	case orc.InteractiveSessionTypeVnc:
		return ctrl.ConfiguredWebSessionResult{
			Endpoints: []ctrl.ConfiguredWebEndpoint{{
				TargetDomain: cfg.Provider.Hosts.SelfPublic.Address,
				Flags:        ctrl.RegisteredIngressFlagsVnc | ctrl.RegisteredIngressFlagsNoGatewayConfig,
				IsPublic:     false,
			}},
		}, nil
	}

	return ctrl.ConfiguredWebSessionResult{}, nil
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

	isSensitiveProject := shared.IsSensitiveProject(job.Owner.Project.Value)
	if isSensitiveProject {
		return util.HttpErr(http.StatusForbidden, "VMs are not supported in sensitive projects")
	}

	if rank != 0 {
		return util.HttpErr(http.StatusInternalServerError, "VMs with multiple ranks are not supported")
	}

	nameOfVm := vmName(job.Id, rank)

	podSelector := k8smeta.LabelSelector{
		MatchLabels: map[string]string{
			"ucloud.dk/vmName": nameOfVm,
		},
	}

	firewall := &k8snetwork.NetworkPolicy{
		ObjectMeta: k8smeta.ObjectMeta{
			Name: shared.FirewallName(job.Id),
		},
		Spec: k8snetwork.NetworkPolicySpec{
			PodSelector: podSelector,
			Egress: []k8snetwork.NetworkPolicyEgressRule{
				{
					To: []k8snetwork.NetworkPolicyPeer{{PodSelector: &podSelector}},
				},
			},
			Ingress: []k8snetwork.NetworkPolicyIngressRule{{
				From: []k8snetwork.NetworkPolicyPeer{{PodSelector: &podSelector}},
			}},
		},
	}

	sshService := shared.AssignAndPrepareSshService(job).GetOrDefault(nil)
	if sshService != nil {
		shared.AllowNetworkFromWorld(firewall, []orc.PortRangeAndProto{
			{
				Protocol: orc.IpProtocolTcp,
				Start:    22,
				End:      22,
			},
		})
	}

	preparedIp := shared.PublicIpPrepare(job, firewall)
	ipService := preparedIp.Service

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
		"cp /etc/ucloud/ucloud-vmagent.service /etc/systemd/system",
		"cp /etc/ucloud/ucloud-metrics.service /etc/systemd/system",
		"systemctl daemon-reload",
		"systemctl enable --now /etc/systemd/system/ucloud-vmagent.service",
		"systemctl enable --now /etc/systemd/system/ucloud-metrics.service",
	}

	// NOTE(Dan): By default, cloud-init or KubeVirt will create a faulty netplan (based on the network-data) after a
	// reboot. This netplan will directly target a mac-address, but this mac-address will incorrectly change between
	// reboots. A result of this, is that DHCP is never turned on for the pod interface resulting in the incorrect
	// configuration of the interface itself (and thus no network). We fix this by applying a similar plan to the
	// default, but much more broadly returning any interface which might be the correct one and turning DHCP on.
	networkData := map[string]any{
		"version": 2,
		"ethernets": map[string]any{
			"default": map[string]any{
				"match": map[string]any{
					"name": "en*",
				},
				"dhcp4": true,
				"dhcp6": true,
			},
		},
	}

	machine := &job.Status.ResolvedProduct.Value

	ctx := context.Background()
	existingVm, err := KubevirtClient.VirtualMachine(Namespace).Get(ctx, nameOfVm, k8smeta.GetOptions{})
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
			NodeSelector: map[string]string{
				"kubernetes.io/hostname": node,
			},
			Domain: kvcore.DomainSpec{
				CPU: &kvcore.CPU{
					Cores:   max(1, uint32(machine.Cpu/2)),
					Threads: max(1, uint32(machine.Cpu)),
				},
				Memory: &kvcore.Memory{
					Guest: k8sresource.NewScaledQuantity(int64(machine.MemoryInGigs), k8sresource.Giga),
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

	vm.Spec.Template.ObjectMeta.Labels = map[string]string{}
	vm.Spec.Template.ObjectMeta.Labels["ucloud.dk/vmName"] = vm.Name
	jobIdLabel := shared.JobIdLabel(job.Id)
	vm.Spec.Template.ObjectMeta.Labels[jobIdLabel.First] = jobIdLabel.Second
	jobRankLabel := shared.JobRankLabel(rank)
	vm.Spec.Template.ObjectMeta.Labels[jobRankLabel.First] = jobRankLabel.Second

	tplSpec := &vm.Spec.Template.Spec

	type mountEntry struct {
		volName     string
		subpath     string
		mountFolder string
		title       string
	}
	mountsByName := map[string][]mountEntry{}

	type unpreparedMount struct {
		SubPath       string
		ReadOnly      bool
		UCloudPath    string
		Title         string
		MountFolder   string
		PersistentTag string
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
		SubPath:       shared.ExecutablesDir,
		ReadOnly:      true,
		Title:         "ucloud",
		MountFolder:   "/opt",
		PersistentTag: "ucloud-opt",
	})

	jobFolder, herr := JobFolder(job)
	if herr != nil {
		log.Warn("Could not find job folder: %v %s", job.Id, err)
		return util.HttpErr(http.StatusInternalServerError, "internal error")
	} else {
		confDir := filepath.Join(jobFolder, "config")
		confDirSubpath, ok := strings.CutPrefix(confDir, filepath.Clean(ServiceConfig.FileSystem.MountPoint)+"/")
		if !ok {
			log.Warn("sub path to folder is invalid: %v %s", job.Id, err)
			return util.HttpErr(http.StatusInternalServerError, "internal error")
		}

		logsDir := filepath.Join(jobFolder, "logs")
		logsDirSubPath, ok := strings.CutPrefix(logsDir, filepath.Clean(ServiceConfig.FileSystem.MountPoint)+"/")
		if !ok {
			log.Warn("sub path to folder is invalid: %v %s", job.Id, err)
			return util.HttpErr(http.StatusInternalServerError, "internal error")
		}

		if !hasExistingVm {
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
						on conflict (job_id) do update set 
						    agent_token = excluded.agent_token, 
						    srv_token = excluded.srv_token
					`,
					db.Params{
						"job_id":      job.Id,
						"agent_token": agentTok,
						"srv_token":   srvTok,
					},
				)
			})

			writeConfFile := func(name string, data []byte, mode os.FileMode) *util.HttpError {
				tokPath := filepath.Join(confDir, name)
				err := os.WriteFile(tokPath, data, mode)
				if err != nil {
					log.Warn("Could not create write %s: %v %s", name, job.Id, err)
					return util.HttpErr(http.StatusInternalServerError, "internal error")
				}

				err = os.Chown(tokPath, filesystem.DefaultUid, filesystem.DefaultUid)
				if err != nil {
					log.Warn("Could not chown %s: %v %s", name, job.Id, err)
					return util.HttpErr(http.StatusInternalServerError, "internal error")
				}

				return nil
			}

			if herr = writeConfFile("token", []byte(fmt.Sprintf("%s\n%s", agentTok, srvTok)), 0600); herr != nil {
				return herr
			}

			if herr = writeConfFile("ucloud-vmagent.service", vmAgentSystemdFile, 0600); herr != nil {
				return herr
			}

			if herr = writeConfFile("ucloud-metrics.service", ucmetricsSystemdFile, 0600); herr != nil {
				return herr
			}

			err = os.MkdirAll(logsDir, 0770)
			if err != nil {
				log.Warn("Could not create logs dir: %v %s", job.Id, err)
				return util.HttpErr(http.StatusInternalServerError, "internal error")
			}

			err = os.Chown(logsDir, filesystem.DefaultUid, filesystem.DefaultUid)
			if err != nil {
				log.Warn("Could not chown logs dir: %v %s", job.Id, err)
				return util.HttpErr(http.StatusInternalServerError, "internal error")
			}
		}

		unpreparedMounts = append(unpreparedMounts, unpreparedMount{
			SubPath:       confDirSubpath,
			ReadOnly:      true,
			Title:         "ucloud",
			MountFolder:   "/etc",
			PersistentTag: "ucloud-etc",
		})

		unpreparedMounts = append(unpreparedMounts, unpreparedMount{
			SubPath:       logsDirSubPath,
			ReadOnly:      false,
			Title:         "",
			MountFolder:   "/work",
			PersistentTag: "ucloud-work",
		})
	}

	fsIdx := 0
	for key := range vm.Annotations {
		if strings.HasPrefix(key, "ucloud.dk/vmVolPath-") || strings.HasPrefix(key, "ucloud.dk/vmVolReadOnly-") {
			delete(vm.Annotations, key)
		}
	}

	for _, param := range unpreparedMounts {
		subpath := param.SubPath

		volName := fmt.Sprintf("ucloud-r%s%d", util.RandomTokenNoTs(4), fsIdx)
		if param.PersistentTag != "" {
			volName = param.PersistentTag
		}

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
		} else {
			title = param.Title
		}

		bucket, _ := mountsByName[title]
		bucket = append(bucket, mountEntry{volName: volName, subpath: subpath, mountFolder: param.MountFolder, title: title})
		mountsByName[param.MountFolder+title] = bucket

		fsIdx++
	}

	var userDrives struct {
		Mounts [][]string `yaml:"mounts"`
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
				userDrives.Mounts = append(userDrives.Mounts, []string{
					item.volName, mountPath,
				})
			} else {
				mountPath = filepath.Join(bucket[0].mountFolder, title)
				cinit.Mounts = append(cinit.Mounts, []string{
					item.volName, mountPath, "virtiofs", "nofail", "0", "0",
				})
			}
		}
	}

	{
		confDir := filepath.Join(jobFolder, "config")
		optionalDriveMounts, _ := yaml.Marshal(userDrives)
		mountsYml := filepath.Join(confDir, "mounts.yml")
		_ = os.WriteFile(mountsYml, optionalDriveMounts, 0600)
		_ = os.Chown(mountsYml, filesystem.DefaultUid, filesystem.DefaultUid)
	}

	cinitRawData, _ := json.Marshal(cinit)
	cinitData := "#cloud-config\n" + string(cinitRawData)
	networkDataBytes, _ := json.Marshal(networkData)

	tplSpec.Volumes = append(tplSpec.Volumes,
		kvcore.Volume{
			Name: "cloudinitdisk",
			VolumeSource: kvcore.VolumeSource{
				CloudInitNoCloud: &kvcore.CloudInitNoCloudSource{
					UserData:    cinitData,
					NetworkData: string(networkDataBytes),
				},
			},
		},
	)

	dnsConfig, herr := shared.PrivateNetworkCreateDnsConfig(job)
	if herr != nil {
		return herr
	}

	tplSpec.Hostname = dnsConfig.Hostname
	tplSpec.Subdomain = dnsConfig.Subdomain
	tplSpec.DNSConfig = dnsConfig.PodDns
	for label, value := range dnsConfig.Labels {
		vm.Spec.Template.ObjectMeta.Labels[label] = value
	}

	if hasExistingVm {
		err = updateExistingVmWithRetry(ctx, nameOfVm, vm)
	} else {
		vm, localErr := KubevirtClient.VirtualMachine(Namespace).Create(ctx, vm, k8smeta.CreateOptions{})
		err = localErr
		if err == nil {
			ownerReference := k8smeta.OwnerReference{
				APIVersion: "kubevirt.io/v1",
				Kind:       "VirtualMachine",
				Name:       vm.Name,
				UID:        vm.UID,
			}

			herr := diskPrepareForJob(job, primaryDiskClaimName, ownerReference, diskSize)

			if firewall != nil && herr == nil {
				firewall.OwnerReferences = append(firewall.OwnerReferences, ownerReference)

				_, myError := shared.K8sClient.NetworkingV1().NetworkPolicies(Namespace).
					Create(ctx, firewall, k8smeta.CreateOptions{})
				herr = util.MergeHttpErr(herr, util.HttpErrorFromErr(myError))
			}

			if sshService != nil && herr == nil {
				sshService.OwnerReferences = append(sshService.OwnerReferences, ownerReference)

				_, myError := shared.K8sClient.CoreV1().Services(Namespace).
					Create(ctx, sshService, k8smeta.CreateOptions{})
				herr = util.MergeHttpErr(herr, util.HttpErrorFromErr(myError))
			}
			if ipService != nil && herr == nil {
				ipService.OwnerReferences = append(ipService.OwnerReferences, ownerReference)

				_, myError := shared.K8sClient.CoreV1().Services(Namespace).
					Create(ctx, ipService, k8smeta.CreateOptions{})
				herr = util.MergeHttpErr(herr, util.HttpErrorFromErr(myError))
			}

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

const vmUpdateConflictRetries = 5

func updateExistingVmWithRetry(ctx context.Context, name string, desired *kvcore.VirtualMachine) error {
	var err error

	for attempt := 0; attempt <= vmUpdateConflictRetries; attempt++ {
		latestVm, getErr := KubevirtClient.VirtualMachine(Namespace).Get(ctx, name, k8smeta.GetOptions{})
		if getErr != nil {
			return getErr
		}

		latestVm.Spec = desired.Spec
		latestVm.Annotations = desired.Annotations

		_, err = KubevirtClient.VirtualMachine(Namespace).Update(ctx, latestVm, k8smeta.UpdateOptions{})
		if err == nil {
			return nil
		}

		if !k8serrors.IsConflict(err) {
			return err
		}

		if attempt == vmUpdateConflictRetries {
			break
		}

		time.Sleep(time.Duration(attempt+1) * 100 * time.Millisecond)
	}

	return err
}

const vmDiskSizeParameter = "diskSize"

func JobFolder(job *orc.Job) (string, *util.HttpError) {
	internalMemberFiles, _, herr := filesystem.InitializeMemberFiles(job.Owner.CreatedBy, job.Owner.Project)
	if herr != nil {
		return "", herr
	}

	return filepath.Join(internalMemberFiles, "Jobs", "VirtualMachines", job.Id), nil
}

func attachResource(job *orc.Job, resource orc.AppParameterValue) *util.HttpError {
	return nil
}

func detachResource(job *orc.Job, resource orc.AppParameterValue) *util.HttpError {
	return nil
}
