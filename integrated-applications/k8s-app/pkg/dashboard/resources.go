package dashboard

import (
	"context"
	"fmt"
	"net/url"
	"sort"
	"strings"
	"sync"
	"time"

	"k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/runtime/schema"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/apimachinery/pkg/util/version"
	"k8s.io/client-go/dynamic"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/kubernetes/scheme"
	"k8s.io/client-go/tools/clientcmd"

	"ucloud.dk/iapp/k8s/pkg/maintenance"
	"ucloud.dk/iapp/k8s/pkg/shared"
	k8sapi "ucloud.dk/shared/pkg/iapps/k8s"
	"ucloud.dk/shared/pkg/ucx"
)

// Resource types
// =====================================================================================================================
// A resource type describes one kind of Kubernetes object. The dashboard uses the type to list the objects, to build
// the table columns and to decide which actions a row supports.

type ResourceTypeDef struct {
	Id         string
	Label      string
	Aliases    []string
	Group      string
	Gvr        schema.GroupVersionResource
	Columns    []ucx.TableColumn
	HasYaml    bool
	CanUpdate  bool
	CanCreate  bool
	Namespaced bool
}

type ResourceRow struct {
	Key     string
	Group   string
	Cells   []string
	Actions []ucx.TableRowAction
	Busy    bool
}

const nodeGroupLabel = "ucloud.dk/k8s-node-group"

var resourceTypes = []ResourceTypeDef{
	{
		Id: "nodes", Label: "Nodes", Aliases: []string{"node", "no"}, Group: "Cluster",
		Gvr: schema.GroupVersionResource{Group: "", Version: "v1", Resource: "nodes"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "status", Label: "Status"},
			{Key: "scheduling", Label: "Scheduling"},
			{Key: "maintenance", Label: "Maintenance"},
			{Key: "role", Label: "Roles"},
			{Key: "version", Label: "Version"},
			{Key: "os", Label: "OS updates"},
			{Key: "ip", Label: "IP", SortType: ucx.TableColumnSortIp},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
	},
	{
		Id: "pods", Label: "Pods", Aliases: []string{"pod", "po"}, Group: "Workloads", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "", Version: "v1", Resource: "pods"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "ready", Label: "Ready", SortType: ucx.TableColumnSortRatio},
			{Key: "status", Label: "Status"},
			{Key: "restarts", Label: "Restarts", SortType: ucx.TableColumnSortNumber},
			{Key: "node", Label: "Node"},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
	},
	{
		Id: "deployments", Label: "Deployments", Aliases: []string{"deployment", "deploy"}, Group: "Workloads", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "apps", Version: "v1", Resource: "deployments"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "ready", Label: "Ready", SortType: ucx.TableColumnSortRatio},
			{Key: "upToDate", Label: "Up-to-date", SortType: ucx.TableColumnSortNumber},
			{Key: "available", Label: "Available", SortType: ucx.TableColumnSortNumber},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
	},
	{
		Id: "statefulsets", Label: "StatefulSets", Aliases: []string{"statefulset", "sts"}, Group: "Workloads", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "apps", Version: "v1", Resource: "statefulsets"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "ready", Label: "Ready", SortType: ucx.TableColumnSortRatio},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
	},
	{
		Id: "daemonsets", Label: "DaemonSets", Aliases: []string{"daemonset", "ds"}, Group: "Workloads", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "apps", Version: "v1", Resource: "daemonsets"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "desired", Label: "Desired", SortType: ucx.TableColumnSortNumber},
			{Key: "ready", Label: "Ready", SortType: ucx.TableColumnSortNumber},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
	},
	{
		Id: "jobs", Label: "Jobs", Aliases: []string{"job"}, Group: "Workloads", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "batch", Version: "v1", Resource: "jobs"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "completions", Label: "Completions", SortType: ucx.TableColumnSortRatio},
			{Key: "duration", Label: "Duration", SortType: ucx.TableColumnSortDuration},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
	},
	{
		Id: "cronjobs", Label: "CronJobs", Aliases: []string{"cronjob", "cj"}, Group: "Workloads", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "batch", Version: "v1", Resource: "cronjobs"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "schedule", Label: "Schedule"},
			{Key: "suspend", Label: "Suspend", SortType: ucx.TableColumnSortBool},
			{Key: "active", Label: "Active", SortType: ucx.TableColumnSortNumber},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
	},
	{
		Id: "services", Label: "Services", Aliases: []string{"service", "svc"}, Group: "Networking", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "", Version: "v1", Resource: "services"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "type", Label: "Type"},
			{Key: "clusterIp", Label: "Cluster IP", SortType: ucx.TableColumnSortIp},
			{Key: "ports", Label: "Ports"},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
	},
	{
		Id: "ingresses", Label: "Ingresses", Aliases: []string{"ingress", "ing"}, Group: "Networking", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "networking.k8s.io", Version: "v1", Resource: "ingresses"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "class", Label: "Class"},
			{Key: "hosts", Label: "Hosts"},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
	},
	{
		Id: "configmaps", Label: "ConfigMaps", Aliases: []string{"configmap", "cm"}, Group: "Config", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "", Version: "v1", Resource: "configmaps"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "data", Label: "Data", SortType: ucx.TableColumnSortNumber},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
		HasYaml: false,
	},
	{
		Id: "secrets", Label: "Secrets", Aliases: []string{"secret"}, Group: "Config", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "", Version: "v1", Resource: "secrets"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "type", Label: "Type"},
			{Key: "data", Label: "Data", SortType: ucx.TableColumnSortNumber},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
		HasYaml: false,
	},
	{
		Id: "persistentvolumes", Label: "PVs", Aliases: []string{"persistentvolume", "pv"}, Group: "Storage",
		Gvr: schema.GroupVersionResource{Group: "", Version: "v1", Resource: "persistentvolumes"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "capacity", Label: "Capacity", SortType: ucx.TableColumnSortCapacity},
			{Key: "phase", Label: "Phase"},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
	},
	{
		Id: "persistentvolumeclaims", Label: "PVCs", Aliases: []string{"persistentvolumeclaim", "pvc"}, Group: "Storage", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "", Version: "v1", Resource: "persistentvolumeclaims"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "status", Label: "Status"},
			{Key: "volume", Label: "Volume"},
			{Key: "capacity", Label: "Capacity", SortType: ucx.TableColumnSortCapacity},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
	},
	{
		Id: "events", Label: "Events", Aliases: []string{"event", "ev"}, Group: "Cluster", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "", Version: "v1", Resource: "events"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "reason", Label: "Reason"},
			{Key: "object", Label: "Object"},
			{Key: "message", Label: "Message"},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
	},
}

