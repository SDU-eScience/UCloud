package controller

import (
	"bytes"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"sync"
	"time"

	ws "github.com/gorilla/websocket"
	lru "github.com/hashicorp/golang-lru/v2/expirable"
	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/ipc"
	apm "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var EventHandler EventService

type EventService struct {
	HandleNotification func(update *EventWalletUpdated)
}

var eventUserReplayChannel chan string

func initEvents() {
	eventGetLastKnownProjectIpc.Handler(func(r *ipc.Request[string]) ipc.Response[fnd.Project] {
		projectId := r.Payload
		if BelongsToWorkspace(apm.WalletOwnerProject(projectId), r.Uid) {
			project, ok := ProjectRetrieve(projectId)
			if ok {
				return ipc.Response[fnd.Project]{
					StatusCode: http.StatusOK,
					Payload:    project,
				}
			}
		}
		return ipc.Response[fnd.Project]{
			StatusCode: http.StatusNotFound,
		}
	})

	eventUserReplayChannel = make(chan string)

	go func() {
		for util.IsAlive {
			replayFromTime := db.NewTx[time.Time](func(tx *db.Transaction) time.Time {
				r, _ := db.Get[struct{ LastUpdate time.Time }](
					tx,
					`
						select last_update from apm_events_replay_from where provider_id = :provider_id
				    `,
					db.Params{
						"provider_id": cfg.Provider.Id,
					},
				)
				return r.LastUpdate
			})

			replayFrom := replayFromTime.UnixMilli()

			url := cfg.Provider.Hosts.UCloud.ToURL()
			url = strings.ReplaceAll(url, "http://", "ws://")
			url = strings.ReplaceAll(url, "https://", "wss://")
			url = url + "/api/accounting/notifications"

			c, _, err := ws.DefaultDialer.Dial(url, nil)
			if err != nil {
				log.Warn("Failed to establish WebSocket connection: %v %v", url, err)
				time.Sleep(5 * time.Second)
				continue
			}

			eventHandleSession(c, eventUserReplayChannel, replayFrom)

			if util.IsAlive {
				time.Sleep(5 * time.Second)
			}
		}
	}()
}

func eventHandleNotification(nType EventNotificationMessageType, notification any) {
	walletHandler := EventHandler.HandleNotification
	if walletHandler == nil {
		walletHandler = func(notification *EventWalletUpdated) {
			log.Info("Ignoring wallet notification")
		}
	}

	projectHandler := IdentityManagement.HandleProjectNotification
	if projectHandler == nil {
		projectHandler = func(updated *EventProjectUpdated) bool {
			log.Info("Ignoring project update")
			return true
		}
	}

	switch nType {
	case EventNotificationMessageWalletUpdated:
		success := true
		update := notification.(*EventWalletUpdated)

		if update.Project.IsSet() && !update.Project.Get().Specification.CanConsumeResources {
			// ignore allocator projects
			return
		}

		allocationTrack(update)

		log.Info("Handling wallet event %v %v %v %v %v", update.Owner.Username, update.Owner.ProjectId,
			update.Category.Name, update.CombinedQuota, update.Locked)

		if LaunchUserInstances {
			if update.Owner.Type == apm.WalletOwnerTypeUser {
				_, ok, _ := IdmMapUCloudToLocal(update.Owner.Username)
				if ok {
					walletHandler(update)
				} else {
					log.Info("Could not map user %v", update.Owner.Username)
					success = false
				}
			} else {
				walletHandler(update)
			}
		} else {
			walletHandler(update)
		}

		if success {
			eventUpdateReplayFromToNow()
		}

	case EventNotificationMessageProjectUpdated:
		update := notification.(*EventProjectUpdated)

		before, _ := ProjectRetrieve(update.Project.Id)
		update.ProjectComparison = projectsCompare(before, update.Project)

		if projectHandler(update) {
			project := update.Project

			if LaunchUserInstances {
				realMembers := project.Status.Members
				var usernames []string
				for _, member := range realMembers {
					usernames = append(usernames, member.Username)
				}

				var knownMembers []fnd.ProjectMember
				mappedUsers := IdmMapUCloudUsersToLocalUsers(usernames)
				for _, member := range realMembers {
					_, ok := mappedUsers[member.Username]
					if !ok {
						continue
					}

					knownMembers = append(knownMembers, member)
				}

				project.Status.Members = knownMembers
			}

			eventSaveLastKnownProject(project)

			if LaunchUserInstances {
				for _, newMember := range update.ProjectComparison.MembersAddedToProject {
					uid, ok, _ := IdmMapUCloudToLocal(newMember)
					if ok {
						RequestUserTermination(uid)
					}
				}
			}
		}

		for _, callback := range idmProjectNotificationCallbacks {
			callback(update)
		}

		eventUpdateReplayFromToNow()
	}
}

func eventUpdateReplayFromToNow() {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into apm_events_replay_from(provider_id, last_update) 
				values (:provider_id, now())
				on conflict (provider_id) do update set
					last_update = excluded.last_update
		    `,
			db.Params{
				"provider_id": cfg.Provider.Id,
			},
		)
	})
}

const (
	EvOpAuth         uint8 = 0
	EvOpWallet       uint8 = 1
	EvOpProject      uint8 = 2
	EvOpCategoryInfo uint8 = 3
	EvOpUserInfo     uint8 = 4
	EvOpReplayUser   uint8 = 5
)

const (
	eventIncludeRetired uint64 = 1 << iota
)

func eventHandleSession(session *ws.Conn, userReplayChannel chan string, replayFrom int64) {
	defer util.SilentClose(session)

	projects := make(map[uint32]fnd.Project)
	products := make(map[uint32]apm.ProductCategory)
	users := make(map[uint32]string)

	writeBuf := buf{}
	writeBuf.PutU8(EvOpAuth)
	writeBuf.PutU64(uint64(replayFrom))
	writeBuf.PutU64(eventIncludeRetired) // flags
	writeBuf.PutString(rpc.DefaultClient.RetrieveAccessTokenOrRefresh())

	_ = session.WriteMessage(ws.TextMessage, writeBuf.Bytes())
	writeBuf.Reset()

	socketMessages := make(chan []byte)
	go func() {
		for util.IsAlive {
			_, message, err := session.ReadMessage()
			if err != nil {
				break
			}

			socketMessages <- message
		}

		socketMessages <- nil
	}()

	for util.IsAlive {
		select {
		case message := <-socketMessages:
			if message == nil {
				return
			}

			readBuf := buf{Buffer: *bytes.NewBuffer(message)}

			for readBuf.Len() > 0 {
				op := readBuf.GetU8()
				switch op {
				case EvOpWallet:
					workspaceRef := readBuf.GetU32()
					categoryRef := readBuf.GetU32()
					combinedQuota := readBuf.GetU64()
					flags := readBuf.GetU32()
					lastUpdate := readBuf.GetU64()
					localRetiredUsage := readBuf.GetU64()

					category, ok := products[categoryRef]
					if !ok || category.Name == "" {
						log.Error("Received an APM event with invalid product reference. Refusing to handle this event!")
						return
					}

					isLocked := (flags & 0x1) != 0
					isProject := (flags & 0x2) != 0

					notification := EventWalletUpdated{
						CombinedQuota:     combinedQuota,
						LastUpdate:        fnd.TimeFromUnixMilli(lastUpdate),
						Locked:            isLocked,
						Category:          category,
						LocalRetiredUsage: localRetiredUsage,
					}

					if isProject {
						notification.Owner.Type = apm.WalletOwnerTypeProject
						notification.Owner.ProjectId = projects[workspaceRef].Id
						projectInfo, ok := projects[workspaceRef]
						if !ok || projectInfo.Id == "" {
							log.Error("Received an APM event with invalid project reference. Refusing to handle this event!")
							return
						}
						notification.Project.Set(projectInfo)
					} else {
						notification.Owner.Type = apm.WalletOwnerTypeUser
						userInfo, ok := users[workspaceRef]
						if !ok || userInfo == "" {
							log.Error("Received an APM event with an invalid user reference. Refusing to handle this event!")
							return
						}
						notification.Owner.Username = users[workspaceRef]
					}

					eventHandleNotification(EventNotificationMessageWalletUpdated, &notification)

				case EvOpCategoryInfo:
					ref := readBuf.GetU32()
					categoryJson := readBuf.GetString()
					var category apm.ProductCategory
					err := json.Unmarshal([]byte(categoryJson), &category)
					if err != nil {
						log.Warn("Failed to read product category: %v\n%v", err, categoryJson)
						return
					}

					products[ref] = category

				case EvOpUserInfo:
					ref := readBuf.GetU32()
					username := readBuf.GetString()
					users[ref] = username

				case EvOpProject:
					ref := readBuf.GetU32()
					lastUpdated := fnd.TimeFromUnixMilli(readBuf.GetU64())
					projectJson := readBuf.GetString()
					var project fnd.Project
					err := json.Unmarshal([]byte(projectJson), &project)
					if err != nil {
						log.Warn("Failed to read project: %v\n%v", err, projectJson)
						return
					}

					projects[ref] = project

					notification := EventProjectUpdated{
						LastUpdate: lastUpdated,
						Project:    project,
					}

					eventHandleNotification(EventNotificationMessageProjectUpdated, &notification)
				default:
					log.Warn("Invalid APM opcode received: %v", op)
				}
			}

		case replayUser := <-userReplayChannel:
			writeBuf.Reset()
			writeBuf.PutU8(EvOpReplayUser)
			writeBuf.PutString(replayUser)
			_ = session.WriteMessage(ws.BinaryMessage, writeBuf.Bytes())
		}
	}
}

type EventNotificationMessageType int

const (
	EventNotificationMessageWalletUpdated EventNotificationMessageType = iota
	EventNotificationMessageProjectUpdated
)

type EventWalletUpdated struct {
	Owner             apm.WalletOwner
	Category          apm.ProductCategory
	CombinedQuota     uint64
	Locked            bool
	LastUpdate        fnd.Timestamp
	Project           util.Option[fnd.Project]
	LocalRetiredUsage uint64
}

type EventProjectUpdated struct {
	LastUpdate fnd.Timestamp
	Project    fnd.Project
	ProjectComparison
}

type buf struct {
	bytes.Buffer
	Error error
}

func (b *buf) PutNumeric(value any) {
	if b.Error != nil {
		return
	}

	b.Error = binary.Write(&b.Buffer, binary.BigEndian, value)
}

func (b *buf) PutU8(value uint8) {
	b.PutNumeric(value)
}

func (b *buf) PutU16(value uint16) {
	b.PutNumeric(value)
}

func (b *buf) PutU32(value uint32) {
	b.PutNumeric(value)
}

func (b *buf) PutU64(value uint64) {
	b.PutNumeric(value)
}

func (b *buf) PutS8(value uint8) {
	b.PutNumeric(value)
}

func (b *buf) PutS16(value int16) {
	b.PutNumeric(value)
}

func (b *buf) PutS32(value int32) {
	b.PutNumeric(value)
}

func (b *buf) PutS64(value int64) {
	b.PutNumeric(value)
}

func (b *buf) PutString(value string) {
	if b.Error != nil {
		return
	}

	data := []byte(value)
	b.PutU32(uint32(len(data)))
	_, err := b.Buffer.Write(data)
	b.Error = err
}

func (b *buf) GetNumeric(value any) {
	if b.Error != nil {
		return
	}

	b.Error = binary.Read(&b.Buffer, binary.BigEndian, value)
}

func (b *buf) GetU8() uint8 {
	var result uint8
	b.GetNumeric(&result)
	return result
}

func (b *buf) GetU16() uint16 {
	var result uint16
	b.GetNumeric(&result)
	return result
}

func (b *buf) GetU32() uint32 {
	var result uint32
	b.GetNumeric(&result)
	return result
}

func (b *buf) GetU64() uint64 {
	var result uint64
	b.GetNumeric(&result)
	return result
}

func (b *buf) GetS8() int8 {
	var result int8
	b.GetNumeric(&result)
	return result
}

func (b *buf) GetS16() int16 {
	var result int16
	b.GetNumeric(&result)
	return result
}

func (b *buf) GetS32() int32 {
	var result int32
	b.GetNumeric(&result)
	return result
}

func (b *buf) GetS64() int64 {
	var result int64
	b.GetNumeric(&result)
	return result
}

func (b *buf) GetString() string {
	if b.Error != nil {
		return ""
	}

	size := b.GetU32()
	result := make([]byte, size)
	bytesRead, err := b.Read(result)
	if uint32(bytesRead) != size {
		b.Error = fmt.Errorf("malformed message while reading string. expected %d bytes, read %d", size, bytesRead)
		return ""
	}
	if err != nil {
		b.Error = err
		return ""
	}

	return string(result)
}

func eventSaveLastKnownProject(project fnd.Project) {
	eventProjectCacheServerOnly.Add(project.Id, project)

	data, _ := json.Marshal(project)

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into tracked_projects(project_id, ucloud_project, last_update) 
				values (:project_id, :ucloud_project, now())
				on conflict(project_id) do update set
					ucloud_project = excluded.ucloud_project,
					last_update = excluded.last_update
			`,
			db.Params{
				"project_id":     project.Id,
				"ucloud_project": string(data),
			},
		)
	})
}

