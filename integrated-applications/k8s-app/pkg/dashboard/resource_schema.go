package dashboard

import (
	"context"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"net/url"
	"strings"
	"time"

	"k8s.io/apimachinery/pkg/runtime/schema"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
)

type dashboardResourceSchemaCache struct {
	Url      string
	Revision string
	Schemas  map[string]any
}

func dashboardResourceSchemaId(gvk schema.GroupVersionKind) string {
	return "kubernetes:" + gvk.GroupVersion().String() + ":" + gvk.Kind
}

func dashboardResourceSchemaFetch(ctx context.Context, client *K8sClient, gvk schema.GroupVersionKind) (ucxapi.EditorSchemaRegistration, error) {
	discovery := client.Typed.Discovery().RESTClient()
	indexData, err := discovery.Get().AbsPath("/openapi/v3").Do(ctx).Raw()
	if err != nil {
		return ucxapi.EditorSchemaRegistration{}, err
	}
	var index struct {
		Paths map[string]struct {
			ServerRelativeUrl string `json:"serverRelativeURL"`
		} `json:"paths"`
	}
	err = json.Unmarshal(indexData, &index)
	if err != nil {
		return ucxapi.EditorSchemaRegistration{}, err
	}
	path := "apis/" + gvk.GroupVersion().String()
	if gvk.Group == "" {
		path = "api/" + gvk.Version
	}
	schemaUrl := index.Paths[path].ServerRelativeUrl
	parsedUrl, err := url.Parse(schemaUrl)
	if err != nil {
		return ucxapi.EditorSchemaRegistration{}, err
	}
	if schemaUrl == "" || parsedUrl.IsAbs() || parsedUrl.Host != "" || !strings.HasPrefix(parsedUrl.Path, "/openapi/v3/") {
		return ucxapi.EditorSchemaRegistration{}, fmt.Errorf("no OpenAPI schema available for %s", gvk.GroupVersion())
	}
	client.schemaMutex.Lock()
	cached, present := client.schemaCache[path]
	client.schemaMutex.Unlock()
	hasHash := parsedUrl.Query().Get("hash") != ""
	if !present || cached.Url != schemaUrl || !hasHash {
		request := discovery.Get().AbsPath(parsedUrl.Path).SetHeader("Accept", "application/json")
		for key, values := range parsedUrl.Query() {
			for _, value := range values {
				request.Param(key, value)
			}
		}
		data, fetchErr := request.Do(ctx).Raw()
		if fetchErr != nil {
			return ucxapi.EditorSchemaRegistration{}, fetchErr
		}
		var document struct {
			Components struct {
				Schemas map[string]any `json:"schemas"`
			} `json:"components"`
		}
		err = json.Unmarshal(data, &document)
		if err != nil {
			return ucxapi.EditorSchemaRegistration{}, err
		}
		cached = dashboardResourceSchemaCache{
			Url:      schemaUrl,
			Revision: fmt.Sprintf("%x", sha256.Sum256(data)),
			Schemas:  document.Components.Schemas,
		}
		client.schemaMutex.Lock()
		if client.schemaCache == nil {
			client.schemaCache = map[string]dashboardResourceSchemaCache{}
		}
		client.schemaCache[path] = cached
		client.schemaMutex.Unlock()
	}
	resourceSchema, err := dashboardResourceSchemaBuild(cached.Schemas, gvk)
	if err != nil {
		return ucxapi.EditorSchemaRegistration{}, err
	}
	return ucxapi.EditorSchemaRegistration{
		SchemaId: dashboardResourceSchemaId(gvk),
		Revision: cached.Revision,
		Schema:   resourceSchema,
	}, nil
}

