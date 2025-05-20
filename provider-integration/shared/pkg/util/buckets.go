package util

import "sync"

func ReadOrInsertBucket[T any](mu *sync.RWMutex, buckets map[string]*T, key string, init func() *T) *T {
	mu.RLock()
	bucket, ok := buckets[key]
	mu.RUnlock()
	if !ok {
		mu.Lock()
		bucket, ok = buckets[key]
		if !ok {
			if init == nil {
				var t T
				bucket, ok = &t, true
			} else {
				bucket, ok = init(), true
			}
			buckets[key] = bucket
		}
		mu.Unlock()
	}

	return bucket
}
