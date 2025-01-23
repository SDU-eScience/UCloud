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

func prepareMountsOnJobCreate(
	job *orc.Job,
	pod *core.Pod,
	userContainer *core.Container,
	jobFolder string,
) {
	// TODO Limit checks

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

	ucloudToSubpath := func(ucloudPath string) (string, bool) {
		path, ok := filesystem.UCloudToInternal(ucloudPath)
		if !ok {
			return "", false
		}

		subpath, ok := strings.CutPrefix(path, filepath.Clean(ServiceConfig.FileSystem.MountPoint)+"/")
		if !ok {
			return "", false
		}

		return subpath, true
	}

	addMount := func(ucloudPath, containerPath string, readOnly bool) {
		subpath, ok := ucloudToSubpath(jobFolder)
		if ok {
			existing, _ := resolvedMounts[containerPath]
			existing = append(existing, util.Tuple2[string, bool]{subpath, readOnly})
			resolvedMounts[containerPath] = existing
		}
	}

	addMount(containerMountDir, jobFolder, false)

	for mount, readOnly := range ucloudMounts {
		comps := util.Components(mount)
		compsLen := len(comps)
		title := comps[compsLen-1]

		if compsLen == 0 {
			continue
		}

		if compsLen == 1 {
			drive, ok := filesystem.ResolveDrive(comps[0])
			if !ok {
				continue
			}

			title = strings.ReplaceAll(drive.Specification.Title, "Members' Files: ", "")
		}

		addMount(filepath.Join(containerMountDir, title), mount, readOnly)
	}

	// TODO Make this configurable
	spec.Volumes = append(spec.Volumes, core.Volume{
		Name: "ucloud_filesystem",
		VolumeSource: core.VolumeSource{
			PersistentVolumeClaim: &core.PersistentVolumeClaimVolumeSource{
				ClaimName: "cephfs",
				ReadOnly:  false,
			},
		},
	})

	mountIdx := 0
	for containerPath, mounts := range resolvedMounts {
		if len(mounts) == 1 {
			mount := mounts[0]

			userContainer.VolumeMounts = append(userContainer.VolumeMounts, core.VolumeMount{
				Name:      fmt.Sprintf("mount_%d", mountIdx),
				ReadOnly:  mount.Second,
				MountPath: containerPath,
				SubPath:   mount.First,
			})

			mountIdx++
		} else {
			slices.SortFunc(mounts, func(a, b util.Tuple2[string, bool]) int {
				return strings.Compare(a.First, b.First)
			})

			for i, mount := range mounts {
				userContainer.VolumeMounts = append(userContainer.VolumeMounts, core.VolumeMount{
					Name:      fmt.Sprintf("mount_%d", mountIdx),
					ReadOnly:  mount.Second,
					MountPath: fmt.Sprintf("%s-%d", containerPath, i),
					SubPath:   mount.First,
				})

				mountIdx++
			}
		}
	}
}
