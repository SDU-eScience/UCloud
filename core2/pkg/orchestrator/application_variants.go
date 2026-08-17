package orchestrator

import (
	"cmp"
	"encoding/json"
	"fmt"
	"net/http"
	"slices"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/anyascii/go"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Application variants
// =====================================================================================================================
// A variant combines the invocation from one catalog application with a different container image. This allows
// end-users to create custom applications that are tied to an existing UCloud managed application.
//
// Variants share application groups with their base applications. Access is different from normal catalog access:
// each request can see variants from its current workspace only. For a personal workspace, created_by identifies the
// owner because project_id is null.
//
// ---------------------------------------------------------------------------------------------------------------------
// MUTEX LOCK ORDER: applicationVariantReservationMu -> bucket -> variant
// ---------------------------------------------------------------------------------------------------------------------

type internalApplicationVariant struct {
	Mu            sync.RWMutex
	Value         orcapi.ApplicationVariant
	BaseGroup     AppGroupId
	RevisionCount int64
	ImageName     string
}

var applicationVariantReservationMu sync.Mutex

func applicationVariantRetrieve(id int64) (*internalApplicationVariant, bool) {
	b := appBucket(fmt.Sprintf("variant-%d", id))
	b.Mu.RLock()
	variant, ok := b.ApplicationVariants[id]
	b.Mu.RUnlock()
	return variant, ok
}

func applicationVariantSnapshot(internal *internalApplicationVariant) (orcapi.ApplicationVariant, AppGroupId) {
	internal.Mu.RLock()
	variant := internal.Value
	group := internal.BaseGroup
	internal.Mu.RUnlock()
	return variant, group
}

func applicationVariantCanRead(actor rpc.Actor, variant orcapi.ApplicationVariant) bool {
	if variant.State == orcapi.ApplicationVariantStateDeleted {
		return false
	}
	if actor.Username == rpc.ActorSystem.Username {
		return true
	}
	if !applicationVariantInWorkspace(actor, variant) {
		return false
	}
	return actor.Role == rpc.RoleAdmin || variant.PublishedToProject || variant.CreatedBy == actor.Username ||
		actor.Membership[actor.Project.Value].Satisfies(rpc.ProjectRoleAdmin)
}

func applicationVariantCanManage(actor rpc.Actor, variant orcapi.ApplicationVariant) bool {
	if actor.Username == rpc.ActorSystem.Username {
		return true
	}
	if !applicationVariantInWorkspace(actor, variant) {
		return false
	}
	return actor.Role == rpc.RoleAdmin || variant.CreatedBy == actor.Username ||
		actor.Membership[actor.Project.Value].Satisfies(rpc.ProjectRoleAdmin)
}

func applicationVariantInWorkspace(actor rpc.Actor, variant orcapi.ApplicationVariant) bool {
	if actor.Username == rpc.ActorSystem.Username {
		return true
	}
	if actor.Project.Present {
		return variant.Project.Present && variant.Project.Value == string(actor.Project.Value)
	}
	return !variant.Project.Present && variant.CreatedBy == actor.Username
}

func applicationVariantWorkspaceKey(variant orcapi.ApplicationVariant) string {
	return variant.Project.GetOrDefault(variant.CreatedBy)
}

func applicationVariantBase(actor rpc.Actor, requested orcapi.NameAndVersion) (orcapi.Application, orcapi.NameAndVersion, AppGroupId, *util.HttpError) {
	base, ok := AppRetrieve(actor, requested.Name, requested.Version, AppDiscoveryAll, AppCatalogIncludeGroups)
	if !ok || !base.Invocation.Tool.Tool.Present {
		return orcapi.Application{}, orcapi.NameAndVersion{}, 0, util.HttpErr(http.StatusBadRequest, "unknown base application")
	}
	if base.Invocation.Tool.Tool.Value.Description.Backend != orcapi.ToolBackendDocker {
		return orcapi.Application{}, orcapi.NameAndVersion{}, 0, util.HttpErr(http.StatusBadRequest, "only container applications can have variants")
	}
	managedBase := base.Metadata.NameAndVersion
	if base.Metadata.Variant.Present {
		managedBase = base.Metadata.Variant.Value.BaseApplication
	}
	group := AppGroupId(base.Metadata.Group.Metadata.Id)
	if group < 0 {
		return orcapi.Application{}, orcapi.NameAndVersion{}, 0, util.HttpErr(http.StatusBadRequest, "the base application must belong to a group")
	}
	return base, managedBase, group, nil
}

func applicationVariantTitleAvailable(workspace string, group AppGroupId, title string, except int64) bool {
	for i := range appCatalogGlobals.Buckets {
		b := &appCatalogGlobals.Buckets[i]
		b.Mu.RLock()
		variants := make([]*internalApplicationVariant, 0, len(b.ApplicationVariants))
		for _, variant := range b.ApplicationVariants {
			variants = append(variants, variant)
		}
		b.Mu.RUnlock()
		for _, internal := range variants {
			variant, baseGroup := applicationVariantSnapshot(internal)
			if variant.Id != except && applicationVariantWorkspaceKey(variant) == workspace && baseGroup == group &&
				variant.State != orcapi.ApplicationVariantStateDeleted && strings.EqualFold(variant.Title, title) {
				return false
			}
		}
	}
	return true
}

func applicationVariantImageName(workspace, applicationTitle, variantTitle string) string {
	base := applicationVariantLowerKebab(applicationTitle + " " + variantTitle)
	used := map[string]bool{}
	for i := range appCatalogGlobals.Buckets {
		bucket := &appCatalogGlobals.Buckets[i]
		bucket.Mu.RLock()
		variants := make([]*internalApplicationVariant, 0, len(bucket.ApplicationVariants))
		for _, variant := range bucket.ApplicationVariants {
			variants = append(variants, variant)
		}
		bucket.Mu.RUnlock()

		for _, internal := range variants {
			internal.Mu.RLock()
			variant := internal.Value
			imageName := internal.ImageName
			internal.Mu.RUnlock()
			if variant.State == orcapi.ApplicationVariantStateDeleted || applicationVariantWorkspaceKey(variant) != workspace {
				continue
			}
			used[imageName] = true
		}
	}
	for suffix := 0; ; suffix++ {
		candidate := applicationVariantNameWithSuffix(base, suffix)
		if !used[candidate] {
			return candidate
		}
	}
}

func applicationVariantLowerKebab(value string) string {
	transliterated := strings.ToLower(anyascii.Transliterate(value))
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
		name = "application-variant"
	}
	if len(name) > 32 {
		name = strings.TrimRight(name[:32], "-")
	}
	return name
}

