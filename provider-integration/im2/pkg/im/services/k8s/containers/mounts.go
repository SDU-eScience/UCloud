package containers

import (
	"encoding/json"
	"fmt"
	core "k8s.io/api/core/v1"
	"path/filepath"
	"slices"
	"strings"
	"ucloud.dk/pkg/im/services/k8s/filesystem"
	"ucloud.dk/pkg/im/services/k8s/shared"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

type mountedFolder struct {
	InternalPath string
	SubPath      string
	PodPath      string
	ReadOnly     bool
}

type mountResult struct {
	Folders                 map[string]mountedFolder
	MountedDrivesAsReadOnly map[string]bool
}

func calculateMounts(job *orc.Job, internalJobFolder string) mountResult {
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

	mountedDrivesAsReadOnly := map[string]bool{}
	addUCloudMount := func(containerPath, internalPath string, readOnly bool) {
		sub, ok := ucloudToSubpath(internalPath)
		if ok {
			addMount(containerPath, sub, readOnly)
		}
	}

	addInternalMount(containerMountDir, internalJobFolder, false)

	for mount, readOnly := range ucloudMounts {
		comps := util.Components(mount)
		compsLen := len(comps)

		if compsLen == 0 {
			continue
		}

		alreadyMountedAsReadOnly, ok := mountedDrivesAsReadOnly[comps[0]]
		if ok && alreadyMountedAsReadOnly {
			mountedDrivesAsReadOnly[comps[0]] = readOnly
		} else if !ok {
			mountedDrivesAsReadOnly[comps[0]] = readOnly
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

	mountPaths := map[string]mountedFolder{}

	mountIdx := 0
	for containerPath, mounts := range resolvedMounts {
		if len(mounts) == 1 {
			mount := mounts[0]

			internalPath := ServiceConfig.FileSystem.MountPoint + "/" + mount.First
			mountPaths[internalPath] = mountedFolder{
				InternalPath: internalPath,
				SubPath:      mount.First,
				PodPath:      containerPath,
				ReadOnly:     mount.Second,
			}

			mountIdx++
		} else {
			// NOTE(Dan): Must remain consistent with VM mount logic for overall consistency
			slices.SortFunc(mounts, func(a, b util.Tuple2[string, bool]) int {
				return strings.Compare(a.First, b.First)
			})

			for i, mount := range mounts {
				resolvedContainerPath := fmt.Sprintf("%s-%d", containerPath, i)

				internalPath := ServiceConfig.FileSystem.MountPoint + "/" + mount.First
				mountPaths[internalPath] = mountedFolder{
					InternalPath: internalPath,
					SubPath:      mount.First,
					PodPath:      resolvedContainerPath,
					ReadOnly:     mount.Second,
				}

				mountIdx++
			}
		}
	}

	return mountResult{
		Folders:                 mountPaths,
		MountedDrivesAsReadOnly: mountedDrivesAsReadOnly,
	}
}

// prepareMountsOnJobCreate add relevant mounts into the pod and returns a mapping from internal paths (including the
// mount point) to their corresponding paths inside the container.
func prepareMountsOnJobCreate(
	job *orc.Job,
	pod *core.Pod,
	userContainer *core.Container,
	jobFolder string,
) map[string]string {
	spec := &pod.Spec

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

	mounts := calculateMounts(job, jobFolder)
	folders := mounts.Folders
	result := map[string]string{}
	mountedDrivesAsReadOnly := mounts.MountedDrivesAsReadOnly

	for internalPath, folder := range folders {
		userContainer.VolumeMounts = append(userContainer.VolumeMounts, core.VolumeMount{
			Name:      fsVolume,
			ReadOnly:  folder.ReadOnly,
			MountPath: folder.PodPath,
			SubPath:   folder.SubPath,
		})

		result[internalPath] = folder.PodPath
	}

	{
		var driveIds []string
		var driveAsReadOnly []bool

		for driveId, readOnly := range mountedDrivesAsReadOnly {
			driveIds = append(driveIds, driveId)
			driveAsReadOnly = append(driveAsReadOnly, readOnly)
		}

		driveIdsBytes, _ := json.Marshal(driveIds)
		driveReadOnlyBytes, _ := json.Marshal(driveAsReadOnly)

		pod.Annotations[shared.AnnotationMountedDriveIds] = string(driveIdsBytes)
		pod.Annotations[shared.AnnotationMountedDriveAsReadOnly] = string(driveReadOnlyBytes)
	}

	return result
}
