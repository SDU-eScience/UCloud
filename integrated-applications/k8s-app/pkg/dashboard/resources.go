package dashboard

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"sort"
	"strings"
	"time"

	"k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/runtime/schema"
	"k8s.io/client-go/dynamic"
	"k8s.io/client-go/tools/clientcmd"
	"sigs.k8s.io/yaml"

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
			{Key: "name", Label: "Name"},
			{Key: "status", Label: "Status"},
			{Key: "role", Label: "Roles"},
			{Key: "version", Label: "Version"},
			{Key: "ip", Label: "IP"},
			{Key: "age", Label: "Age"},
		},
	},
	{
		Id: "pods", Label: "Pods", Aliases: []string{"po"}, Group: "Workloads", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "", Version: "v1", Resource: "pods"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name"},
			{Key: "ready", Label: "Ready"},
			{Key: "status", Label: "Status"},
			{Key: "restarts", Label: "Restarts"},
			{Key: "node", Label: "Node"},
			{Key: "age", Label: "Age"},
		},
	},
	{
		Id: "deployments", Label: "Deployments", Aliases: []string{"deploy"}, Group: "Workloads", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "apps", Version: "v1", Resource: "deployments"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name"},
			{Key: "ready", Label: "Ready"},
			{Key: "upToDate", Label: "Up-to-date"},
			{Key: "available", Label: "Available"},
			{Key: "age", Label: "Age"},
		},
	},
	{
		Id: "statefulsets", Label: "StatefulSets", Aliases: []string{"sts"}, Group: "Workloads", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "apps", Version: "v1", Resource: "statefulsets"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name"},
			{Key: "ready", Label: "Ready"},
			{Key: "age", Label: "Age"},
		},
	},
	{
		Id: "daemonsets", Label: "DaemonSets", Aliases: []string{"ds"}, Group: "Workloads", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "apps", Version: "v1", Resource: "daemonsets"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name"},
			{Key: "desired", Label: "Desired"},
			{Key: "ready", Label: "Ready"},
			{Key: "age", Label: "Age"},
		},
	},
	{
		Id: "jobs", Label: "Jobs", Aliases: []string{"job"}, Group: "Workloads", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "batch", Version: "v1", Resource: "jobs"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name"},
			{Key: "completions", Label: "Completions"},
			{Key: "duration", Label: "Duration"},
			{Key: "age", Label: "Age"},
		},
	},
	{
		Id: "cronjobs", Label: "CronJobs", Aliases: []string{"cj"}, Group: "Workloads", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "batch", Version: "v1", Resource: "cronjobs"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name"},
			{Key: "schedule", Label: "Schedule"},
			{Key: "suspend", Label: "Suspend"},
			{Key: "active", Label: "Active"},
			{Key: "age", Label: "Age"},
		},
	},
	{
		Id: "services", Label: "Services", Aliases: []string{"svc"}, Group: "Networking", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "", Version: "v1", Resource: "services"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name"},
			{Key: "type", Label: "Type"},
			{Key: "clusterIp", Label: "Cluster IP"},
			{Key: "ports", Label: "Ports"},
			{Key: "age", Label: "Age"},
		},
	},
	{
		Id: "ingresses", Label: "Ingresses", Aliases: []string{"ing"}, Group: "Networking", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "networking.k8s.io", Version: "v1", Resource: "ingresses"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name"},
			{Key: "class", Label: "Class"},
			{Key: "hosts", Label: "Hosts"},
			{Key: "age", Label: "Age"},
		},
	},
	{
		Id: "configmaps", Label: "ConfigMaps", Aliases: []string{"cm"}, Group: "Config", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "", Version: "v1", Resource: "configmaps"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name"},
			{Key: "data", Label: "Data"},
			{Key: "age", Label: "Age"},
		},
		HasYaml: false,
	},
	{
		Id: "secrets", Label: "Secrets", Aliases: []string{"sec"}, Group: "Config", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "", Version: "v1", Resource: "secrets"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name"},
			{Key: "type", Label: "Type"},
			{Key: "data", Label: "Data"},
			{Key: "age", Label: "Age"},
		},
		HasYaml: false,
	},
	{
		Id: "persistentvolumes", Label: "PVs", Aliases: []string{"pv"}, Group: "Storage",
		Gvr: schema.GroupVersionResource{Group: "", Version: "v1", Resource: "persistentvolumes"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name"},
			{Key: "capacity", Label: "Capacity"},
			{Key: "phase", Label: "Phase"},
			{Key: "age", Label: "Age"},
		},
	},
	{
		Id: "persistentvolumeclaims", Label: "PVCs", Aliases: []string{"pvc"}, Group: "Storage", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "", Version: "v1", Resource: "persistentvolumeclaims"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name"},
			{Key: "status", Label: "Status"},
			{Key: "volume", Label: "Volume"},
			{Key: "capacity", Label: "Capacity"},
			{Key: "age", Label: "Age"},
		},
	},
	{
		Id: "events", Label: "Events", Aliases: []string{"ev"}, Group: "Cluster", Namespaced: true,
		Gvr: schema.GroupVersionResource{Group: "", Version: "v1", Resource: "events"},
		Columns: []ucx.TableColumn{
			{Key: "name", Label: "Name"},
			{Key: "reason", Label: "Reason"},
			{Key: "object", Label: "Object"},
			{Key: "message", Label: "Message"},
			{Key: "age", Label: "Age"},
		},
	},
}

