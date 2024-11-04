package controller

import (
	"bytes"
	"encoding/binary"
	"encoding/json"
	"fmt"
	ws "github.com/gorilla/websocket"
	"net/http"
	"strings"
	"time"
	"ucloud.dk/pkg/apm"
	"ucloud.dk/pkg/client"
	db "ucloud.dk/pkg/database"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

var ApmHandler ApmService

type ApmService struct {
	HandleNotification func(update *NotificationWalletUpdated)
}

var userReplayChannel chan string

func initEvents() {
	getLastKnownProjectIpc.Handler(func(r *ipc.Request[string]) ipc.Response[apm.Project] {
		projectId := r.Payload
		if BelongsToWorkspace(apm.WalletOwnerProject(projectId), r.Uid) {
			project, ok := GetLastKnownProject(projectId)
			if ok {
				return ipc.Response[apm.Project]{
					StatusCode: http.StatusOK,
					Payload:    project,
				}
			}
		}
		return ipc.Response[apm.Project]{
			StatusCode: http.StatusNotFound,
		}
	})

	userReplayChannel = make(chan string)

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

			handleSession(c, userReplayChannel, replayFrom)

			if util.IsAlive {
				time.Sleep(5 * time.Second)
			}
		}
	}()
}

func handleNotification(nType NotificationMessageType, notification any) {
	walletHandler := ApmHandler.HandleNotification
	if walletHandler == nil {
		walletHandler = func(notification *NotificationWalletUpdated) {
			log.Info("Ignoring wallet notification")
		}
	}

	projectHandler := IdentityManagement.HandleProjectNotification
	if projectHandler == nil {
		projectHandler = func(updated *NotificationProjectUpdated) bool {
			log.Info("Ignoring project update")
			return true
		}
	}

	switch nType {
	case NotificationMessageWalletUpdated:
		success := true
		update := notification.(*NotificationWalletUpdated)

		if update.Project.IsSet() && !update.Project.Get().Specification.CanConsumeResources {
			// ignore allocator projects
			return
		}

		trackAllocation(update)

		log.Info("Handling wallet event %v %v %v %v %v", update.Owner.Username, update.Owner.ProjectId,
			update.Category.Name, update.CombinedQuota, update.Locked)

		if LaunchUserInstances {
			if update.Owner.Type == apm.WalletOwnerTypeUser {
				_, ok := MapUCloudToLocal(update.Owner.Username)
				if ok {
					walletHandler(update)
				} else {
					log.Info("Could not map user %v", update.Owner.Username)
					success = false
				}
			} else {
				walletHandler(update)
			}
		}

		if success {
			setReplayFrom()
		}

	case NotificationMessageProjectUpdated:
		update := notification.(*NotificationProjectUpdated)

		before, _ := GetLastKnownProject(update.Project.Id)
		update.ProjectComparison = compareProjects(before, update.Project)

		if projectHandler(update) {
			project := update.Project
			realMembers := project.Status.Members
			var usernames []string
			for _, member := range realMembers {
				usernames = append(usernames, member.Username)
			}

			var knownMembers []apm.ProjectMember
			mappedUsers := MapUCloudUsersToLocalUsers(usernames)
			for _, member := range realMembers {
				_, ok := mappedUsers[member.Username]
				if !ok {
					continue
				}

				knownMembers = append(knownMembers, member)
			}

			project.Status.Members = knownMembers
			saveLastKnownProject(project)

			for _, newMember := range update.ProjectComparison.MembersAddedToProject {
				uid, ok := MapUCloudToLocal(newMember)
				if ok {
					RequestUserTermination(uid)
				}
			}
		}

		for _, callback := range projectNotificationCallbacks {
			callback(update)
		}

		setReplayFrom()
	}
}

func setReplayFrom() {
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
	OpAuth         uint8 = 0
	OpWallet       uint8 = 1
	OpProject      uint8 = 2
	OpCategoryInfo uint8 = 3
	OpUserInfo     uint8 = 4
	OpReplayUser   uint8 = 5
)