const containersTypeId = "containers"

var containersTypeDef = ResourceTypeDef{
	Id:         containersTypeId,
	Label:      "Containers",
	Group:      "Workloads",
	Gvr:        schema.GroupVersionResource{Group: "", Version: "v1", Resource: "pods"},
	Namespaced: true,
	Columns: []ucx.TableColumn{
		{Key: "name", Label: "Name", Copy: true},
		{Key: "image", Label: "Image"},
		{Key: "ready", Label: "Ready"},
		{Key: "restarts", Label: "Restarts", SortType: ucx.TableColumnSortNumber},
		{Key: "state", Label: "State"},
	},
}

func ResourceTypes() []ResourceTypeDef {
	result := make([]ResourceTypeDef, 0, len(resourceTypes)+1)
	for _, def := range resourceTypes {
		def.CanUpdate = true
		def.CanCreate = def.Id != "nodes"
		result = append(result, def)
	}
	return result
}

func ResourceType(id string) (ResourceTypeDef, bool) {
	if id == containersTypeId {
		return containersTypeDef, true
	}
	for _, def := range resourceTypes {
		if def.Id == id {
			def.CanUpdate = true
			def.CanCreate = def.Id != "nodes"
			return def, true
		}
	}
	return ResourceTypeDef{}, false
}

func (app *stackUiApp) allTypeDefs() []ResourceTypeDef {
	if app.poller != nil {
		return app.poller.resourceTypesSnapshot()
	}
	return ResourceTypes()
}

func (app *stackUiApp) resolveType(typeId string) (ResourceTypeDef, bool) {
	if app.poller != nil {
		return app.poller.resourceTypeById(typeId)
	}
	return ResourceType(typeId)
}

func (app *stackUiApp) activeTypeLabel(typeDefs []ResourceTypeDef) string {
	if def, ok := app.resolveType(app.ActiveType); ok && def.Id == app.ActiveType {
		return def.Label
	}
	for _, def := range typeDefs {
		if def.Id == app.ActiveType {
			return def.Label
		}
	}
	return ""
}

func (app *stackUiApp) resourceStreamId() string {
	def, _ := app.resolveType(app.ActiveType)
	return resourceDatasetId(resourceSelection{
		typeId:    app.ActiveType,
		namespace: app.ActiveNamespace,
		filter:    app.pollerFilter(),
		def:       def,
	})
}

func (app *stackUiApp) pollerFilter() resourceFilter {
	return app.ActiveFilter
}

// Kubernetes client
// =====================================================================================================================
// One client serves the whole session. It wraps the dynamic client for arbitrary objects and the typed client for
// discovery. The schema cache holds the OpenAPI documents between edits.

type K8sClient struct {
	Dynamic     dynamic.Interface
	Typed       kubernetes.Interface
	schemaMutex sync.Mutex
	schemaCache map[string]dashboardResourceSchemaCache
}

func K8sClientFromKubeconfig(path string) (*K8sClient, error) {
	config, err := clientcmd.BuildConfigFromFlags("", path)
	if err != nil {
		return nil, err
	}

	config.QPS = 20
	config.Burst = 40

	dyn, err := dynamic.NewForConfig(config)
	if err != nil {
		return nil, err
	}

	typed, err := kubernetes.NewForConfig(config)
	if err != nil {
		return nil, err
	}

	return &K8sClient{Dynamic: dyn, Typed: typed}, nil
}

var listOptions = v1.ListOptions{}

func (c *K8sClient) List(ctx context.Context, def ResourceTypeDef) ([]unstructured.Unstructured, error) {
	list, err := c.Dynamic.Resource(def.Gvr).List(ctx, listOptions)
	if err != nil {
		return nil, err
	}
	return list.Items, nil
}

func (c *K8sClient) ListWithLabelSelector(ctx context.Context, def ResourceTypeDef, labelSelector string) ([]unstructured.Unstructured, error) {
	list, err := c.Dynamic.Resource(def.Gvr).List(ctx, v1.ListOptions{LabelSelector: labelSelector})
	if err != nil {
		return nil, err
	}
	return list.Items, nil
}

func (c *K8sClient) ListNamespaces(ctx context.Context) ([]string, error) {
	list, err := c.Dynamic.Resource(schema.GroupVersionResource{Group: "", Version: "v1", Resource: "namespaces"}).List(ctx, listOptions)
	if err != nil {
		return nil, err
	}

	result := make([]string, 0, len(list.Items))
	for i := range list.Items {
		result = append(result, list.Items[i].GetName())
	}
	sort.Strings(result)
	return result, nil
}

func (c *K8sClient) NodeUid(ctx context.Context, nodeName string) (string, error) {
	node, err := c.Typed.CoreV1().Nodes().Get(ctx, nodeName, v1.GetOptions{})
	if err != nil {
		return "", err
	}
	return string(node.UID), nil
}

