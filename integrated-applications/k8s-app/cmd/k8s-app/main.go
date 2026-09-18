package main

import (
	"os"
	"strconv"

	"ucloud.dk/iapp/k8s/pkg/creator"
	"ucloud.dk/iapp/k8s/pkg/dashboard"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

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
	if _, err := os.Stat("/etc/ucloud-stack"); err == nil {
		if os.Getenv("UCX_PORT") != "" {
			return dashboard.App()
		}
	}

	return creator.App()
}
