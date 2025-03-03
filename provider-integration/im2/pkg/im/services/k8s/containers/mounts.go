package containers

import (
	"fmt"
	core "k8s.io/api/core/v1"
	"path/filepath"
	"slices"
	"strings"
	"ucloud.dk/pkg/im/services/k8s/filesystem"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

// prepareMountsOnJobCreate add relevant mounts into the pod and returns a mapping from internal paths (including the
// mount point) to their corresponding paths inside the container.
func prepareMountsOnJobCreate(
	job *orc.Job,
	pod *core.Pod,
	userContainer *core.Container,
	jobFolder string,
) map[string]string {
	spec := &pod.Spec
	ucloudMounts := map[string]bool{}

	for _, v := range job.Specification.Resources {
		if v.Type == orc.AppParameterValueTypeFile {
			ucloudMounts[v.Path] = v.ReadOnly
		}
	}

	for _, v := range job.Specification.Parameters {
		if v.Type == orc.AppParameterValueTypeFile {
			ucloudMounts[v.Path] = v.ReadOnly
		}
	}

	containerMountDir := "/work"

	// Container internal path to (potentially conflicting) mount sub-paths
	resolvedMounts := map[string][]util.Tuple2[string, bool]{}

	internalToSubpath := func(internalPath string) (string, bool) {
		subpath, ok := strings.CutPrefix(internalPath, filepath.Clean(ServiceConfig.FileSystem.MountPoint)+"/")
		if !ok {
			return "", false
		}

		return subpath, true
	}

	ucloudToSubpath := func(ucloudPath string) (string, bool) {
		path, ok := filesystem.UCloudToInternal(ucloudPath)
		if !ok {
			return "", false
		}

		return internalToSubpath(path)
	}

	addMount := func(containerPath, subpath string, readOnly bool) {
		existing, _ := resolvedMounts[containerPath]
		existing = append(existing, util.Tuple2[string, bool]{subpath, readOnly})
		resolvedMounts[containerPath] = existing
	}

	addInternalMount := func(containerPath, internalPath string, readOnly bool) {
		sub, ok := internalToSubpath(internalPath)
		if ok {
			addMount(containerPath, sub, readOnly)
		}
	}

	addUCloudMount := func(containerPath, internalPath string, readOnly bool) {
		sub, ok := ucloudToSubpath(internalPath)
		if ok {
			addMount(containerPath, sub, readOnly)
		}
	}

	addInternalMount(containerMountDir, jobFolder, false)

	for mount, readOnly := range ucloudMounts {
		comps := util.Components(mount)
		compsLen := len(comps)

		if compsLen == 0 {
			continue
		}

		title := comps[compsLen-1]

		if compsLen == 1 {
			drive, ok := filesystem.ResolveDrive(comps[0])
			if !ok {
				continue
			}

			title = strings.ReplaceAll(drive.Specification.Title, "Members' Files: ", "")
		}

		addUCloudMount(filepath.Join(containerMountDir, title), mount, readOnly)
	}

	fsVolume := "ucloud-filesystem"
	spec.Volumes = append(spec.Volumes, core.Volume{
		Name: fsVolume,
		VolumeSource: core.VolumeSource{
			PersistentVolumeClaim: &core.PersistentVolumeClaimVolumeSource{
				ClaimName: ServiceConfig.FileSystem.ClaimName,
				ReadOnly:  false,
			},
		},
	})

	// Internal to pod
	mountPaths := map[string]string{}

	mountIdx := 0
	for containerPath, mounts := range resolvedMounts {
		if len(mounts) == 1 {
			mount := mounts[0]

			userContainer.VolumeMounts = append(userContainer.VolumeMounts, core.VolumeMount{
				Name:      fsVolume,
				ReadOnly:  mount.Second,
				MountPath: containerPath,
				SubPath:   mount.First,
			})

			internalPath := ServiceConfig.FileSystem.MountPoint + "/" + mount.First
			mountPaths[internalPath] = containerPath

			mountIdx++
		} else {
			// NOTE(Dan): Must remain consistent with VM mount logic for overall consistency
			slices.SortFunc(mounts, func(a, b util.Tuple2[string, bool]) int {
				return strings.Compare(a.First, b.First)
			})

			for i, mount := range mounts {
				resolvedContainerPath := fmt.Sprintf("%s-%d", containerPath, i)
				userContainer.VolumeMounts = append(userContainer.VolumeMounts, core.VolumeMount{
					Name:      fsVolume,
					ReadOnly:  mount.Second,
					MountPath: resolvedContainerPath,
					SubPath:   mount.First,
				})

				internalPath := ServiceConfig.FileSystem.MountPoint + "/" + mount.First
				mountPaths[internalPath] = resolvedContainerPath

				mountIdx++
			}
		}
	}

	return mountPaths
}
