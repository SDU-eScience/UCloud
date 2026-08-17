package registry

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"slices"
	"sort"
	"strings"
	"sync"
	"time"

	ocid "github.com/distribution/distribution/v3"
	ocidstorage "github.com/distribution/distribution/v3/registry/storage"
	"github.com/distribution/distribution/v3/registry/storage/driver"
	ocidfs "github.com/distribution/distribution/v3/registry/storage/driver/filesystem"
	"github.com/distribution/reference"
	"github.com/opencontainers/go-digest"
	v1 "github.com/opencontainers/image-spec/specs-go/v1"
	"ucloud.dk/pkg/config"
	"ucloud.dk/pkg/controller"
	apm "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const (
	accountingMiddlewareName = "ucloud-accounting"
	accountingUnitBytes      = int64(1_000_000_000)
	accountingScanInterval   = time.Hour
	accountingRetryInterval  = 30 * time.Second
)

var registryAccounting = struct {
	mu        sync.Mutex
	namespace ocid.Namespace
	locks     map[string]*sync.Mutex
	ready     map[string]bool
	dirty     chan string
}{
	locks: map[string]*sync.Mutex{},
	ready: map[string]bool{},
	dirty: make(chan string, 256),
}

type accountingRepository struct {
	repository ocid.Repository
}

func newAccountingRepository(_ context.Context, repository ocid.Repository, _ map[string]any) (ocid.Repository, error) {
	return &accountingRepository{repository: repository}, nil
}

func (r *accountingRepository) Named() reference.Named {
	return r.repository.Named()
}

func (r *accountingRepository) Manifests(ctx context.Context, options ...ocid.ManifestServiceOption) (ocid.ManifestService, error) {
	manifests, err := r.repository.Manifests(ctx, options...)
	if err != nil {
		return nil, err
	}
	return &accountingManifestService{
		ManifestService: manifests,
		repository:      r.repository.Named().Name(),
		request:         requestStateFromContext(ctx),
	}, nil
}

func (r *accountingRepository) Blobs(ctx context.Context) ocid.BlobStore {
	return &accountingBlobStore{
		BlobStore:  r.repository.Blobs(ctx),
		repository: r.repository.Named().Name(),
		request:    requestStateFromContext(ctx),
	}
}

func (r *accountingRepository) Tags(ctx context.Context) ocid.TagService {
	return &accountingTagService{
		TagService: r.repository.Tags(ctx),
		repository: r.repository.Named().Name(),
		request:    requestStateFromContext(ctx),
	}
}

type accountingBlobStore struct {
	ocid.BlobStore
	repository string
	request    *requestState
}

func (s *accountingBlobStore) Stat(ctx context.Context, dgst digest.Digest) (v1.Descriptor, error) {
	return s.BlobStore.Stat(ctx, dgst)
}

func (s *accountingBlobStore) Get(ctx context.Context, dgst digest.Digest) ([]byte, error) {
	return s.BlobStore.Get(ctx, dgst)
}

func (s *accountingBlobStore) Open(ctx context.Context, dgst digest.Digest) (io.ReadSeekCloser, error) {
	return s.BlobStore.Open(ctx, dgst)
}

func (s *accountingBlobStore) Put(ctx context.Context, mediaType string, payload []byte) (v1.Descriptor, error) {
	return s.BlobStore.Put(ctx, mediaType, payload)
}

func (s *accountingBlobStore) Create(ctx context.Context, options ...ocid.BlobCreateOption) (ocid.BlobWriter, error) {
	writer, err := s.BlobStore.Create(ctx, options...)
	if err != nil {
		return nil, err
	}
	return &accountingBlobWriter{BlobWriter: writer, repository: s.repository, request: s.request}, nil
}

func (s *accountingBlobStore) Resume(ctx context.Context, id string) (ocid.BlobWriter, error) {
	writer, err := s.BlobStore.Resume(ctx, id)
	if err != nil {
		return nil, err
	}
	return &accountingBlobWriter{BlobWriter: writer, repository: s.repository, request: s.request}, nil
}

func (s *accountingBlobStore) ServeBlob(ctx context.Context, w http.ResponseWriter, r *http.Request, dgst digest.Digest) error {
	return s.BlobStore.ServeBlob(ctx, w, r, dgst)
}

func (s *accountingBlobStore) Delete(ctx context.Context, dgst digest.Digest) error {
	return s.BlobStore.Delete(ctx, dgst)
}

