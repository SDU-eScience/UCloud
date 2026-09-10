package coreutil

import (
	"net/http"
	"slices"
	"time"

	cfg "ucloud.dk/core/pkg/config"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var featureGrantCache = util.NewCache[string, []string](time.Minute)

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

func FeatureIsEnabled(actor rpc.Actor, feature string) *util.HttpError {
	if slices.Contains(cfg.Configuration.Features, feature) {
		return nil
	}

	granted := FeatureGrantsOfActor(actor)
	if slices.Contains(granted, feature) {
		return nil
	}
	return FeatureNotEnabledError
}

func FeatureInvalidateGrantCache() {
	featureGrantCache.InvalidateAll()
}
