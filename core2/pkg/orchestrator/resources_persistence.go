package orchestrator

import (
	"database/sql"
	"slices"
	"strings"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/util"
)

type resourceLoadRow struct {
	Id                  int64
	CreatedAt           time.Time
	CreatedBy           sql.NullString
	Project             sql.NullString
	ProductName         sql.NullString
	ProductCategory     sql.NullString
	Provider            sql.NullString
	ProviderGeneratedId sql.NullString
}

func resourceLoadInternal(tx *db.Transaction, typeName string, rows []resourceLoadRow) map[ResourceId]*resource {
	foundResources := map[ResourceId]*resource{}
	var foundResourceIds []int64
	for _, row := range rows {
		r := &resource{
			Id:         ResourceId(row.Id),
			ProviderId: util.SqlNullStringToOpt(row.ProviderGeneratedId),
			Owner: orcapi.ResourceOwner{
				CreatedBy: util.SqlNullStringToOpt(row.CreatedBy).GetOrDefault("_ucloud"),
				Project:   util.SqlNullStringToOpt(row.Project).GetOrDefault(""),
			},
			CreatedAt:  row.CreatedAt,
			ModifiedAt: row.CreatedAt, // NOTE(Dan): We don't store this! That seems silly.
			Type:       typeName,
			Confirmed:  true,
		}

		if row.ProductName.Valid && row.ProductCategory.Valid && row.Provider.Valid {
			r.Product = util.OptValue(accapi.ProductReference{
				Id:       row.ProductName.String,
				Category: row.ProductCategory.String,
				Provider: row.Provider.String,
			})
		}

		_, exists := foundResources[r.Id]
		foundResources[r.Id] = r
		if !exists {
			foundResourceIds = append(foundResourceIds, int64(r.Id))
		}
	}

	if len(foundResources) > 0 {
		aclRows := db.Select[struct {
			ResourceId   int64
			Username     sql.NullString
			GroupId      sql.NullString
			GroupProject sql.NullString
			Permission   string
		}](
			tx,
			`
				select e.resource_id, e.username, g.id as group_id, g.project as group_project, e.permission
				from
					provider.resource_acl_entry e
					left join project.groups g on e.group_id = g.id
				where
					e.resource_id = some(cast(:ids as int8[]))
			`,
			db.Params{
				"ids": foundResourceIds,
			},
		)

		for _, row := range aclRows {
			r, ok := foundResources[ResourceId(row.ResourceId)]
			if !ok {
				continue
			}

			entry := orcapi.ResourceAclEntry{
				Permissions: []orcapi.Permission{orcapi.Permission(row.Permission)},
				Entity: orcapi.AclEntity{
					Group:     row.GroupId.String,
					ProjectId: row.GroupProject.String,
					Username:  row.Username.String,
				},
			}
			if entry.Entity.Username != "" {
				entry.Entity.Type = orcapi.AclEntityTypeUser
			} else {
				entry.Entity.Type = orcapi.AclEntityTypeProjectGroup
			}

			found := false
			for i := 0; i < len(r.Acl); i++ {
				if r.Acl[i].Entity == entry.Entity {
					r.Acl[i].Permissions = append(r.Acl[i].Permissions, orcapi.Permission(row.Permission))
					found = true
					break
				}
			}
			if !found {
				r.Acl = append(r.Acl, entry)
			}
		}
	}

	g := resourceGetGlobals(typeName)
	g.OnLoad(tx, foundResourceIds, foundResources)

	for key, resc := range foundResources {
		if resc.Extra == nil {
			delete(foundResources, key)
		}
	}

	return foundResources
}

