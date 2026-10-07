package dashboard

import (
	"context"
	"errors"
	"reflect"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/watch"
	"k8s.io/client-go/dynamic"

	"ucloud.dk/iapp/k8s/pkg/maintenance"
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
	filter    resourceFilter
	def       ResourceTypeDef
}

type resourceFilter struct {
	fieldSelector string
	labelSelector string
	title         string
}

func (f resourceFilter) key() string {
	result := "f:" + f.fieldSelector + "|l:" + f.labelSelector
	if f.title != "" {
		result = result + "|t:" + f.title
	}
	return result
}

func parseResourceFilterKey(key string) resourceFilter {
	if !strings.HasPrefix(key, "f:") {
		return resourceFilter{fieldSelector: key}
	}

	rest := strings.TrimPrefix(key, "f:")
	fieldSelector, rest, _ := strings.Cut(rest, "|l:")
	labelSelector, rest, _ := strings.Cut(rest, "|t:")
	return resourceFilter{
		fieldSelector: fieldSelector,
		labelSelector: labelSelector,
		title:         rest,
	}
}

func (f resourceFilter) isEmpty() bool {
	return f.fieldSelector == "" && f.labelSelector == ""
}

type resourcePoller struct {
	mu              sync.Mutex
	sendMu          sync.Mutex
	client          *K8sClient
	session         *ucx.Session
	cancel          context.CancelFunc
	activeType      string
	activeNamespace string
	activeFilter    resourceFilter
	selectionEpoch  uint64
	selectionCancel context.CancelFunc
	pollNow         chan util.Empty
	lastRows        map[string]ResourceRow
	lastColumns     []ucx.TableColumn
	lastRev         int64
	lastRv          string
	cache           map[string]*unstructured.Unstructured
	customTypes     []ResourceTypeDef
	builtinTypes    []ResourceTypeDef
	resourceTypesAt atomic.Int64
	onTypesChanged  func()
	nodeJobIds      func() map[string]string
	extraRows       func(ResourceTypeDef) []ResourceRow
}

