package foundation

import (
	"net/http"
	"slices"
	"sort"
	"time"

	cfg "ucloud.dk/core/pkg/config"
	"ucloud.dk/core/pkg/coreutil"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initFeatures() {
	fndapi.FeaturesRetrieveEnabled.Handler(func(info rpc.RequestInfo, request util.Empty) ([]string, *util.HttpError) {
		result := make([]string, 0, len(fndapi.FeatureDefinitions))
		for _, definition := range fndapi.FeatureDefinitions {
			if coreutil.FeatureIsEnabled(info.Actor, definition.Feature) == nil {
				result = append(result, definition.Feature)
			}
		}
		sort.Strings(result)
		return result, nil
	})

	fndapi.FeaturesRetrieveToggles.Handler(func(info rpc.RequestInfo, request util.Empty) ([]fndapi.FeatureToggle, *util.HttpError) {
		granted := coreutil.FeatureGrantsOfActor(info.Actor)
		overrides := coreutil.FeatureUserOverrides(info.Actor.Username)

		result := make([]fndapi.FeatureToggle, 0)
		for _, definition := range fndapi.FeatureDefinitions {
			if !definition.Toggleable {
				continue
			}
			if coreutil.FeatureIsGloballyForced(definition.Feature) {
				continue
			}
			if !coreutil.FeatureIsGloballyEnabled(definition.Feature) &&
				!slices.Contains(granted, definition.Feature) {
				continue
			}

			enabled := true
			if override, exists := overrides[definition.Feature]; exists {
				enabled = override
			}

			result = append(result, fndapi.FeatureToggle{
				Feature:     definition.Feature,
				Title:       definition.Title,
				Description: definition.Description,
				Enabled:     enabled,
			})
		}
		return result, nil
	})

	fndapi.FeaturesUpdateToggle.Handler(func(info rpc.RequestInfo, request fndapi.FeatureToggleUpdateRequest) (util.Empty, *util.HttpError) {
		definition, ok := fndapi.FeatureDefinitionById(request.Feature)
		if !ok || !definition.Toggleable {
			return util.Empty{}, util.HttpErr(http.StatusBadRequest, "feature is not toggleable: %v", request.Feature)
		}
		if coreutil.FeatureIsGloballyForced(request.Feature) {
			return util.Empty{}, util.HttpErr(http.StatusBadRequest, "feature is globally enabled: %v", request.Feature)
		}

		if !coreutil.FeatureIsGloballyEnabled(request.Feature) {
			granted := coreutil.FeatureGrantsOfActor(info.Actor)
			if !slices.Contains(granted, request.Feature) {
				return util.Empty{}, util.HttpErr(http.StatusForbidden, "feature is not available to this user")
			}
		}

		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					insert into features.user_overrides(username, feature, enabled, updated_at)
					values (:username, :feature, :enabled, now())
					on conflict (username, feature) do update set
						enabled = excluded.enabled,
						updated_at = excluded.updated_at
				`,
				db.Params{
					"username": info.Actor.Username,
					"feature":  request.Feature,
					"enabled":  request.Enabled,
				},
			)
		})
		coreutil.FeatureInvalidateUserCache(info.Actor.Username)
		return util.Empty{}, nil
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
		coreutil.FeatureInvalidateGrantCache()
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
		coreutil.FeatureInvalidateGrantCache()
		return util.Empty{}, nil
	})

	fndapi.FeaturesBrowseAdmin.Handler(func(info rpc.RequestInfo, request util.Empty) ([]fndapi.FeatureAdminEntry, *util.HttpError) {
		rows := db.NewTx(func(tx *db.Transaction) []struct{ Feature string } {
			return db.Select[struct{ Feature string }](
				tx,
				`
					select feature
					from features.global_enabled
				`,
				db.Params{},
			)
		})

		globallyEnabled := make(map[string]bool, len(rows))
		for _, row := range rows {
			globallyEnabled[row.Feature] = true
		}

		result := make([]fndapi.FeatureAdminEntry, 0, len(fndapi.FeatureDefinitions))
		for _, definition := range fndapi.FeatureDefinitions {
			result = append(result, fndapi.FeatureAdminEntry{
				Feature:         definition.Feature,
				Title:           definition.Title,
				Description:     definition.Description,
				Toggleable:      definition.Toggleable,
				GloballyEnabled: globallyEnabled[definition.Feature],
				ConfigEnabled:   slices.Contains(cfg.Configuration.Features, definition.Feature),
			})
		}
		return result, nil
	})

	fndapi.FeaturesUpdateGlobal.Handler(func(info rpc.RequestInfo, request fndapi.FeatureGlobalUpdateRequest) (util.Empty, *util.HttpError) {
		if !fndapi.FeatureIsValid(request.Feature) {
			return util.Empty{}, util.HttpErr(http.StatusBadRequest, "unknown feature: %v", request.Feature)
		}

		db.NewTx0(func(tx *db.Transaction) {
			if request.Enabled {
				db.Exec(
					tx,
					`
						insert into features.global_enabled(feature, enabled_by, enabled_at)
						values (:feature, :enabled_by, now())
						on conflict (feature) do nothing
					`,
					db.Params{
						"feature":    request.Feature,
						"enabled_by": info.Actor.Username,
					},
				)
			} else {
				db.Exec(
					tx,
					`
						delete from features.global_enabled
						where feature = :feature
					`,
					db.Params{
						"feature": request.Feature,
					},
				)
			}
		})
		coreutil.FeatureInvalidateGlobalCache()
		return util.Empty{}, nil
	})
}
