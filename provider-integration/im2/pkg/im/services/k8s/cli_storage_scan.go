package k8s

import (
	"net/http"

	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/im/services/k8s/filesystem"
	"ucloud.dk/pkg/termio"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/util"
)

var (
	requestScanIpc = ipc.NewCall[fnd.FindByStringId, util.Empty]("ctrl.drives.requestScan")
	reportUsageIpc = ipc.NewCall[StorageScanReportUsage, util.Empty]("ctrl.drives.reportUsage")
)

func initStorageScanCli() {
	requestScanIpc.Handler(func(r *ipc.Request[fnd.FindByStringId]) ipc.Response[util.Empty] {
		if r.Uid != 0 {
			return ipc.Response[util.Empty]{
				StatusCode:   http.StatusForbidden,
				ErrorMessage: "Must be run as root",
			}
		}

		filesystem.RequestScan(r.Payload.Id)

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
		}
	})

	reportUsageIpc.Handler(func(r *ipc.Request[StorageScanReportUsage]) ipc.Response[util.Empty] {
		if r.Uid != 0 {
			return ipc.Response[util.Empty]{
				StatusCode:   http.StatusForbidden,
				ErrorMessage: "Must be run as root",
			}
		}

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
		}
	})
}

func StorageScanCli(args []string) {
	if len(args) == 0 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Unknown command")
		return
	}

	switch {
	case args[0] == "scan-now":
		if len(args) < 2 {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Missing parameter: drive id")
			return
		}

		driveId := args[1]
		requestScanIpc.Invoke(fnd.FindByStringId{Id: driveId})
	case args[0] == "report-usage":
		reportUsageIpc.Invoke(StorageScanReportUsage{})
	}
}

type StorageScanReportUsage struct {
	workspace       string
	productName     string
	productCategory string
	driveId         int64
	usageInBytes    int64
}