func newResourcePoller(
	client *K8sClient,
	session *ucx.Session,
	activeType string,
	nodeJobIds func() map[string]string,
	extraRows func(ResourceTypeDef) []ResourceRow,
	onTypesChanged func(),
) *resourcePoller {
	p := &resourcePoller{
		client:         client,
		session:        session,
		activeType:     activeType,
		lastRows:       map[string]ResourceRow{},
		pollNow:        make(chan util.Empty, 1),
		onTypesChanged: onTypesChanged,
		nodeJobIds:     nodeJobIds,
		extraRows:      extraRows,
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

func (p *resourcePoller) SetActiveFilter(filter resourceFilter) {
	p.mu.Lock()
	changed := p.activeFilter != filter
	if changed {
		p.activeFilter = filter
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
	p.resourceRefreshTypes(ctx)

	for {
		p.resourceMaybeRefreshTypes(ctx)

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
		p.resourceMaybeRefreshTypes(ctx)
	}
	return true
}

func (p *resourcePoller) resourceMaybeRefreshTypes(ctx context.Context) {
	if time.Since(time.Unix(0, p.resourceTypesAt.Load())) > time.Minute {
		p.resourceRefreshTypes(ctx)
	}
}

func (p *resourcePoller) resourceCurrentSelection() (resourceSelection, bool) {
	p.mu.Lock()
	typeId := p.activeType
	namespace := p.activeNamespace
	filter := p.activeFilter
	epoch := p.selectionEpoch
	p.mu.Unlock()

	if p.client == nil || typeId == "" {
		return resourceSelection{}, false
	}

	def, ok := p.resourceTypeById(typeId)
	if !ok {
		return resourceSelection{}, false
	}

	return resourceSelection{epoch: epoch, typeId: typeId, namespace: namespace, filter: filter, def: def}, true
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

		p.resourceMaybeRefreshTypes(cycleCtx)

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
	list, err := p.resourceInterface(selection).List(listCtx, p.resourceListOptions(selection))
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
			flush()

		case <-renderTicker.C:
			p.resourceMaybeRefreshTypes(cycleCtx)
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
		FieldSelector:       selection.filter.fieldSelector,
		LabelSelector:       selection.filter.labelSelector,
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

	isNodes := selection.def.Id == "nodes"
	maintenanceSnapshot := map[string]maintenance.Operation{}
	maintenanceUnavailable := false
	nodeJobIds := map[string]string{}
	if isNodes {
		read, err := maintenance.Snapshot()
		if err != nil {
			log.Warn("k8s-app: could not read the maintenance state: %s", err)
			maintenanceUnavailable = true
		} else {
			maintenanceSnapshot = read
		}
		if p.nodeJobIds != nil {
			nodeJobIds = p.nodeJobIds()
		}
	}

	p.mu.Lock()

	if p.selectionEpoch != selection.epoch {
		p.mu.Unlock()
		return
	}
	items := make([]unstructured.Unstructured, 0, len(p.cache))
	for _, obj := range p.cache {
		items = append(items, *obj)
	}
	rows := resourceRowsForType(items, selection.def)
	extraRows := p.extraRows
	p.mu.Unlock()

	if extraRows != nil {
		baseKeys := make(map[string]bool, len(rows))
		baseNames := make(map[string]bool, len(rows))
		for _, row := range rows {
			baseKeys[row.Key] = true
			if len(row.Cells) > 0 {
				baseNames[row.Cells[0]] = true
			}
		}

		for _, row := range extraRows(selection.def) {
			if baseKeys[row.Key] {
				continue
			}
			if len(row.Cells) > 0 && baseNames[row.Cells[0]] {
				continue
			}
			rows = append(rows, row)
		}
	}

	p.mu.Lock()

	if p.selectionEpoch != selection.epoch {
		p.mu.Unlock()
		return
	}

	for i := range rows {
		rows[i].Actions = resourceRowActions(selection.def, rows[i], p.cache[rows[i].Key], nodeJobIds, maintenanceSnapshot, maintenanceUnavailable)
		if isNodes && !strings.HasPrefix(rows[i].Key, provisioningRowKeyPrefix) && len(rows[i].Cells) > 3 {
			operation, present := maintenanceSnapshot[rows[i].Cells[0]]
			rows[i].Cells[3] = maintenanceCellForOperation(operation, present)
		}
	}

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
			upserts = append(upserts, resourceTableUpsert(row))
		}
	} else {
		for _, row := range rows {
			if prev, ok := p.lastRows[row.Key]; !ok || !rowsEqual(prev, row) {
				upserts = append(upserts, resourceTableUpsert(row))
			}
		}
		for key := range p.lastRows {
			if _, ok := rowByKey[key]; !ok {
				removed = append(removed, key)
			}
		}
		if len(upserts) == 0 && len(removed) == 0 {
			p.mu.Unlock()
			return
		}
	}

	p.lastRows = rowByKey
	p.lastColumns = selection.def.Columns
	p.lastRev++
	rev := p.lastRev
	p.mu.Unlock()

	p.sendMu.Lock()
	defer p.sendMu.Unlock()

	p.mu.Lock()
	stale := p.selectionEpoch != selection.epoch
	p.mu.Unlock()
	if stale {
		return
	}

	session.SendTableUpdate(ucx.TableUpdate{
		TableId:  resourceDatasetId(selection),
		Revision: rev,
		Snapshot: forceSnapshot,
		Columns:  selection.def.Columns,
		Upserts:  upserts,
		Removed:  removed,
	})
}

func resourceDatasetId(selection resourceSelection) string {
	id := selection.typeId
	if selection.namespace != "" && selection.def.Namespaced {
		id = id + "/" + selection.namespace
	}
	if selection.filter.fieldSelector != "" || selection.filter.labelSelector != "" {
		id = id + "?" + selection.filter.fieldSelector + "&" + selection.filter.labelSelector
	}
	return id
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
	if selection.namespace != "" && selection.def.Namespaced && !strings.Contains(selection.filter.fieldSelector, "metadata.namespace=") {
		ri = p.client.Dynamic.Resource(selection.def.Gvr).Namespace(selection.namespace)
	}
	return ri
}

func (p *resourcePoller) resourceListOptions(selection resourceSelection) metav1.ListOptions {
	opts := metav1.ListOptions{}
	if selection.filter.fieldSelector != "" {
		opts.FieldSelector = selection.filter.fieldSelector
	}
	if selection.filter.labelSelector != "" {
		opts.LabelSelector = selection.filter.labelSelector
	}
	return opts
}

func (p *resourcePoller) resourceRefreshTypes(ctx context.Context) {
	if p.client == nil {
		return
	}

	discoveryCtx, cancel := context.WithTimeout(ctx, pollInterval)
	defer cancel()

	types := p.client.CustomResourceTypes(discoveryCtx)
	builtinCtx, builtinCancel := context.WithTimeout(ctx, pollInterval)
	defer builtinCancel()
	builtinTypes, err := resourceBuiltinTypes(builtinCtx, p.client, types)
	if err != nil {
		log.Warn("k8s-app: failed to discover built-in resource types: %s", err)
	}

	p.mu.Lock()
	typesChanged := !reflect.DeepEqual(p.customTypes, types)
	p.customTypes = types
	if err == nil {
		typesChanged = typesChanged || !reflect.DeepEqual(p.builtinTypes, builtinTypes)
		p.builtinTypes = builtinTypes
	}
	p.mu.Unlock()
	p.resourceTypesAt.Store(time.Now().UnixNano())
	log.Info("k8s-app: discovered %d built-in and %d custom resource types", len(builtinTypes), len(types))

	if typesChanged {
		if onTypesChanged := p.onTypesChanged; onTypesChanged != nil {
			onTypesChanged()
		}
	}
}

func (p *resourcePoller) resourceTypeById(typeId string) (ResourceTypeDef, bool) {
	p.mu.Lock()
	defer p.mu.Unlock()
	for _, def := range p.builtinTypes {
		if def.Id == typeId {
			return def, true
		}
	}
	for _, def := range p.customTypes {
		if def.Id == typeId {
			return def, true
		}
	}
	return ResourceType(typeId)
}

func (p *resourcePoller) resourceTypesSnapshot() []ResourceTypeDef {
	p.mu.Lock()
	defer p.mu.Unlock()
	types := p.builtinTypes
	if types == nil {
		types = ResourceTypes()
	}
	result := append([]ResourceTypeDef(nil), types...)
	return append(result, p.customTypes...)
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
	case def.Id == containersTypeId:
		return rowsFromContainers(items)
	case strings.HasPrefix(def.Id, "crd:"):
		return rowsFromCustom(items, def)
	default:
		return rowsFromGeneric(items, def)
	}
}

