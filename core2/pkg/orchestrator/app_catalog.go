package orchestrator

import (
	"encoding/binary"
	"fmt"
	"github.com/blevesearch/bleve"
	"golang.org/x/exp/slices"
	"hash/fnv"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Lock order: bucket -> category -> group -> app

func initAppCatalog() {
	appCatalogLoad()

	orcapi.AppsRetrieveLandingPage.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogRetrieveLandingPageRequest) (orcapi.AppCatalogRetrieveLandingPageResponse, *util.HttpError) {
		return AppCatalogRetrieveLandingPage(info.Actor, request)
	})

	orcapi.AppsRetrieveCategory.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogRetrieveCategoryRequest) (orcapi.ApplicationCategory, *util.HttpError) {
		discovery := AppDiscovery{
			Mode:     request.Discovery.GetOrDefault(orcapi.CatalogDiscoveryModeAll),
			Selected: request.Selected,
		}

		cat, ok := AppCatalogRetrieveCategory(info.Actor, AppCategoryId(request.Id), discovery, AppCatalogIncludeGroups)
		if ok {
			return cat, nil
		} else {
			return cat, util.HttpErr(http.StatusNotFound, "not found")
		}
	})

	orcapi.AppsRetrieveCarrouselImage.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogRetrieveCarrouselImageRequest) ([]byte, *util.HttpError) {
		_, images := AppListCarrousel(info.Actor, AppDiscovery{
			Mode: orcapi.CatalogDiscoveryModeAll,
		})

		if request.Index >= 0 && request.Index < len(images) {
			return images[request.Index], nil
		} else {
			return nil, util.HttpErr(http.StatusNotFound, "not found")
		}
	})

	orcapi.AppsRetrieveGroupLogo.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogRetrieveGroupLogoRequest) ([]byte, *util.HttpError) {
		group, logo, ok := AppRetrieveGroup(info.Actor, AppGroupId(request.Id), AppDiscovery{
			Mode: orcapi.CatalogDiscoveryModeAll,
		}, 0)

		if ok {
			title := group.Specification.Title
			if len(logo) > 0 && (group.Specification.LogoHasText || !request.IncludeText) {
				title = ""
			}

			cacheKey := fmt.Sprintf("%v%v%v%v%v", request.Id, request.DarkMode, request.IncludeText,
				request.PlaceTextUnder, title)
			backgroundColor := LightBackground
			mapping := group.Specification.ColorReplacement.Light
			if request.DarkMode {
				backgroundColor = DarkBackground
				mapping = group.Specification.ColorReplacement.Dark
			}

			return AppLogoGenerate(cacheKey, title, logo, false, backgroundColor, mapping), nil
		} else {
			return nil, util.HttpErr(http.StatusNotFound, "not found")
		}
	})

	orcapi.AppsRetrieveAppLogo.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogRetrieveAppLogoRequest) ([]byte, *util.HttpError) {
		var ok bool
		var logo []byte
		var app orcapi.Application

		discovery := AppDiscovery{Mode: orcapi.CatalogDiscoveryModeAll}
		app, ok = AppRetrieveNewest(info.Actor, request.Name, discovery, 0)
		mapping := map[int]int{}

		if !ok {
			return nil, util.HttpErr(http.StatusNotFound, "not found")
		} else {
			title := app.Metadata.Title

			groupId := AppGroupId(app.Metadata.Group.Metadata.Id)
			if groupId != -1 {
				var group orcapi.ApplicationGroup
				group, logo, ok = AppRetrieveGroup(info.Actor, groupId, discovery, 0)
				title = group.Specification.Title

				mapping = group.Specification.ColorReplacement.Light
				if request.DarkMode {
					mapping = group.Specification.ColorReplacement.Dark
				}
			}

			if app.Metadata.FlavorName != "" {
				title += fmt.Sprintf(" (%v)", app.Metadata.FlavorName)
			}

			if len(logo) != 0 && !request.IncludeText {
				title = ""
			}

			cacheKey := fmt.Sprintf("%v%v%v%v%v", request.Name, request.DarkMode, request.IncludeText,
				request.PlaceTextUnder, title)
			backgroundColor := LightBackground
			if request.DarkMode {
				backgroundColor = DarkBackground
			}

			return AppLogoGenerate(cacheKey, title, logo, false, backgroundColor, mapping), nil
		}
	})

	orcapi.AppsFindGroupByApplication.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogFindGroupByApplicationRequest) (orcapi.ApplicationGroup, *util.HttpError) {
		discovery := AppDiscovery{
			Mode:     request.Discovery.GetOrDefault(orcapi.CatalogDiscoveryModeAll),
			Selected: request.Selected,
		}

		var group orcapi.ApplicationGroup
		var app orcapi.Application
		ok := false
		if request.AppVersion.Present {
			app, ok = AppRetrieve(info.Actor, request.AppName, request.AppVersion.Value, discovery, AppCatalogIncludeVersionNumbers)
		} else {
			app, ok = AppRetrieveNewest(info.Actor, request.AppName, discovery, AppCatalogIncludeVersionNumbers)
		}

		if ok {
			groupId := AppGroupId(app.Metadata.Group.Metadata.Id)
			if groupId != -1 {
				group, _, _ = AppRetrieveGroup(info.Actor, groupId, discovery, AppCatalogIncludeApps)
			} else {
				group = orcapi.ApplicationGroup{
					Metadata: orcapi.ApplicationGroupMetadata{
						Id: -1,
					},
					Specification: orcapi.ApplicationGroupSpecification{
						Title:       app.Metadata.Title,
						Description: app.Metadata.Description,
					},
					Status: orcapi.ApplicationGroupStatus{
						Applications: []orcapi.Application{app},
					},
				}
			}

			// NOTE(Dan): The following snippet removes the application retrieved from the group and replaces it with
			// the exact application that was requested (the group contains the newest by default).
			for i := 0; i < len(group.Status.Applications); i++ {
				a := &group.Status.Applications[i]
				if a.Metadata.Name == request.AppName {
					group.Status.Applications = util.RemoveAtIndex(group.Status.Applications, i)
					break
				}
			}

			group.Status.Applications = append(group.Status.Applications, app)
			return group, nil
		}

		return orcapi.ApplicationGroup{}, util.HttpErr(http.StatusNotFound, "not found")
	})

	orcapi.AppsFindByNameAndVersion.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogFindByNameAndVersionRequest) (orcapi.Application, *util.HttpError) {
		discovery := AppDiscovery{
			Mode:     request.Discovery.GetOrDefault(orcapi.CatalogDiscoveryModeAll),
			Selected: request.Selected,
		}
		app, ok := AppRetrieve(info.Actor, request.AppName, request.AppName, discovery, 0)
		if ok {
			return app, nil
		} else {
			return orcapi.Application{}, util.HttpErr(http.StatusNotFound, "not found")
		}
	})

	orcapi.AppsToggleStar.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogToggleStarRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppToggleStar(info.Actor, request.Name)
	})

	orcapi.AppsRetrieveStars.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogRetrieveStarsRequest) (orcapi.AppCatalogRetrieveStarsResponse, *util.HttpError) {
		discovery := AppDiscovery{
			Mode:     request.Discovery.GetOrDefault(orcapi.CatalogDiscoveryModeAll),
			Selected: request.Selected,
		}
		return orcapi.AppCatalogRetrieveStarsResponse{Items: AppRetrieveStars(info.Actor, discovery)}, nil
	})

	orcapi.AppsRetrieveGroup.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogRetrieveGroupRequest) (orcapi.ApplicationGroup, *util.HttpError) {
		discovery := AppDiscovery{
			Mode:     request.Discovery.GetOrDefault(orcapi.CatalogDiscoveryModeAll),
			Selected: request.Selected,
		}

		g, _, ok := AppRetrieveGroup(info.Actor, AppGroupId(request.Id), discovery, AppCatalogIncludeApps)
		if ok {
			return g, nil
		} else {
			return orcapi.ApplicationGroup{}, util.HttpErr(http.StatusNotFound, "not found")
		}
	})

	orcapi.AppsSearch.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogSearchRequest) (fndapi.PageV2[orcapi.Application], *util.HttpError) {
		result := fndapi.PageV2[orcapi.Application]{ItemsPerPage: fndapi.ItemsPerPage(request.ItemsPerPage.GetOrDefault(0))}

		discovery := AppDiscovery{
			Mode:     request.Discovery.GetOrDefault(orcapi.CatalogDiscoveryModeAll),
			Selected: request.Selected,
		}

		fuzz := 2
		titlePrefixQ := bleve.NewPrefixQuery(request.Query)
		titlePrefixQ.SetBoost(5)

		titleQ := bleve.NewFuzzyQuery(request.Query)
		titleQ.SetField("Title")
		titleQ.SetFuzziness(fuzz)
		titleQ.SetBoost(3)

		flavorQ := bleve.NewFuzzyQuery(request.Query)
		flavorQ.SetField("Flavor")
		flavorQ.SetFuzziness(fuzz)
		flavorQ.SetBoost(2)

		descQ := bleve.NewFuzzyQuery(request.Query)
		descQ.SetField("Description")
		descQ.SetFuzziness(fuzz)

		dq := bleve.NewDisjunctionQuery(titleQ, titlePrefixQ, flavorQ, descQ)
		searchRequest := bleve.NewSearchRequest(dq)

		res, err := appIndex.Search(searchRequest)
		if err == nil {
			for _, hit := range res.Hits {
				rawId, _ := strconv.ParseInt(hit.ID, 10, 64)
				g, _, ok := AppRetrieveGroup(info.Actor, AppGroupId(rawId), discovery, AppCatalogIncludeApps)
				if ok && len(g.Status.Applications) > 0 {
					defaultFlavor := g.Specification.DefaultFlavor
					app := g.Status.Applications[0]
					if defaultFlavor != "" {
						for _, a := range g.Status.Applications {
							if a.Metadata.FlavorName == defaultFlavor {
								app = a
								break
							}
						}
					}

					result.Items = append(result.Items, app)
				}
			}
		}

		return result, nil
	})

	orcapi.AppsBrowseOpenWithRecommendations.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogBrowseOpenWithRecommendationsRequest) (fndapi.PageV2[orcapi.Application], *util.HttpError) {
		panic("TODO")
	})

	orcapi.AppsUpdatePublicFlag.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogUpdatePublicFlagRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioUpdatePublicFlag(request.Name, request.Version, request.Public)
	})

	orcapi.AppsRetrieveAcl.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogRetrieveAclRequest) (orcapi.AppCatalogRetrieveAclResponse, *util.HttpError) {
		list := AppStudioRetrieveAccessList(request.Name)
		var result []orcapi.AppDetailedPermissionEntry
		for _, item := range list {
			result = append(result, orcapi.AppDetailedPermissionEntry{
				User: util.OptStringIfNotEmpty(item.Username),
				Project: util.OptMap(util.OptStringIfNotEmpty(item.ProjectId), func(value string) fndapi.Project {
					return fndapi.Project{
						Id: value,
						// TODO
					}
				}),
				Group: util.OptMap(util.OptStringIfNotEmpty(item.Group), func(value string) fndapi.ProjectGroup {
					return fndapi.ProjectGroup{
						Id: value,
						// TODO
					}
				}),
			})
		}
		return orcapi.AppCatalogRetrieveAclResponse{Entries: result}, nil
	})

	orcapi.AppsUpdateAcl.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogUpdateAclRequest) (util.Empty, *util.HttpError) {
		var entities []orcapi.AclEntity
		for _, item := range request.Changes {
			e := orcapi.AclEntity{
				Username:  item.Entity.User.GetOrDefault(""),
				ProjectId: item.Entity.Project.GetOrDefault(""),
				Group:     item.Entity.Group.GetOrDefault(""),
			}
			entities = append(entities, e)
		}
		return util.Empty{}, AppStudioUpdateAcl(request.Name, entities)
	})

	orcapi.AppsUpdateApplicationFlavor.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogUpdateApplicationFlavorRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioUpdateFlavorName(request.ApplicationName, request.FlavorName)
	})

	orcapi.AppsListAllApplications.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.AppCatalogListAllApplicationsResponse, *util.HttpError) {
		return orcapi.AppCatalogListAllApplicationsResponse{Items: AppStudioListAll()}, nil
	})

	orcapi.AppsRetrieveStudioApplication.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogRetrieveStudioApplicationRequest) (orcapi.AppCatalogRetrieveStudioApplicationResponse, *util.HttpError) {
		versions, err := AppStudioRetrieveAllVersions(request.Name)
		return orcapi.AppCatalogRetrieveStudioApplicationResponse{Versions: versions}, err
	})

	orcapi.AppsCreateGroup.Handler(func(info rpc.RequestInfo, request orcapi.ApplicationGroupSpecification) (fndapi.FindByIntId, *util.HttpError) {
		id, err := AppStudioCreateGroup(request)
		return fndapi.FindByIntId{Id: int(id)}, err
	})

	orcapi.AppsDeleteGroup.Handler(func(info rpc.RequestInfo, request fndapi.FindByIntId) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioDeleteGroup(AppGroupId(request.Id))
	})

	orcapi.AppsUpdateGroup.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogUpdateGroupRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioUpdateGroup(request)
	})

	orcapi.AppsAssignApplicationToGroup.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogAssignApplicationToGroupRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioAssignToGroup(request.Name, util.OptMap(request.Group, func(value int) AppGroupId {
			return AppGroupId(value)
		}))
	})

	orcapi.AppsBrowseGroups.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogBrowseGroupsRequest) (fndapi.PageV2[orcapi.ApplicationGroup], *util.HttpError) {
		groups := AppStudioListGroups()
		return fndapi.PageV2[orcapi.ApplicationGroup]{Items: groups, ItemsPerPage: len(groups)}, nil
	})

	orcapi.AppsRetrieveStudioGroup.Handler(func(info rpc.RequestInfo, request fndapi.FindByIntId) (orcapi.ApplicationGroup, *util.HttpError) {
		return AppStudioRetrieveGroup(AppGroupId(request.Id))
	})

	orcapi.AppsAddLogoToGroup.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogAddLogoToGroupRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioUpdateLogo(AppGroupId(request.GroupId), request.LogoBytes)
	})

	orcapi.AppsRemoveLogoFromGroup.Handler(func(info rpc.RequestInfo, request fndapi.FindByIntId) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioUpdateLogo(AppGroupId(request.Id), nil)
	})

	orcapi.AppsCreateCategory.Handler(func(info rpc.RequestInfo, request orcapi.AppCategorySpecification) (fndapi.FindByIntId, *util.HttpError) {
		id, err := AppStudioCreateCategory(request.Title)
		if err != nil {
			return fndapi.FindByIntId{}, err
		} else {
			return fndapi.FindByIntId{int(id)}, nil
		}
	})

	orcapi.AppsAddGroupToCategory.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogAddGroupToCategoryRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioAddGroupToCategory(AppGroupId(request.GroupId), AppCategoryId(request.CategoryId))
	})

	orcapi.AppsRemoveGroupFromCategory.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogRemoveGroupFromCategoryRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioRemoveGroupFromCategory(AppGroupId(request.GroupId), AppCategoryId(request.CategoryId))
	})

	orcapi.AppsAssignPriorityToCategory.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogAssignPriorityToCategoryRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioAssignPriorityToCategory(AppCategoryId(request.Id), request.Priority)
	})

	orcapi.AppsBrowseStudioCategories.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogBrowseStudioCategoriesRequest) (fndapi.PageV2[orcapi.ApplicationCategory], *util.HttpError) {
		result := AppStudioListCategories()
		return fndapi.PageV2[orcapi.ApplicationCategory]{
			ItemsPerPage: len(result),
			Items:        result,
		}, nil
	})

	orcapi.AppsDeleteCategory.Handler(func(info rpc.RequestInfo, request fndapi.FindByIntId) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioDeleteCategory(AppCategoryId(request.Id))
	})

	orcapi.AppsCreateSpotlight.Handler(func(info rpc.RequestInfo, request orcapi.Spotlight) (fndapi.FindByIntId, *util.HttpError) {
		if request.Id.Present {
			return fndapi.FindByIntId{}, util.HttpErr(http.StatusBadRequest, "id must not be present")
		}
		id, err := AppStudioCreateOrUpdateSpotlight(request)
		return fndapi.FindByIntId{Id: int(id)}, err
	})

	orcapi.AppsUpdateSpotlight.Handler(func(info rpc.RequestInfo, request orcapi.Spotlight) (util.Empty, *util.HttpError) {
		if !request.Id.Present {
			return util.Empty{}, util.HttpErr(http.StatusBadRequest, "missing id")
		}
		_, err := AppStudioCreateOrUpdateSpotlight(request)
		return util.Empty{}, err
	})

	orcapi.AppsDeleteSpotlight.Handler(func(info rpc.RequestInfo, request fndapi.FindByIntId) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioDeleteSpotlight(AppSpotlightId(request.Id))
	})

	orcapi.AppsRetrieveSpotlight.Handler(func(info rpc.RequestInfo, request fndapi.FindByIntId) (orcapi.Spotlight, *util.HttpError) {
		return AppStudioRetrieveSpotlight(AppSpotlightId(request.Id))
	})

	orcapi.AppsBrowseSpotlights.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogBrowseSpotlightRequest) (fndapi.PageV2[orcapi.Spotlight], *util.HttpError) {
		result := AppStudioListSpotlights()
		return fndapi.PageV2[orcapi.Spotlight]{
			ItemsPerPage: len(result),
			Items:        result,
		}, nil
	})

	orcapi.AppsActivateSpotlight.Handler(func(info rpc.RequestInfo, request fndapi.FindByIntId) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioActivateSpotlight(AppSpotlightId(request.Id))
	})

	orcapi.AppsUpdateCarrousel.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogUpdateCarrouselRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioUpdateCarrousel(request.NewSlides)
	})

	orcapi.AppsUpdateCarrouselImage.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogUpdateCarrouselImageRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioUpdateCarrouselSlideImage(request.SlideIndex, request.ImageBytes)
	})

	orcapi.AppsUpdateTopPicks.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogUpdateTopPicksRequest) (util.Empty, *util.HttpError) {
		var groupIds []AppGroupId
		for _, pick := range request.NewTopPicks {
			if pick.GroupId.Present {
				groupIds = append(groupIds, AppGroupId(pick.GroupId.Value))
			}
		}
		return util.Empty{}, AppStudioUpdateTopPicks(groupIds)
	})

	orcapi.AppsDevImport.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogDevImportRequest) (util.Empty, *util.HttpError) {
		panic("TODO")
	})

	orcapi.AppsImportFromFile.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
		panic("TODO")
	})

	orcapi.AppsExport.Handler(func(info rpc.RequestInfo, request util.Empty) (util.Empty, *util.HttpError) {
		panic("TODO")
	})
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
	Applications           map[string][]*internalApplication
	ApplicationPermissions map[string][]orcapi.AclEntity
	Tools                  map[string][]*internalTool
	Groups                 map[AppGroupId]*internalAppGroup
	Spotlights             map[AppSpotlightId]*internalSpotlight
	Stars                  map[string]*internalStars
}

