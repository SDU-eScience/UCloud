package filesystem

import (
	"context"
	"flag"
	"fmt"
	"net/http"
	"os"
	"time"

	"ucloud.dk/pkg/ipc"
	"ucloud.dk/shared/pkg/cli"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"
)

type metadataCliPathRequest struct {
	Path string
}

type metadataCliSearchRequest struct {
	Path   string
	Prefix string
	Limit  int
}

type metadataCliDriveRequest struct {
	DriveID string
}

var (
	metadataCliScan    = ipc.NewCall[metadataCliPathRequest, util.Empty]("cli.k8s.metadata.scan")
	metadataCliStats   = ipc.NewCall[metadataCliPathRequest, MetadataDirectoryStats]("cli.k8s.metadata.stats")
	metadataCliSearch  = ipc.NewCall[metadataCliSearchRequest, MetadataSearchResponse]("cli.k8s.metadata.search")
	metadataCliMetrics = ipc.NewCall[metadataCliDriveRequest, MetadataCatalogMetrics]("cli.k8s.metadata.metrics")
)

func InitMetadataCli() {
	metadataCliScan.Handler(func(request *ipc.Request[metadataCliPathRequest]) ipc.Response[util.Empty] {
		if request.Uid != 0 {
			return metadataCliForbidden[util.Empty]()
		}
		if _, ok, _ := UCloudToInternal(request.Payload.Path); !ok {
			return metadataCliError[util.Empty](http.StatusBadRequest, "Invalid UCloud path")
		}
		MetadataSubmitScanRequest(request.Payload.Path)
		return ipc.Response[util.Empty]{StatusCode: http.StatusAccepted}
	})

	metadataCliStats.Handler(func(request *ipc.Request[metadataCliPathRequest]) ipc.Response[MetadataDirectoryStats] {
		if request.Uid != 0 {
			return metadataCliForbidden[MetadataDirectoryStats]()
		}
		if _, ok, _ := UCloudToInternal(request.Payload.Path); !ok {
			return metadataCliError[MetadataDirectoryStats](http.StatusBadRequest, "Invalid UCloud path")
		}
		stats, found, err := MetadataLookupDirectoryStats(request.Payload.Path)
		if err != nil {
			return metadataCliError[MetadataDirectoryStats](http.StatusInternalServerError, err.Error())
		}
		if !found {
			return metadataCliError[MetadataDirectoryStats](http.StatusNotFound, "Path is not present in the metadata catalog")
		}
		return ipc.Response[MetadataDirectoryStats]{StatusCode: http.StatusOK, Payload: stats}
	})

	metadataCliSearch.Handler(func(request *ipc.Request[metadataCliSearchRequest]) ipc.Response[MetadataSearchResponse] {
		if request.Uid != 0 {
			return metadataCliForbidden[MetadataSearchResponse]()
		}
		if request.Payload.Limit <= 0 {
			return metadataCliError[MetadataSearchResponse](http.StatusBadRequest, "Search limit must be positive")
		}
		if _, ok, _ := UCloudToInternal(request.Payload.Path); !ok {
			return metadataCliError[MetadataSearchResponse](http.StatusNotFound, "Unknown UCloud path")
		}
		response := MetadataSearchResponse{Results: []MetadataSearchResult{}}
		complete, err := MetadataSearchByNamePrefix(context.Background(), request.Payload.Path, request.Payload.Prefix, request.Payload.Limit, func(result MetadataSearchResult) bool {
			response.Results = append(response.Results, result)
			return true
		})
		if err != nil {
			return metadataCliError[MetadataSearchResponse](http.StatusInternalServerError, err.Error())
		}
		response.Complete = complete
		return ipc.Response[MetadataSearchResponse]{StatusCode: http.StatusOK, Payload: response}
	})

	metadataCliMetrics.Handler(func(request *ipc.Request[metadataCliDriveRequest]) ipc.Response[MetadataCatalogMetrics] {
		if request.Uid != 0 {
			return metadataCliForbidden[MetadataCatalogMetrics]()
		}
		if _, ok := ResolveDrive(request.Payload.DriveID); !ok {
			return metadataCliError[MetadataCatalogMetrics](http.StatusNotFound, "Unknown drive")
		}
		metrics, err := MetadataCatalogMetricsForDrive(request.Payload.DriveID)
		if err != nil {
			return metadataCliError[MetadataCatalogMetrics](http.StatusInternalServerError, err.Error())
		}
		return ipc.Response[MetadataCatalogMetrics]{StatusCode: http.StatusOK, Payload: metrics}
	})
}

