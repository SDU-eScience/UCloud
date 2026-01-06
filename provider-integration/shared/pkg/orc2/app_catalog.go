package orchestrators

import (
	"fmt"
	"io"
	"net/http"
	"strconv"

	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Catalog types
// =====================================================================================================================

type CatalogDiscoveryMode string

const (
	CatalogDiscoveryModeAll       CatalogDiscoveryMode = "ALL"
	CatalogDiscoveryModeAvailable CatalogDiscoveryMode = "AVAILABLE"
	CatalogDiscoveryModeSelected  CatalogDiscoveryMode = "SELECTED"
)

type TopPick struct {
	Title                   string              `json:"title"`
	ApplicationName         util.Option[string] `json:"applicationName"`
	GroupId                 util.Option[int]    `json:"groupId"`
	Description             string              `json:"description"`
	DefaultApplicationToRun util.Option[string] `json:"defaultApplicationToRun"`
	LogoHasText             bool                `json:"logoHasText"`
}

type CarrouselItem struct {
	Title             string              `json:"title"`
	Body              string              `json:"body"`
	ImageCredit       string              `json:"imageCredit"`
	LinkedApplication util.Option[string] `json:"linkedApplication"`
	LinkedWebPage     util.Option[string] `json:"linkedWebPage"`
	LinkedGroup       util.Option[int]    `json:"linkedGroup"`

	// if linkedGroup != null this will point to the default app. if linkedApplication != null then it will be equal
	// to linkedApplication
	ResolvedLinkedApp util.Option[string] `json:"resolvedLinkedApp"`
}

type Spotlight struct {
	Title        string           `json:"title"`
	Body         string           `json:"body"`
	Applications []TopPick        `json:"applications"`
	Active       bool             `json:"active"`
	Id           util.Option[int] `json:"id"`
}

type ApplicationCategory struct {
	Metadata      AppCategoryMetadata      `json:"metadata"`
	Specification AppCategorySpecification `json:"specification"`
	Status        AppCategoryStatus        `json:"status"`
}

type AppCategoryMetadata struct {
	Id       int `json:"id"`
	Priority int `json:"priority"`
}

type AppCategorySpecification struct {
	Title       string              `json:"title"`
	Description util.Option[string] `json:"description"`
	Curator     util.Option[string] `json:"curator"`
}

type AppCategoryStatus struct {
	Groups []ApplicationGroup `json:"groups"`
}

type ApplicationFlags struct {
	// If categories are requested, should the groups in the categories be included?
	IncludeGroups bool `json:"includeGroups"`

	// If groups are included, should the applications in the groups be included?
	IncludeApplications bool `json:"includeApplications"`

	// If an application is included, should the star status be included?
	IncludeStars bool `json:"includeStars"`

	// If an application is included, should the invocation be included?
	IncludeInvocation bool `json:"includeInvocation"`

	// If an application is included, should the invocation be included?
	IncludeVersions bool `json:"includeVersions"`
}

type AppPermission struct {
	Entity AppAccessEntity
	Rights AppAccessRight
	Revoke bool
}

type AppAccessRight string

const (
	AppAccessRightLaunch AppAccessRight = "LAUNCH"
)

// Henrik: To match current FE
type AppAccessProjectOrGroupInfo struct {
	Id    string `json:"id"`
	Title string `json:"title"`
}

type AppAccessEntity struct {
	User    util.Option[string] `json:"user"`
	Project util.Option[string] `json:"project"`
	Group   util.Option[string] `json:"group"`
}

type AppDetailedEntityWithPermission struct {
	Entity AppDetailedPermissionEntry `json:"entity"`
}

type AppDetailedPermissionEntry struct {
	User    util.Option[string]                      `json:"user"`
	Project util.Option[AppAccessProjectOrGroupInfo] `json:"project"`
	Group   util.Option[AppAccessProjectOrGroupInfo] `json:"group"`
}

// Core CRUD
// =====================================================================================================================

const appCatalogNamespace = "hpc/apps"
const toolCatalogNamespace = "hpc/tools"

type AppCatalogFindByNameAndVersionRequest struct {
	AppName    string                            `json:"appName"`
	AppVersion util.Option[string]               `json:"appVersion,omitempty"`
	Discovery  util.Option[CatalogDiscoveryMode] `json:"discovery,omitempty"`
	Selected   util.Option[string]               `json:"selected,omitempty"`
}

var AppsFindByNameAndVersion = rpc.Call[AppCatalogFindByNameAndVersionRequest, Application]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionQueryParameters,
	Roles:       rpc.RolesAuthenticated,
	Operation:   "byNameAndVersion",
}

