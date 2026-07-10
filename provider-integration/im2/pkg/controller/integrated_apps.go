package controller

import (
	"encoding/json"
	"fmt"
	"sync"
	"time"

	cfg "ucloud.dk/pkg/config"
	apm "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type iappConfigKey struct {
	AppName string
	Owner   orc.ResourceOwner
}

var iappConfigs = map[iappConfigKey]IAppRunningConfiguration{}
var iappConfigsMutex = sync.Mutex{}

type IntegratedApplicationFlag int

const (
	IntegratedAppInternal IntegratedApplicationFlag = 1 << iota
)

type IntegratedApplicationHandler struct {
	Flags                        IntegratedApplicationFlag
	UpdateConfiguration          func(job *orc.Job, etag string, configuration json.RawMessage) *util.HttpError
	ResetConfiguration           func(job *orc.Job, configuration json.RawMessage) (json.RawMessage, *util.HttpError)
	RestartApplication           func(job *orc.Job) *util.HttpError
	RetrieveDefaultConfiguration func(owner orc.ResourceOwner) json.RawMessage
	RetrieveLegacyConfiguration  func(owner orc.ResourceOwner) util.Option[json.RawMessage]
	MutateSpecBeforeRegistration func(owner orc.ResourceOwner, spec *orc.JobSpecification) *util.HttpError
}

var IntegratedApplications = map[string]IntegratedApplicationHandler{}

func IsIntegratedApplication(appName string) bool {
	_, ok := IntegratedApplications[appName]
	return ok
}

func initIntegratedApps() {
	if !RunsServerCode() {
		return
	}

	initAllIntegratedApps()

	for k, v := range IntegratedApplications {
		initIntegratedApp(k, v)
	}
}

func IAppConfigureFromLegacy(appName string, owner orc.ResourceOwner) (util.Option[IAppRunningConfiguration], *util.HttpError) {
	handler, ok := IntegratedApplications[appName]
	if ok && handler.RetrieveLegacyConfiguration != nil {
		rawConfig := handler.RetrieveLegacyConfiguration(owner)
		if rawConfig.Present {
			err := IAppConfigure(appName, owner, util.OptNone[string](), rawConfig.Value)
			if err != nil {
				return util.OptNone[IAppRunningConfiguration](), err
			} else {
				config := IAppRetrieveConfiguration(appName, owner)
				if !config.Present {
					return util.OptNone[IAppRunningConfiguration](), util.ServerHttpError("error configuring iapp")
				} else {
					return util.OptValue(config.Value), nil
				}
			}
		}
	}

	return util.OptNone[IAppRunningConfiguration](), nil
}

func initIntegratedApp(appName string, handler IntegratedApplicationHandler) {
	if handler.Flags&IntegratedAppInternal == 0 {
		retrieveRpc := orc.IAppProviderRetrieveConfiguration[json.RawMessage](appName)
		retrieveRpc.Handler(func(info rpc.RequestInfo, request orc.IAppProviderRetrieveConfigRequest) (orc.IAppProviderRetrieveConfigResponse[json.RawMessage], *util.HttpError) {
			config := IAppRetrieveConfiguration(appName, request.Principal)
			resp := orc.IAppRetrieveConfigResponse[json.RawMessage]{
				ETag:   config.Value.ETag,
				Config: config.Value.Configuration,
			}

			if !config.Present {
				legacyConfig, err := IAppConfigureFromLegacy(appName, request.Principal)
				if err != nil {
					return orc.IAppProviderRetrieveConfigResponse[json.RawMessage]{}, err
				} else if legacyConfig.Present {
					config = legacyConfig
					resp = orc.IAppRetrieveConfigResponse[json.RawMessage]{
						ETag:   config.Value.ETag,
						Config: config.Value.Configuration,
					}
				}

				if !config.Present {
					// NOTE(Dan): When returning the default configuration we _do not_ save it in the database.
					// This is meant only for the frontend to receive a starting point.
					resp.Config = handler.RetrieveDefaultConfiguration(request.Principal)
					resp.ETag = "initial"
				}
			}

			return resp, nil
		})

		updateRpc := orc.IAppProviderUpdateConfiguration[json.RawMessage](appName)
		updateRpc.Handler(func(info rpc.RequestInfo, request orc.IAppProviderUpdateConfigurationRequest[json.RawMessage]) (util.Empty, *util.HttpError) {
			err := IAppConfigure(appName, request.Principal, request.ExpectedETag, request.Config)
			return util.Empty{}, err
		})

		resetRpc := orc.IAppProviderReset[json.RawMessage](appName)
		resetRpc.Handler(func(info rpc.RequestInfo, request orc.IAppProviderResetRequest) (util.Empty, *util.HttpError) {
			err := IAppReset(appName, request.Principal, request.ExpectedETag)
			return util.Empty{}, err
		})

		restartRpc := orc.IAppProviderRestart[json.RawMessage](appName)
		restartRpc.Handler(func(info rpc.RequestInfo, request orc.IAppProviderRestartRequest) (util.Empty, *util.HttpError) {
			err := IAppRestart(appName, request.Principal)
			return util.Empty{}, err
		})
	}
}

