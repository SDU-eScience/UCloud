package dashboard

import (
	"fmt"
	"net/url"
	"strings"

	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
)

type dashboardNavigationEntry struct {
	route        string
	baseRoute    string
	label        string
	typeId       string
	namespace    string
	filter       resourceFilter
	filterOrigin string
}

func dashboardNavigationStart(app *stackUiApp) {
	if len(app.resourceNavigation) > 0 {
		last := len(app.resourceNavigation) - 1
		entry := app.resourceNavigation[last]
		app.resourceNavigation[last] = dashboardNavigationSnapshot(app, entry.baseRoute, entry.label)
		app.resourceNavigation[last].route = entry.route
		return
	}
	route := app.browseRoute(app.ActiveType, app.ActiveFilter)
	if strings.HasPrefix(app.RoutePath, "browse/") {
		route = app.RoutePath
	}
	label := app.activeTypeLabel(app.allTypeDefs())
	app.resourceNavigation = []dashboardNavigationEntry{dashboardNavigationSnapshot(app, route, label)}
}

func dashboardNavigationSnapshot(app *stackUiApp, route string, label string) dashboardNavigationEntry {
	baseRoute, _, _ := strings.Cut(route, "?")
	return dashboardNavigationEntry{
		route:        route,
		baseRoute:    baseRoute,
		label:        label,
		typeId:       app.ActiveType,
		namespace:    app.ActiveNamespace,
		filter:       app.ActiveFilter,
		filterOrigin: app.filterOrigin,
	}
}

func dashboardNavigationPush(app *stackUiApp, route string, label string) {
	app.resourceNavigation = append(app.resourceNavigation, dashboardNavigationSnapshot(app, route, label))
	ucxsvc.RouterPushPage(app, dashboardNavigationUpdateRoutes(app))
}

func dashboardNavigationUpdateRoutes(app *stackUiApp) string {
	for i := range app.resourceNavigation {
		app.resourceNavigation[i].route = dashboardNavigationRoute(app.resourceNavigation[:i+1])
	}
	return app.resourceNavigation[len(app.resourceNavigation)-1].route
}

func dashboardNavigationSelectType(app *stackUiApp) string {
	app.resourceNavigation = []dashboardNavigationEntry{dashboardNavigationSnapshot(
		app,
		"browse/"+app.ActiveType,
		app.activeTypeLabel(app.allTypeDefs()),
	)}
	return dashboardNavigationUpdateRoutes(app)
}

func dashboardNavigationUpdateNamespace(app *stackUiApp) {
	if app.ActiveType == navHomeId || app.ResourceDetail != "" {
		return
	}
	if len(app.resourceNavigation) == 0 {
		dashboardNavigationSelectType(app)
	} else {
		dashboardNavigationStart(app)
	}
	route := dashboardNavigationUpdateRoutes(app)
	if route != app.RoutePath {
		ucxsvc.RouterPushPage(app, route)
	}
}

func dashboardNavigationRestore(app *stackUiApp) {
	app.resourceNavigation = dashboardNavigationParse(app, app.RoutePath)
	if len(app.resourceNavigation) == 0 {
		return
	}
	entry := app.resourceNavigation[len(app.resourceNavigation)-1]
	app.ActiveType = entry.typeId
	app.ActiveNamespace = entry.namespace
	app.ActiveFilter = entry.filter
	app.filterOrigin = entry.filterOrigin
	if app.poller != nil {
		app.poller.SetActiveFilter(entry.filter)
	}
}

func dashboardNavigationRoute(entries []dashboardNavigationEntry) string {
	var levels []string
	query := url.Values{}
	for i, entry := range entries {
		level := entry.baseRoute
		if i > 0 && strings.HasPrefix(level, "browse/") {
			for _, part := range strings.Split(entry.label, "/") {
				level += "/" + url.PathEscape(part)
			}
		}
		levels = append(levels, level)
		if !entry.filter.isEmpty() {
			query.Set(fmt.Sprintf("filter%d", i), entry.filter.key())
		}
		if i == 0 || entry.namespace != "" {
			query.Set(fmt.Sprintf("namespace%d", i), entry.namespace)
		}
		if strings.HasPrefix(level, "detail/") {
			query.Set(fmt.Sprintf("type%d", i), entry.typeId)
		}
	}
	route := strings.Join(levels, "/linked/")
	if len(query) > 0 {
		route += "?" + query.Encode()
	}
	return route
}