func (c *K8sClient) NodeNames(ctx context.Context) (map[string]bool, error) {
	list, err := c.Dynamic.Resource(schema.GroupVersionResource{Group: "", Version: "v1", Resource: "nodes"}).List(ctx, listOptions)
	if err != nil {
		return nil, err
	}

	result := make(map[string]bool, len(list.Items))
	for i := range list.Items {
		result[list.Items[i].GetName()] = true
	}
	return result, nil
}

type NodeHealth struct {
	Total               int
	Ready               int
	NotReady            int
	Unknown             int
	ControlPlaneVersion string
	Versions            map[string]string
	States              map[string]string
}

func nodeHealthEqual(left NodeHealth, right NodeHealth) bool {
	if left.Total != right.Total || left.Ready != right.Ready || left.NotReady != right.NotReady || left.Unknown != right.Unknown || left.ControlPlaneVersion != right.ControlPlaneVersion {
		return false
	}
	if len(left.Versions) != len(right.Versions) {
		return false
	}
	for name, nodeVersion := range left.Versions {
		if right.Versions[name] != nodeVersion {
			return false
		}
	}
	if len(left.States) != len(right.States) {
		return false
	}
	for name, state := range left.States {
		if right.States[name] != state {
			return false
		}
	}
	return true
}

func (c *K8sClient) NodeHealth(ctx context.Context) (NodeHealth, error) {
	list, err := c.Dynamic.Resource(schema.GroupVersionResource{Group: "", Version: "v1", Resource: "nodes"}).List(ctx, listOptions)
	if err != nil {
		return NodeHealth{}, err
	}

	health := NodeHealth{
		Total:    len(list.Items),
		Versions: map[string]string{},
		States:   map[string]string{},
	}
	minControlPlaneString := ""
	var minControlPlane *version.Version
	for i := range list.Items {
		obj := &list.Items[i]
		state := nodeReadyState(obj)
		switch state {
		case "Ready":
			health.Ready++
		case "NotReady":
			health.NotReady++
		default:
			health.Unknown++
		}
		health.States[obj.GetName()] = state

		kubelet := firstString(obj, "status", "nodeInfo", "kubeletVersion")
		if kubelet != "" {
			health.Versions[obj.GetName()] = kubelet
		}

		if !nodeIsControlPlane(obj) {
			continue
		}

		parsed, parseErr := version.ParseSemantic(kubelet)
		if parseErr != nil {
			continue
		}

		if minControlPlane == nil || parsed.LessThan(minControlPlane) {
			minControlPlane = parsed
			minControlPlaneString = kubelet
		}
	}

	health.ControlPlaneVersion = minControlPlaneString
	return health, nil
}

func nodeIsControlPlane(obj *unstructured.Unstructured) bool {
	labels := obj.GetLabels()
	if labels[nodeGroupLabel] == shared.GroupControlPlane {
		return true
	}

	for label := range labels {
		role, ok := strings.CutPrefix(label, "node-role.kubernetes.io/")
		if ok && (role == "control-plane" || role == "master") {
			return true
		}
	}

	return false
}

var crdGvr = schema.GroupVersionResource{Group: "apiextensions.k8s.io", Version: "v1", Resource: "customresourcedefinitions"}

func (c *K8sClient) CustomResourceTypes(ctx context.Context) ([]ResourceTypeDef, bool) {
	crds, err := c.Dynamic.Resource(crdGvr).List(ctx, listOptions)
	if err != nil {
		return nil, false
	}

	result := make([]ResourceTypeDef, 0, len(crds.Items))
	for i := range crds.Items {
		crd := &crds.Items[i]

		name := crd.GetName()
		resource, _, _ := strings.Cut(name, ".")
		group := firstString(crd, "spec", "group")
		if resource == "" || group == "" {
			continue
		}

		versions, ok, _ := unstructured.NestedSlice(crd.Object, "spec", "versions")
		if !ok {
			continue
		}

		for _, versionItem := range versions {
			version, ok := versionItem.(map[string]any)
			if !ok {
				continue
			}

			served, _, _ := unstructured.NestedBool(version, "served")
			versionName, _, _ := unstructured.NestedString(version, "name")
			if !served || versionName == "" {
				continue
			}

			names, _, _ := unstructured.NestedMap(crd.Object, "spec", "names")
			kind, _ := names["kind"].(string)
			label := kind
			if label == "" {
				label = resource
			}

			aliases := []string{resource}
			if kind != "" {
				aliases = append(aliases, strings.ToLower(kind))
			}
			if shortNames, ok, _ := unstructured.NestedSlice(names, "shortNames"); ok {
				for _, sn := range shortNames {
					if s, ok := sn.(string); ok && s != "" {
						aliases = append(aliases, s)
					}
				}
			}

			scope, _, _ := unstructured.NestedString(crd.Object, "spec", "scope")

			result = append(result, ResourceTypeDef{
				Id:         "crd:" + name,
				Label:      label,
				Aliases:    aliases,
				Group:      group,
				Gvr:        schema.GroupVersionResource{Group: group, Version: versionName, Resource: resource},
				Columns:    crdPrinterColumns(version),
				HasYaml:    true,
				CanUpdate:  true,
				CanCreate:  true,
				Namespaced: scope == "Namespaced",
			})
			break
		}
	}

	return result, true
}

func crdColumnSortType(printerType any) ucx.TableColumnSortType {
	text, ok := printerType.(string)
	if !ok {
		return ucx.TableColumnSortText
	}

	switch text {
	case "integer", "number":
		return ucx.TableColumnSortNumber
	case "date":
		return ucx.TableColumnSortDuration
	default:
		return ucx.TableColumnSortText
	}
}

