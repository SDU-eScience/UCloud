package dashboard

import (
	"sort"
	"strings"

	"ucloud.dk/shared/pkg/ucx"
)

type dashboardCustomNavNode struct {
	label     string
	domain    string
	children  map[string]*dashboardCustomNavNode
	resources []ucx.NavItemChild
}

func (app *stackUiApp) resourceNavItems() []ucx.NavItem {
	typeDefs := app.allTypeDefs()

	navItems := make([]ucx.NavItem, 0, 8)
	groups := map[string][]ucx.NavItemChild{}
	var customTypes []ResourceTypeDef
	for _, def := range typeDefs {
		if strings.HasPrefix(def.Id, "crd:") {
			customTypes = append(customTypes, def)
			continue
		}
		path := dashboardBuiltinNavPath(def)
		group := path[0]
		children := groups[group]
		dashboardBuiltinNavAdd(&children, path[1:], "group:"+group, ucx.NavItemChild{
			Id:      def.Id,
			Label:   def.Label,
			Aliases: def.Aliases,
		})
		groups[group] = children
	}
	for group, children := range groups {
		dashboardNavSort(children)
		navItems = append(navItems, ucx.NavItem{
			Id:       "group:" + group,
			Label:    group,
			Children: children,
		})
	}
	navItems = append(navItems, dashboardCustomNavItems(customTypes)...)
	sort.Slice(navItems, func(i, j int) bool {
		leftCluster := navItems[i].Id == "group:Cluster"
		rightCluster := navItems[j].Id == "group:Cluster"
		if leftCluster != rightCluster {
			return leftCluster
		}
		leftDirectory := len(navItems[i].Children) > 0
		rightDirectory := len(navItems[j].Children) > 0
		if leftDirectory != rightDirectory {
			return leftDirectory
		}
		return navItems[i].Label < navItems[j].Label
	})

	navItems = append([]ucx.NavItem{dashboardHomeNavItem()}, navItems...)
	if len(navItems) > 1 {
		navItems[1].SeparatorBefore = true
	}

	return navItems
}

func dashboardHomeNavItem() ucx.NavItem {
	return ucx.NavItem{
		Id:      navHomeId,
		Label:   "Home",
		Aliases: []string{"home", "overview"},
	}
}

func dashboardBuiltinNavPath(def ResourceTypeDef) []string {
	group := def.Group
	if group == "" {
		group = "Other"
	}
	if def.Gvr.Group == "flowcontrol.apiserver.k8s.io" {
		return []string{"Cluster", "Advanced", "Flow control"}
	}
	if def.Gvr.Group == "resource.k8s.io" {
		return []string{"Cluster", "Advanced", "Device allocation"}
	}
	if def.Gvr.Group == "admissionregistration.k8s.io" {
		return []string{"Cluster", "Admission"}
	}
	advanced := false
	switch group {
	case "Cluster":
		switch def.Gvr.Resource {
		case "nodes", "namespaces", "events":
		default:
			advanced = true
		}
	case "Config":
		switch def.Gvr.Resource {
		case "limitranges", "resourcequotas":
			advanced = true
		}
	case "Networking":
		switch def.Gvr.Resource {
		case "services", "ingresses", "networkpolicies":
		default:
			advanced = true
		}
	case "Storage":
		switch def.Gvr.Resource {
		case "persistentvolumes", "persistentvolumeclaims", "storageclasses":
		default:
			advanced = true
		}
	case "Workloads":
		switch def.Gvr.Resource {
		case "pods", "deployments", "statefulsets", "daemonsets", "replicasets", "jobs", "cronjobs":
		default:
			advanced = true
		}
	}
	if advanced {
		return []string{group, "Advanced"}
	}
	return []string{group}
}

func dashboardBuiltinNavAdd(children *[]ucx.NavItemChild, path []string, parentId string, item ucx.NavItemChild) {
	if len(path) == 0 {
		*children = append(*children, item)
		return
	}
	id := parentId + "/" + path[0]
	for i := range *children {
		child := &(*children)[i]
		if child.Id == id {
			dashboardBuiltinNavAdd(&child.Children, path[1:], id, item)
			return
		}
	}
	child := ucx.NavItemChild{
		Id:    id,
		Label: path[0],
	}
	dashboardBuiltinNavAdd(&child.Children, path[1:], id, item)
	*children = append(*children, child)
}

func dashboardCustomNavItems(typeDefs []ResourceTypeDef) []ucx.NavItem {
	roots := map[string]*dashboardCustomNavNode{}
	var items []ucx.NavItem
	for _, def := range typeDefs {
		parts := strings.Split(def.Group, ".")
		if len(parts) < 2 {
			items = append(items, ucx.NavItem{
				Id:      def.Id,
				Label:   def.Label,
				Aliases: def.Aliases,
			})
			continue
		}
		rootDomain := strings.Join(parts[len(parts)-2:], ".")
		parts = append(parts[:len(parts)-2], rootDomain)
		children := roots
		var node *dashboardCustomNavNode
		for i := len(parts) - 1; i >= 0; i-- {
			part := parts[i]
			node = children[part]
			if node == nil {
				node = &dashboardCustomNavNode{
					label:    part,
					domain:   strings.Join(parts[i:], "."),
					children: map[string]*dashboardCustomNavNode{},
				}
				children[part] = node
			}
			children = node.children
		}
		node.resources = append(node.resources, ucx.NavItemChild{
			Id:      def.Id,
			Label:   def.Label,
			Aliases: def.Aliases,
		})
	}
	for _, root := range roots {
		child := dashboardCustomNavItem(root)
		items = append(items, ucx.NavItem{
			Id:       child.Id,
			Label:    child.Label,
			Children: child.Children,
		})
	}
	return items
}

func dashboardCustomNavItem(node *dashboardCustomNavNode) ucx.NavItemChild {
	label := node.label
	for len(node.resources) == 0 && len(node.children) == 1 {
		for _, child := range node.children {
			node = child
		}
		label = node.label + "." + label
	}
	children := append([]ucx.NavItemChild{}, node.resources...)
	for _, child := range node.children {
		children = append(children, dashboardCustomNavItem(child))
	}
	dashboardNavSort(children)
	return ucx.NavItemChild{
		Id:       "crd-group:" + node.domain,
		Label:    label,
		Children: children,
	}
}

func dashboardNavSort(children []ucx.NavItemChild) {
	sort.Slice(children, func(i, j int) bool {
		leftDirectory := len(children[i].Children) > 0
		rightDirectory := len(children[j].Children) > 0
		if leftDirectory != rightDirectory {
			return leftDirectory
		}
		if children[i].Label == children[j].Label {
			return children[i].Id < children[j].Id
		}
		return children[i].Label < children[j].Label
	})
	for _, child := range children {
		dashboardNavSort(child.Children)
	}
}