var AppsCreate = rpc.Call[util.Empty, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

type AppCatalogSearchRequest struct {
	Query        string                            `json:"query"`
	ItemsPerPage util.Option[int]                  `json:"itemsPerPage,omitempty"`
	Next         util.Option[string]               `json:"next,omitempty"`
	ItemsToSkip  util.Option[int64]                `json:"itemsToSkip,omitempty"`
	Discovery    util.Option[CatalogDiscoveryMode] `json:"discovery,omitempty"`
	Selected     util.Option[string]               `json:"selected,omitempty"`
}

var AppsSearch = rpc.Call[AppCatalogSearchRequest, fnd.PageV2[Application]]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionSearch,
	Roles:       rpc.RolesEndUser,
}

type AppCatalogBrowseOpenWithRecommendationsRequest struct {
	Files        []string            `json:"files"`
	ItemsPerPage util.Option[int]    `json:"itemsPerPage,omitempty"`
	Next         util.Option[string] `json:"next,omitempty"`
	ItemsToSkip  util.Option[int64]  `json:"itemsToSkip,omitempty"`
}

var AppsBrowseOpenWithRecommendations = rpc.Call[AppCatalogBrowseOpenWithRecommendationsRequest, fnd.PageV2[Application]]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "openWith",
}

type AppCatalogToggleStarRequest struct {
	Name string `json:"name"`
}

var AppsToggleStar = rpc.Call[AppCatalogToggleStarRequest, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "toggleStar",
}

type AppCatalogRetrieveStarsRequest struct {
	Discovery util.Option[CatalogDiscoveryMode] `json:"discovery,omitempty"`
	Selected  util.Option[string]               `json:"selected,omitempty"`
}

type AppCatalogRetrieveStarsResponse struct {
	Items []Application `json:"items"`
}

var AppsRetrieveStars = rpc.Call[AppCatalogRetrieveStarsRequest, AppCatalogRetrieveStarsResponse]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "stars",
}

type AppCatalogUpdatePublicFlagRequest struct {
	Name    string `json:"name"`
	Version string `json:"version"`
	Public  bool   `json:"public"`
}

var AppsUpdatePublicFlag = rpc.Call[AppCatalogUpdatePublicFlagRequest, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updatePublicFlag",
}

type AppCatalogRetrieveAclRequest struct {
	Name string `json:"name"`
}

type AppCatalogRetrieveAclResponse struct {
	Entries []AppDetailedEntityWithPermission `json:"entries"`
}

var AppsRetrieveAcl = rpc.Call[AppCatalogRetrieveAclRequest, AppCatalogRetrieveAclResponse]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "acl",
}

type AppCatalogUpdateAclRequest struct {
	Name    string          `json:"name"`
	Changes []AppPermission `json:"changes"`
}

var AppsUpdateAcl = rpc.Call[AppCatalogUpdateAclRequest, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateAcl",
}

type AppCatalogUpdateApplicationFlavorRequest struct {
	ApplicationName string              `json:"applicationName"`
	FlavorName      util.Option[string] `json:"flavorName,omitempty"`
}

var AppsUpdateApplicationFlavor = rpc.Call[AppCatalogUpdateApplicationFlavorRequest, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateApplicationFlavor",
}

type AppCatalogFindGroupByApplicationRequest struct {
	AppName    string                            `json:"appName"`
	AppVersion util.Option[string]               `json:"appVersion,omitempty"`
	Flags      ApplicationFlags                  `json:"flags"`
	Discovery  util.Option[CatalogDiscoveryMode] `json:"discovery,omitempty"`
	Selected   util.Option[string]               `json:"selected,omitempty"`
}

var AppsFindGroupByApplication = rpc.Call[AppCatalogFindGroupByApplicationRequest, ApplicationGroup]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser | rpc.RolesProvider,
	Operation:   "findGroupByApplication",
}