func dashboardNavigationParse(app *stackUiApp, route string) []dashboardNavigationEntry {
	path, rawQuery, _ := strings.Cut(route, "?")
	query, err := url.ParseQuery(rawQuery)
	if err != nil {
		return nil
	}
	if !strings.Contains(path, "/linked/") && !query.Has("namespace0") && !query.Has("filter0") {
		return nil
	}
	var entries []dashboardNavigationEntry
	for i, level := range strings.Split(path, "/linked/") {
		parts := strings.Split(level, "/")
		if len(parts) < 2 {
			return nil
		}
		typeId := parts[1]
		label := ""
		baseRoute := level
		switch parts[0] {
		case "browse":
			if i == 0 && len(parts) != 2 {
				return nil
			}
			def, ok := app.resolveType(typeId)
			if !ok {
				return nil
			}
			label = def.Label
			baseRoute = "browse/" + typeId
			if i > 0 {
				if len(parts) != 3 && len(parts) != 4 {
					return nil
				}
				var names []string
				for _, part := range parts[2:] {
					name, decodeErr := url.PathUnescape(part)
					if decodeErr != nil {
						return nil
					}
					names = append(names, name)
				}
				label = strings.Join(names, "/")
			}
		case "detail":
			detail := detailFromRoute(level)
			if detail == "" {
				return nil
			}
			detailParts := strings.Split(detail, "/")
			label = strings.TrimPrefix(detailParts[1]+"/"+detailParts[2], "/")
			if len(detailParts) == 4 {
				label = detailParts[3]
			}
			typeId = query.Get(fmt.Sprintf("type%d", i))
			if _, ok := app.resolveType(typeId); !ok {
				return nil
			}
		default:
			return nil
		}
		filter := resourceFilter{}
		if key := query.Get(fmt.Sprintf("filter%d", i)); key != "" {
			filter = parseResourceFilterKey(key)
		}
		origin := ""
		if i > 0 {
			origin = entries[i-1].typeId
		}
		entries = append(entries, dashboardNavigationEntry{
			baseRoute:    baseRoute,
			label:        label,
			typeId:       typeId,
			namespace:    query.Get(fmt.Sprintf("namespace%d", i)),
			filter:       filter,
			filterOrigin: origin,
		})
		entries[i].route = dashboardNavigationRoute(entries)
	}
	return entries
}

func dashboardNavigationCurrentRoute(app *stackUiApp) string {
	if len(app.resourceNavigation) == 0 {
		return app.RoutePath
	}
	entry := app.resourceNavigation[len(app.resourceNavigation)-1]
	if strings.HasPrefix(entry.baseRoute, "browse/") {
		return app.browseRoute(entry.typeId, entry.filter)
	}
	return entry.baseRoute
}

func dashboardNavigationBack(app *stackUiApp) ucx.UiNode {
	entries := app.resourceNavigation
	backTarget := entries[len(entries)-2].route
	return ucx.ButtonEx("resourceBack", "Back", ucx.ColorSecondaryMain, ucx.IconHeroArrowLeft, "", "").ButtonEscapeHint(true).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
		ucxsvc.RouterPushPage(app, backTarget)
	})
}

func dashboardNavigationBreadcrumbs(app *stackUiApp) ucx.UiNode {
	entries := app.resourceNavigation
	var children []ucx.UiNode
	displayedNamespace := ""
	for i, entry := range entries {
		if i > 0 {
			children = append(children, ucx.Text(" / "))
		}
		label := entry.label
		if i > 0 {
			namespace, name, found := strings.Cut(label, "/")
			if found {
				if namespace != displayedNamespace {
					children = append(children, ucx.Text(namespace).Sx(ucx.SxColor(ucx.ColorTextSecondary)), ucx.Text(" / "))
					displayedNamespace = namespace
				}
				label = name
			}
		}
		if i == len(entries)-1 {
			children = append(children, ucx.Text(label).Sx(ucx.SxColor(ucx.ColorTextSecondary)))
			continue
		}
		children = append(children, ucx.LinkEx(fmt.Sprintf("resourceBreadcrumb%d", i), entry.route).Children(ucx.Text(label)))
	}
	return ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 4}).Sx(
		ucx.SxAlignItemsCenter,
		ucx.SxWidthAuto(),
		ucx.SxMinWidth(0),
		ucx.SxOverflowX("auto"),
		ucx.SxWhiteSpace("nowrap"),
	).Children(children...)
}
