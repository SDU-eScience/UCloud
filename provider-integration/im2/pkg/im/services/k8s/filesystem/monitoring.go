package filesystem

import (
	"context"
	"fmt"
	"github.com/prometheus/client_golang/prometheus"
	"golang.org/x/sync/semaphore"
	"golang.org/x/sys/unix"
	"runtime"
	"strconv"
	"sync"
	"sync/atomic"
	"time"
	"ucloud.dk/pkg/apm"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/k8s/shared"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var driveScanQueue chan orc.Drive
var scanSemaphore *semaphore.Weighted
var scanSlotsAvailable = atomic.Int64{}
var maxScanSlots int64

var (
	metricScansInProgress = prometheus.NewGauge(prometheus.GaugeOpts{
		Name: "ucloud_im_k8s_storage_scans_in_progress",
		Help: "The total number of drives currently being scanned by UCloud/IM",
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
			driveScanQueue <- drive
		}
	}

	metricScansInProgress.Set(float64(maxScanSlots - scanSlotsAvailable.Load()))

	return 1 * time.Second
}

func initScanQueue() {
	driveScanQueue = make(chan orc.Drive)
	maxScanSlots = max(4, int64(runtime.NumCPU()))
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

	internalPath, ok := DriveToLocalPath(&drive)
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

		size, err := strconv.ParseInt(string(buffer[:count]), 10, 64)
		if err != nil {
			log.Info("Could not read extended attribute of drive for reporting %v: %s", drive.Id, err)
			return
		}

		sizeToReport = size

	case cfg.K8sScanMethodTypeWalk:
		fd, ok := OpenFile(internalPath, unix.O_RDONLY, 0)
		if !ok {
			log.Info("Could not open drive for reporting %v", drive.Id)
			return
		}
		defer util.SilentClose(fd)
		fdStat, err := fd.Stat()
		if err != nil {
			log.Info("Could not open drive for reporting %v", drive.Id)
			return
		}

		files := make(chan discoveredFile)
		ctx := context.Background()

		wg := sync.WaitGroup{}
		go func() {
			wg.Add(1)
			defer wg.Done()

		outer:
			for {
				select {
				case <-ctx.Done():
					break outer
				case f, ok := <-files:
					if !ok {
						break outer
					}
					if f.InternalPath != "" {
						util.SilentClose(f.FileDescriptor)
					}

					sizeToReport += f.FileInfo.Size()
				}
			}
		}()

		normalFileWalk(ctx, files, fd, fdStat)
		close(files)
		wg.Wait()
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
					// TODO Migration: This is a different ID and will require a reset.
					Scope: util.OptValue(fmt.Sprintf("drive-%v", drive.Id)),
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
