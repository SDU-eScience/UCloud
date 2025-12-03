package orchestrator

import (
	"encoding/json"
	"fmt"
	"strconv"

	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
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
	return ScriptBrowseBy(actor, request.ItemsPerPage, request.Next, util.OptNone[int](), util.OptValue(request.FilterApplicationName))
}

func ScriptBrowseBy(
	actor rpc.Actor,
	itemsPerPage int,
	next util.Option[string],
	filterById util.Option[int],
	filterApplicationName util.Option[string],
) (fndapi.PageV2[orcapi.Script], *util.HttpError) {
	itemsPerPage = fndapi.ItemsPerPage(itemsPerPage)

	db.NewTx(func(tx *db.Transaction) T {
		workspace := string(actor.Project.Value)
		if !actor.Project.Present {
			workspace = actor.Username
		}
		db.Select[struct{}](
			tx,
			fmt.Sprintf(`
				with
					workspace_flows as (
						select
							w.id,
							provider.timestamp_to_unix(w.created_at)::int8 as created_at,
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
	})
}

func ScriptRename(actor rpc.Actor, request fndapi.BulkRequest[orcapi.ScriptRenameRequest]) *util.HttpError {

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

}

func ScriptRetrieve(actor rpc.Actor, id string) (orcapi.Script, *util.HttpError) {
	return ScriptBrowseBy(actor)
}