func applicationVariantNameWithSuffix(base string, suffix int) string {
	if suffix == 0 {
		return base
	}
	ending := fmt.Sprintf("-%d", suffix)
	maxBaseLength := 32 - len(ending)
	return strings.TrimRight(base[:min(len(base), maxBaseLength)], "-") + ending
}

func applicationVariantImageBase(image string) string {
	lastSlash := strings.LastIndex(image, "/")
	name := image[lastSlash+1:]
	if separator := strings.IndexAny(name, ":@"); separator >= 0 {
		name = name[:separator]
	}
	return name
}

func applicationVariantReserve(actor rpc.Actor, requested orcapi.NameAndVersion, provider, title string, published bool) (*internalApplicationVariant, string, *util.HttpError) {
	if published && !actor.Project.Present {
		return nil, "", util.HttpErr(http.StatusBadRequest, "a personal variant cannot be published to a project")
	}
	title = strings.TrimSpace(title)
	if err := util.ValidateStringE(&title, "title", 0); err != nil {
		return nil, "", err
	}
	base, managedBase, group, err := applicationVariantBase(actor, requested)
	if err != nil {
		return nil, "", err
	}
	project := util.OptMap(actor.Project, func(value rpc.ProjectId) string { return string(value) })
	workspace := project.GetOrDefault(actor.Username)
	applicationVariantReservationMu.Lock()
	defer applicationVariantReservationMu.Unlock()
	if !applicationVariantTitleAvailable(workspace, group, title, 0) {
		return nil, "", util.HttpErr(http.StatusConflict, "a flavor with this title already exists")
	}
	imageName := applicationVariantImageName(workspace, base.Metadata.Title, title)
	type createdVariant struct {
		Id        int64
		CreatedAt time.Time
	}
	created, found := db.NewTx2(func(tx *db.Transaction) (createdVariant, bool) {
		return db.Get[createdVariant](
			tx,
			`
				insert into app_store.application_variants(
					base_name, base_version, base_group, created_by, project_id, provider, title, published_to_project, state
				) values (
					:base_name, :base_version, :base_group, :created_by, :project, :provider, :title, :published, 'PENDING'
				) returning id, created_at
			`,
			db.Params{
				"base_name":    managedBase.Name,
				"base_version": managedBase.Version,
				"base_group":   group,
				"created_by":   actor.Username,
				"project":      project.Sql(),
				"provider":     provider,
				"title":        title,
				"published":    published,
			},
		)
	})
	if !found {
		return nil, "", util.HttpErr(http.StatusInternalServerError, "failed to reserve application variant")
	}
	internal := &internalApplicationVariant{
		Value: orcapi.ApplicationVariant{
			Id: created.Id, BaseApplication: managedBase, CreatedBy: actor.Username, Project: project, Provider: provider,
			Title: title, PublishedToProject: published, State: orcapi.ApplicationVariantStatePending,
			CreatedAt: fndapi.Timestamp(created.CreatedAt),
		},
		BaseGroup: group,
		ImageName: imageName,
	}
	generatedName := fmt.Sprintf("variant-%d", created.Id)
	b := appBucket(generatedName)
	b.Mu.RLock()
	_, exists := b.Applications[generatedName]
	_, duplicate := b.ApplicationVariants[created.Id]
	b.Mu.RUnlock()
	if duplicate {
		return nil, "", util.HttpErr(http.StatusInternalServerError, "application variant cache already contains the reserved ID")
	}
	if exists {
		failure := "generated application name is already in use"
		applicationVariantPersistFailure(created.Id, failure)
		internal.Value.State = orcapi.ApplicationVariantStateFailed
		internal.Value.Failure.Set(failure)
	}
	b.Mu.Lock()
	if _, duplicate = b.ApplicationVariants[created.Id]; !duplicate {
		b.ApplicationVariants[created.Id] = internal
	}
	b.Mu.Unlock()
	if duplicate {
		return nil, "", util.HttpErr(http.StatusInternalServerError, "application variant cache already contains the reserved ID")
	}
	if exists {
		return nil, "", util.HttpErr(http.StatusConflict, "generated application name is already in use; retry the request")
	}
	return internal, imageName, nil
}