func MetadataCli(args []string) {
	if os.Getuid() != 0 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "This command must be run as root!")
		return
	}
	if len(args) == 0 {
		metadataCliHelp()
		return
	}

	switch args[0] {
	case "scan":
		if len(args) < 2 {
			metadataCliMissing("UCloud path")
			return
		}
		_, err := metadataCliScan.Invoke(metadataCliPathRequest{Path: args[1]})
		cli.HandleError("submitting metadata scan", err)
		termio.WriteLine("Scan submitted for %s", args[1])

	case "stats":
		if len(args) < 2 {
			metadataCliMissing("UCloud path")
			return
		}
		stats, err := metadataCliStats.Invoke(metadataCliPathRequest{Path: args[1]})
		cli.HandleError("retrieving metadata directory stats", err)
		frame := termio.Frame{}
		frame.AppendTitle("Metadata directory stats")
		frame.AppendField("Path", stats.Path)
		frame.AppendField("Logical size", metadataCliFormatBytes(stats.LogicalBytes))
		frame.AppendField("Allocated size", metadataCliFormatBytes(stats.AllocatedBytes))
		frame.AppendField("Files", fmt.Sprintf("%d", stats.FileCount))
		frame.AppendField("Directories", fmt.Sprintf("%d", stats.DirectoryCount))
		frame.AppendField("Observed", metadataCliFormatTimestamp(stats.AggregateObservedAtNano))
		frame.AppendField("Entry generation", fmt.Sprintf("%d", stats.EntryGeneration))
		frame.AppendField("Aggregate generation", fmt.Sprintf("%d", stats.AggregateGeneration))
		frame.AppendField("Complete drive coverage", fmt.Sprintf("%t", stats.CompleteCoverage))
		frame.Print()

	case "search":
		if len(args) < 3 {
			metadataCliMissing("UCloud folder path and basename prefix")
			return
		}
		request := metadataCliSearchRequest{Path: args[1], Prefix: args[2], Limit: 100}
		flags := flag.NewFlagSet("metadata search", flag.ExitOnError)
		flags.IntVar(&request.Limit, "limit", request.Limit, "Maximum number of results")
		_ = flags.Parse(args[3:])
		response, err := metadataCliSearch.Invoke(request)
		cli.HandleError("searching metadata catalog", err)
		table := termio.Table{}
		table.AppendHeader("Path")
		table.AppendHeader("Type")
		table.AppendHeader("Logical size")
		table.AppendHeader("Modified")
		for _, result := range response.Results {
			table.Cell("%s", result.Path)
			table.Cell("%s", metadataCliEntryType(result.Entry.EntryType))
			table.Cell("%s", metadataCliFormatBytes(result.Entry.LogicalSize))
			table.Cell("%s", metadataCliFormatTimestamp(result.Entry.ModificationTime))
		}
		table.Print()
		if !response.Complete {
			termio.WriteStyledLine(termio.Bold, termio.Yellow, 0, "NAME refresh is pending. Search results may be incomplete.")
		}

	case "metrics":
		if len(args) < 2 {
			metadataCliMissing("drive ID")
			return
		}
		metrics, err := metadataCliMetrics.Invoke(metadataCliDriveRequest{DriveID: args[1]})
		cli.HandleError("retrieving metadata catalog metrics", err)
		metadataCliPrintMetrics(metrics)

	default:
		metadataCliHelp()
	}
}

