package fsearch

import (
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"sync"
	"sync/atomic"
	"time"

	"github.com/MichaelTJones/walk"
)

func CliMain(q string, loadIndex bool, bucketCount int) {
	Init()
	start := time.Now()

	cwd, _ := filepath.Abs(".")
	cwd = filepath.Clean(cwd)
	filesInIndex := atomic.Int64{}
	naiveSize := atomic.Int64{}
	var bin []byte

	var idx *SearchIndex

	if !loadIndex {
		idx = NewIndexBuilder(bucketCount)

		fc := make(chan PreparedFileInfo)
		baseInfoCh := make(chan FileInfo)

		prepWg := &sync.WaitGroup{}
		appendWg := &sync.WaitGroup{}

		for i := 0; i < 16; i++ {
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
			count := 0
			ts := time.Now()
			for {
				item, ok := <-fc
				if !ok {
					break
				}

				idx.Append(item)

				count++

				if count%10000 == 0 {
					newTs := time.Now()
					itemsPerSecond := 10000 / newTs.Sub(ts).Seconds()
					ts = newTs
					fmt.Printf("%v files indexed (%.2f items per second)\n", count, itemsPerSecond)
				}
			}
			appendWg.Done()
		}()

		_ = walk.Walk(cwd, runtime.NumCPU(), func(path string, info os.FileInfo, err error) error {
			filesInIndex.Add(1)
			baseInfoCh <- FileInfo{
				Parent: filepath.Dir(path),
				Name:   filepath.Base(path),
			}

			return nil
		})

		close(baseInfoCh)
		prepWg.Wait()
		close(fc)
		appendWg.Wait()

		bin = idx.Marshal()
		_ = os.WriteFile("/tmp/index.bin", bin, 0600)
	} else {
		data, err := os.ReadFile("/tmp/index.bin")
		if err != nil {
			panic(err)
		}
		bin = data

		idx = LoadIndex(data)
	}

	afterIndex := time.Now()

	visited := atomic.Int64{}
	skipped := atomic.Int64{}
	matches := atomic.Int64{}

	query := NewQuery(q)

	_ = walk.Walk(cwd, runtime.NumCPU(), func(path string, info os.FileInfo, err error) error {
		visited.Add(1)
		if query.Matches(path) {
			matches.Add(1)
			fmt.Printf("Match: %v\n", path)
		}

		if info.IsDir() && !idx.ContinueDown(path, query) {
			skipped.Add(1)
			return walk.SkipDir
		}
		return nil
	})

	afterSearch := time.Now()

	if loadIndex {
		fmt.Printf("Visited = %v | Skipped = %v | Matches = %v\n", visited.Load(), skipped.Load(), matches.Load())
		fmt.Printf("Load time = %v | Search time = %v\n", afterIndex.Sub(start), afterSearch.Sub(afterIndex))
	} else {
		fmt.Printf("FilesInIndex = %v | Visited = %v | Skipped = %v | Matches = %v\n", filesInIndex.Load(), visited.Load(), skipped.Load(), matches.Load())
		fmt.Printf("Index time = %v | Search time = %v\n", afterIndex.Sub(start), afterSearch.Sub(afterIndex))
		fmt.Printf("Naive size = %.2fMB | Index size = %.2fMB | Percent = %.2f%%\n", float64(naiveSize.Load())/1000000.0, float64(len(bin))/1000000.0, (float64(len(bin))/float64(naiveSize.Load()))*100)
	}
}