func applicationVariantPersistFailure(id int64, failure string) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update app_store.application_variants
				set state = 'FAILED', failure = :failure, modified_at = now()
				where
					id = :id
					and not exists (
						select 1
						from
							app_store.application_variant_revisions r
						where r.variant_id = :id
					)
			`,
			db.Params{
				"id":      id,
				"failure": failure,
			},
		)
	})
}

func applicationVariantSetFailure(id int64, failure string) {
	if internal, ok := applicationVariantRetrieve(id); ok {
		internal.Mu.Lock()
		if internal.Value.RevisionId == 0 {
			applicationVariantPersistFailure(id, failure)
			internal.Value.State = orcapi.ApplicationVariantStateFailed
			internal.Value.Failure.Set(failure)
		}
		internal.Mu.Unlock()
	}
}

func applicationVariantBeginPush(internal *internalApplicationVariant) (string, int64, bool) {
	internal.Mu.Lock()
	defer internal.Mu.Unlock()
	if internal.Value.State != orcapi.ApplicationVariantStateActive {
		return "", 0, false
	}
	_, updated := db.NewTx2(func(tx *db.Transaction) (struct{ Id int64 }, bool) {
		return db.Get[struct{ Id int64 }](
			tx,
			`
				update app_store.application_variants
				set failure = 'PUSH_PENDING', modified_at = now()
				where
					id = :id
					and state = 'ACTIVE'
					and failure is null
				returning id
			`,
			db.Params{
				"id": internal.Value.Id,
			},
		)
	})
	if !updated {
		return "", 0, false
	}
	return internal.ImageName, internal.RevisionCount + 1, true
}

func applicationVariantCancelPush(internal *internalApplicationVariant) {
	internal.Mu.Lock()
	defer internal.Mu.Unlock()
	if internal.Value.State != orcapi.ApplicationVariantStateActive || internal.Value.RevisionId == 0 {
		return
	}
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update app_store.application_variants
				set failure = null, modified_at = now()
				where
					id = :id
					and state = 'ACTIVE'
					and failure = 'PUSH_PENDING'
			`,
			db.Params{
				"id": internal.Value.Id,
			},
		)
	})
}

func applicationVariantMaterialize(internal *internalApplicationVariant, image, imageDigest, actor string, baseOverride util.Option[orcapi.NameAndVersion]) (orcapi.ApplicationVariant, *util.HttpError) {
	variant, _ := applicationVariantSnapshot(internal)
	if baseOverride.Present {
		variant.BaseApplication = baseOverride.Value
	}
	base, ok := AppRetrieve(rpc.ActorSystem, variant.BaseApplication.Name, variant.BaseApplication.Version, AppDiscoveryAll, AppCatalogIncludeGroups)
	if !ok || !base.Invocation.Tool.Tool.Present {
		return orcapi.ApplicationVariant{}, util.HttpErr(http.StatusBadRequest, "base application is no longer available")
	}
	internal.Mu.Lock()
	if internal.Value.State == orcapi.ApplicationVariantStateDeleted {
		internal.Mu.Unlock()
		return orcapi.ApplicationVariant{}, util.HttpErr(http.StatusConflict, "flavor was deleted")
	}

	result := internal.Value
	result.BaseApplication = variant.BaseApplication
	var materialized orcapi.Application
	db.NewTx0(func(tx *db.Transaction) {
		revision, _ := db.Get[struct {
			Id        int64
			CreatedAt time.Time
		}](
			tx,
			`
				insert into app_store.application_variant_revisions(
					variant_id, created_by, image, image_digest
				) values (
					:variant, :created_by, :image, :digest
				)
				returning id, created_at
			`,
			db.Params{
				"variant":    variant.Id,
				"created_by": actor,
				"image":      image,
				"digest":     imageDigest,
			},
		)

		result.RevisionId = revision.Id
		result.Image = image
		result.ImageDigest = imageDigest
		result.State = orcapi.ApplicationVariantStateActive
		result.Failure.Clear()
		materialized = applicationVariantBuildApplication(base, result, internal.RevisionCount+1, revision.CreatedAt)

		db.Exec(
			tx,
			`
				update app_store.application_variants
				set
					base_name = :base_name,
					base_version = :base_version,
					state = 'ACTIVE',
					failure = null,
					modified_at = now()
				where id = :id
			`,
			db.Params{
				"id":           variant.Id,
				"base_name":    result.BaseApplication.Name,
				"base_version": result.BaseApplication.Version,
			},
		)
	})

	internal.Value = result
	internal.RevisionCount++
	internal.ImageName = applicationVariantImageBase(image)
	internal.Mu.Unlock()
	applicationVariantCacheAdd(materialized, true)
	return result, nil
}

