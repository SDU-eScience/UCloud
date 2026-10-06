package dashboard

import (
	"context"
	"fmt"
	"strings"
	"time"

	apierrors "k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
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
	if parts := strings.Split(detail, "/"); len(parts) == 3 {
		tabLabel = parts[2]
		if parts[1] != "" {
			tabLabel = parts[1] + "-" + parts[2]
		}
		tabLabel += ".yaml"
	}
	id := fmt.Sprintf("resourceYamlEditor:%d", state.Revision)
	editor := ucx.CodeEditor(id, bindPath, ucx.CodeEditorProps{
		DocumentId: detail,
		SchemaId:   state.SchemaId,
		Revision:   fmt.Sprint(state.Revision),
		Lang:       "yaml",
		ReadOnly:   !state.Editing || state.Loading || state.Saving,
		Saving:     state.Saving,
		ShowSave:   state.Editing,
		ShowClose:  state.Editing,
		CloseLabel: "Cancel",
		AutoFocus:  state.Editing,
		TabLabel:   tabLabel,
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
		children = append(children, ucx.Warning(state.Error).Sx(ucx.SxM(8)))
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
	parts := strings.Split(detail, "/")
	if len(parts) != 3 || parts[0] == "provisioning" {
		return
	}
	def, ok := app.resolveType(parts[0])
	if !ok {
		app.resourceEditor.Error = fmt.Sprintf("Unknown resource type: %s", parts[0])
		return
	}
	if editing && !def.CanUpdate {
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
	go func() {
		ctx, cancel := context.WithTimeout(session.Context(), 15*time.Second)
		defer cancel()
		object, err := dashboardResourceGet(ctx, client, def, parts[1], parts[2])
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

func dashboardResourceEditorCancel(app *stackUiApp) {
	state := &app.resourceEditor
	if !state.Editing || state.Saving || state.Detail != app.ResourceDetail {
		return
	}
	state.Editing = false
	state.Error = ""
	state.Revision++
	dashboardResourceEditorSchemasStart(app)
	app.ResourceDraft = ""
	ucx.AppUpdateUi(app)
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
	state.Saving = true
	state.Error = ""
	detail := state.Detail
	revision := state.Revision
	def := state.Def
	original := state.Snapshot.DeepCopy()
	ucx.AppUpdateUi(app)
	go func() {
		ctx, cancel := context.WithTimeout(session.Context(), 15*time.Second)
		defer cancel()
		object, err := dashboardResourceUpdateYaml(ctx, client, def, original, source)
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
