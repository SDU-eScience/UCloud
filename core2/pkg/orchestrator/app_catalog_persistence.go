package orchestrator

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"runtime"
	"strconv"
	"time"

	db "ucloud.dk/shared/pkg/database2"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func appCatalogLoad() {
	reset := func() {
		appCatalogGlobals.TopPicks.Items = nil
		appCatalogGlobals.Carrousel.Items = nil
		appCatalogGlobals.RecentAdditions.NewApplications = nil
		appCatalogGlobals.RecentAdditions.RecentlyUpdated = nil

		appCatalogGlobals.Buckets = make([]appCatalogBucket, runtime.NumCPU())
		for i := 0; i < len(appCatalogGlobals.Buckets); i++ {
			b := &appCatalogGlobals.Buckets[i]
			b.Applications = make(map[string][]*internalApplication)
			b.ApplicationPermissions = make(map[string][]orcapi.AclEntity)
			b.Tools = make(map[string][]*internalTool)
			b.Groups = make(map[AppGroupId]*internalAppGroup)
			b.Spotlights = make(map[AppSpotlightId]*internalSpotlight)
			b.Stars = make(map[string]*internalStars)
		}
		appCatalogGlobals.Categories.Categories = make(map[AppCategoryId]*internalCategory)
	}

	maxGroupId := int64(0)
	maxCategoryId := int64(0)
	maxSpotlightId := int64(0)

	if appCatalogGlobals.Testing.Enabled {
		reset()
	} else {
		db.NewTx0(func(tx *db.Transaction) {
			reset()

			apps := db.Select[struct {
				Name        string
				Version     string
				Application string
				CreatedAt   time.Time

				Invocation  string
				ToolName    string
				ToolVersion string

				Title       string
				Description string
				Website     sql.NullString
				FlavorName  sql.NullString
				IsPublic    bool
				GroupId     sql.NullInt64
				ModifiedAt  time.Time
			}](
				tx,
				`
					select 
						name, version, application, created_at, 
						application as invocation, tool_name, tool_version,
						title, description, website, flavor_name, is_public, group_id, modified_at
					from
						app_store.applications
					order by name, created_at
				`,
				db.Params{},
			)

			for _, app := range apps {
				b := appBucket(app.Name)
				i := &internalApplication{
					Name:      app.Name,
					Version:   app.Version,
					CreatedAt: app.CreatedAt,
					Tool: orcapi.NameAndVersion{
						Name:    app.ToolName,
						Version: app.ToolVersion,
					},
					Title:             app.Title,
					Description:       app.Description,
					DocumentationSite: util.SqlNullStringToOpt(app.Website),
					FlavorName:        util.SqlNullStringToOpt(app.FlavorName),
					Public:            app.IsPublic,
					ModifiedAt:        app.ModifiedAt,
				}
				if app.GroupId.Valid {
					i.Group.Set(AppGroupId(app.GroupId.Int64))
				}

				if err := json.Unmarshal([]byte(app.Invocation), &i.Invocation); err != nil {
					panic(fmt.Sprintf("Could not load application: %s %s", app.Name, app.Version))
				}
				b.Applications[app.Name] = append(b.Applications[app.Name], i)
			}

			appsByCreatedAt := db.Select[struct {
				Name    string
				Version string
			}](
				tx,
				`
					select name, version
					from app_store.applications
					order by created_at
			    `,
				db.Params{},
			)

			for _, nv := range appsByCreatedAt {
				appStudioTrackUpdate(orcapi.NameAndVersion{Name: nv.Name, Version: nv.Version})
			}

			tools := db.Select[struct {
				Name    string
				Version string
				Tool    string
			}](
				tx,
				`
					select name, version, tool
					from app_store.tools
				`,
				db.Params{},
			)

			for _, tool := range tools {
				b := appBucket(tool.Name)

				t := &internalTool{
					Name:    tool.Name,
					Version: tool.Version,
				}

				if err := json.Unmarshal([]byte(tool.Tool), &t.Tool); err != nil {
					panic(fmt.Sprintf("Could not load tool: %s %s", tool.Name, tool.Version))
				}

				b.Tools[tool.Name] = append(b.Tools[tool.Name], t)
			}

			appPermissions := db.Select[struct {
				ApplicationName string
				Username        string
				Project         string
				ProjectGroup    string
			}](
				tx,
				`
					select application_name, username, project, project_group
					from app_store.permissions
					order by application_name
				`,
				db.Params{},
			)

			for _, perm := range appPermissions {
				b := appBucket(perm.ApplicationName)
				p := orcapi.AclEntity{}
				if perm.Username != "" {
					p.Type = orcapi.AclEntityTypeUser
					p.Username = perm.Username
				} else if perm.Project != "" && perm.ProjectGroup != "" {
					p.Type = orcapi.AclEntityTypeProjectGroup
					p.ProjectId = perm.Project
					p.Group = perm.ProjectGroup
				} else {
					continue
				}

				b.ApplicationPermissions[perm.ApplicationName] = append(b.ApplicationPermissions[perm.ApplicationName], p)
			}

			groups := db.Select[struct {
				Id             int
				Title          string
				Description    string
				Logo           []byte
				LogoHasText    bool
				DefaultName    sql.NullString
				ColorRemapping sql.NullString
			}](
				tx,
				`
					select
						id, title, coalesce(description, '') as description, coalesce(logo, E'\\x') as logo, logo_has_text,
						default_name, color_remapping
					from
						app_store.application_groups
					order by id asc
				`,
				db.Params{},
			)

			for _, group := range groups {
				id := AppGroupId(group.Id)
				maxGroupId = int64(group.Id)
				b := appGroupBucket(id)
				appGroup := internalAppGroup{
					Title:       group.Title,
					Description: group.Description,
					Logo:        group.Logo,
					LogoHasText: group.LogoHasText,
					DefaultName: util.SqlNullStringToOpt(group.DefaultName).GetOrDefault(""),
				}

				if group.ColorRemapping.Valid {
					var mapping struct {
						Dark  map[int]int `json:"dark"`
						Light map[int]int `json:"light"`
					}
					if err := json.Unmarshal([]byte(group.ColorRemapping.String), &mapping); err == nil {
						appGroup.ColorRemappingLight = mapping.Light
						appGroup.ColorRemappingDark = mapping.Dark
					}
				}

				if appGroup.ColorRemappingLight == nil {
					appGroup.ColorRemappingLight = make(map[int]int)
				}

				if appGroup.ColorRemappingDark == nil {
					appGroup.ColorRemappingDark = make(map[int]int)
				}

				b.Groups[id] = &appGroup
				appStudioTrackNewGroup(id)
			}

			categories := db.Select[struct {
				Id       int
				Title    string
				Priority int
			}](
				tx,
				`
					select
						id, tag as title, priority
					from
						app_store.categories
					order by
						priority
				`,
				db.Params{},
			)

			for _, cat := range categories {
				id := AppCategoryId(cat.Id)
				maxCategoryId = int64(cat.Id)
				c := &appCatalogGlobals.Categories
				c.Categories[id] = &internalCategory{
					Id:       id,
					Title:    cat.Title,
					Priority: cat.Priority,
				}
			}

			categoryItems := db.Select[struct {
				GroupId    int
				CategoryId int
			}](
				tx,
				`
					select group_id, tag_id as category_id
					from app_store.category_items
					order by tag_id
				`,
				db.Params{},
			)

			for _, item := range categoryItems {
				catId := AppCategoryId(item.CategoryId)
				groupId := AppGroupId(item.GroupId)

				c := &appCatalogGlobals.Categories
				c.Categories[catId].Items = append(c.Categories[catId].Items, groupId)
			}

			spotlights := db.Select[struct {
				Id          int
				Title       string
				Description string
				Active      bool
			}](
				tx,
				`
					select id, title, description, active
					from app_store.spotlights
				`,
				db.Params{},
			)

			appCatalogGlobals.ActiveSpotlight.Store(-1)

			for _, spotlight := range spotlights {
				id := AppSpotlightId(spotlight.Id)
				maxSpotlightId = int64(spotlight.Id)
				b := appSpotlightBucket(id)
				b.Spotlights[id] = &internalSpotlight{
					Title:       spotlight.Title,
					Description: spotlight.Description,
				}

				if spotlight.Active {
					appCatalogGlobals.ActiveSpotlight.Store(int64(spotlight.Id))
				}
			}

			spotlightItems := db.Select[struct {
				SpotlightId int
				GroupId     int
			}](
				tx,
				`
					select spotlight_id, group_id
					from app_store.spotlight_items
					where group_id is not null
					order by spotlight_id, priority
				`,
				db.Params{},
			)

			for _, item := range spotlightItems {
				spotlightId := AppSpotlightId(item.SpotlightId)
				groupId := AppGroupId(item.GroupId)

				b := appSpotlightBucket(spotlightId)
				b.Spotlights[spotlightId].Items = append(b.Spotlights[spotlightId].Items, groupId)
			}

			carrouselItems := db.Select[struct {
				Title             string
				Body              string
				LinkedApplication sql.NullString
				LinkedGroup       sql.NullInt64
				LinkedWebPage     sql.NullString
				Image             []byte
			}](
				tx,
				`
					select title, body, linked_application, linked_group, linked_web_page, image
					from app_store.carrousel_items
					order by priority
				`,
				db.Params{},
			)

			for _, item := range carrouselItems {
				c := &appCatalogGlobals.Carrousel
				ci := appCarrouselItem{
					Title: item.Title,
					Body:  item.Body,
					Image: item.Image,
				}

				if item.LinkedWebPage.Valid {
					ci.LinkId = item.LinkedWebPage.String
					ci.LinkType = appCarrouselWebPage
				} else if item.LinkedApplication.Valid {
					ci.LinkId = item.LinkedApplication.String
					ci.LinkType = appCarrouselApplication
				} else if item.LinkedGroup.Valid {
					ci.LinkId = fmt.Sprint(item.LinkedGroup.Int64)
					ci.LinkType = appCarrouselGroup
				}

				c.Items = append(c.Items, ci)
			}

			topPicks := db.Select[struct{ GroupId int }](
				tx,
				`
					select group_id
					from app_store.top_picks
					where group_id is not null
					order by priority
				`,
				db.Params{},
			)

			for _, pick := range topPicks {
				appCatalogGlobals.TopPicks.Items = append(appCatalogGlobals.TopPicks.Items, AppGroupId(pick.GroupId))
			}

			stars := db.Select[struct {
				TheUser         string
				ApplicationName string
			}](
				tx,
				`
					select the_user, application_name
					from app_store.favorited_by
					order by the_user
			    `,
				db.Params{},
			)

			for _, star := range stars {
				b := appBucket(star.TheUser)
				s, ok := b.Stars[star.TheUser]
				if !ok {
					s = &internalStars{Applications: make(map[string]util.Empty)}
					b.Stars[star.TheUser] = s
				}
				s.Applications[star.ApplicationName] = util.Empty{}
			}
		})

		// Updating global ID counters so we do not override on new creations after restart
		appCatalogGlobals.GroupIdAcc.Add(maxGroupId)
		appCatalogGlobals.CategoryIdAcc.Add(maxCategoryId)
		appCatalogGlobals.SpotlightIdAcc.Add(maxSpotlightId)

		// Indexing
		// ---------------------------------------------------------------------------------------------------------
		for i := 0; i < len(appCatalogGlobals.Buckets); i++ {
			b := &appCatalogGlobals.Buckets[i]
			for name, allVersions := range b.Applications {
				latest := allVersions[len(allVersions)-1]
				if g := latest.Group; g.Present {
					gb := appGroupBucket(g.Value)
					gb.Groups[g.Value].Items = util.AppendUnique(gb.Groups[g.Value].Items, name)
				}
			}

			for id, _ := range b.Groups {
				g, _, ok := AppRetrieveGroup(rpc.ActorSystem, id, AppDiscovery{Mode: orcapi.CatalogDiscoveryModeAll},
					AppCatalogIncludeApps)

				if ok {
					appAddToIndex(id, g)
				}
			}
		}

		cats := &appCatalogGlobals.Categories
		for _, category := range cats.Categories {
			for _, item := range category.Items {
				g, ok := appRetrieveGroup(item)
				if ok {
					g.Categories = append(g.Categories, category.Id)
				}
			}
		}
	}
}

