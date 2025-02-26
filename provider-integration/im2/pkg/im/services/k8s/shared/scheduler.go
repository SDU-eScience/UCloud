package shared

import (
	"sync"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

type JobReplicaState struct {
	Id     string
	Rank   int
	State  orc.JobState
	Node   util.Option[string]
	Status util.Option[string]
}

type JobTracker interface {
	AddUpdate(id string, update orc.JobUpdate)
	TrackState(state JobReplicaState) bool
	RequestCleanup(id string)
}

var scheduleLock = sync.Mutex{}
var scheduledEntries []*orc.Job = nil

func RequestSchedule(entry *orc.Job) {
	scheduleLock.Lock()
	scheduledEntries = append(scheduledEntries, entry)
	scheduleLock.Unlock()
}

func SwapScheduleQueue() []*orc.Job {
	scheduleLock.Lock()
	result := scheduledEntries
	scheduledEntries = nil
	scheduleLock.Unlock()
	return result
}