func dashboardResourceSchemaBuild(schemas map[string]any, gvk schema.GroupVersionKind) (map[string]any, error) {
	var selected map[string]any
	for _, item := range schemas {
		node, ok := item.(map[string]any)
		if !ok {
			continue
		}
		versions, _ := node["x-kubernetes-group-version-kind"].([]any)
		for _, item := range versions {
			version, _ := item.(map[string]any)
			if version["group"] == gvk.Group && version["version"] == gvk.Version && version["kind"] == gvk.Kind {
				selected = node
				break
			}
		}
		if selected != nil {
			break
		}
	}
	if selected == nil {
		return nil, fmt.Errorf("no OpenAPI schema available for %s", gvk)
	}
	definitions := map[string]any{}
	root, err := dashboardResourceSchemaNormalize(selected, schemas, definitions)
	if err != nil {
		return nil, err
	}
	properties, _ := root["properties"].(map[string]any)
	if properties == nil {
		properties = map[string]any{}
		root["properties"] = properties
	}
	properties["apiVersion"] = map[string]any{
		"type":  "string",
		"const": gvk.GroupVersion().String(),
	}
	properties["kind"] = map[string]any{
		"type":  "string",
		"const": gvk.Kind,
	}
	if _, present := properties["metadata"]; !present {
		properties["metadata"] = map[string]any{"type": "object"}
	}
	required, _ := root["required"].([]any)
	for _, name := range []string{"apiVersion", "kind", "metadata"} {
		present := false
		for _, item := range required {
			present = present || item == name
		}
		if !present {
			required = append(required, name)
		}
	}
	root["required"] = required
	root["definitions"] = definitions
	root["$schema"] = "http://json-schema.org/draft-07/schema#"
	return root, nil
}

func dashboardResourceEditorSchemaFetch(ctx context.Context, client *K8sClient, gvk schema.GroupVersionKind, namespaced bool) (ucxapi.EditorSchemaRegistration, error) {
	registration, err := dashboardResourceSchemaFetch(ctx, client, gvk)
	if err != nil || !namespaced {
		return registration, err
	}
	namespaces, err := client.ListNamespaces(ctx)
	if err != nil || len(namespaces) == 0 {
		return registration, nil
	}
	definitions, _ := registration.Schema["definitions"].(map[string]any)
	metadata := dashboardResourceTemplateProperty(registration.Schema, definitions, "metadata")
	if reference, ok := metadata["$ref"].(string); ok {
		key := strings.TrimPrefix(reference, "#/definitions/")
		key = strings.ReplaceAll(strings.ReplaceAll(key, "~1", "/"), "~0", "~")
		metadata, _ = definitions[key].(map[string]any)
	}
	if metadata == nil {
		return registration, nil
	}
	namespace := dashboardResourceTemplateProperty(metadata, definitions, "namespace")
	if namespace == nil {
		properties, _ := metadata["properties"].(map[string]any)
		if properties == nil {
			properties = map[string]any{}
			metadata["properties"] = properties
		}
		namespace = map[string]any{"type": "string"}
		properties["namespace"] = namespace
	}
	values := make([]any, 0, len(namespaces))
	for _, name := range namespaces {
		values = append(values, name)
	}
	namespace["anyOf"] = []any{
		map[string]any{"enum": values},
		map[string]any{"type": "string"},
	}
	registration.Revision += fmt.Sprintf(":%x", sha256.Sum256([]byte(strings.Join(namespaces, "\n"))))
	return registration, nil
}