type appRecentAdditions struct {
	Mu sync.RWMutex

	RecentlyUpdated []orcapi.NameAndVersion
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

type AppCatalogFlags int

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

	case orcapi.CatalogDiscoveryModeAvailable:
		return true // TODO

	case orcapi.CatalogDiscoveryModeSelected:
		return true // TODO
	}
	return false
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
		if actor.Role == rpc.RoleAdmin {
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
					FlavorName:     app.FlavorName.GetOrDefault(""),
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
			apiApplication.Invocation.Tool.Tool = orcapi.Tool{
				Owner:       "_ucloud",
				CreatedAt:   apiApplication.Metadata.CreatedAt,
				Description: t.Tool,
			}
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
			if ok {
				apiGroup.Status.Applications = append(apiGroup.Status.Applications, app)
			}
		}

		slices.SortFunc(apiGroup.Status.Applications, func(a, b orcapi.Application) int {
			if c := strings.Compare(a.Metadata.FlavorName, b.Metadata.FlavorName); c != 0 {
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

	for _, cat := range categories {
		apiCategory := appCategoryToApi(actor, cat, discovery, flags)
		result = append(result, apiCategory)
	}

	slices.SortFunc(result, func(a, b orcapi.ApplicationCategory) int {
		return strings.Compare(a.Specification.Title, b.Specification.Title)
	})

	// TODO Filter categories with no results.
	// TODO Load groups and apps for filtering purposes if discovery is not all
	return result
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
			group, _, ok := AppRetrieveGroup(actor, g, discovery, flags)
			if ok {
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
	for _, gId := range groupIds {
		group, _, ok := AppRetrieveGroup(actor, gId, discovery, 0)
		if ok {
			// TODO This needs to go through the applications to find the one that works in this discovery mode

			result = append(result, orcapi.TopPick{
				Title:                   group.Specification.Title,
				GroupId:                 util.OptValue[int](int(gId)),
				DefaultApplicationToRun: util.OptStringIfNotEmpty(group.Specification.DefaultFlavor),
				LogoHasText:             group.Specification.LogoHasText,
			})
		}
	}
	return result
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
			apiItem.LinkedGroup.Set(int(id))
			// TODO resolve app

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

	return apiSpotlight, ok
}

type AppDiscovery struct {
	Mode     orcapi.CatalogDiscoveryMode
	Selected util.Option[string]
}

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

	return orcapi.AppCatalogRetrieveLandingPageResponse{
		Carrousel:  carrousel,
		TopPicks:   topPicks,
		Categories: categories,
		Spotlight: util.Option[orcapi.Spotlight]{
			Present: hasSpotlight,
			Value:   spotlight,
		},
		NewApplications:    make([]orcapi.Application, 0),
		RecentlyUpdated:    make([]orcapi.Application, 0),
		AvailableProviders: make([]string, 0),
		Curator:            make([]orcapi.AppCatalogCuratorStatus, 0),
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
	return result
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

	return result
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

	return result, nil
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

	return result
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
	return result
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
			previousGroupId = groupId
			app.Group = groupId
			app.Mu.Unlock()
		}
	}
	b.Mu.RUnlock()

	if previousGroupId.Present {
		g, ok := appRetrieveGroup(previousGroupId.Value)
		if ok {
			g.Items = util.RemoveFirst(g.Items, name)
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
		ok := strings.EqualFold(title, cat.Title)
		cat.Mu.RUnlock()

		if !ok {
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

	cats := &appCatalogGlobals.Categories
	cats.Mu.Lock()
	{
		category.Mu.Lock()
		for _, groupId := range category.Items {
			group, ok := appRetrieveGroup(groupId)
			if ok {
				group.Mu.Lock()
				group.Categories = util.RemoveFirst(group.Categories, id)
				group.Mu.Unlock()
			}
		}

		category.Items = nil
		category.Mu.Unlock()
	}
	delete(cats.Categories, id)
	cats.Mu.Unlock()

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

	appCatalogGlobals.SpotlightIdAcc.Store(int64(id))
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
	return nil
}

func AppStudioCreateTool(tool *orcapi.Tool) *util.HttpError {
	return nil
}
