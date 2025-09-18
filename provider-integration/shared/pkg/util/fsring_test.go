package util

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"
)

type Metric struct {
	UnixNano int64
	CPUUser  float32
	CPUSys   float32
	MemUsed  uint64
	MemTot   uint64
	GPUUtil  float32
	NetRx    uint64
	NetTx    uint64
}

func TestFsRing(t *testing.T) {
	path := filepath.Join(os.TempDir(), "ring.bin")
	slotCount := 256
	slotSize := FsRingHeaderSize + 64
	itemCount := 50
	itemSleep := 10 * time.Millisecond

	_ = os.Remove(path)

	metricSerializer := FsRingSerializer[Metric]{
		Serialize: func(item Metric, buf *UBufferWriter) {
			buf.WriteS64(item.UnixNano)
			buf.WriteF32(item.CPUUser)
			buf.WriteF32(item.CPUSys)
			buf.WriteU64(item.MemUsed)
			buf.WriteU64(item.MemTot)
			buf.WriteF32(item.GPUUtil)
			buf.WriteU64(item.NetRx)
			buf.WriteU64(item.NetTx)
		},
		Deserialize: func(buf *UBufferReader) Metric {
			return Metric{
				UnixNano: buf.ReadS64(),
				CPUUser:  buf.ReadF32(),
				CPUSys:   buf.ReadF32(),
				MemUsed:  buf.ReadU64(),
				MemTot:   buf.ReadU64(),
				GPUUtil:  buf.ReadF32(),
				NetRx:    buf.ReadU64(),
				NetTx:    buf.ReadU64(),
			}
		},
	}

	wg := sync.WaitGroup{}
	wg.Add(3)

	go func() {
		writer, err := FsRingCreate(path, slotCount, slotSize, metricSerializer)
		if err != nil {
			panic(err)
		}

		for i := 0; i < itemCount; i++ {
			err = writer.Write(Metric{
				UnixNano: int64(i),
				CPUUser:  2,
				CPUSys:   3,
				MemUsed:  4,
				MemTot:   5,
				GPUUtil:  6,
				NetRx:    7,
				NetTx:    8,
			})

			if err != nil {
				panic(err)
			}

			time.Sleep(itemSleep)
		}

		SilentClose(writer)
		fmt.Printf("Writer done\n")
		wg.Done()
	}()

	go func() {
		for {
			follower, err := FsRingOpen(path, metricSerializer)
			if err != nil {
				time.Sleep(5 * time.Millisecond)
				continue
			}

			output := make(chan Metric)

			go func() {
				for {
					item, ok := <-output
					if !ok {
						break
					}

					fmt.Printf("%#v\n", item)
					if item.UnixNano >= int64(itemCount-1) {
						_ = follower.Close()
						break
					}
				}
				wg.Done()
				fmt.Printf("Processor done\n")
			}()

			err = follower.Follow(context.Background(), output, 100)
			wg.Done()
			fmt.Printf("Follow done\n")
			break
		}
	}()

	wg.Wait()
}
