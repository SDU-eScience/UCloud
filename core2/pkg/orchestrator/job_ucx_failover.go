package orchestrator

import (
	"sync"
	"time"
)

const (
	ucxUiShortSession       = 10 * time.Second
	ucxUiUnavailableTimeout = 2 * time.Minute
	ucxUiSelectionTimeout   = 24 * time.Hour
)

type ucxUiSelection struct {
	JobId     string
	UpdatedAt time.Time
}

var ucxUiMu sync.Mutex
var ucxUiUnavailableUntil = map[string]time.Time{}
var ucxUiShortSessions = map[string]int{}
var ucxUiSelections = map[string]ucxUiSelection{}

// ucxUiJobAvailable reports if a UI job answered recently. A job is marked unavailable when its
// sessions keep failing within seconds, which happens when the machine that hosts it reboots.
func ucxUiJobAvailable(jobId string) bool {
	if jobId == "" {
		return true
	}

	ucxUiMu.Lock()
	defer ucxUiMu.Unlock()

	until, present := ucxUiUnavailableUntil[jobId]
	if !present {
		return true
	}
	if time.Now().After(until) {
		delete(ucxUiUnavailableUntil, jobId)
		delete(ucxUiShortSessions, jobId)
		return true
	}
	return false
}

func ucxUiNoteSessionEnd(jobId string, duration time.Duration) {
	ucxUiMu.Lock()
	defer ucxUiMu.Unlock()

	now := time.Now()
	for key, until := range ucxUiUnavailableUntil {
		if now.After(until) {
			delete(ucxUiUnavailableUntil, key)
			delete(ucxUiShortSessions, key)
		}
	}

	if jobId == "" {
		return
	}

	if duration >= ucxUiShortSession {
		delete(ucxUiShortSessions, jobId)
		return
	}

	ucxUiShortSessions[jobId]++
	if ucxUiShortSessions[jobId] < 2 {
		return
	}

	delete(ucxUiShortSessions, jobId)
	ucxUiUnavailableUntil[jobId] = now.Add(ucxUiUnavailableTimeout)
}

func ucxUiSelectedJob(entityId string) string {
	ucxUiMu.Lock()
	defer ucxUiMu.Unlock()

	selection, present := ucxUiSelections[entityId]
	if !present {
		return ""
	}
	if time.Since(selection.UpdatedAt) > ucxUiSelectionTimeout {
		delete(ucxUiSelections, entityId)
		return ""
	}
	return selection.JobId
}

func ucxUiRememberSelectedJob(entityId string, jobId string) {
	if entityId == "" || jobId == "" {
		return
	}

	ucxUiMu.Lock()
	defer ucxUiMu.Unlock()

	ucxUiSelections[entityId] = ucxUiSelection{JobId: jobId, UpdatedAt: time.Now()}

	for key, selection := range ucxUiSelections {
		if time.Since(selection.UpdatedAt) > ucxUiSelectionTimeout {
			delete(ucxUiSelections, key)
		}
	}
}