func resourceLoad(typeName string, id ResourceId, prefetchHint []ResourceId) {
	if resourceGlobals.Testing.Enabled {
		return
	}

	resourceLoadCacheMiss.WithLabelValues(typeName).Inc()
	var toFetch []int64 // annoying transformation needed to help sql layers below transmit the data
	{
		found := false
		for _, resc := range prefetchHint {
			toFetch = append(toFetch, int64(resc))
			if resc == id {
				found = true
			}
		}

		if !found {
			toFetch = append(toFetch, int64(id))
		}
	}

	resources := db.NewTx(func(tx *db.Transaction) map[ResourceId]*resource {
		rows := db.Select[resourceLoadRow](
			tx,
			`
				select 
					r.id,
					created_at, 
					created_by, 
					project, 
					p.name as product_name, 
					pc.category as product_category, 
					coalesce(r.provider, pc.provider) as provider, 
					provider_generated_id
				from
					provider.resource r
					left join accounting.products p on r.product = p.id
					left join accounting.product_categories pc on p.category = pc.id
				where
					r.id = some(cast(:ids as int8[]))
					and r.confirmed_by_provider = true
					and r.type = :type
			`,
			db.Params{
				"ids":  toFetch,
				"type": typeName,
			},
		)

		return resourceLoadInternal(tx, typeName, rows)
	})

	_, ok := resources[id]
	if !ok {
		log.Warn("Did not find: %v when missing cache!", id)
	}

	for _, r := range resources {
		b := resourceGetBucket(typeName, r.Id)
		b.Mu.Lock()
		if _, exists := b.Resources[r.Id]; !exists {
			b.Resources[r.Id] = r
		}
		b.Mu.Unlock()
	}
}

func lResourcePersist(r *resource) {
	if resourceGlobals.Testing.Enabled {
		return
	}

	g := resourceGetGlobals(r.Type)
	db.NewTx0(func(tx *db.Transaction) {
		b := db.BatchNew(tx)

		if r.MarkedForDeletion {
			g.OnPersist(b, r)

			params := db.Params{
				"id": r.Id,
			}

			db.BatchExec(
				b,
				`
					delete from provider.resource_acl_entry
					where resource_id = :id
			    `,
				params,
			)

			// NOTE(Dan): Shouldn't technically be needed, but for backward-compatibility reasons we put this here
			// to be certain that updates are definitely deleted.
			db.BatchExec(
				b,
				`
					delete from provider.resource_update
					where resource = :id
			    `,
				params,
			)

			db.BatchExec(
				b,
				`
					delete from provider.resource
					where id = :id
			    `,
				params,
			)
		} else {
			product := r.Product.GetOrDefault(accapi.ProductReference{
				Id:       "_ucloud",
				Category: "_ucloud",
				Provider: "_ucloud",
			})

			db.BatchExec(
				b,
				`
					with products as (
						select (
							select p.id
							from
								accounting.products p
								join accounting.product_categories pc on p.category = pc.id
							where
								p.name = :name
								and pc.category = :product_category
								and pc.provider = :provider
							limit 1
						) as product_id
					)
					insert into provider.resource(type, provider, created_at, created_by, project, id, product, 
						provider_generated_id, confirmed_by_provider, public_read) 
					select :type, case when :provider = '_ucloud' then null else :provider end, now(), :created_by, 
						:project, :id, product_id, :provider_id, true, false
					from products
					on conflict (id) do update set
						provider_generated_id = excluded.provider_generated_id
			    `,
				db.Params{
					"type":             r.Type,
					"provider":         product.Provider,
					"name":             product.Id,
					"product_category": product.Category,
					"created_by":       r.Owner.CreatedBy,
					"project":          util.OptSqlStringIfNotEmpty(r.Owner.Project),
					"id":               r.Id,
					"provider_id":      r.ProviderId.Sql(),
				},
			)

			db.BatchExec(
				b,
				`
					delete from provider.resource_acl_entry
					where resource_id = :id
			    `,
				db.Params{
					"id": r.Id,
				},
			)

			if len(r.Acl) > 0 {
				var groupIds []string
				var usernames []string
				var permissions []string

				for _, entry := range r.Acl {
					for _, perm := range entry.Permissions {
						groupIds = append(groupIds, entry.Entity.Group)
						usernames = append(usernames, entry.Entity.Username)
						permissions = append(permissions, string(perm))
					}
				}

				db.BatchExec(
					b,
					`
						with
							raw_entries as (
								select
									unnest(cast(:group_ids as text[])) group_id,
									unnest(cast(:usernames as text[])) username,
									unnest(cast(:permissions as text[])) permission
							),
							entries as (
								select
									case
										when group_id = '' then null
										else group_id
									end as group_id,
									case
										when username = '' then null
										else username
									end as username,
									permission
								from raw_entries
							)
						insert into provider.resource_acl_entry(group_id, username, permission, resource_id) 
						select group_id, username, permission, :id
						from entries
					`,
					db.Params{
						"group_ids":   groupIds,
						"usernames":   usernames,
						"permissions": permissions,
						"id":          r.Id,
					},
				)
			}

			g.OnPersist(b, r)
		}

		db.BatchSend(b)
	})

	if fn := g.OnPersistCommitted; fn != nil {
		fn(r)
	}
}