var AppsUpload = rpc.Call[[]byte, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesAdmin,
	Operation:   "upload",

	CustomPath:   "/api/" + appCatalogNamespace + "/upload",
	CustomMethod: http.MethodPut,

	CustomClientHandler: func(self *rpc.Call[[]byte, util.Empty], client *rpc.Client, request []byte) (util.Empty, *util.HttpError) {
		panic("Client not implemented")
	},

	CustomServerParser: func(w http.ResponseWriter, r *http.Request) ([]byte, *util.HttpError) {
		data, err := io.ReadAll(r.Body)
		if err != nil {
			return nil, util.HttpErr(http.StatusBadRequest, "Bad request")
		} else {
			return data, nil
		}
	},
}

var AppsUploadTool = rpc.Call[[]byte, util.Empty]{
	BaseContext: toolCatalogNamespace,
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesAdmin,
	Operation:   "upload",

	CustomPath:   "/api/hpc/tools/upload",
	CustomMethod: http.MethodPut,

	CustomClientHandler: func(self *rpc.Call[[]byte, util.Empty], client *rpc.Client, request []byte) (util.Empty, *util.HttpError) {
		panic("Client not implemented")
	},

	CustomServerParser: func(w http.ResponseWriter, r *http.Request) ([]byte, *util.HttpError) {
		data, err := io.ReadAll(r.Body)
		if err != nil {
			return nil, util.HttpErr(http.StatusBadRequest, "Bad request")
		} else {
			return data, nil
		}
	},
}

var AppsUploadToolAlias = rpc.Call[[]byte, util.Empty]{
	BaseContext: toolCatalogNamespace + "alias",
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesAdmin,
	Operation:   "upload",

	CustomPath:   "/api/hpc/tools",
	CustomMethod: http.MethodPut,

	CustomClientHandler: func(self *rpc.Call[[]byte, util.Empty], client *rpc.Client, request []byte) (util.Empty, *util.HttpError) {
		panic("Client not implemented")
	},

	CustomServerParser: func(w http.ResponseWriter, r *http.Request) ([]byte, *util.HttpError) {
		data, err := io.ReadAll(r.Body)
		if err != nil {
			return nil, util.HttpErr(http.StatusBadRequest, "Bad request")
		} else {
			return data, nil
		}
	},
}

// Studio endpoints
// =====================================================================================================================

type AppCatalogListAllApplicationsResponse struct {
	Items []NameAndVersion `json:"items"`
}

var AppsListAllApplications = rpc.Call[util.Empty, AppCatalogListAllApplicationsResponse]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "allApplications",
}

type AppCatalogRetrieveStudioApplicationRequest struct {
	Name string `json:"name"`
}

type AppCatalogRetrieveStudioApplicationResponse struct {
	Versions []Application `json:"versions"`
}

var AppsRetrieveStudioApplication = rpc.Call[AppCatalogRetrieveStudioApplicationRequest, AppCatalogRetrieveStudioApplicationResponse]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "studioApplication",
}

// Group management
// =====================================================================================================================

var AppsCreateGroup = rpc.Call[ApplicationGroupSpecification, fnd.FindByIntId]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "createGroup",
}

var AppsDeleteGroup = rpc.Call[fnd.FindByIntId, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "deleteGroup",
}

type AppCatalogUpdateGroupRequest struct {
	Id               int                 `json:"id"`
	NewTitle         util.Option[string] `json:"newTitle,omitempty"`
	NewDefaultFlavor util.Option[string] `json:"newDefaultFlavor,omitempty"`
	NewDescription   util.Option[string] `json:"newDescription,omitempty"`
	NewLogoHasText   util.Option[bool]   `json:"newLogoHasText,omitempty"`
}

var AppsUpdateGroup = rpc.Call[AppCatalogUpdateGroupRequest, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateGroup",
}

type AppCatalogAssignApplicationToGroupRequest struct {
	Name  string           `json:"name"`
	Group util.Option[int] `json:"group"`
}

var AppsAssignApplicationToGroup = rpc.Call[AppCatalogAssignApplicationToGroupRequest, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "assignApplicationToGroup",
}

type AppCatalogBrowseGroupsRequest struct {
	ItemsPerPage util.Option[int]    `json:"itemsPerPage,omitempty"`
	Next         util.Option[string] `json:"next,omitempty"`
	ItemsToSkip  util.Option[int64]  `json:"itemsToSkip,omitempty"`
	Curator      util.Option[string] `json:"curator,omitempty"`
}

