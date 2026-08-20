package registry

import (
	"context"
	"fmt"
	"net/http"
	"strings"
	"sync"

	"github.com/anyascii/go"
	ocid "github.com/distribution/distribution/v3"
	"github.com/distribution/reference"
	"ucloud.dk/pkg/config"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	apm "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var repositoryFindDefaultMu sync.Mutex

func RepositoryFindProjectDefault(projectId string) (string, *util.HttpError) {
	if projectId == "" {
		return "", util.HttpErr(http.StatusBadRequest, "project ID is required")
	}
	return RepositoryFindDefault(orc.ResourceOwner{Project: util.OptValue(projectId)})
}

func RepositoryFindDefault(owner orc.ResourceOwner) (string, *util.HttpError) {
	if !owner.Project.Present && owner.CreatedBy == "" {
		return "", util.HttpErr(http.StatusBadRequest, "repository owner is required")
	}

	repositoryFindDefaultMu.Lock()
	defer repositoryFindDefaultMu.Unlock()

	ownerType := "user"
	ownerId := owner.CreatedBy
	ownerTitle := owner.CreatedBy
	if owner.Project.Present {
		ownerType = "project"
		ownerId = owner.Project.Value
		project, ok := controller.ProjectRetrieve(owner.Project.Value)
		if !ok {
			return "", util.HttpErr(http.StatusNotFound, "project not found")
		}
		ownerTitle = project.Specification.Title
	}
	providerId := "container-repository-" + ownerType + "-" + ownerId
	if repository, found, err := repositoryFindByProviderId(providerId); err != nil {
		return "", err
	} else if found {
		if accountingErr := repositoryCreate(&repository); accountingErr != nil {
			return "", accountingErr
		}
		controller.ContainerRepositoryTrack(repository)
		return repository.Specification.Name, nil
	}

	baseName := repositoryProjectName(ownerTitle)
	for suffix := 0; ; suffix++ {
		name := repositoryProjectNameWithSuffix(baseName, suffix)
		createdBy := util.OptNone[string]()
		if !owner.Project.Present {
			createdBy.Set(owner.CreatedBy)
		}
		request := orc.ProviderRegisteredResource[orc.ContainerRepositorySpecification]{
			CreatedBy: createdBy,
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
			Project:             owner.Project,
			ProjectAllRead:      owner.Project.Present,
			ProjectAllWrite:     owner.Project.Present,
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
			if accountingErr := repositoryCreate(&repository); accountingErr != nil {
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
			if accountingErr := repositoryCreate(&repository); accountingErr != nil {
				return "", accountingErr
			}
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

func repositoryDelete(repository *orc.ContainerRepository) *util.HttpError {
	owner := walletOwner(*repository)
	lock := repositoryOwnerLock(owner)
	lock.Lock()
	defer lock.Unlock()

	request := apm.ReportUsageRequest{
		IsDeltaCharge: false,
		Owner:         owner,
		CategoryIdV2: apm.ProductCategoryIdV2{
			Name:     repository.Specification.Product.Category,
			Provider: config.Provider.Id,
		},
		Usage:       0,
		Description: apm.ChargeDescription{Scope: util.OptValue("repository-" + repository.Id)},
	}
	if _, err := apm.ReportUsage.Invoke(fnd.BulkRequest[apm.ReportUsageRequest]{Items: []apm.ReportUsageRequest{request}}); err != nil {
		return util.HttpErr(http.StatusServiceUnavailable, "unable to clear repository accounting: %v", err)
	}
	restoreAccounting := true
	defer func() {
		if restoreAccounting {
			repositoryMarkDirty(owner)
		}
	}()

	catalog, err := accountingCatalog(context.Background())
	if err != nil {
		return util.HttpErr(http.StatusInternalServerError, "unable to enumerate registry repositories: %v", err)
	}
	remover, ok := registryAccounting.namespace.(ocid.RepositoryRemover)
	if !ok {
		return util.HttpErr(http.StatusInternalServerError, "registry does not support repository deletion")
	}
	root := repository.Specification.Name
	for _, repositoryName := range catalog {
		if repositoryName != root && !strings.HasPrefix(repositoryName, root+"/") {
			continue
		}
		named, err := reference.WithName(repositoryName)
		if err != nil {
			return util.HttpErr(http.StatusInternalServerError, "invalid registry repository name: %v", err)
		}
		if err := remover.Remove(context.Background(), named); err != nil {
			return util.HttpErr(http.StatusInternalServerError, "unable to delete registry repository: %v", err)
		}
	}
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `delete from container_repository_accounting where repository_id = :id`, db.Params{"id": repository.Id})
	})
	restoreAccounting = false
	return nil
}

func repositoryCreate(repository *orc.ContainerRepository) *util.HttpError {
	// A newly created repository has no reachable tags. Marking its owner ready allows the first tag operation to
	// perform the authoritative prospective scan after the controller has tracked the resource.
	owner := walletOwner(*repository)
	for _, existing := range controller.ContainerRepositoryEnumerateKnown() {
		if walletOwner(existing) == owner {
			return nil
		}
	}
	repositorySetReady(owner, true)
	return nil
}

func repositoryOnDeleted(repository *orc.ContainerRepository) {
	owner := walletOwner(*repository)
	repositorySetReady(owner, false)
	repositoryMarkDirty(owner)
}

func walletOwner(repository orc.ContainerRepository) apm.WalletOwner {
	return apm.WalletOwnerFromIds(repository.Owner.CreatedBy, repository.Owner.Project.Value)
}

func repositoryOwnerKey(owner apm.WalletOwner) string {
	return string(owner.Type) + ":" + owner.Reference()
}

func repositoryOwnerLock(owner apm.WalletOwner) *sync.Mutex {
	key := repositoryOwnerKey(owner)
	registryAccounting.mu.Lock()
	lock := registryAccounting.locks[key]
	if lock == nil {
		lock = &sync.Mutex{}
		registryAccounting.locks[key] = lock
	}
	registryAccounting.mu.Unlock()
	return lock
}

func repositorySetReady(owner apm.WalletOwner, ready bool) {
	registryAccounting.mu.Lock()
	registryAccounting.ready[repositoryOwnerKey(owner)] = ready
	registryAccounting.mu.Unlock()
}

func repositoryIsReady(owner apm.WalletOwner) bool {
	registryAccounting.mu.Lock()
	ready := registryAccounting.ready[repositoryOwnerKey(owner)]
	registryAccounting.mu.Unlock()
	return ready
}

func repositoryMarkDirty(owner apm.WalletOwner) {
	repositoryMarkDirtyEx(repositoryOwnerKey(owner))
}

func repositoryMarkDirtyEx(ownerKey string) {
	select {
	case registryAccounting.dirty <- ownerKey:
	default:
	}
}

func repositoryBelongsToRoot(root, repository string) bool {
	return repository == root || strings.HasPrefix(repository, root+"/")
}
