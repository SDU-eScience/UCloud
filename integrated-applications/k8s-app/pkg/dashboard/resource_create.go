package dashboard

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/url"
	"strings"
	"time"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/runtime/schema"
	k8syaml "k8s.io/apimachinery/pkg/util/yaml"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"
)

func dashboardResourceCreateOpen(app *stackUiApp) {
	def, ok := app.resolveType(app.ActiveType)
	if !ok || !def.CanCreate || app.resourceEditor.Saving {
		return
	}
	namespace := app.ActiveNamespace
	if !app.ActiveFilter.isEmpty() {
		namespace = ""
		for _, field := range strings.Split(app.ActiveFilter.fieldSelector, ",") {
			if value, found := strings.CutPrefix(field, "metadata.namespace="); found {
				namespace = strings.TrimPrefix(value, "=")
			}
		}
	}
	if !def.Namespaced {
		namespace = ""
	}
	dashboardNavigationStart(app)
	app.ResourceDetail = def.Id + "/" + namespace + "/@create"
	app.prevDetail = app.ResourceDetail
	dashboardResourceEditorLoad(app, app.ResourceDetail, true)
	dashboardNavigationPush(app, "create/"+url.PathEscape(def.Id)+"/"+url.PathEscape(namespace), "Create")
	ucx.AppUpdateUi(app)
}

func dashboardResourceCreateBack(app *stackUiApp) {
	if app.resourceEditor.SchemaCancel != nil {
		app.resourceEditor.SchemaCancel()
	}
	app.resourceEditor = dashboardResourceEditorState{Revision: app.resourceEditor.Revision + 1}
	app.ResourceYaml = ""
	app.ResourceDraft = ""
	app.ResourceDetail = ""
	app.prevDetail = ""
	if app.poller != nil {
		app.poller.signalPollNow()
	}
	route := app.browseRoute(app.ActiveType, app.ActiveFilter)
	if len(app.resourceNavigation) > 1 {
		route = app.resourceNavigation[len(app.resourceNavigation)-2].route
		app.resourceNavigation = app.resourceNavigation[:len(app.resourceNavigation)-1]
	}
	ucxsvc.RouterPushPage(app, route)
	ucx.AppUpdateUi(app)
}

func dashboardResourceCreateDraftKeep(app *stackUiApp) {
	state := app.resourceEditor
	if !state.Creating || state.Loading || state.Snapshot == nil {
		return
	}
	if app.resourceCreateDrafts == nil {
		app.resourceCreateDrafts = map[string]string{}
	}
	app.resourceCreateDrafts[state.Detail] = app.ResourceDraft
}

func dashboardResourceCreateLoad(app *stackUiApp, namespace string) {
	state := &app.resourceEditor
	state.Creating = true
	state.Editing = true
	if !state.Def.CanCreate {
		state.Loading = false
		state.Error = "This resource type does not support creation"
		return
	}
	def, detail, revision := state.Def, state.Detail, state.Revision
	client, session := app.k8sClient, app.session
	provider := ""
	if app.stackInfo != nil {
		provider = app.stackInfo.Provider
	}
	go func() {
		ctx, cancel := context.WithTimeout(session.Context(), 15*time.Second)
		defer cancel()
		path := "/apis/" + def.Gvr.GroupVersion().String()
		if def.Gvr.Group == "" {
			path = "/api/" + def.Gvr.Version
		}
		var resources metav1.APIResourceList
		err := client.Typed.Discovery().RESTClient().Get().AbsPath(path).Do(ctx).Into(&resources)
		gvk := schema.GroupVersionKind{Group: def.Gvr.Group, Version: def.Gvr.Version}
		if err == nil {
			for _, resource := range resources.APIResources {
				if resource.Name == def.Gvr.Resource {
					gvk.Kind = resource.Kind
					break
				}
			}
			if gvk.Kind == "" {
				err = fmt.Errorf("no kind found for %s", def.Gvr)
			}
		}
		var draft string
		if err == nil {
			var registration ucxapi.EditorSchemaRegistration
			registration, err = dashboardResourceEditorSchemaFetch(ctx, client, gvk, def.Namespaced)
			if err == nil {
				_, err = ucxapi.EditorRegisterSchemas.InvokeEx(ctx, session, ucxapi.EditorRegisterSchemasRequest{
					Schemas: []ucxapi.EditorSchemaRegistration{registration},
				})
			}
			if err == nil {
				ingressHost := ""
				if def.Gvr.Group == "networking.k8s.io" && def.Gvr.Resource == "ingresses" {
					ingressHost = dashboardResourceCreateIngressHost(ctx, session, provider)
				}
				draft, err = dashboardResourceCreateTemplate(registration.Schema, def, namespace, ingressHost)
			}
		}
		app.mu.Lock()
		defer app.mu.Unlock()
		if app.resourceEditor.Revision != revision || app.ResourceDetail != detail {
			return
		}
		app.resourceEditor.Loading = false
		if err != nil {
			app.resourceEditor.Error = fmt.Sprintf("Could not load resource template: %s", err)
		} else {
			object := &unstructured.Unstructured{Object: map[string]any{}}
			object.SetGroupVersionKind(gvk)
			app.resourceEditor.Snapshot = object
			if retained, present := app.resourceCreateDrafts[detail]; present {
				draft = retained
			}
			app.ResourceDraft = draft
			dashboardResourceCreateDraftKeep(app)
			app.resourceEditor.Revision++
			dashboardResourceEditorSchemasStart(app)
		}
		ucx.AppUpdateUi(app)
	}()
}