func applicationVariantBuildApplication(base orcapi.Application, variant orcapi.ApplicationVariant, revision int64, createdAt time.Time) orcapi.Application {
	var result orcapi.Application
	encoded, _ := json.Marshal(base)
	_ = json.Unmarshal(encoded, &result)

	name := fmt.Sprintf("variant-%d", variant.Id)
	version := fmt.Sprintf("r%d", revision)
	result.Metadata.Name = name
	result.Metadata.Version = version
	result.Metadata.CreatedAt = fndapi.Timestamp(createdAt)
	result.Metadata.FlavorName.Set(variant.Title)
	result.Metadata.Public = false
	result.Metadata.Variant.Set(variant)
	result.Metadata.Authors = []string{variant.CreatedBy}
	result.Favorite.Clear()
	result.Versions = nil
	tool := &result.Invocation.Tool.Tool.Value.Description
	tool.Info = orcapi.NameAndVersion{Name: name, Version: version}
	tool.Image = variant.ImageDigest
	tool.SupportedProviders = []string{variant.Provider}
	result.Invocation.Tool.NameAndVersion = tool.Info
	return result
}

func applicationVariantCacheAdd(app orcapi.Application, addToGroup bool) {
	if !app.Metadata.Variant.Present || app.Metadata.Variant.Value.State == orcapi.ApplicationVariantStateDeleted ||
		!app.Invocation.Tool.Tool.Present {
		return
	}
	tool := app.Invocation.Tool.Tool.Value.Description
	internal := &internalApplication{
		Name: app.Metadata.Name, Version: app.Metadata.Version, CreatedAt: app.Metadata.CreatedAt.Time(),
		Invocation: app.Invocation, Tool: app.Invocation.Tool.NameAndVersion, Title: app.Metadata.Title,
		Description: app.Metadata.Description, DocumentationSite: util.OptStringIfNotEmpty(app.Metadata.Website),
		FlavorName: app.Metadata.FlavorName, Group: util.OptValue(AppGroupId(app.Metadata.Group.Metadata.Id)),
		ModifiedAt: app.Metadata.CreatedAt.Time(), Variant: app.Metadata.Variant,
	}
	b := appBucket(internal.Name)
	b.Mu.Lock()
	b.Applications[internal.Name] = append(b.Applications[internal.Name], internal)
	b.Tools[tool.Info.Name] = append(b.Tools[tool.Info.Name], &internalTool{Name: tool.Info.Name, Version: tool.Info.Version, Tool: tool})
	b.Mu.Unlock()
	if group, ok := appRetrieveGroup(internal.Group.Value); ok && addToGroup {
		group.Mu.Lock()
		group.Items = util.AppendUnique(group.Items, internal.Name)
		group.Mu.Unlock()
	}
}

func applicationVariantLoadCurrent(rows []applicationVariantLoadRow) {
	for _, row := range rows {
		variant := orcapi.ApplicationVariant{
			Id:                 row.Id,
			BaseApplication:    orcapi.NameAndVersion{Name: row.BaseName, Version: row.BaseVersion},
			CreatedBy:          row.CreatedBy,
			Project:            util.SqlNullStringToOpt(row.ProjectId),
			Provider:           row.Provider,
			Title:              row.Title,
			PublishedToProject: row.PublishedToProject,
			State:              orcapi.ApplicationVariantState(row.State),
			CreatedAt:          fndapi.Timestamp(row.CreatedAt),
		}
		if row.RevisionId.Valid {
			variant.RevisionId = row.RevisionId.Int64
		}
		if row.Image.Valid {
			variant.Image = row.Image.String
		}
		if row.ImageDigest.Valid {
			variant.ImageDigest = row.ImageDigest.String
		}
		variant.Failure = util.SqlNullStringToOpt(row.Failure)

		internal := &internalApplicationVariant{
			Value:         variant,
			BaseGroup:     AppGroupId(row.BaseGroup),
			RevisionCount: row.RevisionCount,
		}
		if row.Image.Valid {
			internal.ImageName = applicationVariantImageBase(row.Image.String)
		} else if base, ok := appRetrieve(row.BaseName, row.BaseVersion); ok {
			base.Mu.RLock()
			internal.ImageName = applicationVariantLowerKebab(base.Title + " " + row.Title)
			base.Mu.RUnlock()
		}
		b := appBucket(fmt.Sprintf("variant-%d", row.Id))
		b.ApplicationVariants[row.Id] = internal
	}
}

