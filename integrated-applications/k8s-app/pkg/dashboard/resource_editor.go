package dashboard

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"strings"
	"time"

	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	k8syaml "k8s.io/apimachinery/pkg/util/yaml"
	"k8s.io/client-go/dynamic"
	"sigs.k8s.io/yaml"

	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
)

type dashboardResourceEditorState struct {
	Detail       string
	Revision     uint64
	Def          ResourceTypeDef
	Snapshot     *unstructured.Unstructured
	Loading      bool
	Editing      bool
	Creating     bool
	Saving       bool
	Error        string
	SchemaId     string
	SchemaError  string
	SchemaCancel context.CancelFunc
}

func dashboardResourceEditorNode(app *stackUiApp, detail string) ucx.UiNode {
	state := app.resourceEditor
	bindPath := "resourceYaml"
	if state.Editing {
		bindPath = "resourceDraft"
	}
	tabLabel := detail
	if ref, ok := parseResourceDetail(detail); ok && !ref.hasContainer {
		tabLabel = ref.Name
		if ref.Namespace != "" {
			tabLabel = ref.Namespace + "-" + ref.Name
		}
		tabLabel += ".yaml"
	}
	id := fmt.Sprintf("resourceYamlEditor:%d", state.Revision)
	if state.Creating {
		tabLabel = "Create " + state.Def.Label + ".yaml"
	}
	var yamlFieldLinks []ucx.CodeEditorYamlFieldLink
	if state.Creating {
		yamlFieldLinks = dashboardResourceCreateFieldLinks(state.Def)
	}
	editor := ucx.CodeEditor(id, bindPath, ucx.CodeEditorProps{
		ShowSchemaReference: true,
		MarkdownFixer:       "k8s-api",
		DocumentId:          detail,
		SchemaId:            state.SchemaId,
		Revision:            fmt.Sprint(state.Revision),
		Lang:                "yaml",
		ReadOnly:            !state.Editing || state.Loading || state.Saving,
		Saving:              state.Saving,
		ShowSave:            state.Editing,
		ShowClose:           state.Editing,
		CloseLabel:          "Cancel",
		AutoFocus:           state.Editing,
		TabLabel:            tabLabel,
		YamlFieldLinks:      yamlFieldLinks,
	}).WithStretch().On(ucx.UiEventSave, func(ev ucx.UiEvent) {
		if app.resourceEditor.Revision != state.Revision || ev.Value.Kind != ucx.ValueString {
			return
		}
		dashboardResourceEditorSave(app, ev.Value.String)
	}).On(ucx.UiEventClose, func(ev ucx.UiEvent) {
		if app.resourceEditor.Revision != state.Revision {
			return
		}
		dashboardResourceEditorCancel(app)
	})
	if state.Loading {
		editor = editor.Children(ucx.Spinner(16), ucx.Text("Loading YAML…"))
	} else if !state.Editing && state.Def.CanUpdate {
		editor = editor.Children(
			ucx.IconButtonEx(fmt.Sprintf("editResourceYaml:%d", state.Revision), "Edit", ucx.ColorTextSecondary, ucx.IconHeroPencilSquare).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				if app.resourceEditor.Revision != state.Revision || app.ResourceDetail != detail {
					return
				}
				dashboardResourceEditorLoad(app, detail, true)
			}),
		)
	}
	children := []ucx.UiNode{}
	if state.Error != "" {
		children = append(children, ucx.Warning(state.Error).Sx(ucx.SxM(8), ucx.SxMt(16)))
	}
	if state.SchemaError != "" {
		children = append(children, ucx.Warning(state.SchemaError).Sx(ucx.SxM(8)))
	}
	children = append(children, editor)
	return ucx.Flex(ucx.FlexProps{Direction: "column"}).
		Sx(ucx.SxFlex("1 1 0"), ucx.SxMinHeight(0), ucx.SxHeightPercent(100)).
		Children(children...)
}

