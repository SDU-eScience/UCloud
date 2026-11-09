package command

import (
	"context"
	"encoding/json"
	"fmt"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

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
	ctx          context.Context
	cancel       context.CancelFunc
	mu           sync.Mutex
	tracked      map[string]bool
	stages       map[string]k8sJobStage
	widgetStages map[string]bool
	listener     func(hostname string, stage k8sJobStage)
}

type k8sJobStageStreamKey struct {
	rank    int
	channel string
}

type k8sJobStageWidget struct {
	Id   string `json:"id"`
	Type int    `json:"type"`
}

type k8sJobStageStream struct {
	buffer string
	state  int
	action int
	widget k8sJobStageWidget
}

const (
	k8sJobStageLabelId    = "ucloud-init-stage"
	k8sJobStageProgressId = "ucloud-init-progress"
)

const (
	k8sJobStageActionCreate = iota
	k8sJobStageActionUpdate
	k8sJobStageActionDelete
)

const (
	k8sJobStageWidgetLabel = iota
	k8sJobStageWidgetProgress
)

const (
	k8sJobStageReadHeader = iota
	k8sJobStageReadWidget
	k8sJobStageReadValue
)

var k8sJobStageLinePattern = regexp.MustCompile(`^\[([0-9]+(?:\.[0-9]+)?%|\.\.\.)\]\s+(.+)$`)

func k8sJobStagesNew(parent context.Context) *k8sJobStages {
	ctx, cancel := context.WithCancel(parent)
	return &k8sJobStages{
		ctx:          ctx,
		cancel:       cancel,
		tracked:      map[string]bool{},
		stages:       map[string]k8sJobStage{},
		widgetStages: map[string]bool{},
	}
}

func (s *k8sJobStages) Stop() {
	s.cancel()
}

func (s *k8sJobStages) SetListener(listener func(hostname string, stage k8sJobStage)) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.listener = listener
	if listener != nil {
		for hostname, stage := range s.stages {
			listener(hostname, stage)
		}
	}
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
	for s.ctx.Err() == nil {
		conn, err := k8sJobFollowDial(s.ctx, jobId)
		if err == nil {
			stopClose := context.AfterFunc(s.ctx, func() {
				_ = conn.Close()
			})
			s.followConnection(hostname, conn)
			stopClose()
			_ = conn.Close()
		}

		timer := time.NewTimer(2 * time.Second)
		select {
		case <-s.ctx.Done():
			timer.Stop()
			return
		case <-timer.C:
		}
	}
}

func (s *k8sJobStages) followConnection(hostname string, conn *websocket.Conn) {
	buffers := map[k8sJobStageStreamKey]string{}
	widgetStreams := map[k8sJobStageStreamKey]*k8sJobStageStream{}
	for {
		_, raw, err := conn.ReadMessage()
		if err != nil {
			return
		}

		var message rpc.WSResponseMessage[orcapi.JobsFollowMessage]
		err = json.Unmarshal(raw, &message)
		if err != nil {
			continue
		}

		for _, entry := range message.Payload.Log {
			text := entry.Stdout.GetOrDefault("")
			if text == "" {
				text = entry.Stderr.GetOrDefault("")
			}
			if text == "" {
				continue
			}

			channel := entry.Channel.GetOrDefault("")
			key := k8sJobStageStreamKey{
				rank:    entry.Rank,
				channel: channel,
			}
			if channel == "ui" || channel == "data" {
				stream := widgetStreams[key]
				if stream == nil {
					stream = &k8sJobStageStream{}
					widgetStreams[key] = stream
				}
				s.applyWidgetChunk(hostname, stream, text)
				continue
			}
			if channel != "" && channel != "serial" {
				continue
			}

			buffer := buffers[key] + text
			for {
				index := strings.IndexByte(buffer, '\n')
				if index < 0 {
					break
				}
				s.applyLine(hostname, buffer[:index])
				buffer = buffer[index+1:]
			}
			buffers[key] = buffer
		}
	}
}

func (s *k8sJobStages) applyWidgetChunk(hostname string, stream *k8sJobStageStream, chunk string) {
	s.mu.Lock()
	defer s.mu.Unlock()

	stream.buffer += chunk
	for {
		index := strings.IndexByte(stream.buffer, '\n')
		if index < 0 {
			return
		}

		line := stream.buffer[:index]
		stream.buffer = stream.buffer[index+1:]
		s.applyWidgetLine(hostname, stream, line)
	}
}

func (s *k8sJobStages) applyWidgetLine(hostname string, stream *k8sJobStageStream, line string) {
	switch stream.state {
	case k8sJobStageReadHeader:
		var header struct {
			Action *int `json:"action"`
		}
		err := json.Unmarshal([]byte(line), &header)
		if err != nil || header.Action == nil {
			return
		}
		if *header.Action < k8sJobStageActionCreate || *header.Action > k8sJobStageActionDelete {
			return
		}
		stream.action = *header.Action
		stream.state = k8sJobStageReadWidget

	case k8sJobStageReadWidget:
		if stream.action == k8sJobStageActionDelete {
			stream.state = k8sJobStageReadHeader
			return
		}
		var widget k8sJobStageWidget
		err := json.Unmarshal([]byte(line), &widget)
		if err != nil {
			stream.state = k8sJobStageReadHeader
			return
		}
		stream.widget = widget
		stream.state = k8sJobStageReadValue

	case k8sJobStageReadValue:
		stream.state = k8sJobStageReadHeader
		isLabel := stream.action == k8sJobStageActionCreate &&
			stream.widget.Id == k8sJobStageLabelId &&
			stream.widget.Type == k8sJobStageWidgetLabel
		isProgress := (stream.action == k8sJobStageActionCreate || stream.action == k8sJobStageActionUpdate) &&
			stream.widget.Id == k8sJobStageProgressId &&
			stream.widget.Type == k8sJobStageWidgetProgress
		if !isLabel && !isProgress {
			return
		}

		stage := s.stages[hostname]
		if isLabel {
			var label struct {
				Text string `json:"text"`
			}
			err := json.Unmarshal([]byte(line), &label)
			if err != nil || strings.TrimSpace(label.Text) == "" {
				return
			}

			stage.Text = label.Text
		} else {
			var progress struct {
				Progress *float64 `json:"progress"`
			}
			err := json.Unmarshal([]byte(line), &progress)
			if err != nil || progress.Progress == nil {
				return
			}

			stage.Progress = min(max(*progress.Progress, 0), 1)
			stage.Known = true
		}
		s.widgetStages[hostname] = true
		s.publishLocked(hostname, stage)
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
	defer s.mu.Unlock()
	if s.widgetStages[hostname] {
		return
	}
	s.publishLocked(hostname, stage)
}

func (s *k8sJobStages) publishLocked(hostname string, stage k8sJobStage) {
	previous, exists := s.stages[hostname]
	changed := !exists || previous != stage
	if changed {
		s.stages[hostname] = stage
	}
	if changed && s.listener != nil {
		s.listener(hostname, stage)
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
