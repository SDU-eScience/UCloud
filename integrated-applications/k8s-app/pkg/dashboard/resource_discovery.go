package dashboard

import (
	"context"
	"sort"
	"strings"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime/schema"
	"k8s.io/client-go/kubernetes/scheme"

	"ucloud.dk/shared/pkg/ucx"
)

func resourceBuiltinTypes(ctx context.Context, client *K8sClient, customTypes []ResourceTypeDef) ([]ResourceTypeDef, error) {
	discovery := client.Typed.Discovery().RESTClient()
	var groups metav1.APIGroupList
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
		var resources metav1.APIResourceList
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

func resourceBuiltinLabel(resource metav1.APIResource) string {
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

func resourceBuiltinAliases(resource metav1.APIResource, group string) []string {
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
