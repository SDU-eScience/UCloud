package orchestrator

import (
	"context"
	"database/sql"
	"encoding/json"
	"net/http"
	"runtime"
	"slices"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
	"unicode"
	"unicode/utf16"
	"unicode/utf8"

	"gopkg.in/yaml.v3"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Custom applications
// =====================================================================================================================
// This file implements the workspace-owned application catalog. Custom applications let users run their own container
// images through the normal UCloud application and job APIs without adding user content to the managed catalog.
// Managed applications remain global and operator-curated. Custom applications belong to one personal or project
// workspace and use category ACLs, publication state, and provider discovery to determine who can use them.
//
// Data model and namespaces
// ---------------------------------------------------------------------------------------------------------------------
// A custom application references one custom group resource and one custom category resource. These resources exist
// before application creation and are stored separately from managed catalog objects. This separation prevents user
// actions from changing managed metadata or membership. Custom groups and categories are always workspace-owned
// objects.
//
// Custom group and category IDs are negative in the API and positive in the database. Managed IDs remain positive.
// This sign convention lets existing catalog responses refer to both kinds without introducing another ID type.
// Application names use the reserved `custom-` prefix, which prevents collisions with managed applications. A custom
// application is unique by workspace, name, version, and provider.
//
// Access model
// ---------------------------------------------------------------------------------------------------------------------
// Categories are the only access boundary. Project groups can receive `READ` or `EDIT`; `EDIT` implies `READ`.
// Project administrators receive implicit `ADMIN`, and personal workspace owners act as administrators. Groups do not
// have ACLs because the same group can be used with several categories. A non-administrator can see a group only when
// at least one application in that group remains visible after category and publication checks.
//
// Category access is evaluated before publication. An unpublished application is visible only to its creator and
// workspace administrators, and both still need category access. Other members need category `READ` and a published
// application. Removing category access therefore removes access even from the application creator. Personal
// applications cannot be published.
//
// Creation and provider validation
// ---------------------------------------------------------------------------------------------------------------------
// Creation accepts normalized version 2 application YAML plus provider, publication, flavor, group, and category
// fields. Only container software is supported. UCX, modules, documentation, and extensions are rejected because
// custom applications do not have a separate tool abstraction and must remain portable through the container path.
//
// The selected provider must support containers and its custom registry. The workspace must have active compute and
// storage allocations there. The provider validates that it can run the image, that the image comes from its registry,
// that the caller can pull it, and that the repository belongs to the active workspace. The provider returns a digest,
// which is stored in the normalized application to make later execution stable.
//
// Flavor names are unique among custom-origin flavors in the same workspace and group. Application variants use the
// managed catalog namespace and do not share the custom flavor namespace because a custom group never resolves to a
// managed group ID.
//
// Catalog merge
// ---------------------------------------------------------------------------------------------------------------------
// The managed catalog is always the base. Read paths select custom rows from the actor's active workspace, apply
// category access, publication, and provider discovery, and then append the remaining groups, categories, and
// applications after managed objects. Existing request flags still control application expansion and removal of empty
// groups or categories.
//
// Custom objects and applications use `CUSTOM` origin and negative IDs in catalog responses. Clients must use the
// origin field, not an ID sign or name prefix, when they present ownership.
//
// Persistence and deletion
// ---------------------------------------------------------------------------------------------------------------------
// Startup loads groups, categories, ACLs, and applications into one cache. YAML parsing and normalization run in
// parallel without holding the cache lock. Invalid persisted applications are skipped. The completed maps replace the
// cache in one short critical section, so readers never observe a partial reload.
//
// Custom applications are hard-deleted. The same transaction rewrites matching jobs to the `unknown/unknown`
// application so job history remains readable without retaining a custom application tombstone. Groups and categories
// can be deleted only when no custom application references them. Database foreign keys close creation and deletion
// races. Category ACL rows do not count toward emptiness and are removed by cascading deletion.
//
// Project group deletion removes category ACL rows in the database and emits `project_group_updates`. The listener in
// this file removes the same group from cached ACLs. Permission checks also require current actor group membership, so
// stale ACL entries fail closed while notification processing completes.
//
// ---------------------------------------------------------------------------------------------------------------------
// MUTEX LOCK ORDER: applicationVariantReservationMu -> appCustomCache.Mu -> managed catalog bucket/group
// ---------------------------------------------------------------------------------------------------------------------

// Core model and cache
// =====================================================================================================================
// The cache owns custom rows only. Managed catalog state remains in app_catalog.go. Values are pointers because ACL,
// publication, and backing state can change after startup. Callers must hold appCustomCache.Mu while they read or
// change these maps or their values. A reload builds replacement maps off-lock and swaps all four maps together.

type appCustomGroup struct {
	Id          int64
	CreatedBy   string
	Project     util.Option[string]
	CreatedAt   time.Time
	Title       string
	Description string
	Logo        orcapi.ApplicationGroupLogo
}

type appCustomCategory struct {
	Id          int64
	CreatedBy   string
	Project     util.Option[string]
	CreatedAt   time.Time
	Title       string
	Description string
	Acl         []orcapi.ResourceAclEntry
}

type appCustomApplication struct {
	Id                 int64
	CreatedBy          string
	Project            util.Option[string]
	CreatedAt          time.Time
	Provider           string
	PublishedToProject bool
	FlavorName         string
	GroupId            int64
	CategoryId         int64
	Source             string
	Application        orcapi.Application
}

var appCustomCache = struct {
	Mu         sync.RWMutex
	Groups     map[int64]*appCustomGroup
	Categories map[int64]*appCustomCategory
	Apps       map[int64]*appCustomApplication
	AppKeys    map[string]int64
}{
	Groups:     map[int64]*appCustomGroup{},
	Categories: map[int64]*appCustomCategory{},
	Apps:       map[int64]*appCustomApplication{},
	AppKeys:    map[string]int64{},
}

func appCustomApplicationKey(workspace, name, version, provider string) string {
	return workspace + "\x00" + name + "\x00" + version + "\x00" + provider
}

// Workspace and authorization
// =====================================================================================================================
// A workspace is either a project ID or, when project_id is null, the creator's username. Category ACL evaluation
// starts with this boundary and then derives effective permissions from current project-group membership. Application
// visibility adds publication and creator checks after category access. ActorSystem bypasses this layer for internal
// catalog maintenance; user-facing callers must pass the request actor.

func appCustomWorkspaceEx(createdBy string, project util.Option[string]) string {
	return project.GetOrDefault(createdBy)
}

func appCustomWorkspace(actor rpc.Actor) string {
	project := util.OptNone[string]()
	if actor.Project.Present {
		project.Set(string(actor.Project.Value))
	}
	return appCustomWorkspaceEx(actor.Username, project)
}

func appCustomBelongsToActorsWorkspace(actor rpc.Actor, createdBy string, project util.Option[string]) bool {
	if actor.Username == rpc.ActorSystem.Username {
		return true
	}
	return appCustomWorkspace(actor) == appCustomWorkspaceEx(createdBy, project)
}

func appCustomIsAdmin(actor rpc.Actor) bool {
	if actor.Role == rpc.RoleAdmin || actor.Username == rpc.ActorSystem.Username {
		return true
	}
	if !actor.Project.Present {
		return true
	}
	return actor.Membership[actor.Project.Value].Satisfies(rpc.ProjectRoleAdmin)
}

func appCustomCategoryPermissions(actor rpc.Actor, category *appCustomCategory) []orcapi.Permission {
	if !appCustomBelongsToActorsWorkspace(actor, category.CreatedBy, category.Project) {
		return nil
	}
	if appCustomIsAdmin(actor) {
		return []orcapi.Permission{orcapi.PermissionRead, orcapi.PermissionEdit, orcapi.PermissionAdmin}
	}

	var result []orcapi.Permission
	for _, entry := range category.Acl {
		if entry.Entity.Type != orcapi.AclEntityTypeProjectGroup {
			continue
		}
		if _, member := actor.Groups[rpc.GroupId(entry.Entity.Group)]; !member {
			continue
		}
		for _, permission := range entry.Permissions {
			if permission == orcapi.PermissionRead || permission == orcapi.PermissionEdit {
				result = orcapi.PermissionsAdd(result, permission)
			}
			if permission == orcapi.PermissionEdit {
				result = orcapi.PermissionsAdd(result, orcapi.PermissionRead)
			}
		}
	}
	return result
}

func appCustomCategoryHasPermission(actor rpc.Actor, category *appCustomCategory, permission orcapi.Permission) bool {
	return slices.Contains(appCustomCategoryPermissions(actor, category), permission)
}

func appCustomCanReadApplication(actor rpc.Actor, app *appCustomApplication, category *appCustomCategory) bool {
	if !appCustomCategoryHasPermission(actor, category, orcapi.PermissionRead) {
		return false
	}
	return appCustomIsAdmin(actor) || app.PublishedToProject || app.CreatedBy == actor.Username
}

// Loading and backing-object lifecycle
// =====================================================================================================================
// Startup reconstructs groups and categories first because application validation needs both references. Workers
// parse disjoint sets of stored YAML and collect local results. Only the final cache swap takes the global lock.
// Catalog deletion hooks materialize backed objects by clearing their backing IDs; snapshots preserve their metadata.

func appCustomLoad() {
	type groupRow struct {
		Id          int64
		CreatedBy   string
		ProjectId   sql.NullString
		CreatedAt   time.Time
		Title       string
		Description string
		Logo        sql.NullString
	}
	type categoryRow struct {
		Id          int64
		CreatedBy   string
		ProjectId   sql.NullString
		CreatedAt   time.Time
		Title       string
		Description string
	}
	type aclRow struct {
		CategoryId     int64
		ProjectGroupId string
		Permission     string
	}
	type applicationRow struct {
		Id                 int64
		CreatedBy          string
		ProjectId          sql.NullString
		CreatedAt          time.Time
		ServiceProvider    string
		PublishedToProject bool
		FlavorName         string
		CustomGroupId      int64
		CustomCategoryId   int64
		SourceApplication  string
	}
	type applicationResult struct {
		Application *appCustomApplication
		Key         string
	}

	var groups []groupRow
	var categories []categoryRow
	var acls []aclRow
	var apps []applicationRow
	db.NewTx0(func(tx *db.Transaction) {
		tx.NoDevResetThisIsNotAHackIPromise = true

		groups = db.Select[groupRow](
			tx,
			`
				select
					id,
					created_by,
					project_id,
					created_at,
					title,
					description,
					logo
				from
					app_store.custom_application_groups
			`,
			db.Params{},
		)
		categories = db.Select[categoryRow](
			tx,
			`
				select id, created_by, project_id, created_at, title, description
				from app_store.custom_application_categories
			`,
			db.Params{},
		)
		acls = db.Select[aclRow](
			tx,
			`
				select category_id, project_group_id, permission
				from app_store.custom_application_category_acl
			`,
			db.Params{},
		)
		apps = db.Select[applicationRow](
			tx,
			`
				select id, created_by, project_id, created_at, service_provider, published_to_project,
					flavor_name, custom_group_id, custom_category_id, source_application
				from app_store.custom_applications
				order by id
			`,
			db.Params{},
		)
	})

	loadedGroups := map[int64]*appCustomGroup{}
	loadedCategories := map[int64]*appCustomCategory{}
	for _, row := range groups {
		logo := appCustomDefaultLogo(row.Title)
		if row.Logo.Valid {
			var stored orcapi.ApplicationGroupLogo
			if json.Unmarshal([]byte(row.Logo.String), &stored) == nil && appCustomValidateLogo(stored) == nil {
				logo = stored
			}
		}
		loadedGroups[row.Id] = &appCustomGroup{
			Id:          row.Id,
			CreatedBy:   row.CreatedBy,
			Project:     util.SqlNullStringToOpt(row.ProjectId),
			CreatedAt:   row.CreatedAt,
			Title:       row.Title,
			Description: row.Description,
			Logo:        logo,
		}
	}
	for _, row := range categories {
		loadedCategories[row.Id] = &appCustomCategory{
			Id:          row.Id,
			CreatedBy:   row.CreatedBy,
			Project:     util.SqlNullStringToOpt(row.ProjectId),
			CreatedAt:   row.CreatedAt,
			Title:       row.Title,
			Description: row.Description,
		}
	}
	for _, row := range acls {
		if category := loadedCategories[row.CategoryId]; category != nil {
			found := false
			for index := range category.Acl {
				if category.Acl[index].Entity.Group == row.ProjectGroupId {
					category.Acl[index].Permissions = orcapi.PermissionsAdd(category.Acl[index].Permissions, orcapi.Permission(row.Permission))
					found = true
					break
				}
			}
			if !found {
				category.Acl = append(
					category.Acl,
					orcapi.ResourceAclEntry{
						Entity: orcapi.AclEntity{
							Type:      orcapi.AclEntityTypeProjectGroup,
							ProjectId: category.Project.GetOrDefault(""),
							Group:     row.ProjectGroupId,
						},
						Permissions: []orcapi.Permission{
							orcapi.Permission(row.Permission),
						},
					},
				)
			}
		}
	}

	workerCount := min(len(apps), runtime.GOMAXPROCS(0))
	workerResults := make([][]applicationResult, workerCount)
	var workers sync.WaitGroup
	workers.Add(workerCount)
	for worker := range workerCount {
		go func(worker int) {
			defer workers.Done()
			var results []applicationResult
			for index := worker; index < len(apps); index += workerCount {
				row := apps[index]
				project := util.SqlNullStringToOpt(row.ProjectId)
				group := loadedGroups[row.CustomGroupId]
				category := loadedCategories[row.CustomCategoryId]
				if group == nil || category == nil {
					continue
				}
				workspace := appCustomWorkspaceEx(row.CreatedBy, project)
				groupWorkspace := appCustomWorkspaceEx(group.CreatedBy, group.Project)
				categoryWorkspace := appCustomWorkspaceEx(category.CreatedBy, category.Project)
				if workspace != groupWorkspace || workspace != categoryWorkspace {
					continue
				}
				if !project.Present && row.PublishedToProject {
					continue
				}
				var source orcapi.A2Yaml
				if yaml.Unmarshal([]byte(row.SourceApplication), &source) != nil {
					continue
				}
				application, normalizeErr := source.Normalize()
				if normalizeErr != nil {
					continue
				}
				unsupportedSoftware := source.Software.Type != orcapi.A2SoftwareContainer ||
					source.Software.Container == nil
				usesUnsupportedFeatures := source.Ucx.Present ||
					source.Modules.Present ||
					source.Documentation.Present ||
					len(source.Extensions) != 0
				invalidName := !strings.HasPrefix(source.Name, "custom-")
				if unsupportedSoftware || usesUnsupportedFeatures || invalidName {
					continue
				}
				application.Metadata.Origin = orcapi.CatalogOriginCustom
				application.Metadata.PublishedToProject.Set(row.PublishedToProject)
				application.Metadata.FlavorName.Set(row.FlavorName)
				application.Metadata.CreatedAt = fndapi.Timestamp(row.CreatedAt)
				application.Metadata.Authors = []string{row.CreatedBy}
				application.Metadata.Group.Metadata.Id = -int(row.CustomGroupId)
				application.Invocation.Tool.Tool.Value.Description.SupportedProviders = []string{row.ServiceProvider}
				custom := &appCustomApplication{
					Id:                 row.Id,
					CreatedBy:          row.CreatedBy,
					Project:            project,
					CreatedAt:          row.CreatedAt,
					Provider:           row.ServiceProvider,
					PublishedToProject: row.PublishedToProject,
					FlavorName:         row.FlavorName,
					GroupId:            row.CustomGroupId,
					CategoryId:         row.CustomCategoryId,
					Source:             row.SourceApplication,
					Application:        application,
				}
				results = append(
					results,
					applicationResult{
						Application: custom,
						Key: appCustomApplicationKey(
							appCustomWorkspaceEx(custom.CreatedBy, custom.Project),
							source.Name,
							source.Version,
							custom.Provider,
						),
					},
				)
			}
			workerResults[worker] = results
		}(worker)
	}
	workers.Wait()

	loadedApps := map[int64]*appCustomApplication{}
	loadedAppKeys := map[string]int64{}
	for _, results := range workerResults {
		for _, result := range results {
			loadedApps[result.Application.Id] = result.Application
			loadedAppKeys[result.Key] = result.Application.Id
		}
	}

	appCustomCache.Mu.Lock()
	appCustomCache.Groups = loadedGroups
	appCustomCache.Categories = loadedCategories
	appCustomCache.Apps = loadedApps
	appCustomCache.AppKeys = loadedAppKeys
	appCustomCache.Mu.Unlock()

	go appCustomListenForProjectGroupUpdates()
}

func appCustomListenForProjectGroupUpdates() {
	updates := db.Listen(context.Background(), "project_group_updates")
	for groupId := range updates {
		if resourceProjectGroupExists(groupId) {
			continue
		}
		appCustomCache.Mu.Lock()
		for _, category := range appCustomCache.Categories {
			category.Acl = slices.DeleteFunc(category.Acl, func(entry orcapi.ResourceAclEntry) bool {
				return entry.Entity.Group == groupId
			})
		}
		appCustomCache.Mu.Unlock()
	}
}

// Group, category, and ACL management
// =====================================================================================================================
// Management APIs operate on negative custom resource IDs. Group creation requires `EDIT` on a category in the active
// workspace, but the new group is not automatically tied to that category. Category creation and ACL updates require
// workspace administration. ACL writes lock referenced project groups to close deletion races.

func appCustomGroupToApi(group *appCustomGroup) orcapi.AppCatalogCustomGroup {
	logo := group.Logo
	return orcapi.AppCatalogCustomGroup{
		Id:        -int(group.Id),
		CreatedAt: fndapi.Timestamp(group.CreatedAt),
		Owner: orcapi.ResourceOwner{
			CreatedBy: group.CreatedBy,
			Project:   group.Project,
		},
		Specification: orcapi.AppCatalogCustomGroupSpecification{
			Title:       group.Title,
			Description: group.Description,
			Logo:        &logo,
		},
	}
}

func appCustomCategoryToApi(actor rpc.Actor, category *appCustomCategory) orcapi.AppCatalogCustomCategory {
	permissions := appCustomCategoryPermissions(actor, category)
	others := make([]orcapi.ResourceAclEntry, len(category.Acl))
	for i, entry := range category.Acl {
		others[i] = entry
		others[i].Permissions = append([]orcapi.Permission(nil), entry.Permissions...)
	}
	return orcapi.AppCatalogCustomCategory{
		Id:        -int(category.Id),
		CreatedAt: fndapi.Timestamp(category.CreatedAt),
		Owner: orcapi.ResourceOwner{
			CreatedBy: category.CreatedBy,
			Project:   category.Project,
		},
		Specification: orcapi.AppCatalogCustomCategorySpecification{
			Title:       category.Title,
			Description: category.Description,
		},
		Permissions: orcapi.ResourcePermissions{
			Myself: util.NonNilSlice(permissions),
			Others: util.NonNilSlice(others),
		},
	}
}

func appCustomValidateSpec(title, description string) *util.HttpError {
	title = strings.TrimSpace(title)
	if err := util.ValidateStringE(&title, "title", 0); err != nil {
		return err
	}
	if err := util.ValidateStringE(&description, "description", util.StringValidationAllowEmpty|util.StringValidationAllowMultiline|util.StringValidationAllowLong); err != nil {
		return err
	}
	return nil
}

var appCustomLogoShapes = []string{"circle", "rounded-square", "hexagon", "diamond", "shield"}
var appCustomLogoBorders = []string{"none", "thin", "thick", "double"}
var appCustomLogoColors = []string{"auto", "gold", "blue", "violet", "green", "cyan", "rose"}
var appCustomLogoPaletteColors = appCustomLogoColors[1:]
var appCustomLogoDirections = []string{"top-right", "bottom-right", "bottom-left", "top-left"}
var appCustomLogoSizes = []string{"small", "medium", "large"}
var appCustomLogoIcons = []string{
	"academic-cap", "beaker", "bolt", "calculator", "chart-bar", "circle-stack", "cloud", "code-bracket",
	"command-line", "cpu-chip", "cube", "document", "folder", "globe", "photo", "rocket", "server", "sparkles", "wrench",
	"gpu",
}

func appCustomLogoHash(title string) uint32 {
	hash := uint32(5381)
	characters := utf16.Encode([]rune(title))
	for i := len(characters) - 1; i >= 0; i-- {
		hash = hash*33 ^ uint32(characters[i])
	}
	return hash
}

func appCustomLogoNext(value uint32) uint32 {
	return value*1664525 + 1013904223
}

func appCustomDefaultLogo(title string) orcapi.ApplicationGroupLogo {
	value := appCustomLogoHash(title)
	next := func(length int) int {
		value = appCustomLogoNext(value)
		return int(value % uint32(length))
	}
	colorA := appCustomLogoPaletteColors[next(len(appCustomLogoPaletteColors))]
	colorB := appCustomLogoPaletteColors[next(len(appCustomLogoPaletteColors))]
	if colorA == colorB {
		colorB = appCustomLogoPaletteColors[(slices.Index(appCustomLogoPaletteColors, colorA)+1)%len(appCustomLogoPaletteColors)]
	}
	return orcapi.ApplicationGroupLogo{
		Version: 1,
		Shape:   appCustomLogoShapes[next(len(appCustomLogoShapes))],
		Border: orcapi.ApplicationGroupLogoBorder{
			Style: appCustomLogoBorders[next(len(appCustomLogoBorders))],
			Color: appCustomLogoPaletteColors[next(len(appCustomLogoPaletteColors))],
		},
		Fill: orcapi.ApplicationGroupLogoFill{
			Type:      []string{"solid", "gradient"}[next(2)],
			ColorA:    colorA,
			ColorB:    colorB,
			Direction: appCustomLogoDirections[next(len(appCustomLogoDirections))],
		},
		Content: orcapi.ApplicationGroupLogoContent{
			Type:  "icon",
			Value: appCustomLogoIcons[next(len(appCustomLogoIcons)-1)],
			Size:  appCustomLogoSizes[next(len(appCustomLogoSizes))],
			Color: "auto",
		},
	}
}

func appCustomValidateLogo(logo orcapi.ApplicationGroupLogo) *util.HttpError {
	if logo.Version != 1 || !slices.Contains(appCustomLogoShapes, logo.Shape) {
		return util.HttpErr(http.StatusBadRequest, "invalid logo shape or version")
	}
	if !slices.Contains(appCustomLogoBorders, logo.Border.Style) || !slices.Contains(appCustomLogoColors, logo.Border.Color) {
		return util.HttpErr(http.StatusBadRequest, "invalid logo border")
	}
	if (logo.Fill.Type != "solid" && logo.Fill.Type != "gradient") ||
		!slices.Contains(appCustomLogoColors, logo.Fill.ColorA) ||
		!slices.Contains(appCustomLogoColors, logo.Fill.ColorB) ||
		!slices.Contains(appCustomLogoDirections, logo.Fill.Direction) {
		return util.HttpErr(http.StatusBadRequest, "invalid logo fill")
	}
	validContentColor := logo.Content.Color == "auto" || logo.Content.Color == "light" || logo.Content.Color == "dark" ||
		slices.Contains(appCustomLogoColors, logo.Content.Color)
	if !slices.Contains(appCustomLogoSizes, logo.Content.Size) || !validContentColor {
		return util.HttpErr(http.StatusBadRequest, "invalid logo content style")
	}
	if logo.Content.Type == "icon" && slices.Contains(appCustomLogoIcons, logo.Content.Value) {
		return nil
	}
	if logo.Content.Type == "text" && strings.TrimSpace(logo.Content.Value) == logo.Content.Value &&
		utf8.RuneCountInString(logo.Content.Value) >= 1 && utf8.RuneCountInString(logo.Content.Value) <= 4 {
		for _, character := range logo.Content.Value {
			if unicode.IsControl(character) {
				return util.HttpErr(http.StatusBadRequest, "invalid logo content")
			}
		}
		return nil
	}
	return util.HttpErr(http.StatusBadRequest, "invalid logo content")
}

func appCustomCanCreateGroup(actor rpc.Actor) bool {
	if appCustomIsAdmin(actor) {
		return true
	}
	appCustomCache.Mu.RLock()
	defer appCustomCache.Mu.RUnlock()
	activeWorkspace := appCustomWorkspace(actor)
	for _, category := range appCustomCache.Categories {
		categoryWorkspace := appCustomWorkspaceEx(category.CreatedBy, category.Project)
		if categoryWorkspace != activeWorkspace {
			continue
		}
		if appCustomCategoryHasPermission(actor, category, orcapi.PermissionEdit) {
			return true
		}
	}
	return false
}

func appCustomCanReadGroup(actor rpc.Actor, group *appCustomGroup) bool {
	if !appCustomBelongsToActorsWorkspace(actor, group.CreatedBy, group.Project) {
		return false
	}
	if appCustomIsAdmin(actor) {
		return true
	}
	for _, app := range appCustomCache.Apps {
		if app.GroupId != group.Id {
			continue
		}
		category := appCustomCache.Categories[app.CategoryId]
		if category != nil && appCustomCanReadApplication(actor, app, category) {
			return true
		}
	}
	return false
}

func appCustomCreateGroup(actor rpc.Actor, request orcapi.AppCatalogCreateCustomGroupRequest) (fndapi.FindByIntId, *util.HttpError) {
	if !appCustomCanCreateGroup(actor) {
		return fndapi.FindByIntId{}, util.HttpErr(http.StatusForbidden, "permission denied")
	}
	title, description := strings.TrimSpace(request.Specification.Title), request.Specification.Description
	if err := appCustomValidateSpec(title, description); err != nil {
		return fndapi.FindByIntId{}, err
	}
	logo := appCustomDefaultLogo(title)
	if request.Specification.Logo != nil {
		logo = *request.Specification.Logo
	}
	if err := appCustomValidateLogo(logo); err != nil {
		return fndapi.FindByIntId{}, err
	}
	logoJson, _ := json.Marshal(logo)
	project := util.OptMap(actor.Project, func(value rpc.ProjectId) string { return string(value) })
	created, ok := db.NewTx2(func(tx *db.Transaction) (struct {
		Id        int64
		CreatedAt time.Time
	}, bool) {
		return db.Get[struct {
			Id        int64
			CreatedAt time.Time
		}](
			tx,
			`
				insert into app_store.custom_application_groups(
					created_by, project_id, title, description, logo
				) values (
					:created_by, :project, :title, :description, cast(:logo as jsonb)
				) on conflict do nothing
				returning id, created_at
			`,
			db.Params{
				"created_by":  actor.Username,
				"project":     project.Sql(),
				"title":       title,
				"description": description,
				"logo":        string(logoJson),
			},
		)
	})
	if !ok {
		return fndapi.FindByIntId{}, util.HttpErr(http.StatusConflict, "group already exists")
	}
	appCustomCache.Mu.Lock()
	appCustomCache.Groups[created.Id] = &appCustomGroup{
		Id:          created.Id,
		CreatedBy:   actor.Username,
		Project:     project,
		CreatedAt:   created.CreatedAt,
		Title:       title,
		Description: description,
		Logo:        logo,
	}
	appCustomCache.Mu.Unlock()
	return fndapi.FindByIntId{Id: -int(created.Id)}, nil
}

func appCustomUpdateGroup(actor rpc.Actor, request orcapi.AppCatalogUpdateCustomGroupRequest) *util.HttpError {
	if request.Id >= 0 {
		return util.HttpErr(http.StatusNotFound, "group not found")
	}
	title := strings.TrimSpace(request.NewTitle)
	description := request.NewDescription
	if err := appCustomValidateSpec(title, description); err != nil {
		return err
	}
	id := int64(-request.Id)
	appCustomCache.Mu.Lock()
	defer appCustomCache.Mu.Unlock()
	group := appCustomCache.Groups[id]
	canEdit := group != nil && appCustomBelongsToActorsWorkspace(actor, group.CreatedBy, group.Project) &&
		(appCustomIsAdmin(actor) || group.CreatedBy == actor.Username)
	if !canEdit {
		return util.HttpErr(http.StatusNotFound, "group not found")
	}
	workspace := appCustomWorkspaceEx(group.CreatedBy, group.Project)
	if strings.ToLower(group.Title) != strings.ToLower(title) {
		for _, other := range appCustomCache.Groups {
			sameWorkspace := appCustomWorkspaceEx(other.CreatedBy, other.Project) == workspace
			if other.Id != id && sameWorkspace && strings.EqualFold(other.Title, title) {
				return util.HttpErr(http.StatusConflict, "group already exists")
			}
		}
	}
	_, updated := db.NewTx2(func(tx *db.Transaction) (struct{ Id int64 }, bool) {
		return db.Get[struct{ Id int64 }](
			tx,
			`
				update app_store.custom_application_groups
				set
					title = :title,
					description = :description
				where id = :id
				returning id
			`,
			db.Params{
				"id":          id,
				"title":       title,
				"description": description,
			},
		)
	})
	if !updated {
		return util.HttpErr(http.StatusNotFound, "group not found")
	}
	if group = appCustomCache.Groups[id]; group != nil {
		group.Title = title
		group.Description = description
	}
	return nil
}

func appCustomUpdateGroupLogo(actor rpc.Actor, request orcapi.AppCatalogUpdateCustomGroupLogoRequest) *util.HttpError {
	if request.Id >= 0 {
		return util.HttpErr(http.StatusNotFound, "group not found")
	}
	if err := appCustomValidateLogo(request.Logo); err != nil {
		return err
	}
	id := int64(-request.Id)
	appCustomCache.Mu.Lock()
	defer appCustomCache.Mu.Unlock()
	group := appCustomCache.Groups[id]
	canEdit := group != nil && appCustomBelongsToActorsWorkspace(actor, group.CreatedBy, group.Project) &&
		(appCustomIsAdmin(actor) || group.CreatedBy == actor.Username)
	if !canEdit {
		return util.HttpErr(http.StatusNotFound, "group not found")
	}
	logoJson, _ := json.Marshal(request.Logo)
	_, updated := db.NewTx2(func(tx *db.Transaction) (struct{ Id int64 }, bool) {
		return db.Get[struct{ Id int64 }](
			tx,
			`
				update app_store.custom_application_groups
				set logo = cast(:logo as jsonb)
				where id = :id
				returning id
			`,
			db.Params{
				"id":   id,
				"logo": string(logoJson),
			},
		)
	})
	if !updated {
		return util.HttpErr(http.StatusNotFound, "group not found")
	}
	if group = appCustomCache.Groups[id]; group != nil {
		group.Logo = request.Logo
	}
	return nil
}

func appCustomValidateAcl(actor rpc.Actor, entries []orcapi.ResourceAclEntry) ([]orcapi.ResourceAclEntry, *util.HttpError) {
	if !actor.Project.Present && len(entries) != 0 {
		return nil, util.HttpErr(http.StatusBadRequest, "personal categories cannot have ACL entries")
	}
	project := string(actor.Project.GetOrDefault(""))
	result := make([]orcapi.ResourceAclEntry, 0, len(entries))
	for _, entry := range entries {
		if entry.Entity.Type != orcapi.AclEntityTypeProjectGroup || entry.Entity.ProjectId != project || entry.Entity.Group == "" {
			return nil, util.HttpErr(http.StatusBadRequest, "ACL entries must reference a group in the active project")
		}
		if !appCustomProjectGroupInProject(entry.Entity.Group, project) {
			return nil, util.HttpErr(http.StatusBadRequest, "unknown project group")
		}
		permissions := []orcapi.Permission{}
		for _, permission := range entry.Permissions {
			if permission != orcapi.PermissionRead && permission != orcapi.PermissionEdit {
				return nil, util.HttpErr(http.StatusBadRequest, "only READ and EDIT permissions are supported")
			}
			permissions = orcapi.PermissionsAdd(permissions, permission)
		}
		if len(permissions) == 0 {
			return nil, util.HttpErr(http.StatusBadRequest, "ACL entries must have READ or EDIT permission")
		}
		result = append(result, orcapi.ResourceAclEntry{Entity: entry.Entity, Permissions: permissions})
	}
	return result, nil
}

func appCustomProjectGroupInProject(groupId, projectId string) bool {
	return db.NewTx(func(tx *db.Transaction) bool {
		_, ok := db.Get[struct{ Id string }](
			tx,
			`select id from project.groups where id = :group and project = :project`,
			db.Params{
				"group":   groupId,
				"project": projectId,
			},
		)
		return ok
	})
}

func appCustomCreateCategory(actor rpc.Actor, request orcapi.AppCatalogCreateCustomCategoryRequest) (fndapi.FindByIntId, *util.HttpError) {
	if !appCustomIsAdmin(actor) {
		return fndapi.FindByIntId{}, util.HttpErr(http.StatusForbidden, "permission denied")
	}
	acl, aclErr := appCustomValidateAcl(actor, request.Acl)
	if aclErr != nil {
		return fndapi.FindByIntId{}, aclErr
	}
	title, description := strings.TrimSpace(request.Specification.Title), request.Specification.Description
	if err := appCustomValidateSpec(title, description); err != nil {
		return fndapi.FindByIntId{}, err
	}
	project := util.OptMap(actor.Project, func(value rpc.ProjectId) string { return string(value) })
	created, ok := db.NewTx2(func(tx *db.Transaction) (struct {
		Id        int64
		CreatedAt time.Time
	}, bool) {
		if !appCustomValidateAclGroups(tx, string(actor.Project.GetOrDefault("")), acl) {
			return struct {
				Id        int64
				CreatedAt time.Time
			}{}, false
		}
		created, found := db.Get[struct {
			Id        int64
			CreatedAt time.Time
		}](
			tx,
			`
				insert into app_store.custom_application_categories(
					created_by, project_id, title, description
				) values (
					:created_by, :project, :title, :description
				) on conflict do nothing
				returning id, created_at
			`,
			db.Params{
				"created_by":  actor.Username,
				"project":     project.Sql(),
				"title":       title,
				"description": description,
			},
		)
		if found {
			appCustomPersistAcl(tx, created.Id, acl, nil)
		}
		return created, found
	})
	if !ok {
		return fndapi.FindByIntId{}, util.HttpErr(http.StatusConflict, "category already exists")
	}
	appCustomCache.Mu.Lock()
	appCustomCache.Categories[created.Id] = &appCustomCategory{
		Id:          created.Id,
		CreatedBy:   actor.Username,
		Project:     project,
		CreatedAt:   created.CreatedAt,
		Title:       title,
		Description: description,
		Acl:         acl,
	}
	appCustomCache.Mu.Unlock()
	return fndapi.FindByIntId{Id: -int(created.Id)}, nil
}

func appCustomPersistAcl(tx *db.Transaction, categoryId int64, added []orcapi.ResourceAclEntry, deleted []orcapi.AclEntity) {
	for _, entity := range deleted {
		db.Exec(
			tx,
			`
				delete from app_store.custom_application_category_acl
				where category_id = :category and project_group_id = :group
			`,
			db.Params{
				"category": categoryId,
				"group":    entity.Group,
			},
		)
	}
	for _, entry := range added {
		db.Exec(
			tx,
			`
				delete from app_store.custom_application_category_acl
				where category_id = :category and project_group_id = :group
			`,
			db.Params{
				"category": categoryId,
				"group":    entry.Entity.Group,
			},
		)
		for _, permission := range entry.Permissions {
			db.Exec(
				tx,
				`
					insert into app_store.custom_application_category_acl(category_id, project_group_id, permission)
					values (:category, :group, :permission)
					on conflict do nothing
				`,
				db.Params{
					"category":   categoryId,
					"group":      entry.Entity.Group,
					"permission": permission,
				},
			)
		}
	}
}

func appCustomValidateAclGroups(tx *db.Transaction, project string, entries []orcapi.ResourceAclEntry) bool {
	for _, entry := range entries {
		_, found := db.Get[struct{ Id string }](
			tx,
			`
				select id
				from project.groups
				where id = :group and project = :project
				for key share
			`,
			db.Params{
				"group":   entry.Entity.Group,
				"project": project,
			},
		)
		if !found {
			return false
		}
	}
	return true
}

func appCustomUpdateCategoryAcl(actor rpc.Actor, request orcapi.UpdatedAcl) *util.HttpError {
	if !appCustomIsAdmin(actor) {
		return util.HttpErr(http.StatusForbidden, "permission denied")
	}
	id, parseErr := strconv.ParseInt(request.Id, 10, 64)
	if parseErr != nil || id >= 0 {
		return util.HttpErr(http.StatusBadRequest, "invalid category ID")
	}
	id = -id
	added, aclErr := appCustomValidateAcl(actor, request.Added)
	if aclErr != nil {
		return aclErr
	}
	for _, entity := range request.Deleted {
		if entity.Type != orcapi.AclEntityTypeProjectGroup || entity.ProjectId != string(actor.Project.GetOrDefault("")) {
			return util.HttpErr(http.StatusBadRequest, "invalid ACL entity")
		}
	}
	appCustomCache.Mu.Lock()
	defer appCustomCache.Mu.Unlock()
	category := appCustomCache.Categories[id]
	if category == nil || !appCustomBelongsToActorsWorkspace(actor, category.CreatedBy, category.Project) {
		return util.HttpErr(http.StatusNotFound, "category not found")
	}
	persisted := db.NewTx(func(tx *db.Transaction) bool {
		if !appCustomValidateAclGroups(tx, string(actor.Project.GetOrDefault("")), added) {
			return false
		}
		appCustomPersistAcl(tx, id, added, request.Deleted)
		return true
	})
	if !persisted {
		return util.HttpErr(http.StatusBadRequest, "unknown project group")
	}
	for _, entity := range request.Deleted {
		category.Acl = slices.DeleteFunc(category.Acl, func(entry orcapi.ResourceAclEntry) bool { return entry.Entity.Group == entity.Group })
	}
	for _, entry := range added {
		if !appCustomProjectGroupInProject(entry.Entity.Group, entry.Entity.ProjectId) {
			continue
		}
		category.Acl = slices.DeleteFunc(category.Acl, func(existing orcapi.ResourceAclEntry) bool { return existing.Entity.Group == entry.Entity.Group })
		category.Acl = append(category.Acl, entry)
	}
	return nil
}

// Application validation and lifecycle
// =====================================================================================================================
// Application creation combines catalog validation with provider-owned image validation. It reserves the shared
// custom-origin flavor namespace before the final database insert and cache update. Retrieval applies workspace,
// category, publication, and discovery checks. Update changes publication only; application YAML remains immutable.

func appCustomActorHasProviderAllocations(actor rpc.Actor, provider string) (compute bool, storage bool) {
	owner := accapi.WalletOwnerUser(actor.Username)
	if actor.Project.Present {
		owner = accapi.WalletOwnerProject(string(actor.Project.Value))
	}
	response, err := accapi.WalletsBrowseInternal.Invoke(accapi.WalletsBrowseInternalRequest{Owner: owner})
	if err != nil {
		return false, false
	}
	found := map[accapi.ProductType]bool{}
	for _, wallet := range response.Wallets {
		if wallet.PaysFor.Provider != provider {
			continue
		}
		for _, group := range wallet.AllocationGroups {
			for _, allocation := range group.Group.Allocations {
				if allocation.Activated && !allocation.Retired {
					found[wallet.PaysFor.ProductType] = true
				}
			}
		}
	}
	return found[accapi.ProductTypeCompute], found[accapi.ProductTypeStorage]
}

func appCustomProviderHasSupportForDockerAndRegistry(provider string) (bool, bool) {
	docker := false
	for _, product := range SupportRetrieveProducts[orcapi.JobSupport](jobType).ProductsByProvider[provider] {
		docker = docker || product.Support.Docker.Enabled
	}
	registry := false
	for _, product := range SupportRetrieveProducts[orcapi.FSSupport](driveType).ProductsByProvider[provider] {
		registry = registry || product.Support.ContainerRepositories.Enabled
	}
	return docker, registry
}

func appCustomHasProviderAllocations(actor rpc.Actor, provider string) bool {
	compute, storage := appCustomActorHasProviderAllocations(actor, provider)
	return compute && storage
}

func appCustomProviderHasSupport(provider string) bool {
	docker, registry := appCustomProviderHasSupportForDockerAndRegistry(provider)
	return docker && registry
}

func appCustomValidateStoredImage(actor rpc.Actor, provider, image string) *util.HttpError {
	owner := orcapi.ResourceOwner{CreatedBy: actor.Username}
	if actor.Project.Present {
		owner.CreatedBy = ""
		owner.Project.Set(string(actor.Project.Value))
	}
	_, err := InvokeProvider(
		provider,
		orcapi.ApplicationVariantsProviderValidateImage,
		orcapi.ApplicationVariantValidateImageRequest{
			Owner:                 owner,
			Image:                 image,
			RequireWorkspaceOwner: true,
		},
		ProviderCallOpts{
			Username: util.OptValue(actor.Username),
			Reason:   util.OptValue("validate custom application image"),
		},
	)
	return err
}

func appCustomApplicationToApi(actor rpc.Actor, app *appCustomApplication) orcapi.Application {
	result := app.Application
	result.Favorite.Set(false)
	stars := appStarsRetrieve(actor.Username)
	stars.Mu.RLock()
	_, isStarred := stars.Applications[app.Application.Metadata.Name]
	stars.Mu.RUnlock()
	result.Favorite.Set(isStarred)
	if group := appCustomCache.Groups[app.GroupId]; group != nil {
		result.Metadata.Group.Metadata.Id = -int(group.Id)
		result.Metadata.Group.Metadata.Origin = orcapi.CatalogOriginCustom
	}
	return result
}

func appCustomFlavorAvailable(workspace, name string, group *appCustomGroup, flavor string) bool {
	for _, app := range appCustomCache.Apps {
		if appCustomWorkspaceEx(app.CreatedBy, app.Project) != workspace || app.GroupId != group.Id {
			continue
		}
		if app.Application.Metadata.Name == name {
			continue
		}
		if strings.EqualFold(app.FlavorName, flavor) {
			return false
		}
	}
	return true
}

func appCustomNormalizeFlavorName(flavorName *string) *util.HttpError {
	return util.ValidateStringE(flavorName, "flavorName", 0)
}

func appCustomCreateApplication(actor rpc.Actor, request orcapi.AppCatalogCreateCustomApplicationRequest) *util.HttpError {
	if !strings.HasPrefix(request.A2Yaml.Name, "custom-") {
		request.A2Yaml.Name = "custom-" + request.A2Yaml.Name
	}
	if err := appCustomNormalizeFlavorName(&request.FlavorName); err != nil {
		return err
	}
	if !actor.Project.Present && request.PublishedToProject {
		return util.HttpErr(http.StatusBadRequest, "personal applications cannot be published")
	}
	if request.Ucx.Present || request.Modules.Present || request.Documentation.Present ||
		len(request.Extensions) != 0 || request.Software.Type != orcapi.A2SoftwareContainer ||
		request.Software.Container == nil {
		return util.HttpErr(http.StatusBadRequest, "custom applications must use Container without UCX, modules, documentation, or extensions")
	}
	appCustomCache.Mu.RLock()
	category := appCustomCache.Categories[int64(-request.CategoryId)]
	group := appCustomCache.Groups[int64(-request.GroupId)]
	categoryAllowed := request.CategoryId < 0 && category != nil && appCustomCategoryHasPermission(actor, category, orcapi.PermissionEdit)
	groupAllowed := request.GroupId < 0 && group != nil && appCustomBelongsToActorsWorkspace(actor, group.CreatedBy, group.Project)
	appCustomCache.Mu.RUnlock()
	if !categoryAllowed {
		return util.HttpErr(http.StatusForbidden, "category EDIT permission is required")
	}
	if !groupAllowed {
		return util.HttpErr(http.StatusBadRequest, "group not found in the active workspace")
	}
	if request.ServiceProvider == "" || !appCustomProviderHasSupport(request.ServiceProvider) {
		return util.HttpErr(http.StatusBadRequest, "the provider does not support custom container applications")
	}
	if !appCustomHasProviderAllocations(actor, request.ServiceProvider) {
		return util.HttpErr(http.StatusBadRequest, "active compute and storage allocations are required at the provider")
	}
	application, normalizeErr := request.A2Yaml.Normalize()
	if normalizeErr != nil {
		return normalizeErr
	}
	validated, imageErr := applicationVariantValidateImage(actor, request.ServiceProvider, request.Software.Container.Image, false, true)
	if imageErr != nil {
		return imageErr
	}

	applicationVariantReservationMu.Lock()
	defer applicationVariantReservationMu.Unlock()
	appCustomCache.Mu.Lock()
	defer appCustomCache.Mu.Unlock()
	category = appCustomCache.Categories[int64(-request.CategoryId)]
	group = appCustomCache.Groups[int64(-request.GroupId)]
	if request.CategoryId >= 0 || category == nil || !appCustomCategoryHasPermission(actor, category, orcapi.PermissionEdit) {
		return util.HttpErr(http.StatusForbidden, "category EDIT permission is required")
	}
	if request.GroupId >= 0 || group == nil || !appCustomBelongsToActorsWorkspace(actor, group.CreatedBy, group.Project) {
		return util.HttpErr(http.StatusBadRequest, "group not found in the active workspace")
	}
	workspace := appCustomWorkspace(actor)
	if !appCustomFlavorAvailable(workspace, request.Name, group, request.FlavorName) {
		return util.HttpErr(http.StatusConflict, "a flavor with this name already exists")
	}

	request.Software.Container.Image = validated.Image
	application.Invocation.Tool.Tool.Value.Description.Image = validated.Image
	application.Invocation.Tool.Tool.Value.Description.Container = validated.Image
	source := appEditorMarshalSource(request.A2Yaml)
	project := util.OptMap(actor.Project, func(value rpc.ProjectId) string { return string(value) })
	created, ok := db.NewTx2(func(tx *db.Transaction) (struct {
		Id        int64
		CreatedAt time.Time
	}, bool) {
		return db.Get[struct {
			Id        int64
			CreatedAt time.Time
		}](
			tx,
			`
				insert into app_store.custom_applications(
					created_by, project_id, name, version, service_provider, published_to_project, flavor_name,
					custom_group_id, custom_category_id, source_application
				) values (
					:created_by, :project, :name, :version, :provider, :published, :flavor, :group, :category, :source
				) on conflict do nothing
				returning id, created_at
			`,
			db.Params{
				"created_by": actor.Username,
				"project":    project.Sql(),
				"name":       request.Name,
				"version":    request.Version,
				"provider":   request.ServiceProvider,
				"published":  request.PublishedToProject,
				"flavor":     request.FlavorName,
				"group":      group.Id,
				"category":   category.Id,
				"source":     string(source),
			},
		)
	})
	if !ok {
		return util.HttpErr(http.StatusConflict, "an application with this version or flavor name already exists")
	}
	application.Metadata.Origin = orcapi.CatalogOriginCustom
	application.Metadata.PublishedToProject.Set(request.PublishedToProject)
	application.Metadata.FlavorName.Set(request.FlavorName)
	application.Metadata.CreatedAt = fndapi.Timestamp(created.CreatedAt)
	application.Metadata.Authors = []string{actor.Username}
	application.Metadata.Group.Metadata.Id = -int(group.Id)
	application.Invocation.Tool.Tool.Value.Description.SupportedProviders = []string{request.ServiceProvider}
	custom := &appCustomApplication{
		Id:                 created.Id,
		CreatedBy:          actor.Username,
		Project:            project,
		CreatedAt:          created.CreatedAt,
		Provider:           request.ServiceProvider,
		PublishedToProject: request.PublishedToProject,
		FlavorName:         request.FlavorName,
		GroupId:            group.Id,
		CategoryId:         category.Id,
		Source:             string(source),
		Application:        application,
	}
	appCustomCache.Apps[created.Id] = custom
	appCustomCache.AppKeys[appCustomApplicationKey(workspace, request.Name, request.Version, request.ServiceProvider)] = created.Id
	return nil
}

func appCustomRetrieveApplication(actor rpc.Actor, name, version, provider string, discovery AppDiscovery, flags AppCatalogFlags) (orcapi.Application, bool) {
	appCustomCache.Mu.RLock()
	defer appCustomCache.Mu.RUnlock()
	var requested *appCustomApplication
	var candidates []*appCustomApplication
	for _, app := range appCustomCache.Apps {
		nameDoesNotMatch := app.Application.Metadata.Name != name
		providerDoesNotMatch := provider != "" && app.Provider != provider
		outsideWorkspace := !appCustomBelongsToActorsWorkspace(actor, app.CreatedBy, app.Project)
		if nameDoesNotMatch || providerDoesNotMatch || outsideWorkspace {
			continue
		}
		category := appCustomCache.Categories[app.CategoryId]
		if category == nil || !appCustomCanReadApplication(actor, app, category) {
			continue
		}
		if discovery.Mode == orcapi.CatalogDiscoveryModeSelected && (!discovery.Selected.Present || discovery.Selected.Value != app.Provider) {
			continue
		}
		if discovery.Mode == orcapi.CatalogDiscoveryModeAvailable && !slices.Contains(appRelevantProvidersForUser(actor.Username, actor.Project), app.Provider) {
			continue
		}
		if version != "" && app.Application.Metadata.Version == version {
			requested = app
		}
		candidates = append(candidates, app)
	}
	if version != "" && requested == nil {
		return orcapi.Application{}, false
	}
	if len(candidates) == 0 {
		return orcapi.Application{}, false
	}
	sort.Slice(candidates, func(i, j int) bool { return candidates[i].CreatedAt.After(candidates[j].CreatedAt) })
	selected := candidates[0]
	if requested != nil {
		selected = requested
	}
	result := appCustomApplicationToApi(actor, selected)
	for _, candidate := range candidates {
		result.Versions = append(result.Versions, candidate.Application.Metadata.Version)
	}
	if flags&(AppCatalogIncludeGroups|AppCatalogIncludeCategories) != 0 {
		customGroup := appCustomCache.Groups[selected.GroupId]
		if customGroup != nil {
			logo := customGroup.Logo
			result.Metadata.Group = orcapi.ApplicationGroup{
				Metadata: orcapi.ApplicationGroupMetadata{
					Id:     -int(customGroup.Id),
					Origin: orcapi.CatalogOriginCustom,
				},
				Specification: orcapi.ApplicationGroupSpecification{
					Title:       customGroup.Title,
					Description: customGroup.Description,
					Logo:        &logo,
				},
			}
			if flags&AppCatalogIncludeCategories != 0 {
				if category := appCustomCache.Categories[selected.CategoryId]; category != nil {
					result.Metadata.Group.Specification.Categories = []int{-int(category.Id)}
				}
			}
		}
	}
	return result, true
}

func appCustomUpdateApplication(actor rpc.Actor, request orcapi.AppCatalogUpdateCustomApplicationRequest) *util.HttpError {
	appCustomCache.Mu.RLock()
	var target *appCustomApplication
	for _, app := range appCustomCache.Apps {
		if app.Application.Metadata.Name == request.Name && app.Application.Metadata.Version == request.Version &&
			app.Provider == request.ServiceProvider && appCustomBelongsToActorsWorkspace(actor, app.CreatedBy, app.Project) {
			category := appCustomCache.Categories[app.CategoryId]
			if category == nil {
				break
			}
			cannotRead := !appCustomCategoryHasPermission(actor, category, orcapi.PermissionRead)
			cannotEdit := !appCustomIsAdmin(actor) && app.CreatedBy != actor.Username
			if cannotRead || cannotEdit {
				break
			}
			target = app
			break
		}
	}
	if target == nil {
		appCustomCache.Mu.RUnlock()
		return util.HttpErr(http.StatusNotFound, "application not found")
	}
	id := target.Id
	wasPublished := target.PublishedToProject
	image := target.Application.Invocation.Tool.Tool.Value.Description.Image
	appCustomCache.Mu.RUnlock()
	if request.PublishedToProject && !actor.Project.Present {
		return util.HttpErr(http.StatusBadRequest, "personal applications cannot be published")
	}
	if request.PublishedToProject && !wasPublished {
		if _, imageErr := applicationVariantValidateImage(actor, request.ServiceProvider, image, false, true); imageErr != nil {
			return util.HttpErr(http.StatusBadRequest, "the image is no longer owned by the workspace")
		}
	}
	appCustomCache.Mu.Lock()
	defer appCustomCache.Mu.Unlock()
	target = appCustomCache.Apps[id]
	if target == nil || target.Provider != request.ServiceProvider {
		return util.HttpErr(http.StatusNotFound, "application not found")
	}
	category := appCustomCache.Categories[target.CategoryId]
	if category == nil {
		return util.HttpErr(http.StatusNotFound, "application not found")
	}
	cannotRead := !appCustomCategoryHasPermission(actor, category, orcapi.PermissionRead)
	cannotEdit := !appCustomIsAdmin(actor) && target.CreatedBy != actor.Username
	if cannotRead || cannotEdit {
		return util.HttpErr(http.StatusNotFound, "application not found")
	}
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update app_store.custom_applications
				set published_to_project = :published
				where id = :id
			`,
			db.Params{
				"published": request.PublishedToProject,
				"id":        target.Id,
			},
		)
	})
	target.PublishedToProject = request.PublishedToProject
	target.Application.Metadata.PublishedToProject.Set(request.PublishedToProject)
	return nil
}

func appCustomDeleteApplication(actor rpc.Actor, request orcapi.AppCatalogDeleteCustomApplicationRequest) *util.HttpError {
	appCustomCache.Mu.RLock()
	var target *appCustomApplication
	for _, app := range appCustomCache.Apps {
		if app.Application.Metadata.Name != request.Name || app.Application.Metadata.Version != request.Version ||
			app.Provider != request.ServiceProvider || !appCustomBelongsToActorsWorkspace(actor, app.CreatedBy, app.Project) {
			continue
		}
		category := appCustomCache.Categories[app.CategoryId]
		if category == nil {
			break
		}
		cannotRead := !appCustomCategoryHasPermission(actor, category, orcapi.PermissionRead)
		cannotDelete := !appCustomIsAdmin(actor) && app.CreatedBy != actor.Username
		if cannotRead || cannotDelete {
			break
		}
		target = app
		break
	}
	if target == nil {
		appCustomCache.Mu.RUnlock()
		return util.HttpErr(http.StatusNotFound, "application not found")
	}
	id := target.Id
	name := target.Application.Metadata.Name
	version := target.Application.Metadata.Version
	provider := target.Provider
	workspace := appCustomWorkspaceEx(target.CreatedBy, target.Project)
	appCustomCache.Mu.RUnlock()

	jobIds, deleted := db.NewTx2(func(tx *db.Transaction) ([]ResourceId, bool) {
		var rewritten []ResourceId
		rows := db.Select[struct{ Resource int64 }](
			tx,
			`
					update app_orchestrator.jobs j
					set application_name = 'unknown', application_version = 'unknown'
					from provider.resource r
					where
						j.resource = r.id
						and j.application_name = :name
						and j.application_version = :version
						and r.provider = :provider
						and coalesce(r.project, r.created_by) = :workspace
					returning j.resource
				`,
			db.Params{
				"name":      name,
				"version":   version,
				"provider":  provider,
				"workspace": workspace,
			},
		)
		for _, row := range rows {
			rewritten = append(rewritten, ResourceId(row.Resource))
		}
		_, found := db.Get[struct{ Id int64 }](
			tx,
			`delete from app_store.custom_applications where id = :id returning id`,
			db.Params{
				"id": id,
			},
		)
		return rewritten, found
	})
	if !deleted {
		return util.HttpErr(http.StatusNotFound, "application not found")
	}
	appCustomCache.Mu.Lock()
	delete(appCustomCache.AppKeys, appCustomApplicationKey(workspace, name, version, provider))
	delete(appCustomCache.Apps, id)
	appCustomCache.Mu.Unlock()
	jobRewriteApplicationInCache(jobIds, orcapi.NameAndVersion{Name: "unknown", Version: "unknown"})
	return nil
}

func appCustomRetrieveApplicationForWorkspace(workspace, name, version, provider string) (orcapi.Application, bool) {
	appCustomCache.Mu.RLock()
	defer appCustomCache.Mu.RUnlock()
	id, ok := appCustomCache.AppKeys[appCustomApplicationKey(workspace, name, version, provider)]
	if !ok {
		return orcapi.Application{}, false
	}
	app := appCustomCache.Apps[id]
	if app == nil {
		return orcapi.Application{}, false
	}
	return appCustomApplicationToApi(rpc.ActorSystem, app), true
}

// Group and category deletion
// =====================================================================================================================
// Groups can be deleted by their creator or a workspace administrator; categories require an administrator. Both must
// be empty of custom applications. The database checks emptiness in the delete statement, so a concurrent application
// insert produces a conflict instead of leaving a dangling reference.

func appCustomDeleteGroup(actor rpc.Actor, apiId int) *util.HttpError {
	if apiId >= 0 {
		return util.HttpErr(http.StatusBadRequest, "invalid group ID")
	}
	id := int64(-apiId)
	appCustomCache.Mu.Lock()
	defer appCustomCache.Mu.Unlock()
	group := appCustomCache.Groups[id]
	if group == nil {
		return util.HttpErr(http.StatusNotFound, "group not found")
	}
	outsideWorkspace := !appCustomBelongsToActorsWorkspace(actor, group.CreatedBy, group.Project)
	cannotDelete := !appCustomIsAdmin(actor) && group.CreatedBy != actor.Username
	if outsideWorkspace || cannotDelete {
		return util.HttpErr(http.StatusNotFound, "group not found")
	}
	for _, app := range appCustomCache.Apps {
		if app.GroupId == id {
			return util.HttpErr(http.StatusConflict, "group is not empty")
		}
	}
	_, deleted := db.NewTx2(func(tx *db.Transaction) (struct{ Id int64 }, bool) {
		return db.Get[struct{ Id int64 }](
			tx,
			`
				delete from app_store.custom_application_groups
				where
					id = :id
					and not exists (
						select 1
						from app_store.custom_applications
						where custom_group_id = :id
					)
				returning id
			`,
			db.Params{
				"id": id,
			},
		)
	})
	if !deleted {
		return util.HttpErr(http.StatusConflict, "group is not empty")
	}
	delete(appCustomCache.Groups, id)
	return nil
}

func appCustomDeleteCategory(actor rpc.Actor, apiId int) *util.HttpError {
	if apiId >= 0 || !appCustomIsAdmin(actor) {
		return util.HttpErr(http.StatusForbidden, "permission denied")
	}
	id := int64(-apiId)
	appCustomCache.Mu.Lock()
	defer appCustomCache.Mu.Unlock()
	category := appCustomCache.Categories[id]
	if category == nil || !appCustomBelongsToActorsWorkspace(actor, category.CreatedBy, category.Project) {
		return util.HttpErr(http.StatusNotFound, "category not found")
	}
	for _, app := range appCustomCache.Apps {
		if app.CategoryId == id {
			return util.HttpErr(http.StatusConflict, "category cannot be deleted because it contains applications")
		}
	}
	_, deleted := db.NewTx2(func(tx *db.Transaction) (struct{ Id int64 }, bool) {
		return db.Get[struct{ Id int64 }](
			tx,
			`
				delete from app_store.custom_application_categories
				where
					id = :id
					and not exists (
						select 1
						from app_store.custom_applications
						where custom_category_id = :id
					)
				returning id
			`,
			db.Params{
				"id": id,
			},
		)
	})
	if !deleted {
		return util.HttpErr(http.StatusConflict, "category cannot be deleted because it contains applications")
	}
	delete(appCustomCache.Categories, id)
	return nil
}

// Managed catalog integration
// =====================================================================================================================
// These functions provide the custom catalog used by app_catalog.go. They append only visible custom memberships and
// use normal discovery modes for provider filtering.

func appCustomRetrieveGroupForCatalog(actor rpc.Actor, id AppGroupId, discovery AppDiscovery, flags AppCatalogFlags) (orcapi.ApplicationGroup, bool) {
	appCustomCache.Mu.RLock()
	defer appCustomCache.Mu.RUnlock()
	custom := appCustomCache.Groups[int64(-id)]
	if id >= 0 || custom == nil || !appCustomBelongsToActorsWorkspace(actor, custom.CreatedBy, custom.Project) {
		return orcapi.ApplicationGroup{}, false
	}
	logo := custom.Logo
	result := orcapi.ApplicationGroup{
		Metadata: orcapi.ApplicationGroupMetadata{
			Id:     int(id),
			Origin: orcapi.CatalogOriginCustom,
		},
		Specification: orcapi.ApplicationGroupSpecification{
			Title:       custom.Title,
			Description: custom.Description,
			ColorReplacement: orcapi.ColorReplacements{
				Light: map[int]int{},
				Dark:  map[int]int{},
			},
			Logo: &logo,
		},
	}
	if flags&AppCatalogIncludeApps != 0 {
		newestByName := map[string]*appCustomApplication{}
		for _, app := range appCustomCache.Apps {
			category := appCustomCache.Categories[app.CategoryId]
			if app.GroupId != custom.Id || category == nil || !appCustomCanReadApplication(actor, app, category) || !appCustomDiscoveryAllows(actor, app, discovery) {
				continue
			}
			name := app.Application.Metadata.Name
			current := newestByName[name]
			if current == nil || app.CreatedAt.After(current.CreatedAt) {
				newestByName[name] = app
			}
		}
		for _, app := range newestByName {
			result.Status.Applications = append(result.Status.Applications, appCustomApplicationToApi(actor, app))
		}
		slices.SortFunc(result.Status.Applications, func(a, b orcapi.Application) int {
			return strings.Compare(a.Metadata.FlavorName.GetOrDefault(a.Metadata.Title), b.Metadata.FlavorName.GetOrDefault(b.Metadata.Title))
		})
		if len(result.Status.Applications) == 1 {
			result.Specification.DefaultFlavor = result.Status.Applications[0].Metadata.Name
		}
	}
	if flags&AppCatalogIncludeCategories != 0 {
		categories := map[int]bool{}
		for _, app := range appCustomCache.Apps {
			category := appCustomCache.Categories[app.CategoryId]
			if app.GroupId != custom.Id || category == nil || !appCustomCanReadApplication(actor, app, category) {
				continue
			}
			categories[-int(category.Id)] = true
		}
		for categoryId := range categories {
			result.Specification.Categories = append(result.Specification.Categories, categoryId)
		}
		sort.Ints(result.Specification.Categories)
	}
	if flags&AppCatalogIncludeApps != 0 && len(result.Status.Applications) == 0 {
		return orcapi.ApplicationGroup{}, false
	}
	return result, true
}

func appCustomDiscoveryAllows(actor rpc.Actor, app *appCustomApplication, discovery AppDiscovery) bool {
	if discovery.Mode == orcapi.CatalogDiscoveryModeSelected {
		return discovery.Selected.Present && discovery.Selected.Value == app.Provider
	}
	if discovery.Mode == orcapi.CatalogDiscoveryModeAvailable {
		return slices.Contains(appRelevantProvidersForUser(actor.Username, actor.Project), app.Provider)
	}
	return true
}

func appCustomCategories(actor rpc.Actor, discovery AppDiscovery, flags AppCatalogFlags) []orcapi.ApplicationCategory {
	appCustomCache.Mu.RLock()
	defer appCustomCache.Mu.RUnlock()
	var result []orcapi.ApplicationCategory
	for _, category := range appCustomCache.Categories {
		if !appCustomCategoryHasPermission(actor, category, orcapi.PermissionRead) {
			continue
		}
		apiCategory := orcapi.ApplicationCategory{
			Metadata: orcapi.AppCategoryMetadata{
				Id:     -int(category.Id),
				Origin: orcapi.CatalogOriginCustom,
			},
			Specification: orcapi.AppCategorySpecification{
				Title:       category.Title,
				Description: util.OptStringIfNotEmpty(category.Description),
			},
		}
		if flags&AppCatalogIncludeGroups != 0 {
			groupIds := map[int64]bool{}
			for _, app := range appCustomCache.Apps {
				if app.CategoryId == category.Id && appCustomCanReadApplication(actor, app, category) && appCustomDiscoveryAllows(actor, app, discovery) {
					groupIds[app.GroupId] = true
				}
			}
			for groupId := range groupIds {
				group := appCustomCache.Groups[groupId]
				if group == nil {
					continue
				}
				logo := group.Logo
				apiGroup := orcapi.ApplicationGroup{
					Metadata: orcapi.ApplicationGroupMetadata{
						Id:     -int(group.Id),
						Origin: orcapi.CatalogOriginCustom,
					},
					Specification: orcapi.ApplicationGroupSpecification{
						Title:       group.Title,
						Description: group.Description,
						ColorReplacement: orcapi.ColorReplacements{
							Light: map[int]int{},
							Dark:  map[int]int{},
						},
						Logo: &logo,
					},
				}
				for _, app := range appCustomCache.Apps {
					if app.GroupId == group.Id && app.CategoryId == category.Id && appCustomCanReadApplication(actor, app, category) && appCustomDiscoveryAllows(actor, app, discovery) {
						apiGroup.Status.Applications = append(apiGroup.Status.Applications, appCustomApplicationToApi(actor, app))
					}
				}
				if len(apiGroup.Status.Applications) != 0 {
					if flags&AppCatalogIncludeApps == 0 {
						apiGroup.Status.Applications = util.NonNilSlice[orcapi.Application](nil)
					} else if len(apiGroup.Status.Applications) == 1 {
						apiGroup.Specification.DefaultFlavor = apiGroup.Status.Applications[0].Metadata.Name
					}
					apiCategory.Status.Groups = append(apiCategory.Status.Groups, apiGroup)
				}
			}
		}
		result = append(result, apiCategory)
	}
	sort.Slice(result, func(i, j int) bool {
		return strings.ToLower(result[i].Specification.Title) < strings.ToLower(result[j].Specification.Title)
	})
	return result
}

func appCustomSearch(actor rpc.Actor, terms []string, discovery AppDiscovery) []orcapi.Application {
	available := map[string]bool{}
	if discovery.Mode == orcapi.CatalogDiscoveryModeAvailable {
		for _, provider := range appRelevantProvidersForUser(actor.Username, actor.Project) {
			available[provider] = true
		}
	}
	groupIds := map[int64]bool{}
	appCustomCache.Mu.RLock()
	for _, group := range appCustomCache.Groups {
		if !appCustomBelongsToActorsWorkspace(actor, group.CreatedBy, group.Project) {
			continue
		}
		text := strings.ToLower(group.Title + " " + group.Description)
		for _, app := range appCustomCache.Apps {
			if app.GroupId != group.Id {
				continue
			}
			category := appCustomCache.Categories[app.CategoryId]
			if category == nil || !appCustomCanReadApplication(actor, app, category) {
				continue
			}
			if discovery.Mode == orcapi.CatalogDiscoveryModeSelected && (!discovery.Selected.Present || discovery.Selected.Value != app.Provider) {
				continue
			}
			if discovery.Mode == orcapi.CatalogDiscoveryModeAvailable && !available[app.Provider] {
				continue
			}
			text += " " + strings.ToLower(app.FlavorName+" "+app.Application.Metadata.Name+" "+app.Application.Metadata.Title+" "+app.Application.Metadata.Description)
		}
		matches := true
		for _, term := range terms {
			if !strings.Contains(text, strings.ToLower(term)) {
				matches = false
				break
			}
		}
		if matches {
			groupIds[group.Id] = true
		}
	}
	appCustomCache.Mu.RUnlock()

	var result []orcapi.Application
	for id := range groupIds {
		group, _, ok := AppRetrieveGroup(actor, AppGroupId(-id), discovery, AppCatalogIncludeApps)
		if ok && len(group.Status.Applications) != 0 {
			result = append(result, group.Status.Applications[0])
		}
	}
	return result
}

func appCustomRetrieveLogo(actor rpc.Actor, request orcapi.AppCatalogRetrieveCustomLogoRequest) (orcapi.ApplicationGroupLogo, *util.HttpError) {
	appCustomCache.Mu.RLock()
	defer appCustomCache.Mu.RUnlock()
	if request.GroupId < 0 {
		group := appCustomCache.Groups[int64(-request.GroupId)]
		if group != nil && appCustomBelongsToActorsWorkspace(actor, group.CreatedBy, group.Project) &&
			(appCustomIsAdmin(actor) || group.CreatedBy == actor.Username || appCustomCanReadGroup(actor, group)) {
			return group.Logo, nil
		}
	}
	if request.ApplicationName != "" {
		var newest *appCustomApplication
		for _, app := range appCustomCache.Apps {
			if app.Application.Metadata.Name != request.ApplicationName || !appCustomBelongsToActorsWorkspace(actor, app.CreatedBy, app.Project) {
				continue
			}
			category := appCustomCache.Categories[app.CategoryId]
			if category != nil && appCustomCanReadApplication(actor, app, category) &&
				(newest == nil || app.CreatedAt.After(newest.CreatedAt) || app.CreatedAt.Equal(newest.CreatedAt) && app.Id > newest.Id) {
				newest = app
			}
		}
		if newest != nil {
			if group := appCustomCache.Groups[newest.GroupId]; group != nil {
				return group.Logo, nil
			}
		}
	}
	return orcapi.ApplicationGroupLogo{}, util.HttpErr(http.StatusNotFound, "logo not found")
}

// RPC registration
// =====================================================================================================================
// RPC handlers translate signed API IDs and keep cache locks around each management snapshot. Business rules remain in
// the functions above so catalog integration and RPC paths use the same authorization and lifecycle behavior. Browse
// responses filter first and paginate the visible result.

func appCustomPageBounds(itemsPerPage int, next util.Option[string], length int) (int, int, util.Option[string]) {
	limit := min(max(itemsPerPage, 1), 250)
	offset, _ := strconv.Atoi(next.GetOrDefault("0"))
	offset = min(max(offset, 0), length)
	end := min(offset+limit, length)
	resultNext := util.OptNone[string]()
	if end < length {
		resultNext.Set(strconv.Itoa(end))
	}
	return offset, end, resultNext
}

func appCustomInitRpc() {
	orcapi.AppsCreateCustomGroup.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogCreateCustomGroupRequest) (fndapi.FindByIntId, *util.HttpError) {
		return appCustomCreateGroup(info.Actor, request)
	})
	orcapi.AppsRetrieveCustomGroup.Handler(func(info rpc.RequestInfo, request fndapi.FindByIntId) (orcapi.AppCatalogCustomGroup, *util.HttpError) {
		appCustomCache.Mu.RLock()
		defer appCustomCache.Mu.RUnlock()
		group := appCustomCache.Groups[int64(-request.Id)]
		if request.Id >= 0 || group == nil {
			return orcapi.AppCatalogCustomGroup{}, util.HttpErr(http.StatusNotFound, "group not found")
		}
		if !appCustomBelongsToActorsWorkspace(info.Actor, group.CreatedBy, group.Project) {
			return orcapi.AppCatalogCustomGroup{}, util.HttpErr(http.StatusNotFound, "group not found")
		}
		if !appCustomCanReadGroup(info.Actor, group) {
			return orcapi.AppCatalogCustomGroup{}, util.HttpErr(http.StatusNotFound, "group not found")
		}
		return appCustomGroupToApi(group), nil
	})
	orcapi.AppsBrowseCustomGroups.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogBrowseCustomGroupsRequest) (fndapi.PageV2[orcapi.AppCatalogCustomGroup], *util.HttpError) {
		appCustomCache.Mu.RLock()
		defer appCustomCache.Mu.RUnlock()
		result := fndapi.PageV2[orcapi.AppCatalogCustomGroup]{ItemsPerPage: request.ItemsPerPage}
		for _, group := range appCustomCache.Groups {
			if !appCustomBelongsToActorsWorkspace(info.Actor, group.CreatedBy, group.Project) {
				continue
			}
			if !appCustomCanReadGroup(info.Actor, group) {
				continue
			}
			result.Items = append(result.Items, appCustomGroupToApi(group))
		}
		sort.Slice(result.Items, func(i, j int) bool { return result.Items[i].Id > result.Items[j].Id })
		start, end, next := appCustomPageBounds(request.ItemsPerPage, request.Next, len(result.Items))
		result.Items = result.Items[start:end]
		result.ItemsPerPage = end - start
		result.Next = next
		return result, nil
	})
	orcapi.AppsDeleteCustomGroup.Handler(func(info rpc.RequestInfo, request fndapi.FindByIntId) (util.Empty, *util.HttpError) {
		return util.Empty{}, appCustomDeleteGroup(info.Actor, request.Id)
	})
	orcapi.AppsUpdateCustomGroupLogo.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogUpdateCustomGroupLogoRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, appCustomUpdateGroupLogo(info.Actor, request)
	})
	orcapi.AppsUpdateCustomGroup.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogUpdateCustomGroupRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, appCustomUpdateGroup(info.Actor, request)
	})
	orcapi.AppsRetrieveCustomLogo.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogRetrieveCustomLogoRequest) (orcapi.ApplicationGroupLogo, *util.HttpError) {
		return appCustomRetrieveLogo(info.Actor, request)
	})
	orcapi.AppsCreateCustomCategory.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogCreateCustomCategoryRequest) (fndapi.FindByIntId, *util.HttpError) {
		return appCustomCreateCategory(info.Actor, request)
	})
	orcapi.AppsRetrieveCustomCategory.Handler(func(info rpc.RequestInfo, request fndapi.FindByIntId) (orcapi.AppCatalogCustomCategory, *util.HttpError) {
		appCustomCache.Mu.RLock()
		defer appCustomCache.Mu.RUnlock()
		category := appCustomCache.Categories[int64(-request.Id)]
		if request.Id >= 0 || category == nil || !appCustomCategoryHasPermission(info.Actor, category, orcapi.PermissionRead) {
			return orcapi.AppCatalogCustomCategory{}, util.HttpErr(http.StatusNotFound, "category not found")
		}
		return appCustomCategoryToApi(info.Actor, category), nil
	})
	orcapi.AppsBrowseCustomCategories.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogBrowseCustomCategoriesRequest) (fndapi.PageV2[orcapi.AppCatalogCustomCategory], *util.HttpError) {
		appCustomCache.Mu.RLock()
		defer appCustomCache.Mu.RUnlock()
		result := fndapi.PageV2[orcapi.AppCatalogCustomCategory]{ItemsPerPage: request.ItemsPerPage}
		for _, category := range appCustomCache.Categories {
			if appCustomCategoryHasPermission(info.Actor, category, orcapi.PermissionRead) {
				result.Items = append(result.Items, appCustomCategoryToApi(info.Actor, category))
			}
		}
		sort.Slice(result.Items, func(i, j int) bool { return result.Items[i].Id > result.Items[j].Id })
		start, end, next := appCustomPageBounds(request.ItemsPerPage, request.Next, len(result.Items))
		result.Items = result.Items[start:end]
		result.ItemsPerPage = end - start
		result.Next = next
		return result, nil
	})
	orcapi.AppsDeleteCustomCategory.Handler(func(info rpc.RequestInfo, request fndapi.FindByIntId) (util.Empty, *util.HttpError) {
		return util.Empty{}, appCustomDeleteCategory(info.Actor, request.Id)
	})
	orcapi.AppsUpdateCustomCategoryAcl.Handler(func(info rpc.RequestInfo, request orcapi.UpdatedAcl) (util.Empty, *util.HttpError) {
		return util.Empty{}, appCustomUpdateCategoryAcl(info.Actor, request)
	})
	orcapi.AppsCreateCustom.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogCreateCustomApplicationRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, appCustomCreateApplication(info.Actor, request)
	})
	orcapi.AppsUpdateCustom.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogUpdateCustomApplicationRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, appCustomUpdateApplication(info.Actor, request)
	})
	orcapi.AppsDeleteCustom.Handler(func(info rpc.RequestInfo, request orcapi.AppCatalogDeleteCustomApplicationRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, appCustomDeleteApplication(info.Actor, request)
	})
}
