package filesystem

import (
	"context"
	"fmt"
	"io"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync/atomic"
	"time"

	"github.com/prometheus/client_golang/prometheus/promauto"

	"github.com/prometheus/client_golang/prometheus"
	"golang.org/x/sync/semaphore"
	"golang.org/x/sys/unix"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/controller/fsearch"
	"ucloud.dk/pkg/im/services/k8s/shared"
	"ucloud.dk/shared/pkg/apm"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var driveScanQueue chan orc.Drive
var scanSemaphore *semaphore.Weighted
var scanSlotsAvailable = atomic.Int64{}
var maxScanSlots int64

var (
	metricScansInProgress = promauto.NewGauge(prometheus.GaugeOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "storage",
		Name:      "scans_in_progress",
		Help:      "The total number of drives currently being scanned by UCloud/IM",
	})

	timeSpentIndexingSeconds = promauto.NewCounter(prometheus.CounterOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "storage",
		Name:      "time_spent_indexing_seconds",
		Help:      "Time spent indexing",
	})

	indexingBucketLive = promauto.NewGauge(prometheus.GaugeOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "storage",
		Name:      "indexing_live_buckets",
		Help:      "Total number of live buckets used for indexing",
	})

	totalFilesIndexed = promauto.NewCounter(prometheus.CounterOpts{
		Namespace: "ucloud_im_k8s",
		Subsystem: "storage",
		Name:      "total_files_indexed",
		Help:      "Total number of files indexed",
	})
)

func loopMonitoring() time.Duration {
	if scanSlotsAvailable.Load() > 0 {
		// NOTE(Dan): We use scanSlotsAvailable to try and limit the amount of drives above the processing limit
		// we submit. We are _not_ trying to submit less than the actual limit, we are just trying to cap how big the
		// queue is likely to get. This could be done purely with the channels, but we also have the database to worry
		// about so this seems like the better solution.

		drivesToScan := ctrl.RetrieveDrivesNeedingScan()
		for _, drive := range drivesToScan {
			if drive.Specification.Product.Provider != cfg.Provider.Id {
				continue
			}

			driveScanQueue <- drive
		}
	}

	metricScansInProgress.Set(float64(maxScanSlots - scanSlotsAvailable.Load()))

	return 1 * time.Second
}

func initScanQueue() {
	driveScanQueue = make(chan orc.Drive)
	maxScanSlots = min(8, int64(runtime.NumCPU()))
	scanSemaphore = semaphore.NewWeighted(maxScanSlots)
	scanSlotsAvailable.Store(maxScanSlots)

	go func() {
		for util.IsAlive {
			driveToScan, ok := <-driveScanQueue
			if !ok {
				break
			}

			err := scanSemaphore.Acquire(context.Background(), 1)
			if err != nil {
				log.Warn("scanSemaphore should never return an error but did: %s", err)
				break
			}

			scanSlotsAvailable.Add(-1)
			go scanDrive(driveToScan)
		}
	}()
}

func scanDrive(drive orc.Drive) {
	defer scanSemaphore.Release(1)
	defer scanSlotsAvailable.Add(1)

	internalPath, ok, _ := DriveToLocalPath(&drive)
	if !ok {
		return
	}

	sizeToReport := int64(0)

	method := shared.ServiceConfig.FileSystem.ScanMethod
	switch method.Type {
	case cfg.K8sScanMethodTypeExtendedAttribute:
		fd, ok := OpenFile(internalPath, unix.O_RDONLY, 0)
		buffer := make([]byte, 64)
		if !ok {
			log.Info("Could not open drive for reporting %v", drive.Id)
			return
		}
		defer util.SilentClose(fd)

		count, err := unix.Fgetxattr(int(fd.Fd()), method.ExtendedAttribute, buffer)
		if err != nil {
			log.Info("Could not read extended attribute of drive for reporting %v: %s", drive.Id, err)
			return
		}

		size, err := strconv.ParseInt(strings.TrimSpace(string(buffer[:count])), 10, 64)
		if err != nil {
			log.Info("Could not read extended attribute of drive for reporting %v: %s", drive.Id, err)
			return
		}

		sizeToReport = size

	case cfg.K8sScanMethodTypeDevFile:
		fd, ok := OpenFile(filepath.Join(internalPath, ".usage"), unix.O_RDONLY, 0)
		if !ok {
			log.Info("Could not open drive for reporting %v", drive.Id)
			return
		}
		defer util.SilentClose(fd)

		data, err := io.ReadAll(fd)
		if err != nil {
			log.Info("Could not read extended attribute of drive for reporting %v: %s", drive.Id, err)
			return
		}

		size, err := strconv.ParseInt(strings.TrimSpace(string(data)), 10, 64)
		if err != nil {
			log.Info("Could not read extended attribute of drive for reporting %v: %s", drive.Id, err)
			return
		}

		sizeToReport = size

	case cfg.K8sScanMethodTypeWalk:
		start := time.Now()

		bucketCount := ctrl.LoadNextSearchBucketCount(drive.Id)
		indexingBucketLive.Add(float64(bucketCount))
		result := fsearch.BuildIndex(bucketCount, internalPath)
		indexingBucketLive.Sub(float64(bucketCount))

		end := time.Now()
		indexingDurationSeconds := end.Sub(start).Seconds()

		timeSpentIndexingSeconds.Add(indexingDurationSeconds)
		totalFilesIndexed.Add(float64(result.TotalFileCount))
		ctrl.TrackSearchIndex(drive.Id, result.Index, result.SuggestNewBucketCount)

		sizeToReport = int64(result.TotalFileSize)
	}

	// NOTE(Dan): There are no configuration options on the product at the moment to change this to anything else.
	sizeInGb := sizeToReport / 1000000000

	reportUsedStorage(drive, sizeInGb)

	ctrl.UpdateDriveScannedAt(drive.Id)
}

func reportUsedStorage(drive orc.Drive, sizeInGb int64) {
	request := fnd.BulkRequest[apm.UsageReportItem]{
		Items: []apm.UsageReportItem{
			{
				IsDeltaCharge: false,
				Owner:         apm.WalletOwnerFromIds(drive.Owner.CreatedBy, drive.Owner.Project),
				CategoryIdV2: apm.ProductCategoryIdV2{
					Name:     drive.Specification.Product.Category,
					Provider: cfg.Provider.Id,
				},
				Usage: sizeInGb,
				Description: apm.ChargeDescription{
					Scope: util.OptValue(fmt.Sprintf("drive-%v-%v", cfg.Provider.Id, drive.Id)),
				},
			},
		},
	}

	util.RetryOrPanic("report storage", func() (util.Empty, error) {
		_, err := apm.ReportUsage(request)
		return util.EmptyValue, err
	})
}

func RequestScan(driveId string) {
	// NOTE(Dan): This doesn't currently set the submitted_at timestamp on purpose. If it turns out that this is a bad
	// idea then it should be changed.
	drive, ok := ctrl.RetrieveDrive(driveId)
	if ok {
		driveScanQueue <- *drive
	}
}
