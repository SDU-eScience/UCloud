package orchestrator

import (
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initIngresses() {
	orcapi.IngressesBrowse.Handler(func(info rpc.RequestInfo, request orcapi.IngressesBrowseRequest) (fndapi.PageV2[orcapi.Ingress], *util.HttpError) {
		return fndapi.EmptyPage[orcapi.Ingress](), nil
	})

	orcapi.IngressesControlBrowse.Handler(func(info rpc.RequestInfo, request orcapi.IngressesControlBrowseRequest) (fndapi.PageV2[orcapi.Ingress], *util.HttpError) {
		return fndapi.EmptyPage[orcapi.Ingress](), nil
	})
}
