package k8s

import (
	"net/http"

	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/ipc"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"
)

var (
	requestScanIpc = ipc.NewCall[fnd.FindByStringId, util.Empty]("ctrl.drives.requestScan")
)

func writeHelp() {
	f := termio.Frame{}

	f.AppendTitle("storage-scan help")
	f.AppendField("", "Used to interact with storage scan functionality")
	f.AppendSeparator()

	f.AppendField("help", "Prints this help text")
	f.AppendSeparator()

	f.AppendField("scan-now", "Triggers a scan of a specific drive.")
	f.AppendField("", "")
	f.AppendField("  <driveId>", "The UCloud ID of the drive")
	f.AppendSeparator()

	f.Print()
}

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
	default:
		writeHelp()
	}
}