func appPersistStars(actor rpc.Actor, s *internalStars) {
	if appCatalogGlobals.Testing.Enabled {
		return
	}

	s.Mu.RLock()
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from app_store.favorited_by
				where the_user = :user
		    `,
			db.Params{
				"user": actor.Username,
			},
		)

		var apps []string
		for a, _ := range s.Applications {
			apps = append(apps, a)
		}

		if len(apps) > 0 {
			db.Exec(
				tx,
				`
					insert into app_store.favorited_by(the_user, application_name) 
					select :user, unnest(cast(:apps as text[]))
				`,
				db.Params{
					"user": actor.Username,
					"apps": apps,
				},
			)
		}
	})
	s.Mu.RUnlock()
}

func appPersistPublic(app *internalApplication) {
	if appCatalogGlobals.Testing.Enabled {
		return
	}

	app.Mu.RLock()
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update app_store.applications
				set is_public = :public
				where
					name = :name
					and version = :version
		    `,
			db.Params{
				"public":  app.Public,
				"name":    app.Name,
				"version": app.Version,
			},
		)
	})
	app.Mu.RUnlock()
}

func appPersistFlavor(name string, flavor util.Option[string]) {
	if appCatalogGlobals.Testing.Enabled {
		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update app_store.applications
				set flavor_name = :flavor
				where
					name = :name
		    `,
			db.Params{
				"flavor": util.OptSqlStringIfNotEmpty(flavor.GetOrDefault("")),
				"name":   name,
			},
		)
	})
}

func appPersistGroupMetadata(id AppGroupId, group *internalAppGroup) *util.HttpError {
	if appCatalogGlobals.Testing.Enabled {
		return nil
	}

	group.Mu.RLock()
	err := db.NewTx(func(tx *db.Transaction) *util.HttpError {

		type Exists struct {
			Exists bool
		}

		// Henrik: Updates are no longer allowed if Figlet -> figlet, but I would say this is better
		// than allowing two groups called figlet and Figlet
		queryResponse, _ := db.Get[Exists](
			tx,
			`
				select exists(
					select 1 
					from app_store.application_groups
					where lower(title) = lower(:title) 
				)
			`,
			db.Params{
				"title": group.Title,
			},
		)

		if queryResponse.Exists {
			// Henrik: Accepts that the accGroupId has been increased instead of attempting to lower it.
			return util.HttpErr(http.StatusBadRequest, "Group with title %s already exists", group.Title)
		}
		db.Exec(
			tx,
			`
				insert into app_store.application_groups(id, title, logo, description, default_name, logo_has_text, color_remapping, curator) 
				values (:id, :title, null, :description, case when :flavor = '' then null else :flavor end, :logo_has_text, null, 'main')
				on conflict (id) do update set
				    title = excluded.title,
				    description = excluded.description,
				    default_name = excluded.default_name,
					logo_has_text = excluded.logo_has_text
		    `,
			db.Params{
				"id":            id,
				"title":         group.Title,
				"description":   group.Description,
				"flavor":        group.DefaultName,
				"logo_has_text": group.LogoHasText,
			},
		)
		return nil
	})
	group.Mu.RUnlock()
	return err
}

func appPersistGroupLogo(id AppGroupId, group *internalAppGroup) {
	if appCatalogGlobals.Testing.Enabled {
		return
	}

	group.Mu.RLock()
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update app_store.application_groups
				set logo = :logo
				where id = :id
		    `,
			db.Params{
				"id":   id,
				"logo": group.Logo,
			},
		)
	})
	group.Mu.RUnlock()
}

