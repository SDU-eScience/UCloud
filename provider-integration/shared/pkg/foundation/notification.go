package foundation

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type Notification struct {
	Type    string                       `json:"type"`
	Message string                       `json:"message"`
	Id      util.Option[int64]           `json:"id"`
	Meta    util.Option[json.RawMessage] `json:"meta"`
	Ts      Timestamp                    `json:"ts"`
	Read    bool                         `json:"read"`
}

type NotificationSettings struct {
	JobStarted bool `json:"jobStarted"`
	JobStopped bool `json:"jobStopped"`
}

func DefaultNotificationSettings() NotificationSettings {
	return NotificationSettings{
		JobStarted: true,
		JobStopped: true,
	}
}

const NotificationContext = "notifications"

type NotificationsListRequest struct {
	Type         util.Option[string]    `json:"type"`
	Since        util.Option[Timestamp] `json:"since"`
	ItemsPerPage util.Option[int]       `json:"itemsPerPage"`
	Page         util.Option[int]       `json:"page"`
}

var NotificationsList = rpc.Call[NotificationsListRequest, Page[Notification]]{
	BaseContext: NotificationContext,
	Operation:   "",
	Convention:  rpc.ConventionQueryParameters,
	Roles:       rpc.RolesEndUser,
}

type NotificationIds struct {
	// Ids is a comma-separated list of IDs
	Ids string `json:"ids"`
}

func (i *NotificationIds) IdList() []int64 {
	var result []int64
	split := strings.Split(i.Ids, ",")
	for _, tok := range split {
		tok = strings.TrimSpace(tok)
		parsed, err := strconv.ParseInt(tok, 10, 64)
		if err == nil {
			result = append(result, parsed)
		}
	}
	return result
}

var NotificationsMarkAsRead = rpc.Call[NotificationIds, util.Empty]{
	BaseContext: NotificationContext,
	Operation:   "read",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

var NotificationsMarkAllAsRead = rpc.Call[util.Empty, util.Empty]{
	BaseContext: NotificationContext + "/read",
	Operation:   "all",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}

type NotificationsCreateRequest struct {
	User         string       `json:"user"`
	Notification Notification `json:"notification"`
}

var NotificationsCreate = rpc.Call[NotificationsCreateRequest, util.Empty]{
	BaseContext: NotificationContext,
	Operation:   "create",
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesService,

	CustomPath: "/api/notifications",

	CustomServerParser: func(w http.ResponseWriter, r *http.Request) (NotificationsCreateRequest, *util.HttpError) {
		return rpc.ParseRequestFromBody[NotificationsCreateRequest](w, r)
	},

	CustomClientHandler: func(self *rpc.Call[NotificationsCreateRequest, util.Empty], client *rpc.Client, request NotificationsCreateRequest) (util.Empty, *util.HttpError) {
		resp := rpc.CallViaJsonBody(client, http.MethodPut, self.CustomPath, request)
		_, err := rpc.ParseResponse[util.Empty](resp)
		return util.Empty{}, err
	},
}

var NotificationsCreateBulk = rpc.Call[BulkRequest[NotificationsCreateRequest], BulkResponse[util.Empty]]{
	BaseContext: NotificationContext,
	Operation:   "bulk",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
}

var NotificationsRetrieveSettings = rpc.Call[util.Empty, NotificationSettings]{
	BaseContext: NotificationContext,
	Operation:   "settings",
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

var NotificationsUpdateSettings = rpc.Call[NotificationSettings, util.Empty]{
	BaseContext: NotificationContext,
	Operation:   "settings",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}
