package maintenance

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/log"
)

// Operation logs
// =====================================================================================================================
// The worker sends its log lines through a relay instead of writing them directly. The relay batches lines and
// delivers them to the node agent on a background goroutine, which keeps the worker free to make progress. Batches
// that cannot be delivered are dropped rather than retried forever, because the log is a convenience and not a
// record of truth.

const (
	logQueueSize = 64
	logMaxLines  = 8

	logSendAttempts   = 3
	logSendRetryDelay = 500 * time.Millisecond
	logDrainTimeout   = 5 * time.Second
	logBatchTtl       = 30 * time.Second

	nodeLogResetTimeout = 5 * time.Second
)

type logBatch struct {
	nodeName     string
	nodeIp       string
	operationUid string
	lines        []string
	enqueuedAt   time.Time
}

type logRelay struct {
	worker *nodeWorker
	queue  chan logBatch
	done   chan struct{}
}

func logRelayStart(worker *nodeWorker) *logRelay {
	relay := &logRelay{
		worker: worker,
		queue:  make(chan logBatch, logQueueSize),
		done:   make(chan struct{}),
	}

	go func() {
		defer close(relay.done)
		for {
			select {
			case <-worker.ctx.Done():
				relay.drain()
				return
			case batch := <-relay.queue:
				relay.deliver(batch)
			}
		}
	}()

	return relay
}

func (relay *logRelay) submit(lines []string) {
	if relay == nil || len(lines) == 0 {
		return
	}

	batch := logBatch{
		lines:      logPackLines(lines),
		enqueuedAt: time.Now(),
	}
	if len(batch.lines) == 0 {
		return
	}

	state, err := relay.worker.load()
	if err != nil || !state.Found {
		return
	}
	operation := state.Operation
	if operation.NodeName == "" {
		return
	}

	var nodeIpErr error
	batch.nodeIp, nodeIpErr = upgradeNodeIp(relay.worker, state.Operation)
	if nodeIpErr != nil {
		log.Info(
			"k8s-app maintenance %s: the progress log could not be queued because the node address is unknown: %s",
			relay.worker.nodeName,
			nodeIpErr,
		)
		return
	}
	batch.nodeName = state.Operation.NodeName
	batch.operationUid = state.Operation.Uid

	select {
	case relay.queue <- batch:
	default:
		log.Warn("k8s-app maintenance %s: a progress log batch was dropped because the queue is full", relay.worker.nodeName)
	}
}

func logPackLines(lines []string) []string {
	packed := make([]string, 0, len(lines))
	budget := shared.NodeAgentMaxLogBody - 512
	for _, line := range lines {
		if len(packed) == logMaxLines {
			break
		}

		trimmed := strings.TrimSpace(fmt.Sprintf("[ucloud-k8s] %s", line))
		if trimmed == "" {
			continue
		}
		trimmed = shared.NodeAgentTruncateLine(trimmed)

		encoded, err := json.Marshal(trimmed)
		if err != nil {
			continue
		}

		if len(encoded)+2 > budget {
			log.Warn("k8s-app maintenance: a progress log line was dropped because the remaining request budget was too small")
			continue
		}

		packed = append(packed, trimmed)
		budget -= len(encoded) + 2
	}
	return packed
}

func (relay *logRelay) drain() {
	deadline := time.Now().Add(logDrainTimeout)
	for {
		select {
		case batch := <-relay.queue:
			relay.deliver(batch)
		default:
			return
		}
		if time.Now().After(deadline) {
			return
		}
	}
}

func (relay *logRelay) deliver(batch logBatch) {
	deadline := batch.enqueuedAt.Add(logBatchTtl)
	if !time.Now().Before(deadline) {
		log.Info(
			"k8s-app maintenance %s: a stale progress log batch was discarded",
			relay.worker.nodeName,
		)
		return
	}

	ctx, cancel := context.WithDeadline(context.Background(), deadline)
	defer cancel()

	var lastErr error
	for attempt := 0; attempt < logSendAttempts; attempt++ {
		lastErr = shared.NodeAgentClientAppendLog(ctx, batch.nodeName, batch.nodeIp, batch.lines, batch.operationUid)
		if lastErr == nil {
			return
		}

		if ctx.Err() != nil {
			log.Info(
				"k8s-app maintenance %s: a stale progress log batch was discarded",
				relay.worker.nodeName,
			)
			return
		}

		select {
		case <-ctx.Done():
			log.Info(
				"k8s-app maintenance %s: a stale progress log batch was discarded",
				relay.worker.nodeName,
			)
			return
		case <-time.After(logSendRetryDelay):
		}
	}

	log.Warn(
		"k8s-app maintenance %s: the progress log could not be delivered after %d attempts: %s",
		relay.worker.nodeName,
		logSendAttempts,
		lastErr,
	)
}

func resetNodeLog(nodeName string, operationUid string) {
	record, err := readClusterRecord()
	if err != nil {
		log.Warn(
			"k8s-app maintenance %s: the node log could not be reset: could not read the cluster record: %s",
			nodeName,
			err,
		)
		return
	}

	nodeRecord, known := upgradeNodeRecord(record, nodeName)
	if !known || nodeRecord.IpAddress == "" {
		log.Warn(
			"k8s-app maintenance %s: the node log could not be reset: the node has no IP address",
			nodeName,
		)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), nodeLogResetTimeout)
	defer cancel()

	if err := shared.NodeAgentClientResetLog(ctx, nodeName, nodeRecord.IpAddress, operationUid); err != nil {
		log.Warn(
			"k8s-app maintenance %s: the node log could not be reset: %s",
			nodeName,
			err,
		)
	}
}

func operationLogStage(worker *nodeWorker, stage string, lines ...string) {
	if worker == nil {
		return
	}

	changed := false
	err := worker.mutate(func(op *Operation) bool {
		if op.LogStage == stage {
			return false
		}
		changed = true
		op.LogStage = stage
		op.LogStageUpdatedAt = time.Now().UTC()
		return true
	})
	if err != nil {
		logMutateFailure(worker.nodeName, err)
		return
	}
	if !changed {
		return
	}

	if worker.logs != nil {
		worker.logs.submit(lines)
	}
}

func operationLogLines(worker *nodeWorker, lines ...string) {
	if worker == nil || worker.logs == nil {
		return
	}
	worker.logs.submit(lines)
}

type drainLogger struct {
	worker          *nodeWorker
	lastDeletable   int
	lastTerminating int
	lastBlocked     int
	reported        bool
}

func (drainLog *drainLogger) report(deletable int, terminating int, blockers int) {
	if drainLog.worker == nil {
		return
	}

	if drainLog.reported && deletable == drainLog.lastDeletable &&
		terminating == drainLog.lastTerminating && blockers == drainLog.lastBlocked {
		return
	}
	drainLog.reported = true
	drainLog.lastDeletable = deletable
	drainLog.lastTerminating = terminating
	drainLog.lastBlocked = blockers

	line := fmt.Sprintf("Draining %s: %d pods to remove, %d pods terminating", drainLog.worker.nodeName, deletable, terminating)
	if blockers > 0 {
		line = fmt.Sprintf(
			"Draining %s: %d pods to remove, %d pods terminating, %d pods blocked",
			drainLog.worker.nodeName,
			deletable,
			terminating,
			blockers,
		)
	}
	operationLogLines(drainLog.worker, line)
}
