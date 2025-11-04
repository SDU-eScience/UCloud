package foundation

import "ucloud.dk/shared/pkg/util"

type StreamMessageType string

const (
	StreamMsgTask         StreamMessageType = "TASK"
	StreamMsgNotification StreamMessageType = "NOTIFICATION"
	StreamMsgProjects     StreamMessageType = "PROJECTS"
)

type StreamMessage struct {
	Type         StreamMessageType          `json:"type"`
	Task         util.Option[*Task]         `json:"task,omitempty"`
	Notification util.Option[*Notification] `json:"notification,omitempty"`
	// NOTE(Dan): Projects have no payload
}

type StreamMessageBatch struct {
	Messages []StreamMessage `json:"messages"`
}