func resourceRowActions(
	def ResourceTypeDef,
	row ResourceRow,
	obj *unstructured.Unstructured,
	nodeJobIds map[string]string,
	maintenanceSnapshot map[string]maintenance.Operation,
	maintenanceUnavailable bool,
) []ucx.TableRowAction {
	if len(row.Cells) == 0 {
		return nil
	}

	provisioningRow := strings.HasPrefix(row.Key, provisioningRowKeyPrefix)

	viewYaml := ucx.TableRowAction{Id: "viewYaml", Enabled: false}
	if !provisioningRow {
		viewYaml.Enabled = true
	}

	editYaml := ucx.TableRowAction{Id: "editYaml", Enabled: false}
	if !provisioningRow && (def.CanUpdate || def.Id == containersTypeId) {
		editYaml.Enabled = true
	}

	if def.Id == "pods" || def.Id == containersTypeId {
		if provisioningRow {
			return []ucx.TableRowAction{viewYaml}
		}
		actions := []ucx.TableRowAction{
			{Id: "openShell", Enabled: true},
			viewYaml,
			editYaml,
		}
		if dashboardResourceDeleteSupported(def) {
			actions = append(actions, ucx.TableRowAction{Id: "deleteResource", Enabled: true})
		}
		return actions
	}

	if rolloutRestartSupported(def.Id) {
		return []ucx.TableRowAction{
			{Id: "rolloutRestart", Enabled: true},
			viewYaml,
			editYaml,
			{Id: "deleteResource", Enabled: dashboardResourceDeleteSupported(def)},
		}
	}

	if def.Id != "nodes" {
		actions := []ucx.TableRowAction{
			viewYaml,
			editYaml,
			{Id: "deleteResource", Enabled: dashboardResourceDeleteSupported(def)},
		}
		if def.Id == "ingresses" && obj != nil {
			if serviceUrl := dashboardIngressServiceUrl(obj); serviceUrl != "" {
				actions = append(actions, ucx.TableRowAction{Id: "openService", Enabled: true, Text: serviceUrl})
			}
		}
		return actions
	}

	nodeName := row.Cells[0]

	jobId := nodeJobIds[nodeName]
	goToJob := ucx.TableRowAction{
		Id:             "goToJob",
		Enabled:        false,
		DisabledReason: "No UCloud job is associated with this node",
	}
	openShell := ucx.TableRowAction{
		Id:             "openShell",
		Enabled:        false,
		DisabledReason: "No UCloud job is associated with this node",
	}
	if jobId != "" {
		goToJob.Enabled = true
		goToJob.DisabledReason = ""
		openShell.Enabled = true
		openShell.DisabledReason = ""
	}

	operation, knownRecorded := maintenanceSnapshot[nodeName]
	phaseActive := knownRecorded && maintenance.PhaseActive(operation.Phase)
	nodeCordoned := len(row.Cells) > 2 && row.Cells[2] == "Cordoned"

	actions := []ucx.TableRowAction{
		{Id: "copyNodeName", Enabled: true, Text: row.Cells[0]},
		goToJob,
		openShell,
		viewYaml,
		editYaml,
	}

	if provisioningRow {
		if !maintenanceUnavailable {
			actions = append(actions, ucx.TableRowAction{
				Id:             "removeNode",
				Enabled:        true,
				DisabledReason: "Removes the node from the cluster",
			})
		}
		return actions
	}
	isUpgrade := knownRecorded && maintenance.KindIsUpgrade(operation.Kind)
	recoveryBlocked := isUpgrade && operation.ExecutorSubmitted && operation.RecoveryRequired
	upgradeNode := ucx.TableRowAction{Id: "upgradeNode", Enabled: true}
	if maintenanceUnavailable {
		upgradeNode.Enabled = false
		upgradeNode.DisabledReason = "The maintenance state is unavailable"
	} else if phaseActive && !isUpgrade {
		upgradeNode.Enabled = false
		upgradeNode.DisabledReason = "An operation is already active on this node"
	}
	actions = append(actions, upgradeNode)

	switch {
	case maintenanceUnavailable:
		if nodeCordoned {
			actions = append(actions, ucx.TableRowAction{
				Id:             "uncordon",
				Enabled:        false,
				DisabledReason: "The maintenance state is unavailable",
			})
		} else {
			actions = append(actions, ucx.TableRowAction{
				Id:             "cordonDrain",
				Enabled:        false,
				DisabledReason: "The maintenance state is unavailable",
			})
		}
	case phaseActive:
		if nodeCordoned {
			actions = append(actions, ucx.TableRowAction{
				Id:             "uncordon",
				Enabled:        false,
				DisabledReason: "An operation is already active on this node",
			})
		}
	default:
		if nodeCordoned {
			if recoveryBlocked {
				actions = append(actions, ucx.TableRowAction{
					Id:             "uncordon",
					Enabled:        true,
					Text:           "Uncordon (recovery)",
					DisabledReason: "Releases the traffic suspension and returns the node to service on its current release",
				})
			} else {
				actions = append(actions, uncordonEnabled)
			}
		} else {
			actions = append(actions, cordonDrainEnabled)
		}
		actions = append(actions, removeNodeEnabled)
	}

	return actions
}

