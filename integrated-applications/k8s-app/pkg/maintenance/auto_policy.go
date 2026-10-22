package maintenance

import (
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

const (
	AutoPolicyKey = "maintenance/auto/policy"
	AutoStateKey  = "maintenance/auto/state"
)

const AutoSchemaRevision = 1

const AutoWindowDurationHours = 4

const autoClaimLease = 30 * time.Minute

const (
	autoDefaultStartHour    = 2
	autoDefaultMaxBackupAge = time.Hour
)

const (
	AutoChannelK8s = "k8s"
	AutoChannelOs  = "os"
)

const (
	AutoTriggerWindow = "window"
	AutoTriggerManual = "manual"
)

const (
	AutoPhasePending   = "pending"
	AutoPhasePreflight = "preflight"
	AutoPhaseBackup    = "backup"
	AutoPhaseRunning   = "running"
	AutoPhaseCompleted = "completed"
	AutoPhaseBlocked   = "blocked"
)

const (
	AutoRebootWhenRequired = shared.NodeAgentOsRebootWhenRequired
	AutoRebootAlways       = shared.NodeAgentOsRebootAlways
)

type AutoWindow struct {
	Days          []string `json:"days"`
	StartHourUtc  int      `json:"startHourUtc"`
	DurationHours int      `json:"durationHours"`
}

type AutoPolicy struct {
	SchemaRevision  int           `json:"schemaRevision"`
	Window          AutoWindow    `json:"window"`
	DeferUntil      time.Time     `json:"deferUntil,omitempty"`
	TestWindowStart time.Time     `json:"testWindowStart,omitempty"`
	TestWindowEnd   time.Time     `json:"testWindowEnd,omitempty"`
	K8s             AutoK8sPolicy `json:"k8s"`
	Os              AutoOsPolicy  `json:"os"`
}

type AutoK8sPolicy struct {
	Enabled      bool          `json:"enabled"`
	MaxBackupAge time.Duration `json:"maxBackupAge"`
}

type AutoOsPolicy struct {
	Enabled      bool   `json:"enabled"`
	RebootPolicy string `json:"rebootPolicy"`
}

type AutoCheck struct {
	Name   string `json:"name"`
	Ok     bool   `json:"ok"`
	Detail string `json:"detail,omitempty"`
}

type AutoRun struct {
	Uid            string      `json:"uid,omitempty"`
	Channel        string      `json:"channel"`
	Trigger        string      `json:"trigger"`
	Phase          string      `json:"phase"`
	TargetRelease  string      `json:"targetRelease,omitempty"`
	WindowStart    time.Time   `json:"windowStart,omitempty"`
	WindowEnd      time.Time   `json:"windowEnd,omitempty"`
	Checks         []AutoCheck `json:"checks,omitempty"`
	Error          string      `json:"error,omitempty"`
	StartedAt      time.Time   `json:"startedAt,omitempty"`
	FinishedAt     time.Time   `json:"finishedAt,omitempty"`
	ManualWindow   bool        `json:"manualWindow,omitempty"`
	K8sRelease     string      `json:"k8sRelease,omitempty"`
	K8sRunUid      string      `json:"k8sRunUid,omitempty"`
	K8sCompletedAt time.Time   `json:"k8sCompletedAt,omitempty"`
}

type AutoUpdateReport struct {
	NodeName       string    `json:"nodeName"`
	Pending        int       `json:"pending"`
	Security       int       `json:"security"`
	RebootRequired bool      `json:"rebootRequired"`
	CheckedAt      time.Time `json:"checkedAt"`
	Error          string    `json:"error,omitempty"`
}

type AutoState struct {
	SchemaRevision int                `json:"schemaRevision"`
	ClaimUid       string             `json:"claimUid,omitempty"`
	ClaimDeadline  time.Time          `json:"claimDeadline,omitempty"`
	NextWindowAt   time.Time          `json:"nextWindowAt,omitempty"`
	ResumedAt      time.Time          `json:"resumedAt,omitempty"`
	LastRun        AutoRun            `json:"lastRun,omitempty"`
	OsReports      []AutoUpdateReport `json:"osReports,omitempty"`
}

var autoWindowDays = map[string]time.Weekday{
	"mon": time.Monday,
	"tue": time.Tuesday,
	"wed": time.Wednesday,
	"thu": time.Thursday,
	"fri": time.Friday,
	"sat": time.Saturday,
	"sun": time.Sunday,
}

func AutoDayValid(day string) bool {
	_, ok := autoWindowDays[strings.ToLower(strings.TrimSpace(day))]
	return ok
}

func AutoWindowValid(window AutoWindow) bool {
	if len(window.Days) == 0 || window.StartHourUtc < 0 || window.StartHourUtc > 23 || window.DurationHours < 1 || window.DurationHours > 12 {
		return false
	}

	for _, day := range window.Days {
		if !AutoDayValid(day) {
			return false
		}
	}

	return true
}

func AutoDefaultPolicy() AutoPolicy {
	return AutoPolicy{
		SchemaRevision: AutoSchemaRevision,
		Window: AutoWindow{
			Days:          []string{"sun"},
			StartHourUtc:  autoDefaultStartHour,
			DurationHours: AutoWindowDurationHours,
		},
		K8s: AutoK8sPolicy{
			MaxBackupAge: autoDefaultMaxBackupAge,
		},
		Os: AutoOsPolicy{
			RebootPolicy: AutoRebootWhenRequired,
		},
	}
}

func AutoPolicyNormalize(policy AutoPolicy) AutoPolicy {
	policy.SchemaRevision = AutoSchemaRevision
	policy.Window.DurationHours = AutoWindowDurationHours

	if !AutoWindowValid(policy.Window) {
		policy.Window = AutoDefaultPolicy().Window
	}

	if policy.K8s.MaxBackupAge <= 0 {
		policy.K8s.MaxBackupAge = autoDefaultMaxBackupAge
	}

	if policy.Os.RebootPolicy != AutoRebootAlways {
		policy.Os.RebootPolicy = AutoRebootWhenRequired
	}

	return policy
}

func AutoNextWindow(now time.Time, window AutoWindow) (time.Time, time.Time, bool) {
	if !AutoWindowValid(window) {
		return time.Time{}, time.Time{}, false
	}

	now = now.UTC()
	duration := time.Duration(window.DurationHours) * time.Hour

	for offset := -1; offset <= 7; offset++ {
		day := now.AddDate(0, 0, offset)
		if !autoWindowHasDay(window, day) {
			continue
		}

		start := time.Date(day.Year(), day.Month(), day.Day(), window.StartHourUtc, 0, 0, 0, time.UTC)
		end := start.Add(duration)
		if end.After(now) {
			return start, end, true
		}
	}

	return time.Time{}, time.Time{}, false
}

func AutoEffectiveWindow(now time.Time, policy AutoPolicy) (time.Time, time.Time, bool) {
	if AutoTestWindowOpen(now, policy) {
		return now.UTC(), policy.TestWindowEnd.UTC(), true
	}
	floor := now.UTC()
	if policy.DeferUntil.After(floor) {
		floor = policy.DeferUntil.UTC()
	}

	start, end, ok := AutoNextWindow(floor, policy.Window)
	if !ok {
		return time.Time{}, time.Time{}, false
	}

	effective := start
	if effective.Before(floor) {
		effective = floor
	}

	return effective, end, true
}

func autoWindowHasDay(window AutoWindow, day time.Time) bool {
	for _, candidate := range window.Days {
		if autoWindowDays[strings.ToLower(strings.TrimSpace(candidate))] == day.Weekday() {
			return true
		}
	}
	return false
}

type autoPolicyRecord = stackValue[AutoPolicy]

type autoStateRecord = stackValue[AutoState]

func autoPolicyRead(client stackClient) (autoPolicyRecord, error) {
	record, err := stackValueRead[AutoPolicy](client, AutoPolicyKey, "automatic maintenance policy")
	if err != nil {
		return autoPolicyRecord{}, err
	}

	if !record.Found {
		return record, nil
	}

	record.Value = AutoPolicyNormalize(record.Value)
	return record, nil
}

func autoStateRead(client stackClient) (autoStateRecord, error) {
	return stackValueRead[AutoState](client, AutoStateKey, "automatic maintenance state")
}

func autoStateWrite(client stackClient, state AutoState, expectedRevision int64) (int64, error) {
	value, err := json.Marshal(state)
	if err != nil {
		return 0, err
	}

	return shared.RpcGrantStateWrite(client, AutoStateKey, value, expectedRevision)
}

func autoStateClaimActive(state AutoState, now time.Time) bool {
	return state.ClaimUid != "" && now.Before(state.ClaimDeadline)
}

func AutoRunActive(run AutoRun) bool {
	switch run.Phase {
	case AutoPhasePending, AutoPhasePreflight, AutoPhaseBackup, AutoPhaseRunning:
		return true
	}
	return false
}

func AutoOsReportsRead() ([]AutoUpdateReport, error) {
	client, err := stackClientNew()
	if err != nil {
		return nil, err
	}

	record, err := autoStateRead(client)
	if err != nil {
		return nil, err
	}
	if !record.Found {
		return nil, nil
	}

	return record.Value.OsReports, nil
}

func AutoStateResumed(state AutoState) bool {
	return state.LastRun.Phase == AutoPhaseBlocked && state.ResumedAt.After(state.LastRun.FinishedAt)
}

func AutoStateBlocked(state AutoState) bool {
	return state.LastRun.Phase == AutoPhaseBlocked && !AutoStateResumed(state)
}

func AutoWindowChannelPending(state AutoState, policy AutoPolicy, windowStart time.Time) bool {
	return autoChannelStartAllowed(state.LastRun, policy, windowStart, AutoStateResumed(state))
}

func autoStateClaim(client stackClient, record autoStateRecord, uid string, now time.Time) (autoStateRecord, error) {
	if autoStateClaimActive(record.Value, now) {
		return record, fmt.Errorf(
			"another coordinator holds the automatic maintenance claim until %s",
			record.Value.ClaimDeadline.UTC().Format(time.RFC3339),
		)
	}

	state := record.Value
	state.SchemaRevision = AutoSchemaRevision
	state.ClaimUid = uid
	state.ClaimDeadline = now.UTC().Add(autoClaimLease)

	revision, err := autoStateWrite(client, state, record.Revision)
	if err != nil {
		return record, err
	}

	return autoStateRecord{Found: true, Value: state, Revision: revision}, nil
}

func autoStateRelease(client stackClient, record autoStateRecord, claimUid string) (autoStateRecord, error) {
	if record.Value.ClaimUid != claimUid {
		return record, nil
	}

	state := record.Value
	state.SchemaRevision = AutoSchemaRevision
	state.ClaimUid = ""
	state.ClaimDeadline = time.Time{}

	revision, err := autoStateWrite(client, state, record.Revision)
	if err != nil {
		return record, err
	}

	return autoStateRecord{Found: true, Value: state, Revision: revision}, nil
}