var eventGetLastKnownProjectIpc = ipc.NewCall[string, fnd.Project]("event.getlastknownproject")
var eventProjectCacheServerOnly = lru.NewLRU[string, fnd.Project](1024, nil, 10*time.Minute)

func ProjectRetrieve(projectId string) (fnd.Project, bool) {
	if RunsServerCode() {
		result, ok := eventProjectCacheServerOnly.Get(projectId)
		if !ok {
			jsonData, ok := db.NewTx2[string, bool](func(tx *db.Transaction) (string, bool) {
				jsonData, ok := db.Get[struct{ UCloudProject string }](
					tx,
					`
					select ucloud_project
					from tracked_projects
					where project_id = :project_id
				`,
					db.Params{
						"project_id": projectId,
					},
				)
				return jsonData.UCloudProject, ok
			})

			if !ok {
				return fnd.Project{}, false
			}

			var result fnd.Project
			err := json.Unmarshal([]byte(jsonData), &result)
			if err != nil {
				log.Warn("Could not unmarshal last known project %v -> %v", projectId, jsonData)
				return fnd.Project{}, false
			}
			eventProjectCacheServerOnly.Add(projectId, result)
			return result, true
		} else {
			return result, true
		}
	} else {
		project, err := eventGetLastKnownProjectIpc.Invoke(projectId)
		if err != nil {
			return fnd.Project{}, false
		}
		return project, true
	}
}

