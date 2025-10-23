package util

import (
	"time"
	"ucloud.dk/shared/pkg/log"
)

func RetryOrPanic[R any](action string, fn func() (R, error)) R {
	wait := 10
	var lastErr error
	for attempt := 0; attempt < 30; attempt++ {
		res, err := fn()
		if err != nil {
			lastErr = err
			time.Sleep(time.Duration(wait) * time.Millisecond)
			log.Warn("Failed %s: %v", action, err)
			wait *= 2
			if wait > 5000 {
				wait = 5000
			}
			continue
		}

		return res
	}

	log.Fatal("RetryOrPanic failure: %s", lastErr)
	panic(lastErr)
}
