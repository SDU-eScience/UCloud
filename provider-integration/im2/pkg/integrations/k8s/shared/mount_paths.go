package shared

import (
	"fmt"
	"path/filepath"
	"slices"
	"strings"

	"ucloud.dk/pkg/controller"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func ValidateFileMountPath(path string) (string, bool) {
	return orc.ValidateFileMountPath(path)
}

func ValidateExplicitFileMountPaths(values []orc.AppParameterValue) (bool, string) {
	return orc.ValidateExplicitFileMountPaths(values)
}

type ResolvedJobMount struct {
	ContainerPath string
	UCloudPath    string
	ReadOnly      bool
}

func ResolveJobMounts(job *orc.Job) ([]ResolvedJobMount, bool) {
	type ucloudMount struct {
		MountPath string
		ReadOnly  bool
	}

	type resolvedMount struct {
		UCloudPath string
		IsExplicit bool
		ReadOnly   bool
	}

	ucloudMounts := map[string]ucloudMount{}
	for _, value := range job.Specification.Resources {
		if value.Type == orc.AppParameterValueTypeFile {
			existing, hasExisting := ucloudMounts[value.Path]
			if hasExisting {
				if existing.ReadOnly {
					existing.ReadOnly = value.ReadOnly
					ucloudMounts[value.Path] = existing
				}
			} else {
				ucloudMounts[value.Path] = ucloudMount{MountPath: value.MountPath, ReadOnly: value.ReadOnly}
			}
		}
	}
	for _, value := range job.Specification.Parameters {
		if value.Type == orc.AppParameterValueTypeFile {
			existing, hasExisting := ucloudMounts[value.Path]
			if hasExisting {
				if existing.ReadOnly {
					existing.ReadOnly = value.ReadOnly
					ucloudMounts[value.Path] = existing
				}
			} else {
				ucloudMounts[value.Path] = ucloudMount{MountPath: value.MountPath, ReadOnly: value.ReadOnly}
			}
		}
	}

	resolvedMounts := map[string][]resolvedMount{}
	addMount := func(mountPath string, mount resolvedMount) bool {
		existing := resolvedMounts[mountPath]

		hasExistingExplicit := false
		for _, existingMount := range existing {
			if existingMount.IsExplicit {
				hasExistingExplicit = true
				break
			}
		}

		if (mount.IsExplicit || hasExistingExplicit) && len(existing) > 0 {
			return false
		}

		resolvedMounts[mountPath] = append(existing, mount)
		return true
	}

	for ucloudPath, mount := range ucloudMounts {
		components := util.Components(ucloudPath)
		if len(components) == 0 {
			continue
		}

		title := components[len(components)-1]
		if len(components) == 1 {
			drive, ok := controller.DriveRetrieve(components[0])
			if !ok {
				continue
			}

			title = strings.ReplaceAll(drive.Specification.Title, "Member Files: ", "")
		}

		mountPath := filepath.Join("/work", title)
		isExplicit := false
		if strings.TrimSpace(mount.MountPath) != "" {
			normalizedMountPath, ok := ValidateFileMountPath(mount.MountPath)
			if !ok {
				return nil, false
			}

			mountPath = normalizedMountPath
			isExplicit = true
		}

		ok := addMount(mountPath, resolvedMount{
			UCloudPath: filepath.Clean(ucloudPath),
			IsExplicit: isExplicit,
			ReadOnly:   mount.ReadOnly,
		})
		if !ok {
			return nil, false
		}
	}

	var finalMounts []ResolvedJobMount
	for mountPath, mounts := range resolvedMounts {
		if len(mounts) == 1 {
			finalMounts = append(
				finalMounts,
				ResolvedJobMount{
					ContainerPath: mountPath,
					UCloudPath:    mounts[0].UCloudPath,
					ReadOnly:      mounts[0].ReadOnly,
				},
			)
			continue
		}

		slices.SortFunc(mounts, func(a, b resolvedMount) int {
			return strings.Compare(a.UCloudPath, b.UCloudPath)
		})

		for i, mount := range mounts {
			finalMounts = append(finalMounts, ResolvedJobMount{
				ContainerPath: fmt.Sprintf("%s-%d", mountPath, i),
				UCloudPath:    mount.UCloudPath,
				ReadOnly:      mount.ReadOnly,
			})
		}
	}

	slices.SortFunc(finalMounts, func(a, b ResolvedJobMount) int {
		return len(b.ContainerPath) - len(a.ContainerPath)
	})
	return finalMounts, true
}

func ContainerPathToUCloudFileMount(job *orc.Job, path string) (string, bool) {
	containerPath := filepath.Clean(path)
	if !filepath.IsAbs(containerPath) {
		return "", false
	}

	finalMounts, ok := ResolveJobMounts(job)
	if !ok {
		return "", false
	}

	for _, mount := range finalMounts {
		if containerPath == mount.ContainerPath {
			return mount.UCloudPath, true
		}

		relative, err := filepath.Rel(mount.ContainerPath, containerPath)
		if err != nil || relative == "." || strings.HasPrefix(relative, "..") || filepath.IsAbs(relative) {
			continue
		}

		return filepath.Join(mount.UCloudPath, relative), true
	}

	return "", false
}