func BelongsToWorkspace(workspace apm.WalletOwner, uid uint32) bool {
	ucloudUser, ok, _ := IdmMapLocalToUCloud(uid)
	if !ok {
		return false
	}

	if workspace.Type == apm.WalletOwnerTypeUser {
		return ucloudUser == workspace.Username
	} else {
		project, ok := ProjectRetrieve(workspace.ProjectId)
		if !ok {
			return false
		}

		isMember := false
		for _, member := range project.Status.Members {
			if member.Username == ucloudUser {
				isMember = true
				break
			}
		}

		return isMember
	}
}

type ProjectComparison struct {
	MembersAddedToProject     []string
	MembersRemovedFromProject []string
}

func projectsCompare(before fnd.Project, after fnd.Project) ProjectComparison {
	oldMembers := before.Status.Members

	var newMembers []string
	var removedMembers []string
	{
		for _, member := range after.Status.Members {
			found := false
			for _, oldMember := range oldMembers {
				if member.Username == oldMember.Username {
					found = true
					break
				}
			}

			if !found {
				newMembers = append(newMembers, member.Username)
			}
		}

		for _, oldMember := range oldMembers {
			found := false
			for _, newMember := range after.Status.Members {
				if oldMember.Username == newMember.Username {
					found = true
					break
				}
			}

			if !found {
				removedMembers = append(removedMembers, oldMember.Username)
			}
		}
	}

	return ProjectComparison{
		MembersAddedToProject:     newMembers,
		MembersRemovedFromProject: removedMembers,
	}
}

