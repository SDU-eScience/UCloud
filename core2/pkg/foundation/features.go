package foundation

import (
	"net/http"
	"sort"
	"time"

	"ucloud.dk/core/pkg/coreutil"
	cfg "ucloud.dk/core/pkg/config"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initFeatures() {
	fndapi.FeaturesRetrieveEnabled.Handler(func(info rpc.RequestInfo, request util.Empty) ([]string, *util.HttpError) {
		granted := db.NewTx(
			func(tx *db.Transaction) []string {
				rows := db.Select[struct{ Feature string }](
					tx,
					`
						select distinct g.feature
						from
							features.project_grants g
							join project.project_members pm on
								g.project_id = pm.project_id
								and pm.username = :username
						order by g.feature
					`,
					db.Params{
						"username": info.Actor.Username,
					},
				)

				result := make([]string, 0, len(rows))
				for _, row := range rows {
					result = append(result, row.Feature)
				}
				return result
			},
		)

		result := util.Combined(cfg.Configuration.Features, granted)
		sort.Strings(result)
		return result, nil
	})

	fndapi.FeaturesBrowseGrants.Handler(func(info rpc.RequestInfo, request util.Empty) ([]fndapi.FeatureGrant, *util.HttpError) {
		type grantRow struct {
			Feature      string
			ProjectId    string
			ProjectTitle string
			GrantedAt    time.Time
			GrantedBy    string
		}

		rows := db.NewTx(
			func(tx *db.Transaction) []grantRow {
				return db.Select[grantRow](
					tx,
					`
						select
							g.feature,
							g.project_id,
							p.title as project_title,
							g.granted_at,
							g.granted_by
						from
							features.project_grants g
							join project.projects p on g.project_id = p.id
						order by g.feature, p.title
					`,
					db.Params{},
				)
			},
		)

		result := make([]fndapi.FeatureGrant, 0, len(rows))
		for _, row := range rows {
			result = append(result, fndapi.FeatureGrant{
				Feature:      row.Feature,
				ProjectId:    row.ProjectId,
				ProjectTitle: row.ProjectTitle,
				GrantedAt:    fndapi.Timestamp(row.GrantedAt),
				GrantedBy:    row.GrantedBy,
			})
		}
		return result, nil
	})

	fndapi.FeaturesGrantToProject.Handler(func(info rpc.RequestInfo, request fndapi.FeatureGrantRequest) (util.Empty, *util.HttpError) {
		if !fndapi.FeatureIsValid(request.Feature) {
			return util.Empty{}, util.HttpErr(http.StatusBadRequest, "unknown feature: %v", request.Feature)
		}

		projectExists := false
		db.NewTx0(func(tx *db.Transaction) {
			_, projectExists = coreutil.ProjectRetrieveFromDatabase(tx, request.ProjectId)
		})
		if !projectExists {
			return util.Empty{}, util.HttpErr(http.StatusBadRequest, "project does not exist")
		}

		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					insert into features.project_grants(feature, project_id, granted_by)
					values (:feature, :project_id, :granted_by)
					on conflict (feature, project_id) do nothing
				`,
				db.Params{
					"feature":    request.Feature,
					"project_id": request.ProjectId,
					"granted_by": info.Actor.Username,
				},
			)
		})
		return util.Empty{}, nil
	})

	fndapi.FeaturesRevokeFromProject.Handler(func(info rpc.RequestInfo, request fndapi.FeatureGrantRequest) (util.Empty, *util.HttpError) {
		if !fndapi.FeatureIsValid(request.Feature) {
			return util.Empty{}, util.HttpErr(http.StatusBadRequest, "unknown feature: %v", request.Feature)
		}

		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					delete from features.project_grants
					where
						feature = :feature
						and project_id = :project_id
				`,
				db.Params{
					"feature":    request.Feature,
					"project_id": request.ProjectId,
				},
			)
		})
		return util.Empty{}, nil
	})
}
