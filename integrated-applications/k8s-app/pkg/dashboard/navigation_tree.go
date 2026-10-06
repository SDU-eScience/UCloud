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
	sort.Slice(children, func(i, j int) bool {
		return children[i].Label < children[j].Label
	})
	return ucx.NavItemChild{
		Id:       "crd-group:" + node.domain,
		Label:    label,
		Children: children,
	}
}
