package util

import (
	"cmp"
	"sync"
	"time"
	"ucloud.dk/pkg/log"
)

type cacheEntry[V any] struct {
	Value       Option[V]
	Wg          sync.WaitGroup
	RetrievedAt time.Time
}

func (e *cacheEntry[V]) Result() (V, bool) {
	e.Wg.Wait()
	ok := e.Value.IsSet()
	return e.Value.Get(), ok
}

type AsyncCache[K cmp.Ordered, V any] struct {
	TimeToLive     time.Duration
	nextExpiration time.Time
	entries        map[K]*cacheEntry[V]
	mutex          sync.RWMutex
	createdBy      FileAndLine
}

func NewCache[K cmp.Ordered, V any](ttl time.Duration) *AsyncCache[K, V] {
	return &AsyncCache[K, V]{
		TimeToLive:     ttl,
		mutex:          sync.RWMutex{},
		entries:        make(map[K]*cacheEntry[V]),
		nextExpiration: time.Now(),
		createdBy:      GetCaller(),
	}
}

func (c *AsyncCache[K, V]) Get(key K, valueGetter func() (V, error)) (V, bool) {
	// Attempt purge prior to retrieving from the cache
	c.attemptPurge()

	// Attempt to get the result immediately, without waiting
	res, ok := c.GetNow(key)
	if ok {
		return res, true
	}

	// If not, we must try to submit it
	c.mutex.Lock()

	// Once we have the lock, check if someone else came before us
	cachedEntry, ok := c.entries[key]
	if ok {
		// Someone did, so read the result from the entry. Unlocking immediately to avoid blocking others.
		c.mutex.Unlock()
		res, ok = cachedEntry.Result()
		return res, ok
	}

	// We got here first, so we should submit it
	entry := &cacheEntry[V]{
		Wg:          sync.WaitGroup{},
		RetrievedAt: time.Now(),
	}
	entry.Wg.Add(1)

	go func() {
		res, err := valueGetter()
		if err != nil {
			log.Warn("Failed to retrieve key '%v'. Cache created at %v. Error: '%v'.", key, c.createdBy, err)
		} else {
			entry.Value = OptValue(res)
		}
		entry.Wg.Done()
	}()

	c.entries[key] = entry

	// Unlock the mutex and then wait for the result
	c.mutex.Unlock()
	return entry.Result()
}

func (c *AsyncCache[K, V]) getEntry(key K) (*cacheEntry[V], bool) {
	c.mutex.RLock()
	entry, ok := c.entries[key]
	c.mutex.RUnlock()
	return entry, ok
}

func (c *AsyncCache[K, V]) GetNow(key K) (V, bool) {
	entry, ok := c.getEntry(key)
	if !ok {
		var v V
		return v, false
	}
	return entry.Result()
}

func (c *AsyncCache[K, V]) Set(key K, value V) {
	c.mutex.Lock()
	defer c.mutex.Unlock()
	c.entries[key] = &cacheEntry[V]{
		Value:       OptValue(value),
		RetrievedAt: time.Now(),
		Wg:          sync.WaitGroup{},
	}
}

func (c *AsyncCache[K, V]) attemptPurge() {
	c.mutex.RLock()
	if time.Now().After(c.nextExpiration) {
		c.mutex.RUnlock()

		c.mutex.Lock()
		defer c.mutex.Unlock()

		now := time.Now()
		if now.Before(c.nextExpiration) {
			return
		}

		for k, e := range c.entries {
			if now.After(e.RetrievedAt.Add(c.TimeToLive)) {
				delete(c.entries, k)
			}
		}
		c.nextExpiration = now.Add(c.TimeToLive)
	} else {
		c.mutex.RUnlock()
	}
}

func (c *AsyncCache[K, V]) FindEntry(predicate func(V) bool) (key K, value V, ok bool) {
	c.attemptPurge()
	c.mutex.RLock()
	defer c.mutex.RUnlock()

	for k, v := range c.entries {
		if !v.Value.IsSet() {
			continue
		}

		actualValue := v.Value.Get()
		if predicate(actualValue) {
			return k, actualValue, true
		}
	}

	ok = false
	return
}