func dashboardResourceEditorLoad(app *stackUiApp, detail string, editing bool) {
	if editing && app.resourceEditor.Saving {
		return
	}
	sameResource := app.resourceEditor.Detail == detail
	if editing && sameResource && (app.resourceEditor.Editing || app.resourceEditor.Loading) {
		return
	}
	revision := app.resourceEditor.Revision + 1
	if app.resourceEditor.SchemaCancel != nil {
		app.resourceEditor.SchemaCancel()
	}
	app.resourceEditor = dashboardResourceEditorState{
		Detail:   detail,
		Revision: revision,
	}
	defer ucx.AppUpdateUi(app)
	app.ResourceDraft = ""
	if !editing || !sameResource {
		app.ResourceYaml = ""
	}
	ref, parsed := parseResourceDetail(detail)
	if !parsed || ref.hasContainer || ref.Type == "provisioning" {
		return
	}
	def, ok := app.resolveType(ref.Type)
	if !ok {
		app.resourceEditor.Error = fmt.Sprintf("Unknown resource type: %s", ref.Type)
		return
	}
	if editing && !ref.creating() && !def.CanUpdate {
		app.resourceEditor.Error = "This resource type does not support updates"
		return
	}
	client := app.k8sClient
	session := app.session
	if client == nil || session == nil {
		app.resourceEditor.Error = "Kubernetes client is not available"
		return
	}
	app.resourceEditor.Def = def
	app.resourceEditor.Loading = true
	if ref.creating() {
		dashboardResourceCreateLoad(app, ref.Namespace)
		return
	}
	go func() {
		ctx, cancel := context.WithTimeout(session.Context(), 15*time.Second)
		defer cancel()
		object, err := dashboardResourceGet(ctx, client, def, ref.Namespace, ref.Name)
		var view, draft string
		if err == nil {
			view, draft, err = dashboardResourceYamlTexts(object)
		}
		app.mu.Lock()
		defer app.mu.Unlock()
		if app.ResourceDetail != detail || app.resourceEditor.Revision != revision {
			return
		}
		app.resourceEditor.Loading = false
		if err != nil {
			app.resourceEditor.Error = fmt.Sprintf("Failed to fetch YAML: %s", err)
		} else {
			app.resourceEditor.Snapshot = object
			app.resourceEditor.Editing = editing
			app.ResourceYaml = view
			app.ResourceDraft = draft
			app.resourceEditor.Revision++
			dashboardResourceEditorSchemasStart(app)
		}
		ucx.AppUpdateUi(app)
	}()
}

func dashboardResourceEditorSave(app *stackUiApp, source string) {
	state := &app.resourceEditor
	if !state.Editing || state.Loading || state.Saving || state.Snapshot == nil || state.Detail != app.ResourceDetail {
		return
	}
	client := app.k8sClient
	session := app.session
	if client == nil || session == nil {
		state.Error = "Kubernetes client is not available"
		ucx.AppUpdateUi(app)
		return
	}
	app.ResourceDraft = source
	dashboardResourceCreateDraftKeep(app)
	state.Saving = true
	state.Error = ""
	detail := state.Detail
	revision := state.Revision
	def := state.Def
	original := state.Snapshot.DeepCopy()
	creating := state.Creating
	ucx.AppUpdateUi(app)
	go func() {
		ctx, cancel := context.WithTimeout(session.Context(), 15*time.Second)
		defer cancel()
		var object *unstructured.Unstructured
		var err error
		if creating {
			object, err = dashboardResourceCreateYaml(ctx, client, def, original, source)
		} else {
			object, err = dashboardResourceUpdateYaml(ctx, client, def, original, source)
		}
		var view, draft string
		if err == nil {
			view, draft, err = dashboardResourceYamlTexts(object)
		}
		app.mu.Lock()
		defer app.mu.Unlock()
		if app.resourceEditor.Revision != revision {
			return
		}
		app.resourceEditor.Saving = false
		if app.ResourceDetail != detail {
			dashboardResourceEditorLoad(app, app.ResourceDetail, false)
			return
		}
		if err != nil {
			if apierrors.IsConflict(err) {
				app.resourceEditor.Error = "This resource changed since editing started. Your draft has been kept. Cancel and edit again to load the latest version."
			} else {
				app.resourceEditor.Error = fmt.Sprintf("Failed to save YAML: %s", err)
			}
		} else if creating {
			delete(app.resourceCreateDrafts, detail)
			dashboardResourceCreateBack(app)
			return
		} else if len(app.resourceNavigation) > 1 {
			if app.resourceEditor.SchemaCancel != nil {
				app.resourceEditor.SchemaCancel()
			}
			app.resourceEditor = dashboardResourceEditorState{}
			app.ResourceYaml = ""
			app.ResourceDraft = ""
			app.ResourceDetail = ""
			app.prevDetail = ""
			if app.poller != nil {
				app.poller.signalPollNow()
			}
			backTarget := app.resourceNavigation[len(app.resourceNavigation)-2].route
			app.resourceNavigation = app.resourceNavigation[:len(app.resourceNavigation)-1]
			ucxsvc.RouterPushPage(app, backTarget)
			return
		} else {
			app.resourceEditor.Snapshot = object
			app.resourceEditor.Editing = false
			app.ResourceYaml = view
			app.ResourceDraft = draft
			app.resourceEditor.Revision++
			dashboardResourceEditorSchemasStart(app)
			if app.poller != nil {
				app.poller.signalPollNow()
			}
		}
		ucx.AppUpdateUi(app)
	}()
}

