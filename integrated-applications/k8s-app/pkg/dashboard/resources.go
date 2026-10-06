package dashboard

import (
	"context"
	"encoding/json"
	"fmt"
	"sort"
	"strings"
	"time"

	"k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/runtime/schema"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/apimachinery/pkg/util/version"
	"k8s.io/client-go/dynamic"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/tools/clientcmd"
	"sigs.k8s.io/yaml"

	"ucloud.dk/iapp/k8s/pkg/shared"
	"ucloud.dk/shared/pkg/ucx"
)

type ResourceTypeDef struct {
	Id         string
	Label      string
	Aliases    []string
	Group      string
	Gvr        schema.GroupVersionResource
	Columns    []ucx.TableColumn
	HasYaml    bool
	Namespaced bool
}

type ResourceRow struct {
	Key     string
	Group   string
	Cells   []string
	Actions []ucx.TableRowAction
}

const nodeGroupLabel = "ucloud.dk/k8s-node-group"

var resourceTypes = []ResourceTypeDef{
	{
		Id: "nodes", Label: "Nodes", Aliases: []string{"no"}, Group: "Cluster",
		Gvr: schema.GroupVersionResource{Group: "", Version: "v1", Resource: "nodes"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "status", Label: "Status"},
			{Key: "scheduling", Label: "Scheduling"},
			{Key: "maintenance", Label: "Maintenance"},
			{Key: "role", Label: "Roles"},
			{Key: "version", Label: "Version"},
			{Key: "ip", Label: "IP", SortType: ucx.TableColumnSortIp},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
	},
	{
		Id: "pods", Label: "Pods", Aliases: []string{"po"}, Group: "Workloads", Namespaced: true,
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
		Id: "deployments", Label: "Deployments", Aliases: []string{"deploy"}, Group: "Workloads", Namespaced: true,
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
		Id: "statefulsets", Label: "StatefulSets", Aliases: []string{"sts"}, Group: "Workloads", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "apps", Version: "v1", Resource: "statefulsets"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "ready", Label: "Ready", SortType: ucx.TableColumnSortRatio},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
	},
	{
		Id: "daemonsets", Label: "DaemonSets", Aliases: []string{"ds"}, Group: "Workloads", Namespaced: true,
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
		Id: "cronjobs", Label: "CronJobs", Aliases: []string{"cj"}, Group: "Workloads", Namespaced: true,
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
		Id: "services", Label: "Services", Aliases: []string{"svc"}, Group: "Networking", Namespaced: true,
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
		Id: "ingresses", Label: "Ingresses", Aliases: []string{"ing"}, Group: "Networking", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "networking.k8s.io", Version: "v1", Resource: "ingresses"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "class", Label: "Class"},
			{Key: "hosts", Label: "Hosts"},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
	},
	{
		Id: "configmaps", Label: "ConfigMaps", Aliases: []string{"cm"}, Group: "Config", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "", Version: "v1", Resource: "configmaps"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "data", Label: "Data", SortType: ucx.TableColumnSortNumber},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
		HasYaml: false,
	},
	{
		Id: "secrets", Label: "Secrets", Aliases: []string{"sec"}, Group: "Config", Namespaced: true,
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
		Id: "persistentvolumes", Label: "PVs", Aliases: []string{"pv"}, Group: "Storage",
		Gvr: schema.GroupVersionResource{Group: "", Version: "v1", Resource: "persistentvolumes"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name", Copy: true},
			{Key: "capacity", Label: "Capacity", SortType: ucx.TableColumnSortCapacity},
			{Key: "phase", Label: "Phase"},
			{Key: "age", Label: "Age", SortType: ucx.TableColumnSortDuration},
		},
	},
	{
		Id: "persistentvolumeclaims", Label: "PVCs", Aliases: []string{"pvc"}, Group: "Storage", Namespaced: true,
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
		Id: "events", Label: "Events", Aliases: []string{"ev"}, Group: "Cluster", Namespaced: true,
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

// containersTypeDef is a synthetic resource type used to drill into the containers of a single pod.
// It lists pods (filtered to exactly one) and renders one row per container of that pod.
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
			return def, true
		}
	}
	return ResourceTypeDef{}, false
}