var AppsBrowseGroups = rpc.Call[AppCatalogBrowseGroupsRequest, fnd.PageV2[ApplicationGroup]]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
	Operation:   "groups",
}

type AppCatalogRetrieveGroupRequest struct {
	Id        int64                             `json:"id"`
	Discovery util.Option[CatalogDiscoveryMode] `json:"discovery,omitempty"`
	Selected  util.Option[string]               `json:"selected,omitempty"`
}

var AppsRetrieveGroup = rpc.Call[AppCatalogRetrieveGroupRequest, ApplicationGroup]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "groups",
}

var AppsRetrieveStudioGroup = rpc.Call[fnd.FindByIntId, ApplicationGroup]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "studioGroups",
}

type AppCatalogAddLogoToGroupRequest struct {
	GroupId   int `json:"groupId"`
	LogoBytes []byte
}

var AppsAddLogoToGroup = rpc.Call[AppCatalogAddLogoToGroupRequest, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesEndUser,
	Operation:   "uploadLogo",

	CustomMethod: http.MethodPost,
	CustomPath:   fmt.Sprintf("/api/%s/uploadLogo", appCatalogNamespace),
	CustomServerParser: func(w http.ResponseWriter, r *http.Request) (AppCatalogAddLogoToGroupRequest, *util.HttpError) {
		uploadName := util.Base64DecodeToString(r.Header.Get("upload-name"))
		groupId, err := strconv.ParseInt(uploadName, 10, 64)
		if uploadName == "" || err != nil {
			return AppCatalogAddLogoToGroupRequest{}, util.HttpErr(http.StatusBadRequest, "missing/invalid group id")
		}

		reader := io.LimitReader(r.Body, 1024*1024*4)
		logoBytes, err := io.ReadAll(reader)
		if err != nil {
			return AppCatalogAddLogoToGroupRequest{}, util.HttpErr(http.StatusBadRequest, "malformed request")
		}

		return AppCatalogAddLogoToGroupRequest{GroupId: int(groupId), LogoBytes: logoBytes}, nil
	},

	CustomClientHandler: func(self *rpc.Call[AppCatalogAddLogoToGroupRequest, util.Empty], client *rpc.Client, request AppCatalogAddLogoToGroupRequest) (util.Empty, *util.HttpError) {
		panic("client not implemented")
	},
}

var AppsRemoveLogoFromGroup = rpc.Call[fnd.FindByIntId, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "removeLogoFromGroup",
}

type AppCatalogRetrieveGroupLogoRequest struct {
	Id                 int  `json:"id"`
	DarkMode           bool `json:"darkMode"`
	IncludeText        bool `json:"includeText"`
	PlaceTextUnderLogo bool `json:"placeTextUnderLogo"`
}

var AppsRetrieveGroupLogo = rpc.Call[AppCatalogRetrieveGroupLogoRequest, []byte]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesPublic,
	Operation:   "groupLogo",
	CustomServerProducer: func(response []byte, err *util.HttpError, w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "image/png")
		w.Write(response)
	},
}

type AppCatalogRetrieveAppLogoRequest struct {
	Name               string `json:"name"`
	DarkMode           bool   `json:"darkMode"`
	IncludeText        bool   `json:"includeText"`
	PlaceTextUnderLogo bool   `json:"placeTextUnderLogo"`
}

var AppsRetrieveAppLogo = rpc.Call[AppCatalogRetrieveAppLogoRequest, []byte]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesPublic,
	Operation:   "appLogo",
	CustomServerProducer: func(response []byte, err *util.HttpError, w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "image/png")
		w.Write(response)
	},
}

// Category management
// =====================================================================================================================

var AppsCreateCategory = rpc.Call[AppCategorySpecification, fnd.FindByIntId]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "createCategory",
}

type AppCatalogAddGroupToCategoryRequest struct {
	GroupId    int `json:"groupId"`
	CategoryId int `json:"categoryId"`
}

var AppsAddGroupToCategory = rpc.Call[AppCatalogAddGroupToCategoryRequest, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "addGroupToCategory",
}

type AppCatalogRemoveGroupFromCategoryRequest struct {
	GroupId    int `json:"groupId"`
	CategoryId int `json:"categoryId"`
}

var AppsRemoveGroupFromCategory = rpc.Call[AppCatalogRemoveGroupFromCategoryRequest, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "removeGroupFromCategory",
}

