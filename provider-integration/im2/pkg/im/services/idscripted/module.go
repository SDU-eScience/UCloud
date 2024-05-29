package idscripted

import (
	"errors"
	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/services/nopconn"
)

func Init(config *cfg.IdentityManagementScripted) {
	createUser.Script = config.CreateUser
	syncUserGroups.Script = config.SyncUserGroups

	nopconn.Init(func(username string) (uint32, error) {
		resp, ok := createUser.Invoke(createUserRequest{UCloudUsername: username})
		if !ok {
			return 11400, errors.New("failed to create a new user (internal error)")
		}

		return resp.Uid, nil
	})
}

type createUserRequest struct {
	UCloudUsername string `json:"ucloudUsername"`
}

type createUserResponse struct {
	Uid uint32 `json:"uid"`
}

var createUser = ctrl.NewExtension[createUserRequest, createUserResponse]()

type syncUserGroupsRequest struct {
}

type syncUserGroupsResponse struct {
}

var syncUserGroups = ctrl.NewExtension[syncUserGroupsRequest, syncUserGroupsResponse]()
