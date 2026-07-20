package containers

import (
	"context"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"math/rand"
	"os"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"sync"
	"time"

	"golang.org/x/sys/unix"
	core "k8s.io/api/core/v1"
	networking "k8s.io/api/networking/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	meta "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/util/intstr"
	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/integrations/k8s/shared"
	"ucloud.dk/pkg/ucxdelivery"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var syncthingPorts = map[int]bool{}
var syncthingPortsMutex = sync.Mutex{}
var syncthingConfig cfg.KubernetesSyncthingConfiguration

var syncthingDimensions = shared.SchedulerDimensions{
	CpuMillis:     400,
	MemoryInBytes: 1000 * 1000 * 1000 * 2,
	Resources:     map[string]int{},
}

const syncthingAppName = "syncthing"

const (
	syncthingUcxExecutable         = "builtin://ucx-syncthing"
	syncthingUcxIntegrationVersion = "1"
	syncthingImageVersion          = "2.1.2"
	syncthingUcxPort               = 8435
)

func initSyncthing() {
	syncthingConfig = ServiceConfig.Compute.Syncthing

	shared.RegisterJobDimensionMapper(syncthingAppName, func(job *orc.Job) shared.SchedulerDimensions {
		return syncthingDimensions
	})

	if ServiceConfig.Compute.Syncthing.Enabled {
		IApps[syncthingAppName] = ContainerIAppHandler{
			Version:                         syncthingImageVersion,
			Flags:                           0,
			BeforeRestart:                   syncthingBeforeRestart,
			ValidateConfiguration:           syncthingValidateConfiguration,
			ConfigurationChanged:            syncthingConfigurationChanged,
			ResetConfiguration:              syncthingResetConfiguration,
			RetrieveDefaultConfiguration:    syncthingRetrieveDefaultConfiguration,
			ShouldRun:                       syncthingShouldRun,
			PodMatchesConfiguration:         syncthingPodMatchesConfiguration,
			MutateJobNonPersistent:          syncthingMutateJobNonPersistent,
			MutatePod:                       syncthingMutatePod,
			MutateService:                   syncthingMutateService,
			MutateNetworkPolicy:             syncthingMutateFirewall,
			MutateJobSpecBeforeRegistration: syncthingMutateJobSpec,
			BeforeMonitor:                   syncthingBeforeMonitor,
		}

		go func() {
			for {
				reconfigureFile := "/tmp/syncthing-reconfigure"
				_, err := os.Stat(reconfigureFile)
				if err == nil {
					err := os.Remove(reconfigureFile)
					if err != nil {
						panic(err)
					} else {
						syncthingReconfigure()
					}
				}
				time.Sleep(1 * time.Second)
			}
		}()
	}
}

func initSyncthingFolder(owner orc.ResourceOwner) (string, string, *util.HttpError) {
	return initSyncthingFolderEx(owner, true)
}

func initSyncthingFolderEx(owner orc.ResourceOwner, init bool) (string, string, *util.HttpError) {
	internalFolder, drive, err := filesystem.InitializeMemberFiles(owner.CreatedBy, util.OptNone[string]())
	if err != nil {
		return "", "", err
	}

	ucloudPath, ok := filesystem.InternalToUCloudWithDrive(drive, internalFolder)
	if !ok {
		return "", "", util.ServerHttpError("Could not find home folder for syncthing")
	}

	internalSyncthing := filepath.Join(internalFolder, "Syncthing")
	ucloudSyncthing := filepath.Join(ucloudPath, "Syncthing")

	fd, ok := filesystem.OpenFile(internalSyncthing, unix.O_RDONLY, 0)
	if ok {
		util.SilentClose(fd)
	} else {
		if init {
			_ = filesystem.DoCreateFolder(internalSyncthing)
		}
	}
	return internalSyncthing, ucloudSyncthing, nil
}

func syncthingBeforeMonitor(pods []*core.Pod, jobs map[string]*orc.Job, appsByJobId map[string]controller.IAppRunningConfiguration) {
	result := map[int]bool{}

	for _, pod := range pods {
		idAndRank, ok := podNameToIdAndRank(pod.Name)
		if !ok {
			continue
		}
		iapp, ok := appsByJobId[idAndRank.First]
		if !ok || iapp.AppName != "syncthing" {
			continue
		}

		assignedPort := syncthingGetAssignedPort(pod)
		if assignedPort.Present {
			result[assignedPort.Value] = true
		}
	}

	syncthingPortsMutex.Lock()
	syncthingPorts = result
	syncthingPortsMutex.Unlock()
}

func syncthingGetAssignedPort(pod *core.Pod) util.Option[int] {
	port := util.OptMapGet(pod.Annotations, AnnotationSyncthingPort)
	if port.Present {
		portValue, err := strconv.ParseInt(port.Value, 10, 64)
		if err == nil {
			return util.OptValue(int(portValue))
		}
	}
	return util.OptNone[int]()
}

func syncthingMutateJobSpec(owner orc.ResourceOwner, spec *orc.JobSpecification) *util.HttpError {
	spec.Application.Version = syncthingUcxIntegrationVersion
	if spec.Labels == nil {
		spec.Labels = map[string]string{}
	}
	spec.Labels["ucloud.dk/ucxport"] = strconv.Itoa(syncthingUcxPort)
	_, ucloudSyncthing, err := initSyncthingFolder(owner)
	if err != nil {
		return err
	}
	spec.Parameters["stateFolder"] = orc.AppParameterValueFile(ucloudSyncthing, false)
	return nil
}

func syncthingBeforeRestart(job *orc.Job) *util.HttpError {
	return nil
}

func syncthingValidateConfiguration(job *orc.Job, configuration json.RawMessage) *util.HttpError {
	var config orc.SyncthingConfig
	if err := json.Unmarshal(configuration, &config); err != nil {
		return util.HttpErrorFromErr(err)
	}

	folderIds := map[string]bool{}
	for _, folder := range config.Folders {
		if folderIds[folder.Id] {
			return util.UserHttpError("Duplicate Syncthing folder ID: %s", folder.Id)
		}
		folderIds[folder.Id] = true
	}

	deviceIds := map[string]bool{}
	for _, device := range config.Devices {
		if deviceIds[device.DeviceId] {
			return util.UserHttpError("Duplicate Syncthing device ID: %s", device.DeviceId)
		}
		deviceIds[device.DeviceId] = true
	}

	return nil
}

func syncthingConfigurationChanged(job *orc.Job, configuration json.RawMessage) {
	config, ok := syncthingRuntimeConfiguration(job, configuration)
	if !ok {
		return
	}

	internalSyncthing, _, folderErr := initSyncthingFolder(job.Owner)
	if folderErr != nil {
		return
	}

	normalizedConfig, marshalErr := json.Marshal(config)
	if marshalErr != nil {
		return
	}
	_ = filesystem.WriteFileAtomic(filepath.Join(internalSyncthing, "ucloud_config.json"), normalizedConfig, 0660)
	_ = filesystem.WriteFileAtomic(filepath.Join(internalSyncthing, "job_id.txt"), []byte(job.Id), 0660)
}

func syncthingResetConfiguration(job *orc.Job, configuration json.RawMessage) (json.RawMessage, *util.HttpError) {
	internal, _, err := initSyncthingFolder(job.Owner)
	if err == nil {
		_ = filesystem.DoDeleteFile(internal)
		_, _, _ = initSyncthingFolder(job.Owner)
	} else {
		return json.RawMessage{}, util.ServerHttpError("failed to reset configuration")
	}
	return syncthingRetrieveDefaultConfiguration(job.Owner), nil
}

func syncthingRetrieveDefaultConfiguration(owner orc.ResourceOwner) json.RawMessage {
	value := orc.SyncthingConfig{
		Folders: []orc.SyncthingFolder{},
		Devices: []orc.SyncthingDevice{},
	}
	result, _ := json.Marshal(value)
	return result
}

func syncthingShouldRun(job *orc.Job, configuration json.RawMessage) bool {
	config, ok := syncthingRuntimeConfiguration(job, configuration)
	if !ok {
		return false
	}

	return len(config.Folders) > 0 && len(config.Devices) > 0
}

func syncthingPodMatchesConfiguration(job *orc.Job, configuration json.RawMessage, pod *core.Pod) bool {
	observedUcxVersion := util.OptMapGet(pod.Annotations, AnnotationSyncthingUcxVersion)
	if !observedUcxVersion.Present || observedUcxVersion.Value != syncthingUcxIntegrationVersion {
		return false
	}

	config, ok := syncthingRuntimeConfiguration(job, configuration)
	if !ok {
		return true
	}

	observedFolders := util.OptMapGet(pod.Annotations, AnnotationSyncthingFolders)
	return observedFolders.Present && observedFolders.Value == syncthingFoldersComputeHash(config)
}

func syncthingRuntimeConfiguration(job *orc.Job, configuration json.RawMessage) (orc.SyncthingConfig, bool) {
	var config orc.SyncthingConfig
	if err := json.Unmarshal(configuration, &config); err != nil {
		return orc.SyncthingConfig{}, false
	}

	newFolders := make([]orc.SyncthingFolder, 0, len(config.Folders))
	for _, folder := range config.Folders {
		if syncthingFolderAccessible(job, folder) {
			if folder.Id == "" {
				hash := sha256.Sum256([]byte(folder.UCloudPath))
				folder.Id = fmt.Sprintf("%x", hash[:8])
			}
			newFolders = append(newFolders, folder)
		}
	}
	config.Folders = newFolders
	return config, true
}

func syncthingFolderAccessible(job *orc.Job, folder orc.SyncthingFolder) bool {
	driveId, ok := filesystem.DriveIdFromUCloudPath(folder.UCloudPath)
	if !ok {
		return false
	}

	drive, ok := controller.DriveRetrieve(driveId)
	if !ok || controller.ResourceIsLocked(drive.Resource, drive.Specification.Product) {
		return false
	}

	if !controller.DriveCanUse(job.Owner, driveId, false) {
		return false
	}

	internalPath, ok, _ := filesystem.UCloudToInternal(folder.UCloudPath)
	if !ok {
		return false
	}

	fd, ok := filesystem.OpenFile(internalPath, unix.O_RDONLY, 0)
	if !ok {
		return false
	}
	defer util.SilentClose(fd)

	info, err := fd.Stat()
	return err == nil && info.IsDir()
}

func syncthingFoldersComputeHash(config orc.SyncthingConfig) string {
	paths := make([]string, 0, len(config.Folders))
	for _, folder := range config.Folders {
		paths = append(paths, folder.UCloudPath)
	}
	slices.Sort(paths)
	serialized, _ := json.Marshal(paths)
	return fmt.Sprintf("%x", sha256.Sum256(serialized))
}

func syncthingMountedFoldersComputeHash(job *orc.Job) string {
	config := orc.SyncthingConfig{}
	for _, parameter := range job.Specification.Parameters {
		if parameter.Type == orc.AppParameterValueTypeFile {
			config.Folders = append(config.Folders, orc.SyncthingFolder{UCloudPath: parameter.Path})
		}
	}
	return syncthingFoldersComputeHash(config)
}

func syncthingMutateJobNonPersistent(job *orc.Job, configuration json.RawMessage) {
	config, _ := syncthingRuntimeConfiguration(job, configuration)

	app := &job.Status.ResolvedApplication.Value
	app.Metadata.Name = syncthingAppName
	app.Metadata.Version = syncthingUcxIntegrationVersion
	app.Invocation.Ucx = util.OptValue(orc.UcxDescription{
		Executable: util.OptValue(orc.UcxExecutableDescription{ManifestUrl: syncthingUcxExecutable}),
	})
	appInvocation := &app.Invocation
	appInvocation.Environment = map[string]orc.InvocationParameter{}

	spec := &job.Specification

	spec.Parameters = map[string]orc.AppParameterValue{}
	spec.Resources = nil

	for i := 0; i < len(config.Folders); i++ {
		folder := &config.Folders[i]
		appInvocation.Environment["f"+folder.Id] = orc.InvocationVar(folder.Id)
		spec.Parameters[folder.Id] = orc.AppParameterValueFile(folder.UCloudPath, false)
		appInvocation.Parameters = append(appInvocation.Parameters, orc.ApplicationParameterInputFile(folder.Id, false, "file", ""))
	}
}

func syncthingMutatePod(job *orc.Job, configuration json.RawMessage, pod *core.Pod) *util.HttpError {
	pod.Annotations[AnnotationSyncthingFolders] = syncthingMountedFoldersComputeHash(job)
	pod.Annotations[AnnotationSyncthingUcxVersion] = syncthingUcxIntegrationVersion
	if err := ucxdelivery.TrackApp(&job.Status.ResolvedApplication.Value); err != nil {
		return util.ServerHttpError("failed to track Syncthing UCX executable: %s", err)
	}

	port := syncthingAllocatePort(pod)
	if !port.Present {
		return util.ServerHttpError("could not allocate a port for Syncthing")
	}

	podSpec := &pod.Spec
	for i := 0; i < len(podSpec.Containers); i++ {
		container := &podSpec.Containers[i]

		if container.Name == ContainerUserJob {
			internalSyncthing, _, err := initSyncthingFolder(job.Owner)
			if err != nil {
				return err
			}

			internalSyncthingSubPath, ok := strings.CutPrefix(internalSyncthing, shared.ServiceConfig.FileSystem.MountPoint+"/")
			if !ok {
				return util.ServerHttpError("internal error")
			}

			container.Env = append(container.Env, core.EnvVar{
				Name:  "SYNCTHING_PORT",
				Value: fmt.Sprint(port.Value),
			})

			container.Env = append(container.Env, core.EnvVar{
				Name:  "SYNCTHING_RELAYS",
				Value: fmt.Sprint(syncthingConfig.RelaysEnabled),
			})

			container.Env = append(container.Env, core.EnvVar{
				Name:  "STATE_DIR",
				Value: "/syncthing-state",
			})

			container.VolumeMounts = append(container.VolumeMounts, core.VolumeMount{
				Name:      "ucloud-filesystem",
				ReadOnly:  false,
				MountPath: "/syncthing-state",
				SubPath:   internalSyncthingSubPath,
			})

			container.Resources.Limits = map[core.ResourceName]resource.Quantity{}
			container.Resources.Requests = map[core.ResourceName]resource.Quantity{
				core.ResourceCPU:    *resource.NewScaledQuantity(int64(syncthingDimensions.CpuMillis), resource.Milli),
				core.ResourceMemory: *resource.NewScaledQuantity(int64(syncthingDimensions.MemoryInBytes), 0),
			}

			if util.DevelopmentModeEnabled() && ServiceConfig.Compute.Syncthing.DevelopmentSourceCode != "" {
				container.Image = fmt.Sprintf("dreg.cloud.sdu.dk/ucloud/syncthing-go-dev:%s", syncthingImageVersion)
				container.VolumeMounts = append(container.VolumeMounts, core.VolumeMount{
					Name:      "ucloud-filesystem",
					ReadOnly:  false,
					MountPath: "/opt/source",
					SubPath:   ServiceConfig.Compute.Syncthing.DevelopmentSourceCode,
				})

				container.Command = []string{"bash", "-c", syncthingContainerCommand("cd /opt/source; export PATH=$PATH:/usr/local/go/bin; exec go run . \"$STATE_DIR\"")}
			} else {
				container.Image = fmt.Sprintf("dreg.cloud.sdu.dk/ucloud/syncthing-go:%s", syncthingImageVersion)
				container.Command = []string{"bash", "-c", syncthingContainerCommand("exec /usr/bin/ucloud-sync \"$STATE_DIR\"")}
			}
		}
	}
	return nil
}

func syncthingContainerCommand(syncCommand string) string {
	return fmt.Sprintf(`set -u

supervise_ucx() {
  WATCHED=/opt/ucloud-ucx/current
  RUNTIME_DIR=/tmp/ucloud-ucx-syncthing
  RUNTIME_BIN="$RUNTIME_DIR/current"
  mkdir -p "$RUNTIME_DIR"

  file_state() {
    if [ ! -f "$WATCHED" ]; then
      printf 'missing'
      return
    fi
    sha256sum "$WATCHED" | cut -d ' ' -f 1
  }

  export UCX_PORT=%d
  export UCLOUD_UCX_APP_NAME=%q
  export UCLOUD_UCX_APP_VERSION=%q
  LAST_STATE=""
  UCX_PID=""

  stop_ucx() {
    [ -z "$UCX_PID" ] || kill "$UCX_PID" 2>/dev/null || true
    wait "$UCX_PID" 2>/dev/null || true
    exit 0
  }
  trap stop_ucx TERM INT

  while true; do
    while [ ! -f "$WATCHED" ]; do
      sleep 1
    done

    CURRENT_STATE="$(file_state)"
    if [ "$CURRENT_STATE" != "$LAST_STATE" ]; then
      if ! cp "$WATCHED" "$RUNTIME_BIN.tmp"; then
        sleep 1
        continue
      fi
      chmod +x "$RUNTIME_BIN.tmp"
      mv "$RUNTIME_BIN.tmp" "$RUNTIME_BIN"
      LAST_STATE="$CURRENT_STATE"
    fi

    export UCX_EXECUTABLE="$RUNTIME_BIN"
    "$RUNTIME_BIN" &
    UCX_PID="$!"
    while kill -0 "$UCX_PID" 2>/dev/null; do
      sleep 1
      NEXT_STATE="$(file_state)"
      if [ "$NEXT_STATE" != "$LAST_STATE" ]; then
        kill "$UCX_PID" 2>/dev/null || true
        wait "$UCX_PID" 2>/dev/null || true
        break
      fi
    done
    wait "$UCX_PID" 2>/dev/null || true
    sleep 1
  done
}

run_sync() {
  %s
}

SYNC_PID=""
UCX_SUPERVISOR_PID=""
terminate() {
  trap - TERM INT
  [ -z "$SYNC_PID" ] || kill "$SYNC_PID" 2>/dev/null || true
  [ -z "$UCX_SUPERVISOR_PID" ] || kill "$UCX_SUPERVISOR_PID" 2>/dev/null || true
  wait 2>/dev/null || true
  exit 0
}
trap terminate TERM INT

supervise_ucx &
UCX_SUPERVISOR_PID="$!"
run_sync &
SYNC_PID="$!"
wait "$SYNC_PID"
STATUS="$?"
kill "$UCX_SUPERVISOR_PID" 2>/dev/null || true
wait "$UCX_SUPERVISOR_PID" 2>/dev/null || true
exit "$STATUS"
`, syncthingUcxPort, syncthingAppName, syncthingUcxIntegrationVersion, syncCommand)
}

func syncthingAllocatePort(pod *core.Pod) util.Option[int] {
	syncthingPortsMutex.Lock()
	defer syncthingPortsMutex.Unlock()

	count := syncthingConfig.PortMax - syncthingConfig.PortMin
	if count <= 0 {
		return util.OptNone[int]()
	}

	attempt := rand.Intn(count)
	remaining := count
	for remaining > 0 {
		port := syncthingConfig.PortMin + (attempt % count)
		if port != syncthingUcxPort {
			_, exists := syncthingPorts[port]
			if !exists {
				pod.Annotations[AnnotationSyncthingPort] = fmt.Sprint(port)
				syncthingPorts[port] = true
				return util.OptValue(port)
			}
		}

		attempt++
		remaining--
	}

	return util.OptNone[int]()
}

func syncthingMutateService(job *orc.Job, configuration json.RawMessage, unrelatedService *core.Service, pod *core.Pod) *util.HttpError {
	// NOTE(Dan): This code doesn't actually modify the already created service, but instead creates a new one.
	// We need this function mostly to be able to grab the owner reference from the other service. We can't do it in the
	// pod mutation function because the pod hasn't been created yet.

	port := syncthingGetAssignedPort(pod)
	if !port.Present {
		return util.ServerHttpError("no syncthing port")
	}

	serviceLabel := shared.JobIdLabel(job.Id)
	service := &core.Service{
		ObjectMeta: meta.ObjectMeta{
			Name: fmt.Sprintf("j-%v-syncthing", job.Id),
			Labels: map[string]string{
				serviceLabel.First: serviceLabel.Second,
			},
			OwnerReferences: unrelatedService.OwnerReferences,
		},
		Spec: core.ServiceSpec{
			Type:      core.ServiceTypeClusterIP,
			ClusterIP: "",
			Selector: map[string]string{
				serviceLabel.First: serviceLabel.Second,
			},
			ExternalIPs: []string{syncthingConfig.IpAddress},
			Ports: []core.ServicePort{
				{
					Name:     "syncthing",
					Protocol: core.ProtocolTCP,
					Port:     int32(port.Value),
					TargetPort: intstr.IntOrString{
						IntVal: int32(port.Value),
					},
				},
			},
		},
	}

	timeout, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	_, err := K8sClient.CoreV1().Services(Namespace).Create(timeout, service, meta.CreateOptions{})
	return util.HttpErrorFromErr(err)
}

func syncthingMutateFirewall(job *orc.Job, configuration json.RawMessage, firewall *networking.NetworkPolicy, pod *core.Pod) *util.HttpError {
	port := syncthingGetAssignedPort(pod)
	if !port.Present {
		return util.ServerHttpError("no syncthing port")
	}

	shared.AllowNetworkFromWorld(firewall, []orc.PortRangeAndProto{
		{
			Protocol: orc.IpProtocolTcp,
			Start:    port.Value,
			End:      port.Value,
		},
		{
			Protocol: orc.IpProtocolTcp,
			Start:    syncthingUcxPort,
			End:      syncthingUcxPort,
		},
	})

	return nil
}

const (
	AnnotationSyncthingPort       = "ucloud.dk/syncthingPort"
	AnnotationSyncthingFolders    = "ucloud.dk/syncthingFolders"
	AnnotationSyncthingUcxVersion = "ucloud.dk/syncthingUcxVersion"
)

func syncthingReconfigure() {
	drives := controller.DriveEnumerateKnown()
	success := 0
	for _, drive := range drives {
		if strings.HasPrefix(drive.ProviderGeneratedId, "h-") {
			config, err := controller.IAppConfigureFromLegacy(syncthingAppName, drive.Owner)
			if err == nil && config.Present {
				success++
			}
		}
	}

	log.Info("Configured %v instances of syncthing from %v drives", success, len(drives))
}
