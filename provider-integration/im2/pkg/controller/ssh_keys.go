package controller

import (
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var SshKeys SshKeyService

type SshKeyService struct {
	OnKeyUploaded func(username string, keys []orcapi.SshKey) *util.HttpError
}

func initSshKeys() {
	if RunsUserCode() {
		orcapi.SshProviderKeyUploaded.Handler(func(info rpc.RequestInfo, request orcapi.SshProviderKeyUploadedRequest) (util.Empty, *util.HttpError) {
			var err *util.HttpError

			handler := SshKeys.OnKeyUploaded
			if handler != nil {
				err = handler(request.Username, request.AllKeys)
			}

			return util.Empty{}, err
		})
	}
}
