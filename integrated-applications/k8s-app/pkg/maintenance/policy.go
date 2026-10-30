package maintenance

import (
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/util"
)

const (
	PolicyKey   = "maintenance/policy"
	ScheduleKey = "maintenance/schedule"

	WindowDurationHours = 4

	defaultStartHour    = 2
	defaultMaxBackupAge = time.Hour
)

const (
	PreparationNone   = "none"
	PreparationCordon = "cordon"
	PreparationDrain  = "drain"
)

var windowDays = map[string]time.Weekday{
	"mon": time.Monday,
	"tue": time.Tuesday,
	"wed": time.Wednesday,
	"thu": time.Thursday,
	"fri": time.Friday,
	"sat": time.Saturday,
	"sun": time.Sunday,
}

type Window struct {
	Day          string `json:"day"`
	StartHourUtc int    `json:"startHourUtc"`
}

type TaskPolicy struct {
	Enabled        bool          `json:"enabled"`
	TimeoutSeconds int           `json:"timeoutSeconds"`
	MaxBackupAge   time.Duration `json:"maxBackupAge,omitempty"`
}

type Policy struct {
	SchemaRevision int        `json:"schemaRevision"`
	Window         Window     `json:"window"`
	Kubernetes     TaskPolicy `json:"kubernetes"`
	Os             TaskPolicy `json:"os"`
}

type ScheduleState struct {
	SchemaRevision  int       `json:"schemaRevision"`
	LastWindowStart time.Time `json:"lastWindowStart,omitempty"`
}

type PolicyPatch struct {
	K8sEnabled      util.Option[bool]
	OsEnabled       util.Option[bool]
	WindowDay       string
	WindowStartHour util.Option[int]
}

func DefaultPolicy() Policy {
	return Policy{
		SchemaRevision: 1,
		Window: Window{
			Day:          "sun",
			StartHourUtc: defaultStartHour,
		},
		Kubernetes: TaskPolicy{
			TimeoutSeconds: defaultTimeoutSeconds,
			MaxBackupAge:   defaultMaxBackupAge,
		},
		Os: TaskPolicy{
			TimeoutSeconds: defaultTimeoutSeconds,
		},
	}
}

func PolicyNormalize(policy Policy) Policy {
	policy.SchemaRevision = 1

	day := strings.ToLower(strings.TrimSpace(policy.Window.Day))
	if !DayValid(day) {
		day = "sun"
	}
	policy.Window.Day = day
	if policy.Window.StartHourUtc < 0 || policy.Window.StartHourUtc > 23 {
		policy.Window.StartHourUtc = defaultStartHour
	}

	policy.Kubernetes = normalizeTaskPolicy(policy.Kubernetes)
	policy.Os = normalizeTaskPolicy(policy.Os)
	return policy
}

func normalizeTaskPolicy(policy TaskPolicy) TaskPolicy {
	if policy.TimeoutSeconds < timeoutMinSeconds || policy.TimeoutSeconds > timeoutMaxSeconds {
		policy.TimeoutSeconds = defaultTimeoutSeconds
	}
	if policy.MaxBackupAge <= 0 {
		policy.MaxBackupAge = defaultMaxBackupAge
	}
	return policy
}

func DayValid(day string) bool {
	_, ok := windowDays[strings.ToLower(strings.TrimSpace(day))]
	return ok
}

func PreparationValid(preparation string) bool {
	switch preparation {
	case PreparationNone, PreparationCordon, PreparationDrain:
		return true
	}
	return false
}

func PolicyRead(client stackClient) (stackValue[Policy], error) {
	record, err := stackValueRead[Policy](client, PolicyKey, "maintenance policy")
	if err != nil {
		return stackValue[Policy]{}, err
	}
	if record.Found {
		record.Value = PolicyNormalize(record.Value)
	}
	return record, nil
}

func PolicyWrite(client stackClient, policy Policy, expectedRevision int64) (int64, error) {
	value, err := json.Marshal(PolicyNormalize(policy))
	if err != nil {
		return 0, err
	}
	return shared.RpcGrantStateWrite(client, PolicyKey, value, expectedRevision)
}

func ScheduleRead(client stackClient) (stackValue[ScheduleState], error) {
	return stackValueRead[ScheduleState](client, ScheduleKey, "maintenance schedule")
}

func ScheduleWrite(client stackClient, state ScheduleState, expectedRevision int64) (int64, error) {
	value, err := json.Marshal(state)
	if err != nil {
		return 0, err
	}
	return shared.RpcGrantStateWrite(client, ScheduleKey, value, expectedRevision)
}

func ApplyPolicyUpdate(patch PolicyPatch) error {
	client, err := stackClientNew()
	if err != nil {
		return err
	}

	if policyPatchHasPolicyFields(patch) {
		for attempt := 0; attempt < 3; attempt++ {
			policyRecord, readErr := PolicyRead(client)
			if readErr != nil {
				return readErr
			}
			policy := DefaultPolicy()
			if policyRecord.Found {
				policy = policyRecord.Value
			}

			policy, err = applyPolicyPatch(policy, patch)
			if err != nil {
				return err
			}

			_, err = PolicyWrite(client, policy, policyRecord.Revision)
			if err == nil {
				break
			}
			if !shared.IsConflict(err) {
				return err
			}
			if attempt == 2 {
				return fmt.Errorf("the maintenance policy changed while saving. Try again")
			}
		}
	}

	return nil
}

func policyPatchHasPolicyFields(patch PolicyPatch) bool {
	return patch.K8sEnabled.Present ||
		patch.OsEnabled.Present ||
		patch.WindowDay != "" ||
		patch.WindowStartHour.Present
}

func applyPolicyPatch(policy Policy, patch PolicyPatch) (Policy, error) {
	if patch.K8sEnabled.Present {
		policy.Kubernetes.Enabled = patch.K8sEnabled.Value
	}
	if patch.OsEnabled.Present {
		policy.Os.Enabled = patch.OsEnabled.Value
	}
	if patch.WindowDay != "" {
		day := strings.ToLower(strings.TrimSpace(patch.WindowDay))
		if !DayValid(day) {
			return Policy{}, fmt.Errorf("the maintenance day must be mon, tue, wed, thu, fri, sat or sun")
		}
		policy.Window.Day = day
	}
	if patch.WindowStartHour.Present {
		if patch.WindowStartHour.Value < 0 || patch.WindowStartHour.Value > 23 {
			return Policy{}, fmt.Errorf("the maintenance window start hour must be from 0 to 23")
		}
		policy.Window.StartHourUtc = patch.WindowStartHour.Value
	}
	return policy, nil
}

func NextWindow(now time.Time, window Window) (time.Time, time.Time, bool) {
	now = now.UTC()
	duration := time.Duration(WindowDurationHours) * time.Hour

	for offset := -1; offset <= 7; offset++ {
		day := now.AddDate(0, 0, offset)
		if !windowHasDay(window, day) {
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

func WindowOpen(now time.Time, window Window) (time.Time, time.Time, bool) {
	start, end, ok := NextWindow(now, window)
	if !ok || now.Before(start) || !now.Before(end) {
		return time.Time{}, time.Time{}, false
	}
	return start, end, true
}

func windowHasDay(window Window, day time.Time) bool {
	return windowDays[strings.ToLower(window.Day)] == day.Weekday()
}
