package dashboard

import (
	"fmt"
	"net/url"
	"sort"
	"strings"

	"ucloud.dk/shared/pkg/ucx"

	"ucloud.dk/iapp/k8s/pkg/maintenance"
	"ucloud.dk/iapp/k8s/pkg/shared"
)

func (app *stackUiApp) pageProvisioning() []ucx.UiNode {
	messages := []ucx.UiNode{}

	if record, ok := app.readClusterRecord(); ok && record.FailureReason != "" {
		messages = append(messages, ucx.Text("The cluster reported a problem: "+record.FailureReason))
		for _, pending := range record.PendingCleanup {
			parts := []string{pending.Hostname}
			if pending.JobId != "" {
				parts = append(parts, "job "+pending.JobId)
			}
			if pending.ReservationId != "" {
				parts = append(parts, "reservation "+pending.ReservationId)
			}
			messages = append(messages, ucx.Text(
				"These resources need manual cleanup: "+strings.Join(parts, ", "),
			))
		}
	}

	message := ""
	if record, ok := app.readClusterRecord(); ok {
		ctx, cancel := app.dashboardContext()
		message = app.clusterNodeMessage(ctx, &record)
		cancel()
	}

	if message == "" {
		message = "The cluster control plane is starting. The dashboard loads when the Kubernetes API is ready."
	}

	messages = append(messages, ucx.Text(message))

	maintenanceLinks := app.pageProvisioningMaintenanceLinks()
	if len(maintenanceLinks) > 0 {
		messages = append(messages, ucx.Text("Node maintenance (including recovery of finished operations):"))
		messages = append(messages, maintenanceLinks...)
	}

	children := []ucx.UiNode{ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).Children(messages...)}

	if record, ok := app.readClusterRecord(); ok {
		for _, node := range record.Nodes {
			if node.JobId == "" {
				continue
			}

			children = append(children,
				ucx.H3Ex("initLogsHeading-"+node.JobId, node.Hostname),
				ucx.JobLogs("initLogs-"+node.JobId, node.JobId),
			)
		}
	}

	return children
}

func (app *stackUiApp) pageProvisioningMaintenanceLinks() []ucx.UiNode {
	record, recordOk := app.readClusterRecord()
	if !recordOk {
		return nil
	}

	known := maintenanceKnownNodeNames(record)

	snapshot, err := maintenance.Snapshot()
	if err != nil {
		return nil
	}

	names := make([]string, 0, len(snapshot))
	for nodeName := range snapshot {
		if known[nodeName] {
			names = append(names, nodeName)
		}
	}
	sort.Strings(names)

	links := make([]ucx.UiNode, 0, len(names))
	for _, nodeName := range names {
		links = append(links, ucx.Link(fmt.Sprintf("maintenance/%s", url.PathEscape(nodeName))).Children(ucx.Text(
			fmt.Sprintf("%s (%s)", nodeName, snapshot[nodeName].Phase),
		)))
	}
	return links
}

func maintenanceKnownNodeNames(record shared.ClusterRecord) map[string]bool {
	known := make(map[string]bool, len(record.Nodes))
	for _, node := range record.Nodes {
		if node.Hostname != "" {
			known[node.Hostname] = true
		}
	}
	return known
}
