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
)

type VmaAgentOpCode uint8

const (
	VmaAgentHeartbeat VmaAgentOpCode = iota
)
