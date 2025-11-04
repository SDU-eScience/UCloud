package rpc

import (
	"encoding/json"
	"ucloud.dk/shared/pkg/util"
)

type WSRequestMessage[T any] struct {
	Call     string              `json:"call"`
	StreamId string              `json:"streamId"`
	Payload  T                   `json:"payload"`
	Bearer   string              `json:"bearer"`
	Project  util.Option[string] `json:"project"`
}

type WSResponseMessage[T any] struct {
	Type     string `json:"type"`
	StreamId string `json:"streamId"`
	Payload  T      `json:"payload"`
	Status   int    `json:"status,omitempty"`
}

func WSResponseMessageMarshal[T any](streamId string, data T) []byte {
	dataBytes, _ := json.Marshal(WSResponseMessage[T]{StreamId: streamId, Type: "message", Payload: data})
	return dataBytes
}
