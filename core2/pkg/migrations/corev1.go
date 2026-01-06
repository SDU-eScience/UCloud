package migrations

import (
	_ "embed"
	"strings"

	db "ucloud.dk/shared/pkg/database2"
)

//go:embed corev1.sql
var coreV1Sql []byte

func coreV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "coreV1",
		Execute: func(tx *db.Transaction) {
			row, ok := db.Get[struct{ TableExists bool }](
				tx,
				`select to_regclass('task.tasks') is not null as table_exists;`,
				db.Params{},
			)

			isMigratingFromCore1 := ok && row.TableExists

			if isMigratingFromCore1 {
				return
			}

			script := string(coreV1Sql)
			for len(script) > 0 {
				// NOTE(Dan): I have manually added "-- SNIP" around blocks which should not use semicolon as a
				// delimiter between queries. This was much easier than doing correct SQL parsing on the file.

				nextSnip := strings.Index(script, "-- SNIP")
				nextSemicolon := strings.Index(script, ";")

				toExecute := ""

				if nextSnip == -1 && nextSemicolon == -1 {
					toExecute = script
					script = ""
				} else if nextSnip != -1 && nextSemicolon != -1 && nextSnip < nextSemicolon {
					script = script[nextSnip:]
					script = script[strings.Index(script, "\n"):]

					idx := strings.Index(script, "-- SNIP")
					toExecute = script[:idx]

					script = script[idx:]
					script = script[strings.Index(script, "\n"):]
				} else {
					toExecute = script[:nextSemicolon]
					script = script[nextSemicolon+1:]
				}

				db.Exec(tx, toExecute, db.Params{})
			}
		},
	}
}

func coreV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "coreV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					INSERT INTO provider.resource (type, provider, created_at, created_by, project, id, product, provider_generated_id, confirmed_by_provider, public_read) VALUES ('metadata_template_namespace', null, '2022-11-01 11:47:14.698642 +00:00', '_ucloud', null, 1, null, null, true, true)
					ON CONFLICT DO NOTHING 
			    `,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					INSERT INTO provider.resource (type, provider, created_at, created_by, project, id, product, provider_generated_id, confirmed_by_provider, public_read) VALUES ('metadata_template_namespace', null, '2022-11-01 11:47:14.698642 +00:00', '_ucloud', null, 2, null, null, true, true)
					ON CONFLICT DO NOTHING 
			    `,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					INSERT INTO file_orchestrator.metadata_template_namespaces (resource, uname, namespace_type, 
						deprecated, latest_version) VALUES (1, 'favorite', 'PER_USER', false, '1.0.0')
					ON CONFLICT DO NOTHING 
			    `,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					INSERT INTO file_orchestrator.metadata_template_namespaces (resource, uname, namespace_type, 
						deprecated, latest_version) VALUES (2, 'sensitivity', 'COLLABORATORS', false, '1.0.0')
					ON CONFLICT DO NOTHING
			    `,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					INSERT INTO file_orchestrator.metadata_templates (title, namespace, uversion, schema, 
						inheritable, require_approval, description, change_log, ui_schema, deprecated, created_at) 
					VALUES ('UCloud File Sensitivity', 2, '1.0.0', '{"type": "object", 
						"title": "UCloud: File Sensitivity", "required": ["sensitivity"], "properties": 
						{"sensitivity": {"enum": ["SENSITIVE", "CONFIDENTIAL", "PRIVATE"], "type": "string", 
						"title": "File Sensitivity", "enumNames": ["Sensitive", "Confidential", "Private"]}}, 
						"dependencies": {}}', true, false, 'Describes the sensitivity of a file.', 'Initial', 
						'{"ui:order": ["sensitivity"]}', false, '2022-11-01 11:47:14.698642 +00:00')
					ON CONFLICT DO NOTHING
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					INSERT INTO file_orchestrator.metadata_templates (title, namespace, uversion, schema, 
						inheritable, require_approval, description, change_log, ui_schema, deprecated, created_at) 
					VALUES ('Favorite', 1, '1.0.0', '{"type": "object", "title": "UCloud: Favorite Files", "required": 
						["favorite"], "properties": {"favorite": {"type": "boolean", "title": 
						"Is this file one of your favorites?"}}, "description": "A document describing your favorite files", 
						"dependencies": {}}', false, false, 'favorite', 'Initial', '{"ui:order": ["favorite"]}', false, 
						'2022-11-01 11:47:14.698642 +00:00')
					ON CONFLICT DO NOTHING 
			    `,
				db.Params{},
			)
		},
	}
}
