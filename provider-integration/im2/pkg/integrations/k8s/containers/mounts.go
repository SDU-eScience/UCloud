package containers

import (
	"encoding/json"
	"fmt"
	"path/filepath"
	"slices"
	"strings"

	core "k8s.io/api/core/v1"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/integrations/k8s/shared"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
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

type resolvedMount struct {
	SubPath    string
	ReadOnly   bool
	IsExplicit bool
}

func calculateMounts(job *orc.Job, internalJobFolder string) (mountResult, bool) {
	type ucloudMount struct {
		ReadOnly  bool
		MountPath string
	}

	ucloudMounts := map[string]ucloudMount{}

	for _, v := range job.Specification.Resources {
		if v.Type == orc.AppParameterValueTypeFile {
			ucloudMounts[v.Path] = ucloudMount{ReadOnly: v.ReadOnly, MountPath: v.MountPath}
		}
	}

	for _, v := range job.Specification.Parameters {
		if v.Type == orc.AppParameterValueTypeFile {
			ucloudMounts[v.Path] = ucloudMount{ReadOnly: v.ReadOnly, MountPath: v.MountPath}
		}
	}

	containerMountDir := "/work"

	// Container internal path to (potentially conflicting) mount sub-paths
	resolvedMounts := map[string][]resolvedMount{}
	hasMountPathConflict := false

	internalToSubpath := func(internalPath string) (string, bool) {
		subpath, ok := strings.CutPrefix(internalPath, filepath.Clean(ServiceConfig.FileSystem.MountPoint)+"/")
		if !ok {
			return "", false
		}

		return subpath, true
	}

	ucloudToSubpath := func(ucloudPath string) (string, bool) {
		path, ok, _ := filesystem.UCloudToInternal(ucloudPath)
		if !ok {
			return "", false
		}

		return internalToSubpath(path)
	}

	addMount := func(containerPath, subpath string, readOnly bool, isExplicit bool) {
		existing, _ := resolvedMounts[containerPath]

		hasExistingExplicit := false
		for _, mount := range existing {
			if mount.IsExplicit {
				hasExistingExplicit = true
				break
			}
		}

		if (isExplicit || hasExistingExplicit) && len(existing) > 0 {
			hasMountPathConflict = true
			return
		}

		existing = append(existing, resolvedMount{SubPath: subpath, ReadOnly: readOnly, IsExplicit: isExplicit})
		resolvedMounts[containerPath] = existing
	}

	addInternalMount := func(containerPath, internalPath string, readOnly bool, isExplicit bool) {
		sub, ok := internalToSubpath(internalPath)
		if ok {
			addMount(containerPath, sub, readOnly, isExplicit)
		}
	}

	var allUCloudPaths []string
	mountedDrivesAsReadOnly := map[string]bool{}
	addUCloudMount := func(containerPath, ucloudPath string, readOnly bool, isExplicit bool) {
		allUCloudPaths = append(allUCloudPaths, ucloudPath)

		sub, ok := ucloudToSubpath(ucloudPath)
		if ok {
			addMount(containerPath, sub, readOnly, isExplicit)
		}
	}

	addInternalMount(containerMountDir, internalJobFolder, false, false)

	for mountPath, mount := range ucloudMounts {
		comps := util.Components(mountPath)
		compsLen := len(comps)

		if compsLen == 0 {
			continue
		}

		alreadyMountedAsReadOnly, ok := mountedDrivesAsReadOnly[comps[0]]
		if ok && alreadyMountedAsReadOnly {
			mountedDrivesAsReadOnly[comps[0]] = mount.ReadOnly
		} else if !ok {
			mountedDrivesAsReadOnly[comps[0]] = mount.ReadOnly
		}

		title := comps[compsLen-1]

		if compsLen == 1 {
			drive, ok := filesystem.ResolveDrive(comps[0])
			if !ok {
				continue
			}

			title = strings.ReplaceAll(drive.Specification.Title, "Member Files: ", "")
		}

		containerPath := filepath.Join(containerMountDir, title)
		isExplicit := false

		if strings.TrimSpace(mount.MountPath) != "" {
			normalizedMountPath, ok := shared.ValidateFileMountPath(mount.MountPath)
			if !ok {
				return mountResult{}, false
			}

			containerPath = normalizedMountPath
			isExplicit = true
		}

		addUCloudMount(containerPath, mountPath, mount.ReadOnly, isExplicit)
		if hasMountPathConflict {
			return mountResult{}, false
		}
	}

	mountPaths := map[string]mountedFolder{}

	mountIdx := 0
	for containerPath, mounts := range resolvedMounts {
		if len(mounts) == 1 {
			mount := mounts[0]

			internalPath := ServiceConfig.FileSystem.MountPoint + "/" + mount.SubPath
			mountPaths[internalPath] = mountedFolder{
				InternalPath: internalPath,
				SubPath:      mount.SubPath,
				PodPath:      containerPath,
				ReadOnly:     mount.ReadOnly,
			}

			mountIdx++
		} else {
			// NOTE(Dan): Must remain consistent with VM mount logic for overall consistency
			slices.SortFunc(mounts, func(a, b resolvedMount) int {
				return strings.Compare(a.SubPath, b.SubPath)
			})

			for i, mount := range mounts {
				resolvedContainerPath := fmt.Sprintf("%s-%d", containerPath, i)

				internalPath := ServiceConfig.FileSystem.MountPoint + "/" + mount.SubPath
				mountPaths[internalPath] = mountedFolder{
					InternalPath: internalPath,
					SubPath:      mount.SubPath,
					PodPath:      resolvedContainerPath,
					ReadOnly:     mount.ReadOnly,
				}

				mountIdx++
			}
		}
	}

	if !filesystem.AllowUCloudPathsTogetherWithProjects(allUCloudPaths, []string{job.Owner.Project.Value}) {
		return mountResult{}, false
	}

	return mountResult{
		Folders:                 mountPaths,
		MountedDrivesAsReadOnly: mountedDrivesAsReadOnly,
	}, true
}

// prepareMountsOnJobCreate add relevant mounts into the pod and returns a mapping from internal paths (including the
// mount point) to their corresponding paths inside the container.
func prepareMountsOnJobCreate(
	job *orc.Job,
	pod *core.Pod,
	userContainer *core.Container,
	jobFolder string,
) (map[string]string, bool) {
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

	mounts, ok := calculateMounts(job, jobFolder)
	if !ok {
		return map[string]string{}, false
	}

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

	return result, true
}
