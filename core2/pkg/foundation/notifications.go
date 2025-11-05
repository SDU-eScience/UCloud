package foundation

import (
	"context"
	"encoding/json"
	"runtime"
	"sync"
	"time"

	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var notificationGlobals struct {
	Buckets []*notificationBucket
}

type notificationBucket struct {
	Mu                sync.RWMutex
	UserSubscriptions map[string]map[string]*notificationSubscription // user -> session id -> subscription
}

type notificationSubscription struct {
	SessionId string
	Ctx       context.Context
	Channel   chan fndapi.Notification
}

func notificationBucketByUser(username string) *notificationBucket {
	return notificationGlobals.Buckets[util.NonCryptographicHash(username)%len(notificationGlobals.Buckets)]
}

func initNotifications() {
	for i := 0; i < runtime.NumCPU(); i++ {
		notificationGlobals.Buckets = append(notificationGlobals.Buckets, &notificationBucket{
			UserSubscriptions: map[string]map[string]*notificationSubscription{},
		})
	}

	fndapi.NotificationsCreate.Handler(func(info rpc.RequestInfo, request fndapi.NotificationsCreateRequest) (util.Empty, *util.HttpError) {
		NotificationsCreate([]fndapi.NotificationsCreateRequest{request})
		return util.Empty{}, nil
	})

	fndapi.NotificationsCreateBulk.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.NotificationsCreateRequest]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		NotificationsCreate(request.Items)
		var resp fndapi.BulkResponse[util.Empty]
		resp.Responses = make([]util.Empty, len(request.Items))
		return resp, nil
	})

	fndapi.NotificationsList.Handler(func(info rpc.RequestInfo, request fndapi.NotificationsListRequest) (fndapi.Page[fndapi.Notification], *util.HttpError) {
		return NotificationsBrowse(info.Actor, request), nil
	})

	fndapi.NotificationsMarkAsRead.Handler(func(info rpc.RequestInfo, request fndapi.NotificationIds) (util.Empty, *util.HttpError) {
		NotificationsMarkAsRead(info.Actor, request.IdList())
		return util.Empty{}, nil
	})

	fndapi.NotificationsMarkAllAsRead.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
		NotificationsMarkAllAsRead(info.Actor)
		return util.Empty{}, nil
	})

	fndapi.NotificationsRetrieveSettings.Handler(func(info rpc.RequestInfo, request util.Empty) (fndapi.NotificationSettings, *util.HttpError) {
		return NotificationsRetrieveSettings(info.Actor), nil
	})

	fndapi.NotificationsUpdateSettings.Handler(func(info rpc.RequestInfo, request fndapi.NotificationSettings) (util.Empty, *util.HttpError) {
		NotificationsUpdateSettings(info.Actor, request)
		return util.Empty{}, nil
	})

	followCall := rpc.Call[util.Empty, util.Empty]{
		BaseContext: "notifications",
		Convention:  rpc.ConventionWebSocket,
	}

	followCall.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
		conn := info.WebSocket
		for {
			_, _, err := conn.ReadMessage()
			if err != nil {
				break
			}
		}
		util.SilentClose(conn)

		return util.Empty{}, nil
	})
}

func NotificationsCreate(notifications []fndapi.NotificationsCreateRequest) {
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

	ids := db.NewTx(func(tx *db.Transaction) []struct{ Id int64 } {
		return db.Select[struct{ Id int64 }](
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
				returning id
		    `,
			db.Params{
				"messages": messages,
				"users":    users,
				"types":    types,
				"meta":     meta,
			},
		)
	})

	for i, notification := range notifications {
		notification.Notification.Id.Set(ids[i].Id)
		notificationNotify(notification.User, notification.Notification)
	}
}

func NotificationsMarkAsRead(actor rpc.Actor, ids []int64) {
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

func NotificationsMarkAllAsRead(actor rpc.Actor) {
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

func NotificationsBrowse(actor rpc.Actor, request fndapi.NotificationsListRequest) fndapi.Page[fndapi.Notification] {
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

func NotificationsRetrieveSettings(actor rpc.Actor) fndapi.NotificationSettings {
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

func NotificationsUpdateSettings(actor rpc.Actor, settings fndapi.NotificationSettings) {
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

func NotificationsSubscribe(actor rpc.Actor, ctx context.Context) <-chan fndapi.Notification {
	sub := &notificationSubscription{
		SessionId: util.RandomTokenNoTs(32),
		Ctx:       ctx,
		Channel:   make(chan fndapi.Notification, 128),
	}

	{
		b := notificationBucketByUser(actor.Username)
		b.Mu.Lock()
		subs, ok := b.UserSubscriptions[actor.Username]
		if !ok {
			b.UserSubscriptions[actor.Username] = map[string]*notificationSubscription{}
			subs, ok = b.UserSubscriptions[actor.Username]
		}
		subs[sub.SessionId] = sub
		b.Mu.Unlock()
	}

	go func() {
		<-ctx.Done()

		b := notificationBucketByUser(actor.Username)
		b.Mu.Lock()
		subs, ok := b.UserSubscriptions[actor.Username]
		if ok {
			delete(subs, sub.SessionId)
		}
		b.Mu.Unlock()
	}()

	return sub.Channel
}

func notificationNotify(username string, notification fndapi.Notification) {
	b := notificationBucketByUser(username)

	var subscriptions []*notificationSubscription

	b.Mu.RLock()
	subs, ok := b.UserSubscriptions[username]
	if ok {
		for _, sub := range subs {
			subscriptions = append(subscriptions, sub)
		}
	}
	b.Mu.RUnlock()

	for _, sub := range subscriptions {
		select {
		case <-sub.Ctx.Done():
		case sub.Channel <- notification:
		case <-time.After(30 * time.Millisecond):
		}
	}
}