func metadataCliPrintMetrics(metrics MetadataCatalogMetrics) {
	frame := termio.Frame{}
	frame.AppendTitle("Metadata catalog metrics")
	frame.AppendField("Drive", metrics.DriveID)
	frame.AppendField("Database size", metadataCliFormatBytes(metrics.DatabaseBytes))
	frame.AppendField("Estimated PATH keyspace size", metadataCliFormatBytes(metrics.DatabasePathBytes))
	frame.AppendField("Estimated NAME keyspace size", metadataCliFormatBytes(metrics.DatabaseNameBytes))
	frame.AppendField("Scans submitted", fmt.Sprintf("%d", metrics.ScansSubmitted))
	frame.AppendField("Scans in progress", fmt.Sprintf("%d", metrics.ScansInProgress))
	frame.AppendField("Scans completed", fmt.Sprintf("%d", metrics.ScansCompleted))
	frame.AppendField("Scans failed", fmt.Sprintf("%d", metrics.ScansFailed))
	frame.AppendField("NAME refreshes in progress", fmt.Sprintf("%d", metrics.NameRefreshesInProgress))
	frame.AppendField("NAME refreshes failed", fmt.Sprintf("%d", metrics.NameRefreshesFailed))
	frame.AppendField("Last NAME refresh status", metrics.LastNameRefreshStatus)
	frame.AppendField("Last scan status", metrics.LastScanStatus)
	frame.AppendField("Last attempt duration", time.Duration(metrics.LastScanDurationNanos).String())
	frame.AppendField("Last successful scan", metadataCliFormatTimestamp(metrics.LastScanCompletedAtNano))
	frame.AppendField("Last successful files", fmt.Sprintf("%d", metrics.LastFilesScanned))
	frame.AppendField("Last successful directories", fmt.Sprintf("%d", metrics.LastDirectoriesScanned))
	frame.AppendField("Last successful scan rate", fmt.Sprintf("%.2f entries/s", metrics.LastScanEntriesPerSecond))
	frame.AppendField("Last successful logical size", metadataCliFormatBytes(metrics.LastLogicalBytesScanned))
	frame.AppendField("Last successful allocated size", metadataCliFormatBytes(metrics.LastAllocatedBytesScanned))
	frame.AppendField("Total files scanned", fmt.Sprintf("%d", metrics.TotalFilesScanned))
	frame.AppendField("Total directories scanned", fmt.Sprintf("%d", metrics.TotalDirectoriesScanned))
	frame.AppendField("Total logical size scanned", metadataCliFormatBytes(metrics.TotalLogicalBytesScanned))
	frame.AppendField("Queries completed", fmt.Sprintf("%d", metrics.QueriesCompleted))
	frame.AppendField("Queries failed", fmt.Sprintf("%d", metrics.QueriesFailed))
	frame.AppendField("Total query time", time.Duration(metrics.TotalQueryDurationNanos).String())
	frame.Print()
}

func metadataCliHelp() {
	frame := termio.Frame{}
	frame.AppendTitle("metadata help")
	frame.AppendField("metadata scan <UCloud path>", "Submit a metadata scan")
	frame.AppendField("metadata stats <UCloud path>", "Show recursive directory statistics")
	frame.AppendField("metadata search <UCloud folder path> <prefix> [--limit N]", "Search normalized basenames")
	frame.AppendField("metadata metrics <drive ID>", "Show scan, query, and database metrics")
	frame.Print()
}

func metadataCliMissing(parameter string) {
	termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Missing parameter: %s", parameter)
}

func metadataCliForbidden[T any]() ipc.Response[T] {
	return metadataCliError[T](http.StatusForbidden, "Must be run as root")
}

func metadataCliError[T any](status int, message string) ipc.Response[T] {
	return ipc.Response[T]{StatusCode: status, ErrorMessage: message}
}

func metadataCliEntryType(entryType MetaEntryType) string {
	switch entryType {
	case MetaEntryRegular:
		return "file"
	case MetaEntryDirectory:
		return "directory"
	case MetaEntrySymlink:
		return "symlink"
	default:
		return "other"
	}
}

func metadataCliFormatBytes(value uint64) string {
	const unit = 1000
	if value < unit {
		return fmt.Sprintf("%d B", value)
	}
	divisor := float64(unit)
	suffix := "KB"
	for _, next := range []string{"MB", "GB", "TB", "PB", "EB"} {
		if value < uint64(divisor*unit) {
			break
		}
		divisor *= unit
		suffix = next
	}
	return fmt.Sprintf("%.2f %s", float64(value)/divisor, suffix)
}

func metadataCliFormatTimestamp(unixNano int64) string {
	if unixNano == 0 {
		return "never"
	}
	return time.Unix(0, unixNano).Format(time.RFC3339Nano)
}