func applicationVariantLoadRevisions(rows []applicationVariantRevisionLoadRow) {
	revisionNumbers := map[int64]int64{}
	for _, row := range rows {
		internal, ok := applicationVariantRetrieve(row.VariantId)
		if !ok {
			continue
		}
		variant, _ := applicationVariantSnapshot(internal)
		base, ok := AppRetrieve(
			rpc.ActorSystem,
			variant.BaseApplication.Name,
			variant.BaseApplication.Version,
			AppDiscoveryAll,
			AppCatalogIncludeGroups,
		)
		if !ok || !base.Invocation.Tool.Tool.Present {
			continue
		}
		revisionNumbers[row.VariantId]++
		revisionVariant := variant
		revisionVariant.RevisionId = row.Id
		revisionVariant.Image = row.Image
		revisionVariant.ImageDigest = row.ImageDigest
		app := applicationVariantBuildApplication(base, revisionVariant, revisionNumbers[row.VariantId], row.CreatedAt)
		applicationVariantCacheAdd(app, variant.State == orcapi.ApplicationVariantStateActive && variant.RevisionId == row.Id)
	}
}

func applicationVariantValidateImage(actor rpc.Actor, provider, image string, requireProjectAccess bool) (orcapi.ApplicationVariantValidateImageResponse, *util.HttpError) {
	owner := orcapi.ResourceOwner{CreatedBy: actor.Username}
	if actor.Project.Present {
		owner.Project.Set(string(actor.Project.Value))
	}
	return InvokeProvider(provider, orcapi.ApplicationVariantsProviderValidateImage, orcapi.ApplicationVariantValidateImageRequest{
		Owner: owner, Image: image, RequireProjectAccess: requireProjectAccess,
	}, ProviderCallOpts{
		Username: util.OptValue(actor.Username), Reason: util.OptValue("validate application variant image"),
	})
}