func resourceLoadIndex(b *resourceIndexBucket, typeName string, reference string) {
	ref := reference
	providerId, isProvider := strings.CutPrefix(reference, fndapi.ProviderSubjectPrefix)
	if isProvider {
		ref = providerId
	}

	resources := db.NewTx(func(tx *db.Transaction) map[ResourceId]*resource {
		tx.NoDevResetThisIsNotAHackIPromise = true
		rows := db.Select[resourceLoadRow](
			tx,
			`
				select
					r.id,
					created_at,
					created_by,
					project,
					p.name as product_name,
					pc.category as product_category,
					coalesce(r.provider, pc.provider) as provider,
					provider_generated_id
				from
					provider.resource r
					left join accounting.products p on r.product = p.id
					left join accounting.product_categories pc on p.category = pc.id
					left join provider.resource_acl_entry acl on 
						r.id = acl.resource_id 
						and acl.username = :reference 
						and r.project is null
				where
					(
						(r.created_by = :reference and r.project is null and pc.provider is distinct from :reference)
						or (r.project = :reference and r.created_by != :reference and pc.provider is distinct from :reference)
						or (pc.provider is not distinct from :reference and r.created_by is distinct from :reference and r.project is distinct from :reference)
						or (acl.username = :reference)
					)
					and r.type = :type
					and r.confirmed_by_provider
				order by r.id
		    `,
			db.Params{
				"reference": ref,
				"type":      typeName,
			},
		)

		return resourceLoadInternal(tx, typeName, rows)
	})

	var ids []ResourceId
	for id := range resources {
		ids = append(ids, id)
	}
	slices.Sort(ids)

	{
		b.Mu.Lock()
		if _, exists := b.ByOwner[reference]; !exists {
			b.ByOwner[reference] = ids
		}
		b.Mu.Unlock()
	}

	for _, r := range resources {
		b := resourceGetBucket(typeName, r.Id)
		b.Mu.Lock()
		if _, exists := b.Resources[r.Id]; !exists {
			b.Resources[r.Id] = r
		}
		b.Mu.Unlock()
	}
}

func resourceLoadProvider(providerId string) *resourceProvider {
	if resourceGlobals.Testing.Enabled {
		resourceGlobals.Mu.Lock()
		result, exists := resourceGlobals.Providers[providerId]
		if !exists {
			result = &resourceProvider{
				ProviderIds: map[string]ResourceId{},
			}
			resourceGlobals.Providers[providerId] = result
		}
		resourceGlobals.Mu.Unlock()
		return result
	} else {
		ids := db.NewTx(func(tx *db.Transaction) map[string]ResourceId {
			result := map[string]ResourceId{}
			rows := db.Select[struct {
				Id                  int
				ProviderGeneratedId string
			}](
				tx,
				`
					select r.id, r.provider_generated_id
					from
						provider.resource r
						join accounting.products p on r.product = p.id
						join accounting.product_categories pc on p.category = pc.id
					where
						r.provider_generated_id is not null
						and pc.provider = :provider_id
			    `,
				db.Params{
					"provider_id": providerId,
				},
			)

			for _, row := range rows {
				result[row.ProviderGeneratedId] = ResourceId(row.Id)
			}

			return result
		})

		resourceGlobals.Mu.Lock()
		result, exists := resourceGlobals.Providers[providerId]
		if !exists {
			result = &resourceProvider{
				ProviderIds: ids,
			}
			resourceGlobals.Providers[providerId] = result
		}
		resourceGlobals.Mu.Unlock()
		return result
	}
}
