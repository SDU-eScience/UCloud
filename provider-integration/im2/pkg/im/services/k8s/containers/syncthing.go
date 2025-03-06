package containers

import (
	"encoding/json"
	"fmt"
	"golang.org/x/sys/unix"
	core "k8s.io/api/core/v1"
	"path/filepath"
	"strings"
	"ucloud.dk/pkg/im/services/k8s/filesystem"
	"ucloud.dk/pkg/im/services/k8s/shared"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

func initSyncthing() {
	if ServiceConfig.Compute.Syncthing.Enabled {
		iapps["syncthing"] = ContainerIAppHandler{
			Flags:                           0,
			BeforeRestart:                   syncthingBeforeRestart,
			ValidateConfiguration:           syncthingValidateConfiguration,
			ResetConfiguration:              syncthingResetConfiguration,
			RetrieveDefaultConfiguration:    syncthingRetrieveDefaultConfiguration,
			ShouldRun:                       syncthingShouldRun,
			MutateJobNonPersistent:          syncthingMutateJobNonPersistent,
			MutatePod:                       syncthingMutatePod,
			MutateService:                   nil,
			MutateNetworkPolicy:             nil,
			MutateJobSpecBeforeRegistration: syncthingMutateJobSpec,
		}
	}
}

func initSyncthingFolder(owner orc.ResourceOwner) (string, string, error) {
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

	_ = filesystem.DoCreateFolder(internalSyncthing)
	return internalSyncthing, ucloudSyncthing, nil
}

func syncthingMutateJobSpec(owner orc.ResourceOwner, spec *orc.JobSpecification) error {
	spec.Application.Version = "1"
	_, ucloudSyncthing, err := initSyncthingFolder(owner)
	if err != nil {
		return err
	}
	spec.Parameters["stateFolder"] = orc.AppParameterValueFile(ucloudSyncthing, false)
	return nil
}

func syncthingBeforeRestart(job *orc.Job) error {
	return nil
}

func syncthingValidateConfiguration(job *orc.Job, configuration json.RawMessage) error {
	var config orc.SyncthingConfig
	return json.Unmarshal(configuration, &config)
}

func syncthingResetConfiguration(job *orc.Job, configuration json.RawMessage) (json.RawMessage, error) {
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

	return len(config.Folders) > 0 && len(config.Devices) > 0
}

func syncthingMutateJobNonPersistent(job *orc.Job, configuration json.RawMessage) {
	var config orc.SyncthingConfig
	_ = json.Unmarshal(configuration, &config)

	appInvocation := &job.Status.ResolvedApplication.Invocation
	tool := &appInvocation.Tool.Tool
	tool.Description.Image = "alpine:latest"
	appInvocation.Invocation = []orc.InvocationParameter{
		orc.InvocationWord("sleep"),
		orc.InvocationWord("inf"),
	}
	appInvocation.Environment = map[string]orc.InvocationParameter{}

	spec := &job.Specification

	spec.Parameters = map[string]orc.AppParameterValue{}
	spec.Resources = nil

	for i := 0; i < len(config.Folders); i++ {
		folder := &config.Folders[i]
		if folder.Id == "" {
			folder.Id = util.RandomToken(16)
		}

		// TODO Mounts permissions
		appInvocation.Environment["f"+folder.Id] = orc.InvocationVar(folder.Id)
		spec.Parameters[folder.Id] = orc.AppParameterValueFile(folder.UCloudPath, false)
		appInvocation.Parameters = append(appInvocation.Parameters, orc.ApplicationParameterInputFile(folder.Id, false, "file", ""))
		//spec.Resources = append(spec.Resources, orc.AppParameterValueFile(folder.UCloudPath, false))
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
	}
}

func syncthingMutatePod(job *orc.Job, configuration json.RawMessage, pod *core.Pod) error {
	podSpec := &pod.Spec
	for i := 0; i < len(podSpec.Containers); i++ {
		container := &podSpec.Containers[i]

		if container.Name == "user-job" {
			container.ImagePullPolicy = "Always"
			internalSyncthing, _, err := initSyncthingFolder(job.Owner)
			if err != nil {
				return err
			}

			internalSyncthingSubPath, ok := strings.CutPrefix(internalSyncthing, shared.ServiceConfig.FileSystem.MountPoint+"/")
			if !ok {
				return fmt.Errorf("internal error")
			}

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
			}
		}
	}

	// TODO service
	return nil
}
