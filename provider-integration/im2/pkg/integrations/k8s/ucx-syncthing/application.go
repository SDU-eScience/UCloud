package ucx_syncthing

import (
	"encoding/json"
	"sync"
	"time"

	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
)

type application struct {
	mu      sync.Mutex   `ucx:"-"`
	session *ucx.Session `ucx:"-"`
	store   *stateStore  `ucx:"-"`
	once    sync.Once    `ucx:"-"`

	JobID string
	State Snapshot
}

func newApplication(store *stateStore) *application {
	return &application{store: store, State: store.read()}
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
	_ = message
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