type AppCatalogAssignPriorityToCategoryRequest struct {
	Id       int `json:"id"`
	Priority int `json:"priority"`
}

var AppsAssignPriorityToCategory = rpc.Call[AppCatalogAssignPriorityToCategoryRequest, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "assignPriorityToCategory",
}

type AppCatalogBrowseStudioCategoriesRequest struct {
	ItemsPerPage util.Option[int]    `json:"itemsPerPage,omitempty"`
	Next         util.Option[string] `json:"next,omitempty"`
	ItemsToSkip  util.Option[int64]  `json:"itemsToSkip,omitempty"`
}

var AppsBrowseStudioCategories = rpc.Call[AppCatalogBrowseStudioCategoriesRequest, fnd.PageV2[ApplicationCategory]]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser | rpc.RolesService,
	Operation:   "categories",
}

type AppCatalogRetrieveCategoryRequest struct {
	Id        int                               `json:"id"`
	Discovery util.Option[CatalogDiscoveryMode] `json:"discovery,omitempty"`
	Selected  util.Option[string]               `json:"selected,omitempty"`
}

var AppsRetrieveCategory = rpc.Call[AppCatalogRetrieveCategoryRequest, ApplicationCategory]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "category",
}

var AppsDeleteCategory = rpc.Call[fnd.FindByIntId, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "deleteCategory",
}

// Landing page & images
// =====================================================================================================================

type AppCatalogRetrieveLandingPageRequest struct {
	Discovery util.Option[CatalogDiscoveryMode] `json:"discovery,omitempty"`
	Selected  util.Option[string]               `json:"selected,omitempty"`
}

type AppCatalogRetrieveLandingPageResponse struct {
	Carrousel          []CarrouselItem        `json:"carrousel"`
	TopPicks           []TopPick              `json:"topPicks"`
	Categories         []ApplicationCategory  `json:"categories"`
	Spotlight          util.Option[Spotlight] `json:"spotlight,omitempty"`
	NewApplications    []Application          `json:"newApplications"`
	RecentlyUpdated    []Application          `json:"recentlyUpdated"`
	AvailableProviders []string               `json:"availableProviders"`

	Curator []AppCatalogCuratorStatus `json:"curator"` // deprecated
}

type AppCatalogCuratorStatus struct {
	ProjectId        string `json:"projectId"`
	CanManageCatalog bool   `json:"canManageCatalog"`
	MandatedPrefix   string `json:"mandatedPrefix"`
}

var AppsRetrieveLandingPage = rpc.Call[AppCatalogRetrieveLandingPageRequest, AppCatalogRetrieveLandingPageResponse]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "landingPage",
}

type AppCatalogRetrieveCarrouselImageRequest struct {
	Index      int    `json:"index"`
	SlideTitle string `json:"slideTitle"`
}

var AppsRetrieveCarrouselImage = rpc.Call[AppCatalogRetrieveCarrouselImageRequest, []byte]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesPublic,
	Operation:   "carrouselImage",
	CustomServerProducer: func(response []byte, err *util.HttpError, w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "image/png")
		w.Write(response)
	},
}

// Spotlight management
// =====================================================================================================================

var AppsCreateSpotlight = rpc.Call[Spotlight, fnd.FindByIntId]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "createSpotlight",
}

var AppsUpdateSpotlight = rpc.Call[Spotlight, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateSpotlight",
}

var AppsDeleteSpotlight = rpc.Call[fnd.FindByIntId, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "deleteSpotlight",
}

var AppsRetrieveSpotlight = rpc.Call[fnd.FindByIntId, Spotlight]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "spotlight",
}

type AppCatalogBrowseSpotlightRequest struct {
	ItemsPerPage util.Option[int]    `json:"itemsPerPage,omitempty"`
	Next         util.Option[string] `json:"next,omitempty"`
	ItemsToSkip  util.Option[int64]  `json:"itemsToSkip,omitempty"`
}

var AppsBrowseSpotlights = rpc.Call[AppCatalogBrowseSpotlightRequest, fnd.PageV2[Spotlight]]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
	Operation:   "spotlight",
}

var AppsActivateSpotlight = rpc.Call[fnd.FindByIntId, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "activateSpotlight",
}

// Carrousel & top-picks
// =====================================================================================================================