func dashboardResourceCreateIngressHost(ctx context.Context, session *ucx.Session, provider string) string {
	const placeholder = "<choose-name>"
	ctx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	if provider == "" {
		info, err := ucxapi.StackInfo.InvokeEx(ctx, session, util.Empty{})
		if err != nil {
			return placeholder
		}
		provider = info.Provider
	}
	if provider == "" {
		return placeholder
	}
	products, err := ucxapi.PublicLinksRetrieveProducts.InvokeEx(ctx, session, util.Empty{})
	if err != nil {
		return placeholder
	}
	for _, product := range products {
		if product.Product.Category.Provider == provider {
			return product.Support.Prefix + placeholder + product.Support.Suffix
		}
	}
	return placeholder
}

func dashboardResourceCreateTemplate(root map[string]any, def ResourceTypeDef, namespace string, ingressHost string) (string, error) {
	definitions, _ := root["definitions"].(map[string]any)
	object, _ := dashboardResourceTemplateValue(root, definitions, map[string]bool{}).(map[string]any)
	if object == nil {
		object = map[string]any{}
	}
	delete(object, "status")
	metadata, _ := object["metadata"].(map[string]any)
	if metadata == nil {
		metadata = map[string]any{}
	}
	metadata["name"] = ""
	if def.Namespaced {
		metadata["namespace"] = namespace
	} else {
		delete(metadata, "namespace")
	}
	object["metadata"] = metadata
	if def.Gvr.Group == "networking.k8s.io" && def.Gvr.Resource == "ingresses" {
		if ingressHost == "" {
			ingressHost = "<choose-name>"
		}
		if err := unstructured.SetNestedSlice(object, []any{
			map[string]any{
				"host": ingressHost,
				"http": map[string]any{
					"paths": []any{
						map[string]any{
							"path":     "/",
							"pathType": "Prefix",
							"backend": map[string]any{
								"service": map[string]any{
									"name": "",
									"port": map[string]any{"number": int64(80)},
								},
							},
						},
					},
				},
			},
		}, "spec", "rules"); err != nil {
			return "", err
		}
	}
	if def.Gvr.Group == "" {
		switch def.Gvr.Resource {
		case "configmaps":
			object["data"] = map[string]any{}
		case "secrets":
			object["stringData"] = map[string]any{}
		}
	}
	podPath := dashboardResourceCreatePodPath(def)
	if len(podPath) > 0 {
		podSpec := object
		podSchema := root
		for _, key := range podPath {
			podSchema = dashboardResourceTemplateProperty(podSchema, definitions, key)
			child, _ := podSpec[key].(map[string]any)
			if child == nil {
				child, _ = dashboardResourceTemplateValue(podSchema, definitions, map[string]bool{}).(map[string]any)
				if child == nil {
					child = map[string]any{}
				}
				podSpec[key] = child
			}
			podSpec = child
		}
		containerSchema := dashboardResourceTemplateProperty(podSchema, definitions, "containers")
		items, _ := containerSchema["items"].(map[string]any)
		container, _ := dashboardResourceTemplateValue(items, definitions, map[string]bool{}).(map[string]any)
		if container == nil {
			container = map[string]any{}
		}
		container["name"] = ""
		container["image"] = ""
		podSpec["containers"] = []any{container}
		if def.Gvr.Group == "batch" {
			podSpec["restartPolicy"] = "Never"
		}
	}
	selectorPath := dashboardResourceCreateSelectorPath(def)
	if len(selectorPath) > 0 {
		if err := unstructured.SetNestedField(object, "", selectorPath...); err != nil {
			return "", err
		}
		if err := unstructured.SetNestedField(object, "", "spec", "template", "metadata", "labels", "app.kubernetes.io/name"); err != nil {
			return "", err
		}
	}
	return dashboardResourceYaml(&unstructured.Unstructured{Object: object})
}

