package orchestrator

import (
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initLicenses() {
	orcapi.LicensesBrowse.Handler(func(info rpc.RequestInfo, request orcapi.LicensesBrowseRequest) (fndapi.PageV2[orcapi.License], *util.HttpError) {
		return fndapi.EmptyPage[orcapi.License](), nil
	})

	orcapi.LicensesControlBrowse.Handler(func(info rpc.RequestInfo, request orcapi.LicensesControlBrowseRequest) (fndapi.PageV2[orcapi.License], *util.HttpError) {
		return fndapi.EmptyPage[orcapi.License](), nil
	})
}