type TrackedAllocation struct {
	Owner             apm.WalletOwner
	Category          string
	CombinedQuota     uint64
	Locked            bool
	LastUpdate        fnd.Timestamp
	LocalRetiredUsage uint64
}

func allocationTrack(update *EventWalletUpdated) {
	cacheKey := lockedCacheKey{Owner: update.Owner, Category: update.Category.Name}
	isLockedCacheMutex.Lock()
	isLockedCache[cacheKey] = update.Locked
	isLockedCacheMutex.Unlock()

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into tracked_allocations(owner_username, owner_project, category, combined_quota,
					locked, last_update, local_retired_usage)
				values (:owner_username, :owner_project, :category, :combined_quota, :locked, now(), :local_retired_usage)
				on conflict(category, owner_username, owner_project) do update set
					combined_quota = excluded.combined_quota,
					locked = excluded.locked,
					last_update = excluded.last_update,
					local_retired_usage = excluded.local_retired_usage
			`,
			db.Params{
				"owner_username":      update.Owner.Username,
				"owner_project":       update.Owner.ProjectId,
				"category":            update.Category.Name,
				"combined_quota":      update.CombinedQuota,
				"locked":              update.Locked,
				"local_retired_usage": update.LocalRetiredUsage,
			},
		)
	})
}

func AllocationsFindAll(categoryName string) []TrackedAllocation {
	return db.NewTx[[]TrackedAllocation](func(tx *db.Transaction) []TrackedAllocation {
		var result []TrackedAllocation
		rows := db.Select[struct {
			OwnerUsername     string
			OwnerProject      string
			Category          string
			CombinedQuota     uint64
			LastUpdate        time.Time
			Locked            bool
			LocalRetiredUsage uint64
		}](
			tx,
			`
				select *
				from tracked_allocations
				where
					category = :category
			`,
			db.Params{
				"category": categoryName,
			},
		)

		for _, row := range rows {
			result = append(result, TrackedAllocation{
				Owner:             apm.WalletOwnerFromIds(row.OwnerUsername, row.OwnerProject),
				Category:          row.Category,
				CombinedQuota:     row.CombinedQuota,
				Locked:            row.Locked,
				LastUpdate:        fnd.Timestamp(row.LastUpdate),
				LocalRetiredUsage: row.LocalRetiredUsage,
			})
		}

		return result
	})
}

type lockedCacheKey struct {
	Owner    apm.WalletOwner
	Category string
}

var isLockedCache = map[lockedCacheKey]bool{}
var isLockedCacheMutex = sync.Mutex{}

func WalletIsLocked(owner apm.WalletOwner, category string) bool {
	if category == "terminal" || category == "syncthing" {
		return false // TODO(Dan): Fix this correctly.
	}

	if RunsServerCode() {
		cacheKey := lockedCacheKey{Owner: owner, Category: category}
		isLockedCacheMutex.Lock()
		locked, isCached := isLockedCache[cacheKey]
		isLockedCacheMutex.Unlock()

		if isCached {
			return locked
		}

		locked = db.NewTx[bool](func(tx *db.Transaction) bool {
			row, ok := db.Get[struct{ Locked bool }](
				tx,
				`
					select a.locked
					from tracked_allocations a
					where
						a.category = :category
						and a.owner_project = :project
						and a.owner_username = :username
				`,
				db.Params{
					"username": owner.Username,
					"project":  owner.ProjectId,
					"category": category,
				},
			)

			if !ok {
				return false
			} else {
				return row.Locked
			}
		})

		isLockedCacheMutex.Lock()
		isLockedCache[cacheKey] = locked
		isLockedCacheMutex.Unlock()
		return locked
	} else {
		panic("WalletIsLocked is only implemented for server mode")
	}
}

func ResourceIsLocked(resource orc.Resource, ref apm.ProductReference) bool {
	return WalletIsLocked(apm.WalletOwnerFromIds(resource.Owner.CreatedBy, resource.Owner.Project.Value), ref.Category)
}
