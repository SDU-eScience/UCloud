package orchestrator

import (
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	"ucloud.dk/core/pkg/coreutil"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
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

	orcapi.SshRetrieve.Handler(func(info rpc.RequestInfo, request fndapi.FindByStringId) (orcapi.SshKey, *util.HttpError) {
		result, err := SshKeyRetrieve(info.Actor, request.Id)
		return result, err
	})

	orcapi.SshBrowse.Handler(func(info rpc.RequestInfo, request orcapi.SshKeysBrowseRequest) (fndapi.PageV2[orcapi.SshKey], *util.HttpError) {
		result := SshKeyBrowse(info.Actor, request)
		return result, nil
	})

	orcapi.SshDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (util.Empty, *util.HttpError) {
		err := SshKeyDelete(info.Actor, request.Items)
		return util.Empty{}, err
	})
}

func SshKeyCreate(actor rpc.Actor, keys []orcapi.SshKeySpecification) ([]fndapi.FindByStringId, *util.HttpError) {
	for _, key := range keys {
		anyOk := false
		for _, validPrefix := range validPrefixes {
			if strings.HasPrefix(key.Key, validPrefix) {
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
	sshKeyNotifyProviders(actor)

	return result, nil
}

func sshKeyNotifyProviders(actor rpc.Actor) {
	allProviders, err := accapi.FindRelevantProviders.Invoke(fndapi.BulkRequestOf(accapi.FindRelevantProvidersRequest{
		Username:          actor.Username,
		Project:           util.OptStringIfNotEmpty(string(actor.Project.Value)),
		UseProject:        actor.Project.Present,
		FilterProductType: util.OptValue(accapi.ProductTypeCompute),
		IncludeFreeToUse:  util.OptValue(false),
	}))
	if err != nil || len(allProviders.Responses) != 1 {
		return
	}
	allKeys := SshKeyBrowse(actor, orcapi.SshKeysBrowseRequest{
		ItemsPerPage: 1000,
	})

	for _, provider := range allProviders.Responses[0].Providers {
		_, err = InvokeProvider(provider, orcapi.SshProviderKeyUploaded, orcapi.SshProviderKeyUploadedRequest{
			Username: actor.Username,
			AllKeys:  allKeys.Items,
		}, ProviderCallOpts{
			Username: util.OptValue(actor.Username),
			Reason:   util.OptValue("New SSH key"),
		})
		if err != nil {
			log.Warn("Could not notify provider regarding SSH keys")
		}
	}
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
			Title     string
			Key       string
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

		var item []orcapi.SshKey
		for _, row := range items {
			item = append(item, orcapi.SshKey{
				Id:        strconv.Itoa(row.Id),
				Owner:     actor.Username,
				CreatedAt: fndapi.Timestamp(row.CreatedAt),
				Specification: orcapi.SshKeySpecification{
					Title: row.Title,
					Key:   row.Key,
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
				"ids":   ids,
				"owner": actor.Username,
			},
		)
	})
	sshKeyNotifyProviders(actor)

	return nil
}

// TODO
func SshKeyRetrieveByJob(actor rpc.Actor, jobId string) ([]orcapi.SshKey, *util.HttpError) {
	job, err := JobsRetrieve(actor, jobId, orcapi.JobFlags{})
	if err != nil {
		return nil, err
	}
	result := db.NewTx(func(tx *db.Transaction) []orcapi.SshKey {
		relevantUsers := map[string]util.Empty{}
		relevantUsers[job.Owner.CreatedBy] = util.Empty{}
		projectId := job.Owner.Project
		if projectId != "" {
			project, ok := coreutil.ProjectRetrieveFromDatabase(tx, projectId)
			if ok {
				for _, member := range project.Status.Members {
					if member.Role.Satisfies(fndapi.ProjectRoleAdmin) {
						relevantUsers[member.Username] = util.Empty{}
					}
				}
				otherAcl := job.Permissions.Others
				relevantGroups := map[string]util.Empty{}
				for _, entry := range otherAcl {
					if !orcapi.PermissionsHas(entry.Permissions, orcapi.PermissionEdit) {
						continue
					}
					relevantGroups[entry.Entity.Group] = util.Empty{}
				}

				if len(relevantGroups) > 0 {
					for _, group := range project.Status.Groups {
						_, ok := relevantGroups[group.Id]
						if ok {
							for _, member := range group.Status.Members {
								relevantUsers[member] = util.Empty{}
							}
						}
					}
				}
			}
		}

		var relevantUsersArray []string
		for username, _ := range relevantUsers {
			relevantUsersArray = append(relevantUsersArray, username)
		}

		items := db.Select[struct {
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
				where
					owner = some(:owners::text[])
				order by id
			`,
			db.Params{
				"owners": relevantUsersArray,
			},
		)

		var result []orcapi.SshKey
		for _, row := range items {
			result = append(result, orcapi.SshKey{
				Id:        strconv.Itoa(row.Id),
				Owner:     actor.Username,
				CreatedAt: fndapi.Timestamp(row.CreatedAt),
				Specification: orcapi.SshKeySpecification{
					Title: row.Title,
					Key:   row.Key,
				},
			})
		}
		return result
	})
	return result, nil
}
