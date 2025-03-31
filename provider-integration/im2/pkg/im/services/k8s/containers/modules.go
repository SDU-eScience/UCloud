package containers

import (
	"fmt"
	core "k8s.io/api/core/v1"
	"path/filepath"
	cfg "ucloud.dk/pkg/im/config"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

func prepareModules(job *orc.Job, pod *core.Pod, userContainer *core.Container) {
	app := &job.Status.ResolvedApplication.Invocation
	if len(app.Modules.Optional) > 0 {
		userContainer.Env = append(userContainer.Env, core.EnvVar{
			Name:  "UCLOUD_MODULES_ROOT",
			Value: app.Modules.MountPath,
		})

		modulesToMount := map[string]cfg.KubernetesModuleEntry{}

		for _, requestedModule := range app.Modules.Optional {
			for _, availableModule := range ServiceConfig.Compute.Modules {
				if availableModule.Name == requestedModule {
					modulesToMount[availableModule.Name] = availableModule
					break
				}
			}
		}

		volCounter := 0
		for _, mod := range modulesToMount {
			var volumeToUse util.Option[string]

			for _, existingVolume := range pod.Spec.Volumes {
				claimName := ""
				hostPath := ""
				if existingVolume.PersistentVolumeClaim != nil {
					claimName = existingVolume.PersistentVolumeClaim.ClaimName
				}

				if existingVolume.HostPath != nil {
					hostPath = existingVolume.HostPath.Path
				}

				if mod.ClaimName.Present && mod.ClaimName.Value == claimName {
					volumeToUse.Set(existingVolume.Name)
					break
				}

				if mod.HostPath.Present && mod.HostPath.Value == hostPath {
					volumeToUse.Set(existingVolume.Name)
					break
				}
			}

			if !volumeToUse.Present {
				source := core.VolumeSource{}
				if mod.HostPath.Present {
					source.HostPath = &core.HostPathVolumeSource{
						Path: mod.HostPath.Value,
					}
				} else if mod.ClaimName.Present {
					source.PersistentVolumeClaim = &core.PersistentVolumeClaimVolumeSource{
						ClaimName: mod.ClaimName.Value,
					}
				}

				volName := fmt.Sprintf("module-%d", volCounter)
				volCounter++

				pod.Spec.Volumes = append(pod.Spec.Volumes, core.Volume{
					Name:         volName,
					VolumeSource: source,
				})

				volumeToUse.Set(volName)
			}

			userContainer.VolumeMounts = append(userContainer.VolumeMounts, core.VolumeMount{
				Name:      volumeToUse.Value,
				ReadOnly:  true,
				MountPath: filepath.Join(app.Modules.MountPath, mod.Name),
				SubPath:   mod.VolSubPath,
			})
		}
	}
}
