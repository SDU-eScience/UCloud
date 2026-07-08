package shared

import (
	"path/filepath"
	"strings"

	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const UcxCacheMountPath = "/opt/ucloud/ucx-cache"

type UcxCacheMount struct {
	SubPath   string
	MountPath string
}

func UcxCacheMountForJob(job *orc.Job) util.Option[UcxCacheMount] {
	if job == nil || !job.Status.ResolvedApplication.Present {
		return util.OptNone[UcxCacheMount]()
	}

	app := job.Status.ResolvedApplication.Value
	if !app.Invocation.Ucx.Present || !app.Invocation.Ucx.Value.Executable.Present {
		return util.OptNone[UcxCacheMount]()
	}

	cacheDir := filepath.Join(ServiceConfig.FileSystem.MountPoint, "ucloud-ucx")
	subPath, ok := strings.CutPrefix(filepath.Clean(cacheDir), filepath.Clean(ServiceConfig.FileSystem.MountPoint)+"/")
	if !ok {
		return util.OptNone[UcxCacheMount]()
	}

	return util.OptValue(UcxCacheMount{
		SubPath:   subPath,
		MountPath: UcxCacheMountPath,
	})
}
