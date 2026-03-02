package fsearch

import (
	"os"
	"path/filepath"
	"sync"
	"sync/atomic"
	"time"

	"github.com/MichaelTJones/walk"
)

type BuildIndexResult struct {
	Index                          *SearchIndex
	SuggestNewBucketCount          int
	TotalFileSize                  uint64
	TotalFileCount                 uint64
	EstimatedFilesIndexedPerSecond float64
}

func BuildIndex(bucketCount int, rootPath string) BuildIndexResult {
	startTime := time.Now()
	index := NewIndexBuilder(bucketCount)
	filesInIndex := atomic.Uint64{}
	fileSize := atomic.Uint64{}

	fc := make(chan PreparedFileInfo)
	baseInfoCh := make(chan FileInfo)

	prepWg := &sync.WaitGroup{}
	appendWg := &sync.WaitGroup{}

	workerCount := 1

	for i := 0; i < workerCount; i++ {
		prepWg.Add(1)
		go func() {
			for {
				item, ok := <-baseInfoCh
				if !ok {
					break
				}

				fc <- item.Prepare()
			}

			prepWg.Done()
		}()
	}

	appendWg.Add(1)
	go func() {
		for {
			item, ok := <-fc
			if !ok {
				break
			}

			index.Append(item)
		}
		appendWg.Done()
	}()

	cleanPath, _ := filepath.Abs(rootPath)
	cleanPath = filepath.Clean(cleanPath)

	_ = walk.Walk(cleanPath, workerCount, func(path string, info os.FileInfo, err error) error {
		if info != nil {
			filesInIndex.Add(1)
			fileSize.Add(uint64(max(0, info.Size())))
			baseInfoCh <- FileInfo{
				Parent: filepath.Dir(path),
				Name:   filepath.Base(path),
			}
		}

		return nil
	})

	close(baseInfoCh)
	prepWg.Wait()
	close(fc)
	appendWg.Wait()

	endTime := time.Now()

	const desiredBucketSize = 256.0
	bucketSize := index.BuilderAvgBucketSize()
	factor := bucketSize / desiredBucketSize

	return BuildIndexResult{
		Index:                          index,
		SuggestNewBucketCount:          min(MaxBucketsPerIndex, max(16, int(float64(bucketCount)*factor))),
		TotalFileSize:                  fileSize.Load(),
		TotalFileCount:                 filesInIndex.Load(),
		EstimatedFilesIndexedPerSecond: float64(filesInIndex.Load()) / endTime.Sub(startTime).Seconds(),
	}
}

const MaxBucketsPerIndex = 1024 * 16