func appPersistUpdateGroupAssignment(name string, id util.Option[AppGroupId]) {
	if appCatalogGlobals.Testing.Enabled {
		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update app_store.applications
				set
					group_id = cast(case when :group = -1 then null else :group end as int)
				where
					name = :name
		    `,
			db.Params{
				"name":  name,
				"group": id.GetOrDefault(-1),
			},
		)
	})
}

func appPersistCategoryItems(category *internalCategory) {
	if appCatalogGlobals.Testing.Enabled {
		return
	}

	category.Mu.RLock()
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from app_store.category_items
				where tag_id = :category
		    `,
			db.Params{
				"category": category.Id,
			},
		)

		db.Exec(
			tx,
			`
				insert into app_store.category_items(group_id, tag_id) 
				select unnest(cast(:groups as int[])), :category
				on conflict do nothing 
		    `,
			db.Params{
				"category": category.Id,
				"groups":   category.Items,
			},
		)
	})
	category.Mu.RUnlock()
}

func appPersistCategoryMetadata(category *internalCategory) {
	if appCatalogGlobals.Testing.Enabled {
		return
	}

	category.Mu.RLock()
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into app_store.categories(id, tag, priority, curator)
				values (:id, :title, :priority, 'main')
				on conflict (id) do update set tag = excluded.tag, priority = excluded.priority
		    `,
			db.Params{
				"id":       category.Id,
				"title":    category.Title,
				"priority": category.Priority,
			},
		)
	})
	category.Mu.RUnlock()
}

func appPersistDeleteCategory(id AppCategoryId) {
	if appCatalogGlobals.Testing.Enabled {
		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from app_store.categories where id = :id
		    `,
			db.Params{
				"id": id,
			},
		)
	})
}

