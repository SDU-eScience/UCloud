package registry

import (
	"net/http"
	"sync"
	"time"

	"golang.org/x/time/rate"
	"ucloud.dk/shared/pkg/util"
)

const (
	registryTokenRateLimitPerSecond  = 1
	registryTokenRateLimitBurst      = 30
	registryTokenRateLimitEntryTtl   = 10 * time.Minute
	registryTokenRateLimitMaxEntries = 8192
)

type registryTokenRateLimiterEntry struct {
	limiter  *rate.Limiter
	lastSeen time.Time
}

var registryTokenRateLimiters = struct {
	mutex     sync.Mutex
	lastSweep time.Time
	entries   map[string]*registryTokenRateLimiterEntry
}{
	entries: map[string]*registryTokenRateLimiterEntry{},
}

func registryTokenRateLimitAllow(r *http.Request) bool {
	key := "unknown"
	if ip := util.ClientIP(r); ip != nil {
		key = ip.String()
	}

	now := time.Now()
	registryTokenRateLimiters.mutex.Lock()
	defer registryTokenRateLimiters.mutex.Unlock()

	if now.Sub(registryTokenRateLimiters.lastSweep) >= time.Minute {
		for entryKey, entry := range registryTokenRateLimiters.entries {
			if now.Sub(entry.lastSeen) > registryTokenRateLimitEntryTtl {
				delete(registryTokenRateLimiters.entries, entryKey)
			}
		}
		registryTokenRateLimiters.lastSweep = now
	}

	entry, ok := registryTokenRateLimiters.entries[key]
	if !ok {
		if len(registryTokenRateLimiters.entries) >= registryTokenRateLimitMaxEntries {
			return false
		}
		entry = &registryTokenRateLimiterEntry{
			limiter: rate.NewLimiter(rate.Limit(registryTokenRateLimitPerSecond), registryTokenRateLimitBurst),
		}
		registryTokenRateLimiters.entries[key] = entry
	}

	entry.lastSeen = now
	return entry.limiter.Allow()
}