func crdPrinterColumns(version map[string]any) []ucx.TableColumn {
	columns, ok, _ := unstructured.NestedSlice(version, "additionalPrinterColumns")
	if !ok {
		return []ucx.TableColumn{{Key: "name", Label: "Name", Copy: true}, {Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration}}
	}

	result := []ucx.TableColumn{{Key: "name", Label: "Name", Copy: true}}
	for _, item := range columns {
		m, ok := item.(map[string]any)
		if !ok {
			continue
		}

		jsonPath, _ := m["jsonPath"].(string)
		name, _ := m["name"].(string)
		if name == "" || jsonPath == "" {
			continue
		}

		key := "pc:" + strings.ToLower(strings.ReplaceAll(name, " ", "-"))
		result = append(result, ucx.TableColumn{Key: key, Label: name, JsonPath: jsonPath, SortType: crdColumnSortType(m["type"])})
	}

	result = append(result, ucx.TableColumn{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration})
	return result
}

func (c *K8sClient) RolloutRestart(ctx context.Context, typeId string, namespace string, name string) error {
	def, ok := ResourceType(typeId)
	if !ok || !rolloutRestartSupported(typeId) {
		return fmt.Errorf("rollout restart is not supported for %s", typeId)
	}

	ri := dashboardResourceInterface(c, def, namespace)
	if def.Namespaced && namespace == "" {
		ri = c.Dynamic.Resource(def.Gvr)
	}

	if typeId == "deployments" {
		obj, err := ri.Get(ctx, name, v1.GetOptions{})
		if err != nil {
			return err
		}
		if paused, ok, _ := unstructured.NestedBool(obj.Object, "spec", "paused"); ok && paused {
			return fmt.Errorf("can't restart paused deployment (run rollout resume first)")
		}
	}

	patch := []byte(fmt.Sprintf(
		`{"spec":{"template":{"metadata":{"annotations":{"kubectl.kubernetes.io/restartedAt":%q}}}}}`,
		time.Now().Format(time.RFC3339),
	))

	_, err := ri.Patch(ctx, name, types.StrategicMergePatchType, patch, v1.PatchOptions{})
	return err
}

// Table rows
// =====================================================================================================================
// A table row is a flat list of cell values. Each resource type has its own builder. The builders read only the
// Kubernetes object, so the same object always gives the same row.

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

func rowsFromNodes(items []unstructured.Unstructured) []ResourceRow {
	rows := make([]ResourceRow, 0, len(items))
	for i := range items {
		obj := &items[i]

		scheduling := "Schedulable"
		if unschedulable, ok, _ := unstructured.NestedBool(obj.Object, "spec", "unschedulable"); ok && unschedulable {
			scheduling = "Cordoned"
		}

		roles := []string{}
		for label := range obj.GetLabels() {
			if role, ok := strings.CutPrefix(label, "node-role.kubernetes.io/"); ok {
				roles = append(roles, role)
			}
		}
		sort.Strings(roles)

		group := obj.GetLabels()[nodeGroupLabel]

		rows = append(rows, ResourceRow{
			Key:   string(obj.GetUID()),
			Group: group,
			Cells: []string{
				obj.GetName(),
				nodeReadyState(obj),
				scheduling,
				"",
				strings.Join(roles, ","),
				firstString(obj, "status", "nodeInfo", "kubeletVersion"),
				"",
				nodeInternalIp(obj),
				objectAge(obj),
			},
		})
	}
	return rows
}

func conditionsOf(obj *unstructured.Unstructured) []map[string]any {
	value, ok, _ := unstructured.NestedSlice(obj.Object, "status", "conditions")
	if !ok {
		return nil
	}

	result := make([]map[string]any, 0, len(value))
	for _, item := range value {
		if m, ok := item.(map[string]any); ok {
			result = append(result, m)
		}
	}
	return result
}

func nodeReadyState(obj *unstructured.Unstructured) string {
	for _, cond := range conditionsOf(obj) {
		if cond["type"] == "Ready" {
			if cond["status"] == "True" {
				return k8sapi.NodeStateReady
			}
			return k8sapi.NodeStateNotReady
		}
	}
	return k8sapi.NodeStateUnknown
}

func nodeInternalIp(obj *unstructured.Unstructured) string {
	addresses, ok, _ := unstructured.NestedSlice(obj.Object, "status", "addresses")
	if !ok {
		return ""
	}

	for _, item := range addresses {
		m, ok := item.(map[string]any)
		if !ok {
			continue
		}
		if m["type"] == "InternalIP" {
			if s, ok := m["address"].(string); ok {
				return s
			}
		}
	}
	return ""
}

func rowsFromPods(items []unstructured.Unstructured) []ResourceRow {
	rows := make([]ResourceRow, 0, len(items))
	for i := range items {
		obj := &items[i]

		readyCount := 0
		totalCount := len(containerStatusesOf(obj))
		restarts := int64(0)
		for _, status := range containerStatusesOf(obj) {
			if status["ready"] == true {
				readyCount++
			}
			if v, ok := status["restartCount"].(int64); ok {
				restarts += v
			}
		}

		rows = append(rows, ResourceRow{
			Key:   string(obj.GetUID()),
			Group: obj.GetNamespace(),
			Cells: []string{
				obj.GetName(),
				fmt.Sprintf("%d/%d", readyCount, totalCount),
				podPhase(obj),
				fmt.Sprintf("%d", restarts),
				firstString(obj, "spec", "nodeName"),
				objectAge(obj),
			},
		})
	}
	return rows
}

func containerStatusesOf(obj *unstructured.Unstructured) []map[string]any {
	value, ok, _ := unstructured.NestedSlice(obj.Object, "status", "containerStatuses")
	if !ok {
		return nil
	}

	result := make([]map[string]any, 0, len(value))
	for _, item := range value {
		if m, ok := item.(map[string]any); ok {
			result = append(result, m)
		}
	}
	return result
}

func podPhase(obj *unstructured.Unstructured) string {
	if obj.GetDeletionTimestamp() != nil {
		return "Terminating"
	}
	phase := firstString(obj, "status", "phase")
	if phase == "" {
		return "Unknown"
	}
	return phase
}