func appPersistAcl(name string, list []orcapi.AclEntity) {
	if appCatalogGlobals.Testing.Enabled {
		return
	}

	var users []string
	var projects []string
	var groups []string

	for _, item := range list {
		users = append(users, item.Username)
		projects = append(projects, item.ProjectId)
		groups = append(groups, item.Group)
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`delete from app_store.permissions where application_name = :app`,
			db.Params{
				"app": name,
			},
		)

		if len(users) > 0 {
			db.Exec(
				tx,
				`
					insert into app_store.permissions(application_name, permission, username, project, project_group) 
					select :app, 'LAUNCH', unnest(cast(:users as text[])), unnest(cast(:projects as text[])),
						unnest(cast(:groups as text[]))
				`,
				db.Params{
					"app":      name,
					"users":    users,
					"projects": projects,
					"groups":   groups,
				},
			)
		}
	})
}

func appPersistGroupDeletion(id AppGroupId) {
	if appCatalogGlobals.Testing.Enabled {
		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update app_store.applications
				set group_id = null
				where group_id = :id
		    `,
			db.Params{
				"id": id,
			},
		)

		db.Exec(
			tx,
			`
				delete from app_store.application_groups
				where id = :id
		    `,
			db.Params{
				"id": id,
			},
		)
	})
}

func appPersistSpotlight(id AppSpotlightId, spotlight *internalSpotlight) {
	if appCatalogGlobals.Testing.Enabled {
		return
	}

	spotlight.Mu.RLock()
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from app_store.spotlight_items
				where spotlight_id = :id
		    `,
			db.Params{
				"id": id,
			},
		)

		db.Exec(
			tx,
			`
				insert into app_store.spotlights(id, title, description, active)
				values (:id, :title, :description, false)
				on conflict (id) do update set
					title = excluded.title,
					description = excluded.description
		    `,
			db.Params{
				"id":          id,
				"title":       spotlight.Title,
				"description": spotlight.Description,
			},
		)

		var groups []int
		var priorities []int
		for i, item := range spotlight.Items {
			groups = append(groups, int(item))
			priorities = append(priorities, i)
		}

		if len(groups) > 0 {
			db.Exec(
				tx,
				`
					insert into app_store.spotlight_items(spotlight_id, application_name, group_id, description, priority) 
					select :id, null, unnest(cast(:groups as int[])), '', unnest(cast(:priorities as int[]))
				`,
				db.Params{
					"id":         id,
					"groups":     groups,
					"priorities": priorities,
				},
			)
		}

	})
	spotlight.Mu.RUnlock()
}