type K8sClient struct {
	Dynamic dynamic.Interface
	Typed   kubernetes.Interface
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

var listOptions = v1.ListOptions{}

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
}

func (c *K8sClient) NodeHealth(ctx context.Context) (NodeHealth, error) {
	list, err := c.Dynamic.Resource(schema.GroupVersionResource{Group: "", Version: "v1", Resource: "nodes"}).List(ctx, listOptions)
	if err != nil {
		return NodeHealth{}, err
	}

	health := NodeHealth{Total: len(list.Items)}
	minControlPlaneString := ""
	var minControlPlane *version.Version
	for i := range list.Items {
		obj := &list.Items[i]
		switch nodeReadyState(obj) {
		case "Ready":
			health.Ready++
		case "NotReady":
			health.NotReady++
		default:
			health.Unknown++
		}

		if !nodeIsControlPlane(obj) {
			continue
		}

		kubelet := firstString(obj, "status", "nodeInfo", "kubeletVersion")
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

func (c *K8sClient) CustomResourceTypes(ctx context.Context) []ResourceTypeDef {
	crds, err := c.Dynamic.Resource(crdGvr).List(ctx, listOptions)
	if err != nil {
		return nil
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
				Namespaced: scope == "Namespaced",
			})
			break
		}
	}

	return result
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

func objectAge(obj *unstructured.Unstructured) string {
	created := obj.GetCreationTimestamp().Time
	return ageString(created)
}

func firstString(obj *unstructured.Unstructured, path ...string) string {
	value, ok, _ := unstructured.NestedString(obj.Object, path...)
	if !ok {
		return ""
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

func firstInt64(obj *unstructured.Unstructured, path ...string) int64 {
	value, ok, _ := unstructured.NestedInt64(obj.Object, path...)
	if !ok {
		return 0
	}
	return value
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
				return "Ready"
			}
			return "NotReady"
		}
	}
	return "Unknown"
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
			if v, ok := status["restartCount"].(float64); ok {
				restarts += int64(v)
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
				if v, ok := status["restartCount"].(float64); ok {
					restarts = fmt.Sprintf("%d", int64(v))
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
	phase := firstString(obj, "status", "phase")
	if phase == "" {
		return "Unknown"
	}
	return phase
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
		})
	}
	return rows
}

func (c *K8sClient) YamlForUid(ctx context.Context, def ResourceTypeDef, namespace string, name string) (string, error) {
	var ri dynamic.ResourceInterface = c.Dynamic.Resource(def.Gvr)
	if def.Namespaced && namespace != "" {
		ri = c.Dynamic.Resource(def.Gvr).Namespace(namespace)
	}

	obj, err := ri.Get(ctx, name, v1.GetOptions{})
	if err != nil {
		return "", err
	}

	data, err := obj.MarshalJSON()
	if err != nil {
		return "", err
	}

	var generic any
	if err := json.Unmarshal(data, &generic); err != nil {
		return "", err
	}

	out, err := yaml.Marshal(generic)
	if err != nil {
		return "", err
	}
	return string(out), nil
}

func (c *K8sClient) RolloutRestart(ctx context.Context, typeId string, namespace string, name string) error {
	def, ok := ResourceType(typeId)
	if !ok || !rolloutRestartSupported(typeId) {
		return fmt.Errorf("rollout restart is not supported for %s", typeId)
	}

	var ri dynamic.ResourceInterface = c.Dynamic.Resource(def.Gvr)
	if def.Namespaced && namespace != "" {
		ri = c.Dynamic.Resource(def.Gvr).Namespace(namespace)
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
			quantityString(obj, "spec", "capacity", "storage"),
			firstString(obj, "status", "phase"),
			objectAge(obj),
		}
	case "persistentvolumeclaims":
		return []string{
			obj.GetName(),
			firstString(obj, "status", "phase"),
			firstString(obj, "spec", "volumeName"),
			quantityString(obj, "status", "capacity", "storage"),
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
	end, ok, _ := unstructured.NestedInt64(obj.Object, "status", "completionTime")
	if !ok {
		return ageString(start)
	}
	completed := time.UnixMilli(end)
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

func quantityString(obj *unstructured.Unstructured, path ...string) string {
	value, ok, _ := unstructured.NestedString(obj.Object, path...)
	if !ok {
		return ""
	}
	return value
}
