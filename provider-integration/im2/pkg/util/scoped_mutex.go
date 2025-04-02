package util

import "sync"

type ScopedMutex struct {
	mu    sync.RWMutex
	locks map[any]*sync.Mutex
}

func NewScopedMutex() *ScopedMutex {
	return &ScopedMutex{
		mu:    sync.RWMutex{},
		locks: make(map[any]*sync.Mutex),
	}
}

func (m *ScopedMutex) Lock(key any) {
	m.mu.RLock()
	lock, exists := m.locks[key]
	m.mu.RUnlock()

	if !exists {
		m.mu.Lock()
		if lock, exists = m.locks[key]; !exists {
			lock = &sync.Mutex{}
			m.locks[key] = lock
		}
		m.mu.Unlock()
	}

	lock.Lock()
}

func (m *ScopedMutex) Unlock(key any) {
	m.mu.Lock()
	if lock, exists := m.locks[key]; exists {
		lock.Unlock()
		delete(m.locks, key)
	}
	m.mu.Unlock()
}