func appPersistDeleteSpotlight(id AppSpotlightId) {
	if appCatalogGlobals.Testing.Enabled {
		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from app_store.spotlight_items
				where spotlight_id = :id
		    `,
			db.Params{
				"id": id,
			},
		)

		db.Exec(
			tx,
			`
				delete from app_store.spotlights
				where id = :id
		    `,
			db.Params{
				"id": id,
			},
		)
	})
}

func appPersistSetActiveSpotlight(id AppSpotlightId) {
	if appCatalogGlobals.Testing.Enabled {
		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update app_store.spotlights
				set active = false
				where active = true
		    `,
			db.Params{},
		)

		db.Exec(
			tx,
			`
				update app_store.spotlights
				set active = true
				where id = :id
		    `,
			db.Params{
				"id": id,
			},
		)
	})
}

func appPersistTopPicks(picks []AppGroupId) {
	if appCatalogGlobals.Testing.Enabled {
		return
	}

	var groups []int
	var priorities []int
	for i, pick := range picks {
		groups = append(groups, int(pick))
		priorities = append(priorities, i)
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from app_store.top_picks
				where true
		    `,
			db.Params{},
		)

		if len(groups) > 0 {
			db.Exec(
				tx,
				`
					insert into app_store.top_picks(application_name, group_id, description, priority) 
					select null, unnest(cast(:groups as int[])), '', unnest(cast(:priorities as int[]))
				`,
				db.Params{
					"groups":     groups,
					"priorities": priorities,
				},
			)
		}
	})
	// TODO Stuff like this could technically cause a crash if it is deleted before persistence runs.
	//   Might want to verify in here by joining the table.
}

func appPersistCarrouselSlides() {
	if appCatalogGlobals.Testing.Enabled {
		return
	}

	c := &appCatalogGlobals.Carrousel
	c.Mu.RLock()
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from app_store.carrousel_items where priority >= :length
		    `,
			db.Params{
				"length": len(c.Items),
			},
		)

		var title []string
		var body []string
		var linkedApplication []string
		var linkedGroup []int
		var linkedWebPage []string
		var priority []int

		for i, slide := range c.Items {
			title = append(title, slide.Title)
			body = append(body, slide.Body)

			lApp := ""
			lGroup := 0
			lPage := ""
			if slide.LinkType == appCarrouselWebPage {
				lPage = slide.LinkId
			} else if slide.LinkType == appCarrouselApplication {
				lApp = slide.LinkId
			} else if slide.LinkType == appCarrouselGroup {
				g, _ := strconv.ParseInt(slide.LinkId, 10, 64)
				lGroup = int(g)
			}

			linkedApplication = append(linkedApplication, lApp)
			linkedGroup = append(linkedGroup, lGroup)
			linkedWebPage = append(linkedWebPage, lPage)
			priority = append(priority, i)
		}

		if len(title) > 0 {
			db.Exec(
				tx,
				`
					with data as (
						select 
							unnest(cast(:title as text[])) as title,
							unnest(cast(:body as text[])) as body,
							unnest(cast(:linked_application as text[])) as linked_application,
							unnest(cast(:linked_group as int[])) as linked_group, 
							unnest(cast(:linked_web_page as text[])) as linked_web_page,
							unnest(cast(:priority as int[])) as priority
					)
					insert into app_store.carrousel_items
						(title, body, image_credit, linked_application, linked_group, linked_web_page, image, priority) 
					select 
						title,
						body,
						'', 
						case when linked_application = '' then null else linked_application end,
						case when linked_group = 0 then null else linked_group end,
						case when linked_web_page = '' then null else linked_web_page end,
						E'\\x',
						priority
					from
						data
					on conflict (priority) do update set
						title = excluded.title,
						body = excluded.body,
						linked_application = excluded.linked_application,
						linked_group = excluded.linked_group,
						linked_web_page = excluded.linked_web_page
				`,
				db.Params{
					"title":              title,
					"body":               body,
					"linked_application": linkedApplication,
					"linked_group":       linkedGroup,
					"linked_web_page":    linkedWebPage,
					"priority":           priority,
				},
			)
		}
	})
	c.Mu.RUnlock()
}

