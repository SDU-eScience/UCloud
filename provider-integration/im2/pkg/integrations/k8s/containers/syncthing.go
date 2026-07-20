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

func initSyncthing() {
	syncthingConfig = ServiceConfig.Compute.Syncthing

	shared.RegisterJobDimensionMapper(syncthingAppName, func(job *orc.Job) shared.SchedulerDimensions {
		return syncthingDimensions
	})

	if ServiceConfig.Compute.Syncthing.Enabled {
		IApps[syncthingAppName] = ContainerIAppHandler{
			Flags:                           0,
			BeforeRestart:                   syncthingBeforeRestart,
			ValidateConfiguration:           syncthingValidateConfiguration,
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
	spec.Application.Version = "1"
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
	return util.HttpErrorFromErr(json.Unmarshal(configuration, &config))
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

	appInvocation := &job.Status.ResolvedApplication.Value.Invocation
	appInvocation.Environment = map[string]orc.InvocationParameter{}

	spec := &job.Specification

	spec.Parameters = map[string]orc.AppParameterValue{}
	spec.Resources = nil

	for i := 0; i < len(config.Folders); i++ {
		folder := &config.Folders[i]
		if folder.Id == "" {
			folder.Id = util.RandomToken(16)
		}

		appInvocation.Environment["f"+folder.Id] = orc.InvocationVar(folder.Id)
		spec.Parameters[folder.Id] = orc.AppParameterValueFile(folder.UCloudPath, false)
		appInvocation.Parameters = append(appInvocation.Parameters, orc.ApplicationParameterInputFile(folder.Id, false, "file", ""))
	}

	internalSyncthing, _, err := initSyncthingFolder(job.Owner)
	if err != nil {
		log.Warn("Could not find syncthing folder: %v", err)
	} else {
		// Write configuration to filesystem for the job to consume
		fd, ok := filesystem.OpenFile(filepath.Join(internalSyncthing, "ucloud_config.json"), unix.O_WRONLY|unix.O_CREAT|unix.O_TRUNC, 0660)
		if ok {
			normalizedConfig, _ := json.Marshal(config)
			_, _ = fd.Write(normalizedConfig)
			util.SilentClose(fd)
		}

		fd, ok = filesystem.OpenFile(filepath.Join(internalSyncthing, "job_id.txt"), unix.O_WRONLY|unix.O_CREAT|unix.O_TRUNC, 0660)
		if ok {
			_, _ = fd.Write([]byte(job.Id))
			util.SilentClose(fd)
		}
	}
}

func syncthingMutatePod(job *orc.Job, configuration json.RawMessage, pod *core.Pod) *util.HttpError {
	pod.Annotations[AnnotationSyncthingFolders] = syncthingMountedFoldersComputeHash(job)

	port := syncthingAllocatePort(pod)
	if !port.Present {
		return util.ServerHttpError("could not allocate a port for Syncthing")
	}

	podSpec := &pod.Spec
	for i := 0; i < len(podSpec.Containers); i++ {
		container := &podSpec.Containers[i]

		if container.Name == ContainerUserJob {
			container.ImagePullPolicy = "Always"
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
				container.Image = "dreg.cloud.sdu.dk/ucloud/syncthing-go-dev:latest"
				container.VolumeMounts = append(container.VolumeMounts, core.VolumeMount{
					Name:      "ucloud-filesystem",
					ReadOnly:  false,
					MountPath: "/opt/source",
					SubPath:   ServiceConfig.Compute.Syncthing.DevelopmentSourceCode,
				})

				container.Command = []string{
					"bash", "-c", "cd /opt/source ; export PATH=$PATH:/usr/local/go/bin ; go run . \"$STATE_DIR\"; sleep inf",
				}
			} else {
				container.Image = "dreg.cloud.sdu.dk/ucloud/syncthing-go:latest"
				container.Command = []string{
					"bash", "-c", "/usr/bin/ucloud-sync \"$STATE_DIR\"",
				}
			}
		}
	}
	return nil
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
		if port == 8434 {
			// Used by Syncthing for the API.
			continue
		}

		_, exists := syncthingPorts[port]
		if !exists {
			pod.Annotations[AnnotationSyncthingPort] = fmt.Sprint(port)
			syncthingPorts[port] = true
			return util.OptValue(port)
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
	})

	return nil
}

const (
	AnnotationSyncthingPort    = "ucloud.dk/syncthingPort"
	AnnotationSyncthingFolders = "ucloud.dk/syncthingFolders"
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
