package k8s

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"

	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/containers"
	"ucloud.dk/pkg/integrations/k8s/shared"
	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func stackSpawnDeclaredJob(request orc.StacksProviderSpawnDeclaredJobRequest) (fnd.FindByStringId, *util.HttpError) {
	stackInstance := strings.TrimSpace(request.StackInstance)
	stackName := strings.TrimSpace(request.StackName)
	stackEntityId := strings.TrimSpace(request.StackEntityId)
	stateFolder := strings.TrimSpace(request.StateFolder)

	if stackInstance == "" || stackName == "" || stackEntityId == "" || stateFolder == "" {
		return fnd.FindByStringId{}, util.HttpErr(http.StatusBadRequest, "the application is unavailable. Contact your service provider")
	}
	if strings.TrimSpace(request.Owner.CreatedBy) == "" {
		return fnd.FindByStringId{}, util.HttpErr(http.StatusBadRequest, "the application owner is unavailable. Contact your service provider")
	}
	if _, ok := orc.DriveIdFromUCloudPath(stateFolder); !ok {
		return fnd.FindByStringId{}, util.HttpErr(http.StatusBadRequest, "the application storage is unavailable. Contact your service provider")
	}

	declaration := request.Job
	if strings.TrimSpace(declaration.Image) == "" {
		return fnd.FindByStringId{}, util.HttpErr(http.StatusBadRequest, "the recovery image is not configured. Contact your service provider")
	}
	if declaration.Port <= 0 || declaration.Port > 65535 {
		return fnd.FindByStringId{}, util.HttpErr(http.StatusBadRequest, "the network settings for recovery are invalid. Contact your service provider")
	}
	if strings.TrimSpace(request.Product.Id) == "" || request.Product.Provider != cfg.Provider.Id {
		return fnd.FindByStringId{}, util.HttpErr(http.StatusBadRequest, "no suitable machine is available to start recovery. Contact your service provider")
	}
	if strings.TrimSpace(request.Application.Name) == "" || strings.TrimSpace(request.Application.Version) == "" {
		return fnd.FindByStringId{}, util.HttpErr(http.StatusBadRequest, "the application for recovery is unavailable. Contact your service provider")
	}

	args, marshalErr := json.Marshal(util.NonNilSlice(declaration.Args))
	if marshalErr != nil {
		return fnd.FindByStringId{}, util.ServerHttpError("recovery is not configured correctly. Contact your service provider")
	}

	for _, existing := range controller.JobRetrieveAll() {
		if !containers.StackDeclaredJobIsDeclared(existing) {
			continue
		}
		if strings.TrimSpace(existing.Specification.Labels[orc.ResourceLabelStackInstance]) != stackInstance {
			continue
		}
		if !stackControlOwnerScopeMatches(existing.Owner, request.Owner) {
			continue
		}
		return fnd.FindByStringId{}, util.HttpErr(http.StatusConflict, "a recovery job is already running for this application")
	}

	spec := orc.JobSpecification{
		ResourceSpecification: orc.ResourceSpecification{
			Product: request.Product,
			Labels: map[string]string{
				orc.ResourceLabelStack:            "true",
				orc.ResourceLabelStackName:        stackName,
				orc.ResourceLabelStackInstance:    stackInstance,
				orc.ResourceLabelStackEntity:      stackEntityId,
				orc.ResourceLabelStackStateFolder: stateFolder,
				orc.ResourceLabelStackController:  "true",
				orc.ResourceLabelDisasterRecovery: "true",
				orc.ResourceLabelUcxAppName:       request.Application.Name,
				orc.ResourceLabelUcxAppVersion:    request.Application.Version,
				orc.ResourceLabelUcxPort:          strconv.Itoa(declaration.Port),
			},
		},
		Application: orc.NameAndVersion{Name: "unknown", Version: "unknown"},
		Name:        stackName + " recovery",
		Replicas:    1,
		Parameters: map[string]orc.AppParameterValue{
			containers.StackDeclaredJobImageParameter: orc.AppParameterValueText(declaration.Image),
			containers.StackDeclaredJobArgsParameter:  orc.AppParameterValueText(string(args)),
			containers.StackDeclaredJobPortParameter:  orc.AppParameterValueInteger(int64(declaration.Port)),
		},
		Resources: []orc.AppParameterValue{
			orc.AppParameterValueFileWithMountPath(stateFolder, false, containers.StackDeclaredJobMountPath),
		},
	}

	response, err := orc.JobsControlRegister.Invoke(fnd.BulkRequest[orc.ProviderRegisteredResource[orc.JobSpecification]]{
		Items: []orc.ProviderRegisteredResource[orc.JobSpecification]{
			{
				Spec:      spec,
				CreatedBy: util.OptStringIfNotEmpty(request.Owner.CreatedBy),
				Project:   util.OptStringIfNotEmpty(request.Owner.Project.Value),
			},
		},
	})
	if err != nil {
		return fnd.FindByStringId{}, err
	}
	if len(response.Responses) != 1 {
		return fnd.FindByStringId{}, util.ServerHttpError("could not prepare recovery. Try again or contact your service provider")
	}

	jobId := response.Responses[0].Id
	job, ok := controller.JobRetrieve(jobId)
	if !ok {
		return fnd.FindByStringId{}, util.ServerHttpError("recovery was prepared, but its status could not be read. Contact your service provider")
	}

	shared.RequestSchedule(job)

	return fnd.FindByStringId{Id: jobId}, nil
}
