package registry

import (
	"fmt"
	"net/http"
	"strings"
	"sync"

	"github.com/anyascii/go"
	"ucloud.dk/pkg/config"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var repositoryFindProjectDefaultMu sync.Mutex

func RepositoryFindProjectDefault(projectId string) (string, *util.HttpError) {
	if projectId == "" {
		return "", util.HttpErr(http.StatusBadRequest, "project ID is required")
	}

	repositoryFindProjectDefaultMu.Lock()
	defer repositoryFindProjectDefaultMu.Unlock()

	providerId := "container-repository-project-" + projectId
	if repository, found, err := repositoryFindByProviderId(providerId); err != nil {
		return "", err
	} else if found {
		controller.ContainerRepositoryTrack(repository)
		return repository.Specification.Name, nil
	}

	project, ok := controller.ProjectRetrieve(projectId)
	if !ok {
		return "", util.HttpErr(http.StatusNotFound, "project not found")
	}

	baseName := repositoryProjectName(project.Specification.Title)
	for suffix := 0; ; suffix++ {
		name := repositoryProjectNameWithSuffix(baseName, suffix)
		request := orc.ProviderRegisteredResource[orc.ContainerRepositorySpecification]{
			Spec: orc.ContainerRepositorySpecification{
				Name: name,
				ResourceSpecification: orc.ResourceSpecification{
					Product: apm.ProductReference{
						Id:       shared.ServiceConfig.FileSystem.Name,
						Category: shared.ServiceConfig.FileSystem.Name,
						Provider: config.Provider.Id,
					},
				},
			},
			ProviderGeneratedId: util.OptValue(providerId),
			Project:             util.OptValue(projectId),
			ProjectAllRead:      true,
		}

		response, err := orc.ContainerRepositoriesControlRegister.Invoke(fnd.BulkRequestOf(request))
		if err == nil {
			if len(response.Responses) != 1 {
				return "", util.HttpErr(http.StatusBadGateway, "Core returned an invalid repository registration response")
			}
			repository, retrieveErr := orc.ContainerRepositoriesControlRetrieve.Invoke(orc.ContainerRepositoriesControlRetrieveRequest{
				Id: response.Responses[0].Id,
				ContainerRepositoryFlags: orc.ContainerRepositoryFlags{
					ResourceFlags: orc.ResourceFlagsIncludeAll(),
				},
			})
			if retrieveErr != nil {
				return "", retrieveErr
			}
			if accountingErr := accountingCreateRepository(&repository); accountingErr != nil {
				return "", accountingErr
			}
			controller.ContainerRepositoryTrack(repository)
			return repository.Specification.Name, nil
		}
		if err.StatusCode != http.StatusConflict {
			return "", err
		}

		if repository, found, findErr := repositoryFindByProviderId(providerId); findErr != nil {
			return "", findErr
		} else if found {
			controller.ContainerRepositoryTrack(repository)
			return repository.Specification.Name, nil
		}
	}
}

func repositoryFindByProviderId(providerId string) (orc.ContainerRepository, bool, *util.HttpError) {
	request := orc.ContainerRepositoriesControlBrowseRequest{ItemsPerPage: 10}
	request.FilterProviderIds.Set(providerId)
	request.IncludeOthers = true
	page, err := orc.ContainerRepositoriesControlBrowse.Invoke(request)
	if err != nil {
		return orc.ContainerRepository{}, false, err
	}
	if len(page.Items) == 0 {
		return orc.ContainerRepository{}, false, nil
	}
	return page.Items[0], true, nil
}

func repositoryProjectName(title string) string {
	transliterated := strings.ToLower(anyascii.Transliterate(title))
	var result strings.Builder
	separator := false
	for _, r := range transliterated {
		if r >= 'a' && r <= 'z' || r >= '0' && r <= '9' {
			if separator && result.Len() > 0 {
				result.WriteByte('-')
			}
			result.WriteRune(r)
			separator = false
		} else {
			separator = true
		}
	}
	name := strings.Trim(result.String(), "-")
	if name == "" {
		name = "project"
	}
	if len(name) > 32 {
		name = strings.TrimRight(name[:32], "-")
	}
	return name
}

func repositoryProjectNameWithSuffix(base string, suffix int) string {
	if suffix == 0 {
		return base
	}
	ending := fmt.Sprintf("-%d", suffix)
	maxBaseLength := 32 - len(ending)
	return strings.TrimRight(base[:min(len(base), maxBaseLength)], "-") + ending
}
