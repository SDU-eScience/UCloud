package orchestrator

import (
	"encoding/binary"
	"fmt"
	"hash/fnv"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"golang.org/x/exp/slices"
	"gopkg.in/yaml.v3"
	accapi "ucloud.dk/shared/pkg/accounting"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Introduction
// =====================================================================================================================
// This file implements the Application Catalog of UCloud. The main responsibilities of this system is:
//
// - Serving the public application catalog used by end-users
// - Enforcing access control for applications and tools before they are exposed through the API
// - Providing a "Studio" API for operators to create, update and curate catalog content
//
// Data model
// ---------------------------------------------------------------------------------------------------------------------
// The catalog stores several related concepts:
//
// - Applications: concrete runnable units, versioned and backed by a Tool
// - Tools: reusable execution backends referenced by applications
// - Groups: logical collections of related applications (different flavors and backends)
// - Categories: high-level groupings used for navigation and discovery on the landing page
// - Spotlights: curated collections of groups highlighted on the front page
// - Top picks and carrousel items: small, curated views over the same underlying data
// - Stars: per-user favorites, used to build personalized views
//
// Concurrency and sharding
// ---------------------------------------------------------------------------------------------------------------------
// To avoid a single global lock and to keep lookups cheap under concurrent load, the catalog is sharded into a
// fixed number of buckets. Buckets are selected via hashing. Higher-level helpers such as appBucket, appGroupBucket
// and appSpotlightBucket encapsulate the hashing logic.
//
// Each bucket contains the concrete instances (applications, tools, groups, spotlights, stars) and a mutex for
// protecting that bucket only.
//
// Immutability and evolution
// ---------------------------------------------------------------------------------------------------------------------
// Applications are modeled as mostly immutable objects: name, version, created time and invocation/tool bindings do
// not change after creation. A small set of fields are mutable to support gradual evolution of catalog metadata
// without forcing a new application version. Groups and categories are more mutable by design and are heavily used
// by the Studio API for curation.
//
// Discovery, relevance and permissions
// ---------------------------------------------------------------------------------------------------------------------
// All user-facing read operations are expressed in terms of AppDiscovery and AppIsRelevant. Discovery determines
// which providers and products are considered "available" for a given user, while relevance filters out applications
// that cannot actually be executed for the current actor. This separation allows the same catalog to serve multiple
// views: a full operator view, a project-scoped user view and a provider-filtered view. Permissions are layered on
// top through ApplicationPermissions and per-user stars.
//
// Studio vs end-user API
// ---------------------------------------------------------------------------------------------------------------------
// The file intentionally mixes higher-level read endpoints with lower-level Studio endpoints that bypass discovery
// and authorization. Studio is a privileged interface used by operators to:
//
// - Upload applications and tools from a YAML definitions
// - Create and modify groups, categories, spotlights, carrousel slides and top picks
// - Manage ACLs, public flags and group assignments
//
// Only the Studio endpoints are allowed to mutate catalog state; all other functions are read-only views over the
// same in-memory structures. Persistence helpers (appPersist*) bridge the catalog to durable storage and are kept
// out of this file to keep the core catalog logic focused and testable.
//
// --------------------------------------------------------------------------------------------------------------------
// !! MUTEX LOCK ORDER !!
// bucket -> category -> group -> app
// --------------------------------------------------------------------------------------------------------------------

func initAppCatalog() {
	appCatalogLoad()

	// NOTE(Dan): Normally this stuff would just reside in app_catalog.go but this API has _a lot_ of endpoints so it
	// was moved here to make the main file read a bit easier.
	appCatalogInitRpc()
}

type AppCategoryId int64
type AppGroupId int64
type AppSpotlightId int64

var appCatalogGlobals struct {
	Testing struct {
		Enabled bool
	}

	Buckets         []appCatalogBucket
	Categories      appCategories
	Carrousel       appCarrousel
	TopPicks        appTopPicks
	ActiveSpotlight atomic.Int64
	RecentAdditions appRecentAdditions

	CategoryIdAcc  atomic.Int64
	GroupIdAcc     atomic.Int64
	SpotlightIdAcc atomic.Int64
}

type appCatalogBucket struct {
	Mu                     sync.RWMutex
	Applications           map[string][]*internalApplication // sorted with the oldest first and newest last
	ApplicationPermissions map[string][]orcapi.AclEntity
	Tools                  map[string][]*internalTool
	Groups                 map[AppGroupId]*internalAppGroup
	Spotlights             map[AppSpotlightId]*internalSpotlight
	Stars                  map[string]*internalStars
}

type appRecentAdditions struct {
	Mu sync.RWMutex

	RecentlyUpdated []string
	NewApplications []AppGroupId
}

type appTopPicks struct {
	Mu    sync.RWMutex
	Items []AppGroupId
}

type appCarrousel struct {
	Mu sync.RWMutex

	Items []appCarrouselItem
}

type appCarrouselLinkType int

const (
	appCarrouselApplication appCarrouselLinkType = iota
	appCarrouselGroup
	appCarrouselWebPage
)

type appCarrouselItem struct {
	Title    string
	Body     string
	LinkId   string
	LinkType appCarrouselLinkType
	Image    []byte
}

type appCategories struct {
	Mu         sync.RWMutex
	Categories map[AppCategoryId]*internalCategory
}

type internalCategory struct {
	Mu       sync.RWMutex
	Id       AppCategoryId
	Title    string
	Items    []AppGroupId
	Priority int
}

type internalApplication struct {
	Mu sync.RWMutex

	// Immutable metadata
	// -----------------------

	Name      string
	Version   string
	CreatedAt time.Time

	// Immutable specification
	// -----------------------

	Invocation orcapi.ApplicationInvocationDescription
	Tool       orcapi.NameAndVersion

	// Mutable metadata
	// -----------------------

	Title             string
	Description       string
	DocumentationSite util.Option[string]
	FlavorName        util.Option[string]
	Public            bool
	Group             util.Option[AppGroupId]
	ModifiedAt        time.Time
}

type internalTool struct {
	Mu sync.RWMutex

	Name    string
	Version string

	Tool orcapi.ToolDescription
}

type internalAppGroup struct {
	Mu sync.RWMutex

	Title       string
	Description string
	Logo        []byte
	LogoHasText bool

	DefaultName string
	Items       []string // app name

	ColorRemappingLight map[int]int
	ColorRemappingDark  map[int]int

	Categories []AppCategoryId
}

type internalSpotlight struct {
	Mu sync.RWMutex

	Title       string
	Description string
	Items       []AppGroupId
}

type internalStars struct {
	Mu           sync.RWMutex
	Applications map[string]util.Empty
}

// Bucket retrieval utilities
// =====================================================================================================================

func appBucket(id string) *appCatalogBucket {
	h := fnv.New32a()
	_, err := h.Write([]byte(id))
	if err != nil {
		panic("hash fail: " + err.Error())
	}

	return &appCatalogGlobals.Buckets[int(h.Sum32())%len(appCatalogGlobals.Buckets)]
}

func appGroupBucket(id AppGroupId) *appCatalogBucket {
	h := fnv.New32a()
	_, err := h.Write(binary.NativeEndian.AppendUint64(nil, uint64(id)))
	if err != nil {
		panic("hash fail: " + err.Error())
	}

	return &appCatalogGlobals.Buckets[int(h.Sum32())%len(appCatalogGlobals.Buckets)]
}

func appSpotlightBucket(id AppSpotlightId) *appCatalogBucket {
	h := fnv.New32a()
	_, err := h.Write(binary.NativeEndian.AppendUint64(nil, uint64(id)))
	if err != nil {
		panic("hash fail: " + err.Error())
	}

	return &appCatalogGlobals.Buckets[int(h.Sum32())%len(appCatalogGlobals.Buckets)]
}

func appRetrieve(name string, version string) (*internalApplication, bool) {
	var result *internalApplication
	ok := false

	b := appBucket(name)
	b.Mu.RLock()
	allVersions, exists := b.Applications[name]
	if exists {
		for _, v := range allVersions {
			if v.Version == version {
				result = v
				ok = true
				break
			}
		}
	}
	if !ok {
		result = &internalApplication{}
	}
	b.Mu.RUnlock()
	return result, ok
}

func toolRetrieve(name string, version string) (*internalTool, bool) {
	var result *internalTool
	ok := false

	b := appBucket(name)
	b.Mu.RLock()
	allVersions, exists := b.Tools[name]
	if exists {
		for _, v := range allVersions {
			if v.Version == version {
				result = v
				ok = true
				break
			}
		}
	}

	if !ok {
		result = &internalTool{}
	}
	b.Mu.RUnlock()
	return result, ok
}

func appStarsRetrieve(name string) *internalStars {
	b := appBucket(name)
	return util.ReadOrInsertBucket(&b.Mu, b.Stars, name, func() *internalStars {
		return &internalStars{Applications: make(map[string]util.Empty)}
	})
}

func appRetrieveGroup(id AppGroupId) (*internalAppGroup, bool) {
	b := appGroupBucket(id)
	b.Mu.RLock()
	group, ok := b.Groups[id]
	b.Mu.RUnlock()
	return group, ok
}

func appRetrieveCategory(id AppCategoryId) (*internalCategory, bool) {
	c := &appCatalogGlobals.Categories
	c.Mu.RLock()
	cat, ok := c.Categories[id]
	c.Mu.RUnlock()
	return cat, ok
}

func appRetrieveSpotlight(id AppSpotlightId) (*internalSpotlight, bool) {
	b := appSpotlightBucket(id)
	b.Mu.RLock()
	cat, ok := b.Spotlights[id]
	b.Mu.RUnlock()
	return cat, ok
}

// Catalog read operations
// =====================================================================================================================

type AppCatalogFlags uint64

const (
	AppCatalogIncludeGroups AppCatalogFlags = 1 << iota
	AppCatalogIncludeApps
	AppCatalogIncludeVersionNumbers
	AppCatalogIncludeCategories
)

func AppIsRelevant(
	actor rpc.Actor,
	app orcapi.Application,
	discovery AppDiscovery,
) bool {
	switch discovery.Mode {
	case "", orcapi.CatalogDiscoveryModeAll:
		return true

	case orcapi.CatalogDiscoveryModeAvailable, orcapi.CatalogDiscoveryModeSelected:
		supportByProvider := SupportRetrieveProducts[orcapi.JobSupport](jobType).ProductsByProvider
		var availableProviders []string
		if discovery.Mode == orcapi.CatalogDiscoveryModeAvailable {
			availableProviders = appRelevantProvidersForUser(actor.Username, actor.Project)
		} else {
			availableProviders = []string{discovery.Selected.Value}
		}

		anyMatch := false

	outer:
		for _, provider := range availableProviders {
			products := supportByProvider[provider]
			for _, product := range products {
				support := product.Support
				tool := app.Invocation.Tool.Tool.Value.Description
				switch tool.Backend {
				case orcapi.ToolBackendDocker:
					anyMatch = support.Docker.Enabled
				case orcapi.ToolBackendNative:
					anyMatch = support.Native.Enabled
				case orcapi.ToolBackendVirtualMachine:
					anyMatch = support.VirtualMachine.Enabled
				}

				if anyMatch {
					break outer
				}
			}
		}

		return anyMatch
	}
	return false
}

var appRelevantProvidersCache = util.NewCache[util.Tuple2[string, util.Option[rpc.ProjectId]], []string](15 * time.Minute)

func appRelevantProvidersForUser(username string, project util.Option[rpc.ProjectId]) []string {
	key := util.Tuple2[string, util.Option[rpc.ProjectId]]{First: username, Second: project}
	result, _ := appRelevantProvidersCache.Get(key, func() ([]string, error) {
		providers, err := accapi.FindRelevantProviders.Invoke(fndapi.BulkRequestOf(accapi.FindRelevantProvidersRequest{
			Username: username,
			Project: util.OptMap(project, func(value rpc.ProjectId) string {
				return string(value)
			}),
			UseProject:        project.Present,
			FilterProductType: util.OptValue(accapi.ProductTypeCompute),
		}))

		if err != nil || len(providers.Responses) == 0 {
			return nil, err
		}

		return providers.Responses[0].Providers, nil
	})

	return result
}

func AppRetrieveNewest(
	actor rpc.Actor,
	name string,
	discovery AppDiscovery,
	flags AppCatalogFlags,
) (orcapi.Application, bool) {
	b := appBucket(name)
	b.Mu.RLock()
	var versions []string
	{
		allVersions, ok := b.Applications[name]
		if ok {
			for _, v := range allVersions {
				versions = append(versions, v.Version)
			}
		}
	}
	b.Mu.RUnlock()

	for i := len(versions) - 1; i >= 0; i-- {
		app, ok := AppRetrieve(actor, name, versions[i], discovery, flags)
		if ok {
			slices.Reverse(versions)
			app.Versions = versions
			return app, true
		}
	}

	return orcapi.Application{}, false
}

func AppRetrieve(
	actor rpc.Actor,
	name string,
	version string,
	discovery AppDiscovery,
	flags AppCatalogFlags,
) (orcapi.Application, bool) {
	apiApplication := orcapi.Application{}
	groupId := util.OptNone[AppGroupId]()

	app, ok := appRetrieve(name, version)
	if !ok {
		return orcapi.Application{}, false
	}

	var versions []string
	if flags&AppCatalogIncludeVersionNumbers != 0 {
		b := appBucket(name)
		b.Mu.RLock()
		allVersions := b.Applications[name]
		for _, v := range allVersions {
			versions = append(versions, v.Version)
		}
		b.Mu.RUnlock()
		slices.Reverse(versions)
	}

	app.Mu.RLock()
	hasPermissions := app.Public
	if !hasPermissions {
		if actor.Role == rpc.RoleAdmin || actor.Username == rpc.ActorSystem.Username {
			hasPermissions = true
		} else {
			var permissions []orcapi.AclEntity
			{
				app.Mu.RUnlock()

				b := appBucket(name)
				b.Mu.RLock()
				permList := b.ApplicationPermissions[name]
				permissions = make([]orcapi.AclEntity, len(permList))
				copy(permissions, permList)
				b.Mu.RUnlock()

				app.Mu.RLock()
			}

		permLoop:
			for _, p := range permissions {
				switch p.Type {
				case orcapi.AclEntityTypeUser:
					if p.Username == actor.Username {
						hasPermissions = true
						break permLoop
					}
				case orcapi.AclEntityTypeProjectGroup:
					if _, ok := actor.Groups[rpc.GroupId(p.Group)]; ok {
						hasPermissions = true
						break permLoop
					}
				}
			}
		}
	}

	if !hasPermissions {
		ok = false
	} else {
		apiApplication = orcapi.Application{
			WithAppMetadata: orcapi.WithAppMetadata{
				Metadata: orcapi.ApplicationMetadata{
					NameAndVersion: orcapi.NameAndVersion{app.Name, app.Version},
					Authors:        []string{"UCloud"},
					Title:          app.Title,
					Description:    app.Description,
					Website:        app.DocumentationSite.GetOrDefault(""),
					Public:         app.Public,
					FlavorName:     app.FlavorName,
					Group: orcapi.ApplicationGroup{
						Metadata: orcapi.ApplicationGroupMetadata{Id: int(app.Group.GetOrDefault(AppGroupId(-1)))},
					},
					CreatedAt: fndapi.Timestamp(app.CreatedAt),
				},
			},
			WithAppInvocation: orcapi.WithAppInvocation{
				Invocation: app.Invocation,
			},
			Versions: versions,
		}

		groupId = app.Group
	}
	app.Mu.RUnlock()

	if ok {
		toolRef := apiApplication.Invocation.Tool.NameAndVersion
		t, toolOk := toolRetrieve(toolRef.Name, toolRef.Version)
		ok = toolOk

		if ok {
			t.Mu.RLock()
			apiApplication.Invocation.Tool.Tool = util.OptValue(orcapi.Tool{
				Owner:       "_ucloud",
				CreatedAt:   apiApplication.Metadata.CreatedAt,
				Description: t.Tool,
			})
			t.Mu.RUnlock()
		}

		if ok {
			apiApplication.Favorite.Set(false)

			s := appStarsRetrieve(actor.Username)
			s.Mu.RLock()
			_, isStarred := s.Applications[name]
			apiApplication.Favorite.Set(isStarred)
			s.Mu.RUnlock()
		}
	}

	if (flags&AppCatalogIncludeGroups != 0 || flags&AppCatalogIncludeCategories != 0) && groupId.Present {
		// NOTE(Dan): Do not include apps when retrieving groups to avoid infinite recursion
		groupFlags := flags
		groupFlags &= ^AppCatalogIncludeApps

		g, _, groupOk := AppRetrieveGroup(actor, groupId.Value, discovery, groupFlags)
		ok = groupOk

		if ok {
			if flags&AppCatalogIncludeGroups != 0 {
				apiApplication.Metadata.Group = g
			}
		}
	}

	if ok && !AppIsRelevant(actor, apiApplication, discovery) {
		ok = false
	}

	return apiApplication, ok
}

func AppRetrieveGroup(
	actor rpc.Actor,
	id AppGroupId,
	discovery AppDiscovery,
	flags AppCatalogFlags,
) (orcapi.ApplicationGroup, []byte, bool) {
	b := appGroupBucket(id)

	b.Mu.RLock()
	group, ok := b.Groups[id]
	b.Mu.RUnlock()

	if !ok {
		return orcapi.ApplicationGroup{}, nil, false
	}

	group.Mu.RLock()
	var apps []string
	apiGroup := orcapi.ApplicationGroup{
		Metadata: orcapi.ApplicationGroupMetadata{
			Id: int(id),
		},
		Specification: orcapi.ApplicationGroupSpecification{
			Title:         group.Title,
			Description:   group.Description,
			DefaultFlavor: group.DefaultName,
			Categories:    nil, // TODO
			ColorReplacement: orcapi.ColorReplacements{
				Dark:  group.ColorRemappingDark,
				Light: group.ColorRemappingLight,
			},
			LogoHasText: group.LogoHasText,
		},
		Status: orcapi.ApplicationGroupStatus{},
	}
	logo := group.Logo

	if flags&AppCatalogIncludeApps != 0 {
		apps = make([]string, len(group.Items))
		copy(apps, group.Items)
	}
	if flags&AppCatalogIncludeCategories != 0 {
		apiGroup.Specification.Categories = make([]int, len(group.Categories))
		for i, catId := range group.Categories {
			apiGroup.Specification.Categories[i] = int(catId)
		}
	}
	group.Mu.RUnlock()

	if len(apps) > 0 {
		for _, name := range apps {
			// NOTE(Dan): Do not retrieve groups in the later layers to avoid infinite recursion
			groupFlags := flags
			groupFlags &= ^AppCatalogIncludeGroups

			app, ok := AppRetrieveNewest(actor, name, discovery, groupFlags)
			if ok && AppIsRelevant(actor, app, discovery) {
				apiGroup.Status.Applications = append(apiGroup.Status.Applications, app)
			}
		}

		slices.SortFunc(apiGroup.Status.Applications, func(a, b orcapi.Application) int {
			if c := strings.Compare(a.Metadata.FlavorName.Value, b.Metadata.FlavorName.Value); c != 0 {
				return c
			}

			return strings.Compare(a.Metadata.Title, b.Metadata.Title)
		})
	}

	return apiGroup, logo, true
}

func AppCatalogRetrieveCategory(
	actor rpc.Actor,
	id AppCategoryId,
	discovery AppDiscovery,
	flags AppCatalogFlags,
) (orcapi.ApplicationCategory, bool) {
	c := &appCatalogGlobals.Categories
	c.Mu.RLock()
	cat, ok := c.Categories[id]
	c.Mu.RUnlock()

	if !ok {
		return orcapi.ApplicationCategory{}, false
	}

	return appCategoryToApi(actor, cat, discovery, flags), true
}

func AppCatalogListCategories(
	actor rpc.Actor,
	discovery AppDiscovery,
	flags AppCatalogFlags,
) []orcapi.ApplicationCategory {
	var result []orcapi.ApplicationCategory

	var categories []*internalCategory
	c := &appCatalogGlobals.Categories
	c.Mu.RLock()
	for _, cat := range c.Categories {
		categories = append(categories, cat)
	}
	c.Mu.RUnlock()

	categoryFlags := flags
	filter := false
	if discovery.Mode != orcapi.CatalogDiscoveryModeAll {
		filter = true
		categoryFlags |= AppCatalogIncludeApps | AppCatalogIncludeGroups
	}

catLoop:
	for _, cat := range categories {
		apiCategory := appCategoryToApi(actor, cat, discovery, categoryFlags)
		if filter {
			wantApps := flags&AppCatalogIncludeApps != 0
			wantGroups := flags&AppCatalogIncludeGroups != 0

			if len(apiCategory.Status.Groups) == 0 {
				continue catLoop
			}

			for _, group := range apiCategory.Status.Groups {
				if len(group.Status.Applications) == 0 {
					continue catLoop
				}
			}

			if !wantGroups {
				apiCategory.Status.Groups = util.NonNilSlice[orcapi.ApplicationGroup](nil)
			} else if !wantApps {
				for _, group := range apiCategory.Status.Groups {
					group.Status.Applications = util.NonNilSlice[orcapi.Application](nil)
				}
			}
		}
		result = append(result, apiCategory)
	}

	slices.SortFunc(result, func(a, b orcapi.ApplicationCategory) int {
		return strings.Compare(a.Specification.Title, b.Specification.Title)
	})

	return util.NonNilSlice(result)
}

func appCategoryToApi(
	actor rpc.Actor,
	cat *internalCategory,
	discovery AppDiscovery,
	flags AppCatalogFlags,
) orcapi.ApplicationCategory {
	var groups []AppGroupId
	cat.Mu.RLock()
	apiCategory := orcapi.ApplicationCategory{
		Metadata: orcapi.AppCategoryMetadata{
			Id: int(cat.Id),
		},
		Specification: orcapi.AppCategorySpecification{
			Title:       cat.Title,
			Description: util.OptNone[string](),
		},
		Status: orcapi.AppCategoryStatus{},
	}
	if flags&AppCatalogIncludeGroups != 0 {
		groups = make([]AppGroupId, len(cat.Items))
		copy(groups, cat.Items)
	}
	cat.Mu.RUnlock()

	if len(groups) > 0 {
		for _, g := range groups {
			groupFlags := flags
			filter := false
			wantApps := flags&AppCatalogIncludeApps != 0
			if discovery.Mode != orcapi.CatalogDiscoveryModeAll {
				filter = true
				groupFlags |= AppCatalogIncludeApps
			}

			group, _, ok := AppRetrieveGroup(actor, g, discovery, groupFlags)
			if ok && (!filter || len(group.Status.Applications) > 0) {
				if !wantApps {
					group.Status.Applications = util.NonNilSlice[orcapi.Application](nil)
				}
				apiCategory.Status.Groups = append(apiCategory.Status.Groups, group)
			}
		}

		slices.SortFunc(apiCategory.Status.Groups, func(a, b orcapi.ApplicationGroup) int {
			return strings.Compare(a.Specification.Title, b.Specification.Title)
		})
	}
	return apiCategory
}

func AppListTopPicks(actor rpc.Actor, discovery AppDiscovery) []orcapi.TopPick {
	var groupIds []AppGroupId
	{
		picks := &appCatalogGlobals.TopPicks
		picks.Mu.RLock()
		groupIds = make([]AppGroupId, len(picks.Items))
		copy(groupIds, picks.Items)
		picks.Mu.RUnlock()
	}

	return appLoadTopPicks(actor, discovery, groupIds)
}

func appLoadTopPicks(actor rpc.Actor, discovery AppDiscovery, groupIds []AppGroupId) []orcapi.TopPick {
	var result []orcapi.TopPick

	flags := AppCatalogFlags(0)
	filter := false
	if discovery.Mode != orcapi.CatalogDiscoveryModeAll {
		flags |= AppCatalogIncludeApps
		filter = true
	}

	for _, gId := range groupIds {
		group, _, ok := AppRetrieveGroup(actor, gId, discovery, flags)
		if ok && (!filter || len(group.Status.Applications) > 0) {
			group.Status.Applications = util.NonNilSlice[orcapi.Application](nil)

			result = append(result, orcapi.TopPick{
				Title:                   group.Specification.Title,
				GroupId:                 util.OptValue[int](int(gId)),
				DefaultApplicationToRun: util.OptStringIfNotEmpty(group.Specification.DefaultFlavor),
				LogoHasText:             group.Specification.LogoHasText,
			})
		}
	}
	return util.NonNilSlice(result)
}

func AppListCarrousel(actor rpc.Actor, discovery AppDiscovery) ([]orcapi.CarrouselItem, [][]byte) {
	var result []orcapi.CarrouselItem
	var images [][]byte

	carrousel := &appCatalogGlobals.Carrousel
	carrousel.Mu.RLock()
	for _, item := range carrousel.Items {
		apiItem := orcapi.CarrouselItem{
			Title: item.Title,
			Body:  item.Body,
		}

		switch item.LinkType {
		case appCarrouselApplication:
			apiItem.LinkedApplication.Set(item.LinkId)
			apiItem.ResolvedLinkedApp.Set(item.LinkId)

		case appCarrouselGroup:
			id, _ := strconv.ParseInt(item.LinkId, 10, 64)

			group, _, ok := AppRetrieveGroup(actor, AppGroupId(id), discovery, AppCatalogIncludeApps)
			if ok && len(group.Status.Applications) != 0 {
				apiItem.LinkedGroup.Set(int(id))
				apiItem.ResolvedLinkedApp.Set(group.Status.Applications[0].Metadata.Name)
			}

		case appCarrouselWebPage:
			apiItem.LinkedWebPage.Set(item.LinkId)
		}

		result = append(result, apiItem)
		images = append(images, item.Image)
	}
	carrousel.Mu.RUnlock()

	return result, images
}

func AppRetrieveSpotlight(actor rpc.Actor, id AppSpotlightId, discovery AppDiscovery) (orcapi.Spotlight, bool) {
	activeSpotlight := AppSpotlightId(appCatalogGlobals.ActiveSpotlight.Load())

	b := appSpotlightBucket(id)
	b.Mu.RLock()
	spotlight, ok := b.Spotlights[id]
	b.Mu.RUnlock()

	apiSpotlight := orcapi.Spotlight{}

	if ok {
		spotlight.Mu.RLock()

		apiSpotlight = orcapi.Spotlight{
			Title:        spotlight.Title,
			Body:         spotlight.Description,
			Applications: nil,
			Active:       id == activeSpotlight,
			Id:           util.OptValue(int(id)),
		}

		groupIds := make([]AppGroupId, len(spotlight.Items))
		copy(groupIds, spotlight.Items)

		spotlight.Mu.RUnlock()

		apiSpotlight.Applications = appLoadTopPicks(actor, discovery, groupIds)
	}

	if len(apiSpotlight.Applications) == 0 {
		return orcapi.Spotlight{}, false
	}

	return apiSpotlight, ok
}

type AppDiscovery struct {
	Mode     orcapi.CatalogDiscoveryMode
	Selected util.Option[string]
}

var AppDiscoveryAll = AppDiscovery{Mode: orcapi.CatalogDiscoveryModeAll}

func AppCatalogRetrieveLandingPage(
	actor rpc.Actor,
	request orcapi.AppCatalogRetrieveLandingPageRequest,
) (orcapi.AppCatalogRetrieveLandingPageResponse, *util.HttpError) {
	discovery := AppDiscovery{
		Mode:     request.Discovery.GetOrDefault(orcapi.CatalogDiscoveryModeAll),
		Selected: request.Selected,
	}

	categories := AppCatalogListCategories(actor, discovery, 0)
	carrousel, _ := AppListCarrousel(actor, discovery)
	topPicks := AppListTopPicks(actor, discovery)
	spotlight, hasSpotlight := AppRetrieveSpotlight(
		actor,
		AppSpotlightId(appCatalogGlobals.ActiveSpotlight.Load()),
		discovery,
	)

	var newApps []orcapi.Application
	var recentlyUpdated []orcapi.Application
	{
		additions := &appCatalogGlobals.RecentAdditions
		additions.Mu.RLock()

		recentsSeen := map[string]util.Empty{}
		for i := len(additions.RecentlyUpdated) - 1; i >= 0; i-- {
			name := additions.RecentlyUpdated[i]
			if _, seen := recentsSeen[name]; seen {
				continue
			}

			recentsSeen[name] = util.Empty{}

			app, ok := AppRetrieveNewest(actor, name, discovery, AppCatalogIncludeGroups)
			if ok {
				recentlyUpdated = append(recentlyUpdated, app)
				if len(recentlyUpdated) >= 5 {
					break
				}
			}
		}

		for i := len(additions.NewApplications) - 1; i >= 0; i-- {
			groupId := additions.NewApplications[i]
			group, _, ok := AppRetrieveGroup(actor, groupId, discovery, AppCatalogIncludeApps|AppCatalogIncludeCategories)
			if ok {
				apps := group.Status.Applications
				if len(apps) > 0 {
					if group.Specification.DefaultFlavor != "" {
						for _, app := range apps {
							if app.Metadata.Name == group.Specification.DefaultFlavor {
								newApps = append(newApps, app)
								break
							}
						}
					} else {
						newApps = append(newApps, apps[len(apps)-1])
					}

					if len(newApps) >= 5 {
						break
					}
				}
			}
		}

		additions.Mu.RUnlock()
	}

	return orcapi.AppCatalogRetrieveLandingPageResponse{
		Carrousel:  util.NonNilSlice(carrousel),
		TopPicks:   util.NonNilSlice(topPicks),
		Categories: util.NonNilSlice(categories),
		Spotlight: util.Option[orcapi.Spotlight]{
			Present: hasSpotlight,
			Value:   spotlight,
		},
		NewApplications:    util.NonNilSlice(newApps),
		RecentlyUpdated:    util.NonNilSlice(recentlyUpdated),
		AvailableProviders: appRelevantProvidersForUser(actor.Username, actor.Project),
	}, nil
}

func AppRetrieveStars(actor rpc.Actor, discovery AppDiscovery) []orcapi.Application {
	s := appStarsRetrieve(actor.Username)

	var apps []string
	s.Mu.RLock()
	for app := range s.Applications {
		apps = append(apps, app)
	}
	s.Mu.RUnlock()

	var result []orcapi.Application
	for _, appName := range apps {
		app, ok := AppRetrieveNewest(actor, appName, discovery, 0)
		if ok {
			result = append(result, app)
		}
	}
	slices.SortFunc(result, func(a, b orcapi.Application) int {
		return strings.Compare(a.Metadata.Title, b.Metadata.Title)
	})
	return util.NonNilSlice(result)
}

// Catalog write operations (end-user)
// =====================================================================================================================

func AppToggleStar(actor rpc.Actor, name string) *util.HttpError {
	if rpc.RolesEndUser&actor.Role == 0 {
		return util.HttpErr(http.StatusForbidden, "forbidden")
	}

	_, ok := AppRetrieveNewest(actor, name, AppDiscovery{Mode: orcapi.CatalogDiscoveryModeAll}, 0)
	if ok {
		s := appStarsRetrieve(actor.Username)

		s.Mu.Lock()
		_, isStarred := s.Applications[name]
		if isStarred {
			delete(s.Applications, name)
		} else {
			s.Applications[name] = util.Empty{}
		}
		s.Mu.Unlock()

		appPersistStars(actor, s)
		return nil
	} else {
		return util.HttpErr(http.StatusNotFound, "unknown application")
	}
}

// Studio endpoints (read)
// =====================================================================================================================
// An important aspect of all these functions is that _no_ discovery and _no_ authorization is applied given that only
// privileged accounts have access to these endpoints to begin with.

func AppStudioListAll() []orcapi.NameAndVersion {
	var result []orcapi.NameAndVersion

	for i := 0; i < len(appCatalogGlobals.Buckets); i++ {
		b := &appCatalogGlobals.Buckets[i]
		b.Mu.RLock()
		for _, versions := range b.Applications {
			for _, app := range versions {
				result = append(result, orcapi.NameAndVersion{app.Name, app.Version})
			}
		}
		b.Mu.RUnlock()
	}

	slices.SortFunc(result, func(a, b orcapi.NameAndVersion) int {
		return strings.Compare(a.Name, b.Name)
	})

	return util.NonNilSlice(result)
}

func AppStudioListAllTools() []orcapi.NameAndVersion {
	var result []orcapi.NameAndVersion

	for i := 0; i < len(appCatalogGlobals.Buckets); i++ {
		b := &appCatalogGlobals.Buckets[i]
		b.Mu.RLock()
		for _, versions := range b.Tools {
			for _, tool := range versions {
				result = append(result, orcapi.NameAndVersion{tool.Name, tool.Version})
			}
		}
		b.Mu.RUnlock()
	}

	slices.SortFunc(result, func(a, b orcapi.NameAndVersion) int {
		return strings.Compare(a.Name, b.Name)
	})

	return util.NonNilSlice(result)
}

func AppStudioRetrieveAllVersions(name string) ([]orcapi.Application, *util.HttpError) {
	discovery := AppDiscovery{Mode: orcapi.CatalogDiscoveryModeAll}
	newest, ok := AppRetrieveNewest(rpc.ActorSystem, name, discovery,
		AppCatalogIncludeVersionNumbers)

	if !ok {
		return nil, util.HttpErr(http.StatusNotFound, "not found")
	}

	result := []orcapi.Application{newest}
	for _, v := range newest.Versions {
		if v != newest.Metadata.Version {
			app, ok := AppRetrieve(rpc.ActorSystem, name, v, discovery, 0)
			if ok {
				result = append(result, app)
			}
		}
	}

	return util.NonNilSlice(result), nil
}

func AppStudioListGroups() []orcapi.ApplicationGroup {
	discovery := AppDiscovery{Mode: orcapi.CatalogDiscoveryModeAll}
	var result []orcapi.ApplicationGroup
	var groupIds []AppGroupId

	for i := 0; i < len(appCatalogGlobals.Buckets); i++ {
		b := &appCatalogGlobals.Buckets[i]
		b.Mu.RLock()
		for groupId := range b.Groups {
			groupIds = append(groupIds, groupId)
		}
		b.Mu.RUnlock()
	}

	for _, groupId := range groupIds {
		group, _, ok := AppRetrieveGroup(rpc.ActorSystem, groupId, discovery, 0)
		if ok {
			result = append(result, group)
		}
	}

	slices.SortFunc(result, func(a, b orcapi.ApplicationGroup) int {
		return strings.Compare(a.Specification.Title, b.Specification.Title)
	})

	return util.NonNilSlice(result)
}

func AppStudioRetrieveGroup(groupId AppGroupId) (orcapi.ApplicationGroup, *util.HttpError) {
	discovery := AppDiscovery{Mode: orcapi.CatalogDiscoveryModeAll}
	group, _, ok := AppRetrieveGroup(rpc.ActorSystem, groupId, discovery, AppCatalogIncludeApps|AppCatalogIncludeCategories)
	if ok {
		return group, nil
	} else {
		return orcapi.ApplicationGroup{}, util.HttpErr(http.StatusNotFound, "not found")
	}
}

func AppStudioListCategories() []orcapi.ApplicationCategory {
	discovery := AppDiscovery{Mode: orcapi.CatalogDiscoveryModeAll}

	var result []orcapi.ApplicationCategory
	var categories []AppCategoryId

	c := &appCatalogGlobals.Categories
	c.Mu.RLock()
	for _, cat := range c.Categories {
		categories = append(categories, cat.Id)
	}
	c.Mu.RUnlock()

	for _, catId := range categories {
		category, ok := AppCatalogRetrieveCategory(rpc.ActorSystem, catId, discovery, 0)
		if ok {
			result = append(result, category)
		}
	}
	return util.NonNilSlice(result)
}

func AppStudioRetrieveSpotlight(spotlightId AppSpotlightId) (orcapi.Spotlight, *util.HttpError) {
	discovery := AppDiscovery{Mode: orcapi.CatalogDiscoveryModeAll}
	spotlight, ok := AppRetrieveSpotlight(rpc.ActorSystem, spotlightId, discovery)
	if ok {
		return spotlight, nil
	} else {
		return orcapi.Spotlight{}, util.HttpErr(http.StatusNotFound, "not found")
	}
}

func AppStudioListSpotlights() []orcapi.Spotlight {
	discovery := AppDiscovery{Mode: orcapi.CatalogDiscoveryModeAll}

	var spotlightIds []AppSpotlightId

	for i := 0; i < len(appCatalogGlobals.Buckets); i++ {
		b := &appCatalogGlobals.Buckets[i]
		b.Mu.RLock()
		for id := range b.Spotlights {
			spotlightIds = append(spotlightIds, id)
		}
		b.Mu.RUnlock()
	}

	var result []orcapi.Spotlight
	for _, id := range spotlightIds {
		spotlight, ok := AppRetrieveSpotlight(rpc.ActorSystem, id, discovery)
		if ok {
			result = append(result, spotlight)
		}
	}

	return result
}

func AppStudioRetrieveAccessList(appName string) []orcapi.AclEntity {
	b := appBucket(appName)
	b.Mu.RLock()
	var result []orcapi.AclEntity
	perms := b.ApplicationPermissions[appName]
	for _, perm := range perms {
		result = append(result, perm)
	}
	b.Mu.RUnlock()
	return result
}

// Studio endpoints (write)
// =====================================================================================================================

func AppStudioUpdatePublicFlag(name string, version string, isPublic bool) *util.HttpError {
	app, ok := appRetrieve(name, version)
	if !ok {
		return util.HttpErr(http.StatusNotFound, "not found")
	}

	app.Mu.Lock()
	app.Public = isPublic
	app.Mu.Unlock()

	appPersistPublic(app)
	return nil
}

func AppStudioUpdateFlavorName(name string, flavorName util.Option[string]) *util.HttpError {
	b := appBucket(name)
	b.Mu.RLock()
	allVersions, ok := b.Applications[name]
	if ok {
		for _, app := range allVersions {
			app.Mu.Lock()
			app.FlavorName = flavorName
			app.Mu.Unlock()
		}
	}
	b.Mu.RUnlock()

	if !ok {
		return util.HttpErr(http.StatusNotFound, "not found")
	} else {
		appPersistFlavor(name, flavorName)
		return nil
	}
}

func AppStudioUpdateGroup(request orcapi.AppCatalogUpdateGroupRequest) *util.HttpError {
	id := AppGroupId(request.Id)
	b := appGroupBucket(id)

	b.Mu.RLock()
	group, ok := b.Groups[id]
	b.Mu.RUnlock()

	if !ok {
		return util.HttpErr(http.StatusNotFound, "not found")
	}

	group.Mu.Lock()
	if prop := request.NewDefaultFlavor; prop.Present {
		group.DefaultName = prop.Value
	}
	if prop := request.NewTitle; prop.Present {
		group.Title = prop.Value
	}
	if prop := request.NewDescription; prop.Present {
		group.Description = prop.Value
	}
	if prop := request.NewLogoHasText; prop.Present {
		group.LogoHasText = prop.Value
	}
	group.Mu.Unlock()

	appPersistGroupMetadata(id, group)
	return nil
}

func AppStudioAssignToGroup(name string, groupId util.Option[AppGroupId]) *util.HttpError {
	var newGroup *internalAppGroup
	var ok bool

	if groupId.Present {
		newGroup, ok = appRetrieveGroup(groupId.Value)
		if !ok {
			return util.HttpErr(http.StatusNotFound, "unknown group")
		}
	}

	previousGroupId := util.OptNone[AppGroupId]()

	b := appBucket(name)
	b.Mu.RLock()
	allVersions, ok := b.Applications[name]
	if ok {
		for _, app := range allVersions {
			app.Mu.Lock()
			previousGroupId = app.Group
			app.Group = groupId
			app.Mu.Unlock()
		}
	}
	b.Mu.RUnlock()

	if previousGroupId.Present {
		g, ok := appRetrieveGroup(previousGroupId.Value)
		if ok {
			g.Mu.Lock()
			g.Items = util.RemoveFirst(g.Items, name)
			g.Mu.Unlock()
		}
	}

	if newGroup != nil {
		newGroup.Mu.Lock()
		newGroup.Items = util.AppendUnique(newGroup.Items, name)
		newGroup.Mu.Unlock()
	}

	if !ok {
		return util.HttpErr(http.StatusNotFound, "not found")
	} else {
		appPersistUpdateGroupAssignment(name, groupId)
		return nil
	}
}

func AppStudioAddGroupToCategory(groupId AppGroupId, categoryId AppCategoryId) *util.HttpError {
	group, ok := appRetrieveGroup(groupId)
	if !ok {
		return util.HttpErr(http.StatusNotFound, "unknown group")
	}

	category, ok := appRetrieveCategory(categoryId)
	if !ok {
		return util.HttpErr(http.StatusNotFound, "unknown category")
	}

	category.Mu.Lock()
	group.Mu.Lock()
	group.Categories = util.AppendUnique(group.Categories, categoryId)
	category.Items = util.AppendUnique(category.Items, groupId)
	group.Mu.Unlock()
	category.Mu.Unlock()

	appPersistCategoryItems(category)
	return nil
}

func AppStudioRemoveGroupFromCategory(groupId AppGroupId, categoryId AppCategoryId) *util.HttpError {
	group, ok := appRetrieveGroup(groupId)
	if !ok {
		return util.HttpErr(http.StatusNotFound, "unknown group")
	}

	category, ok := appRetrieveCategory(categoryId)
	if !ok {
		return util.HttpErr(http.StatusNotFound, "unknown category")
	}

	category.Mu.Lock()
	group.Mu.Lock()
	group.Categories = util.RemoveFirst(group.Categories, categoryId)
	category.Items = util.RemoveFirst(category.Items, groupId)
	group.Mu.Unlock()
	category.Mu.Unlock()

	appPersistCategoryItems(category)
	return nil
}

func AppStudioCreateCategory(title string) (AppCategoryId, *util.HttpError) {
	var resultCategory *internalCategory
	var result AppCategoryId
	var err *util.HttpError = nil

	cats := &appCatalogGlobals.Categories
	cats.Mu.Lock()
	for _, cat := range cats.Categories {
		cat.Mu.RLock()
		exists := strings.EqualFold(title, cat.Title)
		cat.Mu.RUnlock()

		if exists {
			err = util.HttpErr(http.StatusConflict, "a category with this name already exist")
			break
		}
	}

	if err == nil {
		result = AppCategoryId(appCatalogGlobals.CategoryIdAcc.Add(1))
		resultCategory = &internalCategory{
			Id:       result,
			Title:    title,
			Items:    nil,
			Priority: len(cats.Categories) + 1,
		}
		cats.Categories[result] = resultCategory
	}

	cats.Mu.Unlock()

	if err == nil {
		appPersistCategoryMetadata(resultCategory)
	}

	return result, err
}

func AppStudioAssignPriorityToCategory(id AppCategoryId, newPriority int) *util.HttpError {
	category, ok := appRetrieveCategory(id)
	if !ok {
		return util.HttpErr(http.StatusNotFound, "unknown category")
	}

	category.Mu.Lock()
	category.Priority = newPriority
	category.Mu.Unlock()

	appPersistCategoryMetadata(category)
	return nil
}

func AppStudioDeleteCategory(id AppCategoryId) *util.HttpError {
	category, ok := appRetrieveCategory(id)
	if !ok {
		return util.HttpErr(http.StatusNotFound, "unknown category")
	}

	var groupsToUnlink []AppGroupId
	cats := &appCatalogGlobals.Categories
	cats.Mu.Lock()
	{
		category.Mu.Lock()
		groupsToUnlink = category.Items
		category.Items = nil
		category.Mu.Unlock()
	}
	delete(cats.Categories, id)
	cats.Mu.Unlock()

	for _, groupId := range groupsToUnlink {
		group, ok := appRetrieveGroup(groupId)
		if ok {
			group.Mu.Lock()
			group.Categories = util.RemoveFirst(group.Categories, id)
			group.Mu.Unlock()
		}
	}

	appPersistDeleteCategory(id)
	return nil
}

func AppStudioUpdateLogo(groupId AppGroupId, logo []byte) *util.HttpError {
	group, ok := appRetrieveGroup(groupId)
	if !ok {
		return util.HttpErr(http.StatusNotFound, "unknown group")
	}

	resizedImage := AppLogoValidateAndResize(logo)
	if resizedImage == nil && logo != nil {
		return util.HttpErr(http.StatusBadRequest, "invalid or too large image")
	}

	group.Mu.Lock()
	group.Logo = resizedImage
	title := group.Title
	group.Mu.Unlock()

	AppLogoInvalidate(title)
	appPersistGroupLogo(groupId, group)
	return nil
}

func AppStudioUpdateAcl(appName string, newList []orcapi.AclEntity) *util.HttpError {
	b := appBucket(appName)
	b.Mu.Lock()
	_, ok := b.Applications[appName]
	if ok {
		b.ApplicationPermissions[appName] = newList
	}
	b.Mu.Unlock()

	if ok {
		appPersistAcl(appName, newList)
		return nil
	} else {
		return util.HttpErr(http.StatusNotFound, "unknown application")
	}
}

func AppStudioCreateGroup(spec orcapi.ApplicationGroupSpecification) (AppGroupId, *util.HttpError) {
	if spec.Title == "" {
		return 0, util.HttpErr(http.StatusBadRequest, "missing group title")
	} else if len(spec.Title) > 256 {
		return 0, util.HttpErr(http.StatusBadRequest, "group title is too long")
	} else if len(spec.Description) > 1024 {
		return 0, util.HttpErr(http.StatusBadRequest, "group description is too long")
	}

	id := AppGroupId(appCatalogGlobals.GroupIdAcc.Add(1))
	b := appGroupBucket(id)
	group := &internalAppGroup{
		Title:               spec.Title,
		Description:         spec.Description,
		ColorRemappingLight: spec.ColorReplacement.Light,
		ColorRemappingDark:  spec.ColorReplacement.Dark,
	}

	b.Mu.Lock()
	b.Groups[id] = group
	b.Mu.Unlock()

	appPersistGroupMetadata(id, group)
	appStudioTrackNewGroup(id)
	return id, nil
}

func AppStudioDeleteGroup(groupId AppGroupId) *util.HttpError {
	b := appGroupBucket(groupId)
	b.Mu.Lock()
	group, ok := b.Groups[groupId]
	if ok {
		delete(b.Groups, groupId)
	}
	b.Mu.Unlock()

	if !ok {
		return util.HttpErr(http.StatusNotFound, "unknown group")
	}

	group.Mu.Lock()
	for _, item := range group.Items {
		b = appBucket(item)
		b.Mu.RLock()
		appVersions := b.Applications[item]
		for _, app := range appVersions {
			app.Mu.Lock()
			if app.Group.Present && app.Group.Value == groupId {
				app.Group.Clear()
			}
			app.Mu.Unlock()
		}
		b.Mu.RUnlock()
	}

	appPersistGroupDeletion(groupId)

	group.Items = nil
	group.Mu.Unlock()
	return nil
}

func AppStudioCreateOrUpdateSpotlight(spec orcapi.Spotlight) (AppSpotlightId, *util.HttpError) {
	if spec.Title == "" {
		return 0, util.HttpErr(http.StatusBadRequest, "missing title")
	} else if len(spec.Title) > 256 {
		return 0, util.HttpErr(http.StatusBadRequest, "title is too long")
	} else if len(spec.Body) > 1024 {
		return 0, util.HttpErr(http.StatusBadRequest, "body is too long")
	}

	var groups []AppGroupId
	for _, pick := range spec.Applications {
		if pick.GroupId.Present {
			_, ok := appRetrieveGroup(AppGroupId(pick.GroupId.Value))
			if !ok {
				return 0, util.HttpErr(http.StatusNotFound, "unknown group %v", pick.GroupId.Value)
			} else {
				groups = append(groups, AppGroupId(pick.GroupId.Value))
			}
		}
	}

	var id AppSpotlightId
	var spotlight *internalSpotlight = nil
	ok := true

	if spec.Id.Present {
		id = AppSpotlightId(spec.Id.Value)
		spotlight, ok = appRetrieveSpotlight(id)
		if !ok {
			return 0, util.HttpErr(http.StatusNotFound, "unknown spotlight")
		}
	} else {
		id = AppSpotlightId(appCatalogGlobals.SpotlightIdAcc.Add(1))
		spotlight = &internalSpotlight{}
	}

	spotlight.Mu.Lock()
	spotlight.Title = spec.Title
	spotlight.Description = spec.Body
	spotlight.Items = groups
	spotlight.Mu.Unlock()

	if !spec.Id.Present {
		b := appSpotlightBucket(id)
		b.Mu.Lock()
		b.Spotlights[id] = spotlight
		b.Mu.Unlock()
	}

	appPersistSpotlight(id, spotlight)
	return id, nil
}

func AppStudioDeleteSpotlight(id AppSpotlightId) *util.HttpError {
	b := appSpotlightBucket(id)
	b.Mu.Lock()
	_, ok := b.Spotlights[id]
	if ok {
		delete(b.Spotlights, id)
	}
	b.Mu.Unlock()

	if !ok {
		return util.HttpErr(http.StatusNotFound, "unknown spotlight")
	}

	appCatalogGlobals.ActiveSpotlight.CompareAndSwap(int64(id), -1) // conditionally deactivate the spotlight if the deleted one was active
	appPersistDeleteSpotlight(id)
	return nil
}

func AppStudioActivateSpotlight(id AppSpotlightId) *util.HttpError {
	_, ok := appRetrieveSpotlight(id)
	if !ok {
		return util.HttpErr(http.StatusNotFound, "unknown spotlight")
	}

	appCatalogGlobals.ActiveSpotlight.Store(int64(id))
	appPersistSetActiveSpotlight(id)
	return nil
}

func AppStudioUpdateTopPicks(newTopPicks []AppGroupId) *util.HttpError {
	for _, groupId := range newTopPicks {
		_, ok := appRetrieveGroup(groupId)
		if !ok {
			return util.HttpErr(http.StatusNotFound, "unknown group %v", groupId)
		}
	}

	t := &appCatalogGlobals.TopPicks
	t.Mu.Lock()
	t.Items = newTopPicks
	t.Mu.Unlock()

	appPersistTopPicks(newTopPicks)
	return nil
}

func AppStudioUpdateCarrousel(slides []orcapi.CarrouselItem) *util.HttpError {
	discovery := AppDiscovery{Mode: orcapi.CatalogDiscoveryModeAll}

	var err *util.HttpError

	for _, slide := range slides {
		util.ValidateString(&slide.Title, "title", 0, &err)
		util.ValidateString(&slide.Body, "body", 0, &err)
		util.ValidateString(&slide.ImageCredit, "imageCredit", util.StringValidationAllowEmpty, &err)

		linkCount := 0

		if slide.LinkedWebPage.Present {
			linkCount++
			util.ValidateString(&slide.LinkedWebPage.Value, "linkedWebPage", 0, &err)
		}

		if slide.LinkedApplication.Present {
			linkCount++
			_, ok := AppRetrieveNewest(rpc.ActorSystem, slide.LinkedApplication.Value, discovery, 0)
			if !ok {
				err = util.MergeHttpErr(err, util.HttpErr(http.StatusBadRequest, "linkedApplication is invalid"))
			}
		}

		if slide.LinkedGroup.Present {
			linkCount++
			_, ok := appRetrieveGroup(AppGroupId(slide.LinkedGroup.Value))
			if !ok {
				err = util.MergeHttpErr(err, util.HttpErr(http.StatusBadRequest, "linkedGroup is invalid"))
			}
		}

		if linkCount != 1 {
			err = util.MergeHttpErr(err, util.HttpErr(http.StatusBadRequest, "exactly one linked property must be present"))
		}
	}

	if err != nil {
		return err
	}

	c := &appCatalogGlobals.Carrousel
	c.Mu.Lock()
	{
		if len(c.Items) > len(slides) {
			// Slides were removed
			c.Items = c.Items[:len(slides)]
		} else {
			// Add missing slides (if any)
			for len(c.Items) < len(slides) {
				c.Items = append(c.Items, appCarrouselItem{})
			}
		}

		for i := 0; i < len(c.Items); i++ {
			newSlide := slides[i]
			existing := &c.Items[i]

			existing.Title = newSlide.Title
			existing.Body = newSlide.Body

			if newSlide.LinkedGroup.Present {
				existing.LinkType = appCarrouselGroup
				existing.LinkId = fmt.Sprint(newSlide.LinkedGroup.Value)
			} else if newSlide.LinkedApplication.Present {
				existing.LinkType = appCarrouselApplication
				existing.LinkId = newSlide.LinkedApplication.Value
			} else if newSlide.LinkedWebPage.Present {
				existing.LinkType = appCarrouselWebPage
				existing.LinkId = newSlide.LinkedWebPage.Value
			}
		}
	}
	c.Mu.Unlock()

	appPersistCarrouselSlides()

	return err
}

func AppStudioUpdateCarrouselSlideImage(index int, imageBytes []byte) *util.HttpError {
	if index < 0 {
		return util.HttpErr(http.StatusBadRequest, "bad index supplied")
	}

	resized := ImageResize(imageBytes, 968)
	if resized == nil {
		return util.HttpErr(http.StatusBadRequest, "missing or invalid carrousel slide image")
	}

	var err *util.HttpError

	c := &appCatalogGlobals.Carrousel
	c.Mu.Lock()
	if index >= len(c.Items) {
		err = util.HttpErr(http.StatusBadRequest, "bad index supplied")
	} else {
		item := &c.Items[index]
		item.Image = resized
	}
	c.Mu.Unlock()

	if err == nil {
		appPersistCarrouselSlideImage(index, resized)
	}

	return err
}

func AppStudioCreateApplication(app *orcapi.Application) *util.HttpError {
	// NOTE(Dan): This function assumes that the application has already gone through validation and normalization.
	// It should have, given that this is not the endpoint for uploading YAML from the end-user.

	var err *util.HttpError
	var result *internalApplication

	toolName := app.Invocation.Tool.Name
	toolVersion := app.Invocation.Tool.Version
	_, ok := toolRetrieve(toolName, toolVersion)
	if !ok {
		err = util.HttpErr(http.StatusNotFound, "unknown tool specified in application (%s/%s)", toolName, toolVersion)
	}

	if err == nil {
		b := appBucket(app.Metadata.Name)
		b.Mu.Lock()
		groupId := util.OptNone[AppGroupId]()
		flavorName := app.Metadata.FlavorName
		allVersions := b.Applications[app.Metadata.Name]
		for _, v := range allVersions {
			if v.Group.Present {
				groupId.Set(v.Group.Value)
			}

			if v.FlavorName.Present {
				flavorName.Set(v.FlavorName.Value)
			}

			if v.Version == app.Metadata.Version {
				err = util.HttpErr(http.StatusConflict, "an application with this version already exist")
				break
			}
		}
		if err == nil {
			result = &internalApplication{
				Name:              app.Metadata.Name,
				Version:           app.Metadata.Version,
				CreatedAt:         time.Now(),
				Invocation:        app.Invocation,
				Tool:              app.Invocation.Tool.NameAndVersion,
				Title:             app.Metadata.Title,
				Description:       app.Metadata.Description,
				DocumentationSite: util.OptStringIfNotEmpty(app.Metadata.Website),
				FlavorName:        flavorName,
				Public:            false,
				Group:             groupId,
				ModifiedAt:        time.Now(),
			}

			b.Applications[app.Metadata.Name] = append(b.Applications[app.Metadata.Name], result)
		}
		b.Mu.Unlock()
	}

	if err == nil && result == nil {
		return util.HttpErr(http.StatusInternalServerError, "internal error (result == nil)")
	}

	if err == nil {
		appPersistApplication(result)
		appStudioTrackUpdate(app.Metadata.NameAndVersion)
	}

	return err
}

func AppStudioCreateToolDirect(tool *orcapi.Tool) *util.HttpError {
	return AppStudioCreateTool(&orcapi.ToolReference{
		NameAndVersion: tool.Description.Info,
		Tool:           util.OptValue(*tool),
	})
}

func AppStudioCreateTool(tool *orcapi.ToolReference) *util.HttpError {
	var err *util.HttpError
	var result *internalTool

	b := appBucket(tool.Name)
	b.Mu.Lock()
	allVersions := b.Tools[tool.Name]
	for _, v := range allVersions {
		if v.Version == tool.Version {
			err = util.HttpErr(http.StatusConflict, "a tool with this version already exist")
			break
		}
	}
	if err == nil {
		result = &internalTool{
			Name:    tool.Name,
			Version: tool.Version,
			Tool:    tool.Tool.Value.Description,
		}
		b.Tools[tool.Name] = append(b.Tools[tool.Name], result)
	}
	b.Mu.Unlock()

	if err == nil {
		appToolPersist(result)
	}

	return err
}

func AppStudioUploadApp(data []byte) *util.HttpError {
	var node yaml.Node
	yamlErr := yaml.Unmarshal(data, &node)
	if yamlErr != nil {
		return util.HttpErr(http.StatusBadRequest, "invalid yaml supplied: %s", yamlErr.Error())
	}

	var typeWrapper struct {
		Version string `yaml:"application"`
	}

	_ = node.Decode(&typeWrapper)

	if typeWrapper.Version == "v1" {
		doc := A1Yaml{}
		if yamlErr = node.Decode(&doc); yamlErr != nil {
			return util.HttpErr(http.StatusBadRequest, "invalid yaml supplied: %s", yamlErr.Error())
		}

		app, err := doc.Normalize()
		if err != nil {
			return err
		}

		return AppStudioCreateApplication(&app)
	} else if typeWrapper.Version == "v2" {
		doc := A2Yaml{}
		if yamlErr = node.Decode(&doc); yamlErr != nil {
			return util.HttpErr(http.StatusBadRequest, "invalid yaml supplied: %s", yamlErr.Error())
		}

		app, err := doc.Normalize()
		if err != nil {
			return err
		}

		if t := app.Invocation.Tool; t.Tool.Present {
			err = AppStudioCreateTool(&t)
			if err != nil {
				return err
			}
		}

		return AppStudioCreateApplication(&app)
	} else {
		return util.HttpErr(http.StatusBadRequest, "invalid application version specified, must be either v1 or v2")
	}
}

func AppStudioUploadTool(data []byte) *util.HttpError {
	var tool A1Tool
	yamlErr := yaml.Unmarshal(data, &tool)
	if yamlErr != nil {
		return util.HttpErr(http.StatusBadRequest, "invalid yaml supplied: %s", yamlErr.Error())
	}

	ntool, err := tool.Normalize()
	if err != nil {
		return err
	} else {
		return AppStudioCreateTool(&ntool)
	}
}

func appStudioTrackUpdate(app orcapi.NameAndVersion) {
	additions := &appCatalogGlobals.RecentAdditions

	additions.Mu.Lock()
	additions.RecentlyUpdated = append(additions.RecentlyUpdated, app.Name)

	additions.Mu.Unlock()
}

func appStudioTrackNewGroup(group AppGroupId) {
	additions := &appCatalogGlobals.RecentAdditions

	additions.Mu.Lock()
	additions.NewApplications = append(additions.NewApplications, group)
	additions.Mu.Unlock()
}

func AppCatalogOpenWithRecommendations(actor rpc.Actor, files []string) []orcapi.Application {
	extensions := map[string]util.Empty{}
	for _, file := range files {
		if strings.Contains(file, ".") {
			idx := strings.Index(file, ".")
			extensions[file[idx:]] = util.Empty{}
		} else {
			entry := util.FileName(file)
			extensions[entry] = util.Empty{}

			if strings.HasSuffix(file, "/") {
				extensions[fmt.Sprintf("%v/", entry)] = util.Empty{}
				extensions["/"] = util.Empty{}
			}
		}
	}

	// NOTE(Dan): This is excessive, we should consider caching this or just indexing on the extensions.
	appNames := map[string]util.Empty{}
	for i := 0; i < len(appCatalogGlobals.Buckets); i++ {
		b := &appCatalogGlobals.Buckets[i]
		b.Mu.RLock()
		for _, versions := range b.Applications {
			for _, app := range versions {
				appNames[app.Name] = util.Empty{}
			}
		}
		b.Mu.RUnlock()
	}

	var result []orcapi.Application
	for name := range appNames {
		app, ok := AppRetrieveNewest(actor, name, AppDiscoveryAll, 0)
		if ok {
			for _, ext := range app.Invocation.FileExtensions {
				_, matches := extensions[ext]
				if matches {
					result = append(result, app)
				}
			}
		}
	}

	slices.SortFunc(result, func(a, b orcapi.Application) int {
		return strings.Compare(a.Metadata.Title, b.Metadata.Title)
	})

	return result
}