func initAllIntegratedApps() {
	iappConfigsMutex.Lock()
	defer iappConfigsMutex.Unlock()

	db.NewTx0(func(tx *db.Transaction) {
		rows := db.Select[struct {
			Username        string
			Project         string
			ApplicationName string
			Configuration   json.RawMessage
			JobId           string
			Version         string
		}](
			tx,
			`
				select username, project, application_name, configuration, job_id, version
				from integrated_application_config
		    `,
			db.Params{},
		)

		for _, row := range rows {
			owner := orc.ResourceOwner{
				CreatedBy: row.Username,
				Project:   util.OptStringIfNotEmpty(row.Project),
			}

			key := iappConfigKey{
				AppName: row.ApplicationName,
				Owner:   owner,
			}

			iappConfigs[key] = IAppRunningConfiguration{
				AppName:       row.ApplicationName,
				Owner:         owner,
				ETag:          row.Version,
				Configuration: row.Configuration,
				JobId:         row.JobId,
				UpdatedAt:     time.Now(), // TODO use db
			}
		}
	})
}

// IAppReconfigureAll will invoke UpdateConfiguration on all configured .applications. This will typically be invoked
// by the individual services once they are ready to accept configuration events after a restart.
func IAppReconfigureAll() {
	iappConfigsMutex.Lock()
	configs := make([]IAppRunningConfiguration, 0, len(iappConfigs))
	for _, config := range iappConfigs {
		configs = append(configs, config)
	}
	iappConfigsMutex.Unlock()

	for _, config := range configs {
		key := iappConfigKey{AppName: config.AppName, Owner: config.Owner}
		handler, ok := IntegratedApplications[key.AppName]
		if ok {
			if iappConfigIsWaiting(config) {
				continue
			}

			job, ok := JobRetrieve(config.JobId)
			if !ok || job.Status.State.IsFinal() || iappConfigNeedsNewJob(config) {
				log.Warn(
					"iapp %#v (%v) is detached or missing its job",
					key,
					config.JobId,
				)
			} else {
				err := handler.UpdateConfiguration(job, config.ETag, config.Configuration)
				if err != nil {
					log.Warn("Error while reconfiguring job %v: %s", key, err)
				}
			}
		}
	}
}

const iappConfigWaitingForJob = "0"
const iappConfigDetached = "detached"

func iappConfigNeedsNewJob(config IAppRunningConfiguration) bool {
	return config.JobId == iappConfigDetached
}

func iappConfigIsWaiting(config IAppRunningConfiguration) bool {
	return config.JobId == iappConfigWaitingForJob
}

func iappConfigIsActive(config IAppRunningConfiguration) bool {
	return !iappConfigIsWaiting(config) && !iappConfigNeedsNewJob(config)
}