func dashboardResourceCreatePodPath(def ResourceTypeDef) []string {
	if def.Gvr.Group == "" {
		switch def.Gvr.Resource {
		case "pods":
			return []string{"spec"}
		case "replicationcontrollers", "podtemplates":
			if def.Gvr.Resource == "podtemplates" {
				return []string{"template", "spec"}
			}
			return []string{"spec", "template", "spec"}
		}
	} else if def.Gvr.Group == "apps" {
		switch def.Gvr.Resource {
		case "deployments", "statefulsets", "daemonsets", "replicasets":
			return []string{"spec", "template", "spec"}
		}
	} else if def.Gvr.Group == "batch" {
		switch def.Gvr.Resource {
		case "jobs":
			return []string{"spec", "template", "spec"}
		case "cronjobs":
			return []string{"spec", "jobTemplate", "spec", "template", "spec"}
		}
	}
	return nil
}

func dashboardResourceCreateSelectorPath(def ResourceTypeDef) []string {
	if def.Gvr.Group == "apps" {
		switch def.Gvr.Resource {
		case "deployments", "statefulsets", "daemonsets", "replicasets":
			return []string{"spec", "selector", "matchLabels", "app.kubernetes.io/name"}
		}
	} else if def.Gvr.Group == "" && def.Gvr.Resource == "replicationcontrollers" {
		return []string{"spec", "selector", "app.kubernetes.io/name"}
	}
	return nil
}

func dashboardResourceCreateFieldLinks(def ResourceTypeDef) []ucx.CodeEditorYamlFieldLink {
	podPath := dashboardResourceCreatePodPath(def)
	if len(podPath) == 0 {
		return nil
	}
	containerPath := dashboardResourceCreateYamlPath(podPath)
	containerPath = append(containerPath, "containers", 0, "name")
	targets := [][]any{containerPath}
	if selectorPath := dashboardResourceCreateSelectorPath(def); len(selectorPath) > 0 {
		targets = append(targets,
			dashboardResourceCreateYamlPath(selectorPath),
			[]any{"spec", "template", "metadata", "labels", "app.kubernetes.io/name"},
		)
	}
	return []ucx.CodeEditorYamlFieldLink{{Source: []any{"metadata", "name"}, Targets: targets}}
}

func dashboardResourceCreateYamlPath(path []string) []any {
	result := make([]any, len(path))
	for i, key := range path {
		result[i] = key
	}
	return result
}

func dashboardResourceTemplateProperty(node map[string]any, definitions map[string]any, name string) map[string]any {
	return dashboardResourceTemplatePropertyFind(node, definitions, name, map[string]bool{})
}

func dashboardResourceTemplatePropertyFind(node map[string]any, definitions map[string]any, name string, active map[string]bool) map[string]any {
	var matches []any
	properties, _ := node["properties"].(map[string]any)
	if property, ok := properties[name].(map[string]any); ok {
		matches = append(matches, property)
	}
	if reference, ok := node["$ref"].(string); ok {
		if !active[reference] {
			active[reference] = true
			key := strings.TrimPrefix(reference, "#/definitions/")
			key = strings.ReplaceAll(strings.ReplaceAll(key, "~1", "/"), "~0", "~")
			definition, _ := definitions[key].(map[string]any)
			if property := dashboardResourceTemplatePropertyFind(definition, definitions, name, active); property != nil {
				matches = append(matches, property)
			}
			delete(active, reference)
		}
	}
	allOf, _ := node["allOf"].([]any)
	for _, item := range allOf {
		child, _ := item.(map[string]any)
		if property := dashboardResourceTemplatePropertyFind(child, definitions, name, active); property != nil {
			matches = append(matches, property)
		}
	}
	if len(matches) == 0 {
		return nil
	}
	if len(matches) == 1 {
		return matches[0].(map[string]any)
	}
	return map[string]any{"allOf": matches}
}

func dashboardResourceTemplateValue(node map[string]any, definitions map[string]any, active map[string]bool) any {
	return dashboardResourceTemplateFill(node, definitions, active, nil, false)
}

func dashboardResourceTemplateDefaultsFill(value map[string]any, defaults map[string]any) {
	for name, fallback := range defaults {
		current, present := value[name]
		if !present {
			value[name] = runtime.DeepCopyJSONValue(fallback)
			continue
		}
		currentObject, currentIsObject := current.(map[string]any)
		fallbackObject, fallbackIsObject := fallback.(map[string]any)
		if currentIsObject && fallbackIsObject {
			dashboardResourceTemplateDefaultsFill(currentObject, fallbackObject)
		}
	}
}

