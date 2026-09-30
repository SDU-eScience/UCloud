package main

import (
	"context"
	"os"
	"path/filepath"
	"strconv"

	"ucloud.dk/iapp/k8s/pkg/controller"
	"ucloud.dk/iapp/k8s/pkg/creator"
	"ucloud.dk/iapp/k8s/pkg/dashboard"
	"ucloud.dk/iapp/k8s/pkg/maintenance"
	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

var dashboardMarker = filepath.Join(shared.ManagementMountPath, filepath.Base(shared.ClusterRecordPath))

func main() {
	if len(os.Args) >= 2 && os.Args[1] == "controller" {
		controller.Launch()
		return
	}

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

	if os.Getenv("UCX_PORT") != "" {
		if _, err := os.Stat(dashboardMarker); err == nil {
			go maintenance.MaintenanceRun(context.Background(), dashboard.LocalKubeconfigPath())
			ucx.AppServe(launch, port)
			return
		}
	}

	ucx.AppServe(launch, port)
}

func launch() ucx.Application {
	if os.Getenv("UCX_PORT") != "" {
		if _, err := os.Stat(dashboardMarker); err == nil {
			return dashboard.App()
		}
	}

	return creator.App()
}
