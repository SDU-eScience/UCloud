package shared

import (
	"os"
	"path/filepath"
	"strings"

	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const UcxCacheMountPath = "/opt/ucloud-ucx"

type UcxCacheMount struct {
	SubPath   string
	MountPath string
}

func UcxCacheMountForJob(job *orc.Job) util.Option[UcxCacheMount] {
	if job == nil {
		return util.OptNone[UcxCacheMount]()
	}

	if mount := ucxCacheMountForAppNameAndVersion(
		job.Specification.Labels["ucloud.dk/ucxappname"],
		job.Specification.Labels["ucloud.dk/ucxappversion"],
	); mount.Present {
		return mount
	}

	if job.Status.ResolvedApplication.Present {
		app := &job.Status.ResolvedApplication.Value
		if mount := ucxCacheMountForApp(app); mount.Present {
			return mount
		}
	}

	return util.OptNone[UcxCacheMount]()
}

func ucxCacheMountForApp(app *orc.Application) util.Option[UcxCacheMount] {
	if !app.Invocation.Ucx.Present || !app.Invocation.Ucx.Value.Executable.Present {
		return util.OptNone[UcxCacheMount]()
	}

	return ucxCacheMountForAppNameAndVersion(app.Metadata.Name, app.Metadata.Version)
}

func ucxCacheMountForAppNameAndVersion(appName string, appVersion string) util.Option[UcxCacheMount] {
	appName = strings.TrimSpace(appName)
	appVersion = strings.TrimSpace(appVersion)
	if !validUcxCachePathComponent(appName) || !validUcxCachePathComponent(appVersion) {
		return util.OptNone[UcxCacheMount]()
	}

	cacheDir := filepath.Join(ServiceConfig.FileSystem.MountPoint, "ucloud-ucx", "apps", appName, appVersion)
	if err := os.MkdirAll(cacheDir, 0755); err != nil {
		return util.OptNone[UcxCacheMount]()
	}

	subPath, ok := strings.CutPrefix(filepath.Clean(cacheDir), filepath.Clean(ServiceConfig.FileSystem.MountPoint)+"/")
	if !ok {
		return util.OptNone[UcxCacheMount]()
	}

	return util.OptValue(UcxCacheMount{
		SubPath:   subPath,
		MountPath: UcxCacheMountPath,
	})
}

func validUcxCachePathComponent(value string) bool {
	return value != "" && value == filepath.Base(value) && value != "." && value != ".."
}
