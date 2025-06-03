package k8s

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"ucloud.dk/pkg/cli"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/migrations"
	"ucloud.dk/pkg/termio"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/util"
)

func HandleDataImport(args []string) {
	// NOTE(Dan): This is called without configuration having been parsed and without any services being initialized.

	if len(args) != 1 {
		cli.HandleError("", fmt.Errorf("usage: ucloud im2-k8s-import <DIR>"))
		return
	}

	if !cfg.Parse(cfg.ServerModeServer, "/etc/ucloud") {
		return
	}

	dbConfig := &cfg.Server.Database
	db.Database = db.Connect(
		dbConfig.Username,
		dbConfig.Password,
		dbConfig.Host.Address,
		dbConfig.Host.Port,
		dbConfig.Database,
		dbConfig.Ssl,
	)

	db.Database.Connection.MapperFunc(util.ToSnakeCase)
	migrations.Init()
	db.Migrate()

	dataDir := args[0]
	im1Licenses := dataImportReadJsonl(dataDir, "licenses.jsonl")
	im1Ips := dataImportReadJsonl(dataDir, "ips.jsonl")
	coreAllocations := dataImportReadPrefixed(dataDir, "allocations-")
	coreDrives := dataImportReadJsonl(dataDir, "tracked_drives.jsonl")
	coreIngress := dataImportReadJsonl(dataDir, "tracked_ingress.jsonl")
	coreIps := dataImportReadJsonl(dataDir, "tracked_ips.jsonl")
	coreLicenses := dataImportReadJsonl(dataDir, "tracked_licenses.jsonl")
	coreProjects := dataImportReadJsonl(dataDir, "tracked_projects.jsonl")

	db.NewTx0(func(tx *db.Transaction) {
		{
			licenseServers := util.ChunkBy(im1Licenses, 100)
			for i, chunk := range licenseServers {
				termio.WriteLine("License servers: %d", i*100)

				var names []string
				var addresses []sql.NullString
				var ports []int
				var licenses []sql.NullString

				for _, item := range chunk {
					names = append(names, item[0].Value)
					addresses = append(addresses, dataImportNullString(item[1]))
					ports = append(ports, dataImportNullInt(item[2]))
					licenses = append(licenses, dataImportNullString(item[3]))
				}

				db.Exec(
					tx,
					`
						insert into licenses(name, address, port, license) 
						select 
							unnest(cast(:names as text[])), 
							unnest(cast(:addresses as text[])), 
							unnest(cast(:ports as integer[])), 
							unnest(cast(:licenses as text[]))
				    `,
					db.Params{
						"names":     names,
						"addresses": addresses,
						"ports":     ports,
						"licenses":  licenses,
					},
				)
			}

			db.Exec(
				tx,
				`
					update licenses
					set port = null
					where port = -1000
			    `,
				db.Params{},
			)
		}

		{
			ipPools := util.ChunkBy(im1Ips, 100)
			for i, chunk := range ipPools {
				termio.WriteLine("IP pools: %d", i*100)

				var externalCidrs []string
				var internalCidrs []string

				for _, item := range chunk {
					externalCidrs = append(externalCidrs, item[0].Value)
					internalCidrs = append(internalCidrs, item[1].Value)
				}

				db.Exec(
					tx,
					`
						insert into ip_pool(subnet, private_subnet)
						select unnest(cast(:external_cidrs as text[])), unnest(cast(:internal_cidrs as text[]))
				    `,
					db.Params{
						"external_cidrs": externalCidrs,
						"internal_cidrs": internalCidrs,
					},
				)
			}
		}

		{
			drives := util.ChunkBy(coreDrives, 100)
			for i, chunk := range drives {
				termio.WriteLine("Drives: %d", i*100)

				var ids []string
				var productNames []string
				var productCategories []string
				var createdBy []string
				var projectIds []sql.NullString
				var providerGeneratedIds []sql.NullString
				var resourceJson []string

				for _, item := range chunk {
					ids = append(ids, item[0].Value)
					productNames = append(productNames, item[1].Value)
					productCategories = append(productCategories, item[2].Value)
					createdBy = append(createdBy, item[3].Value)
					projectIds = append(projectIds, dataImportNullString(item[4]))
					providerGeneratedIds = append(providerGeneratedIds, dataImportNullString(item[5]))
					resourceJson = append(resourceJson, item[6].Value)
				}

				db.Exec(
					tx,
					`
						insert into tracked_drives(drive_id, product_id, product_category, created_by, 
							project_id, provider_generated_id, resource, search_index) 
						select
							unnest(cast(:ids as text[])),
							unnest(cast(:product_names as text[])),
							unnest(cast(:product_categories as text[])),
							unnest(cast(:created_by as text[])),
							unnest(cast(:project_ids as text[])),
							unnest(cast(:provider_generated_ids as text[])),
							unnest(cast(:resources as jsonb[])),
							null
				    `,
					db.Params{
						"ids":                    ids,
						"product_names":          productNames,
						"product_categories":     productCategories,
						"created_by":             createdBy,
						"project_ids":            projectIds,
						"provider_generated_ids": providerGeneratedIds,
						"resources":              resourceJson,
					},
				)
			}
		}

		{
			ingresses := util.ChunkBy(coreIngress, 100)
			for i, chunk := range ingresses {
				termio.WriteLine("Links: %d", i*100)

				var ids []string
				var productNames []string
				var productCategories []string
				var createdBy []string
				var projectIds []sql.NullString
				var providerGeneratedIds []sql.NullString
				var resourceJson []string

				for _, item := range chunk {
					ids = append(ids, item[0].Value)
					productNames = append(productNames, item[1].Value)
					productCategories = append(productCategories, item[2].Value)
					createdBy = append(createdBy, item[3].Value)
					projectIds = append(projectIds, dataImportNullString(item[4]))
					providerGeneratedIds = append(providerGeneratedIds, dataImportNullString(item[5]))
					resourceJson = append(resourceJson, item[6].Value)
				}

				db.Exec(
					tx,
					`
						insert into tracked_ingresses(resource_id, created_by, project_id, product_id, 
							product_category, resource) 
						select
							unnest(cast(:ids as text[])),
							unnest(cast(:created_by as text[])),
							unnest(cast(:project_ids as text[])),
							unnest(cast(:product_names as text[])),
							unnest(cast(:product_categories as text[])),
							unnest(cast(:resources as jsonb[]))
				    `,
					db.Params{
						"ids":                ids,
						"created_by":         createdBy,
						"project_ids":        projectIds,
						"product_names":      productNames,
						"product_categories": productCategories,
						"resources":          resourceJson,
					},
				)
			}

			db.Exec(
				tx,
				`
					insert into ingresses(domain, owner) 
					select 
						resource->'specification'->>'domain',
						coalesce(resource->'owner'->>'project', resource->'owner'->>'createdBy')
					from tracked_ingresses
			    `,
				db.Params{},
			)
		}

		{
			ips := util.ChunkBy(coreIps, 100)
			for i, chunk := range ips {
				termio.WriteLine("IPs: %d", i*100)

				var ids []string
				var productNames []string
				var productCategories []string
				var createdBy []string
				var projectIds []sql.NullString
				var providerGeneratedIds []sql.NullString
				var resourceJson []string

				for _, item := range chunk {
					ids = append(ids, item[0].Value)
					productNames = append(productNames, item[1].Value)
					productCategories = append(productCategories, item[2].Value)
					createdBy = append(createdBy, item[3].Value)
					projectIds = append(projectIds, dataImportNullString(item[4]))
					providerGeneratedIds = append(providerGeneratedIds, dataImportNullString(item[5]))
					resourceJson = append(resourceJson, item[6].Value)
				}

				db.Exec(
					tx,
					`
						insert into tracked_ips(resource_id, created_by, project_id, product_id, 
							product_category, resource) 
						select
							unnest(cast(:ids as text[])),
							unnest(cast(:created_by as text[])),
							unnest(cast(:project_ids as text[])),
							unnest(cast(:product_names as text[])),
							unnest(cast(:product_categories as text[])),
							unnest(cast(:resources as jsonb[]))
				    `,
					db.Params{
						"ids":                ids,
						"created_by":         createdBy,
						"project_ids":        projectIds,
						"product_names":      productNames,
						"product_categories": productCategories,
						"resources":          resourceJson,
					},
				)
			}
		}

		{
			licenses := util.ChunkBy(coreLicenses, 100)
			for i, chunk := range licenses {
				termio.WriteLine("Licenses: %d", i*100)

				var ids []string
				var productNames []string
				var productCategories []string
				var createdBy []string
				var projectIds []sql.NullString
				var providerGeneratedIds []sql.NullString
				var resourceJson []string

				for _, item := range chunk {
					ids = append(ids, item[0].Value)
					productNames = append(productNames, item[1].Value)
					productCategories = append(productCategories, item[2].Value)
					createdBy = append(createdBy, item[3].Value)
					projectIds = append(projectIds, dataImportNullString(item[4]))
					providerGeneratedIds = append(providerGeneratedIds, dataImportNullString(item[5]))
					resourceJson = append(resourceJson, item[6].Value)
				}

				db.Exec(
					tx,
					`
						insert into tracked_licenses(resource_id, created_by, project_id, product_id, 
							product_category, resource) 
						select
							unnest(cast(:ids as text[])),
							unnest(cast(:created_by as text[])),
							unnest(cast(:project_ids as text[])),
							unnest(cast(:product_names as text[])),
							unnest(cast(:product_categories as text[])),
							unnest(cast(:resources as jsonb[]))
				    `,
					db.Params{
						"ids":                ids,
						"created_by":         createdBy,
						"project_ids":        projectIds,
						"product_names":      productNames,
						"product_categories": productCategories,
						"resources":          resourceJson,
					},
				)
			}
		}

		{
			projects := util.ChunkBy(coreProjects, 100)
			for i, chunk := range projects {
				termio.WriteLine("Projects: %d", i*100)
				var ids []string
				var projectJson []string
				var updates []int64

				for _, item := range chunk {
					ids = append(ids, item[0].Value)
					projectJson = append(projectJson, item[1].Value)

					timestamp, _ := strconv.ParseInt(item[2].Value, 10, 64)
					updates = append(updates, timestamp)
				}

				db.Exec(
					tx,
					`
						insert into tracked_projects(project_id, ucloud_project, last_update) 
						select
							unnest(cast(:ids as text[])),
							unnest(cast(:projects as jsonb[])),
							to_timestamp(unnest(cast(:updates as int8[])) / 1000.0)
				    `,
					db.Params{
						"ids":      ids,
						"projects": projectJson,
						"updates":  updates,
					},
				)
			}
		}

		i := 0
		for _, fileData := range coreAllocations {
			for line := range strings.SplitSeq(string(fileData), "\n") {
				if i%100 == 0 {
					termio.WriteLine("Allocations: %d", i)
				}

				if strings.TrimSpace(line) == "" {
					continue
				}

				db.Exec(
					tx,
					fmt.Sprintf(`
						insert into tracked_allocations(owner_username, owner_project, category, 
							combined_quota, locked, last_update, local_retired_usage) 
						values (%s)
					`, line),
					db.Params{},
				)

				i++
			}
		}

		db.Exec(
			tx,
			`
				insert into apm_events_replay_from(provider_id, last_update) values (:provider, now())
		    `,
			db.Params{
				"provider": cfg.Provider.Id,
			},
		)
	})
}

