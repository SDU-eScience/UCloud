package containers

import (
	"context"
	"encoding/json"
	"fmt"
	"math/rand"
	"os"
	"path/filepath"
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
	filesystem2 "ucloud.dk/pkg/integrations/k8s/filesystem"
	shared2 "ucloud.dk/pkg/integrations/k8s/shared"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/util"
)

var syncthingPorts = map[int]bool{}
var syncthingPortsMutex = sync.Mutex{}
var syncthingConfig cfg.KubernetesSyncthingConfiguration

var syncthingDimensions = shared2.SchedulerDimensions{
	CpuMillis:     400,
	MemoryInBytes: 1000 * 1000 * 1000 * 2,
	Gpu:           0,
}

const syncthingAppName = "syncthing"

func initSyncthing() {
	syncthingConfig = ServiceConfig.Compute.Syncthing

	shared2.RegisterJobDimensionMapper(syncthingAppName, func(job *orc.Job) shared2.SchedulerDimensions {
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
	internalFolder, drive, err := filesystem2.InitializeMemberFiles(owner.CreatedBy, util.OptNone[string]())
	if err != nil {
		return "", "", err
	}

	ucloudPath, ok := filesystem2.InternalToUCloudWithDrive(drive, internalFolder)
	if !ok {
		return "", "", util.ServerHttpError("Could not find home folder for syncthing")
	}

	internalSyncthing := filepath.Join(internalFolder, "Syncthing")
	ucloudSyncthing := filepath.Join(ucloudPath, "Syncthing")

	fd, ok := filesystem2.OpenFile(internalSyncthing, unix.O_RDONLY, 0)
	if ok {
		util.SilentClose(fd)
	} else {
		if init {
			_ = filesystem2.DoCreateFolder(internalSyncthing)
		}
	}
	return internalSyncthing, ucloudSyncthing, nil
}

func syncthingBeforeMonitor(pods []*core.Pod, jobs map[string]*orc.Job, appsByJobId map[string]controller.IAppRunningConfiguration) {
	syncthingPortsMutex.Lock()
	defer syncthingPortsMutex.Unlock()

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

	syncthingPorts = result
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
		_ = filesystem2.DoDeleteFile(internal)
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
	var config orc.SyncthingConfig
	err := json.Unmarshal(configuration, &config)
	if err != nil {
		return false
	}

	if len(config.Folders) > 0 && len(config.Devices) > 0 {
		for _, folder := range config.Folders {
			driveId, ok := filesystem2.DriveIdFromUCloudPath(folder.UCloudPath)
			if !ok {
				return false
			}

			drive, ok := controller.RetrieveDrive(driveId)
			if !ok {
				return false
			}

			storageLocked := controller.IsResourceLocked(drive.Resource, drive.Specification.Product)
			if storageLocked {
				return false
			}

			if !controller.CanUseDrive(job.Owner, driveId, true) {
				return false
			}
		}

		return true
	} else {
		return false
	}
}

func syncthingMutateJobNonPersistent(job *orc.Job, configuration json.RawMessage) {
	var config orc.SyncthingConfig
	_ = json.Unmarshal(configuration, &config)

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

		driveId := util.GetOptionalElement(util.Components(folder.UCloudPath), 0).Value
		if controller.CanUseDrive(job.Owner, driveId, false) {
			appInvocation.Environment["f"+folder.Id] = orc.InvocationVar(folder.Id)
			spec.Parameters[folder.Id] = orc.AppParameterValueFile(folder.UCloudPath, false)
			appInvocation.Parameters = append(appInvocation.Parameters, orc.ApplicationParameterInputFile(folder.Id, false, "file", ""))
		}
	}

	internalSyncthing, _, err := initSyncthingFolder(job.Owner)
	if err != nil {
		log.Warn("Could not find syncthing folder: %v", err)
	} else {
		// Write configuration to filesystem for the job to consume
		fd, ok := filesystem2.OpenFile(filepath.Join(internalSyncthing, "ucloud_config.json"), unix.O_WRONLY|unix.O_CREAT|unix.O_TRUNC, 0660)
		if ok {
			normalizedConfig, _ := json.Marshal(config)
			_, _ = fd.Write(normalizedConfig)
			util.SilentClose(fd)
		}

		fd, ok = filesystem2.OpenFile(filepath.Join(internalSyncthing, "job_id.txt"), unix.O_WRONLY|unix.O_CREAT|unix.O_TRUNC, 0660)
		if ok {
			_, _ = fd.Write([]byte(job.Id))
			util.SilentClose(fd)
		}
	}
}

func syncthingMutatePod(job *orc.Job, configuration json.RawMessage, pod *core.Pod) *util.HttpError {
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

			internalSyncthingSubPath, ok := strings.CutPrefix(internalSyncthing, shared2.ServiceConfig.FileSystem.MountPoint+"/")
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

	serviceLabel := shared2.JobIdLabel(job.Id)
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

	allowNetworkFromWorld(firewall, []orc.PortRangeAndProto{
		{
			Protocol: orc.IpProtocolTcp,
			Start:    port.Value,
			End:      port.Value,
		},
	})

	return nil
}

const (
	AnnotationSyncthingPort = "ucloud.dk/syncthingPort"
)

func syncthingReconfigure() {
	drives := controller.EnumerateKnownDrives()
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