func appPersistCarrouselSlideImage(index int, resized []byte) {
	if appCatalogGlobals.Testing.Enabled {
		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update app_store.carrousel_items
				set image = :bytes
				where priority = :index
		    `,
			db.Params{
				"index": index,
				"bytes": resized,
			},
		)
	})
}

func appPersistApplication(app *internalApplication) {
	if appCatalogGlobals.Testing.Enabled {
		return
	}

	app.Mu.RLock()
	db.NewTx0(func(tx *db.Transaction) {
		appJson, _ := json.Marshal(app.Invocation)

		db.Exec(
			tx,
			`
				insert into app_store.applications
					(name, version, application, created_at, modified_at, original_document, owner, 
						tool_name, tool_version, authors, title, description, website, group_id, flavor_name) 
				values (:name, :version, :app, :created_at, :modified_at, '{}', '_ucloud', 
					:tool_name, :tool_version, '["Unknown"]', :title, :description, :website, 
					cast(case when :group_id = 0 then null else :group_id end as int), :flavor_name)
		    `,
			db.Params{
				"name":         app.Name,
				"version":      app.Version,
				"app":          string(appJson),
				"created_at":   app.CreatedAt,
				"modified_at":  app.ModifiedAt,
				"tool_name":    app.Invocation.Tool.Name,
				"tool_version": app.Invocation.Tool.Version,
				"title":        app.Title,
				"description":  app.Description,
				"website":      app.DocumentationSite.Sql(),
				"group_id":     int(app.Group.GetOrDefault(0)),
				"flavor_name":  app.FlavorName.Sql(),
			},
		)
	})
	app.Mu.RUnlock()
}

func appToolPersist(tool *internalTool) {
	if appCatalogGlobals.Testing.Enabled {
		return
	}

	tool.Mu.RLock()
	db.NewTx0(func(tx *db.Transaction) {
		toolJson, _ := json.Marshal(tool.Tool)

		db.Exec(
			tx,
			`
insert into app_store.tools(name, version, created_at, modified_at, original_document, owner, tool) 
values (:name, :version, now(), now(), '{}', '_ucloud', :tool)
		    `,
			db.Params{
				"name":    tool.Name,
				"version": tool.Version,
				"tool":    string(toolJson),
			},
		)
	})
	tool.Mu.RUnlock()
}
