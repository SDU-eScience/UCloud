package controller

import (
	"fmt"
	"net/http"
	fnd "ucloud.dk/shared/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/shared/pkg/util"
)

var SshKeys SshKeyService

type SshKey struct {
	Id            string              `json:"id"`
	Owner         string              `json:"owner"`
	CreatedAt     fnd.Timestamp       `json:"createdAt"`
	Fingerprint   string              `json:"fingerprint"`
	Specification SshKeySpecification `json:"specification"`
}

type SshKeySpecification struct {
	Title string `json:"title"`
	Key   string `json:"key"`
}

type SshKeyService struct {
	OnKeyUploaded func(username string, keys []SshKey) error
}

func controllerSshKeys(mux *http.ServeMux) {
	baseContext := fmt.Sprintf("/ucloud/%v/ssh/", cfg.Provider.Id)

	if RunsUserCode() {
		type uploadRequest struct {
			Username string   `json:"username"`
			AllKeys  []SshKey `json:"allKeys"`
		}

		mux.HandleFunc(baseContext+"keyUploaded", HttpUpdateHandler(
			0,
			func(w http.ResponseWriter, r *http.Request, request uploadRequest) {
				var err error

				handler := SshKeys.OnKeyUploaded
				if handler != nil {
					err = handler(request.Username, request.AllKeys)
				}

				sendResponseOrError(w, util.Empty{}, err)
			}),
		)
	}
}
