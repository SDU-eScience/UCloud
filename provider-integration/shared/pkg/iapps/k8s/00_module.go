package k8sapi

import "ucloud.dk/shared/pkg/ucx"

type PingRequest struct {
	Message string
}

type PingResponse struct {
	AppName    string
	AppVersion string
	Mode       string
	Headless   bool
	Message    string
}

var Ping = ucx.Rpc[PingRequest, PingResponse]{
	CallName: "k8s.ping",
}
