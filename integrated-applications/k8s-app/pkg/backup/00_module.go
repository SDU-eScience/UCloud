package backup

import (
	"fmt"
	"sort"
	"time"
)

const (
	StateKey   = "backup/state"
	RequestKey = "backup/request"
)

const SchemaRevision = 1

const (
	TriggerScheduled = "scheduled"
	TriggerManual    = "manual"
)

const (
	AttemptRunning = "running"
	AttemptSuccess = "success"
	AttemptFailed  = "failed"
)

const (
	RequestPending   = "pending"
	RequestRunning   = "running"
	RequestCompleted = "completed"
	RequestFailed    = "failed"
)

const (
	hourlyTier = 48
	dailyTier  = 14
)

const idLayout = "2006-01-02T15-04-05Z"

const dateLayout = "2006-01-02"

func NewId(now time.Time) string {
	return now.UTC().Format(idLayout)
}

func ParseId(id string) (time.Time, bool) {
	parsed, err := time.Parse(idLayout, id)
	if err != nil {
		return time.Time{}, false
	}
	return parsed.UTC(), true
}

func NextHour(now time.Time) time.Time {
	return now.UTC().Truncate(time.Hour).Add(time.Hour)
}

func RetentionDeleteIds(ids []string, now time.Time) ([]string, error) {
	parsed := make(map[string]time.Time, len(ids))
	for _, id := range ids {
		parsedAt, ok := ParseId(id)
		if !ok {
			return nil, fmt.Errorf("the backup id %q is not a valid backup id", id)
		}
		parsed[id] = parsedAt
	}

	sorted := make([]string, 0, len(parsed))
	for id := range parsed {
		sorted = append(sorted, id)
	}
	sort.Strings(sorted)

	keep := map[string]bool{}

	hourlyStart := len(sorted) - hourlyTier
	if hourlyStart < 0 {
		hourlyStart = 0
	}
	for i := hourlyStart; i < len(sorted); i++ {
		keep[sorted[i]] = true
	}

	today := now.UTC().Format(dateLayout)
	newestPerDate := map[string]string{}
	for i := len(sorted) - 1; i >= 0; i-- {
		date := parsed[sorted[i]].UTC().Format(dateLayout)
		if date > today {
			continue
		}
		if _, present := newestPerDate[date]; !present {
			newestPerDate[date] = sorted[i]
		}
	}

	dates := make([]string, 0, len(newestPerDate))
	for date := range newestPerDate {
		dates = append(dates, date)
	}
	sort.Sort(sort.Reverse(sort.StringSlice(dates)))
	if len(dates) > dailyTier {
		dates = dates[:dailyTier]
	}
	for _, date := range dates {
		keep[newestPerDate[date]] = true
	}

	if len(sorted) > 0 {
		keep[sorted[len(sorted)-1]] = true
	}

	deletes := make([]string, 0, len(sorted))
	for _, id := range sorted {
		if !keep[id] {
			deletes = append(deletes, id)
		}
	}
	return deletes, nil
}

type SnapshotInfo struct {
	File      string `json:"file"`
	SizeBytes int64  `json:"sizeBytes"`
	Sha256    string `json:"sha256"`
}

type CredentialsInfo struct {
	ServerTokenSha256 string `json:"serverTokenSha256"`
	AgentTokenSha256  string `json:"agentTokenSha256"`
}

type RestoreInfo struct {
	ClusterResetRequired bool `json:"clusterResetRequired"`
}

type Metadata struct {
	SchemaRevision int             `json:"schemaRevision"`
	BackupId       string          `json:"backupId"`
	ClusterId      string          `json:"clusterId"`
	StackId        string          `json:"stackId"`
	Release        string          `json:"release"`
	CreatedAt      time.Time       `json:"createdAt"`
	CreatedByNode  string          `json:"createdByNode"`
	Trigger        string          `json:"trigger"`
	Snapshot       SnapshotInfo    `json:"snapshot"`
	Credentials    CredentialsInfo `json:"credentials"`
	Restore        RestoreInfo     `json:"restore"`
}

type BackupInfo struct {
	Id            string    `json:"id"`
	CreatedAt     time.Time `json:"createdAt,omitempty"`
	Release       string    `json:"release,omitempty"`
	SizeBytes     int64     `json:"sizeBytes,omitempty"`
	Sha256        string    `json:"sha256,omitempty"`
	Trigger       string    `json:"trigger,omitempty"`
	CreatedByNode string    `json:"createdByNode,omitempty"`
}

type Attempt struct {
	Id         string    `json:"id,omitempty"`
	Trigger    string    `json:"trigger,omitempty"`
	Phase      string    `json:"phase"`
	NodeName   string    `json:"nodeName,omitempty"`
	StartedAt  time.Time `json:"startedAt,omitempty"`
	FinishedAt time.Time `json:"finishedAt,omitempty"`
	Error      string    `json:"error,omitempty"`
	SizeBytes  int64     `json:"sizeBytes,omitempty"`
	Sha256     string    `json:"sha256,omitempty"`
}

type State struct {
	SchemaRevision  int          `json:"schemaRevision"`
	DueAt           time.Time    `json:"dueAt"`
	ClaimUid        string       `json:"claimUid,omitempty"`
	ClaimTrigger    string       `json:"claimTrigger,omitempty"`
	ClaimRequestUid string       `json:"claimRequestUid,omitempty"`
	ClaimDeadline   time.Time    `json:"claimDeadline,omitempty"`
	LastAttempt     Attempt      `json:"lastAttempt,omitempty"`
	LastSuccess     Attempt      `json:"lastSuccess,omitempty"`
	Retained        []BackupInfo `json:"retained,omitempty"`
	PruneWarnings   []string     `json:"pruneWarnings,omitempty"`
}

type Request struct {
	SchemaRevision int       `json:"schemaRevision"`
	Uid            string    `json:"uid"`
	Requester      string    `json:"requester,omitempty"`
	RequestedAt    time.Time `json:"requestedAt"`
	Phase          string    `json:"phase"`
	AttemptUid     string    `json:"attemptUid,omitempty"`
	Error          string    `json:"error,omitempty"`
	CompletedAt    time.Time `json:"completedAt,omitempty"`
}