func handleSession(session *ws.Conn, userReplayChannel chan string, replayFrom int64) {
	defer util.SilentClose(session)

	projects := make(map[uint32]apm.Project)
	products := make(map[uint32]apm.ProductCategory)
	users := make(map[uint32]string)

	writeBuf := buf{}
	writeBuf.PutU8(OpAuth)
	writeBuf.PutU64(uint64(replayFrom))
	writeBuf.PutU64(0) // flags (unused)
	writeBuf.PutString(client.DefaultClient.RetrieveAccessTokenOrRefresh())

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
				case OpWallet:
					workspaceRef := readBuf.GetU32()
					categoryRef := readBuf.GetU32()
					combinedQuota := readBuf.GetU64()
					flags := readBuf.GetU32()
					lastUpdate := readBuf.GetU64()

					isLocked := (flags & 0x1) != 0
					isProject := (flags & 0x2) != 0

					notification := NotificationWalletUpdated{
						CombinedQuota: combinedQuota,
						LastUpdate:    fnd.TimeFromUnixMilli(lastUpdate),
						Locked:        isLocked,
						Category:      products[categoryRef],
					}

					if isProject {
						notification.Owner.Type = apm.WalletOwnerTypeProject
						notification.Owner.ProjectId = projects[workspaceRef].Id
						notification.Project.Set(projects[workspaceRef])
					} else {
						notification.Owner.Type = apm.WalletOwnerTypeUser
						notification.Owner.Username = users[workspaceRef]
					}

					handleNotification(NotificationMessageWalletUpdated, &notification)

				case OpCategoryInfo:
					ref := readBuf.GetU32()
					categoryJson := readBuf.GetString()
					var category apm.ProductCategory
					err := json.Unmarshal([]byte(categoryJson), &category)
					if err != nil {
						log.Warn("Failed to read product category: %v\n%v", err, categoryJson)
						return
					}

					products[ref] = category

				case OpUserInfo:
					ref := readBuf.GetU32()
					username := readBuf.GetString()
					users[ref] = username

				case OpProject:
					ref := readBuf.GetU32()
					lastUpdated := fnd.TimeFromUnixMilli(readBuf.GetU64())
					projectJson := readBuf.GetString()
					var project apm.Project
					err := json.Unmarshal([]byte(projectJson), &project)
					if err != nil {
						log.Warn("Failed to read project: %v\n%v", err, projectJson)
						return
					}

					projects[ref] = project

					notification := NotificationProjectUpdated{
						LastUpdate: lastUpdated,
						Project:    project,
					}

					handleNotification(NotificationMessageProjectUpdated, &notification)
				default:
					log.Warn("Invalid APM opcode received: %v", op)
				}
			}

		case replayUser := <-userReplayChannel:
			writeBuf.Reset()
			writeBuf.PutU8(OpReplayUser)
			writeBuf.PutString(replayUser)
			_ = session.WriteMessage(ws.BinaryMessage, writeBuf.Bytes())
		}
	}
}

type NotificationMessageType int

const (
	NotificationMessageWalletUpdated NotificationMessageType = iota
	NotificationMessageProjectUpdated
)

type NotificationWalletUpdated struct {
	Owner         apm.WalletOwner
	Category      apm.ProductCategory
	CombinedQuota uint64
	Locked        bool
	LastUpdate    fnd.Timestamp
	Project       util.Option[apm.Project]
}

type NotificationProjectUpdated struct {
	LastUpdate fnd.Timestamp
	Project    apm.Project
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

func saveLastKnownProject(project apm.Project) {
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

var getLastKnownProjectIpc = ipc.NewCall[string, apm.Project]("event.getlastknownproject")

func GetLastKnownProject(projectId string) (apm.Project, bool) {
	if RunsServerCode() {
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
			return apm.Project{}, false
		}

		var result apm.Project
		err := json.Unmarshal([]byte(jsonData), &result)
		if err != nil {
			log.Warn("Could not unmarshal last known project %v -> %v", projectId, jsonData)
			return apm.Project{}, false
		}
		return result, true
	} else {
		project, err := getLastKnownProjectIpc.Invoke(projectId)
		if err != nil {
			return apm.Project{}, false
		}
		return project, true
	}
}

func BelongsToWorkspace(workspace apm.WalletOwner, uid uint32) bool {
	ucloudUser, ok := MapLocalToUCloud(uid)
	if !ok {
		return false
	}

	if workspace.Type == apm.WalletOwnerTypeUser {
		return ucloudUser == workspace.Username
	} else {
		project, ok := GetLastKnownProject(workspace.ProjectId)
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

func compareProjects(before apm.Project, after apm.Project) ProjectComparison {
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
	Owner         apm.WalletOwner
	Category      string
	CombinedQuota uint64
	Locked        bool
	LastUpdate    fnd.Timestamp
}

func trackAllocation(update *NotificationWalletUpdated) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into tracked_allocations(owner_username, owner_project, category, combined_quota,
					locked, last_update)
				values (:owner_username, :owner_project, :category, :combined_quota, :locked, now())
				on conflict(category, owner_username, owner_project) do update set
					combined_quota = excluded.combined_quota,
					locked = excluded.locked,
					last_update = excluded.last_update
			`,
			db.Params{
				"owner_username": update.Owner.Username,
				"owner_project":  update.Owner.ProjectId,
				"category":       update.Category.Name,
				"combined_quota": update.CombinedQuota,
				"locked":         update.Locked,
			},
		)
	})
}

func FindAllAllocations(categoryName string) []TrackedAllocation {
	return db.NewTx[[]TrackedAllocation](func(tx *db.Transaction) []TrackedAllocation {
		var result []TrackedAllocation
		rows := db.Select[struct {
			OwnerUsername string
			OwnerProject  string
			Category      string
			CombinedQuota uint64
			LastUpdate    time.Time
			Locked        bool
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
				Owner:         apm.WalletOwnerFromIds(row.OwnerUsername, row.OwnerProject),
				Category:      row.Category,
				CombinedQuota: row.CombinedQuota,
				Locked:        row.Locked,
				LastUpdate:    fnd.Timestamp(row.LastUpdate),
			})
		}

		return result
	})
}
