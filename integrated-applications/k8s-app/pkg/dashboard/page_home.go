package dashboard

import (
	"fmt"
	"time"

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
)

func (app *stackUiApp) pageHomeContent() []ucx.UiNode {
	record, recordOk := app.readClusterRecord()

	clusterState := "Unknown"
	clusterStateColor := ucx.ColorTextSecondary
	if recordOk {
		clusterState = record.Phase
		clusterStateColor = ucx.ColorSuccessMain
		if record.Phase != "created" {
			clusterStateColor = ucx.ColorWarningMain
		}
		if record.FailureReason != "" {
			clusterState = record.Phase + ": " + record.FailureReason
			clusterStateColor = ucx.ColorErrorMain
		}
	}

	if health := app.clusterHealth; recordOk && record.Phase == "created" && health.Total > 0 {
		if health.NotReady == 0 && health.Unknown == 0 {
			clusterState = "Healthy"
		} else {
			clusterState = "Unhealthy"
		}
		clusterStateColor = ucx.ColorSuccessMain
		if clusterState == "Unhealthy" {
			clusterStateColor = ucx.ColorErrorMain
		}
	}

	nodesTotal := len(record.Nodes)
	nodesReady := nodesTotal - len(app.provisioningSnapshot())
	if nodesReady < 0 {
		nodesReady = 0
	}
	if health := app.clusterHealth; health.Total > 0 {
		nodesTotal = health.Total
		nodesReady = health.Ready
	}
	nodesLabel := fmt.Sprintf("%d / %d", nodesReady, nodesTotal)

	k8sVersion := "-"
	k8sVersionColor := ucx.ColorTextPrimary
	if app.clusterHealth.ControlPlaneVersion != "" {
		k8sVersion = app.clusterHealth.ControlPlaneVersion
	} else if recordOk && record.K8sVersion != "" {
		k8sVersion = record.K8sVersion
	}
	if k8sVersion != "-" {
		if eolAt, ok := shared.SupportEnds(k8sVersion); ok && !eolAt.After(time.Now().UTC()) {
			k8sVersionColor = ucx.ColorErrorMain
			k8sVersion += " (out of support)"
		}
	}

	manageNodes := ucx.LinkButton("homeManageNodes", "(Manage)", ucx.ColorLinkColor).Sx(
		ucx.SxFontSize(12),
		ucx.SxFontWeight("400"),
	).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
		app.selectResourceType("nodes")
	})

	children := []ucx.UiNode{}

	stackInfo := app.stackInfo
	if stackInfo != nil {
		createdAtLabel := "-"
		if stackInfo.CreatedAt > 0 {
			createdAtLabel = time.UnixMilli(stackInfo.CreatedAt).UTC().Format("Jan 2, 2006 15:04 UTC")
		}

		stackIdMetric := app.homeMetric("Cluster name",
			homeMetricText(stackInfo.Id, ucx.ColorTextPrimary),
			ucx.Box().Sx(ucx.SxMl(-4)).Children(
				ucx.CopyButtonEx("homeCopyStackId", stackInfo.Id).WithTooltip("Copy cluster name"),
			),
		)

		providerMetric := app.homeMetric("Provider", homeMetricText("-", ucx.ColorTextPrimary))
		if stackInfo.Provider != "" {
			providerMetric = app.homeMetric("Provider",
				ucx.ProviderTitleEx("homeProviderTitle", stackInfo.Provider, true).Sx(
					ucx.SxFontWeight("600"),
					ucx.SxFontSize(16),
				),
			)
		}

		children = append(children,
			app.homeMetricsRow(
				stackIdMetric,
				providerMetric,
				app.homeMetric("Created", homeMetricText(createdAtLabel, ucx.ColorTextPrimary)),
			),
		)
	}

	children = append(children,
		app.homeMetricsRow(
			app.homeMetric("Cluster state", homeMetricText(clusterState, clusterStateColor)),
			app.homeMetricEx("Nodes ready", []ucx.UiNode{manageNodes}, homeMetricText(nodesLabel, ucx.ColorTextPrimary)),
			app.homeMetricEx("Kubernetes version", []ucx.UiNode{
				ucx.LinkButton("homeRollingUpgrade", "(Rolling upgrade)", ucx.ColorLinkColor).
					Sx(ucx.SxFontSize(12), ucx.SxFontWeight("400")).
					On(ucx.UiEventClick, func(ev ucx.UiEvent) {
						ucxsvc.RouterPushPage(app, "rolling-upgrade")
					}),
			}, homeMetricText(k8sVersion, k8sVersionColor)),
		),
	)

	resourcesMetric := []ucx.UiNode{}
	if stackInfo != nil {
		resourcesMetric = app.homeMetric("Resources",
			ucx.LinkButton("homeShowResources", fmt.Sprintf("%d", stackInfo.ResourceCount), ucx.ColorLinkColor).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				ucxsvc.StackShowResources(app)
			}),
		)
	}
	children = append(children, app.homeMetricsRow(
		resourcesMetric,
		app.homeBackupsMetric(),
		app.homeAutoMaintenanceMetric(),
	))

	return children
}