type accountingBlobWriter struct {
	ocid.BlobWriter
	repository string
	request    *requestState
}

type accountingManifestService struct {
	ocid.ManifestService
	repository string
	request    *requestState
}

func (s *accountingManifestService) Put(ctx context.Context, manifest ocid.Manifest, options ...ocid.ManifestServiceOption) (digest.Digest, error) {
	return s.ManifestService.Put(ctx, manifest, options...)
}

func (s *accountingManifestService) Delete(ctx context.Context, dgst digest.Digest) error {
	repository, ok := controller.ContainerRepositoryRetrieveByRepository(s.repository)
	if !ok {
		return ocid.ErrAccessDenied
	}
	lock := repositoryOwnerLock(walletOwner(repository))
	lock.Lock()
	defer lock.Unlock()

	err := s.ManifestService.Delete(ctx, dgst)
	if err == nil {
		repositoryMarkDirty(walletOwner(repository))
	}
	return err
}

type accountingTagService struct {
	ocid.TagService
	repository string
	request    *requestState
}

func (s *accountingTagService) Tag(ctx context.Context, tag string, descriptor v1.Descriptor) error {
	repository, ok := controller.ContainerRepositoryRetrieveByRepository(s.repository)
	if !ok || s.request == nil || s.request.owner != walletOwner(repository) {
		return ocid.ErrAccessDenied
	}

	owner := walletOwner(repository)
	lock := repositoryOwnerLock(owner)
	lock.Lock()
	defer lock.Unlock()

	if err := accountingCheckTagMutation(ctx, owner, s.repository, tag, descriptor); err != nil {
		return err
	}
	if err := s.TagService.Tag(ctx, tag, descriptor); err != nil {
		return err
	}
	repositoryMarkDirty(owner)
	return nil
}

func (s *accountingTagService) Untag(ctx context.Context, tag string) error {
	repository, ok := controller.ContainerRepositoryRetrieveByRepository(s.repository)
	if !ok {
		return ocid.ErrAccessDenied
	}
	owner := walletOwner(repository)
	lock := repositoryOwnerLock(owner)
	lock.Lock()
	defer lock.Unlock()

	err := s.TagService.Untag(ctx, tag)
	if err == nil {
		repositoryMarkDirty(owner)
	}
	return err
}

type accountingTagOverride struct {
	repository string
	tag        string
	descriptor v1.Descriptor
}

type repositoryAccountingUsage struct {
	repository orc.ContainerRepository
	exactBytes int64
	usage      int64
}

type repositoryAccountingRow struct {
	RepositoryId   string
	RepositoryName string
	OwnerType      string
	Username       string
	ProjectId      string
	Category       string
	ExactBytes     int64
	ReportedUsage  int64
}

func initAccounting(root string) error {
	regDriver, err := ocidfs.FromParameters(map[string]any{"rootdirectory": root})
	if err != nil {
		return err
	}
	namespace, err := ocidstorage.NewRegistry(context.Background(), regDriver, ocidstorage.EnableDelete)
	if err != nil {
		return err
	}

	registryAccounting.mu.Lock()
	registryAccounting.namespace = namespace
	registryAccounting.mu.Unlock()

	accountingReconcileAll()
	go accountingLoop()
	return nil
}

func accountingLoop() {
	ticker := time.NewTicker(accountingScanInterval)
	defer ticker.Stop()
	for {
		select {
		case ownerKey := <-registryAccounting.dirty:
			if err := accountingReconcileOwnerKey(ownerKey); err != nil {
				log.Warn("Failed to reconcile container registry accounting for %s: %v", ownerKey, err)
				time.AfterFunc(accountingRetryInterval, func() { repositoryMarkDirtyEx(ownerKey) })
			}
		case <-ticker.C:
			accountingReconcileAll()
		}
	}
}

func accountingReconcileAll() {
	repositories := controller.ContainerRepositoryEnumerateKnown()
	owners := map[string]apm.WalletOwner{}
	activeRepositoryIds := map[string]bool{}
	for _, repository := range repositories {
		owner := walletOwner(repository)
		owners[repositoryOwnerKey(owner)] = owner
		activeRepositoryIds[repository.Id] = true
	}

	for ownerKey := range owners {
		if err := accountingReconcileOwnerKey(ownerKey); err != nil {
			log.Warn("Failed to reconcile container registry accounting for %s: %v", ownerKey, err)
			repositoryMarkDirtyEx(ownerKey)
		}
	}
	accountingClearStaleScopes(activeRepositoryIds)
}

