package orchestrator

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"time"

	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initScripts() {
	orcapi.ScriptCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ScriptCreateRequest]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		return ScriptCreate(info.Actor, request)
	})
	orcapi.ScriptBrowse.Handler(func(info rpc.RequestInfo, request orcapi.ScriptBrowseRequest) (fndapi.PageV2[orcapi.Script], *util.HttpError) {
		return ScriptBrowse(info.Actor, request)
	})
	orcapi.ScriptRename.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ScriptRenameRequest]) (util.Empty, *util.HttpError) {
		return util.Empty{}, ScriptRename(info.Actor, request)
	})
	orcapi.ScriptDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (util.Empty, *util.HttpError) {
		return util.Empty{}, ScriptDelete(info.Actor, request.Items)
	})
	orcapi.ScriptUpdateAcl.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.ScriptUpdateAclRequest]) (util.Empty, *util.HttpError) {
		return util.Empty{}, ScriptUpdateAcl(info.Actor, request)
	})
	orcapi.ScriptRetrieve.Handler(func(info rpc.RequestInfo, request fndapi.FindByStringId) (orcapi.Script, *util.HttpError) {
		return ScriptRetrieve(info.Actor, request.Id)
	})
}

func ScriptCreate(
	actor rpc.Actor,
	request fndapi.BulkRequest[orcapi.ScriptCreateRequest],
) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
	txResult := db.NewTx(func(tx *db.Transaction) []fndapi.FindByStringId {
		b := db.BatchNew(tx)

		var ids []*util.Option[struct{ Id int }]
		for _, reqItem := range request.Items {
			if reqItem.AllowOverwrite {
				workspace := string(actor.Project.Value)
				if !actor.Project.Present {
					workspace = actor.Username
				}

				db.BatchExec(
					b,
					`
						delete from app_store.workflows
                        where
                            coalesce(project_id, created_by) = :workspace
                            and application_name = :application_name
                            and path = :path
					`,
					db.Params{
						"workspace":        workspace,
						"application_name": reqItem.Specification.ApplicationName,
						"path":             reqItem.Path,
					},
				)
			}

			inputs, _ := json.Marshal(reqItem.Specification.Inputs)
			row := db.BatchGet[struct{ Id int }](
				b,
				`
					insert into app_store.workflows
                        (created_by, project_id, application_name, language, is_open, path, init, job, inputs, readme) 
                    values
                        (:created_by, :project_id, :application_name, :language, :is_open, :path, :init, :job, :inputs, :readme) 
                    returning id
			    `,
				db.Params{
					"created_by":       actor.Username,
					"project_id":       actor.Project.Sql(),
					"application_name": reqItem.Specification.ApplicationName,
					"language":         reqItem.Specification.Language, // TODO
					"is_open":          true,
					"path":             reqItem.Path,
					"init":             reqItem.Specification.Init.Sql(),
					"job":              reqItem.Specification.Job.Sql(),
					"inputs":           inputs,
					"readme":           reqItem.Specification.Readme.Sql(),
				},
			)
			ids = append(ids, row)
		}
		db.BatchSend(b)

		var result []fndapi.FindByStringId
		for _, id := range ids {
			result = append(result, fndapi.FindByStringId{Id: fmt.Sprint(id.Value)})
		}

		return result
	})

	return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: txResult}, nil
}

func ScriptBrowse(actor rpc.Actor, request orcapi.ScriptBrowseRequest) (fndapi.PageV2[orcapi.Script], *util.HttpError) {
	return db.NewTx2(func(tx *db.Transaction) (fndapi.PageV2[orcapi.Script], *util.HttpError) {
		return ScriptBrowseBy(actor, request.ItemsPerPage, request.Next, util.OptNone[int](), util.OptValue(request.FilterApplicationName), tx)
	})
}

