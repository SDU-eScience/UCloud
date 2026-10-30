package dashboard

import (
	"context"
	"fmt"
	"time"

	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxapi"
	"ucloud.dk/shared/pkg/util"

	"ucloud.dk/iapp/k8s/pkg/shared"
)

type provisioningEntry struct {
	Hostname string
	Group    string
	Status   string
	JobId    string
}

type provisioningHealth struct {
	Known  bool
	States map[string]string
}

func provisioningEntries(record *shared.ClusterRecord, known map[string]string) []provisioningEntry {
	entries := make([]provisioningEntry, 0, len(record.Nodes))
	for _, node := range record.Nodes {
		if node.JobId == "" || node.Hostname == "" {
			continue
		}

		status := known[node.Hostname]
		if status == "" {
			status = k8sapi.ProvisioningStatusProvisioning
		}
		entries = append(entries, provisioningEntry{
			Hostname: node.Hostname,
			Group:    node.Group,
			Status:   status,
			JobId:    node.JobId,
		})
	}
	return entries
}

func provisioningResolve(
	ctx context.Context,
	session *ucx.Session,
	client *K8sClient,
	entries []provisioningEntry,
) provisioningHealth {
	if client == nil {
		return provisioningHealth{}
	}

	healthCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	health, err := client.NodeHealth(healthCtx)
	cancel()
	if err != nil {
		return provisioningHealth{}
	}

	result := provisioningHealth{
		Known:  true,
		States: health.States,
	}

	for i := range entries {
		if _, ok := result.States[entries[i].Hostname]; ok {
			entries[i].Status = k8sapi.ProvisioningStatusReady
			continue
		}
		if entries[i].JobId == "" {
			continue
		}

		jobCtx, jobCancel := context.WithTimeout(ctx, dashboardClusterStateTimeout)
		job, jobErr := provisioningJobRetrieve(jobCtx, session, entries[i].JobId)
		jobCancel()
		if jobErr != nil {
			continue
		}

		if status := k8sapi.ProvisioningStatusFromJobState(job.Status.State); status != "" {
			entries[i].Status = status
		}
	}

	return result
}

func (app *stackUiApp) provisioningSnapshot() []provisioningEntry {
	return app.provisioningCache
}

func (app *stackUiApp) provisioningClient() *K8sClient {
	app.mu.Lock()
	client := app.k8sClient
	app.mu.Unlock()
	if client != nil {
		return client
	}

	client, err := K8sClientFromKubeconfig(LocalKubeconfigPath())
	if err != nil {
		return nil
	}

	app.mu.Lock()
	if app.k8sClient == nil {
		app.k8sClient = client
	}
	client = app.k8sClient
	app.mu.Unlock()
	return client
}

func (app *stackUiApp) activeTypeIsHome() bool {
	app.mu.Lock()
	defer app.mu.Unlock()
	return app.ActiveType == navHomeId
}

func (app *stackUiApp) provisioningWatcher(session *ucx.Session) {
	ticker := time.NewTicker(5 * time.Second)
	uiTicker := time.NewTicker(time.Second)
	defer ticker.Stop()
	defer uiTicker.Stop()

	for {
		app.refreshProvisioningRows(session)
		if app.activeTypeIsHome() {
			go app.refreshStackInfo()
		}
		if app.activeTypeIsHome() || app.onMaintenanceRoute() {
			go app.refreshClusterHealth()
		}
		if app.onMaintenanceRoute() {
			app.mu.Lock()
			ucx.AppUpdateUi(app)
			app.mu.Unlock()
		}

		refreshed := false
		for !refreshed {
			select {
			case <-session.Context().Done():
				return
			case <-app.provisioningKick:
				refreshed = true
			case <-ticker.C:
				refreshed = true
			case <-uiTicker.C:
				app.maintenanceUiTick()
			}
		}
	}
}