func accountingReconcileOwnerKey(ownerKey string) error {
	var owner apm.WalletOwner
	found := false
	for _, repository := range controller.ContainerRepositoryEnumerateKnown() {
		candidate := walletOwner(repository)
		if repositoryOwnerKey(candidate) == ownerKey {
			owner = candidate
			found = true
			break
		}
	}
	if !found {
		return nil
	}

	lock := repositoryOwnerLock(owner)
	lock.Lock()
	defer lock.Unlock()

	usage, err := accountingScanOwner(context.Background(), owner, nil)
	if err != nil {
		repositorySetReady(owner, false)
		return err
	}
	accountingPersistCalculated(usage)
	if err := accountingReport(usage); err != nil {
		repositorySetReady(owner, false)
		return err
	}
	accountingPersistReported(usage)
	repositorySetReady(owner, true)
	return nil
}

func accountingScanOwner(ctx context.Context, owner apm.WalletOwner, override *accountingTagOverride) ([]repositoryAccountingUsage, error) {
	repositories := make([]orc.ContainerRepository, 0)
	for _, repository := range controller.ContainerRepositoryEnumerateKnown() {
		if walletOwner(repository) == owner {
			repositories = append(repositories, repository)
		}
	}
	slices.SortFunc(repositories, func(a, b orc.ContainerRepository) int {
		if result := strings.Compare(a.Specification.Name, b.Specification.Name); result != 0 {
			return result
		}
		return strings.Compare(a.Id, b.Id)
	})
	if len(repositories) > 1 {
		category := repositories[0].Specification.Product.Category
		for _, repository := range repositories[1:] {
			if repository.Specification.Product.Category != category {
				return nil, errors.New("container repositories for one owner must use a single storage category")
			}
		}
	}

	catalog, err := accountingCatalog(ctx)
	if err != nil {
		if _, ok := errors.AsType[driver.PathNotFoundError](err); ok {
			catalog = make([]string, 0)
		} else {
			return nil, err
		}
	}
	seenBlobs := map[digest.Digest]bool{}
	seenManifests := map[digest.Digest]bool{}
	result := make([]repositoryAccountingUsage, 0, len(repositories))

	for _, logicalRepository := range repositories {
		entry := repositoryAccountingUsage{repository: logicalRepository}
		root := logicalRepository.Specification.Name
		for _, repositoryName := range catalog {
			if repositoryName != root && !strings.HasPrefix(repositoryName, root+"/") {
				continue
			}
			named, err := reference.WithName(repositoryName)
			if err != nil {
				return nil, err
			}
			repository, err := registryAccounting.namespace.Repository(ctx, named)
			if err != nil {
				return nil, err
			}
			tags := repository.Tags(ctx)
			tagNames, err := tags.All(ctx)
			if err != nil {
				if _, empty := err.(ocid.ErrRepositoryUnknown); !empty {
					return nil, fmt.Errorf("enumerating tags in %s: %w", repositoryName, err)
				}
				tagNames = nil
			}
			sort.Strings(tagNames)
			overrideApplied := false
			for _, tag := range tagNames {
				descriptor, err := tags.Get(ctx, tag)
				if err != nil {
					return nil, fmt.Errorf("reading tag %s:%s: %w", repositoryName, tag, err)
				}
				if override != nil && override.repository == repositoryName && override.tag == tag {
					descriptor = override.descriptor
					overrideApplied = true
				}
				if err := accountingWalkManifest(ctx, repository, descriptor.Digest, seenBlobs, seenManifests, &entry.exactBytes); err != nil {
					return nil, fmt.Errorf("walking %s:%s: %w", repositoryName, tag, err)
				}
			}
			if override != nil && override.repository == repositoryName && !overrideApplied {
				if err := accountingWalkManifest(ctx, repository, override.descriptor.Digest, seenBlobs, seenManifests, &entry.exactBytes); err != nil {
					return nil, fmt.Errorf("walking prospective %s:%s: %w", repositoryName, override.tag, err)
				}
			}
		}
		result = append(result, entry)
	}
	if err := accountingRoundUsage(result); err != nil {
		return nil, err
	}
	return result, nil
}

