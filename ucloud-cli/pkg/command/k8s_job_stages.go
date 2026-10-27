package command

import (
	"context"
	"encoding/json"
	"fmt"
	"regexp"
	"strconv"
	"strings"
	"sync"

	"github.com/gorilla/websocket"

	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
)

type k8sJobStage struct {
	Text     string
	Progress float64
	Known    bool
}

type k8sJobStages struct {
	ctx      context.Context
	cancel   context.CancelFunc
	mu       sync.Mutex
	tracked  map[string]bool
	stages   map[string]k8sJobStage
	listener func(hostname string, stage k8sJobStage)
}

var k8sJobStageLinePattern = regexp.MustCompile(`^\[([0-9]+(?:\.[0-9]+)?%|\.\.\.)\]\s+(.+)$`)

func k8sJobStagesNew(parent context.Context) *k8sJobStages {
	ctx, cancel := context.WithCancel(parent)
	return &k8sJobStages{
		ctx:     ctx,
		cancel:  cancel,
		tracked: map[string]bool{},
		stages:  map[string]k8sJobStage{},
	}
}

func (s *k8sJobStages) Stop() {
	s.cancel()
}

func (s *k8sJobStages) SetListener(listener func(hostname string, stage k8sJobStage)) {
	s.mu.Lock()
	s.listener = listener
	s.mu.Unlock()
}

func (s *k8sJobStages) Track(hostname string, jobId string) {
	if hostname == "" || jobId == "" {
		return
	}

	s.mu.Lock()
	if s.tracked[jobId] {
		s.mu.Unlock()
		return
	}
	s.tracked[jobId] = true
	s.mu.Unlock()

	go s.follow(hostname, jobId)
}

func (s *k8sJobStages) Stage(hostname string) (k8sJobStage, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	stage, ok := s.stages[hostname]
	return stage, ok
}

func (s *k8sJobStages) follow(hostname string, jobId string) {
	conn, err := k8sJobFollowDial(s.ctx, jobId)
	if err != nil {
		return
	}
	defer conn.Close()

	finished := make(chan struct{})
	defer close(finished)

	go func() {
		select {
		case <-s.ctx.Done():
			_ = conn.Close()
		case <-finished:
		}
	}()

	buffers := map[int]string{}
	for {
		_, raw, err := conn.ReadMessage()
		if err != nil {
			return
		}

		var message rpc.WSResponseMessage[orcapi.JobsFollowMessage]
		if err := json.Unmarshal(raw, &message); err != nil {
			continue
		}

		for _, entry := range message.Payload.Log {
			switch entry.Channel.GetOrDefault("") {
			case "ui", "data":
				continue
			}

			text := entry.Stdout.GetOrDefault("")
			if text == "" {
				continue
			}

			buffer := buffers[entry.Rank] + text
			for {
				index := strings.IndexByte(buffer, '\n')
				if index < 0 {
					break
				}
				s.applyLine(hostname, buffer[:index])
				buffer = buffer[index+1:]
			}
			buffers[entry.Rank] = buffer
		}
	}
}

func (s *k8sJobStages) applyLine(hostname string, line string) {
	match := k8sJobStageLinePattern.FindStringSubmatch(strings.TrimSpace(line))
	if match == nil {
		return
	}

	text := strings.TrimSpace(match[2])
	if text == "" {
		return
	}

	stage := k8sJobStage{Text: text}
	if match[1] != "..." {
		percent, err := strconv.ParseFloat(strings.TrimSuffix(match[1], "%"), 64)
		if err == nil {
			stage.Progress = min(max(percent/100, 0), 1)
			stage.Known = true
		}
	}

	s.mu.Lock()
	previous, exists := s.stages[hostname]
	changed := !exists || previous != stage
	if changed {
		s.stages[hostname] = stage
	}
	listener := s.listener
	s.mu.Unlock()

	if changed && listener != nil {
		listener(hostname, stage)
	}
}

func k8sJobFollowDial(ctx context.Context, jobId string) (*websocket.Conn, error) {
	if rpc.DefaultClient == nil {
		return nil, fmt.Errorf("the UCloud client is not initialized")
	}

	base := strings.TrimSuffix(strings.TrimSpace(rpc.DefaultClient.BasePath), "/")
	url := base + orcapi.JobsFollowEndpoint
	switch {
	case strings.HasPrefix(url, "https://"):
		url = "wss://" + strings.TrimPrefix(url, "https://")
	case strings.HasPrefix(url, "http://"):
		url = "ws://" + strings.TrimPrefix(url, "http://")
	default:
		return nil, fmt.Errorf("unsupported UCloud address %q", rpc.DefaultClient.BasePath)
	}

	dialer := websocket.Dialer{
		Subprotocols: []string{"binary"},
	}
	conn, _, err := dialer.DialContext(ctx, url, nil)
	if err != nil {
		return nil, err
	}

	request := rpc.WSRequestMessage[fnd.FindByStringId]{
		Call:     "jobs.follow",
		StreamId: "k8s-" + jobId,
		Payload: fnd.FindByStringId{
			Id: jobId,
		},
		Bearer:  rpc.DefaultClient.RetrieveAccessTokenOrRefresh(),
		Project: rpc.DefaultClient.ProjectId,
	}

	if err := conn.WriteJSON(request); err != nil {
		conn.Close()
		return nil, err
	}

	return conn, nil
}