func rowsFromContainers(items []unstructured.Unstructured) []ResourceRow {
	rows := make([]ResourceRow, 0, 4)
	for i := range items {
		obj := &items[i]
		statuses := map[string]map[string]any{}
		for _, status := range containerStatusesOf(obj) {
			if name, ok := status["name"].(string); ok {
				statuses[name] = status
			}
		}

		specs, ok, _ := unstructured.NestedSlice(obj.Object, "spec", "containers")
		if !ok {
			continue
		}

		for _, rawSpec := range specs {
			spec, ok := rawSpec.(map[string]any)
			if !ok {
				continue
			}

			name, _ := spec["name"].(string)
			image, _ := spec["image"].(string)

			ready := "Unknown"
			restarts := ""
			state := "Unknown"
			if status, ok := statuses[name]; ok {
				if status["ready"] == true {
					ready = "Yes"
				} else {
					ready = "No"
				}
				if v, ok := status["restartCount"].(int64); ok {
					restarts = fmt.Sprintf("%d", v)
				}
				if rawState, ok := status["state"].(map[string]any); ok && len(rawState) > 0 {
					for stateKind := range rawState {
						state = stateKind
						break
					}
				}
			}

			rows = append(rows, ResourceRow{
				Key:   string(obj.GetUID()) + "/" + name,
				Group: obj.GetNamespace(),
				Cells: []string{
					name,
					image,
					ready,
					restarts,
					state,
				},
			})
		}
	}
	return rows
}

func rowsFromCustom(items []unstructured.Unstructured, def ResourceTypeDef) []ResourceRow {
	rows := make([]ResourceRow, 0, len(items))
	for i := range items {
		obj := &items[i]

		cells := make([]string, 0, len(def.Columns))
		cells = append(cells, obj.GetName())
		for _, col := range def.Columns {
			if col.Key == "name" {
				continue
			}
			if col.Key == "age" {
				cells = append(cells, objectAge(obj))
				continue
			}
			cells = append(cells, evaluateJsonPath(obj, col.JsonPath))
		}

		rows = append(rows, ResourceRow{
			Key:   string(obj.GetUID()),
			Group: namespaceGroup(def, obj),
			Cells: cells,
		})
	}
	return rows
}

func evaluateJsonPath(obj *unstructured.Unstructured, jsonPath string) string {
	path := strings.TrimPrefix(jsonPath, ".")
	path = strings.TrimSuffix(path, "{0}")
	if path == "" {
		return ""
	}

	parts := strings.Split(path, ".")
	value, ok, err := unstructured.NestedFieldNoCopy(obj.Object, parts...)
	if err != nil || !ok || value == nil {
		return ""
	}

	switch v := value.(type) {
	case string:
		return v
	case bool:
		if v {
			return "true"
		}
		return "false"
	case int64:
		return fmt.Sprintf("%d", v)
	case float64:
		return fmt.Sprintf("%g", v)
	case map[string]any:
		return ""
	case []any:
		return fmt.Sprintf("%d", len(v))
	default:
		return fmt.Sprintf("%v", v)
	}
}

func rowsFromGeneric(items []unstructured.Unstructured, def ResourceTypeDef) []ResourceRow {
	rows := make([]ResourceRow, 0, len(items))
	for i := range items {
		obj := &items[i]
		rows = append(rows, ResourceRow{
			Key:   string(obj.GetUID()),
			Group: namespaceGroup(def, obj),
			Cells: genericCells(def, obj),
			Busy:  rolloutRestartBusy(def.Id, obj),
		})
	}
	return rows
}

func namespaceGroup(def ResourceTypeDef, obj *unstructured.Unstructured) string {
	if !def.Namespaced {
		return ""
	}
	return obj.GetNamespace()
}

func genericCells(def ResourceTypeDef, obj *unstructured.Unstructured) []string {
	switch def.Id {
	case "deployments":
		return []string{
			obj.GetName(),
			fmt.Sprintf("%d/%d", firstInt64(obj, "status", "readyReplicas"), firstInt64(obj, "spec", "replicas")),
			fmt.Sprintf("%d", firstInt64(obj, "status", "updatedReplicas")),
			fmt.Sprintf("%d", firstInt64(obj, "status", "availableReplicas")),
			objectAge(obj),
		}
	case "statefulsets":
		return []string{
			obj.GetName(),
			fmt.Sprintf("%d/%d", firstInt64(obj, "status", "readyReplicas"), firstInt64(obj, "spec", "replicas")),
			objectAge(obj),
		}
	case "daemonsets":
		return []string{
			obj.GetName(),
			fmt.Sprintf("%d", firstInt64(obj, "status", "desiredNumberScheduled")),
			fmt.Sprintf("%d", firstInt64(obj, "status", "numberReady")),
			objectAge(obj),
		}
	case "jobs":
		return []string{
			obj.GetName(),
			fmt.Sprintf("%d/%d", firstInt64(obj, "status", "succeeded"), firstInt64(obj, "spec", "completions")),
			jobDuration(obj),
			objectAge(obj),
		}
	case "cronjobs":
		suspended := "False"
		if v, ok, _ := unstructured.NestedBool(obj.Object, "spec", "suspend"); ok && v {
			suspended = "True"
		}
		return []string{
			obj.GetName(),
			firstString(obj, "spec", "schedule"),
			suspended,
			fmt.Sprintf("%d", len(activeJobsOf(obj))),
			objectAge(obj),
		}
	case "services":
		return []string{
			obj.GetName(),
			firstString(obj, "spec", "type"),
			firstString(obj, "spec", "clusterIP"),
			servicePorts(obj),
			objectAge(obj),
		}
	case "ingresses":
		return []string{
			obj.GetName(),
			firstString(obj, "spec", "ingressClassName"),
			ingressHosts(obj),
			objectAge(obj),
		}
	case "configmaps":
		return []string{
			obj.GetName(),
			fmt.Sprintf("%d", len(dataMapOf(obj))),
			objectAge(obj),
		}
	case "secrets":
		return []string{
			obj.GetName(),
			firstString(obj, "type"),
			fmt.Sprintf("%d", len(dataMapOf(obj))),
			objectAge(obj),
		}
	case "persistentvolumes":
		return []string{
			obj.GetName(),
			firstString(obj, "spec", "capacity", "storage"),
			firstString(obj, "status", "phase"),
			objectAge(obj),
		}
	case "persistentvolumeclaims":
		return []string{
			obj.GetName(),
			firstString(obj, "status", "phase"),
			firstString(obj, "spec", "volumeName"),
			firstString(obj, "status", "capacity", "storage"),
			objectAge(obj),
		}
	case "events":
		return []string{
			obj.GetName(),
			firstString(obj, "reason"),
			firstString(obj, "involvedObject", "name"),
			firstString(obj, "message"),
			objectAge(obj),
		}
	default:
		return []string{obj.GetName(), objectAge(obj)}
	}
}

