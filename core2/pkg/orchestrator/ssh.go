package orchestrator

import (
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initSsh() {
	orcapi.SshCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.SshKeySpecification]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		result, err := SshKeyCreate(info.Actor, request.Items)
		if err != nil {
			return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
		} else {
			return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: result}, nil
		}
	})
	//TODO make handlers for the rest of the functions
}

func SshKeyCreate(actor rpc.Actor, keys []orcapi.SshKeySpecification) ([]fndapi.FindByStringId, *util.HttpError) {
	for _, key := range keys {
		anyOk := false
		for _, validPrefix := range validPrefixes {
			if strings.HasPrefix(validPrefix, key.Key) {
				anyOk = true
				break
			}
		}

		if !anyOk {
			return nil, util.HttpErr(http.StatusBadRequest, "Not a valid ssh key")
		}
	}

	result := db.NewTx(func(tx *db.Transaction) []fndapi.FindByStringId {
		b := db.BatchNew(tx)

		var ids []*util.Option[struct{ Id int }]

		for _, key := range keys {
			row := db.BatchGet[struct{ Id int }](
				b,
				`
					insert into app_orchestrator.ssh_keys(owner, created_at, title, key)
					values (:owner, :created_at, :title, :key)
					returning id
				`,
				db.Params{
					"owner":      actor.Username,
					"created_at": time.Now(),
					"title":      key.Title,
					"key":        key.Key,
				},
			)
			ids = append(ids, row)
		}
		db.BatchSend(b)
		var results []fndapi.FindByStringId
		for _, id := range ids {
			if id.Present {
				results = append(results, fndapi.FindByStringId{Id: strconv.Itoa(id.Value.Id)})
			}
		}
		return results
	})
	//TODO add a notifyProviders function call here
	return result, nil
}

var validPrefixes = []string{
	"ecdsa-sha2-nistp256",
	"ecdsa-sha2-nistp384",
	"ecdsa-sha2-nistp521",

	"sk-ecdsa-sha2-nistp256@openssh.com",
	"sk-ssh-ed25519@openssh.com",

	"ssh-ed25519",
	"ssh-rsa",
}

func SshKeyRetrieve(actor rpc.Actor, id string) (orcapi.SshKey, *util.HttpError) {
	result, ok := db.NewTx2(func(tx *db.Transaction) (orcapi.SshKey, bool) {
		row, ok := db.Get[struct {
			Id        int
			Owner     string
			CreatedAt time.Time
			Title     string
			Key       string
		}](
			tx,
			`
				select id, owner, created_at, title, key
				from app_orchestrator.ssh_keys
				where id = :id and owner = :owner
			`,
			db.Params{
				"id":    id,
				"owner": actor.Username,
			},
		)
		return orcapi.SshKey{
			Id:        strconv.Itoa(row.Id),
			Owner:     row.Owner,
			CreatedAt: fndapi.Timestamp(row.CreatedAt),
			Specification: orcapi.SshKeySpecification{
				Title: row.Title,
				Key:   row.Key,
			},
		}, ok
	})
	if !ok {
		return orcapi.SshKey{}, util.HttpErr(http.StatusNotFound, "SSH key does not exist")
	}
	return result, nil
}

func SshKeyBrowse(actor rpc.Actor, pagination orcapi.SshKeysBrowseRequest) fndapi.PageV2[orcapi.SshKey] {
	itemsPerPage := fndapi.ItemsPerPage(pagination.ItemsPerPage)
	result := db.NewTx(func(tx *db.Transaction) []orcapi.SshKey {
		items := db.Select[struct {
			Id        int
			Owner     string
			CreatedAt time.Time
			title     string
			key       string
		}](
			tx,
			fmt.Sprintf(`
				select id, owner, created_at, title, key
				from app_orchestrator.ssh_keys
				where
					owner = :owner
					and (
					    :next::bigint is null 
						or id > :next::bigint
					)
				order by id
				limit %v
			`, itemsPerPage),
			db.Params{
				"next":  pagination.Next.Sql(),
				"owner": actor.Username,
			},
		)
		b := db.BatchNew(tx)
		db.BatchSend(b)
		var item []orcapi.SshKey
		for _, row := range items {
			item = append(item, orcapi.SshKey{
				Id:        strconv.Itoa(row.Id),
				Owner:     actor.Username,
				CreatedAt: fndapi.Timestamp(row.CreatedAt),
				Specification: orcapi.SshKeySpecification{
					Title: row.title,
					Key:   row.key,
				},
			})
		}
		return item
	})

	next := util.Option[string]{}
	if len(result) >= itemsPerPage {
		next.Set(result[len(result)-1].Id)
	}
	return fndapi.PageV2[orcapi.SshKey]{ItemsPerPage: itemsPerPage, Items: result, Next: next}
}

func SshKeyDelete(actor rpc.Actor, keys []fndapi.FindByStringId) *util.HttpError {
	db.NewTx0(func(tx *db.Transaction) {
		var ids []int
		for _, key := range keys {
			parsed, err := strconv.Atoi(key.Id)
			if err == nil {
				ids = append(ids, parsed)
			}
		}

		db.Exec(
			tx,
			`
				delete from app_orchestrator.ssh_keys
				where
					id = some(:ids::bigint[]) and
					owner = :owner
			`,
			db.Params{
				"id":    ids,
				"owner": actor.Username,
			},
		)
	})
	return nil
}