func dashboardResourceTemplateFill(node map[string]any, definitions map[string]any, active map[string]bool, value any, present bool) any {
	if constant, exists := node["const"]; exists {
		return runtime.DeepCopyJSONValue(constant)
	}
	if fallback, exists := node["default"]; exists {
		if !present {
			value = runtime.DeepCopyJSONValue(fallback)
			present = true
		} else {
			currentObject, currentIsObject := value.(map[string]any)
			fallbackObject, fallbackIsObject := fallback.(map[string]any)
			if currentIsObject && fallbackIsObject {
				dashboardResourceTemplateDefaultsFill(currentObject, fallbackObject)
			}
		}
	}
	if reference, ok := node["$ref"].(string); ok {
		if active[reference] {
			if present {
				return value
			}
			return map[string]any{}
		}
		active[reference] = true
		defer delete(active, reference)
		name := strings.TrimPrefix(reference, "#/definitions/")
		name = strings.ReplaceAll(strings.ReplaceAll(name, "~1", "/"), "~0", "~")
		definition, _ := definitions[name].(map[string]any)
		value = dashboardResourceTemplateFill(definition, definitions, active, value, present)
		present = true
	}
	for _, keyword := range []string{"anyOf", "oneOf"} {
		alternatives, _ := node[keyword].([]any)
		for _, item := range alternatives {
			alternative, _ := item.(map[string]any)
			if alternative != nil && alternative["type"] != "null" {
				value = dashboardResourceTemplateFill(alternative, definitions, active, value, present)
				present = true
				break
			}
		}
	}
	switch node["type"] {
	case "string":
		if present {
			return value
		}
		return ""
	case "integer", "number":
		if present {
			return value
		}
		return int64(0)
	case "boolean":
		if present {
			return value
		}
		return false
	case "array":
		if present {
			return value
		}
		return []any{}
	case "null":
		return nil
	}
	result, isObject := value.(map[string]any)
	if present && !isObject {
		return value
	}
	if result == nil {
		result = map[string]any{}
	}
	allOf, _ := node["allOf"].([]any)
	for _, item := range allOf {
		child, _ := item.(map[string]any)
		dashboardResourceTemplateFill(child, definitions, active, result, true)
	}
	required, _ := node["required"].([]any)
	filled := map[string]bool{}
	for _, item := range required {
		name, ok := item.(string)
		if !ok {
			continue
		}
		property := dashboardResourceTemplateProperty(node, definitions, name)
		if property["readOnly"] == true {
			continue
		}
		current, exists := result[name]
		result[name] = dashboardResourceTemplateFill(property, definitions, active, current, exists)
		filled[name] = true
	}
	for name, current := range result {
		if filled[name] {
			continue
		}
		if property := dashboardResourceTemplateProperty(node, definitions, name); property != nil {
			result[name] = dashboardResourceTemplateFill(property, definitions, active, current, true)
		}
	}
	return result
}

func dashboardResourceCreateYaml(ctx context.Context, client *K8sClient, def ResourceTypeDef, template *unstructured.Unstructured, source string) (*unstructured.Unstructured, error) {
	if !def.CanCreate {
		return nil, fmt.Errorf("this resource type does not support creation")
	}
	decoder := k8syaml.NewYAMLOrJSONDecoder(strings.NewReader(source), 4096)
	var object unstructured.Unstructured
	if err := decoder.Decode(&object); err != nil {
		return nil, fmt.Errorf("invalid YAML: %w", err)
	}
	var extra json.RawMessage
	if decoder.Decode(&extra) != io.EOF {
		return nil, fmt.Errorf("provide exactly one Kubernetes resource")
	}
	if object.GetAPIVersion() != template.GetAPIVersion() || object.GetKind() != template.GetKind() {
		return nil, fmt.Errorf("apiVersion and kind must match the selected resource type")
	}
	if object.GetName() == "" {
		return nil, fmt.Errorf("metadata.name is required")
	}
	if def.Namespaced && object.GetNamespace() == "" {
		return nil, fmt.Errorf("metadata.namespace is required")
	}
	if !def.Namespaced && object.GetNamespace() != "" {
		return nil, fmt.Errorf("metadata.namespace is not allowed for cluster resources")
	}
	if _, present := object.Object["status"]; present {
		return nil, fmt.Errorf("status cannot be set here")
	}
	return dashboardResourceInterface(client, def, object.GetNamespace()).Create(ctx, &object, metav1.CreateOptions{
		FieldValidation: metav1.FieldValidationStrict,
	})
}