func jobDuration(obj *unstructured.Unstructured) string {
	start := obj.GetCreationTimestamp().Time
	completedRaw, ok, _ := unstructured.NestedString(obj.Object, "status", "completionTime")
	if !ok {
		return ageString(start)
	}
	completed, err := time.Parse(time.RFC3339, completedRaw)
	if err != nil {
		return ageString(start)
	}
	return durationString(completed.Sub(start))
}

func durationString(d time.Duration) string {
	switch {
	case d < time.Minute:
		return fmt.Sprintf("%ds", int(d.Seconds()))
	case d < time.Hour:
		return fmt.Sprintf("%dm", int(d.Minutes()))
	default:
		return fmt.Sprintf("%dh", int(d.Hours()))
	}
}

func activeJobsOf(obj *unstructured.Unstructured) []any {
	value, ok, _ := unstructured.NestedSlice(obj.Object, "status", "active")
	if !ok {
		return nil
	}
	return value
}

func servicePorts(obj *unstructured.Unstructured) string {
	ports, ok, _ := unstructured.NestedSlice(obj.Object, "spec", "ports")
	if !ok {
		return ""
	}

	parts := make([]string, 0, len(ports))
	for _, item := range ports {
		m, ok := item.(map[string]any)
		if !ok {
			continue
		}

		port, _ := m["port"].(int64)
		nodePort, _ := m["nodePort"].(int64)
		protocol, _ := m["protocol"].(string)

		text := fmt.Sprintf("%d", port)
		if nodePort != 0 {
			text = fmt.Sprintf("%d:%d", port, nodePort)
		}
		if protocol != "" {
			text += "/" + protocol
		}
		parts = append(parts, text)
	}
	return strings.Join(parts, ",")
}

func ingressHosts(obj *unstructured.Unstructured) string {
	rules, ok, _ := unstructured.NestedSlice(obj.Object, "spec", "rules")
	if !ok {
		return ""
	}

	hosts := make([]string, 0, len(rules))
	for _, item := range rules {
		m, ok := item.(map[string]any)
		if !ok {
			continue
		}
		if host, ok := m["host"].(string); ok && host != "" {
			hosts = append(hosts, host)
		}
	}
	return strings.Join(hosts, ",")
}

func dataMapOf(obj *unstructured.Unstructured) map[string]any {
	value, ok, _ := unstructured.NestedMap(obj.Object, "data")
	if !ok {
		return nil
	}
	return value
}

