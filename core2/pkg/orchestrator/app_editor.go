package orchestrator

import (
	"bytes"
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"slices"
	"strconv"
	"strings"
	"time"

	"gopkg.in/yaml.v3"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Application editor
// =====================================================================================================================
// This file implements the editor-specific operations required for creating, editing, and previewing UCloud
// applications. Saving uses the existing managed upload and custom application creation APIs.
//
// The editor exposes four operations:
//
// - Validate application YAML (v2 only) and editor-specific metadata
// - Retrieve canonical source for editing or forking
// - Describe whether a workspace can create custom applications
// - Render a validated application as a provider-specific invocation preview
//
// Source and validation
// ---------------------------------------------------------------------------------------------------------------------
// Requests use version 2 application YAML. The editor parses and normalizes this source before it performs catalog or
// provider work. Validation errors carry a field path and, when the source contains the field, its line and column.
//
// Managed applications are global and require a UCloud administrator to create or edit. Custom applications use only
// container software and add provider, flavor, group, and category metadata. Their internal `custom-` name prefix is
// hidden from editor users so that the editor can use the same logical name format for both application kinds.
//
// Source recovery
// ---------------------------------------------------------------------------------------------------------------------
// Managed applications retain their canonical source when possible. Older entries are recovered from persisted source
// or a normalized application projection. Forks remove comments for non-administrators so source
// annotations from the managed catalog are not disclosed.
//
// Preview rendering
// ---------------------------------------------------------------------------------------------------------------------
// Rendering validates the application and job together, enforces a per-user preview limit, and delegates invocation
// generation to the selected provider. Rendering is preview-only: it does not create a job or change catalog state.
//
// Authorization remains owned by the catalog and custom-application subsystems. This file applies their decisions to
// editor requests and shapes the editor responses.

// Shared helpers
// =====================================================================================================================

func appEditorIsProjectAdmin(actor rpc.Actor) bool {
	return actor.Role == rpc.RoleAdmin ||
		(actor.Project.Present && actor.Membership[actor.Project.Value].Satisfies(rpc.ProjectRoleAdmin))
}

func appEditorLogicalName(name string) string {
	return strings.TrimPrefix(name, "custom-")
}

func appEditorInternalName(name string) string {
	if strings.HasPrefix(name, "custom-") {
		return name
	}
	return "custom-" + name
}

func appEditorError(code, path, message string, node *yaml.Node) orcapi.AppEditorValidationError {
	result := orcapi.AppEditorValidationError{
		Code:    code,
		Path:    path,
		Message: message,
	}
	if location := appEditorFindLocation(node, path); location != nil {
		result.Location.Set(*location)
	}
	return result
}

func appEditorFindLocation(node *yaml.Node, path string) *orcapi.AppEditorSourceLocation {
	if node == nil {
		return nil
	}
	if node.Kind == yaml.DocumentNode && len(node.Content) != 0 {
		node = node.Content[0]
	}
	current := node
	for _, component := range strings.Split(path, ".") {
		component, indexText, indexed := strings.Cut(component, "[")
		if component == "custom" || component == "" {
			return nil
		}
		if current.Kind != yaml.MappingNode {
			break
		}
		found := false
		for index := 0; index+1 < len(current.Content); index += 2 {
			if current.Content[index].Value == component {
				current = current.Content[index+1]
				found = true
				break
			}
		}
		if !found {
			return nil
		}
		if indexed {
			indexText = strings.TrimSuffix(indexText, "]")
			index, err := strconv.Atoi(indexText)
			if err != nil || current.Kind != yaml.SequenceNode || index < 0 || index >= len(current.Content) {
				return nil
			}
			current = current.Content[index]
		}
	}
	if current.Line == 0 {
		return nil
	}
	return &orcapi.AppEditorSourceLocation{
		Line:   current.Line,
		Column: current.Column,
	}
}

// Validation
// =====================================================================================================================
// Custom applications add workspace, provider, and image checks after the common YAML normalization step.

func appEditorParseSource(request orcapi.AppEditorValidateRequest) (orcapi.A2Yaml, *yaml.Node, []orcapi.AppEditorValidationError) {
	var node yaml.Node
	if err := yaml.Unmarshal([]byte(request.Source), &node); err != nil {
		return orcapi.A2Yaml{}, &node, []orcapi.AppEditorValidationError{
			appEditorError("INVALID_YAML", "", fmt.Sprintf("Invalid YAML: %s", err), &node),
		}
	}
	var header struct {
		Application string `yaml:"application"`
	}
	if err := node.Decode(&header); err != nil || header.Application != "v2" {
		return orcapi.A2Yaml{}, &node, []orcapi.AppEditorValidationError{
			appEditorError("INVALID_APPLICATION_VERSION", "application", "Application must be v2", &node),
		}
	}
	var source orcapi.A2Yaml
	if err := node.Decode(&source); err != nil {
		path := appEditorValidationPath(err.Error(), &node)
		return orcapi.A2Yaml{}, &node, []orcapi.AppEditorValidationError{
			appEditorError("INVALID_YAML", path, fmt.Sprintf("Invalid application source: %s", err), &node),
		}
	}
	if request.Kind == orcapi.AppEditorApplicationKindCustom {
		if strings.TrimSpace(source.Name) == "" || source.Name == "custom-" {
			return source, &node, []orcapi.AppEditorValidationError{
				appEditorError("INVALID_FIELD", "name", "Application name is required", &node),
			}
		}
		source.Name = appEditorInternalName(source.Name)
	}
	return source, &node, nil
}

func appEditorValidationPath(message string, node *yaml.Node) string {
	if node == nil {
		return ""
	}
	if node.Kind == yaml.DocumentNode && len(node.Content) != 0 {
		node = node.Content[0]
	}
	var paths []string
	var collect func(*yaml.Node, string)
	collect = func(current *yaml.Node, path string) {
		switch current.Kind {
		case yaml.MappingNode:
			for i := 0; i+1 < len(current.Content); i += 2 {
				childPath := current.Content[i].Value
				if path != "" {
					childPath = path + "." + childPath
				}
				paths = append(paths, childPath)
				collect(current.Content[i+1], childPath)
			}
		case yaml.SequenceNode:
			for i, child := range current.Content {
				childPath := fmt.Sprintf("%s[%d]", path, i)
				paths = append(paths, childPath)
				collect(child, childPath)
			}
		}
	}
	collect(node, "")
	slices.SortFunc(paths, func(a, b string) int { return len(b) - len(a) })
	for _, path := range paths {
		if strings.Contains(message, path) {
			return path
		}
		parameterPath := strings.TrimPrefix(path, "parameters.")
		if parameterPath != path && strings.Contains(message, parameterPath) {
			return path
		}
	}
	return ""
}

func appEditorValidate(actor rpc.Actor, request orcapi.AppEditorValidateRequest) orcapi.AppEditorValidateResponse {
	source, node, errors := appEditorParseSource(request)
	if len(errors) != 0 {
		return orcapi.AppEditorValidateResponse{Errors: errors}
	}

	application, normalizeErr := source.Normalize()
	if normalizeErr != nil {
		path := appEditorValidationPath(normalizeErr.Why, node)
		return orcapi.AppEditorValidateResponse{
			Errors: []orcapi.AppEditorValidationError{
				appEditorError("INVALID_FIELD", path, normalizeErr.Why, node),
			},
		}
	}

	if request.Kind == orcapi.AppEditorApplicationKindManaged {
		if actor.Role != rpc.RoleAdmin {
			errors = append(errors, appEditorError("ADMIN_REQUIRED", "", "Only UCloud administrators can create managed applications", node))
		}
		if _, exists := appRetrieve(source.Name, source.Version); exists {
			errors = append(errors, appEditorError("VERSION_CONFLICT", "version", "An application with this version already exists", node))
		}
		if strings.HasPrefix(source.Name, "variant-") || strings.HasPrefix(source.Name, "custom-") {
			errors = append(errors, appEditorError("RESERVED_NAME", "name", "This application name uses a reserved prefix", node))
		}
	} else if request.Kind == orcapi.AppEditorApplicationKindCustom {
		customErrors, imageDigest, custom := appEditorValidateCustom(actor, source, request.Custom, node)
		errors = append(errors, customErrors...)
		if len(customErrors) == 0 {
			application.Metadata.Origin = orcapi.CatalogOriginCustom
			application.Metadata.PublishedToProject.Set(custom.PublishedToProject)
			application.Metadata.FlavorName.Set(custom.FlavorName)
			application.Metadata.Group.Metadata.Id = custom.GroupId
			application.Invocation.Tool.Tool.Value.Description.Image = imageDigest
			application.Invocation.Tool.Tool.Value.Description.Container = imageDigest
			application.Invocation.Tool.Tool.Value.Description.SupportedProviders = []string{custom.ServiceProvider}
		}
		application.Metadata.Name = appEditorLogicalName(application.Metadata.Name)
	} else {
		errors = append(errors, appEditorError("INVALID_APPLICATION_KIND", "", "Invalid application kind", node))
	}

	result := orcapi.AppEditorValidateResponse{
		Errors: util.NonNilSlice(errors),
	}
	if len(errors) == 0 {
		result.Application.Set(application)
	}
	return result
}

func appEditorValidateCustom(
	actor rpc.Actor,
	source orcapi.A2Yaml,
	metadata util.Option[orcapi.AppEditorCustomMetadata],
	node *yaml.Node,
) ([]orcapi.AppEditorValidationError, string, orcapi.AppEditorCustomMetadata) {
	var errors []orcapi.AppEditorValidationError
	if !metadata.Present {
		return []orcapi.AppEditorValidationError{appEditorError("CUSTOM_METADATA_REQUIRED", "custom", "Custom placement metadata is required", node)}, "", orcapi.AppEditorCustomMetadata{}
	}
	custom := metadata.Value
	if source.Ucx.Present || source.Modules.Present || source.Documentation.Present || len(source.Extensions) != 0 ||
		source.Software.Type != orcapi.A2SoftwareContainer || source.Software.Container == nil {
		errors = append(errors, appEditorError("CUSTOM_FEATURE_UNSUPPORTED", "software", "Custom applications must use Container without UCX, modules, documentation, or extensions", node))
	}
	if !actor.Project.Present && custom.PublishedToProject {
		errors = append(errors, appEditorError("PUBLICATION_NOT_ALLOWED", "custom.publishedToProject", "Personal applications cannot be published", node))
	}
	flavorErr := appCustomNormalizeFlavorName(&custom.FlavorName)
	flavorValid := flavorErr == nil
	if flavorErr != nil {
		errors = append(errors, appEditorError("INVALID_FIELD", "custom.flavorName", flavorErr.Why, node))
	}

	appCustomCache.Mu.RLock()
	category := appCustomCache.Categories[int64(-custom.CategoryId)]
	group := appCustomCache.Groups[int64(-custom.GroupId)]
	categoryAllowed := custom.CategoryId < 0 && category != nil && appCustomCategoryHasPermission(actor, category, orcapi.PermissionEdit)
	groupAllowed := custom.GroupId < 0 && group != nil && appCustomBelongsToActorsWorkspace(actor, group.CreatedBy, group.Project)
	versionAvailable := appCustomCache.AppKeys[appCustomApplicationKey(appCustomWorkspace(actor), source.Name, source.Version, custom.ServiceProvider)] == 0
	flavorAvailable := groupAllowed && flavorValid && appCustomFlavorAvailable(appCustomWorkspace(actor), appCustomEffectiveGroup(group), custom.FlavorName)
	appCustomCache.Mu.RUnlock()
	if !categoryAllowed {
		errors = append(errors, appEditorError("CATEGORY_EDIT_REQUIRED", "custom.categoryId", "Category EDIT permission is required", node))
	}
	if !groupAllowed {
		errors = append(errors, appEditorError("GROUP_NOT_AVAILABLE", "custom.groupId", "Group not found in the active workspace", node))
	}
	if !versionAvailable {
		errors = append(errors, appEditorError("VERSION_CONFLICT", "version", "An application with this version already exists", node))
	}
	if groupAllowed && flavorValid && !flavorAvailable {
		errors = append(errors, appEditorError("FLAVOR_CONFLICT", "custom.flavorName", "A flavor with this name already exists", node))
	}

	docker, registry := appCustomProviderHasSupportForDockerAndRegistry(custom.ServiceProvider)
	compute, storage := appCustomActorHasProviderAllocations(actor, custom.ServiceProvider)
	if custom.ServiceProvider == "" {
		errors = append(errors, appEditorError("PROVIDER_REQUIRED", "custom.serviceProvider", "Service provider is required", node))
	} else {
		if !docker || !registry {
			errors = append(errors, appEditorError("PROVIDER_UNSUPPORTED", "custom.serviceProvider", "The provider does not support custom container applications", node))
		}
		if !compute {
			errors = append(errors, appEditorError("COMPUTE_ALLOCATION_REQUIRED", "custom.serviceProvider", "An active compute allocation is required at the provider", node))
		}
		if !storage {
			errors = append(errors, appEditorError("STORAGE_ALLOCATION_REQUIRED", "custom.serviceProvider", "An active storage allocation is required at the provider", node))
		}
	}
	imageDigest := ""
	if len(errors) == 0 {
		if validated, imageErr := applicationVariantValidateImage(actor, custom.ServiceProvider, source.Software.Container.Image, false, true); imageErr != nil {
			errors = append(errors, appEditorError("IMAGE_INVALID", "software.image", imageErr.Why, node))
		} else {
			imageDigest = validated.ImageDigest
		}
	}
	return errors, imageDigest, custom
}

// Source retrieval and conversion
// =====================================================================================================================
// Retrieval returns editable version 2 source and reconstructs source for legacy managed applications when necessary.

func appEditorRetrieveSource(actor rpc.Actor, request orcapi.AppEditorRetrieveSourceRequest) (orcapi.AppEditorRetrieveSourceResponse, *util.HttpError) {
	switch request.Intent {
	case orcapi.AppEditorSourceIntentEdit:
	case orcapi.AppEditorSourceIntentFork:
		if !appEditorIsProjectAdmin(actor) {
			return orcapi.AppEditorRetrieveSourceResponse{}, util.HttpErr(http.StatusForbidden, "project administrator access is required")
		}
	default:
		return orcapi.AppEditorRetrieveSourceResponse{}, util.HttpErr(http.StatusBadRequest, "invalid source intent")
	}
	if request.Kind == orcapi.AppEditorApplicationKindCustom {
		return appEditorRetrieveCustomSource(actor, request)
	}
	if request.Kind != orcapi.AppEditorApplicationKindManaged {
		return orcapi.AppEditorRetrieveSourceResponse{}, util.HttpErr(http.StatusBadRequest, "invalid application kind")
	}
	if request.Intent == orcapi.AppEditorSourceIntentEdit && actor.Role != rpc.RoleAdmin {
		return orcapi.AppEditorRetrieveSourceResponse{}, util.HttpErr(http.StatusForbidden, "UCloud administrator access is required")
	}
	if _, ok := AppRetrieve(actor, request.Name, request.Version, AppDiscoveryAll, 0); !ok {
		return orcapi.AppEditorRetrieveSourceResponse{}, util.HttpErr(http.StatusNotFound, "application not found")
	}
	managed, ok := appRetrieve(request.Name, request.Version)
	if !ok {
		return orcapi.AppEditorRetrieveSourceResponse{}, util.HttpErr(http.StatusNotFound, "application not found")
	}
	managed.Mu.RLock()
	source := managed.Source
	managed.Mu.RUnlock()
	if source == "" {
		historical := db.NewTx(func(tx *db.Transaction) struct {
			Source sql.NullString
		} {
			row, _ := db.Get[struct {
				Source sql.NullString
			}](
				tx,
				`
					select source_application as source
					from app_store.applications
					where name = :name and version = :version
				`,
				db.Params{
					"name":    request.Name,
					"version": request.Version,
				},
			)
			return row
		})
		if historical.Source.Valid {
			source = historical.Source.String
		}
		var app orcapi.Application
		if source == "" {
			app, ok = AppRetrieve(rpc.ActorSystem, request.Name, request.Version, AppDiscoveryAll, 0)
			if !ok {
				return orcapi.AppEditorRetrieveSourceResponse{}, util.HttpErr(http.StatusConflict, "canonical source is unavailable")
			}
			source = appEditorSourceFromApplication(app)
		}
		if source == "" {
			return orcapi.AppEditorRetrieveSourceResponse{}, util.HttpErr(http.StatusConflict, "canonical source is unavailable")
		}
		managed.Mu.Lock()
		managed.Source = source
		managed.Mu.Unlock()
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					update app_store.applications
					set source_application = :source
					where name = :name and version = :version and source_application is null
				`,
				db.Params{
					"source":  source,
					"name":    request.Name,
					"version": request.Version,
				},
			)
		})
	}
	if request.Intent == orcapi.AppEditorSourceIntentFork && actor.Role != rpc.RoleAdmin {
		var err *util.HttpError
		source, err = appEditorStripSourceComments(source)
		if err != nil {
			return orcapi.AppEditorRetrieveSourceResponse{}, err
		}
	}
	return orcapi.AppEditorRetrieveSourceResponse{
		Kind:   request.Kind,
		Source: source,
	}, nil
}

func appEditorStripSourceComments(source string) (string, *util.HttpError) {
	var document yaml.Node
	if err := yaml.Unmarshal([]byte(source), &document); err != nil {
		return "", util.HttpErr(http.StatusConflict, "canonical source is unavailable")
	}
	var stripComments func(node *yaml.Node)
	stripComments = func(node *yaml.Node) {
		node.HeadComment = ""
		node.LineComment = ""
		node.FootComment = ""
		for _, child := range node.Content {
			stripComments(child)
		}
	}
	stripComments(&document)

	buffer := &bytes.Buffer{}
	encoder := yaml.NewEncoder(buffer)
	encoder.SetIndent(2)
	if err := encoder.Encode(&document); err != nil {
		return "", util.HttpErr(http.StatusConflict, "canonical source is unavailable")
	}
	_ = encoder.Close()
	return buffer.String(), nil
}

func appEditorRetrieveCustomSource(actor rpc.Actor, request orcapi.AppEditorRetrieveSourceRequest) (orcapi.AppEditorRetrieveSourceResponse, *util.HttpError) {
	if !request.ServiceProvider.Present || request.ServiceProvider.Value == "" {
		return orcapi.AppEditorRetrieveSourceResponse{}, util.HttpErr(http.StatusBadRequest, "service provider is required for custom applications")
	}
	name := appEditorInternalName(request.Name)
	appCustomCache.Mu.RLock()
	id := appCustomCache.AppKeys[appCustomApplicationKey(appCustomWorkspace(actor), name, request.Version, request.ServiceProvider.Value)]
	app := appCustomCache.Apps[id]
	if app == nil {
		appCustomCache.Mu.RUnlock()
		return orcapi.AppEditorRetrieveSourceResponse{}, util.HttpErr(http.StatusNotFound, "application not found")
	}
	category := appCustomCache.Categories[app.CategoryId]
	allowed := category != nil && appCustomCanReadApplication(actor, app, category)
	if request.Intent == orcapi.AppEditorSourceIntentEdit {
		allowed = category != nil && appCustomCategoryHasPermission(actor, category, orcapi.PermissionEdit)
	}
	source := app.Source
	metadata := orcapi.AppEditorCustomMetadata{
		ServiceProvider:    app.Provider,
		PublishedToProject: app.PublishedToProject,
		FlavorName:         app.FlavorName,
		GroupId:            -int(app.GroupId),
		CategoryId:         -int(app.CategoryId),
	}
	appCustomCache.Mu.RUnlock()
	if !allowed {
		return orcapi.AppEditorRetrieveSourceResponse{}, util.HttpErr(http.StatusNotFound, "application not found")
	}
	var document orcapi.A2Yaml
	if yaml.Unmarshal([]byte(source), &document) != nil {
		return orcapi.AppEditorRetrieveSourceResponse{}, util.HttpErr(http.StatusConflict, "canonical source is unavailable")
	}
	document.Name = appEditorLogicalName(document.Name)
	source = appEditorMarshalSource(document)
	return orcapi.AppEditorRetrieveSourceResponse{
		Kind:   request.Kind,
		Source: source,
		Custom: util.OptValue(metadata),
	}, nil
}

func appEditorMarshalSource(source orcapi.A2Yaml) string {
	data, err := yaml.Marshal(source)
	if err != nil {
		return ""
	}
	return "application: v2\n" + string(data)
}

func appEditorSourceFromApplication(app orcapi.Application) string {
	invocation := &app.Invocation
	if !invocation.Tool.Tool.Present {
		return ""
	}
	tool := invocation.Tool.Tool.Value.Description
	source := orcapi.A2Yaml{
		Name:          app.Metadata.Name,
		Version:       app.Metadata.Version,
		Title:         util.OptStringIfNotEmpty(app.Metadata.Title),
		Description:   util.OptStringIfNotEmpty(app.Metadata.Description),
		Documentation: util.OptStringIfNotEmpty(app.Metadata.Website),
		Parameters:    map[string]orcapi.A2Parameter{},
		Environment:   map[string]string{},
		Sbatch:        map[string]string{},
		Extensions:    invocation.FileExtensions,
	}
	switch tool.Backend {
	case orcapi.ToolBackendDocker:
		source.Software = orcapi.A2Software{
			Type: orcapi.A2SoftwareContainer,
			Container: &orcapi.A2ContainerSoftware{
				Image: tool.Image,
			},
		}
	case orcapi.ToolBackendVirtualMachine:
		source.Software = orcapi.A2Software{
			Type: orcapi.A2SoftwareVirtualMachine,
			VirtualMachine: &orcapi.A2VirtualMachineSoftware{
				Image: tool.Image,
			},
		}
	case orcapi.ToolBackendUcx:
		source.Software = orcapi.A2Software{
			Type: orcapi.A2SoftwareUcx,
			Ucx: &orcapi.A2UcxSoftware{
				Image: tool.Image,
			},
		}
	case orcapi.ToolBackendNative:
		native := &orcapi.A2NativeSoftware{}
		if tool.LoadInstructions.Present {
			for _, loaded := range tool.LoadInstructions.Value.Applications {
				native.Load = append(native.Load, orcapi.A2ApplicationToLoad{
					Name:    loaded.Name,
					Version: loaded.Version,
				})
			}
		}
		source.Software = orcapi.A2Software{
			Type:   orcapi.A2SoftwareNative,
			Native: native,
		}
	default:
		return ""
	}
	if len(invocation.Invocation) == 1 && invocation.Invocation[0].Type == orcapi.InvocationParameterTypeJinja {
		source.Invocation = invocation.Invocation[0].Template
	} else {
		return ""
	}
	for _, parameter := range invocation.Parameters {
		mapped, ok := appEditorParameterFromApplication(parameter)
		if !ok {
			return ""
		}
		source.Parameters[parameter.Name] = mapped
		source.ParametersOrder = append(source.ParametersOrder, parameter.Name)
	}
	for name, value := range invocation.Environment {
		if value.Type != orcapi.InvocationParameterTypeWord {
			return ""
		}
		source.Environment[name] = value.Word
	}
	for name, value := range invocation.Sbatch {
		if value.Type != orcapi.InvocationParameterTypeWord {
			return ""
		}
		source.Sbatch[name] = value.Word
	}
	source.Features.Set(orcapi.A2Features{
		MultiNode:   invocation.AllowMultiNode.GetOrDefault(false),
		Links:       invocation.AllowPublicLink,
		IPAddresses: invocation.AllowPublicIp,
		Folders:     invocation.AllowAdditionalMounts,
		JobLinking:  invocation.AllowAdditionalPeers,
		JobAuditLog: invocation.JobAuditLogIsEnabled,
	})
	source.Ucx = invocation.Ucx
	if invocation.Modules.Present {
		source.Modules.Set(orcapi.A2Module{
			MountPath: invocation.Modules.Value.MountPath,
			Optional:  invocation.Modules.Value.Optional,
		})
	}
	if invocation.Web.Present {
		source.Web.Set(orcapi.A2Web{
			Enabled: true,
			Port:    util.OptValue(int(invocation.Web.Value.Port)),
		})
	}
	if invocation.Vnc.Present {
		source.Vnc.Set(orcapi.A2Vnc{
			Enabled:  true,
			Port:     util.OptValue(int(invocation.Vnc.Value.Port)),
			Password: util.OptStringIfNotEmpty(invocation.Vnc.Value.Password),
		})
	}
	if invocation.Ssh.Present {
		mode := orcapi.A2SshModeDisabled
		if invocation.Ssh.Value.Mode == orcapi.SshModeOptional {
			mode = orcapi.A2SshModeOptional
		}
		if invocation.Ssh.Value.Mode == orcapi.SshModeMandatory {
			mode = orcapi.A2SshModeMandatory
		}
		source.Ssh.Set(orcapi.A2Ssh{
			Mode: mode,
		})
	}
	if invocation.Inference.Present {
		mode := orcapi.A2InferenceModeNone
		if invocation.Inference.Value.Mode == orcapi.InferenceModeOptional {
			mode = orcapi.A2InferenceModeOptional
		}
		if invocation.Inference.Value.Mode == orcapi.InferenceModeMandatory {
			mode = orcapi.A2InferenceModeMandatory
		}
		source.Inference.Set(orcapi.A2Inference{
			Mode: mode,
		})
	}

	return appEditorMarshalSource(source)
}

func appEditorParameterFromApplication(parameter orcapi.ApplicationParameter) (orcapi.A2Parameter, bool) {
	base := orcapi.A2ParamBase{
		Title:       parameter.Title,
		Description: parameter.Description,
		Optional:    parameter.Optional,
	}
	result := orcapi.A2Parameter{}
	switch parameter.Type {
	case orcapi.ApplicationParameterTypeInputFile:
		result.Type, result.File = "File", &orcapi.A2ParamFile{
			A2ParamBase: base,
		}
	case orcapi.ApplicationParameterTypeInputDirectory:
		result.Type, result.Directory = "Directory", &orcapi.A2ParamDirectory{
			A2ParamBase: base,
		}
	case orcapi.ApplicationParameterTypeLicenseServer:
		result.Type, result.License = "License", &orcapi.A2ParamLicense{
			A2ParamBase: base,
		}
	case orcapi.ApplicationParameterTypePeer:
		result.Type, result.Job = "Job", &orcapi.A2ParamJob{
			A2ParamBase: base,
		}
	case orcapi.ApplicationParameterTypeNetworkIp:
		result.Type, result.PublicIP = "PublicIP", &orcapi.A2ParamPublicIp{
			A2ParamBase: base,
		}
	case orcapi.ApplicationParameterTypeInteger:
		value := &orcapi.A2ParamInt{
			A2ParamBase: base,
		}
		appEditorDecodeDefault(parameter.DefaultValue, &value.DefaultValue)
		result.Type, result.Integer = "Integer", value
	case orcapi.ApplicationParameterTypeFloatingPoint:
		value := &orcapi.A2ParamFloat{
			A2ParamBase: base,
		}
		appEditorDecodeDefault(parameter.DefaultValue, &value.DefaultValue)
		result.Type, result.FloatingPoint = "FloatingPoint", value
	case orcapi.ApplicationParameterTypeBoolean:
		value := &orcapi.A2ParamBool{
			A2ParamBase: base,
		}
		appEditorDecodeDefault(parameter.DefaultValue, &value.DefaultValue)
		result.Type, result.Boolean = "Boolean", value
	case orcapi.ApplicationParameterTypeText:
		value := &orcapi.A2ParamText{
			A2ParamBase: base,
		}
		appEditorDecodeDefault(parameter.DefaultValue, &value.DefaultValue)
		result.Type, result.Text = "Text", value
	case orcapi.ApplicationParameterTypeTextArea:
		value := &orcapi.A2ParamTextArea{
			A2ParamBase: base,
		}
		appEditorDecodeDefault(parameter.DefaultValue, &value.DefaultValue)
		result.Type, result.TextArea = "TextArea", value
	case orcapi.ApplicationParameterTypeEnumeration:
		value := &orcapi.A2ParamEnum{
			A2ParamBase: base,
		}
		appEditorDecodeDefault(parameter.DefaultValue, &value.DefaultValue)
		for _, option := range parameter.Options {
			value.Options = append(value.Options, orcapi.A2EnumOption{
				Title: option.Name,
				Value: option.Value,
			})
		}
		result.Type, result.Enumeration = "Enumeration", value
	case orcapi.ApplicationParameterTypeWorkflow:
		result.Type, result.Workflow = "Workflow", &orcapi.A2ParamWorkflow{
			A2ParamBase: base,
			Parameters:  map[string]orcapi.A2Parameter{},
		}
	default:
		return orcapi.A2Parameter{}, false
	}
	return result, true
}

func appEditorDecodeDefault[T any](raw json.RawMessage, result *util.Option[T]) {
	if len(raw) == 0 || bytes.Equal(raw, []byte("null")) {
		return
	}
	var value T
	if json.Unmarshal(raw, &value) == nil {
		result.Set(value)
	}
}

// Eligibility
// =====================================================================================================================
// Eligibility reports the providers and workspace resources required before a custom application can be created.

func appEditorEligibility(actor rpc.Actor) orcapi.AppEditorCustomEligibilityResponse {
	result := orcapi.AppEditorCustomEligibilityResponse{
		CanPublish: actor.Project.Present,
	}
	providers := map[string]bool{}
	for provider := range SupportRetrieveProducts[orcapi.JobSupport](jobType).ProductsByProvider {
		providers[provider] = true
	}
	for provider := range SupportRetrieveProducts[orcapi.FSSupport](driveType).ProductsByProvider {
		providers[provider] = true
	}
	providerNames := make([]string, 0, len(providers))
	for provider := range providers {
		providerNames = append(providerNames, provider)
	}
	slices.Sort(providerNames)
	for _, provider := range providerNames {
		docker, registry := appCustomProviderHasSupportForDockerAndRegistry(provider)
		compute, storage := appCustomActorHasProviderAllocations(actor, provider)
		result.Providers = append(result.Providers, orcapi.AppEditorProviderEligibility{
			Provider: provider,
			ContainerSupport: orcapi.AppEditorEligibilityRequirement{
				Eligible: docker,
				Message:  "Provider supports container jobs",
			},
			RegistrySupport: orcapi.AppEditorEligibilityRequirement{
				Eligible: registry,
				Message:  "Provider supports container repositories",
			},
			ComputeAllocation: orcapi.AppEditorEligibilityRequirement{
				Eligible: compute,
				Message:  "Workspace has an active compute allocation",
			},
			StorageAllocation: orcapi.AppEditorEligibilityRequirement{
				Eligible: storage,
				Message:  "Workspace has an active storage allocation",
			},
			Eligible: docker && registry && compute && storage,
		})
	}
	result.Providers = util.NonNilSlice(result.Providers)
	return result
}

// Preview rate limiting
// =====================================================================================================================
// Preview attempts are recorded per user and counted in a rolling one-hour window.

func appEditorRateLimit(actor rpc.Actor) (orcapi.AppEditorRateLimit, bool) {
	limit := 60
	if actor.Role == rpc.RoleAdmin {
		limit = 500
	}
	type rateRow struct {
		Count   int
		RetryAt time.Time
	}
	row, allowed := db.NewTx2(func(tx *db.Transaction) (rateRow, bool) {
		db.Exec(
			tx,
			`select pg_advisory_xact_lock(hashtext(:username))`,
			db.Params{
				"username": actor.Username,
			},
		)
		db.Exec(
			tx,
			`
				delete from app_store.application_render_attempts
				where username = :username and attempted_at <= now() - interval '1 hour'
			`,
			db.Params{
				"username": actor.Username,
			},
		)
		state, _ := db.Get[rateRow](
			tx,
			`
				select
					count(*)::int as count,
					coalesce(min(attempted_at) + interval '1 hour', now()) as retry_at
				from app_store.application_render_attempts
				where username = :username
			`,
			db.Params{
				"username": actor.Username,
			},
		)
		if state.Count >= limit {
			return state, false
		}
		db.Exec(
			tx,
			`
				insert into app_store.application_render_attempts(username)
				values (:username)
			`,
			db.Params{
				"username": actor.Username,
			},
		)
		state.Count++
		return state, true
	})
	result := orcapi.AppEditorRateLimit{
		Limit:     limit,
		Remaining: max(0, limit-row.Count),
	}
	if !allowed {
		result.RetryAt.Set(fndapi.Timestamp(row.RetryAt))
	}
	return result, allowed
}

// Invocation preview
// =====================================================================================================================
// Preview requests use the same validation path as creation and are rate limited before provider invocation.

func appEditorRender(actor rpc.Actor, request orcapi.AppEditorRenderRequest) (orcapi.AppEditorRenderResponse, *util.HttpError) {
	validation := appEditorValidate(actor, request.Validation)
	if len(validation.Errors) != 0 {
		return orcapi.AppEditorRenderResponse{Errors: validation.Errors}, nil
	}
	application := validation.Application.Value
	provider := request.Job.Product.Provider
	if request.Validation.Kind == orcapi.AppEditorApplicationKindCustom && request.Validation.Custom.Value.ServiceProvider != provider {
		return orcapi.AppEditorRenderResponse{
			Errors: []orcapi.AppEditorValidationError{
				{
					Code:    "PROVIDER_MISMATCH",
					Path:    "job.product.provider",
					Message: "The job provider must match the custom application provider",
				},
			},
		}, nil
	}
	spec := request.Job
	if err := jobsValidateWithApplication(actor, &spec, application); err != nil {
		return orcapi.AppEditorRenderResponse{
			Errors: []orcapi.AppEditorValidationError{
				{
					Code:    "INVALID_JOB",
					Path:    "job",
					Message: err.Why,
				},
			},
		}, nil
	}
	rate, allowed := appEditorRateLimit(actor)
	if !allowed {
		return orcapi.AppEditorRenderResponse{
			Errors: []orcapi.AppEditorValidationError{
				{
					Code:    "RATE_LIMITED",
					Message: "Invocation preview rate limit reached",
				},
			},
			RateLimit: rate,
		}, nil
	}
	support, ok := SupportByProduct[orcapi.JobSupport](jobType, spec.Product)
	if !ok {
		return orcapi.AppEditorRenderResponse{}, util.HttpErr(http.StatusBadRequest, "bad machine type requested")
	}
	owner := orcapi.ResourceOwner{
		CreatedBy: actor.Username,
		Project:   util.OptStringIfNotEmpty(string(actor.Project.GetOrDefault(""))),
	}
	job := orcapi.Job{
		Resource: orcapi.Resource{
			Id:        fmt.Sprintf("preview-%d", time.Now().UnixNano()),
			CreatedAt: fndapi.Timestamp(time.Now()),
			Owner:     owner,
		},
		Specification: spec,
		Status: orcapi.JobStatus{
			State:               orcapi.JobStateInQueue,
			ResolvedApplication: util.OptValue(application),
			ResolvedProduct:     util.OptValue(support.Product),
			ResolvedSupport:     util.OptValue(support.ResolvedSupport),
		},
	}
	providerResponse, err := InvokeProvider(
		provider,
		orcapi.JobsProviderRenderInvocation,
		orcapi.JobsProviderRenderInvocationRequest{
			Job: job,
		},
		ProviderCallOpts{
			Username: util.OptValue(actor.Username),
			Reason:   util.OptValue("render application invocation preview"),
			Timeout:  util.OptValue(30 * time.Second),
		},
	)
	if err != nil {
		return orcapi.AppEditorRenderResponse{
			Errors: []orcapi.AppEditorValidationError{
				{
					Code:    "PROVIDER_RENDER_FAILED",
					Message: err.Why,
				},
			},
			RateLimit: rate,
		}, nil
	}
	return orcapi.AppEditorRenderResponse{
		Script:    util.OptValue(providerResponse.Script),
		Errors:    []orcapi.AppEditorValidationError{},
		RateLimit: rate,
	}, nil
}
