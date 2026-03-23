package ucxsvc

import "ucloud.dk/shared/pkg/ucx"

type Message struct {
	Message string
}

var Frontend = ucx.Rpc[Message, Message]{CallName: "frontend"}
var Core = ucx.Rpc[Message, Message]{CallName: "core"}
var IM = ucx.Rpc[Message, Message]{CallName: "im"}