func (app *stackUiApp) pageHomeActions() []ucx.UiNode {
	connectControls := []ucx.UiNode{
		ucx.ButtonEx("homeDownloadKubernetesConfig", "Kubeconfig", ucx.ColorPrimaryMain, ucx.IconHeroArrowDownTray, "", "").On(ucx.UiEventClick, func(ev ucx.UiEvent) {
			ucxsvc.StackDownloadFile(app.Stack, shared.KubernetesConfigurationFileName)
		}),
	}

	actions := []ucx.UiNode{
		ucx.SettingsAction("homeConnectAction",
			"Connect to this cluster",
			"Use the cluster from your terminal with kubectl.",
		).Children(
			ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 4}).
				Sx(ucx.SxAlignItemsCenter).
				Children(connectControls...),
		),
		ucx.AccordionNode("Use kubectl from your terminal", false).WithNoHeaderBorder().Sx(ucx.SxMt(8), ucx.SxMb(12)).Children(
			ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 8}).Children(
				ucx.Markdown("kubectl is the standard Kubernetes command line tool. If you do not have it, see [how to install kubectl](https://kubernetes.io/docs/tasks/tools/)."),
				ucx.TextEx("", "1. Download the configuration file with the Kubeconfig button above.").Sx(
					ucx.SxColor(ucx.ColorTextSecondary),
				),
				ucx.TextEx("", "2. Move the file to where kubectl looks for it by default (adjust the source path to where your browser saved it):").Sx(
					ucx.SxColor(ucx.ColorTextSecondary),
				),
				ucx.Code("mkdir -p ~/.kube && mv ~/Downloads/kubeconfig ~/.kube/config"),
				ucx.TextEx("", "3. Check that it works by listing the nodes of the cluster:").Sx(
					ucx.SxColor(ucx.ColorTextSecondary),
				),
				ucx.Code("kubectl get nodes"),
			),
			ucx.Box().Sx(ucx.SxMt(16)).Children(
				ucx.TextEx("", "Some commands you will likely need:").Sx(
					ucx.SxColor(ucx.ColorTextSecondary),
					ucx.SxFontWeight("600"),
					ucx.SxMb(8),
				),
				ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 12}).Children(
					kubectlExample("List all pods across every namespace", "kubectl get pods -A"),
					kubectlExample("List all services across every namespace", "kubectl get services -A"),
					kubectlExample("Show the details of a pod, for example when it will not start", "kubectl describe pod <name>"),
					kubectlExample("Read the logs of a pod", "kubectl logs <pod>"),
					kubectlExample("Create or update resources from a YAML file", "kubectl apply -f <file>"),
				),
			),
			ucx.TextEx("", "If kubectl reports connection errors, verify that the file lives at ~/.kube/config.").Sx(
				ucx.SxColor(ucx.ColorTextSecondary),
			),
		),
	}

	if app.Stack != nil && app.Stack.Ok {
		actions = append(actions, ucx.SettingsAction("homePermissionsAction",
			"Permissions",
			"Share cluster admin rights for this Kubernetes cluster with others in your project.",
		).Children(ucx.StackPermissionsEx("homePermissions", app.Stack.InstanceId, ucx.StackPermissionsProps{
			AccessDescription: "Cluster admin grants full Kubernetes cluster-admin access, including cluster resources, credentials, node shells, and logs. The cluster creator and project administrators keep access.",
			PermissionLabel:   "Cluster admin",
			PermissionIcon:    ucx.IconHeroShieldCheck,
			ButtonIcon:        ucx.IconHeroShare,
		})))
		actions = append(actions, ucx.SettingsAction("homeDeleteAction",
			"Delete cluster",
			"Permanently deletes the cluster and its nodes.",
		).Children(
			ucx.ButtonEx("homeDeleteCluster", "Delete cluster", ucx.ColorErrorMain, ucx.IconTrash, "", "").On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				ucxsvc.StackDelete(app)
			}),
		))
	}

	return actions
}

func kubectlExample(title string, command string) ucx.UiNode {
	return ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 4}).Children(
		ucx.TextEx("", title).Sx(
			ucx.SxColor(ucx.ColorTextSecondary),
		),
		ucx.Code(command),
	)
}

func (app *stackUiApp) homeMetric(title string, valueNodes ...ucx.UiNode) []ucx.UiNode {
	return app.homeMetricEx(title, nil, valueNodes...)
}

func (app *stackUiApp) homeMetricEx(title string, titleNodes []ucx.UiNode, valueNodes ...ucx.UiNode) []ucx.UiNode {
	headerChildren := []ucx.UiNode{
		ucx.TextEx("", title).Sx(
			ucx.SxColor(ucx.ColorTextSecondary),
			ucx.SxFontSize(12),
		),
	}
	headerChildren = append(headerChildren, titleNodes...)

	return []ucx.UiNode{
		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 6}).
			Sx(ucx.SxAlignItemsCenter).
			Children(headerChildren...),
		ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 4}).
			Sx(ucx.SxAlignItemsCenter).
			Children(valueNodes...),
	}
}

func homeMetricText(value string, color ucx.Color) ucx.UiNode {
	return ucx.TextEx("", value).Sx(
		ucx.SxFontWeight("600"),
		ucx.SxColor(color),
		ucx.SxFontSize(16),
	)
}

func (app *stackUiApp) homeMetricsRow(metrics ...[]ucx.UiNode) ucx.UiNode {
	cells := make([]ucx.UiNode, 0, len(metrics))
	for _, metric := range metrics {
		cells = append(cells, ucx.Box().Sx(
			ucx.SxFlexBasis("200px"),
			ucx.SxFlexGrow(1),
		).Children(metric...))
	}

	return ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 24}).
		Sx(ucx.SxFlexWrapWrap).
		Children(cells...)
}