var (
	cordonDrainEnabled = ucx.TableRowAction{Id: "cordonDrain", Enabled: true}
	uncordonEnabled    = ucx.TableRowAction{Id: "uncordon", Enabled: true}
	removeNodeEnabled  = ucx.TableRowAction{
		Id:             "removeNode",
		Enabled:        true,
		DisabledReason: "Removes the node from the cluster",
	}
)

func maintenanceCellForOperation(operation maintenance.Operation, present bool) string {
	if !present {
		return ""
	}
	if maintenance.PhaseActive(operation.Phase) {
		if maintenance.KindIsUpgrade(operation.Kind) {
			return "Upgrading"
		}
		if operation.Kind == maintenance.KindCordon {
			return "Cordoning"
		}
		if operation.Kind == maintenance.KindUncordon {
			return "Uncordoning"
		}
		if operation.Kind == maintenance.KindRemove {
			return "Removing"
		}
		return "Draining"
	}
	if operation.RecoveryRequired {
		return "Recovery required"
	}
	return operation.Phase
}

func (p *resourcePoller) nodeUidForRowName(nodeName string) (string, bool) {
	p.mu.Lock()
	defer p.mu.Unlock()

	for _, row := range p.lastRows {
		if strings.HasPrefix(row.Key, provisioningRowKeyPrefix) {
			continue
		}
		if len(row.Cells) > 0 && row.Cells[0] == nodeName {
			return row.Key, true
		}
	}
	return "", false
}