func ResourceTypes() []ResourceTypeDef {
	return resourceTypes
}

func ResourceType(id string) (ResourceTypeDef, bool) {
	for _, def := range resourceTypes {
		if def.Id == id {
			return def, true
		}
	}
	return ResourceTypeDef{}, false
}

type K8sClient struct {
	Dynamic dynamic.Interface
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

	return &K8sClient{Dynamic: dyn}, nil
}

func (c *K8sClient) List(ctx context.Context, def ResourceTypeDef) ([]unstructured.Unstructured, error) {
	list, err := c.Dynamic.Resource(def.Gvr).List(ctx, listOptions)
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

			parts := strings.Split(group, ".")
			crdGroup := group
			if len(parts) >= 2 {
				crdGroup = parts[len(parts)-2] + "." + parts[len(parts)-1]
			}

			result = append(result, ResourceTypeDef{
				Id:         "crd:" + name,
				Label:      label,
				Aliases:    aliases,
				Group:      crdGroup,
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

func crdPrinterColumns(version map[string]any) []ucx.TableColumn {
	columns, ok, _ := unstructured.NestedSlice(version, "additionalPrinterColumns")
	if !ok {
		return []ucx.TableColumn{{Key: "name", Label: "Name"}, {Key: "age", Label: "Age"}}
	}

	result := []ucx.TableColumn{{Key: "name", Label: "Name"}}
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
		result = append(result, ucx.TableColumn{Key: key, Label: name, JsonPath: jsonPath})
	}

	if len(result) == 1 {
		result = append(result, ucx.TableColumn{Key: "age", Label: "Age"})
	} else {
		result = append(result, ucx.TableColumn{Key: "age", Label: "Age"})
	}
	return result
}

func KubeconfigPath() string {
	path := os.Getenv("KUBECONFIG")
	if path != "" {
		return path
	}
	return "/etc/ucloud-k8s/management/kubeconfig-internal"
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

		ready := "Unknown"
		for _, cond := range conditionsOf(obj) {
			if cond["type"] == "Ready" {
				if cond["status"] == "True" {
					ready = "Ready"
				} else {
					ready = "NotReady"
				}
			}
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
				ready,
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

func (c *K8sClient) ListRows(ctx context.Context, def ResourceTypeDef) ([]ResourceRow, error) {
	return c.ListRowsInNamespace(ctx, def, "")
}

func (c *K8sClient) ListRowsInNamespace(ctx context.Context, def ResourceTypeDef, namespace string) ([]ResourceRow, error) {
	var ri dynamic.ResourceInterface = c.Dynamic.Resource(def.Gvr)
	if namespace != "" && def.Namespaced {
		ri = c.Dynamic.Resource(def.Gvr).Namespace(namespace)
	}

	list, err := ri.List(ctx, listOptions)
	if err != nil {
		return nil, err
	}
	items := list.Items

	switch def.Id {
	case "nodes":
		return rowsFromNodes(items), nil
	case "pods":
		return rowsFromPods(items), nil
	default:
		if strings.HasPrefix(def.Id, "crd:") {
			return rowsFromCustom(items, def), nil
		}
		return rowsFromGeneric(items, def), nil
	}
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
