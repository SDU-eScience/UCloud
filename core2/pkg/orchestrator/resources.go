package orchestrator

import (
	"fmt"
	"math"
	"net/http"
	"runtime"
	"slices"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// mutex lock order: globals -> index -> resource

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

func (r *resource) ToApi() orcapi.Resource {
	return orcapi.Resource{
		Id:        fmt.Sprint(r.Id),
		CreatedAt: fndapi.Timestamp(r.CreatedAt),
		Owner:     r.Owner,
		Permissions: orcapi.ResourcePermissions{
			Others: r.Acl,
		},
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
)

type resourceTypeGlobal struct {
	Type string

	Resources []*resourceBucket
	Indexes   []*resourceIndexBucket

	Flags resourceTypeFlags

	OnLoad             func(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource)
	OnPersist          func(b *db.Batch, resources *resource)
	OnPersistCommitted func(r *resource)
	Transformer        func(r orcapi.Resource, product util.Option[accapi.ProductReference], extra any, flags orcapi.ResourceFlags) any

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
		resourceLoadIndex(b, typeName, reference)
	}

	return b
}

func InitResources() {
	resourceGlobals.IdAcc.Store(1)
	resourceGlobals.ByType = map[string]*resourceTypeGlobal{}
	resourceGlobals.Providers = map[string]*resourceProvider{}

	if !resourceGlobals.Testing.Enabled {
		db.NewTx0(func(tx *db.Transaction) {
			id, ok := db.Get[struct{ Id int64 }](
				tx,
				`select max(id) as id from provider.resource`,
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
	transformer func(r orcapi.Resource, product util.Option[accapi.ProductReference], extra any, flags orcapi.ResourceFlags) any,
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
	var permissions []orcapi.Permission
	b.Mu.RLock()

	r, ok := b.Resources[id]
	if !ok {
		b.Mu.RUnlock()
		resourceLoad(typeName, id, prefetchHint)
		b.Mu.RLock()

		r, ok = b.Resources[id]
	}

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

				if r.Owner.Project != "" {
					role, isMember := actor.Membership[rpc.ProjectId(r.Owner.Project)]

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

	if flags.FilterCreatedAfter.Present && flags.FilterCreatedAfter.Value.Time().Before(r.CreatedAt) {
		return result, false
	}

	if flags.FilterCreatedBefore.Present && flags.FilterCreatedBefore.Value.Time().After(r.CreatedAt) {
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
		ids := strings.Split(flags.FilterIds.Value, ",")
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
		Permissions: orcapi.ResourcePermissions{
			Myself: myPerms,
		},
		ProviderGeneratedId: r.ProviderId.GetOrDefault(""),
	}

	if flags.IncludeOthers {
		result.Permissions.Others = make([]orcapi.ResourceAclEntry, len(r.Acl))
		copy(result.Permissions.Others, r.Acl)
	}

	if flags.IncludeProduct {
		// TODO
	}

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
			rawResult := g.Transformer(mapped, r.Product, r.Extra, flags)
			result = rawResult.(T)
		}
		b.Mu.RUnlock()
		if ok {
			return result, r.ToApi(), r.Product, nil
		}
	}

	return result, orcapi.Resource{}, util.OptNone[accapi.ProductReference](), util.HttpErr(http.StatusNotFound, "not found")
}

func ResourceBrowse[T any](
	actor rpc.Actor,
	typeName string,
	next util.Option[string],
	itemsPerPage int,
	flags orcapi.ResourceFlags,
	filter func(item T) bool,
) fndapi.PageV2[T] {
	// TODO if providerId filter is present, use providerId index instead

	g := resourceGetGlobals(typeName)
	ref := actor.Username
	if actor.Project.Present {
		ref = string(actor.Project.Value)
	}

	idxBucket := resourceGetAndLoadIndex(typeName, ref)

	idxBucket.Mu.RLock()
	idx := idxBucket.ByOwner[ref]

	maxId := ResourceId(math.MaxInt64)
	initialPrefetchIndex := max(0, len(idx)-500)
	if next.Present {
		rId := ResourceParseId(next.Value)
		nextIdx, _ := slices.BinarySearch(idx, rId)
		initialPrefetchIndex = max(0, nextIdx-500)
	}

	lastPrefetchIndex := min(len(idx), initialPrefetchIndex+500)
	prefetchList := make([]ResourceId, lastPrefetchIndex-initialPrefetchIndex)
	copy(prefetchList, idx[initialPrefetchIndex:lastPrefetchIndex])
	idxBucket.Mu.RUnlock()

	var items []T
	newNext := util.OptNone[string]()
	itemsPerPage = fndapi.ItemsPerPage(itemsPerPage)
	prevId := ResourceId(0)

	for i := len(idx) - 1; i >= 0; i-- {
		id := idx[i]
		if id >= maxId {
			continue
		}

		b := resourceGetBucket(typeName, id)
		resc, ok, perms := resourcesReadEx(actor, typeName, orcapi.PermissionRead, b, id, prefetchList)

		if ok {
			b.Mu.RLock()
			mapped, ok := lResourceApplyFlags(resc, perms, flags)
			if ok {
				item := g.Transformer(mapped, resc.Product, resc.Extra, flags).(T)
				if filter == nil || filter(item) {
					if len(items) >= itemsPerPage {
						newNext.Set(fmt.Sprint(prevId))
						break
					} else {
						prevId = resc.Id
						items = append(items, item)
					}
				}
			}
			b.Mu.RUnlock()
		}
	}

	return fndapi.PageV2[T]{
		Items:        items,
		Next:         newNext,
		ItemsPerPage: itemsPerPage,
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
			panic("resource was not supposed to be filtered here")
		}
		mapped := g.Transformer(apiResc, resc.Product, resc.Extra, orcapi.ResourceFlags{}).(T)

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
		if resc.Owner.Project != "" {
			rescOwnerRef = resc.Owner.Project
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
			// is to cause a brief load from the database to realise that it is in fact not in the store.

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
	// TODO verify all entities in the added section

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

	if flags&resourceCreateAllRead != 0 {
		// TODO modify ACL accordingly
	}

	if flags&resourceCreateAllWrite != 0 {
		// TODO modify ACL accordingly
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
		indexRef := util.OptStringIfNotEmpty(owner.Project).GetOrDefault(owner.CreatedBy)
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
	apiResc, _ := lResourceApplyFlags(r, nil, resourceFlags)
	mapped := g.Transformer(apiResc, r.Product, r.Extra, resourceFlags).(T)

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
	if g.Flags&resourceTypeCreateWithoutAdmin == 0 {
		if actor.Project.Present && actor.Membership[actor.Project.Value].Satisfies(rpc.ProjectRoleAdmin) {
			var t T
			return 0, t, util.HttpErr(http.StatusForbidden, "you need administrator privileges to do this operation")
		}
	}

	owner := orcapi.ResourceOwner{
		CreatedBy: actor.Username,
		Project:   string(actor.Project.Value),
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

func ResourceDelete(actor rpc.Actor, typeName string, id ResourceId) {
	ResourceUpdate[any](
		actor,
		typeName,
		id,
		orcapi.PermissionAdmin,
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