func ScriptBrowseBy(
	actor rpc.Actor,
	itemsPerPage int,
	next util.Option[string],
	filterById util.Option[int],
	filterApplicationName util.Option[string],
	tx *db.Transaction,
) (fndapi.PageV2[orcapi.Script], *util.HttpError) {
	itemsPerPage = fndapi.ItemsPerPage(itemsPerPage)

	workspace := string(actor.Project.Value)
	if !actor.Project.Present {
		workspace = actor.Username
	}
	rows := db.Select[struct {
		Id              int
		CreatedAt       time.Time
		CreatedBy       string
		ProjectId       sql.Null[string]
		ApplicationName string
		Language        string
		Init            sql.Null[string]
		Job             sql.Null[string]
		Inputs          sql.Null[string]
		Path            string
		IsOpen          bool
		Readme          sql.Null[string]
		Permissions     string
	}](
		tx,
		fmt.Sprintf(`
				with
					workspace_flows as (
						select
							w.id,
							w.created_at,
							w.created_by,
							w.project_id,
							w.application_name,
							w.language,
							w.init,
							w.job,
							w.inputs,
							w.path,
							w.is_open,
							w.readme
						from
							app_store.workflows w
						where
							coalesce(project_id, created_by) = :workspace
							and (
								:app_name::text is null
								or application_name = :app_name
							)
							and (
								:next::int8 is null
								or id > :next::int8
							)
							and (
								:id::int8 is null
								or id = :id::int8
							)
						order by id desc
						limit %d
					),
					workflow_permissions as (
						select
							wf.id,
							to_jsonb(
								array_remove(
									array_agg(
										jsonb_build_object(
											'group', p.group_id,
											'permission', p.permission
										)
									),
									null
								)
							) as permissions
						from
							workspace_flows wf
							join app_store.workflow_permissions p on wf.id = p.workflow_id
						group by
							wf.id
					)
				select
					wf.*,
					coalesce(p.permissions, '[]'::jsonb) as permissions
				from
					workspace_flows wf
					left join workflow_permissions p on wf.id = p.id
				order by wf.id desc
		    `, itemsPerPage),
		db.Params{
			"workspace": workspace,
			"next":      next.Sql(),
			"app_name":  filterApplicationName.Sql(),
			"id":        filterById.Sql(),
		},
	)

	var items []orcapi.Script
	for _, row := range rows {
		var inputs []orcapi.ApplicationParameter
		if row.Inputs.Valid {
			err := json.Unmarshal([]byte(row.Inputs.V), &inputs)
			if err != nil {
				log.Error("Inputs from the script, %d, could not be unmarshalled. Error: %s", row.Id, err)
				continue
			}
		}

		var otherPermissions []orcapi.ScriptAclEntry
		err := json.Unmarshal([]byte(row.Permissions), &otherPermissions)
		if err != nil {
			log.Error("Permissions from the script, %d, could not be unmarshalled. Error: %s", row.Id, err)
		}

		items = append(items, orcapi.Script{
			Id:        fmt.Sprint(row.Id),
			CreatedAt: fndapi.Timestamp(row.CreatedAt),
			Owner: orcapi.ScriptOwner{
				CreatedBy: row.CreatedBy,
				ProjectId: util.SqlNullToOpt(row.ProjectId),
			},
			Specification: orcapi.ScriptSpecification{
				ApplicationName: row.ApplicationName,
				Language:        orcapi.ScriptLanguage(row.Language),
				Init:            util.SqlNullToOpt(row.Init),
				Job:             util.SqlNullToOpt(row.Job),
				Inputs:          util.OptValue(inputs),
				Readme:          util.SqlNullToOpt(row.Readme),
			},
			Status: orcapi.ScriptStatus{Path: row.Path},
			Permissions: orcapi.ScriptPermissions{
				OpenToWorkspace: row.IsOpen,
				Myself:          nil,
				Others:          otherPermissions,
			},
		})
	}

	for i := 0; i < len(items); i++ {
		wf := &items[i]
		perms := map[orcapi.ScriptPermission]util.Empty{}

		if wf.Permissions.OpenToWorkspace {
			perms[orcapi.ScriptPermissionRead] = util.Empty{}
		}

		if actor.Username == wf.Owner.CreatedBy {
			perms[orcapi.ScriptPermissionRead] = util.Empty{}
			perms[orcapi.ScriptPermissionWrite] = util.Empty{}
			perms[orcapi.ScriptPermissionAdmin] = util.Empty{}
		}

		if actor.Project.Present && actor.Membership[actor.Project.Value].Satisfies(rpc.ProjectRoleAdmin) {
			perms[orcapi.ScriptPermissionRead] = util.Empty{}
			perms[orcapi.ScriptPermissionWrite] = util.Empty{}
			perms[orcapi.ScriptPermissionAdmin] = util.Empty{}
		}

		for _, entry := range wf.Permissions.Others {
			_, isMember := actor.Groups[rpc.GroupId(entry.Group)]
			if isMember {
				perms[entry.Permission] = util.Empty{}
			}
		}

		for perm := range perms {
			wf.Permissions.Myself = append(wf.Permissions.Myself, perm)
		}
	}

	newNext := util.OptNone[string]()
	if len(items) >= itemsPerPage {
		newNext.Set(items[len(items)-1].Id)
	}

	return fndapi.PageV2[orcapi.Script]{
		Items:        items,
		Next:         newNext,
		ItemsPerPage: itemsPerPage,
	}, nil
}

