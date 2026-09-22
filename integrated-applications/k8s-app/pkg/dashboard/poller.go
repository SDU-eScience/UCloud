package dashboard

import (
	"context"
	"sync"
	"sync/atomic"
	"time"

	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

const pollInterval = 5 * time.Second

type resourcePoller struct {
	mu              sync.Mutex
	client          *K8sClient
	session         *ucx.Session
	cancel          context.CancelFunc
	activeType      string
	activeNamespace string
	pollNow         chan util.Empty
	lastRows        map[string]ResourceRow
	lastColumns     []ucx.TableColumn
	lastRev         int64
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
		p.lastRows = map[string]ResourceRow{}
		p.lastColumns = nil
		p.lastRev = 0
	}
	p.mu.Unlock()

	if changed {
		select {
		case p.pollNow <- util.Empty{}:
		default:
		}
	}
}

func (p *resourcePoller) SetActiveNamespace(namespace string) {
	p.mu.Lock()
	changed := p.activeNamespace != namespace
	if changed {
		p.activeNamespace = namespace
		p.lastRows = map[string]ResourceRow{}
		p.lastRev = 0
	}
	p.mu.Unlock()

	if changed {
		select {
		case p.pollNow <- util.Empty{}:
		default:
		}
	}
}

func (p *resourcePoller) run(ctx context.Context) {
	p.refreshCustomTypes(ctx)
	p.pollTable(ctx)

	ticker := time.NewTicker(pollInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-p.pollNow:
			p.pollTable(ctx)
		case <-ticker.C:
			if time.Since(time.Unix(0, p.customAt.Load())) > time.Minute {
				p.refreshCustomTypes(ctx)
			}
			p.pollTable(ctx)
		}
	}
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

func (p *resourcePoller) pollTable(ctx context.Context) {
	session := p.session
	if session == nil || p.client == nil {
		log.Warn("k8s-app: poll skipped, session or client missing")
		return
	}

	p.mu.Lock()
	typeId := p.activeType
	activeNamespace := p.activeNamespace
	lastRows := p.lastRows
	lastColumns := p.lastColumns
	nextRev := p.lastRev + 1
	p.mu.Unlock()

	def, ok := ResourceType(typeId)
	if !ok {
		def = p.customTypeById(typeId)
		if def.Id == "" {
			log.Warn("k8s-app: unknown resource type %q", typeId)
			return
		}
	}

	rows, err := p.client.ListRowsInNamespace(ctx, def, activeNamespace)
	if err != nil {
		log.Warn("k8s-app: failed to list %s: %s", def.Id, err)
		return
	}

	log.Info("k8s-app: listed %d rows for %s (rev %d, snapshot=%v)", len(rows), def.Id, nextRev, len(lastRows) == 0)

	rowByKey := make(map[string]ResourceRow, len(rows))
	for _, row := range rows {
		rowByKey[row.Key] = row
	}

	columnsChanged := !columnsEqual(lastColumns, def.Columns)
	isInitial := len(lastRows) == 0

	var upserts []ucx.TableRow
	var removed []string
	if columnsChanged || isInitial {
		upserts = make([]ucx.TableRow, 0, len(rows))
		for _, row := range rows {
			upserts = append(upserts, ucx.TableRow{Key: row.Key, Group: row.Group, Cells: row.Cells})
		}
	} else {
		for key, row := range rowByKey {
			if prev, ok := lastRows[key]; !ok || !rowsEqual(prev, row) {
				upserts = append(upserts, ucx.TableRow{Key: row.Key, Group: row.Group, Cells: row.Cells})
			}
		}
		for key := range lastRows {
			if _, ok := rowByKey[key]; !ok {
				removed = append(removed, key)
			}
		}
	}

	p.mu.Lock()
	p.lastRows = rowByKey
	p.lastColumns = def.Columns
	p.lastRev = nextRev
	p.mu.Unlock()

	session.SendTableUpdate(ucx.TableUpdate{
		TableId:  typeId,
		Revision: nextRev,
		Snapshot: columnsChanged || isInitial,
		Columns:  def.Columns,
		Upserts:  upserts,
		Removed:  removed,
	})
	log.Info("k8s-app: sent table update for %s (upserts=%d removed=%d)", typeId, len(upserts), len(removed))
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
