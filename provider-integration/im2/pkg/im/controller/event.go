package controller

import (
	"bytes"
	"encoding/binary"
	"encoding/json"
	"fmt"
	ws "github.com/gorilla/websocket"
	"strings"
	"time"
	"ucloud.dk/pkg/apm"
	"ucloud.dk/pkg/client"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/kvdb"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

var ApmHandler ApmService

type ApmService struct {
	HandleNotification func(nType NotificationMessageType, notification any)
}

const replayFromKey = "events-replay-from"

var userReplayChannel chan string

func initEvents() {
	userReplayChannel = make(chan string)

	go func() {
		for util.IsAlive {
			replayFrom, _ := kvdb.Get[uint64](replayFromKey)

			url := cfg.Provider.Hosts.UCloud.ToURL()
			url = strings.ReplaceAll(url, "http://", "ws://")
			url = strings.ReplaceAll(url, "https://", "wss://")
			url = url + "/api/accounting/notifications"

			c, _, err := ws.DefaultDialer.Dial(url, nil)
			if err != nil {
				log.Warn("Failed to establish WebSocket connection: %v %v", url, err)
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
	handler := ApmHandler.HandleNotification
	if handler == nil {
		handler = func(_ NotificationMessageType, _ any) {
			log.Info("Ignoring notification event!")
		}
	}

	switch nType {
	case NotificationMessageWalletUpdated:
		success := true
		update := notification.(*NotificationWalletUpdated)
		log.Info("Handling wallet event %v %v %v %v %v", update.Owner.Username, update.Owner.ProjectId,
			update.Category.Name, update.CombinedQuota, update.Locked)
		if LaunchUserInstances {
			if update.Owner.Type == apm.WalletOwnerTypeUser {
				_, ok := MapUCloudToLocal(update.Owner.Username)
				if ok {
					handler(nType, notification)
				} else {
					log.Info("Could not map user %v", update.Owner.Username)
					success = false
				}
			} else {
				handler(nType, notification)
			}
		}

		if success {
			kvdb.Set(replayFromKey, uint64(update.LastUpdate.UnixMilli()))
		}

	case NotificationMessageProjectUpdated:
		update := notification.(*NotificationProjectUpdated)
		log.Info("Handling project event %v", update)
		kvdb.Set(replayFromKey, uint64(update.LastUpdate.UnixMilli()))
	}
}

const (
	OpAuth         uint8 = 0
	OpWallet       uint8 = 1
	OpProject      uint8 = 2
	OpCategoryInfo uint8 = 3
	OpUserInfo     uint8 = 4
	OpReplayUser   uint8 = 5
)

func handleSession(session *ws.Conn, userReplayChannel chan string, replayFrom uint64) {
	defer util.SilentClose(session)

	projects := make(map[uint32]apm.Project)
	products := make(map[uint32]apm.ProductCategory)
	users := make(map[uint32]string)

	writeBuf := buf{}
	writeBuf.PutU8(OpAuth)
	writeBuf.PutU64(replayFrom)
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
}

type NotificationProjectUpdated struct {
	LastUpdate fnd.Timestamp
	Project    apm.Project
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
