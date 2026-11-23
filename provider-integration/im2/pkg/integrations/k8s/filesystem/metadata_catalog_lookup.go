package filesystem

import (
	"context"
	"sync"
	"time"

	"golang.org/x/time/rate"
)

type metadataScanChild struct {
	name      string
	directory bool
	entry     MetadataEntry
	err       error
}

type metadataLookupRequest struct {
	ctx        context.Context
	limiter    *rate.Limiter
	parent     int
	generation uint64
	child      *metadataScanChild
	done       *sync.WaitGroup
}

var metadataLookups = struct {
	sync.Once
	requests chan metadataLookupRequest
}{}

func metadataLookupScanChildren(ctx context.Context, limiter *rate.Limiter, parent int, generation uint64, children []metadataScanChild) error {
	metadataLookups.Do(func() {
		metadataRuntime.Lock()
		parallelLookups := metadataRuntime.config.ParallelLookups
		metadataRuntime.Unlock()
		metadataLookups.requests = make(chan metadataLookupRequest)
		metadataMetricLookupLimit.Set(float64(parallelLookups))
		idle := metadataMetricLookupWorkers.WithLabelValues("idle")
		waiting := metadataMetricLookupWorkers.WithLabelValues("limiter_wait")
		filesystem := metadataMetricLookupWorkers.WithLabelValues("filesystem")
		idle.Add(float64(parallelLookups))
		for range parallelLookups {
			go func() {
				for request := range metadataLookups.requests {
					idle.Dec()
					waiting.Inc()
					err := metadataWaitForScanOperation(request.ctx, request.limiter, "stat")
					waiting.Dec()
					if err == nil {
						filesystem.Inc()
						request.child.entry, err = metadataLookupEntry(request.parent, request.child.name, request.generation)
						filesystem.Dec()
					}
					request.child.err = err
					idle.Inc()
					request.done.Done()
				}
			}()
		}
	})
	var done sync.WaitGroup
	defer done.Wait()
	submitWait := metadataMetricLookupSubmitWait.WithLabelValues()
	for i := range children {
		if children[i].directory {
			continue
		}
		done.Add(1)
		request := metadataLookupRequest{
			ctx:        ctx,
			limiter:    limiter,
			parent:     parent,
			generation: generation,
			child:      &children[i],
			done:       &done,
		}
		startedAt := time.Now()
		select {
		case metadataLookups.requests <- request:
			submitWait.Observe(time.Since(startedAt).Seconds())
			continue
		default:
		}
		metadataMetricLookupSubmitters.Inc()
		select {
		case metadataLookups.requests <- request:
		case <-ctx.Done():
			metadataMetricLookupSubmitters.Dec()
			submitWait.Observe(time.Since(startedAt).Seconds())
			done.Done()
			return ctx.Err()
		}
		metadataMetricLookupSubmitters.Dec()
		submitWait.Observe(time.Since(startedAt).Seconds())
	}
	return nil
}