func iappStoreRunningConfiguration(key iappConfigKey, config IAppRunningConfiguration) {
	iappConfigsMutex.Lock()
	iappConfigs[key] = config
	iappConfigsMutex.Unlock()

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into integrated_application_config(username, project, application_name, configuration, job_id, version)
				values (:username, :project, :application_name, :configuration, :job_id, :version)
				on conflict (username, project, application_name)
				do update set
					configuration = excluded.configuration,
					job_id = excluded.job_id,
					version = excluded.version
			`,
			db.Params{
				"username":         key.Owner.CreatedBy,
				"project":          key.Owner.Project.Value,
				"application_name": key.AppName,
				"configuration":    string(config.Configuration),
				"job_id":           config.JobId,
				"version":          config.ETag,
			},
		)
	})
}

func iappDetachConfig(key iappConfigKey, config IAppRunningConfiguration) {
	config.JobId = iappConfigDetached
	iappStoreRunningConfiguration(key, config)
}

func iappCreateJob(appName string, owner orc.ResourceOwner, configuration json.RawMessage, etag util.Option[string]) (IAppRunningConfiguration, *util.HttpError) {
	svc, ok := IntegratedApplications[appName]
	if !ok {
		log.Warn("Was asked to configure %s but this integrated application is not actually configured!", appName)
		return IAppRunningConfiguration{}, util.UserHttpError("This application (%s) is not configured for this provider!", appName)
	}

	key := iappConfigKey{AppName: appName, Owner: owner}
	newEtag := util.RandomToken(16)

	res := orc.ProviderRegisteredResource[orc.JobSpecification]{
		Spec: orc.JobSpecification{
			ResourceSpecification: orc.ResourceSpecification{
				Product: apm.ProductReference{Id: appName, Category: appName, Provider: cfg.Provider.Id},
			},
			Application: orc.NameAndVersion{Name: appName, Version: "latest"},
			Name:        appName,
			Replicas:    1,
			Parameters:  map[string]orc.AppParameterValue{},
			Resources:   []orc.AppParameterValue{},
		},
		Project:   util.OptStringIfNotEmpty(""),
		CreatedBy: util.OptStringIfNotEmpty(owner.CreatedBy),
	}

	if svc.MutateSpecBeforeRegistration != nil {
		if err := svc.MutateSpecBeforeRegistration(owner, &res.Spec); err != nil {
			return IAppRunningConfiguration{}, err
		}
	}

	jobId := util.RetryOrPanic[string](fmt.Sprintf("registering iapp: %s", appName), func() (string, error) {
		resp, err := orc.JobsControlRegister.Invoke(
			fnd.BulkRequest[orc.ProviderRegisteredResource[orc.JobSpecification]]{
				Items: []orc.ProviderRegisteredResource[orc.JobSpecification]{res},
			},
		)
		if err != nil {
			return "", err.AsError()
		}
		if len(resp.Responses) != 1 {
			return "", fmt.Errorf("invalid amount of responses returned")
		}
		return resp.Responses[0].Id, nil
	})

	config := IAppRunningConfiguration{
		AppName:       appName,
		Owner:         owner,
		Configuration: configuration,
		JobId:         jobId,
		ETag:          newEtag,
	}
	iappStoreRunningConfiguration(key, config)

	job, ok := JobRetrieve(config.JobId)
	if !ok || job.Status.State.IsFinal() {
		log.Warn("Unable to retrieve running job for %v: %v", appName, config)
		iappDelete(key)
		return IAppRunningConfiguration{}, util.ServerHttpError("Internal error in %s. Try again later.", appName)
	}

	if err := svc.UpdateConfiguration(job, newEtag, configuration); err != nil {
		log.Warn("Configuration failed %s/%v: %s", appName, owner, err)
		return IAppRunningConfiguration{}, util.ServerHttpError("Internal error in %s. Try again later.", appName)
	}

	config.UpdatedAt = time.Now()
	iappStoreRunningConfiguration(key, config)
	return config, nil
}

func IAppConfigure(appName string, owner orc.ResourceOwner, etag util.Option[string], configuration json.RawMessage) *util.HttpError {
	key := iappConfigKey{
		AppName: appName,
		Owner:   owner,
	}

	iappConfigsMutex.Lock()
	config, ok := iappConfigs[key]
	iappConfigsMutex.Unlock()

	for ok && iappConfigIsWaiting(config) {
		time.Sleep(500 * time.Millisecond)
		iappConfigsMutex.Lock()
		config, ok = iappConfigs[key]
		iappConfigsMutex.Unlock()
	}

	etagsMatched := !etag.Present || etag.Value == config.ETag
	if ok && !etagsMatched {
		return util.UserHttpError("The application configuration has changed since you last loaded the page. " +
			"Please reload the page and try again.")
	}

	if !ok || iappConfigNeedsNewJob(config) {
		iappConfigsMutex.Lock()
		iappConfigs[key] = IAppRunningConfiguration{
			AppName:       appName,
			Owner:         owner,
			ETag:          config.ETag,
			Configuration: configuration,
			JobId:         iappConfigWaitingForJob,
		}
		iappConfigsMutex.Unlock()

		_, err := iappCreateJob(appName, owner, configuration, etag)
		return err
	}

	newEtag := util.RandomToken(16)
	svc, ok := IntegratedApplications[appName]
	if !ok {
		log.Warn("Was asked to configure %s but this integrated application is not actually configured!", appName)
		return util.UserHttpError("This application (%s) is not configured for this provider!", appName)
	}

	job, ok := JobRetrieve(config.JobId)
	if !ok || job.Status.State.IsFinal() {
		return IAppRestart(appName, owner)
	}

	err := svc.UpdateConfiguration(job, newEtag, configuration)
	if err == nil {
		config.Configuration = configuration
		config.ETag = newEtag
		config.UpdatedAt = time.Now()
		iappStoreRunningConfiguration(key, config)
	} else {
		log.Warn("Configuration failed %s/%v: %s", appName, owner, err)
		return util.ServerHttpError("Internal error in %s. Try again later.", appName)
	}

	return nil
}

type IAppRunningConfiguration struct {
	AppName       string
	Owner         orc.ResourceOwner
	ETag          string
	Configuration json.RawMessage
	JobId         string
	UpdatedAt     time.Time
}

func (c *IAppRunningConfiguration) IsDetached() bool {
	return c.JobId == iappConfigDetached
}

func IAppRetrieveConfiguration(appName string, owner orc.ResourceOwner) util.Option[IAppRunningConfiguration] {
	key := iappConfigKey{
		AppName: appName,
		Owner:   owner,
	}

	iappConfigsMutex.Lock()
	result, ok := iappConfigs[key]
	iappConfigsMutex.Unlock()

	if ok {
		return util.OptValue(result)
	} else {
		return util.OptNone[IAppRunningConfiguration]()
	}
}

func IAppReset(appName string, owner orc.ResourceOwner, etag util.Option[string]) *util.HttpError {
	handler, ok := IntegratedApplications[appName]
	if !ok {
		log.Warn("Failed to reset integrated application, there is no associated handler for %s.", appName)
		return util.ServerHttpError("Unknown application: %s", appName)
	}

	iappConfigsMutex.Lock()
	key := iappConfigKey{
		AppName: appName,
		Owner:   owner,
	}

	result, ok := iappConfigs[key]
	iappConfigsMutex.Unlock()

	if !ok {
		return nil
	}

	job, ok := JobRetrieve(result.JobId)
	var newConfig json.RawMessage
	var err *util.HttpError

	if !ok {
		// Nothing further to reset, but something was probably wrong so we log it
		log.Warn("No job associated with iapp: %v", result)

		newConfig = handler.RetrieveDefaultConfiguration(owner)
		iappDetachConfig(key, result)
	} else if etag.Present && etag.Value != result.ETag {
		err = util.UserHttpError("The configuration has changed since you last loaded the page. Reload it and try again.")
	} else {
		newConfig, err = handler.ResetConfiguration(job, result.Configuration)
	}

	if err != nil {
		return err
	} else {
		err = IAppConfigure(appName, owner, util.OptNone[string](), newConfig)
		return err
	}
}

func IAppRestart(appName string, owner orc.ResourceOwner) *util.HttpError {
	handler, ok := IntegratedApplications[appName]
	if !ok {
		return util.ServerHttpError("Could not find the application. Reload the page and try again later.")
	}

	config := IAppRetrieveConfiguration(appName, owner)
	if !config.Present {
		return util.ServerHttpError("Could not find the application. Reload the page and try again later.")
	}

	job, ok := JobRetrieve(config.Value.JobId)
	if !ok || job.Status.State.IsFinal() {
		// The stale job ID must be detached before IAppConfigure can create a replacement.
		key := iappConfigKey{AppName: appName, Owner: owner}
		iappDetachConfig(key, config.Value)
		return IAppConfigure(appName, owner, util.OptNone[string](), config.Value.Configuration)
	}

	err := handler.RestartApplication(job)
	return err
}

func IAppRetrieveAllByJobId() map[string]IAppRunningConfiguration {
	result := map[string]IAppRunningConfiguration{}

	iappConfigsMutex.Lock()
	for _, config := range iappConfigs {
		if !iappConfigIsActive(config) {
			continue
		}
		result[config.JobId] = config
	}
	iappConfigsMutex.Unlock()

	return result
}

func IAppRetrieveByJobId(jobId string) util.Option[IAppRunningConfiguration] {
	iappConfigsMutex.Lock()
	defer iappConfigsMutex.Unlock()

	for _, config := range iappConfigs {
		if config.JobId == jobId && iappConfigIsActive(config) {
			return util.OptValue(config)
		}
	}
	return util.OptNone[IAppRunningConfiguration]()
}

func iappDelete(key iappConfigKey) {
	iappConfigsMutex.Lock()
	delete(iappConfigs, key)
	iappConfigsMutex.Unlock()

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from integrated_application_config
				where
					application_name = :app_name
					and username = :username
					and project = :project
			`,
			db.Params{
				"app_name": key.AppName,
				"username": key.Owner.CreatedBy,
				"project":  key.Owner.Project.Value,
			},
		)
	})
}

func IAppDetachByJobId(jobId string) *util.HttpError {
	config := IAppRetrieveByJobId(jobId)
	if !config.Present {
		return nil
	}

	key := iappConfigKey{AppName: config.Value.AppName, Owner: config.Value.Owner}
	iappDetachConfig(key, config.Value)
	return nil
}