func initApplicationVariantRpc() {
	orcapi.JobsCreateApplicationVariant.Handler(func(info rpc.RequestInfo, request orcapi.JobsCreateApplicationVariantRequest) (fndapi.Task, *util.HttpError) {
		job, _, _, err := ResourceRetrieveEx[orcapi.Job](
			info.Actor, jobType, ResourceParseId(request.JobId), orcapi.PermissionEdit, orcapi.ResourceFlagsIncludeAll(),
		)
		if err != nil {
			return fndapi.Task{}, util.HttpErr(http.StatusNotFound, "permission denied or job not found")
		}
		if job.Status.State != orcapi.JobStateRunning {
			return fndapi.Task{}, util.HttpErr(http.StatusBadRequest, "the job must be running")
		}
		if request.Rank < 0 || request.Rank >= job.Specification.Replicas {
			return fndapi.Task{}, util.HttpErr(http.StatusBadRequest, "invalid replica rank")
		}
		if !job.Status.ResolvedApplication.Present || !job.Status.ResolvedApplication.Value.Invocation.Tool.Tool.Present ||
			job.Status.ResolvedApplication.Value.Invocation.Tool.Tool.Value.Description.Backend != orcapi.ToolBackendDocker {
			return fndapi.Task{}, util.HttpErr(http.StatusBadRequest, "only running container jobs can create variants")
		}
		provider := job.Specification.Product.Provider
		support, ok := SupportByProduct[orcapi.JobSupport](jobType, job.Specification.Product)
		if !ok || !support.Support.Docker.ApplicationVariants || !support.Support.Docker.ContainerSnapshots {
			return fndapi.Task{}, util.HttpErr(http.StatusBadRequest, "the provider does not support container snapshots")
		}
		var variant *internalApplicationVariant
		var imageName string
		var revision int64
		_, managedBase, _, baseErr := applicationVariantBase(info.Actor, job.Specification.Application)
		if baseErr != nil {
			return fndapi.Task{}, baseErr
		}
		if request.TargetVariantId.Present {
			var found bool
			variant, found = applicationVariantRetrieve(request.TargetVariantId.Value)
			if !found {
				return fndapi.Task{}, util.HttpErr(http.StatusNotFound, "flavor not found")
			}
			current, targetGroup := applicationVariantSnapshot(variant)
			_, _, jobGroup, _ := applicationVariantBase(info.Actor, job.Specification.Application)
			if !applicationVariantCanManage(info.Actor, current) || current.State != orcapi.ApplicationVariantStateActive {
				return fndapi.Task{}, util.HttpErr(http.StatusNotFound, "flavor not found")
			}
			if targetGroup != jobGroup || current.Provider != provider {
				return fndapi.Task{}, util.HttpErr(http.StatusBadRequest, "the flavor is not compatible with this job")
			}
			var pushStarted bool
			imageName, revision, pushStarted = applicationVariantBeginPush(variant)
			if !pushStarted {
				return fndapi.Task{}, util.HttpErr(http.StatusConflict, "a new flavor version is already being saved")
			}
		} else {
			var reserveErr *util.HttpError
			variant, imageName, reserveErr = applicationVariantReserve(info.Actor, job.Specification.Application, provider, request.Title, request.PublishedToProject)
			if reserveErr != nil {
				return fndapi.Task{}, reserveErr
			}
			revision = 1
		}
		current, _ := applicationVariantSnapshot(variant)
		task, err := InvokeProvider(provider, orcapi.JobsProviderCreateApplicationVariant, orcapi.JobsProviderCreateApplicationVariantRequest{
			Job: job, VariantId: current.Id, Revision: revision, BaseApplication: managedBase, Image: fmt.Sprintf("%s:r%d", imageName, revision), Rank: request.Rank, RequestedBy: info.Actor.Username,
		}, ProviderCallOpts{Username: util.OptValue(info.Actor.Username), Reason: util.OptValue("create application variant")})
		if err != nil {
			if request.TargetVariantId.Present {
				applicationVariantCancelPush(variant)
			}
			applicationVariantSetFailure(current.Id, err.Why)
			return fndapi.Task{}, err
		}
		return task, nil
	})

	orcapi.ApplicationVariantsCreate.Handler(func(info rpc.RequestInfo, request orcapi.ApplicationVariantCreateRequest) (orcapi.ApplicationVariant, *util.HttpError) {
		base, _, _, err := applicationVariantBase(info.Actor, request.BaseApplication)
		if err != nil {
			return orcapi.ApplicationVariant{}, err
		}
		_ = base
		validated, err := applicationVariantValidateImage(info.Actor, request.Provider, request.Image, request.PublishedToProject)
		if err != nil {
			return orcapi.ApplicationVariant{}, err
		}
		variant, _, err := applicationVariantReserve(info.Actor, request.BaseApplication, request.Provider, request.Title, request.PublishedToProject)
		if err != nil {
			return orcapi.ApplicationVariant{}, err
		}
		return applicationVariantMaterialize(variant, validated.Image, validated.ImageDigest, info.Actor.Username, util.OptNone[orcapi.NameAndVersion]())
	})

	orcapi.ApplicationVariantsRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.FindApplicationVariant) (orcapi.ApplicationVariant, *util.HttpError) {
		internal, ok := applicationVariantRetrieve(request.Id)
		if !ok {
			return orcapi.ApplicationVariant{}, util.HttpErr(http.StatusNotFound, "flavor not found")
		}
		variant, _ := applicationVariantSnapshot(internal)
		if !applicationVariantCanRead(info.Actor, variant) {
			return orcapi.ApplicationVariant{}, util.HttpErr(http.StatusNotFound, "flavor not found")
		}
		return variant, nil
	})

	orcapi.ApplicationVariantsBrowse.Handler(func(info rpc.RequestInfo, request orcapi.ApplicationVariantBrowseRequest) (fndapi.PageV2[orcapi.ApplicationVariant], *util.HttpError) {
		limit := min(max(request.ItemsPerPage, 1), 250)
		offset, _ := strconv.Atoi(request.Next.GetOrDefault("0"))
		offset = max(offset, 0)
		variants := make([]orcapi.ApplicationVariant, 0)
		for i := range appCatalogGlobals.Buckets {
			b := &appCatalogGlobals.Buckets[i]
			b.Mu.RLock()
			internals := make([]*internalApplicationVariant, 0, len(b.ApplicationVariants))
			for _, internal := range b.ApplicationVariants {
				internals = append(internals, internal)
			}
			b.Mu.RUnlock()
			for _, internal := range internals {
				variant, _ := applicationVariantSnapshot(internal)
				if applicationVariantInWorkspace(info.Actor, variant) && variant.State != orcapi.ApplicationVariantStateDeleted {
					variants = append(variants, variant)
				}
			}
		}
		slices.SortFunc(variants, func(a, b orcapi.ApplicationVariant) int { return cmp.Compare(b.Id, a.Id) })
		end := min(offset+limit, len(variants))
		result := fndapi.PageV2[orcapi.ApplicationVariant]{ItemsPerPage: limit}
		if offset < len(variants) {
			for _, variant := range variants[offset:end] {
				internal, ok := applicationVariantRetrieve(variant.Id)
				if !ok {
					continue
				}
				internal.Mu.RLock()
				current := internal.Value
				if current.State != orcapi.ApplicationVariantStateDeleted && applicationVariantCanRead(info.Actor, current) {
					result.Items = append(result.Items, current)
				}
				internal.Mu.RUnlock()
			}
		}
		if end < len(variants) {
			result.Next.Set(strconv.Itoa(end))
		}
		return result, nil
	})

	orcapi.ApplicationVariantsUpdate.Handler(func(info rpc.RequestInfo, request orcapi.ApplicationVariantUpdateRequest) (orcapi.ApplicationVariant, *util.HttpError) {
		internal, ok := applicationVariantRetrieve(request.Id)
		if !ok {
			return orcapi.ApplicationVariant{}, util.HttpErr(http.StatusNotFound, "flavor not found")
		}
		variant, baseGroup := applicationVariantSnapshot(internal)
		if !applicationVariantCanManage(info.Actor, variant) || variant.State == orcapi.ApplicationVariantStateDeleted {
			return orcapi.ApplicationVariant{}, util.HttpErr(http.StatusNotFound, "flavor not found")
		}
		if request.Title.Present {
			request.Title.Value = strings.TrimSpace(request.Title.Value)
			if err := util.ValidateStringE(&request.Title.Value, "title", 0); err != nil {
				return orcapi.ApplicationVariant{}, err
			}
			if !applicationVariantTitleAvailable(applicationVariantWorkspaceKey(variant), baseGroup, request.Title.Value, variant.Id) {
				return orcapi.ApplicationVariant{}, util.HttpErr(http.StatusConflict, "a flavor with this title already exists")
			}
			variant.Title = request.Title.Value
		}
		if request.PublishedToProject.Present {
			variant.PublishedToProject = request.PublishedToProject.Value
		}
		validatedImage := util.OptNone[orcapi.ApplicationVariantValidateImageResponse]()
		if request.Image.Present {
			validated, err := applicationVariantValidateImage(info.Actor, variant.Provider, request.Image.Value, variant.PublishedToProject)
			if err != nil {
				return orcapi.ApplicationVariant{}, err
			}
			validatedImage.Set(validated)
		} else if request.PublishedToProject.Present && request.PublishedToProject.Value {
			if _, err := applicationVariantValidateImage(info.Actor, variant.Provider, variant.ImageDigest, true); err != nil {
				return orcapi.ApplicationVariant{}, util.HttpErr(http.StatusBadRequest, "the image is not available to all project members")
			}
		}
		if request.Title.Present {
			applicationVariantReservationMu.Lock()
			if !applicationVariantTitleAvailable(applicationVariantWorkspaceKey(variant), baseGroup, variant.Title, variant.Id) {
				applicationVariantReservationMu.Unlock()
				return orcapi.ApplicationVariant{}, util.HttpErr(http.StatusConflict, "a flavor with this title already exists")
			}
		}
		internal.Mu.Lock()
		if internal.Value.State == orcapi.ApplicationVariantStateDeleted {
			internal.Mu.Unlock()
			if request.Title.Present {
				applicationVariantReservationMu.Unlock()
			}
			return orcapi.ApplicationVariant{}, util.HttpErr(http.StatusNotFound, "flavor not found")
		}
		_, updated := db.NewTx2(func(tx *db.Transaction) (struct{ Id int64 }, bool) {
			return db.Get[struct{ Id int64 }](
				tx,
				`
					update app_store.application_variants as v
					set
						title = :title,
						published_to_project = :published,
						modified_at = now()
					where
						v.id = :id
						and v.state <> 'DELETED'
						and (
							:title_changed = false
							or not exists (
								select 1
								from
									app_store.application_variants other
								where
									other.id <> v.id
									and other.state <> 'DELETED'
									and coalesce(other.project_id, other.created_by) = coalesce(v.project_id, v.created_by)
									and other.base_group = v.base_group
									and lower(other.title) = lower(:title)
							)
						)
					returning v.id
				`,
				db.Params{
					"id":            variant.Id,
					"title":         variant.Title,
					"published":     variant.PublishedToProject,
					"title_changed": request.Title.Present,
				},
			)
		})
		if !updated {
			internal.Mu.Unlock()
			if request.Title.Present {
				applicationVariantReservationMu.Unlock()
				return orcapi.ApplicationVariant{}, util.HttpErr(http.StatusConflict, "a flavor with this title already exists")
			}
			return orcapi.ApplicationVariant{}, util.HttpErr(http.StatusNotFound, "flavor not found")
		}
		if request.Title.Present {
			internal.Value.Title = variant.Title
		}
		if request.PublishedToProject.Present {
			internal.Value.PublishedToProject = variant.PublishedToProject
		}
		variant = internal.Value
		internal.Mu.Unlock()
		if request.Title.Present {
			applicationVariantReservationMu.Unlock()
		}
		applicationVariantCacheUpdateMetadata(variant)
		if validatedImage.Present {
			return applicationVariantMaterialize(internal, validatedImage.Value.Image, validatedImage.Value.ImageDigest, info.Actor.Username, util.OptNone[orcapi.NameAndVersion]())
		}
		return variant, nil
	})

	orcapi.ApplicationVariantsDelete.Handler(func(info rpc.RequestInfo, request orcapi.FindApplicationVariant) (util.Empty, *util.HttpError) {
		internal, ok := applicationVariantRetrieve(request.Id)
		if !ok {
			return util.Empty{}, util.HttpErr(http.StatusNotFound, "flavor not found")
		}
		variant, baseGroup := applicationVariantSnapshot(internal)
		if !applicationVariantCanManage(info.Actor, variant) {
			return util.Empty{}, util.HttpErr(http.StatusNotFound, "flavor not found")
		}
		internal.Mu.Lock()
		wasDeleted := internal.Value.State == orcapi.ApplicationVariantStateDeleted
		if !wasDeleted {
			db.NewTx0(func(tx *db.Transaction) {
				db.Exec(
					tx,
					`
						update app_store.application_variants
						set state = 'DELETED', modified_at = now()
						where id = :id
					`,
					db.Params{
						"id": variant.Id,
					},
				)
			})
			internal.Value.State = orcapi.ApplicationVariantStateDeleted
		}
		internal.Mu.Unlock()
		if !wasDeleted {
			applicationVariantCacheDelete(variant.Id, baseGroup)
		}
		return util.Empty{}, nil
	})

	orcapi.ApplicationVariantsControlCompleteSnapshot.Handler(func(info rpc.RequestInfo, request orcapi.ApplicationVariantCompleteSnapshotRequest) (util.Empty, *util.HttpError) {
		provider, ok := strings.CutPrefix(info.Actor.Username, fndapi.ProviderSubjectPrefix)
		internal, found := applicationVariantRetrieve(request.VariantId)
		if !ok || !found {
			return util.Empty{}, util.HttpErr(http.StatusNotFound, "flavor not found")
		}
		variant, _ := applicationVariantSnapshot(internal)
		if provider != variant.Provider {
			return util.Empty{}, util.HttpErr(http.StatusNotFound, "flavor not found")
		}
		if variant.State == orcapi.ApplicationVariantStateDeleted {
			return util.Empty{}, util.HttpErr(http.StatusConflict, "flavor was deleted")
		}
		if request.Failure.Present {
			applicationVariantCancelPush(internal)
			applicationVariantSetFailure(variant.Id, request.Failure.Value)
			return util.Empty{}, nil
		}
		if variant.RevisionId != 0 && variant.ImageDigest == request.ImageDigest {
			applicationVariantCancelPush(internal)
			return util.Empty{}, nil
		}
		actor := request.RequestedBy
		if actor == "" {
			actor = variant.CreatedBy
		}
		baseOverride := util.OptNone[orcapi.NameAndVersion]()
		if request.BaseApplication.Name != "" {
			baseOverride.Set(request.BaseApplication)
		}
		_, err := applicationVariantMaterialize(internal, request.Image, request.ImageDigest, actor, baseOverride)
		return util.Empty{}, err
	})
}

