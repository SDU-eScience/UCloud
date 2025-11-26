package foundation

import (
	"context"
	"database/sql"
	"encoding/json"
	"runtime"
	"sync"
	"time"

	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Introduction
// =====================================================================================================================
// This file implements the notification subsystem. It provides a unified, generic notification channel for other parts
// of UCloud. This mechanism allows for both live push delivery of notifications and storing notifications persistently
// for catching up to notifications that were received while a user is offline.
//
// The subsystem provides:
// - Persistent user notifications stored in the database.
// - Per-user notification settings persisted in the database.
// - Push delivery to active subscribers via WebSockets

// Core types and globals
// =====================================================================================================================
// - Notification: A message with a type, optional metadata, timestamp, and read/unread state.
// - Notification settings: User-configurable preferences controlling how notifications behave.
// - Subscription: A live connection (via WebSockets) that receives notifications in real-time.

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

// Initialization and RPC
// ======================================================================================================================

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

// Read API
// =====================================================================================================================

func NotificationsBrowse(actor rpc.Actor, request fndapi.NotificationsListRequest) fndapi.Page[fndapi.Notification] {
	return db.NewTx(func(tx *db.Transaction) fndapi.Page[fndapi.Notification] {
		// NOTE(Dan): This is now mostly prepared for the new pagination API. It does not live up to the old one at all.

		limit := min(250, request.ItemsPerPage.GetOrDefault(250))
		offset := request.Page.GetOrDefault(0) * limit

		rows := db.Select[struct {
			Id        int64
			CreatedAt time.Time
			Message   string
			Meta      sql.Null[string]
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

			meta := util.OptNone[json.RawMessage]()
			if row.Meta.Valid {
				meta.Set(json.RawMessage(row.Meta.V))
			}

			result.Items = append(result.Items, fndapi.Notification{
				Type:    row.Type,
				Message: row.Message,
				Id:      util.OptValue(row.Id),
				Meta:    meta,
				Ts:      fndapi.Timestamp(row.CreatedAt),
				Read:    row.Read,
			})
		}

		return result
	})
}

// Notification creation
// =====================================================================================================================
// This sections implements the creation of a notification. After a notification has been persisted, an event is sent
// out to all active subscriptions for the user.

func NotificationsCreate(notifications []fndapi.NotificationsCreateRequest) {
	ids := db.NewTx(func(tx *db.Transaction) []int64 {
		var idPromises []*util.Option[struct{ Id int64 }]
		b := db.BatchNew(tx)
		for _, reqItem := range notifications {
			if reqItem.Notification.Type == "JOB_STARTED" || reqItem.Notification.Type == "JOB_COMPLETED" {
				settings := notificationsRetrieveSettings(tx, reqItem.User)
				if !settings.JobStarted || !settings.JobStopped {
					continue
				}
			}

			meta := util.OptNone[string]()
			if reqItem.Notification.Meta.Present {
				meta.Set(string(reqItem.Notification.Meta.Value))
			}

			promise := db.BatchGet[struct{ Id int64 }](
				b,
				`
					insert into notification.notifications(id, created_at, message, meta, modified_at, owner, read, type) 
					values (nextval('notification.hibernate_sequence'), now(), :message, :meta, now(), :username, false, :type)
					returning id
				`,
				db.Params{
					"username": reqItem.User,
					"type":     reqItem.Notification.Type,
					"meta":     meta.Sql(),
					"message":  reqItem.Notification.Message,
				},
			)

			idPromises = append(idPromises, promise)
		}
		db.BatchSend(b)

		ids := make([]int64, len(notifications))
		for idx, promise := range idPromises {
			if promise != nil && promise.Present {
				ids[idx] = promise.Value.Id
			}
		}

		return ids
	})

	for i, notification := range notifications {
		notification.Notification.Id.Set(ids[i])
		notification.Notification.Ts = fndapi.Timestamp(time.Now())
		notificationNotify(notification.User, notification.Notification)
	}
}

// Read state
// =====================================================================================================================
// The API keeps track of which notifications have been read. These functions run when a user clicks on a notification
// or explicitly asks that all notifications be marked as read.

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

// Subscription management
// =====================================================================================================================
// This section handles live notification delivery to connected clients. Subscriptions are managed entirely in memory.
// Subscriptions are registered when an end-user subscribes and automatically removed when the associated context
// (from WebSocket conn) is cancelled. Events are automatically delivered to all active subscriptions for a given user.

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

// Notification settings
// =====================================================================================================================

func notificationsRetrieveSettings(tx *db.Transaction, username string) fndapi.NotificationSettings {
	row, ok := db.Get[struct{ Settings string }](
		tx,
		`select settings from notification.notification_settings where username = :username`,
		db.Params{
			"username": username,
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
}

func NotificationsRetrieveSettings(actor rpc.Actor) fndapi.NotificationSettings {
	return db.NewTx(func(tx *db.Transaction) fndapi.NotificationSettings {
		return notificationsRetrieveSettings(tx, actor.Username)
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