func dashboardResourceSchemaNormalize(source map[string]any, schemas map[string]any, definitions map[string]any) (map[string]any, error) {
	result := make(map[string]any, len(source))
	for key, value := range source {
		if strings.HasPrefix(key, "x-kubernetes-") || key == "nullable" {
			continue
		}
		switch key {
		case "$ref":
			reference, ok := value.(string)
			if !ok || !strings.HasPrefix(reference, "#/components/schemas/") {
				return nil, fmt.Errorf("unsupported OpenAPI reference: %v", value)
			}
			pointer := strings.TrimPrefix(reference, "#/components/schemas/")
			name := strings.ReplaceAll(strings.ReplaceAll(pointer, "~1", "/"), "~0", "~")
			result[key] = "#/definitions/" + pointer
			if _, present := definitions[name]; !present {
				definition, ok := schemas[name].(map[string]any)
				if !ok {
					return nil, fmt.Errorf("missing OpenAPI definition: %s", name)
				}
				definitions[name] = nil
				normalized, err := dashboardResourceSchemaNormalize(definition, schemas, definitions)
				if err != nil {
					return nil, err
				}
				definitions[name] = normalized
			}
		case "properties", "patternProperties":
			children, _ := value.(map[string]any)
			converted := make(map[string]any, len(children))
			for name, child := range children {
				childSchema, ok := child.(map[string]any)
				if !ok {
					converted[name] = child
					continue
				}
				normalized, err := dashboardResourceSchemaNormalize(childSchema, schemas, definitions)
				if err != nil {
					return nil, err
				}
				converted[name] = normalized
			}
			result[key] = converted
		case "items", "additionalProperties", "not":
			child, ok := value.(map[string]any)
			if !ok {
				result[key] = value
				continue
			}
			normalized, err := dashboardResourceSchemaNormalize(child, schemas, definitions)
			if err != nil {
				return nil, err
			}
			result[key] = normalized
		case "allOf", "anyOf", "oneOf":
			children, _ := value.([]any)
			converted := make([]any, 0, len(children))
			for _, item := range children {
				child, ok := item.(map[string]any)
				if !ok {
					return nil, fmt.Errorf("invalid OpenAPI %s schema", key)
				}
				normalized, err := dashboardResourceSchemaNormalize(child, schemas, definitions)
				if err != nil {
					return nil, err
				}
				converted = append(converted, normalized)
			}
			result[key] = converted
		default:
			result[key] = value
		}
	}
	if source["x-kubernetes-int-or-string"] == true {
		delete(result, "type")
		result["anyOf"] = []any{
			map[string]any{"type": "integer"},
			map[string]any{"type": "string"},
		}
	}
	properties, _ := result["properties"].(map[string]any)
	if source["x-kubernetes-embedded-resource"] == true {
		if properties == nil {
			properties = map[string]any{}
			result["properties"] = properties
		}
		for name, property := range map[string]any{
			"apiVersion": map[string]any{"type": "string"},
			"kind":       map[string]any{"type": "string"},
			"metadata":   map[string]any{"type": "object"},
		} {
			if _, present := properties[name]; !present {
				properties[name] = property
			}
		}
	}
	for _, bound := range []struct {
		Exclusive string
		Inclusive string
	}{
		{
			Exclusive: "exclusiveMinimum",
			Inclusive: "minimum",
		},
		{
			Exclusive: "exclusiveMaximum",
			Inclusive: "maximum",
		},
	} {
		exclusive, isBool := result[bound.Exclusive].(bool)
		if !isBool {
			continue
		}
		delete(result, bound.Exclusive)
		if exclusive {
			if value, present := result[bound.Inclusive]; present {
				result[bound.Exclusive] = value
				delete(result, bound.Inclusive)
			}
		}
	}
	_, hasAdditionalProperties := result["additionalProperties"]
	preserveUnknown := source["x-kubernetes-preserve-unknown-fields"] == true
	if len(properties) > 0 && !hasAdditionalProperties && !preserveUnknown {
		result["additionalProperties"] = false
	}
	if preserveUnknown {
		result["additionalProperties"] = true
	}
	if source["nullable"] == true {
		return map[string]any{
			"anyOf": []any{
				result,
				map[string]any{"type": "null"},
			},
		}, nil
	}
	return result, nil
}

func dashboardResourceEditorSchemasStart(app *stackUiApp) {
	state := &app.resourceEditor
	if state.SchemaCancel != nil {
		state.SchemaCancel()
	}
	if state.Snapshot == nil || app.session == nil || app.k8sClient == nil {
		return
	}
	gvk := state.Snapshot.GroupVersionKind()
	namespaced := state.Def.Namespaced
	state.SchemaId = dashboardResourceSchemaId(gvk)
	revision := state.Revision
	detail := state.Detail
	session := app.session
	client := app.k8sClient
	ctx, cancel := context.WithCancel(session.Context())
	state.SchemaCancel = cancel
	go func() {
		defer cancel()
		ticker := time.NewTicker(30 * time.Second)
		defer ticker.Stop()
		registeredRevision := ""
		for {
			app.mu.Lock()
			current := app.resourceEditor.Revision == revision && app.ResourceDetail == detail
			app.mu.Unlock()
			if !current || ctx.Err() != nil {
				return
			}
			fetchCtx, fetchCancel := context.WithTimeout(ctx, 15*time.Second)
			registration, err := dashboardResourceEditorSchemaFetch(fetchCtx, client, gvk, namespaced)
			if err == nil && registration.Revision != registeredRevision {
				_, err = ucxapi.EditorRegisterSchemas.InvokeEx(fetchCtx, session, ucxapi.EditorRegisterSchemasRequest{
					Schemas: []ucxapi.EditorSchemaRegistration{registration},
				})
				if err == nil {
					registeredRevision = registration.Revision
				}
			}
			fetchCancel()
			if ctx.Err() != nil {
				return
			}
			schemaError := ""
			if err != nil {
				schemaError = fmt.Sprintf("Could not refresh schema validation: %s", err)
			}
			app.mu.Lock()
			current = app.resourceEditor.Revision == revision && app.ResourceDetail == detail
			if current && app.resourceEditor.SchemaError != schemaError {
				app.resourceEditor.SchemaError = schemaError
				ucx.AppUpdateUi(app)
			}
			app.mu.Unlock()
			if !current {
				return
			}
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
			}
		}
	}()
}