// Row actions
// =====================================================================================================================
// The row actions state which actions a row offers. An action that cannot run is present but disabled. The reason
// explains why, so the user does not have to guess.

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

	viewYaml := ucx.TableRowAction{Id: dashboardActionViewYaml, Enabled: false}
	if !provisioningRow {
		viewYaml.Enabled = true
	}

	editYaml := ucx.TableRowAction{Id: dashboardActionEditYaml, Enabled: false}
	if !provisioningRow && (def.CanUpdate || def.Id == containersTypeId) {
		editYaml.Enabled = true
	}

	if def.Id == "pods" || def.Id == containersTypeId {
		if provisioningRow {
			return []ucx.TableRowAction{viewYaml}
		}
		actions := []ucx.TableRowAction{
			{Id: dashboardActionOpenShell, Enabled: true},
			viewYaml,
			editYaml,
		}
		if dashboardResourceDeleteSupported(def) {
			actions = append(actions, ucx.TableRowAction{Id: dashboardActionDeleteResource, Enabled: true})
		}
		return actions
	}

	if rolloutRestartSupported(def.Id) {
		return []ucx.TableRowAction{
			{Id: dashboardActionRolloutRestart, Enabled: true},
			viewYaml,
			editYaml,
			{Id: dashboardActionDeleteResource, Enabled: dashboardResourceDeleteSupported(def)},
		}
	}

	if def.Id != "nodes" {
		actions := []ucx.TableRowAction{
			viewYaml,
			editYaml,
			{Id: dashboardActionDeleteResource, Enabled: dashboardResourceDeleteSupported(def)},
		}
		if def.Id == "ingresses" && obj != nil {
			if serviceUrl := dashboardIngressServiceUrl(obj); serviceUrl != "" {
				actions = append(actions, ucx.TableRowAction{Id: dashboardActionOpenService, Enabled: true, Text: serviceUrl})
			}
		}
		return actions
	}

	nodeName := row.Cells[0]

	jobId := nodeJobIds[nodeName]
	goToJob := ucx.TableRowAction{
		Id:             dashboardActionGoToJob,
		Enabled:        false,
		DisabledReason: "No UCloud job is associated with this node",
	}
	openShell := ucx.TableRowAction{
		Id:             dashboardActionOpenShell,
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
		{Id: dashboardActionCopyNodeName, Enabled: true, Text: row.Cells[0]},
		goToJob,
		openShell,
		viewYaml,
		editYaml,
	}

	if provisioningRow {
		if !maintenanceUnavailable {
			actions = append(actions, ucx.TableRowAction{
				Id:             dashboardActionRemoveNode,
				Enabled:        true,
				DisabledReason: "Removes the node from the cluster",
			})
		}
		return actions
	}
	isUpgrade := knownRecorded && operation.Kind == maintenance.KindUpgrade
	recoveryBlocked := knownRecorded && maintenance.KindRequiresRecovery(operation.Kind) &&
		operation.ExecutorSubmitted && operation.RecoveryRequired
	upgradeNode := ucx.TableRowAction{Id: dashboardActionUpgradeNode, Enabled: true}
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
				Id:             dashboardActionUncordon,
				Enabled:        false,
				DisabledReason: "The maintenance state is unavailable",
			})
		} else {
			actions = append(actions, ucx.TableRowAction{
				Id:             dashboardActionCordonDrain,
				Enabled:        false,
				DisabledReason: "The maintenance state is unavailable",
			})
		}
	case phaseActive:
		if nodeCordoned {
			actions = append(actions, ucx.TableRowAction{
				Id:             dashboardActionUncordon,
				Enabled:        false,
				DisabledReason: "An operation is already active on this node",
			})
		}
	default:
		if nodeCordoned {
			if recoveryBlocked {
				actions = append(actions, ucx.TableRowAction{
					Id:             dashboardActionUncordon,
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
	cordonDrainEnabled = ucx.TableRowAction{Id: dashboardActionCordonDrain, Enabled: true}
	uncordonEnabled    = ucx.TableRowAction{Id: dashboardActionUncordon, Enabled: true}
	removeNodeEnabled  = ucx.TableRowAction{
		Id:             dashboardActionRemoveNode,
		Enabled:        true,
		DisabledReason: "Removes the node from the cluster",
	}
)

func maintenanceCellForOperation(operation maintenance.Operation, present bool) string {
	if !present {
		return ""
	}
	if maintenance.PhaseActive(operation.Phase) {
		if operation.Kind == maintenance.KindUpgrade {
			return "Upgrading"
		}
		if operation.Kind == maintenance.KindOsUpdate {
			return "Updating"
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

func osUpdateCellForReport(report maintenance.AutoUpdateReport, present bool) string {
	if !present || report.CheckedAt.IsZero() {
		return ""
	}
	if report.Error != "" {
		return "check failed"
	}

	parts := []string{}
	if report.Pending > 0 {
		parts = append(parts, fmt.Sprintf("%d pending", report.Pending))
		if report.Security > 0 {
			parts = append(parts, fmt.Sprintf("%d security", report.Security))
		}
	}
	if report.RebootRequired {
		parts = append(parts, "reboot required")
	}
	if len(parts) == 0 {
		return "up to date"
	}
	return strings.Join(parts, ", ")
}

func objectAge(obj *unstructured.Unstructured) string {
	created := obj.GetCreationTimestamp().Time
	return ageString(created)
}

func ageString(t time.Time) string {
	if t.IsZero() {
		return ""
	}

	duration := time.Since(t)
	switch {
	case duration < time.Minute:
		return fmt.Sprintf("%ds", int(duration.Seconds()))
	case duration < time.Hour:
		return fmt.Sprintf("%dm", int(duration.Minutes()))
	case duration < 24*time.Hour:
		return fmt.Sprintf("%dh", int(duration.Hours()))
	default:
		return fmt.Sprintf("%dd", int(duration.Hours()/24))
	}
}

func firstString(obj *unstructured.Unstructured, path ...string) string {
	value, ok, _ := unstructured.NestedString(obj.Object, path...)
	if !ok {
		return ""
	}
	return value
}

func firstInt64(obj *unstructured.Unstructured, path ...string) int64 {
	value, ok, _ := unstructured.NestedInt64(obj.Object, path...)
	if !ok {
		return 0
	}
	return value
}

func workloadLabelSelector(owner *unstructured.Unstructured) string {
	selector, ok, _ := unstructured.NestedMap(owner.Object, "spec", "selector")
	if !ok {
		return ""
	}

	return labelSelectorString(selector)
}

func labelSelectorString(selector map[string]any) string {
	matchLabels, _ := selector["matchLabels"].(map[string]any)
	parts := make([]string, 0, len(matchLabels))
	for key, rawValue := range matchLabels {
		value, _ := rawValue.(string)
		parts = append(parts, key+"="+value)
	}
	sort.Strings(parts)

	matchExpressions, _ := selector["matchExpressions"].([]any)
	for _, rawExpression := range matchExpressions {
		expression, ok := rawExpression.(map[string]any)
		if !ok {
			continue
		}

		key, _ := expression["key"].(string)
		operator, _ := expression["operator"].(string)
		if key == "" || operator == "" {
			continue
		}

		rawValues, _ := expression["values"].([]any)
		values := make([]string, 0, len(rawValues))
		for _, rawValue := range rawValues {
			value, _ := rawValue.(string)
			if value != "" {
				values = append(values, value)
			}
		}

		switch operator {
		case "In":
			parts = append(parts, key+" in ("+strings.Join(values, ",")+")")
		case "NotIn":
			parts = append(parts, key+" notin ("+strings.Join(values, ",")+")")
		case "Exists":
			parts = append(parts, key)
		case "DoesNotExist":
			parts = append(parts, "!"+key)
		}
	}

	return strings.Join(parts, ",")
}

func dashboardIngressServiceUrl(obj *unstructured.Unstructured) string {
	rules, _, _ := unstructured.NestedSlice(obj.Object, "spec", "rules")
	for _, item := range rules {
		rule, ok := item.(map[string]any)
		if !ok {
			continue
		}
		host, _, _ := unstructured.NestedString(rule, "host")
		if host == "" || strings.Contains(host, "*") {
			continue
		}
		paths, _, _ := unstructured.NestedSlice(rule, "http", "paths")
		for _, item := range paths {
			path, ok := item.(map[string]any)
			if !ok {
				continue
			}
			prefix, _, _ := unstructured.NestedString(path, "path")
			if prefix == "" {
				prefix = "/"
			}
			target := url.URL{Scheme: "https", Host: host, Path: prefix}
			return target.String()
		}
	}
	return ""
}

// Discovery
// =====================================================================================================================
// The cluster reports its own API groups. Discovery merges that list with the types that the dashboard defines, so a
// new Kubernetes version adds new types without a change here.

func resourceBuiltinTypes(ctx context.Context, client *K8sClient, customTypes []ResourceTypeDef) ([]ResourceTypeDef, error) {
	discovery := client.Typed.Discovery().RESTClient()
	var groups v1.APIGroupList
	err := discovery.Get().AbsPath("/apis").Do(ctx).Into(&groups)
	if err != nil {
		return nil, err
	}
	versions := []schema.GroupVersion{{Version: "v1"}}
	for _, group := range groups.Groups {
		isBuiltin := scheme.Scheme.IsGroupRegistered(group.Name) ||
			group.Name == "apiextensions.k8s.io" ||
			group.Name == "apiregistration.k8s.io"
		if !isBuiltin {
			continue
		}
		preferred := schema.GroupVersion{
			Group:   group.Name,
			Version: group.PreferredVersion.Version,
		}
		versions = append(versions, preferred)
		for _, version := range group.Versions {
			if version.Version != preferred.Version {
				versions = append(versions, schema.GroupVersion{
					Group:   group.Name,
					Version: version.Version,
				})
			}
		}
	}

	result := make([]ResourceTypeDef, 0)
	seen := map[schema.GroupResource]bool{}
	for _, def := range customTypes {
		seen[def.Gvr.GroupResource()] = true
	}
	for _, version := range versions {
		path := "/apis/" + version.String()
		if version.Group == "" {
			path = "/api/" + version.Version
		}
		var resources v1.APIResourceList
		err = discovery.Get().AbsPath(path).Do(ctx).Into(&resources)
		if err != nil {
			return nil, err
		}
		for _, resource := range resources.APIResources {
			if strings.Contains(resource.Name, "/") {
				continue
			}
			if version.Group == "events.k8s.io" && resource.Name == "events" {
				continue
			}
			canList := false
			canWatch := false
			canUpdate := false
			canCreate := false
			for _, verb := range resource.Verbs {
				canList = canList || verb == "list"
				canWatch = canWatch || verb == "watch"
				canUpdate = canUpdate || verb == "update"
				canCreate = canCreate || verb == "create"
			}
			key := schema.GroupResource{Group: version.Group, Resource: resource.Name}
			if !canList || !canWatch || seen[key] {
				continue
			}
			seen[key] = true
			def, known := ResourceType(resource.Name)
			if !known || def.Gvr.Group != version.Group {
				def = ResourceTypeDef{
					Id:    "builtin:" + key.String(),
					Label: resourceBuiltinLabel(resource),
					Group: resourceBuiltinGroup(version.Group, resource.Name),
					Columns: []ucx.TableColumn{
						{Key: "name", Label: "Name", Copy: true},
						{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
					},
					HasYaml: true,
				}
			}
			def.Gvr = version.WithResource(resource.Name)
			def.Namespaced = resource.Namespaced
			def.CanUpdate = canUpdate
			def.CanCreate = canCreate && resource.Name != "nodes"
			def.Aliases = resourceBuiltinAliases(resource, version.Group)
			result = append(result, def)
		}
	}
	sort.Slice(result, func(i, j int) bool {
		leftDiscovered := strings.HasPrefix(result[i].Id, "builtin:")
		rightDiscovered := strings.HasPrefix(result[j].Id, "builtin:")
		if leftDiscovered != rightDiscovered {
			return !leftDiscovered
		}
		return result[i].Id < result[j].Id
	})
	return result, nil
}

func resourceBuiltinLabel(resource v1.APIResource) string {
	kind := resource.Kind
	if strings.ToLower(kind) == resource.Name {
		return kind
	}
	if strings.HasSuffix(kind, "y") {
		return strings.TrimSuffix(kind, "y") + "ies"
	}
	if strings.HasSuffix(kind, "s") {
		return kind + "es"
	}
	return kind + "s"
}

func resourceBuiltinAliases(resource v1.APIResource, group string) []string {
	aliases := []string{resource.Name, resource.SingularName, strings.ToLower(resource.Kind)}
	aliases = append(aliases, resource.ShortNames...)
	if group != "" {
		aliases = append(aliases, resource.Name+"."+group)
		if resource.SingularName != "" {
			aliases = append(aliases, resource.SingularName+"."+group)
		}
	}
	result := make([]string, 0, len(aliases))
	seen := map[string]bool{}
	for _, alias := range aliases {
		if alias != "" && !seen[alias] {
			seen[alias] = true
			result = append(result, alias)
		}
	}
	return result
}

func resourceBuiltinGroup(group string, name string) string {
	switch group {
	case "":
		switch name {
		case "replicationcontrollers", "podtemplates":
			return "Workloads"
		case "endpoints":
			return "Networking"
		case "serviceaccounts":
			return "RBAC"
		case "resourcequotas", "limitranges":
			return "Config"
		default:
			return "Cluster"
		}
	case "apps", "batch", "autoscaling", "policy":
		return "Workloads"
	case "networking.k8s.io", "discovery.k8s.io":
		return "Networking"
	case "storage.k8s.io", "storagemigration.k8s.io":
		return "Storage"
	case "rbac.authorization.k8s.io":
		return "RBAC"
	case "admissionregistration.k8s.io":
		return "Cluster"
	default:
		return "Cluster"
	}
}