func dashboardResourceEditorCancel(app *stackUiApp) {
	state := &app.resourceEditor
	if !state.Editing || state.Saving || state.Detail != app.ResourceDetail {
		return
	}
	if state.Creating {
		dashboardResourceCreateBack(app)
		return
	}
	state.Editing = false
	state.Error = ""
	state.Revision++
	dashboardResourceEditorSchemasStart(app)
	app.ResourceDraft = ""
	ucx.AppUpdateUi(app)
}

func dashboardResourceInterface(client *K8sClient, def ResourceTypeDef, namespace string) dynamic.ResourceInterface {
	if def.Namespaced {
		return client.Dynamic.Resource(def.Gvr).Namespace(namespace)
	}
	return client.Dynamic.Resource(def.Gvr)
}

func dashboardResourceGet(ctx context.Context, client *K8sClient, def ResourceTypeDef, namespace string, name string) (*unstructured.Unstructured, error) {
	resource := dashboardResourceInterface(client, def, namespace)
	return resource.Get(ctx, name, metav1.GetOptions{})
}

func dashboardResourceYaml(object *unstructured.Unstructured) (string, error) {
	data, err := yaml.Marshal(object.Object)
	return string(data), err
}

func dashboardResourceYamlTexts(object *unstructured.Unstructured) (string, string, error) {
	viewObject := object.DeepCopy()
	unstructured.RemoveNestedField(viewObject.Object, "metadata", "managedFields")
	unstructured.RemoveNestedField(viewObject.Object, "metadata", "selfLink")
	unstructured.RemoveNestedField(viewObject.Object, "metadata", "resourceVersion")
	annotations := viewObject.GetAnnotations()
	delete(annotations, "kubectl.kubernetes.io/last-applied-configuration")
	if len(annotations) == 0 {
		unstructured.RemoveNestedField(viewObject.Object, "metadata", "annotations")
	} else {
		viewObject.SetAnnotations(annotations)
	}
	view, err := dashboardResourceYaml(viewObject)
	if err != nil {
		return "", "", err
	}
	editable := object.DeepCopy()
	delete(editable.Object, "status")
	metadata, _, err := unstructured.NestedMap(editable.Object, "metadata")
	if err != nil {
		return "", "", err
	}
	for _, field := range dashboardResourceManagedMetadata {
		delete(metadata, field)
	}
	editable.Object["metadata"] = metadata
	draft, err := dashboardResourceYaml(editable)
	return view, draft, err
}

func dashboardResourceUpdateYaml(ctx context.Context, client *K8sClient, def ResourceTypeDef, original *unstructured.Unstructured, source string) (*unstructured.Unstructured, error) {
	if !def.CanUpdate {
		return nil, fmt.Errorf("this resource type does not support updates")
	}
	decoder := k8syaml.NewYAMLOrJSONDecoder(strings.NewReader(source), 4096)
	var object unstructured.Unstructured
	err := decoder.Decode(&object)
	if err != nil {
		return nil, fmt.Errorf("invalid YAML: %w", err)
	}
	var extra json.RawMessage
	err = decoder.Decode(&extra)
	if err != io.EOF {
		return nil, fmt.Errorf("provide exactly one Kubernetes resource")
	}
	if len(object.Object) == 0 {
		return nil, fmt.Errorf("provide a Kubernetes resource object")
	}
	sameType := object.GetAPIVersion() == original.GetAPIVersion() && object.GetKind() == original.GetKind()
	sameName := object.GetName() == original.GetName() && object.GetNamespace() == original.GetNamespace()
	if !sameType || !sameName {
		return nil, fmt.Errorf("apiVersion, kind, metadata.name, and metadata.namespace cannot be changed")
	}
	if _, present := object.Object["status"]; present {
		return nil, fmt.Errorf("status cannot be edited here")
	}
	metadata, _, err := unstructured.NestedMap(object.Object, "metadata")
	if err != nil {
		return nil, fmt.Errorf("invalid metadata: %w", err)
	}
	protected := original.DeepCopy()
	originalMetadata, _, err := unstructured.NestedMap(protected.Object, "metadata")
	if err != nil {
		return nil, err
	}
	for _, field := range dashboardResourceManagedMetadata {
		delete(metadata, field)
		if value, present := originalMetadata[field]; present {
			metadata[field] = value
		}
	}
	object.Object["metadata"] = metadata
	if status, present := protected.Object["status"]; present {
		object.Object["status"] = status
	}
	resource := dashboardResourceInterface(client, def, original.GetNamespace())
	return resource.Update(ctx, &object, metav1.UpdateOptions{
		FieldValidation: metav1.FieldValidationStrict,
	})
}

var dashboardResourceManagedMetadata = []string{
	"creationTimestamp",
	"deletionTimestamp",
	"deletionGracePeriodSeconds",
	"generation",
	"managedFields",
	"resourceVersion",
	"selfLink",
	"uid",
}
