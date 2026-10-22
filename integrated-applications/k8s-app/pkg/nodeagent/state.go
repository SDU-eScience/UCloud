package nodeagent

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"time"
)

type nodeState struct {
	SchemaRevision     int       `json:"schemaRevision"`
	Phase              string    `json:"phase"`
	Error              string    `json:"error"`
	NeedsRecovery      bool      `json:"needsRecovery"`
	UpdatedAt          time.Time `json:"updatedAt"`
	StateError         string    `json:"stateError,omitempty"`
	NeedsStateRecovery bool      `json:"needsStateRecovery,omitempty"`
}

func nodeStateRead[T any](
	path string,
	noun string,
	schemaRevision int,
	failedPhase string,
	base func(*T) *nodeState,
) T {
	var state T

	data, err := os.ReadFile(path)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return state
		}
		message := fmt.Sprintf("could not read the %s state: %s", noun, err)
		nodeStateSetFailure(base(&state), schemaRevision, failedPhase, message)
		return state
	}

	err = json.Unmarshal(data, &state)
	if err != nil {
		message := fmt.Sprintf("could not parse the %s state: %s", noun, err)
		state = *new(T)
		nodeStateSetFailure(base(&state), schemaRevision, failedPhase, message)
		return state
	}

	baseState := base(&state)
	if baseState.SchemaRevision != schemaRevision {
		baseState.StateError = fmt.Sprintf("the %s state was written by an incompatible version of the application", noun)
		baseState.Phase = failedPhase
		baseState.Error = baseState.StateError
		baseState.NeedsRecovery = true
		baseState.NeedsStateRecovery = true
	}

	return state
}

func nodeStateSetFailure(state *nodeState, schemaRevision int, failedPhase string, message string) {
	*state = nodeState{
		SchemaRevision:     schemaRevision,
		Phase:              failedPhase,
		Error:              message,
		NeedsRecovery:      true,
		StateError:         message,
		NeedsStateRecovery: true,
	}
}

func nodeStateStore(path string, state any) error {
	data, err := json.MarshalIndent(state, "", "  ")
	if err != nil {
		return err
	}

	return writeFileAtomic(path, data, 0600)
}

func nodeStateSetPhase(
	path string,
	state any,
	base *nodeState,
	phase string,
	mutate func(),
) error {
	base.Phase = phase
	base.Error = ""
	base.UpdatedAt = time.Now().UTC()
	if mutate != nil {
		mutate()
	}

	return nodeStateStore(path, state)
}

func nodeStateFail(
	path string,
	state any,
	base *nodeState,
	failedPhase string,
	message string,
) error {
	base.Phase = failedPhase
	base.Error = message
	base.NeedsRecovery = true
	base.UpdatedAt = time.Now().UTC()

	return nodeStateStore(path, state)
}

func nodeStateDefaultPhase(phase string, idlePhase string) string {
	if phase == "" {
		return idlePhase
	}
	return phase
}

func nodeStateDefaultNodeName(nodeName string, identityConfig Config) string {
	if nodeName == "" && identityConfig.NodeName != "" {
		return identityConfig.NodeName
	}
	return nodeName
}