func applicationVariantCacheUpdateMetadata(variant orcapi.ApplicationVariant) {
	name := fmt.Sprintf("variant-%d", variant.Id)
	b := appBucket(name)
	b.Mu.RLock()
	apps := append([]*internalApplication(nil), b.Applications[name]...)
	b.Mu.RUnlock()
	for _, app := range apps {
		app.Mu.Lock()
		app.FlavorName.Set(variant.Title)
		if app.Variant.Present {
			appVariant := app.Variant.Value
			appVariant.Title = variant.Title
			appVariant.PublishedToProject = variant.PublishedToProject
			app.Variant.Set(appVariant)
		}
		app.Mu.Unlock()
	}
}

func applicationVariantCacheDelete(id int64, groupId AppGroupId) {
	name := fmt.Sprintf("variant-%d", id)
	b := appBucket(name)
	b.Mu.RLock()
	apps := append([]*internalApplication(nil), b.Applications[name]...)
	b.Mu.RUnlock()
	for _, app := range apps {
		app.Mu.Lock()
		if app.Variant.Present {
			variant := app.Variant.Value
			variant.State = orcapi.ApplicationVariantStateDeleted
			app.Variant.Set(variant)
		}
		app.Mu.Unlock()
	}
	if group, ok := appRetrieveGroup(groupId); ok {
		group.Mu.Lock()
		for i := 0; i < len(group.Items); i++ {
			if group.Items[i] == name {
				group.Items = util.RemoveAtIndex(group.Items, i)
				break
			}
		}
		group.Mu.Unlock()
	}
}
