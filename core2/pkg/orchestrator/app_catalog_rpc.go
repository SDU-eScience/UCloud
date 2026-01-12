package orchestrator

import (
	"fmt"
	"io"
	"net/http"
	"strconv"
	"sync/atomic"

	"github.com/blevesearch/bleve"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// NOTE(Dan): Normally this stuff would just reside in app_catalog.go but this API has _a lot_ of endpoints so it was
// moved here to make the main file read a bit easier.

func appCatalogInitRpc() {
	orcapi.AppsRetrieveLandingPage.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogRetrieveLandingPageRequest) (orcapi.AppCatalogRetrieveLandingPageResponse, *util.HttpError) {
		return AppCatalogRetrieveLandingPage(info.Actor, request)
	})

	orcapi.AppsRetrieveCategory.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogRetrieveCategoryRequest) (orcapi.ApplicationCategory, *util.HttpError) {
		discovery := AppDiscovery{
			Mode:     request.Discovery.GetOrDefault(orcapi.CatalogDiscoveryModeAll),
			Selected: request.Selected,
		}

		cat, ok := AppCatalogRetrieveCategory(info.Actor, AppCategoryId(request.Id), discovery, AppCatalogIncludeGroups|AppCatalogRequireNonemptyGroups)
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
				request.PlaceTextUnderLogo, title)
			backgroundColor := LightBackground
			mapping := group.Specification.ColorReplacement.Light
			if request.DarkMode {
				backgroundColor = DarkBackground
				mapping = group.Specification.ColorReplacement.Dark
			}

			return AppLogoGenerate(cacheKey, title, logo, request.PlaceTextUnderLogo, backgroundColor, mapping), nil
		} else {
			return nil, util.HttpErr(http.StatusNotFound, "not found")
		}
	})

	orcapi.AppsRetrieveAppLogo.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogRetrieveAppLogoRequest) ([]byte, *util.HttpError) {
		var ok bool
		var logo []byte
		var app orcapi.Application

		// NOTE(Dan): This request is not authenticated, so we will have to return the logo no matter what
		discovery := AppDiscovery{Mode: orcapi.CatalogDiscoveryModeAll}
		app, ok = AppRetrieveNewest(rpc.ActorSystem, request.Name, discovery, 0)
		mapping := map[int]int{}

		if !ok {
			return nil, util.HttpErr(http.StatusNotFound, "not found")
		} else {
			title := app.Metadata.Title

			logoHasText := false
			groupId := AppGroupId(app.Metadata.Group.Metadata.Id)
			if groupId != -1 {
				var group orcapi.ApplicationGroup
				group, logo, ok = AppRetrieveGroup(rpc.ActorSystem, groupId, discovery, 0)
				title = group.Specification.Title
				logoHasText = group.Specification.LogoHasText

				mapping = group.Specification.ColorReplacement.Light
				if request.DarkMode {
					mapping = group.Specification.ColorReplacement.Dark
				}
			}

			if app.Metadata.FlavorName.GetOrDefault("") != "" {
				title += fmt.Sprintf(" (%v)", app.Metadata.FlavorName.Value)
			}

			if len(logo) != 0 && !request.IncludeText {
				title = ""
			}

			if len(logo) != 0 && request.IncludeText && app.Metadata.FlavorName.GetOrDefault("") == "" && logoHasText {
				title = ""
			}

			cacheKey := fmt.Sprintf("%v%v%v%v%v", request.Name, request.DarkMode, request.IncludeText,
				request.PlaceTextUnderLogo, title)
			backgroundColor := LightBackground
			if request.DarkMode {
				backgroundColor = DarkBackground
			}

			return AppLogoGenerate(cacheKey, title, logo, request.PlaceTextUnderLogo, backgroundColor, mapping), nil
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
		if request.AppVersion.Present {
			app, ok := AppRetrieve(info.Actor, request.AppName, request.AppVersion.Value, discovery, 0)
			if ok {
				return app, nil
			} else {
				return orcapi.Application{}, util.HttpErr(http.StatusNotFound, "not found")
			}
		} else {
			app, ok := AppRetrieveNewest(info.Actor, request.AppName, discovery, 0)
			if ok {
				return app, nil
			} else {
				return orcapi.Application{}, util.HttpErr(http.StatusNotFound, "not found")
			}
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
							if a.Metadata.FlavorName.GetOrDefault("") == defaultFlavor {
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
		items := AppCatalogOpenWithRecommendations(info.Actor, request.Files)
		return fndapi.PageV2[orcapi.Application]{Items: items, ItemsPerPage: len(items)}, nil
	})

	orcapi.AppsUpdatePublicFlag.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogUpdatePublicFlagRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioUpdatePublicFlag(request.Name, request.Version, request.Public)
	})

	orcapi.AppsRetrieveAcl.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogRetrieveAclRequest) (orcapi.AppCatalogRetrieveAclResponse, *util.HttpError) {
		list := AppStudioRetrieveAccessList(request.Name)
		var result []orcapi.AppDetailedEntityWithPermission
		for _, item := range list {
			result = append(result, orcapi.AppDetailedEntityWithPermission{
				Entity: orcapi.AppDetailedPermissionEntry{
					User: util.OptStringIfNotEmpty(item.Username),
					Project: util.OptValue(orcapi.AppAccessProjectOrGroupInfo{
						Id:    item.ProjectId,
						Title: "",
					}),
					Group: util.OptValue(orcapi.AppAccessProjectOrGroupInfo{
						Id:    item.Group,
						Title: "",
					}),
				},
				Permission: orcapi.AppAccessRightLaunch,
			})
		}
		return orcapi.AppCatalogRetrieveAclResponse{Entries: util.NonNilSlice(result)}, nil
	})

	orcapi.AppsUpdateAcl.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogUpdateAclRequest) (util.Empty, *util.HttpError) {
		var entitiesToAdd []orcapi.AclEntity
		var entitiesToRemove []orcapi.AclEntity
		for _, item := range request.Changes {
			aclType := orcapi.AclEntityTypeUser
			if item.Entity.Project.Present {
				aclType = orcapi.AclEntityTypeProjectGroup
			}
			e := orcapi.AclEntity{
				Type:      aclType,
				Username:  item.Entity.User.GetOrDefault(""),
				ProjectId: item.Entity.Project.GetOrDefault(""),
				Group:     item.Entity.Group.GetOrDefault(""),
			}
			if item.Revoke {
				entitiesToRemove = append(entitiesToRemove, e)
			} else {
				entitiesToAdd = append(entitiesToAdd, e)
			}
		}
		return util.Empty{}, AppStudioUpdateAcl(request.Name, entitiesToAdd, entitiesToRemove)
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
		return util.Empty{}, AppStudioToggleSpotlight(AppSpotlightId(request.Id), true)
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

	appImportIsDone := atomic.Bool{}
	orcapi.AppsDevImport.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogDevImportRequest) (util.Empty, *util.HttpError) {
		resp, err := http.Get(request.Endpoint)
		if err != nil {
			return util.Empty{}, util.HttpErr(http.StatusBadRequest, "could not contact endpoint")
		}

		data, err := io.ReadAll(resp.Body)
		if err != nil {
			return util.Empty{}, util.HttpErr(http.StatusBadRequest, "could not download data from endpoint")
		}

		// NOTE(Dan): This checksum assumes that the client can be trusted. This is only intended to protect against a
		// sudden compromise of the domain we use to host the assets or some other mitm attack. This should all be
		// fine given that this code is only ever supposed to run locally.
		calculated := util.Sha256(data)
		if calculated != request.Checksum {
			return util.Empty{}, util.HttpErr(http.StatusBadRequest, "unexpected checksum - got: %s, expected: %s",
				calculated, request.Checksum)
		}

		go func() {
			appImportIsDone.Store(false)
			AppIxImportFromZip(data)
			appImportIsDone.Store(true)
		}()
		return util.Empty{}, nil
	})

	orcapi.AppsImportIsDone.Handler(func(info rpc.RequestInfo, request util.Empty) (bool, *util.HttpError) {
		return appImportIsDone.Load(), nil
	})

	orcapi.AppsImportFromFile.Handler(func(info rpc.RequestInfo, request []byte) (util.Empty, *util.HttpError) {
		AppIxImportFromZip(request)
		return util.Empty{}, nil
	})

	orcapi.AppsExport.Handler(func(info rpc.RequestInfo, request util.Empty) ([]byte, *util.HttpError) {
		return AppIxExportToZip(), nil
	})

	orcapi.AppsUpload.Handler(func(info rpc.RequestInfo, request []byte) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioUploadApp(request)
	})

	orcapi.AppsUploadTool.Handler(func(info rpc.RequestInfo, request []byte) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioUploadTool(request)
	})

	orcapi.AppsUploadToolAlias.Handler(func(info rpc.RequestInfo, request []byte) (util.Empty, *util.HttpError) {
		return util.Empty{}, AppStudioUploadTool(request)
	})
}
