package util

import "sync"

func ReadOrInsertBucket[K comparable, T any](mu *sync.RWMutex, buckets map[K]*T, key K, init func() *T) *T {
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

// LReadOrInsertBucket is like ReadOrInsertBucket except the lock is assumed to be held already in RW mode
func LReadOrInsertBucket[K comparable, V any](buckets map[K]*V, key K, init func() *V) *V {
	bucket, ok := buckets[key]
	if !ok {
		bucket, ok = buckets[key]
		if !ok {
			if init == nil {
				var t V
				bucket, ok = &t, true
			} else {
				bucket, ok = init(), true
			}
			buckets[key] = bucket
		}
	}

	return bucket
}