func (p *resourcePoller) nodeRowForKey(rowKey string) (ResourceRow, bool) {
	p.mu.Lock()
	defer p.mu.Unlock()

	row, ok := p.lastRows[rowKey]
	return row, ok
}

func (p *resourcePoller) nodeRowByName(nodeName string) (ResourceRow, bool) {
	rowKey, ok := p.nodeUidForRowName(nodeName)
	if !ok {
		return ResourceRow{}, false
	}
	return p.nodeRowForKey(rowKey)
}

func resourceTableUpsert(row ResourceRow) ucx.TableRow {
	return ucx.TableRow{
		Key:     row.Key,
		Group:   row.Group,
		Cells:   row.Cells,
		Actions: row.Actions,
		Busy:    row.Busy,
	}
}

func (p *resourcePoller) rowForKey(rowKey string) (ResourceRow, bool) {
	p.mu.Lock()
	defer p.mu.Unlock()

	row, ok := p.lastRows[rowKey]
	return row, ok
}

func (p *resourcePoller) objectForUid(uid string) (*unstructured.Unstructured, bool) {
	p.mu.Lock()
	defer p.mu.Unlock()

	obj, ok := p.cache[uid]
	return obj, ok
}

func (p *resourcePoller) nodeNameForRowKey(rowKey string) (string, bool) {
	row, ok := p.rowForKey(rowKey)
	if !ok || len(row.Cells) == 0 {
		return "", false
	}

	return row.Cells[0], true
}

func rowsEqual(a, b ResourceRow) bool {
	if a.Key != b.Key || a.Group != b.Group || a.Busy != b.Busy || len(a.Cells) != len(b.Cells) {
		return false
	}
	for i := range a.Cells {
		if a.Cells[i] != b.Cells[i] {
			return false
		}
	}
	if len(a.Actions) != len(b.Actions) {
		return false
	}
	for i := range a.Actions {
		if a.Actions[i] != b.Actions[i] {
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
		if a[i].Key != b[i].Key || a[i].Label != b[i].Label || a[i].SortType != b[i].SortType {
			return false
		}
	}
	return true
}