type AppCatalogUpdateCarrouselRequest struct {
	NewSlides []CarrouselItem `json:"newSlides"`
}

var AppsUpdateCarrousel = rpc.Call[AppCatalogUpdateCarrouselRequest, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateCarrousel",
}

type AppCatalogUpdateCarrouselImageRequest struct {
	SlideIndex int `json:"slideIndex"`
	ImageBytes []byte
}

var AppsUpdateCarrouselImage = rpc.Call[AppCatalogUpdateCarrouselImageRequest, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateCarrouselImage",

	CustomMethod: http.MethodPost,
	CustomPath:   fmt.Sprintf("/api/%s/updateCarrouselImage", appCatalogNamespace),
	CustomServerParser: func(w http.ResponseWriter, r *http.Request) (AppCatalogUpdateCarrouselImageRequest, *util.HttpError) {
		uploadName := util.Base64DecodeToString(r.Header.Get("slide-index"))
		slideIndex, err := strconv.ParseInt(uploadName, 10, 64)
		if uploadName == "" || err != nil {
			return AppCatalogUpdateCarrouselImageRequest{}, util.HttpErr(http.StatusBadRequest, "missing/invalid group id")
		}

		reader := io.LimitReader(r.Body, 1024*1024*4)
		imageBytes, err := io.ReadAll(reader)
		if err != nil {
			return AppCatalogUpdateCarrouselImageRequest{}, util.HttpErr(http.StatusBadRequest, "malformed request")
		}

		return AppCatalogUpdateCarrouselImageRequest{SlideIndex: int(slideIndex), ImageBytes: imageBytes}, nil
	},

	CustomClientHandler: func(
		self *rpc.Call[AppCatalogUpdateCarrouselImageRequest, util.Empty],
		client *rpc.Client,
		request AppCatalogUpdateCarrouselImageRequest,
	) (util.Empty, *util.HttpError) {
		panic("client not implemented")
	},
}

type AppCatalogUpdateTopPicksRequest struct {
	NewTopPicks []TopPick `json:"newTopPicks"`
}

var AppsUpdateTopPicks = rpc.Call[AppCatalogUpdateTopPicksRequest, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateTopPicks",
}

// Import / export
// =====================================================================================================================

type AppCatalogDevImportRequest struct {
	Endpoint string `json:"endpoint"`
	Checksum string `json:"checksum"`
}

var AppsDevImport = rpc.Call[AppCatalogDevImportRequest, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "devImport",
}

var AppsImportIsDone = rpc.Call[util.Empty, bool]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesService,
	Operation:   "importIsDone",
}

var AppsImportFromFile = rpc.Call[[]byte, util.Empty]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesPrivileged,
	Operation:   "importFromFile",

	CustomPath:   "/api/" + appCatalogNamespace + "/importFromFile",
	CustomMethod: http.MethodPost,

	CustomClientHandler: func(self *rpc.Call[[]byte, util.Empty], client *rpc.Client, request []byte) (util.Empty, *util.HttpError) {
		panic("Client not implemented")
	},

	CustomServerParser: func(w http.ResponseWriter, r *http.Request) ([]byte, *util.HttpError) {
		data, err := io.ReadAll(r.Body)
		if err != nil {
			return nil, util.HttpErr(http.StatusBadRequest, "corrupt payload received")
		}

		return data, nil
	},
}

var AppsExport = rpc.Call[util.Empty, []byte]{
	BaseContext: appCatalogNamespace,
	Convention:  rpc.ConventionCustom,
	Roles:       rpc.RolesPrivileged,
	Operation:   "export",

	CustomMethod: http.MethodPost,
	CustomPath:   fmt.Sprintf("/api/%s/export", appCatalogNamespace),
	CustomServerParser: func(w http.ResponseWriter, r *http.Request) (util.Empty, *util.HttpError) {
		return util.Empty{}, nil
	},
	CustomServerProducer: func(response []byte, err *util.HttpError, w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/zip")
		if err != nil {
			w.WriteHeader(err.StatusCode)
		} else {
			w.WriteHeader(http.StatusOK)
		}
		_, _ = w.Write(response)
	},
	CustomClientHandler: func(self *rpc.Call[util.Empty, []byte], client *rpc.Client, request util.Empty) ([]byte, *util.HttpError) {
		panic("client not implemented")
	},
}