func accountingWalkManifest(
	ctx context.Context,
	repository ocid.Repository,
	dgst digest.Digest,
	seenBlobs map[digest.Digest]bool,
	seenManifests map[digest.Digest]bool,
	usage *int64,
) error {
	if !seenBlobs[dgst] {
		descriptor, err := registryAccounting.namespace.BlobStatter().Stat(ctx, dgst)
		if err != nil {
			return err
		}
		if descriptor.Size < 0 || *usage > int64(^uint64(0)>>1)-descriptor.Size {
			return errors.New("registry usage exceeds int64")
		}
		*usage += descriptor.Size
		seenBlobs[dgst] = true
	}
	if seenManifests[dgst] {
		return nil
	}
	seenManifests[dgst] = true

	manifests, err := repository.Manifests(ctx)
	if err != nil {
		return err
	}
	manifest, err := manifests.Get(ctx, dgst)
	if err != nil {
		return err
	}
	for _, referenced := range manifest.References() {
		exists, err := manifests.Exists(ctx, referenced.Digest)
		if err != nil {
			return err
		}
		if exists {
			if err := accountingWalkManifest(ctx, repository, referenced.Digest, seenBlobs, seenManifests, usage); err != nil {
				return err
			}
		} else if !seenBlobs[referenced.Digest] {
			descriptor, err := registryAccounting.namespace.BlobStatter().Stat(ctx, referenced.Digest)
			if err != nil {
				return err
			}
			if descriptor.Size < 0 || *usage > int64(^uint64(0)>>1)-descriptor.Size {
				return errors.New("registry usage exceeds int64")
			}
			*usage += descriptor.Size
			seenBlobs[referenced.Digest] = true
		}
	}
	return nil
}

func accountingRoundUsage(usage []repositoryAccountingUsage) error {
	totalBytes := int64(0)
	baseUnits := int64(0)
	for idx := range usage {
		usage[idx].usage = usage[idx].exactBytes / accountingUnitBytes
		if totalBytes > int64(^uint64(0)>>1)-usage[idx].exactBytes {
			return errors.New("container registry usage exceeds int64")
		}
		totalBytes += usage[idx].exactBytes
		baseUnits += usage[idx].usage
	}
	remaining := totalBytes/accountingUnitBytes - baseUnits
	for idx := range usage {
		if remaining == 0 {
			break
		}
		if usage[idx].exactBytes%accountingUnitBytes != 0 {
			usage[idx].usage++
			remaining--
		}
	}
	return nil
}

func accountingCatalog(ctx context.Context) ([]string, error) {
	enumerator, ok := registryAccounting.namespace.(ocid.RepositoryEnumerator)
	if !ok {
		return nil, errors.New("registry does not support repository enumeration")
	}
	var result []string
	err := enumerator.Enumerate(ctx, func(repository string) error {
		result = append(result, repository)
		return nil
	})
	sort.Strings(result)
	return result, err
}

func accountingCheckTagMutation(ctx context.Context, owner apm.WalletOwner, repository, tag string, descriptor v1.Descriptor) error {
	if !repositoryIsReady(owner) {
		return errors.New("registry accounting is temporarily unavailable")
	}
	prospective, err := accountingScanOwner(ctx, owner, &accountingTagOverride{
		repository: repository,
		tag:        tag,
		descriptor: descriptor,
	})
	if err != nil {
		return fmt.Errorf("unable to verify storage quota: %w", err)
	}
	if len(prospective) == 0 {
		return errors.New("unable to identify repository owner")
	}

	category := prospective[0].repository.Specification.Product.Category
	response, rpcErr := apm.CheckProviderUsable.Invoke(fnd.BulkRequest[apm.CheckProviderUsableRequest]{Items: []apm.CheckProviderUsableRequest{{
		Owner: owner,
		Category: apm.ProductCategoryIdV2{
			Name:     category,
			Provider: config.Provider.Id,
		},
	}}})
	if rpcErr != nil || len(response.Responses) != 1 {
		return errors.New("unable to verify storage quota")
	}

	reportedUsage := accountingReportedUsage(owner, category)
	maxUsable := response.Responses[0].MaxUsable
	if reportedUsage < 0 || maxUsable < 0 || reportedUsage > int64(^uint64(0)>>1)-maxUsable {
		return errors.New("invalid storage quota")
	}
	allowedUnits := reportedUsage + maxUsable
	prospectiveBytes := int64(0)
	for _, item := range prospective {
		if prospectiveBytes > int64(^uint64(0)>>1)-item.exactBytes {
			return errors.New("registry usage exceeds int64")
		}
		prospectiveBytes += item.exactBytes
	}
	if allowedUnits < 0 || allowedUnits > int64(^uint64(0)>>1)/accountingUnitBytes || prospectiveBytes > allowedUnits*accountingUnitBytes {
		return errors.New("storage quota exceeded")
	}
	return nil
}

