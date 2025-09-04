package orchestrator

import (
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initPublicIps() {
	orcapi.PublicIpsBrowse.Handler(func(info rpc.RequestInfo, request orcapi.PublicIpsBrowseRequest) (fndapi.PageV2[orcapi.PublicIp], *util.HttpError) {
		return fndapi.EmptyPage[orcapi.PublicIp](), nil
	})

	orcapi.PublicIpsControlBrowse.Handler(func(info rpc.RequestInfo, request orcapi.PublicIpsControlBrowseRequest) (fndapi.PageV2[orcapi.PublicIp], *util.HttpError) {
		return fndapi.EmptyPage[orcapi.PublicIp](), nil
	})
}
