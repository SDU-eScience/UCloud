package ucx_syncthing

import (
	"context"
	"encoding/json"
	"strings"
	"sync"
	"time"

	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
)

type application struct {
	mu      sync.Mutex      `ucx:"-"`
	session *ucx.Session    `ucx:"-"`
	store   *stateStore     `ucx:"-"`
	api     *apiRuntime     `ucx:"-"`
	rescans map[string]bool `ucx:"-"`
	once    sync.Once       `ucx:"-"`

	JobID string
	State Snapshot
}

func newApplication(store *stateStore, api *apiRuntime) *application {
	return &application{store: store, api: api, rescans: map[string]bool{}, State: store.read()}
}

func (a *application) Mutex() *sync.Mutex {
	return &a.mu
}

func (a *application) Session() **ucx.Session {
	return &a.session
}

func (a *application) UserInterface() ucx.UiNode {
	// The Syncthing page owns presentation. This empty root only establishes the
	// local UCX frame whose model carries JobID and State.
	return ucx.UiNode{}
}

func (a *application) OnInit() {
	a.State = a.store.read()
}

func (a *application) OnSysHello(payload string) {
	var request orc.AppUcxConnectJobProviderRequest
	if json.Unmarshal([]byte(payload), &request) == nil {
		a.JobID = request.Job.Id
	}
	a.once.Do(func() { go a.publishUpdates() })
}

func (a *application) OnMessage(message ucx.Frame) {
	if message.Opcode != ucx.OpUiEvent || message.UiEvent.NodeId != "syncthing.rescanFolder" ||
		message.UiEvent.Event != string(ucx.UiEventClick) || message.UiEvent.Value.Kind != ucx.ValueString {
		return
	}

	folderID := strings.TrimSpace(message.UiEvent.Value.String)
	if folderID == "" || len(folderID) > maxLabelLength || a.rescans[folderID] {
		return
	}
	found := false
	for _, folder := range a.store.read().Folders {
		if folder.ID == folderID {
			found = true
			break
		}
	}
	if !found {
		log.Warn("UCX Syncthing: refusing rescan for unknown folder")
		return
	}

	a.rescans[folderID] = true
	go a.rescanFolder(folderID)
}

func (a *application) rescanFolder(folderID string) {
	ctx, cancel := context.WithTimeout(a.session.Context(), 30*time.Second)
	err := apiRequestFolderScan(ctx, a.api, folderID)
	cancel()

	a.mu.Lock()
	delete(a.rescans, folderID)
	a.mu.Unlock()
	if err != nil && a.session.Context().Err() == nil {
		log.Warn("UCX Syncthing: failed to request rescan for folder %q: %v", folderID, err)
	}
}

func (a *application) publishUpdates() {
	id, updates := a.store.subscribe()
	defer a.store.unsubscribe(id)

	// OnSysHello runs immediately before the initial model mount. Delay the
	// first asynchronous patch so it cannot overtake that mount.
	timer := time.NewTimer(250 * time.Millisecond)
	defer timer.Stop()
	select {
	case <-a.session.Context().Done():
		return
	case <-timer.C:
	}
	a.mu.Lock()
	a.State = a.store.read()
	ucx.AppUpdateModel(a)
	a.mu.Unlock()

	for {
		select {
		case <-a.session.Context().Done():
			return
		case <-updates:
			a.mu.Lock()
			a.State = a.store.read()
			ucx.AppUpdateModel(a)
			a.mu.Unlock()
		}
	}
}