func ScriptRename(actor rpc.Actor, request fndapi.BulkRequest[orcapi.ScriptRenameRequest]) *util.HttpError {
	return db.NewTx(func(tx *db.Transaction) *util.HttpError {
		for _, item := range request.Items {
			script, err := ScriptRetrieveEx(actor, item.Id, tx)
			if err != nil {
				return err
			}

			hasPermission := false
			for _, perm := range script.Permissions.Myself {
				if perm == orcapi.ScriptPermissionWrite {
					hasPermission = true
					break
				}
			}

			if !hasPermission {
				return util.HttpErr(http.StatusForbidden, "You do not have permission to rename this script")
			}

			workspace := string(actor.Project.Value)
			if !actor.Project.Present {
				workspace = actor.Username
			}

			if item.AllowOverwrite {
				db.Exec(
					tx,
					`
						delete from app_store.workflows
						where
							coalesce(project_id, created_by) = :workspace
							and path = :path
					`,
					db.Params{
						"workspace": workspace,
						"path":      item.NewPath,
					},
				)
			}

			db.Exec(
				tx,
				`
					update app_store.workflows
					set
					   path = :new_path,
					   modified_at = now()
					where
					    id = :id
				`,
				db.Params{
					"id":       script.Id,
					"new_path": item.Id,
				},
			)
		}

		return nil
	})
}

func ScriptDelete(actor rpc.Actor, items []fndapi.FindByStringId) *util.HttpError {
	db.NewTx0(func(tx *db.Transaction) {
		var ids []int
		for _, reqItem := range items {
			parsed, err := strconv.Atoi(reqItem.Id)
			if err == nil {
				ids = append(ids, parsed)
			}
		}

		db.Exec(
			tx,
			`
				delete from app_store.workflows
				where id = some(:ids)
			`,
			db.Params{"ids": ids},
		)
	})

	return nil
}

func ScriptUpdateAcl(actor rpc.Actor, request fndapi.BulkRequest[orcapi.ScriptUpdateAclRequest]) *util.HttpError {
	return db.NewTx(func(tx *db.Transaction) *util.HttpError {
		for _, item := range request.Items {
			script, err := ScriptRetrieveEx(actor, item.Id, tx)
			if err != nil {
				return err
			}

			hasPermission := false
			for _, perm := range script.Permissions.Myself {
				if perm == orcapi.ScriptPermissionAdmin {
					hasPermission = true
					break
				}
			}

			if !hasPermission {
				return util.HttpErr(http.StatusForbidden, "You do not have permission to change permissions for this script")
			}

			db.Exec(
				tx,
				`
				delete from app_store.workflow_permissions
                where workflow_id = :id
		    	`,
				db.Params{"id": item.Id},
			)

			db.Exec(
				tx,
				`
				update app_store.workflows
            	    set
            	        is_open = :is_open,
            	        modified_at = now()
            	    where id = :id
				`,
				db.Params{
					"id":      item.Id,
					"is_open": item.IsOpenForWorkspace,
				},
			)

			var groupIds []string
			var permissions []orcapi.ScriptPermission

			for _, perm := range item.Entries {
				if perm.Permission != orcapi.ScriptPermissionAdmin {
					continue
				}

				groupIds = append(groupIds, perm.Group)
				permissions = append(permissions, perm.Permission)
			}

			db.Exec(
				tx,
				`
				with
                    entries as (
                        select
                            unnest(:group_ids::text[]) as group_id,
                            unnest(:permissions::text[]) as permission
                    )
                insert into app_store.workflow_permissions (workflow_id, group_id, permission) 
                select :id, group_id, permission
                from entries
		    	`,
				db.Params{
					"id":          item.Id,
					"group_ids":   groupIds,
					"permissions": permissions,
				},
			)
		}
		return nil
	})
}

func ScriptRetrieve(actor rpc.Actor, id string) (orcapi.Script, *util.HttpError) {
	return db.NewTx2(func(tx *db.Transaction) (orcapi.Script, *util.HttpError) {
		return ScriptRetrieveEx(actor, id, tx)
	})
}

func ScriptRetrieveEx(actor rpc.Actor, id string, tx *db.Transaction) (orcapi.Script, *util.HttpError) {
	actualId, _ := strconv.ParseInt(id, 10, 64)
	page, err := ScriptBrowseBy(actor, 1, util.OptNone[string](), util.OptValue[int](int(actualId)), util.OptNone[string](), tx)
	if err != nil {
		return orcapi.Script{}, err
	}
	if len(page.Items) != 1 {
		return orcapi.Script{}, err
	}
	return page.Items[0], nil
}
