package orchestrator

import (
	"fmt"
	"net/http"
	"runtime"
	"slices"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"ucloud.dk/core/pkg/coreutil"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Introduction
// =====================================================================================================================
// This file implements the resource subsystem used by the orchestrator deployment. The resource system exists to
// provide a single generic abstraction for "things" that are managed within a project. Resources are often backed by
// a concrete physical or logical resource in a service-provider. But resources can also be managed entirely within
// UCloud's Core if needed.
//
// The goals of the resource system is to:
//
// - Centralize the authorization and ACL evaluation features
// - Provide a shared indexing and browsing mechanism across different resource types
// - To make it easy to plug in new resource types via a simple flow
//
// Using the resource system
// ---------------------------------------------------------------------------------------------------------------------
// The resource API is designed to be used in a way that it can easily create new resource types. In order to create a
// resource type, you can follow the steps below:
//
// 1. Registering a type:
//    Call InitResourceType with the correct loader, persister and transformer. Once created, you can invoke all the
//    Resource* functions on that type.
//
// 2. Creating resources:
//    Use ResourceCreate or ResourceCreateThroughProvider for user-initiated creation. Use ResourceCreateEx for
//    system/provider-initiated creation.
//
//    These will both allocate a new ID and run all relevant side effects. If you are not using creation through a
//    provider, then you must also call ResourceConfirm after invoking the create function.
//
// 3. Retrieving and browsing:
//    You can retrieve or browse resources through the ResourceRetrieve and ResourceBrowse functions. These will
//    automatically apply permission checks as relevant. For system initiated operations you can pass in
//    rpc.ActorSystem to bypass the authorization checks.
//
// 4. Updates and deletions:
//    You can apply updates to a resource through the ResourceUpdate function. Similarly, deleted can be performed
//    through the ResourceDelete function. These will automatically trigger persistence assuming that the function
//    was successful.

// Core types and globals
// =====================================================================================================================
// This section contains relevant core types and access to the global state.
//
// ---------------------------------------------------------------------------------------------------------------------
// !! MUTEX LOCK ORDER !!
// ---------------------------------------------------------------------------------------------------------------------
// globals -> index -> resource

type ResourceId int64

type resource struct {
	Id         ResourceId
	ProviderId util.Option[string]

	Owner orcapi.ResourceOwner
	Acl   []orcapi.ResourceAclEntry

	CreatedAt  time.Time
	ModifiedAt time.Time

	Type  string
	Extra any

	MarkedForDeletion bool
	Confirmed         bool

	Product util.Option[accapi.ProductReference]

	// NOTE(Dan): Updates and status are now managed by the caller
}

func (r *resource) ToApi(myPermissions []orcapi.Permission) orcapi.Resource {
	return orcapi.Resource{
		Id:        fmt.Sprint(r.Id),
		CreatedAt: fndapi.Timestamp(r.CreatedAt),
		Owner:     r.Owner,
		Permissions: util.OptValue(orcapi.ResourcePermissions{
			Myself: myPermissions,
			Others: r.Acl,
		}),
		ProviderGeneratedId: r.ProviderId.GetOrDefault(""),
	}
}

type resourceBucket struct {
	Mu        sync.RWMutex
	Resources map[ResourceId]*resource
}

type resourceIndexBucket struct {
	Mu      sync.RWMutex
	ByOwner map[string][]ResourceId // Sorted by ID ascending (newest last)
}

type resourceProvider struct {
	Mu          sync.RWMutex
	ProviderIds map[string]ResourceId
}

var resourceGlobals struct {
	Mu sync.RWMutex

	ByType    map[string]*resourceTypeGlobal
	Providers map[string]*resourceProvider // provider -> bucket

	Testing struct {
		Enabled bool
	}

	IdAcc atomic.Int64 // does not require mutex
}

type resourceTypeFlags int64

const (
	// resourceTypeCreateWithoutAdmin allows for the creation of new resources even if the actor is not an admin of
	// the specified workspace.
	resourceTypeCreateWithoutAdmin resourceTypeFlags = 1 << iota

	resourceTypeCreateAsAllocator
)

type resourceTypeGlobal struct {
	Type string

	Resources []*resourceBucket
	Indexes   []*resourceIndexBucket

	Flags resourceTypeFlags

	OnLoad             func(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource)
	OnPersist          func(b *db.Batch, resources *resource)
	OnPersistCommitted func(r *resource)
	Transformer        func(r orcapi.Resource, product util.Option[accapi.ProductReference], extra any, flags orcapi.ResourceFlags, actor rpc.Actor) any

	IndexersMu sync.RWMutex
	Indexers   []func(r *resource) ResourceIndexer
}

func resourceGetProvider(providerId string) *resourceProvider {
	resourceGlobals.Mu.RLock()
	g, ok := resourceGlobals.Providers[providerId]
	resourceGlobals.Mu.RUnlock()

	if !ok {
		g = resourceLoadProvider(providerId)
	}

	return g
}

func resourceGetGlobals(typeName string) *resourceTypeGlobal {
	resourceGlobals.Mu.RLock()
	g := resourceGlobals.ByType[typeName]
	resourceGlobals.Mu.RUnlock()
	return g
}

func resourceGetBucket(typeName string, id ResourceId) *resourceBucket {
	g := resourceGetGlobals(typeName)
	h := util.NonCryptographicHash(id)
	return g.Resources[h%len(g.Resources)]
}

func resourceGetAndLoadIndex(typeName string, reference string) *resourceIndexBucket {
	g := resourceGetGlobals(typeName)

	h := util.NonCryptographicHash(reference)
	b := g.Indexes[h%len(g.Indexes)]

	b.Mu.RLock()
	_, ok := b.ByOwner[reference]
	b.Mu.RUnlock()

	if !ok && !resourceGlobals.Testing.Enabled {
		t := util.NewTimer()
		resourceLoadIndex(b, typeName, reference)
		resourceLoadIndexDuration.WithLabelValues(typeName).Observe(t.Mark().Seconds())
		resourceIndexCacheMiss.WithLabelValues(typeName).Inc()
	} else {
		resourceIndexCacheHit.WithLabelValues(typeName).Inc()
	}

	return b
}

// Initialization
// =====================================================================================================================
// This section contains initialization logic for the resource subsystem and per-type registration.
// Types must be registered with persistence and transformation hooks before use. InitResources must be called exactly
// once and InitResourceType must be called exactly once per type.

func InitResources() {
	resourceGlobals.IdAcc.Store(1)
	resourceGlobals.ByType = map[string]*resourceTypeGlobal{}
	resourceGlobals.Providers = map[string]*resourceProvider{}

	if !resourceGlobals.Testing.Enabled {
		db.NewTx0(func(tx *db.Transaction) {
			id, ok := db.Get[struct{ Id int64 }](
				tx,
				`select coalesce(max(id), 0) as id from provider.resource`,
				db.Params{},
			)

			if ok {
				resourceGlobals.IdAcc.Store(id.Id)
			}
		})
	}
}

func InitResourceType(
	typeName string,
	flags resourceTypeFlags,
	doLoad func(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource),
	doPersist func(b *db.Batch, r *resource),
	transformer func(r orcapi.Resource, product util.Option[accapi.ProductReference], extra any, flags orcapi.ResourceFlags, actor rpc.Actor) any,
	persistCommitted func(r *resource),
) {
	if !resourceGlobals.Testing.Enabled {
		if doLoad == nil || doPersist == nil {
			panic("doLoad and doPersist must be specified")
		}
	}

	if transformer == nil {
		panic("transformer must be specified")
	}

	resourceGlobals.Mu.Lock()

	if _, exists := resourceGlobals.ByType[typeName]; exists {
		panic(fmt.Sprintf("%s has already been initialized", typeName))
	}

	tGlobals := &resourceTypeGlobal{
		Flags:              flags,
		OnLoad:             doLoad,
		OnPersist:          doPersist,
		OnPersistCommitted: persistCommitted,
		Transformer:        transformer,
	}
	resourceGlobals.ByType[typeName] = tGlobals

	for i := 0; i < runtime.NumCPU(); i++ {
		tGlobals.Resources = append(tGlobals.Resources, &resourceBucket{
			Resources: map[ResourceId]*resource{},
		})

		tGlobals.Indexes = append(tGlobals.Indexes, &resourceIndexBucket{
			ByOwner: map[string][]ResourceId{},
		})
	}

	resourceGlobals.Mu.Unlock()
}

// Low-level authorization and validation
// =====================================================================================================================
// This section contain the primary building blocks for accessing parts of the systems which require authorization.
// These APIs will typically load the resource if it has not already been brought into one of the buckets.

func resourcesReadEx(
	actor rpc.Actor,
	typeName string,
	requiredPermission orcapi.Permission,
	b *resourceBucket,
	id ResourceId,
	prefetchHint []ResourceId,
) (*resource, bool, []orcapi.Permission) {
	t := util.NewTimer()

	var permissions []orcapi.Permission
	b.Mu.RLock()

	r, ok := b.Resources[id]
	if !ok {
		b.Mu.RUnlock()
		resourceLoad(typeName, id, prefetchHint)
		b.Mu.RLock()

		r, ok = b.Resources[id]
	} else {
		resourceLoadCacheHit.WithLabelValues(typeName).Inc()
	}

	resourceReadDuration.WithLabelValues(typeName, "retrieve").Observe(t.Mark().Seconds())

	if ok {
		if actor.Username == rpc.ActorSystem.Username {
			permissions = orcapi.PermissionsAdd(permissions, orcapi.PermissionProvider)
			permissions = orcapi.PermissionsAdd(permissions, orcapi.PermissionAdmin)
			permissions = orcapi.PermissionsAdd(permissions, orcapi.PermissionRead)
			permissions = orcapi.PermissionsAdd(permissions, orcapi.PermissionEdit)
		} else {
			providerId, isProvider := strings.CutPrefix(actor.Username, fndapi.ProviderSubjectPrefix)

			if isProvider {
				if r.Product.Present {
					if r.Product.Value.Provider == providerId {
						permissions = orcapi.PermissionsAdd(permissions, orcapi.PermissionRead)
						permissions = orcapi.PermissionsAdd(permissions, orcapi.PermissionProvider)
					}
				}
			} else {
				checkAcl := false

				if r.Owner.Project.Present {
					role, isMember := actor.Membership[rpc.ProjectId(r.Owner.Project.Value)]

					if isMember {
						if role == rpc.ProjectRolePI || role == rpc.ProjectRoleAdmin {
							permissions = orcapi.PermissionsAdd(permissions, orcapi.PermissionAdmin)
							permissions = orcapi.PermissionsAdd(permissions, orcapi.PermissionRead)
							permissions = orcapi.PermissionsAdd(permissions, orcapi.PermissionEdit)
						} else if actor.Username == r.Owner.CreatedBy {
							permissions = orcapi.PermissionsAdd(permissions, orcapi.PermissionAdmin)
							permissions = orcapi.PermissionsAdd(permissions, orcapi.PermissionRead)
							permissions = orcapi.PermissionsAdd(permissions, orcapi.PermissionEdit)
						} else {
							checkAcl = true
						}
					}
				} else {
					if r.Owner.CreatedBy == actor.Username {
						permissions = orcapi.PermissionsAdd(permissions, orcapi.PermissionAdmin)
						permissions = orcapi.PermissionsAdd(permissions, orcapi.PermissionRead)
						permissions = orcapi.PermissionsAdd(permissions, orcapi.PermissionEdit)
					} else {
						checkAcl = true
					}
				}

				if checkAcl {
					for _, entry := range r.Acl {
						entityApplies := false
						if entry.Entity.Type == orcapi.AclEntityTypeUser {
							if entry.Entity.Username == actor.Username {
								entityApplies = true
							}
						} else if entry.Entity.Type == orcapi.AclEntityTypeProjectGroup {
							_, entityApplies = actor.Groups[rpc.GroupId(entry.Entity.Group)]
						}

						if entityApplies {
							for _, p := range entry.Permissions {
								permissions = orcapi.PermissionsAdd(permissions, p)
							}
						}
					}
				}
			}
		}
	}

	if ok && !slices.Contains(permissions, requiredPermission) {
		r, ok, permissions = nil, false, nil
	}

	resourceReadDuration.WithLabelValues(typeName, "acl_check").Observe(t.Mark().Seconds())

	b.Mu.RUnlock()

	return r, ok, permissions
}

func lResourceApplyFlags(r *resource, myPerms []orcapi.Permission, flags orcapi.ResourceFlags) (orcapi.Resource, bool) {
	var result orcapi.Resource
	if r.Product.Present {
		prod := r.Product.Value
		if flags.FilterProvider.Present && prod.Provider != flags.FilterProvider.Value {
			return result, false
		}

		if flags.FilterProductId.Present && prod.Id != flags.FilterProductId.Value {
			return result, false
		}

		if flags.FilterProductCategory.Present && prod.Category != flags.FilterProductCategory.Value {
			return result, false
		}

		if flags.HideProvider.Present && prod.Provider == flags.HideProvider.Value {
			return result, false
		}

		if flags.HideProductCategory.Present && prod.Category == flags.HideProductCategory.Value {
			return result, false
		}

		if flags.HideProductId.Present && prod.Id == flags.HideProductId.Value {
			return result, false
		}
	}

	if flags.FilterCreatedBy.Present && flags.FilterCreatedBy.Value != r.Owner.CreatedBy {
		return result, false
	}

	if flags.FilterCreatedAfter.Present && r.CreatedAt.Before(fndapi.TimeFromUnixMilli(flags.FilterCreatedAfter.Value).Time()) {
		return result, false
	}

	if flags.FilterCreatedBefore.Present && r.CreatedAt.After(fndapi.TimeFromUnixMilli(flags.FilterCreatedBefore.Value).Time()) {
		return result, false
	}

	if flags.FilterIds.Present {
		ids := strings.Split(flags.FilterIds.Value, ",")
		found := false
		for _, id := range ids {
			if ResourceParseId(id) == r.Id {
				found = true
				break
			}
		}

		if !found {
			return result, false
		}
	}

	if flags.FilterProviderIds.Present {
		ids := strings.Split(flags.FilterProviderIds.Value, ",")
		found := false
		if r.ProviderId.Present {
			for _, id := range ids {
				if id == r.ProviderId.Value {
					found = true
					break
				}
			}
		}

		if !found {
			return result, false
		}
	}

	result = orcapi.Resource{
		Id:        fmt.Sprint(r.Id),
		CreatedAt: fndapi.Timestamp(r.CreatedAt),
		Owner:     r.Owner,
		Permissions: util.OptValue(orcapi.ResourcePermissions{
			Myself: myPerms,
		}),
		ProviderGeneratedId: r.ProviderId.GetOrDefault(""),
	}

	if flags.IncludeOthers {
		result.Permissions.Value.Others = make([]orcapi.ResourceAclEntry, len(r.Acl))
		copy(result.Permissions.Value.Others, r.Acl)
	}

	result.Permissions.Value.Myself = util.NonNilSlice(result.Permissions.Value.Myself)
	result.Permissions.Value.Others = util.NonNilSlice(result.Permissions.Value.Others)

	// NOTE(Dan): includeProduct is handled by the individual transformers now

	return result, true
}

// Resource retrieval
// =====================================================================================================================

func ResourceParseId(id string) ResourceId {
	converted, _ := strconv.ParseInt(id, 10, 64) // id 0 is never used
	return ResourceId(converted)
}

func ResourceRetrieve[T any](actor rpc.Actor, typeName string, pId ResourceId, flags orcapi.ResourceFlags) (T, *util.HttpError) {
	t, _, _, err := ResourceRetrieveEx[T](actor, typeName, pId, orcapi.PermissionRead, flags)
	return t, err
}

func ResourceRetrieveEx[T any](
	actor rpc.Actor,
	typeName string,
	pId ResourceId,
	requiredPermission orcapi.Permission,
	flags orcapi.ResourceFlags,
) (T, orcapi.Resource, util.Option[accapi.ProductReference], *util.HttpError) {
	var result T

	g := resourceGetGlobals(typeName)
	b := resourceGetBucket(typeName, pId)

	r, ok, perms := resourcesReadEx(actor, typeName, requiredPermission, b, pId, nil)
	if ok {
		b.Mu.RLock()
		mapped, ok := lResourceApplyFlags(r, perms, flags)
		if ok {
			rawResult := g.Transformer(mapped, r.Product, r.Extra, flags, actor)
			result = rawResult.(T)
		}
		b.Mu.RUnlock()
		if ok {
			return result, r.ToApi(perms), r.Product, nil
		}
	}

	errorMessage := util.HttpErr(http.StatusNotFound, "not found")
	if perms == nil && !ok {
		errorMessage = util.HttpErr(http.StatusForbidden, "write permission is required")
	}
	return result, orcapi.Resource{}, util.OptNone[accapi.ProductReference](), errorMessage
}

type ResourceSortByFn[T any] func(a T, b T) int

func ResourceDefaultComparator[T any](resourceGetter func(item T) orcapi.Resource, flags orcapi.ResourceFlags) ResourceSortByFn[T] {
	if !flags.SortBy.Present {
		return nil
	}

	switch flags.SortBy.Value {
	case "createdAt":
		return func(a T, b T) int {
			aResc := resourceGetter(a)
			bResc := resourceGetter(b)

			cmp := aResc.CreatedAt.Time().Compare(bResc.CreatedAt.Time())
			if cmp != 0 {
				return cmp
			} else {
				aId := ResourceParseId(aResc.Id)
				bId := ResourceParseId(bResc.Id)
				if aId < bId {
					return -1
				} else if aId > bId {
					return 1
				} else {
					return 0
				}
			}
		}

	case "createdBy":
		return func(a T, b T) int {
			aResc := resourceGetter(a)
			bResc := resourceGetter(b)
			cmp := strings.Compare(aResc.Owner.CreatedBy, bResc.Owner.CreatedBy)
			if cmp != 0 {
				return cmp
			} else {
				aId := ResourceParseId(aResc.Id)
				bId := ResourceParseId(bResc.Id)
				if aId < bId {
					return -1
				} else if aId > bId {
					return 1
				} else {
					return 0
				}
			}
		}

	default:
		return nil
	}
}

func ResourceBrowse[T any](
	actor rpc.Actor,
	typeName string,
	next util.Option[string],
	itemsPerPage int,
	flags orcapi.ResourceFlags,
	filter func(item T) bool,
	sortComparator ResourceSortByFn[T],
) fndapi.PageV2[T] {
	if flags.FilterProviderIds.Present {
		providerId, ok := strings.CutPrefix(actor.Username, fndapi.ProviderSubjectPrefix)
		providerGenIds := strings.Split(flags.FilterProviderIds.Value, ",")
		if !ok || len(providerGenIds) > 1000 || len(providerGenIds) == 0 {
			return fndapi.PageV2[T]{Items: util.NonNilSlice[T](nil), ItemsPerPage: 1000}
		}

		var resourceIds []ResourceId

		providerBucket := resourceGetProvider(providerId)
		providerBucket.Mu.Lock()
		for _, id := range providerGenIds {
			rescId, ok := providerBucket.ProviderIds[id]
			if ok {
				resourceIds = append(resourceIds, rescId)
			}
		}
		providerBucket.Mu.Unlock()

		var items []T
		for _, rescId := range resourceIds {
			resc, err := ResourceRetrieve[T](actor, typeName, rescId, flags)
			if err == nil && filter(resc) {
				items = append(items, resc)
			}
		}

		return fndapi.PageV2[T]{Items: items, ItemsPerPage: 1000}
	} else {
		t := util.NewTimer()

		g := resourceGetGlobals(typeName)
		ref := actor.Username
		if actor.Project.Present {
			ref = string(actor.Project.Value)
		}

		idxBucket := resourceGetAndLoadIndex(typeName, ref)
		resourceBrowseDuration.WithLabelValues(typeName, "index").Observe(t.Mark().Seconds())

		idxBucket.Mu.RLock()
		idx := append([]ResourceId(nil), idxBucket.ByOwner[ref]...) // deep copy under lock

		if len(idx) > 10_000 {
			// NOTE(Dan): We refuse to run anything but the default sort if there are too many expected results.
			// We will have to add a better solution if this ever becomes an issue.
			sortComparator = nil
		}

		startIndex := len(idx) - 1
		if sortComparator == nil {
			if next.Present {
				rId := ResourceParseId(next.Value)
				nextIdx, _ := slices.BinarySearch(idx, rId)
				startIndex = nextIdx - 1
			}
		}

		idxBucket.Mu.RUnlock()
		resourceBrowseDuration.WithLabelValues(typeName, "prepare_prefetch").Observe(t.Mark().Seconds())

		var items []T
		newNext := util.OptNone[string]()
		itemsPerPage = fndapi.ItemsPerPage(itemsPerPage)
		prevId := ResourceId(0)

		readAccSeconds := float64(0)
		transformAccSeconds := float64(0)

		for i := startIndex; i >= 0; i-- {
			id := idx[i]

			t.Mark()
			b := resourceGetBucket(typeName, id)
			resc, ok, perms := resourcesReadEx(actor, typeName, orcapi.PermissionRead, b, id, idx)
			readAccSeconds += t.Mark().Seconds()

			if ok {
				shouldBreak := false

				b.Mu.RLock()
				mapped, ok := lResourceApplyFlags(resc, perms, flags)
				if ok {
					item := g.Transformer(mapped, resc.Product, resc.Extra, flags, actor).(T)
					if filter == nil || filter(item) {
						if sortComparator == nil && len(items) >= itemsPerPage {
							newNext.Set(fmt.Sprint(prevId))
							shouldBreak = true
						} else {
							prevId = resc.Id
							items = append(items, item)
						}
					}
				}
				b.Mu.RUnlock()

				transformAccSeconds += t.Mark().Seconds()

				if shouldBreak {
					break
				}
			}
		}

		resourceBrowseDuration.WithLabelValues(typeName, "read").Observe(readAccSeconds)
		resourceBrowseDuration.WithLabelValues(typeName, "transform").Observe(transformAccSeconds)

		t.Mark()

		if sortComparator != nil {
			actualSortComparator := sortComparator
			if flags.SortDirection.Present && flags.SortDirection.Value == orcapi.SortDirectionDescending {
				actualSortComparator = func(a T, b T) int {
					res := sortComparator(a, b)
					return res * -1
				}
			}

			slices.SortFunc(items, actualSortComparator)
			baseIdx := 0
			if next.Present {
				nextIdx, err := strconv.ParseInt(next.Value, 10, 32)
				if err == nil {
					baseIdx = int(nextIdx)
				}
			}

			baseIdx = max(0, min(len(items), baseIdx))
			lastIdx := max(0, min(len(items), baseIdx+itemsPerPage))
			hasMore := len(items) > lastIdx
			items = items[baseIdx:lastIdx]
			if hasMore {
				newNext.Set(fmt.Sprint(lastIdx))
			}
		}

		resourceBrowseDuration.WithLabelValues(typeName, "sort").Observe(t.Mark().Seconds())

		return fndapi.PageV2[T]{
			Items:        items,
			Next:         newNext,
			ItemsPerPage: itemsPerPage,
		}
	}
}

// Resource updates
// =====================================================================================================================

func ResourceSystemUpdate[T any](
	typeName string,
	id ResourceId,
	modification func(r *resource, mapped T),
) bool {
	return ResourceUpdate(rpc.ActorSystem, typeName, id, orcapi.PermissionRead, modification)
}

func ResourceUpdate[T any](
	actor rpc.Actor,
	typeName string,
	id ResourceId,
	requiredPermission orcapi.Permission,
	modification func(r *resource, mapped T),
) bool {
	g := resourceGetGlobals(typeName)

	b := resourceGetBucket(typeName, id)
	resc, ok, perms := resourcesReadEx(actor, typeName, requiredPermission, b, id, nil)
	if ok {
		b.Mu.Lock()
		apiResc, ok := lResourceApplyFlags(resc, perms, orcapi.ResourceFlags{})
		if !ok {
			log.Fatal("resource was not supposed to be filtered here")
		}
		mapped := g.Transformer(apiResc, resc.Product, resc.Extra, orcapi.ResourceFlags{}, actor).(T)

		// Indexing before modification
		var indexers []ResourceIndexer
		{
			g.IndexersMu.RLock()
			for _, begin := range g.Indexers {
				indexers = append(indexers, begin(resc))
			}
			g.IndexersMu.RUnlock()
		}

		for _, idx := range indexers {
			idx.Begin()
		}

		for _, idx := range indexers {
			idx.Remove()
		}

		modification(resc, mapped)

		isDeleting := resc.Confirmed && resc.MarkedForDeletion
		rescOwnerRef := resc.Owner.CreatedBy
		if resc.Owner.Project.Present {
			rescOwnerRef = resc.Owner.Project.Value
		}

		if resc.Confirmed {
			lResourcePersist(resc)

			if resc.MarkedForDeletion {
				delete(b.Resources, id)
			}
		}

		// Indexing after modification
		if !resc.MarkedForDeletion {
			for _, idx := range indexers {
				idx.Add()
			}
		}

		// Commit indexing
		for _, idx := range indexers {
			idx.Commit()
		}

		b.Mu.Unlock()

		if isDeleting {
			// NOTE(Dan): There is technically a race-condition here where the resource may remain in the index, but
			// it has already been deleted from the store. This is not super important since the only thing this does
			// is to cause a brief load from the database to realize that it is in fact not in the store.

			idxBucket := resourceGetAndLoadIndex(typeName, rescOwnerRef)

			idxBucket.Mu.Lock()
			idx := idxBucket.ByOwner[rescOwnerRef]
			idxBucket.ByOwner[rescOwnerRef] = util.RemoveFirst(idx, id)
			idxBucket.Mu.Unlock()
		}

		return true
	} else {
		return false
	}
}

func ResourceUpdateAcl(
	actor rpc.Actor,
	typeName string,
	acl orcapi.UpdatedAcl,
) *util.HttpError {
	{
		// NOTE(Dan): Verify all entities in the added section
		projectInfoIfNeeded := util.Option[fndapi.Project]{}

		for _, item := range acl.Added {
			switch item.Entity.Type {
			case orcapi.AclEntityTypeUser:
				_, ok := rpc.LookupActor(item.Entity.Username)
				if !ok {
					return util.HttpErr(http.StatusForbidden, "bad ACL update supplied")
				}

			case orcapi.AclEntityTypeProjectGroup:
				if actor.Username != rpc.ActorSystem.Username {
					if item.Entity.ProjectId != string(actor.Project.Value) {
						return util.HttpErr(http.StatusForbidden, "bad ACL update supplied")
					}
				}

				if !projectInfoIfNeeded.Present {
					db.NewTx0(func(tx *db.Transaction) {
						p, ok := coreutil.ProjectRetrieveFromDatabase(tx, item.Entity.ProjectId)
						if ok {
							projectInfoIfNeeded.Set(p)
						}
					})

					if !projectInfoIfNeeded.Present {
						return util.HttpErr(http.StatusInternalServerError, "could not verify acl")
					}
				}

				found := false
				for _, existingGroup := range projectInfoIfNeeded.Value.Status.Groups {
					if existingGroup.Id == item.Entity.Group {
						found = true
						break
					}
				}

				if !found {
					return util.HttpErr(http.StatusForbidden, "bad ACL update supplied")
				}
			}
		}
	}

	var addedUsers []string
	pId := ResourceParseId(acl.Id)
	ok := ResourceUpdate[any](actor, typeName, pId, orcapi.PermissionAdmin, func(r *resource, mapped any) {
		var newAcl []orcapi.ResourceAclEntry
		for _, entry := range r.Acl {
			wasDeleted := false
			for _, deletedEntry := range acl.Deleted {
				if entry.Entity == deletedEntry {
					wasDeleted = true
					break
				}
			}

			if !wasDeleted {
				newAcl = append(newAcl, entry)
			}
		}

		for _, toAdd := range acl.Added {
			wasFound := false
			for i, existing := range newAcl {
				if existing.Entity == toAdd.Entity {
					newAcl[i] = toAdd
					wasFound = true
					break
				}
			}

			if !wasFound {
				newAcl = append(newAcl, toAdd)

				if toAdd.Entity.Type == orcapi.AclEntityTypeUser {
					addedUsers = append(addedUsers, toAdd.Entity.Username)
				}
			}
		}

		r.Acl = newAcl
	})

	if len(addedUsers) > 0 {
		for _, user := range addedUsers {
			idx := resourceGetAndLoadIndex(typeName, user)

			idx.Mu.Lock()
			idx.ByOwner[user] = util.AppendUnique(idx.ByOwner[user], pId)
			slices.Sort(idx.ByOwner[user])
			idx.Mu.Unlock()
		}
	}

	if !ok {
		return util.HttpErr(http.StatusNotFound, "not found or permission denied")
	} else {
		return nil
	}
}

type resourceCreateFlags int64

const (
	resourceCreateAllRead resourceCreateFlags = 1 << iota
	resourceCreateAllWrite
)

func ResourceCreateEx[T any](
	typeName string,
	owner orcapi.ResourceOwner,
	acl []orcapi.ResourceAclEntry,
	product util.Option[accapi.ProductReference],
	providerId util.Option[string],
	extra any,
	flags resourceCreateFlags,
) (ResourceId, T, *util.HttpError) {
	g := resourceGetGlobals(typeName)
	id := ResourceId(resourceGlobals.IdAcc.Add(1))
	b := resourceGetBucket(typeName, id)

	allUsersGroup := "" // valid only if resourceCreateAllRead/Write is set
	if owner.Project.Present && (flags&resourceCreateAllRead != 0 || flags&resourceCreateAllWrite != 0) {
		groupId, ok := resourceRetrieveAllUserGroup(owner.Project.Value)
		if !ok {
			var t T
			return 0, t, util.HttpErr(http.StatusInternalServerError, "could not create resource (ACL 0)")
		} else {
			allUsersGroup = groupId
		}
	}

	if product.Present && providerId.Present {
		isReserved := false
		providerBucket := resourceGetProvider(product.Value.Provider)
		providerBucket.Mu.Lock()
		_, isReserved = providerBucket.ProviderIds[providerId.Value]
		if !isReserved {
			providerBucket.ProviderIds[providerId.Value] = id
		}
		providerBucket.Mu.Unlock()

		if isReserved {
			var t T
			return 0, t, util.HttpErr(http.StatusConflict, "already exists")
		}
	}

	{
		indexRef := owner.Project.GetOrDefault(owner.CreatedBy)
		idxBucket := resourceGetAndLoadIndex(typeName, indexRef)
		idxBucket.Mu.Lock()
		current := idxBucket.ByOwner[indexRef]
		current = append(current, id)
		slices.Sort(current)
		idxBucket.ByOwner[indexRef] = current
		idxBucket.Mu.Unlock()
	}

	b.Mu.Lock()
	now := time.Now()
	r := &resource{
		Id:         id,
		ProviderId: providerId,
		Owner:      owner,
		Acl:        acl,
		CreatedAt:  now,
		ModifiedAt: now,
		Type:       typeName,
		Extra:      extra,
		Confirmed:  false,
		Product:    product,
	}
	b.Resources[id] = r
	resourceFlags := orcapi.ResourceFlags{
		IncludeOthers:  true,
		IncludeUpdates: true,
		IncludeSupport: true,
		IncludeProduct: true,
	}

	if owner.Project.Present && (flags&resourceCreateAllRead != 0 || flags&resourceCreateAllWrite != 0) {
		var perms []orcapi.Permission
		if flags&resourceCreateAllRead != 0 {
			perms = append(perms, orcapi.PermissionRead)
		}
		if flags&resourceCreateAllWrite != 0 {
			perms = append(perms, orcapi.PermissionEdit)
		}

		r.Acl = append(r.Acl, orcapi.ResourceAclEntry{
			Entity:      orcapi.AclEntityProjectGroup(owner.Project.Value, allUsersGroup),
			Permissions: perms,
		})
	}

	apiResc, _ := lResourceApplyFlags(r, nil, resourceFlags)
	mapped := g.Transformer(apiResc, r.Product, r.Extra, resourceFlags, rpc.ActorSystem).(T)

	var indexers []ResourceIndexer
	g.IndexersMu.RLock()
	for _, begin := range g.Indexers {
		indexers = append(indexers, begin(r))
	}
	g.IndexersMu.RUnlock()
	for _, idx := range indexers {
		idx.Begin()
	}
	for _, idx := range indexers {
		idx.Add()
	}
	for _, idx := range indexers {
		idx.Commit()
	}

	b.Mu.Unlock()

	return r.Id, mapped, nil
}

func ResourceCreate[T any](
	actor rpc.Actor,
	typeName string,
	product util.Option[accapi.ProductReference],
	extra any,
) (ResourceId, T, *util.HttpError) {
	g := resourceGetGlobals(typeName)

	if actor.Project.Present {
		_, isAllocator := actor.AllocatorProjects[actor.Project.Value]
		if isAllocator && g.Flags&resourceTypeCreateAsAllocator == 0 {
			var t T
			return 0, t, util.HttpErr(http.StatusForbidden, "this project is not allowed to consume resources")
		}
	}

	if g.Flags&resourceTypeCreateWithoutAdmin == 0 {
		if actor.Project.Present && !actor.Membership[actor.Project.Value].Satisfies(rpc.ProjectRoleAdmin) {
			var t T
			return 0, t, util.HttpErr(http.StatusForbidden, "you need administrator privileges to do this operation")
		}
	}

	owner := orcapi.ResourceOwner{
		CreatedBy: actor.Username,
		Project: util.OptMap(actor.Project, func(value rpc.ProjectId) string {
			return string(value)
		}),
	}

	return ResourceCreateEx[T](typeName, owner, nil, product, util.OptNone[string](), extra, 0)
}

func ResourceConfirm(typeName string, id ResourceId) {
	ResourceUpdate[any](
		rpc.ActorSystem,
		typeName,
		id,
		orcapi.PermissionRead,
		func(r *resource, mapped any) {
			r.Confirmed = true
		},
	)
}

func ResourceDelete(actor rpc.Actor, typeName string, id ResourceId) bool {
	return ResourceUpdate[any](
		actor,
		typeName,
		id,
		orcapi.PermissionEdit,
		func(r *resource, mapped any) {
			r.Confirmed = true
			r.MarkedForDeletion = true
		},
	)
}

type ResourceIndexer interface {
	Remove()
	Add()
	Begin()
	Commit()
}

func ResourceAddIndexer(typeName string, indexer func(r *resource) ResourceIndexer) {
	g := resourceGetGlobals(typeName)
	g.IndexersMu.Lock()
	g.Indexers = append(g.Indexers, indexer)
	g.IndexersMu.Unlock()
}

var resourceAllUsersGroupCache = util.NewCache[string, string](8 * time.Hour)

func resourceRetrieveAllUserGroup(projectId string) (string, bool) {
	return resourceAllUsersGroupCache.Get(projectId, func() (string, error) {
		result, err := fndapi.ProjectRetrieveAllUsersGroup.Invoke(
			fndapi.BulkRequestOf(fndapi.FindByProjectId{Project: projectId}))

		if err != nil || len(result.Responses) == 0 {
			return "", err
		} else {
			return result.Responses[0].Id, nil
		}
	})
}

// Metrics
// =====================================================================================================================

var (
	resourceLoadIndexDuration = promauto.NewSummaryVec(prometheus.SummaryOpts{
		Namespace: "ucloud",
		Subsystem: "orchestrator",
		Name:      "resource_index_load_duration_seconds",
		Help:      "Summary of the duration (in seconds) it takes to load an index",
		Objectives: map[float64]float64{
			0.5:  0.01,
			0.75: 0.01,
			0.95: 0.01,
			0.99: 0.01,
		},
	}, []string{"type"})

	resourceIndexCacheHit = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud",
		Subsystem: "orchestrator",
		Name:      "resource_index_cache_hit",
		Help:      "The number of times the cache has been hit when retrieving the index",
	}, []string{"type"})

	resourceIndexCacheMiss = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud",
		Subsystem: "orchestrator",
		Name:      "resource_index_cache_miss",
		Help:      "The number of times the cache has been missed when retrieving the index",
	}, []string{"type"})

	resourceBrowseDuration = promauto.NewSummaryVec(prometheus.SummaryOpts{
		Namespace: "ucloud",
		Subsystem: "orchestrator",
		Name:      "resource_browse_duration_seconds",
		Help:      "Summary of the duration (in seconds) it takes to execute a browse, broken down by section",
		Objectives: map[float64]float64{
			0.5:  0.01,
			0.75: 0.01,
			0.95: 0.01,
			0.99: 0.01,
		},
	}, []string{"type", "section"})

	resourceReadDuration = promauto.NewSummaryVec(prometheus.SummaryOpts{
		Namespace: "ucloud",
		Subsystem: "orchestrator",
		Name:      "resource_read_duration_seconds",
		Help:      "Summary of the duration (in seconds) it takes to execute a single resource read, broken down by section",
		Objectives: map[float64]float64{
			0.5:  0.01,
			0.75: 0.01,
			0.95: 0.01,
			0.99: 0.01,
		},
	}, []string{"type", "section"})

	resourceLoadCacheHit = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud",
		Subsystem: "orchestrator",
		Name:      "resource_load_cache_hit",
		Help:      "The number of times the cache has been hit when retrieving a resource",
	}, []string{"type"})

	resourceLoadCacheMiss = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud",
		Subsystem: "orchestrator",
		Name:      "resource_load_cache_miss",
		Help:      "The number of times the cache has been missed when retrieving a resource",
	}, []string{"type"})
)
