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
	orc "ucloud.dk/shared/pkg/orc2"
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

func controllerIntegratedApps() {
	if !RunsServerCode() {
		return
	}

	initIApps()

	for k, v := range IntegratedApplications {
		controllerIntegratedApp(k, v)
	}
}

func IAppConfigureFromLegacy(appName string, owner orc.ResourceOwner) (util.Option[IAppRunningConfiguration], *util.HttpError) {
	handler, ok := IntegratedApplications[appName]
	if ok && handler.RetrieveLegacyConfiguration != nil {
		rawConfig := handler.RetrieveLegacyConfiguration(owner)
		if rawConfig.Present {
			err := ConfigureIApp(appName, owner, util.OptNone[string](), rawConfig.Value)
			if err != nil {
				return util.OptNone[IAppRunningConfiguration](), err
			} else {
				config := RetrieveIAppConfiguration(appName, owner)
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

func controllerIntegratedApp(appName string, handler IntegratedApplicationHandler) {
	if handler.Flags&IntegratedAppInternal == 0 {
		retrieveRpc := orc.IAppProviderRetrieveConfiguration[json.RawMessage](appName)
		retrieveRpc.Handler(func(info rpc.RequestInfo, request orc.IAppProviderRetrieveConfigRequest) (orc.IAppProviderRetrieveConfigResponse[json.RawMessage], *util.HttpError) {
			config := RetrieveIAppConfiguration(appName, request.Principal)
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
			err := ConfigureIApp(appName, request.Principal, request.ExpectedETag, request.Config)
			return util.Empty{}, err
		})

		resetRpc := orc.IAppProviderReset[json.RawMessage](appName)
		resetRpc.Handler(func(info rpc.RequestInfo, request orc.IAppProviderResetRequest) (util.Empty, *util.HttpError) {
			err := ResetIApp(appName, request.Principal, request.ExpectedETag)
			return util.Empty{}, err
		})

		restartRpc := orc.IAppProviderRestart[json.RawMessage](appName)
		restartRpc.Handler(func(info rpc.RequestInfo, request orc.IAppProviderRestartRequest) (util.Empty, *util.HttpError) {
			err := RestartIApp(appName, request.Principal)
			return util.Empty{}, err
		})
	}
}

func initIApps() {
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

// ReconfigureAllIApps will invoke UpdateConfiguration on all configured .applications. This will typically be invoked
// by the individual services once they are ready to accept configuration events after a restart.
func ReconfigureAllIApps() {
	allJobs := JobsListServer()
	jobsById := map[string]*orc.Job{}
	for _, job := range allJobs {
		jobsById[job.Id] = job
	}

	iappConfigsMutex.Lock()
	for key, config := range iappConfigs {
		handler, ok := IntegratedApplications[key.AppName]
		if ok {
			job, ok := jobsById[config.JobId]
			if !ok {
				log.Info("Deleting iapp, can no longer find associated job %v", key)
				iappConfigsMutex.Unlock() // unlock for the delete function
				iappDelete(key)
				iappConfigsMutex.Lock() // relock following delete
			} else {
				err := handler.UpdateConfiguration(job, config.ETag, config.Configuration)
				if err != nil {
					log.Warn("Error while reconfiguring job %v: %s", key, err)
				}
			}
		}
	}
	iappConfigsMutex.Unlock()
}

const iappConfigWaitingForJob = "0"

func ConfigureIApp(appName string, owner orc.ResourceOwner, etag util.Option[string], configuration json.RawMessage) *util.HttpError {
	newEtag := util.RandomToken(16)
	etagsMatched := false

	svc, ok := IntegratedApplications[appName]
	if !ok {
		log.Warn("Was asked to configure %s but this integrated application is not actually configured!", appName)
		return util.UserHttpError("This application (%s) is not configured for this provider!", appName)
	}

	key := iappConfigKey{
		AppName: appName,
		Owner:   owner,
	}

	iappConfigsMutex.Lock()
	config, ok := iappConfigs[key]
	if !ok {
		iappConfigs[key] = IAppRunningConfiguration{JobId: iappConfigWaitingForJob}
	} else {
		etagsMatched = !etag.Present || etag.Value == config.ETag
		if etagsMatched {
			config.ETag = newEtag
			iappConfigs[key] = config
		}
	}
	iappConfigsMutex.Unlock()

	if !ok {
		res := orc.ProviderRegisteredResource[orc.JobSpecification]{
			Spec: orc.JobSpecification{
				Product: apm.ProductReference{
					Id:       appName,
					Category: appName,
					Provider: cfg.Provider.Id,
				},
				Application: orc.NameAndVersion{
					Name:    appName,
					Version: "latest",
				},
				Name:           appName,
				Replicas:       1,
				Parameters:     map[string]orc.AppParameterValue{},
				TimeAllocation: util.Option[orc.SimpleDuration]{},
				Resources:      []orc.AppParameterValue{},
			},
			Project:   util.OptStringIfNotEmpty(""),
			CreatedBy: util.OptStringIfNotEmpty(owner.CreatedBy),
		}

		if svc.MutateSpecBeforeRegistration != nil {
			err := svc.MutateSpecBeforeRegistration(owner, &res.Spec)
			if err != nil {
				return err
			}
		}

		jobId := util.RetryOrPanic[string](fmt.Sprintf("registering iapp: %s", appName), func() (string, error) {
			resp, err := orc.JobsControlRegister.Invoke(fnd.BulkRequest[orc.ProviderRegisteredResource[orc.JobSpecification]]{
				Items: []orc.ProviderRegisteredResource[orc.JobSpecification]{res},
			})

			if err != nil {
				return "", err.AsError()
			}

			if len(resp.Responses) != 1 {
				return "", fmt.Errorf("invalid amount of responses returned")
			}

			return resp.Responses[0].Id, nil
		})

		config = IAppRunningConfiguration{
			AppName:       appName,
			Owner:         owner,
			Configuration: configuration,
			JobId:         jobId,
		}
		ok = true
		etagsMatched = true

		iappConfigsMutex.Lock()
		iappConfigs[key] = config
		iappConfigsMutex.Unlock()
	} else {
		// Spin until registered by branch above
		for config.JobId == iappConfigWaitingForJob {
			iappConfigsMutex.Lock()
			config, ok = iappConfigs[key]
			etagsMatched = !etag.Present || etag.Value == config.ETag
			if etagsMatched {
				config.ETag = newEtag
				iappConfigs[key] = config
			}
			iappConfigsMutex.Unlock()

			if config.JobId == iappConfigWaitingForJob {
				time.Sleep(500 * time.Millisecond)
			}
		}
	}

	job, ok := RetrieveJob(config.JobId)
	if !ok || job.Status.State.IsFinal() {
		log.Warn("Unable to retrieve running job for %v: %v", appName, config)
		iappDelete(key)
		return util.ServerHttpError("Internal error in %s. Try again later.", appName)
	}

	if !etagsMatched {
		return util.UserHttpError("The application configuration has changed since you last loaded the page. " +
			"Please reload the page and try again.")
	}

	err := svc.UpdateConfiguration(job, newEtag, configuration)
	if err == nil {
		config.Configuration = configuration
		config.ETag = newEtag
		config.UpdatedAt = time.Now()

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
					"username":         owner.CreatedBy,
					"project":          owner.Project.Value,
					"application_name": appName,
					"configuration":    string(configuration),
					"job_id":           job.Id,
					"version":          newEtag,
				},
			)
		})
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

func RetrieveIAppConfiguration(appName string, owner orc.ResourceOwner) util.Option[IAppRunningConfiguration] {
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

func ResetIApp(appName string, owner orc.ResourceOwner, etag util.Option[string]) *util.HttpError {
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

	job, ok := RetrieveJob(result.JobId)
	var newConfig json.RawMessage
	var err *util.HttpError

	if !ok {
		// Nothing further to reset, but something was probably wrong so we log it
		log.Warn("No job associated with iapp: %v", result)

		iappDelete(key)

		newConfig = handler.RetrieveDefaultConfiguration(owner)
	} else if etag.Present && etag.Value != result.ETag {
		err = util.UserHttpError("The configuration has changed since you last loaded the page. Reload it and try again.")
	} else {
		newConfig, err = handler.ResetConfiguration(job, result.Configuration)
	}

	if err != nil {
		return err
	} else {
		err = ConfigureIApp(appName, owner, util.OptNone[string](), newConfig)
		return err
	}
}

func RestartIApp(appName string, owner orc.ResourceOwner) *util.HttpError {
	handler, ok := IntegratedApplications[appName]
	if !ok {
		return util.ServerHttpError("Could not find the application. Reload the page and try again later.")
	}

	config := RetrieveIAppConfiguration(appName, owner)
	if !config.Present {
		return util.ServerHttpError("Could not find the application. Reload the page and try again later.")
	}

	job, ok := RetrieveJob(config.Value.JobId)
	if !ok {
		iappDelete(iappConfigKey{
			AppName: appName,
			Owner:   owner,
		})
		return util.ServerHttpError("Internal error. Reload the page and try again later.")
	}

	err := handler.RestartApplication(job)
	return err
}

func RetrieveIAppsByJobId() map[string]IAppRunningConfiguration {
	result := map[string]IAppRunningConfiguration{}

	iappConfigsMutex.Lock()
	for _, config := range iappConfigs {
		result[config.JobId] = config
	}
	iappConfigsMutex.Unlock()

	return result
}

func RetrieveIAppByJobId(jobId string) util.Option[IAppRunningConfiguration] {
	iappConfigsMutex.Lock()
	defer iappConfigsMutex.Unlock()

	for _, config := range iappConfigs {
		if config.JobId == jobId {
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