func accountingReport(usage []repositoryAccountingUsage) error {
	reports := make([]apm.ReportUsageRequest, 0, len(usage))
	for _, item := range usage {
		reports = append(reports, apm.ReportUsageRequest{
			IsDeltaCharge: false,
			Owner:         walletOwner(item.repository),
			CategoryIdV2: apm.ProductCategoryIdV2{
				Name:     item.repository.Specification.Product.Category,
				Provider: config.Provider.Id,
			},
			Usage: item.usage,
			Description: apm.ChargeDescription{
				Scope: util.OptValue("repository-" + item.repository.Id),
			},
		})
	}
	for _, chunk := range util.ChunkBy(reports, 500) {
		if _, err := apm.ReportUsage.Invoke(fnd.BulkRequest[apm.ReportUsageRequest]{Items: chunk}); err != nil {
			return err.AsError()
		}
	}
	return nil
}

func accountingPersistCalculated(usage []repositoryAccountingUsage) {
	db.NewTx0(func(tx *db.Transaction) {
		for _, item := range usage {
			owner := walletOwner(item.repository)
			db.Exec(tx, `
				insert into container_repository_accounting(
					repository_id, repository_name, owner_type, username, project_id, category, exact_bytes, reported_usage
				) values (
					:repository_id, :repository_name, :owner_type, :username, :project_id, :category, :exact_bytes, 0
				) on conflict (repository_id) do update set
					repository_name = excluded.repository_name,
					owner_type = excluded.owner_type,
					username = excluded.username,
					project_id = excluded.project_id,
					category = excluded.category,
					exact_bytes = excluded.exact_bytes
			`, db.Params{
				"repository_id": item.repository.Id, "repository_name": item.repository.Specification.Name,
				"owner_type": string(owner.Type), "username": owner.Username, "project_id": owner.ProjectId,
				"category": item.repository.Specification.Product.Category, "exact_bytes": item.exactBytes,
			})
		}
	})
}

func accountingPersistReported(usage []repositoryAccountingUsage) {
	db.NewTx0(func(tx *db.Transaction) {
		for _, item := range usage {
			db.Exec(tx, `
				update container_repository_accounting
				set reported_usage = :reported_usage
				where repository_id = :repository_id
			`, db.Params{"repository_id": item.repository.Id, "reported_usage": item.usage})
		}
	})
}

func accountingReportedUsage(owner apm.WalletOwner, category string) int64 {
	return db.NewTx(func(tx *db.Transaction) int64 {
		rows := db.Select[struct{ Usage int64 }](tx, `
			select coalesce(sum(reported_usage), 0) as usage
			from container_repository_accounting
			where owner_type = :owner_type and username = :username and project_id = :project_id and category = :category
		`, db.Params{"owner_type": string(owner.Type), "username": owner.Username, "project_id": owner.ProjectId, "category": category})
		if len(rows) == 0 {
			return 0
		}
		return rows[0].Usage
	})
}

func accountingClearStaleScopes(active map[string]bool) {
	rows := db.NewTx(func(tx *db.Transaction) []repositoryAccountingRow {
		return db.Select[repositoryAccountingRow](tx, `select * from container_repository_accounting`, db.Params{})
	})
	for _, row := range rows {
		if active[row.RepositoryId] {
			continue
		}
		owner := apm.WalletOwner{Type: apm.WalletOwnerType(row.OwnerType), Username: row.Username, ProjectId: row.ProjectId}
		request := apm.ReportUsageRequest{
			IsDeltaCharge: false,
			Owner:         owner,
			CategoryIdV2:  apm.ProductCategoryIdV2{Name: row.Category, Provider: config.Provider.Id},
			Usage:         0,
			Description:   apm.ChargeDescription{Scope: util.OptValue("repository-" + row.RepositoryId)},
		}
		if _, err := apm.ReportUsage.Invoke(fnd.BulkRequest[apm.ReportUsageRequest]{Items: []apm.ReportUsageRequest{request}}); err == nil {
			db.NewTx0(func(tx *db.Transaction) {
				db.Exec(tx, `delete from container_repository_accounting where repository_id = :id`, db.Params{"id": row.RepositoryId})
			})
		}
	}
}