func dataImportNullInt(opt util.Option[string]) int {
	if opt.Present {
		value, _ := strconv.ParseInt(opt.Value, 10, 32)
		return int(value)
	} else {
		return -1000
	}
}

func dataImportNullString(opt util.Option[string]) sql.NullString {
	if opt.Present {
		return sql.NullString{Valid: true, String: opt.Value}
	} else {
		return sql.NullString{Valid: false, String: ""}
	}
}

func dataImportReadOrExit(dir string, file string) []byte {
	data, err := os.ReadFile(filepath.Join(dir, file))
	if err != nil {
		cli.HandleError(fmt.Sprintf("Reading %s", filepath.Join(dir, file)), err)
	}

	return data
}

type importJsonl []util.Option[string]

func dataImportReadJsonl(dir string, file string) []importJsonl {
	var result []importJsonl
	data := string(dataImportReadOrExit(dir, file))
	seq := strings.SplitSeq(data, "\n")
	for s := range seq {
		if strings.TrimSpace(s) == "" {
			continue
		}

		item := importJsonl{}
		_ = json.Unmarshal([]byte(s), &item)
		result = append(result, item)
	}
	return result
}

func dataImportReadPrefixed(dir string, prefix string) map[string][]byte {
	result := map[string][]byte{}
	dirEntries, _ := os.ReadDir(dir)
	for _, entry := range dirEntries {
		name := entry.Name()
		if strings.HasPrefix(name, prefix) {
			result[name] = dataImportReadOrExit(dir, name)
		}
	}
	return result
}