func (app *stackUiApp) refreshProvisioningRows(session *ucx.Session) {
	app.mu.Lock()
	stack := app.Stack
	client := app.k8sClient
	poller := app.poller
	known := make(map[string]string, len(app.provisioningCache))
	for _, entry := range app.provisioningCache {
		known[entry.Hostname] = entry.Status
	}
	app.mu.Unlock()

	record, recordOk := app.readClusterRecordWith(session, stack)
	if !recordOk {
		return
	}

	entries := provisioningEntries(&record, known)

	published := append([]provisioningEntry(nil), entries...)

	app.mu.Lock()
	changed := !provisioningEntriesEqual(app.provisioningCache, published)
	app.provisioningCache = published
	if changed && app.ActiveType == navHomeId {
		ucx.AppUpdateUi(app)
	}
	app.mu.Unlock()

	if changed && poller != nil {
		poller.signalPollNow()
	}

	if client == nil {
		return
	}

	provisioningResolve(session.Context(), session, client, entries)

	survivors := make([]provisioningEntry, 0, len(entries))
	for _, entry := range entries {
		if entry.Status == k8sapi.ProvisioningStatusReady {
			continue
		}
		survivors = append(survivors, entry)
	}

	app.mu.Lock()
	changed = !provisioningEntriesEqual(app.provisioningCache, survivors)
	app.provisioningCache = survivors
	if changed && app.ActiveType == navHomeId {
		ucx.AppUpdateUi(app)
	}
	app.mu.Unlock()

	if changed && poller != nil {
		poller.signalPollNow()
	}
}

func (app *stackUiApp) provisioningKickNow() {
	if app.provisioningKick == nil {
		return
	}
	select {
	case app.provisioningKick <- util.Empty{}:
	default:
	}
}

func provisioningJobRetrieve(ctx context.Context, session *ucx.Session, jobId string) (orcapi.Job, error) {
	return ucxapi.JobsRetrieve.InvokeEx(ctx, session, orcapi.JobsRetrieveRequest{Id: jobId})
}

func (app *stackUiApp) clusterNodeMessage(ctx context.Context, record *shared.ClusterRecord) string {
	session := app.session

	stopped := 0
	failedLookup := 0
	jobCount := 0
	for _, node := range record.Nodes {
		if node.JobId == "" {
			continue
		}
		jobCount++

		job, err := provisioningJobRetrieve(ctx, session, node.JobId)
		if err != nil {
			failedLookup++
			continue
		}

		if job.Status.State.IsFinal() {
			stopped++
		}
	}

	message := ""
	if stopped > 0 {
		message = fmt.Sprintf("Stopped node jobs: %d of %d. See the node logs for details.", stopped, jobCount)
	}
	if failedLookup > 0 {
		if message != "" {
			message += " "
		}
		message += fmt.Sprintf(
			"Could not check %d of %d node jobs. See the node logs for details.",
			failedLookup,
			jobCount,
		)
	}
	return message
}

func provisioningEntriesEqual(a, b []provisioningEntry) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}

func (app *stackUiApp) provisioningResourceRows(def ResourceTypeDef) []ResourceRow {
	if def.Id != "nodes" {
		return nil
	}

	app.mu.Lock()
	entries := append([]provisioningEntry(nil), app.provisioningCache...)
	app.mu.Unlock()

	rows := make([]ResourceRow, 0, len(entries))
	for _, entry := range entries {
		role := ""
		if entry.Group == shared.GroupControlPlane {
			role = "control-plane"
		}
		rows = append(rows, ResourceRow{
			Key:   "provisioning:" + entry.Hostname,
			Group: entry.Group,
			Cells: []string{entry.Hostname, entry.Status, "Schedulable", "", role, "", "", ""},
		})
	}
	return rows
}

func (app *stackUiApp) provisioningJobId(hostname string) string {
	for _, entry := range app.provisioningCache {
		if entry.Hostname == hostname {
			return entry.JobId
		}
	}
	return ""
}

func (app *stackUiApp) clusterReadyForNodes() bool {
	record, ok := app.readClusterRecord()
	return ok && record.Phase == shared.ClusterRecordPhaseCreated && len(record.PendingCleanup) == 0
}
