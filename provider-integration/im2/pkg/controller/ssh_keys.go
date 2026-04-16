package controller

import (
	"bytes"
	"sync"
	"time"

	"github.com/charmbracelet/ssh"
	"ucloud.dk/pkg/ipc"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// This file contains an in-memory cache of SSH keys which is mirrored into the local database on the server side.
// User mode updates the cache through IPC so the SSH terminal can resolve users without querying the main API.

var SshKeys SshKeyService

type SshKeyService struct {
	OnKeyUploaded func(username string, keys []orcapi.SshKey) *util.HttpError
}

type sshKeyTrackRequest struct {
	Username string
	Keys     []orcapi.SshKey
}

var sshKeyTrackIpc = ipc.NewCall[sshKeyTrackRequest, util.Empty]("sshKeys.track")
var sshKeyBrowseIpc = ipc.NewCall[util.Empty, []orcapi.SshKey]("sshKeys.browse")

var sshKeyDatabase = struct {
	Mu   sync.RWMutex
	Keys map[string][]orcapi.SshKey
	Pub  map[string][]ssh.PublicKey
}{
	Keys: map[string][]orcapi.SshKey{},
	Pub:  map[string][]ssh.PublicKey{},
}

func initSshKeys() {
	if RunsServerCode() {
		initSshKeyDatabase()

		sshKeyTrackIpc.Handler(func(r *ipc.Request[sshKeyTrackRequest]) ipc.Response[util.Empty] {
			sshKeyTrackServer(r.Payload.Username, r.Payload.Keys)
			return ipc.Response[util.Empty]{StatusCode: 200, Payload: util.Empty{}}
		})

		sshKeyBrowseIpc.Handler(func(r *ipc.Request[util.Empty]) ipc.Response[[]orcapi.SshKey] {
			return ipc.Response[[]orcapi.SshKey]{StatusCode: 200, Payload: SshKeyRetrieveAll()}
		})
	}

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

func initSshKeyDatabase() {
	sshKeyDatabase.Mu.Lock()

	sshKeyDatabase.Keys = map[string][]orcapi.SshKey{}
	sshKeyDatabase.Pub = map[string][]ssh.PublicKey{}
	type keyRow struct {
		Username    string
		Id          string
		CreatedAt   time.Time
		Fingerprint string
		Title       string
		Key         string
	}
	rows := db.NewTx(func(tx *db.Transaction) []keyRow {
		return db.Select[keyRow](
			tx,
			`
				select owner as username, key_id as id, created_at, fingerprint, title, key
				from tracked_ssh_keys
				order by owner, key_id
			`,
			db.Params{},
		)
	})

	for _, row := range rows {
		key := orcapi.SshKey{
			Id:          row.Id,
			Owner:       row.Username,
			CreatedAt:   fnd.Timestamp(row.CreatedAt),
			Fingerprint: row.Fingerprint,
			Specification: orcapi.SshKeySpecification{
				Title: row.Title,
				Key:   row.Key,
			},
		}
		sshKeyDatabase.Keys[row.Username] = append(sshKeyDatabase.Keys[row.Username], key)

		parsed, _, _, _, err := ssh.ParseAuthorizedKey([]byte(row.Key))
		if err == nil {
			sshKeyDatabase.Pub[row.Username] = append(sshKeyDatabase.Pub[row.Username], parsed)
		}
	}

	isEmpty := len(sshKeyDatabase.Keys) == 0
	sshKeyDatabase.Mu.Unlock()

	if isEmpty {
		keysByUser := map[string][]orcapi.SshKey{}

		var next util.Option[string]
		for {
			page, err := orcapi.SshControlBrowse.Invoke(orcapi.SshKeysControlBrowseRequest{
				ItemsPerPage: 250,
				Next:         next,
			})

			if err != nil {
				log.Fatal("Could not retrieve SSH keys from UCloud/Core: %s", err)
			} else {
				for _, key := range page.Items {
					keysByUser[key.Owner] = append(keysByUser[key.Owner], key)
				}

				next = page.Next
				if !next.Present {
					break
				}
			}
		}

		for user, keys := range keysByUser {
			sshKeyTrackServer(user, keys)
		}
	}
}

func sshKeyTrackServer(username string, keys []orcapi.SshKey) {
	keysCopy := make([]orcapi.SshKey, len(keys))
	copy(keysCopy, keys)
	pubCopy := make([]ssh.PublicKey, 0, len(keysCopy))
	for _, key := range keysCopy {
		parsed, _, _, _, err := ssh.ParseAuthorizedKey([]byte(key.Specification.Key))
		if err == nil {
			pubCopy = append(pubCopy, parsed)
		}
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `delete from tracked_ssh_keys where owner = :owner`, db.Params{"owner": username})
		for _, key := range keysCopy {
			db.Exec(
				tx,
				`
					insert into tracked_ssh_keys(owner, key_id, created_at, fingerprint, title, key)
					values (:owner, :key_id, :created_at, :fingerprint, :title, :key)
				`,
				db.Params{
					"owner":       username,
					"key_id":      key.Id,
					"created_at":  key.CreatedAt.Time(),
					"fingerprint": key.Fingerprint,
					"title":       key.Specification.Title,
					"key":         key.Specification.Key,
				},
			)
		}
	})

	sshKeyDatabase.Mu.Lock()
	sshKeyDatabase.Keys[username] = keysCopy
	sshKeyDatabase.Pub[username] = pubCopy
	sshKeyDatabase.Mu.Unlock()
}

func SshKeyTrackNew(username string, keys []orcapi.SshKey) *util.HttpError {
	if username == "" {
		return util.UserHttpError("missing SSH key owner")
	}

	if RunsServerCode() {
		sshKeyTrackServer(username, keys)
		return nil
	}

	_, err := sshKeyTrackIpc.Invoke(sshKeyTrackRequest{Username: username, Keys: keys})
	if err != nil {
		return util.HttpErrorFromErr(err)
	}
	return nil
}

func SshKeyRetrieveAll() []orcapi.SshKey {
	if RunsServerCode() {
		sshKeyDatabase.Mu.RLock()
		defer sshKeyDatabase.Mu.RUnlock()

		var result []orcapi.SshKey
		for _, keys := range sshKeyDatabase.Keys {
			for _, key := range keys {
				result = append(result, key)
			}
		}
		return result
	}

	result, err := sshKeyBrowseIpc.Invoke(util.Empty{})
	if err != nil {
		return nil
	}
	return result
}

func SshKeyResolveOwner(key ssh.PublicKey) (string, bool) {
	sshKeyDatabase.Mu.RLock()
	defer sshKeyDatabase.Mu.RUnlock()

	var owner string
	for username, keys := range sshKeyDatabase.Pub {
		for _, knownKey := range keys {
			if bytes.Equal(key.Marshal(), knownKey.Marshal()) {
				if owner != "" && owner != username {
					return "", false
				}
				owner = username
			}
		}
	}

	if owner == "" {
		return "", false
	}

	return owner, true
}
