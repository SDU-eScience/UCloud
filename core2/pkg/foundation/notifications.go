package foundation

import (
	"encoding/json"
	"time"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initNotifications() {
	fndapi.NotificationsCreate.Handler(func(info rpc.RequestInfo, request fndapi.NotificationsCreateRequest) (util.Empty, *util.HttpError) {
		CreateNotifications([]fndapi.NotificationsCreateRequest{request})
		return util.Empty{}, nil
	})

	fndapi.NotificationsCreateBulk.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.NotificationsCreateRequest]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		CreateNotifications(request.Items)
		var resp fndapi.BulkResponse[util.Empty]
		resp.Responses = make([]util.Empty, len(request.Items))
		return resp, nil
	})

	fndapi.NotificationsList.Handler(func(info rpc.RequestInfo, request fndapi.NotificationsListRequest) (fndapi.Page[fndapi.Notification], *util.HttpError) {
		return BrowseNotifications(info.Actor, request), nil
	})

	fndapi.NotificationsMarkAsRead.Handler(func(info rpc.RequestInfo, request fndapi.NotificationIds) (util.Empty, *util.HttpError) {
		MarkNotificationAsRead(info.Actor, request.IdList())
		return util.Empty{}, nil
	})

	fndapi.NotificationsMarkAllAsRead.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
		MarkAllNotificationsAsRead(info.Actor)
		return util.Empty{}, nil
	})

	fndapi.NotificationsRetrieveSettings.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.NotificationSettings, *util.HttpError) {
		return RetrieveNotificationSettings(info.Actor), nil
	})

	fndapi.NotificationsUpdateSettings.Handler(func(info rpc.RequestInfo, request fndapi.NotificationSettings) (util.Empty, *util.HttpError) {
		UpdateNotificationSettings(info.Actor, request)
		return util.Empty{}, nil
	})
}

func CreateNotifications(notifications []fndapi.NotificationsCreateRequest) {
	var messages []string
	var users []string
	var types []string
	var meta []string

	for _, reqItem := range notifications {
		notification := reqItem.Notification
		messages = append(messages, notification.Message)
		users = append(users, reqItem.User)
		types = append(types, notification.Type)
		meta = append(meta, string(notification.Meta.Value))
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				with requests as (
					select
						unnest(:messages) as message,
						unnest(:users) as username,
						unnest(:types) as notification_type,
						unnest(:meta) as meta
				)
				insert into notification.notifications(id, created_at, message, meta, modified_at, owner, read, type) 
				select 
					nextval('notification.hibernate_sequence') as id,
					now() as created_at,
					message,
					meta,
					now(),
					username,
					false,
					notification_type
				from
					requests
		    `,
			db.Params{
				"messages": messages,
				"users":    users,
				"types":    types,
				"meta":     meta,
			},
		)
	})

	// TODO Notify users
}

func MarkNotificationAsRead(actor rpc.Actor, ids []int64) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update notification.notifications
				set
					read = true
				where
					id = some(:ids)
					and owner = :username
		    `,
			db.Params{
				"ids":      ids,
				"username": actor.Username,
			},
		)
	})
}

func MarkAllNotificationsAsRead(actor rpc.Actor) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update notification.notifications
				set read = true
				where owner = :username
		    `,
			db.Params{
				"username": actor.Username,
			},
		)
	})
}

func BrowseNotifications(actor rpc.Actor, request fndapi.NotificationsListRequest) fndapi.Page[fndapi.Notification] {
	return db.NewTx(func(tx *db.Transaction) fndapi.Page[fndapi.Notification] {
		// NOTE(Dan): This is now mostly prepared for the new pagination API. It does not live up to the old one at all.

		limit := min(250, request.ItemsPerPage.GetOrDefault(250))
		offset := request.Page.GetOrDefault(0) * request.ItemsPerPage.GetOrDefault(250)

		rows := db.Select[struct {
			Id        int64
			CreatedAt time.Time
			Message   string
			Meta      string
			Type      string
			Read      bool
		}](
			tx,
			`
				select id, created_at, message, meta, type, read
				from notification.notifications
				where
					owner = :username
					and (
						:since = 0
						or created_at >= to_timestamp(:since / 1000.0)
					)
					and (
						:type = ''
						or type = :type
					)
				order by created_at desc
				limit :limit
				offset :offset
		    `,
			db.Params{
				"since":    request.Since.GetOrDefault(fndapi.TimeFromUnixMilli(0)).UnixMilli(),
				"type":     request.Type.GetOrDefault(""),
				"username": actor.Username,
				"limit":    limit + 1,
				"offset":   offset,
			},
		)

		var result fndapi.Page[fndapi.Notification]
		result.ItemsPerPage = limit
		result.PageNumber = offset / limit

		for i, row := range rows {
			if i == limit {
				result.ItemsInTotal = offset + limit + 1
				break
			}

			result.Items = append(result.Items, fndapi.Notification{
				Type:    row.Type,
				Message: row.Message,
				Id:      util.OptValue(row.Id),
				Meta:    util.OptValue(json.RawMessage(row.Meta)),
				Ts:      fndapi.Timestamp(row.CreatedAt),
				Read:    row.Read,
			})
		}

		return result
	})
}

func RetrieveNotificationSettings(actor rpc.Actor) fndapi.NotificationSettings {
	return db.NewTx(func(tx *db.Transaction) fndapi.NotificationSettings {
		row, ok := db.Get[struct{ Settings string }](
			tx,
			`select settings from notification.notification_settings where username = :username`,
			db.Params{
				"username": actor.Username,
			},
		)

		if !ok {
			return fndapi.DefaultNotificationSettings()
		}

		var result fndapi.NotificationSettings
		err := json.Unmarshal([]byte(row.Settings), &result)
		if err != nil {
			return fndapi.DefaultNotificationSettings()
		} else {
			return result
		}
	})
}

func UpdateNotificationSettings(actor rpc.Actor, settings fndapi.NotificationSettings) {
	settingsJson, _ := json.Marshal(settings)
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into notification.notification_settings(username, settings) 
				values (:username, :settings)
				on conflict (username) do update set settings = excluded.settings
		    `,
			db.Params{
				"username": actor.Username,
				"settings": string(settingsJson),
			},
		)
	})
}
