package filesystem

import (
	"context"
	"golang.org/x/sync/semaphore"
	"runtime"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

func loopMonitoring() {

}

var driveScanQueue chan orc.Drive
var scanSemaphore *semaphore.Weighted

func initScanQueue() {
	driveScanQueue = make(chan orc.Drive)
	scanSemaphore = semaphore.NewWeighted(max(4, int64(runtime.NumCPU())))

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

			go scanDrive(driveToScan)
		}
	}()
}

func scanDrive(drive orc.Drive) {

}
