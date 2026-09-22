package dashboard

import (
	"context"
	"errors"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/watch"
	"k8s.io/client-go/dynamic"

	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

const (
	pollInterval                  = 5 * time.Second
	resourceWatchRetryDelay       = 2 * time.Second
	resourceWatchBackoffDelay     = 30 * time.Second
	resourceWatchEstablishTimeout = 15 * time.Second
	resourceListTimeout           = 15 * time.Second
	resourceWatchServerTimeoutSec = 5 * time.Minute
	resourceWatchMaxBatch         = 250
	resourceWatchBatchDelay       = 100 * time.Millisecond
)

var resourceWatchServerTimeoutSeconds = int64(resourceWatchServerTimeoutSec / time.Second)

type resourceWatchResult int

const (
	resourceWatchContinue resourceWatchResult = iota
	resourceWatchDone
	resourceWatchRestart
	resourceWatchRetry
	resourceWatchRelist
	resourceWatchBackoff
)

type resourceWatchFailure int

const (
	resourceFailureTransient resourceWatchFailure = iota
	resourceFailureExpired
	resourceFailurePersistent
)

type resourceSelection struct {
	epoch     uint64
	typeId    string
	namespace string
	def       ResourceTypeDef
}

type resourcePoller struct {
	mu              sync.Mutex
	client          *K8sClient
	session         *ucx.Session
	cancel          context.CancelFunc
	activeType      string
	activeNamespace string
	selectionEpoch  uint64
	selectionCancel context.CancelFunc
	pollNow         chan util.Empty
	lastRows        map[string]ResourceRow
	lastColumns     []ucx.TableColumn
	lastRev         int64
	lastRv          string
	cache           map[string]*unstructured.Unstructured
	customTypes     []ResourceTypeDef
	customAt        atomic.Int64
	onTypesChanged  func()
}

func newResourcePoller(client *K8sClient, session *ucx.Session, activeType string, onTypesChanged func()) *resourcePoller {
	p := &resourcePoller{
		client:         client,
		session:        session,
		activeType:     activeType,
		lastRows:       map[string]ResourceRow{},
		pollNow:        make(chan util.Empty, 1),
		onTypesChanged: onTypesChanged,
	}

	ctx, cancel := context.WithCancel(session.Context())
	p.cancel = cancel
	go p.run(ctx)
	return p
}

func (p *resourcePoller) SetActiveType(typeId string) {
	if typeId == "" {
		return
	}

	p.mu.Lock()
	changed := p.activeType != typeId
	if changed {
		p.activeType = typeId
		p.resetTableLocked()
	}
	p.mu.Unlock()

	if changed {
		p.signalPollNow()
	}
}

func (p *resourcePoller) SetActiveNamespace(namespace string) {
	p.mu.Lock()
	changed := p.activeNamespace != namespace
	if changed {
		p.activeNamespace = namespace
		p.resetTableLocked()
	}
	p.mu.Unlock()

	if changed {
		p.signalPollNow()
	}
}

func (p *resourcePoller) resetTableLocked() {
	if p.selectionCancel != nil {
		p.selectionCancel()
		p.selectionCancel = nil
	}
	p.selectionEpoch++
	p.lastRows = map[string]ResourceRow{}
	p.lastColumns = nil
	p.lastRev = 0
	p.lastRv = ""
	p.cache = nil
}

func (p *resourcePoller) signalPollNow() {
	select {
	case p.pollNow <- util.Empty{}:
	default:
	}
}

func (p *resourcePoller) run(ctx context.Context) {
	p.refreshCustomTypes(ctx)

	for {
		p.resourceMaybeRefreshCustomTypes(ctx)

		selection, ok := p.resourceCurrentSelection()
		if !ok {
			if !p.resourceWaitForSelection(ctx) {
				return
			}
			continue
		}

		switch p.resourceRunWatchCycle(ctx, selection) {
		case resourceWatchDone:
			return
		case resourceWatchRestart:
		case resourceWatchRelist:
			if !p.resourceWait(ctx, resourceWatchRetryDelay) {
				return
			}
		case resourceWatchBackoff:
			if !p.resourceWait(ctx, resourceWatchBackoffDelay) {
				return
			}
		default:
			if !p.resourceWait(ctx, resourceWatchRetryDelay) {
				return
			}
		}
	}
}

func (p *resourcePoller) resourceWaitForSelection(ctx context.Context) bool {
	ticker := time.NewTicker(pollInterval)
	defer ticker.Stop()

	select {
	case <-ctx.Done():
		return false
	case <-p.pollNow:
	case <-ticker.C:
		p.resourceMaybeRefreshCustomTypes(ctx)
	}
	return true
}

func (p *resourcePoller) resourceMaybeRefreshCustomTypes(ctx context.Context) {
	if time.Since(time.Unix(0, p.customAt.Load())) > time.Minute {
		p.refreshCustomTypes(ctx)
	}
}

func (p *resourcePoller) resourceCurrentSelection() (resourceSelection, bool) {
	p.mu.Lock()
	typeId := p.activeType
	namespace := p.activeNamespace
	epoch := p.selectionEpoch
	customTypes := p.customTypes
	p.mu.Unlock()

	if p.client == nil || typeId == "" {
		return resourceSelection{}, false
	}

	def, ok := ResourceType(typeId)
	if !ok {
		for i := range customTypes {
			if customTypes[i].Id == typeId {
				def = customTypes[i]
				ok = true
				break
			}
		}
	}
	if !ok {
		return resourceSelection{}, false
	}

	return resourceSelection{epoch: epoch, typeId: typeId, namespace: namespace, def: def}, true
}

func (p *resourcePoller) resourceRunWatchCycle(ctx context.Context, selection resourceSelection) resourceWatchResult {
	cycleCtx, cancelCycle := context.WithCancel(ctx)
	defer cancelCycle()

	p.mu.Lock()
	if p.selectionEpoch != selection.epoch {
		p.mu.Unlock()
		return resourceWatchRestart
	}
	p.selectionCancel = cancelCycle
	p.mu.Unlock()

	rv, result := p.resourceListAndSend(ctx, cycleCtx, selection)
	if result != resourceWatchContinue {
		return result
	}

	for {
		result = p.resourceWatchLoop(ctx, cycleCtx, selection, rv)
		if result != resourceWatchRetry {
			return result
		}

		rv = p.resourceCurrentRv()
		if rv == "" {
			return resourceWatchRelist
		}

		p.resourceMaybeRefreshCustomTypes(cycleCtx)

		if !p.resourceWait(cycleCtx, resourceWatchRetryDelay) {
			return p.resourceCycleResult(ctx)
		}
		if p.resourceSelectionChanged(selection) {
			return resourceWatchRestart
		}
	}
}

func (p *resourcePoller) resourceCycleResult(rootCtx context.Context) resourceWatchResult {
	if rootCtx.Err() != nil {
		return resourceWatchDone
	}
	return resourceWatchRestart
}

func (p *resourcePoller) resourceListAndSend(rootCtx context.Context, cycleCtx context.Context, selection resourceSelection) (string, resourceWatchResult) {
	if p.session == nil || p.client == nil {
		log.Warn("k8s-app: list skipped, session or client missing")
		return "", resourceWatchBackoff
	}

	listCtx, cancelList := context.WithTimeout(cycleCtx, resourceListTimeout)
	list, err := p.resourceInterface(selection).List(listCtx, listOptions)
	cancelList()
	if err != nil {
		if cycleCtx.Err() != nil {
			return "", p.resourceCycleResult(rootCtx)
		}
		log.Warn("k8s-app: failed to list %s: %s", selection.def.Id, err)
		if resourceClassifyWatchFailure(err) == resourceFailurePersistent {
			return "", resourceWatchBackoff
		}
		return "", resourceWatchRelist
	}

	cache := make(map[string]*unstructured.Unstructured, len(list.Items))
	for i := range list.Items {
		obj := list.Items[i]
		cache[resourceCacheKey(&obj)] = &obj
	}
	rv := list.GetResourceVersion()

	p.mu.Lock()
	if p.selectionEpoch != selection.epoch {
		p.mu.Unlock()
		return "", resourceWatchRestart
	}
	p.cache = cache
	p.lastRv = rv
	p.mu.Unlock()

	p.resourceSendTableUpdate(selection, true)
	log.Info("k8s-app: listed %d rows for %s (rv=%s)", len(list.Items), selection.typeId, rv)
	return rv, resourceWatchContinue
}

func (p *resourcePoller) resourceWatchLoop(rootCtx context.Context, cycleCtx context.Context, selection resourceSelection, rv string) resourceWatchResult {
	watcher, stopWatch, err := p.resourceEstablishWatch(cycleCtx, selection, rv)
	if err != nil {
		if cycleCtx.Err() != nil {
			return p.resourceCycleResult(rootCtx)
		}
		log.Warn("k8s-app: failed to watch %s: %s", selection.def.Id, err)
		switch resourceClassifyWatchFailure(err) {
		case resourceFailureExpired:
			return resourceWatchRelist
		case resourceFailurePersistent:
			return resourceWatchBackoff
		default:
			return resourceWatchRetry
		}
	}
	defer stopWatch()

	renderTicker := time.NewTicker(pollInterval)
	defer renderTicker.Stop()

	burst := 0
	var batchC <-chan time.Time

	flush := func() {
		burst = 0
		batchC = nil
		p.resourceSendTableUpdate(selection, false)
	}
	defer flush()

	for {
		select {
		case <-cycleCtx.Done():
			return p.resourceCycleResult(rootCtx)

		case <-p.pollNow:
			if p.resourceSelectionChanged(selection) {
				return resourceWatchRestart
			}

		case <-renderTicker.C:
			p.resourceMaybeRefreshCustomTypes(cycleCtx)
			flush()

		case <-batchC:
			flush()

		case event, ok := <-watcher.ResultChan():
			if !ok {
				if cycleCtx.Err() != nil {
					return p.resourceCycleResult(rootCtx)
				}
				log.Info("k8s-app: watch for %s closed, resuming from rv=%s", selection.typeId, p.resourceCurrentRv())
				return resourceWatchRetry
			}

			switch event.Type {
			case watch.Error:
				statusErr := apierrors.FromObject(event.Object)
				log.Warn("k8s-app: watch error event for %s: %s", selection.typeId, statusErr)
				switch resourceClassifyWatchFailure(statusErr) {
				case resourceFailureExpired:
					return resourceWatchRelist
				case resourceFailurePersistent:
					return resourceWatchBackoff
				default:
					return resourceWatchRetry
				}
			case watch.Added, watch.Modified, watch.Deleted:
				if !p.resourceApplyWatchEvent(selection, event) {
					return resourceWatchRestart
				}
				burst++
				if burst >= resourceWatchMaxBatch {
					flush()
				} else if batchC == nil {
					batchC = time.After(resourceWatchBatchDelay)
				}
			case watch.Bookmark:
				if !p.resourceApplyWatchEvent(selection, event) {
					return resourceWatchRestart
				}
			}
		}
	}
}

func (p *resourcePoller) resourceEstablishWatch(cycleCtx context.Context, selection resourceSelection, rv string) (watch.Interface, func(), error) {
	watchCtx, cancelWatch := context.WithCancel(cycleCtx)

	timer := time.AfterFunc(resourceWatchEstablishTimeout, cancelWatch)

	watcher, err := p.resourceInterface(selection).Watch(watchCtx, metav1.ListOptions{
		ResourceVersion:     rv,
		AllowWatchBookmarks: true,
		TimeoutSeconds:      &resourceWatchServerTimeoutSeconds,
	})

	if !timer.Stop() {
		if watcher != nil {
			watcher.Stop()
		}
		cancelWatch()
		return nil, func() {}, errors.New("watch establishment timed out")
	}

	if err != nil {
		cancelWatch()
		return nil, func() {}, err
	}

	return watcher, func() {
		watcher.Stop()
		cancelWatch()
	}, nil
}

func (p *resourcePoller) resourceApplyWatchEvent(selection resourceSelection, event watch.Event) bool {
	obj, ok := event.Object.(*unstructured.Unstructured)
	if !ok {
		return true
	}

	p.mu.Lock()
	defer p.mu.Unlock()

	if p.selectionEpoch != selection.epoch {
		return false
	}

	if rv := obj.GetResourceVersion(); rv != "" {
		p.lastRv = rv
	}

	key := resourceCacheKey(obj)
	switch event.Type {
	case watch.Added, watch.Modified:
		if p.cache == nil {
			p.cache = map[string]*unstructured.Unstructured{}
		}
		stored := *obj
		p.cache[key] = &stored
	case watch.Deleted:
		delete(p.cache, key)
	}

	return true
}

func (p *resourcePoller) resourceSendTableUpdate(selection resourceSelection, snapshot bool) {
	session := p.session
	if session == nil {
		return
	}

	p.mu.Lock()
	defer p.mu.Unlock()

	if p.selectionEpoch != selection.epoch {
		return
	}

	items := make([]unstructured.Unstructured, 0, len(p.cache))
	for _, obj := range p.cache {
		items = append(items, *obj)
	}
	rows := resourceRowsForType(items, selection.def)

	rowByKey := make(map[string]ResourceRow, len(rows))
	for _, row := range rows {
		rowByKey[row.Key] = row
	}

	forceSnapshot := snapshot || !columnsEqual(p.lastColumns, selection.def.Columns) || p.lastRev == 0

	var upserts []ucx.TableRow
	var removed []string
	if forceSnapshot {
		upserts = make([]ucx.TableRow, 0, len(rows))
		for _, row := range rows {
			upserts = append(upserts, ucx.TableRow{Key: row.Key, Group: row.Group, Cells: row.Cells})
		}
	} else {
		for _, row := range rows {
			if prev, ok := p.lastRows[row.Key]; !ok || !rowsEqual(prev, row) {
				upserts = append(upserts, ucx.TableRow{Key: row.Key, Group: row.Group, Cells: row.Cells})
			}
		}
		for key := range p.lastRows {
			if _, ok := rowByKey[key]; !ok {
				removed = append(removed, key)
			}
		}
		if len(upserts) == 0 && len(removed) == 0 {
			return
		}
	}

	p.lastRows = rowByKey
	p.lastColumns = selection.def.Columns
	p.lastRev++
	rev := p.lastRev

	session.SendTableUpdate(ucx.TableUpdate{
		TableId:  selection.typeId,
		Revision: rev,
		Snapshot: forceSnapshot,
		Columns:  selection.def.Columns,
		Upserts:  upserts,
		Removed:  removed,
	})
}

func (p *resourcePoller) resourceSelectionChanged(selection resourceSelection) bool {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.selectionEpoch != selection.epoch
}

func (p *resourcePoller) resourceCurrentRv() string {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.lastRv
}

func (p *resourcePoller) resourceWait(ctx context.Context, delay time.Duration) bool {
	timer := time.NewTimer(delay)
	defer timer.Stop()

	select {
	case <-ctx.Done():
		return false
	case <-p.pollNow:
	case <-timer.C:
	}

	return true
}

func (p *resourcePoller) resourceInterface(selection resourceSelection) dynamic.ResourceInterface {
	var ri dynamic.ResourceInterface = p.client.Dynamic.Resource(selection.def.Gvr)
	if selection.namespace != "" && selection.def.Namespaced {
		ri = p.client.Dynamic.Resource(selection.def.Gvr).Namespace(selection.namespace)
	}
	return ri
}

func (p *resourcePoller) refreshCustomTypes(ctx context.Context) {
	if p.client == nil {
		return
	}

	discoveryCtx, cancel := context.WithTimeout(ctx, pollInterval)
	defer cancel()

	types := p.client.CustomResourceTypes(discoveryCtx)

	p.mu.Lock()
	firstDiscovery := len(p.customTypes) == 0 && len(types) > 0
	p.customTypes = types
	p.mu.Unlock()
	p.customAt.Store(time.Now().UnixNano())
	log.Info("k8s-app: discovered %d custom resource types", len(types))

	if firstDiscovery {
		if onTypesChanged := p.onTypesChanged; onTypesChanged != nil {
			onTypesChanged()
		}
	}
}

func (p *resourcePoller) customTypeById(typeId string) ResourceTypeDef {
	p.mu.Lock()
	defer p.mu.Unlock()
	for _, def := range p.customTypes {
		if def.Id == typeId {
			return def
		}
	}
	return ResourceTypeDef{}
}

func (p *resourcePoller) customTypesSnapshot() []ResourceTypeDef {
	p.mu.Lock()
	defer p.mu.Unlock()
	return append([]ResourceTypeDef(nil), p.customTypes...)
}

func resourceClassifyWatchFailure(err error) resourceWatchFailure {
	if err == nil {
		return resourceFailureTransient
	}
	if apierrors.IsResourceExpired(err) || apierrors.IsGone(err) {
		return resourceFailureExpired
	}
	if apierrors.IsForbidden(err) ||
		apierrors.IsUnauthorized(err) ||
		apierrors.IsNotFound(err) ||
		apierrors.IsBadRequest(err) ||
		apierrors.IsMethodNotSupported(err) ||
		apierrors.IsNotAcceptable(err) ||
		apierrors.IsUnsupportedMediaType(err) {
		return resourceFailurePersistent
	}
	return resourceFailureTransient
}

func resourceCacheKey(obj *unstructured.Unstructured) string {
	uid := string(obj.GetUID())
	if uid != "" {
		return uid
	}
	return obj.GetNamespace() + "/" + obj.GetName()
}

func resourceRowsForType(items []unstructured.Unstructured, def ResourceTypeDef) []ResourceRow {
	switch {
	case def.Id == "nodes":
		return rowsFromNodes(items)
	case def.Id == "pods":
		return rowsFromPods(items)
	case strings.HasPrefix(def.Id, "crd:"):
		return rowsFromCustom(items, def)
	default:
		return rowsFromGeneric(items, def)
	}
}

func rowsEqual(a, b ResourceRow) bool {
	if a.Key != b.Key || a.Group != b.Group || len(a.Cells) != len(b.Cells) {
		return false
	}
	for i := range a.Cells {
		if a.Cells[i] != b.Cells[i] {
			return false
		}
	}
	return true
}

func columnsEqual(a, b []ucx.TableColumn) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i].Key != b[i].Key || a[i].Label != b[i].Label {
			return false
		}
	}
	return true
}
