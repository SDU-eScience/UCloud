package main

import (
	"os"
	"path/filepath"
	"strconv"

	"ucloud.dk/iapp/k8s/pkg/creator"
	"ucloud.dk/iapp/k8s/pkg/dashboard"
	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

var dashboardMarker = filepath.Join(shared.ManagementMountPath, filepath.Base(shared.ClusterRecordPath))

func main() {
	port := util.OptNone[int]()
	if len(os.Args) >= 2 {
		converted, err := strconv.Atoi(os.Args[1])
		if err == nil {
			port.Set(converted)
		}
	}

	if os.Getenv("UCX_PORT") != "" {
		converted, err := strconv.Atoi(os.Getenv("UCX_PORT"))
		if err == nil {
			port.Set(converted)
		}
	}

	ucx.AppServe(launch, port)
}

func launch() ucx.Application {
	if os.Getenv("UCX_PORT") != "" {
		if _, err := os.Stat(dashboardMarker); err == nil {
			dashboard.WaitForFunctionalCluster()
			return dashboard.App()
		}
	}

	return creator.App()
}
