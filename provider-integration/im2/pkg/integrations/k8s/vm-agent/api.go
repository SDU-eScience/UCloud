package vm_agent

import (
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var VmaStream = rpc.Call[util.Empty, util.Empty]{
	BaseContext: "vmagent",
	Convention:  rpc.ConventionWebSocket,
}

type VmaServerOpCode uint8

const (
	VmaSrvSshKeys VmaServerOpCode = iota
	VmaSrvRequestTty
)

type VmaAgentOpCode uint8

const (
	VmaAgentHeartbeat VmaAgentOpCode = iota
)

var VmaTty = rpc.Call[util.Empty, util.Empty]{
	BaseContext: "vmagent/tty",
	Convention:  rpc.ConventionWebSocket,
}

type ShellEvent struct {
	Type ShellEventType
	ShellEventInput
	ShellEventResize
	ShellEventTerminate
}

type ShellEventInput struct {
	Data string
}

type ShellEventResize struct {
	Cols int
	Rows int
}

type ShellEventTerminate struct{}

type ShellEventType string

const (
	ShellEventTypeInit      ShellEventType = "initialize"
	ShellEventTypeInput     ShellEventType = "input"
	ShellEventTypeResize    ShellEventType = "resize"
	ShellEventTypeTerminate ShellEventType = "terminate"
)
