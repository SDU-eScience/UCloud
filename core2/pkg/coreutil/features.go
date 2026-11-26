package coreutil

import (
	"net/http"
	"slices"
	"time"

	cfg "ucloud.dk/core/pkg/config"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var featureGrantCache = util.NewCache[string, []string](time.Minute)
var featureProjectGrantCache = util.NewCache[string, bool](time.Minute)
var featureGlobalCache = util.NewCache[string, bool](time.Minute)
var featureUserOverrideCache = util.NewCache[string, map[string]bool](time.Minute)

var FeatureNotEnabledError = &util.HttpError{
	StatusCode: http.StatusForbidden,
	Why:        "This feature is not enabled",
	ErrorCode:  "FEATURE_NOT_ENABLED",
}

func FeatureGrantsOfActor(actor rpc.Actor) []string {
	granted, _ := featureGrantCache.Get(
		actor.Username,
		func() ([]string, error) {
			return db.NewTx(
				func(tx *db.Transaction) []string {
					return FeatureGrantsOfUser(tx, actor.Username)
				},
			), nil
		},
	)
	return granted
}

func FeatureGrantsOfUser(tx *db.Transaction, username string) []string {
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
			"username": username,
		},
	)

	result := make([]string, 0, len(rows))
	for _, row := range rows {
		result = append(result, row.Feature)
	}
	return result
}

func FeatureIsGloballyEnabled(feature string) bool {
	if slices.Contains(cfg.Configuration.Features, feature) {
		return true
	}

	enabled, _ := featureGlobalCache.Get(
		feature,
		func() (bool, error) {
			return db.NewTx(func(tx *db.Transaction) bool {
				_, ok := db.Get[struct{ Feature string }](
					tx,
					`
						select feature
						from features.global_enabled
						where feature = :feature
					`,
					db.Params{
						"feature": feature,
					},
				)
				return ok
			}), nil
		},
	)
	return enabled
}

func FeatureUserOverrides(username string) map[string]bool {
	overrides, _ := featureUserOverrideCache.Get(
		username,
		func() (map[string]bool, error) {
			return db.NewTx(func(tx *db.Transaction) map[string]bool {
				rows := db.Select[struct {
					Feature string
					Enabled bool
				}](
					tx,
					`
						select feature, enabled
						from features.user_overrides
						where username = :username
					`,
					db.Params{
						"username": username,
					},
				)

				result := make(map[string]bool, len(rows))
				for _, row := range rows {
					result[row.Feature] = row.Enabled
				}
				return result
			}), nil
		},
	)
	return overrides
}

func FeatureIsGloballyForced(feature string) bool {
	if !FeatureIsGloballyEnabled(feature) {
		return false
	}

	definition, ok := fndapi.FeatureDefinitionById(feature)
	if !ok {
		return true
	}

	if util.DevelopmentModeEnabled() && definition.Toggleable {
		return false
	}
	return true
}

func featureUserOverrideDisabled(username string, feature string) bool {
	enabled, exists := FeatureUserOverrides(username)[feature]
	return exists && !enabled
}

func FeatureIsEnabled(actor rpc.Actor, feature string) *util.HttpError {
	globallyEnabled := FeatureIsGloballyEnabled(feature)

	definition, ok := fndapi.FeatureDefinitionById(feature)
	if !ok {
		if globallyEnabled {
			return nil
		}
		return FeatureNotEnabledError
	}

	globallyForced := globallyEnabled && (!util.DevelopmentModeEnabled() || !definition.Toggleable)
	if globallyForced {
		return nil
	}

	if !globallyEnabled {
		granted := FeatureGrantsOfActor(actor)
		if !slices.Contains(granted, feature) {
			return FeatureNotEnabledError
		}
	}

	if definition.Toggleable && featureUserOverrideDisabled(actor.Username, feature) {
		return FeatureNotEnabledError
	}

	return nil
}

func FeatureIsEnabledForOwner(createdBy string, projectId string, feature string) *util.HttpError {
	globallyEnabled := FeatureIsGloballyEnabled(feature)

	definition, ok := fndapi.FeatureDefinitionById(feature)
	globallyForced := globallyEnabled && (!ok || !util.DevelopmentModeEnabled() || !definition.Toggleable)
	if globallyForced {
		return nil
	}

	if globallyEnabled {
		userOwned := projectId == "" && createdBy != ""
		if userOwned && definition.Toggleable && featureUserOverrideDisabled(createdBy, feature) {
			return FeatureNotEnabledError
		}
		return nil
	}

	if projectId != "" {
		granted, _ := featureProjectGrantCache.Get(
			projectId+"\x1f"+feature,
			func() (bool, error) {
				return db.NewTx(func(tx *db.Transaction) bool {
					_, ok := db.Get[struct{ ProjectId string }](
						tx,
						`
							select project_id
							from features.project_grants
							where
								project_id = :project_id
								and feature = :feature
						`,
						db.Params{
							"project_id": projectId,
							"feature":    feature,
						},
					)
					return ok
				}), nil
			},
		)
		if granted {
			return nil
		}
		return FeatureNotEnabledError
	}

	if createdBy != "" && slices.Contains(FeatureGrantsOfActor(rpc.Actor{Username: createdBy}), feature) {
		if definition.Toggleable && featureUserOverrideDisabled(createdBy, feature) {
			return FeatureNotEnabledError
		}
		return nil
	}
	return FeatureNotEnabledError
}

func FeatureInvalidateGrantCache() {
	featureGrantCache.InvalidateAll()
	featureProjectGrantCache.InvalidateAll()
}

func FeatureInvalidateGlobalCache() {
	featureGlobalCache.InvalidateAll()
}

func FeatureInvalidateUserCache(username string) {
	featureUserOverrideCache.Invalidate(username)
}
