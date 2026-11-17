package migrations

import (
	"os"
	"os/exec"
	"strings"
	"testing"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
)

func TestAccountingMigrationAppendOnly(t *testing.T) {
	scripts := migrationScripts()
	expected := strings.Fields(`
		coreV1 projectsV1 projectsV2 sharesV1 usageV1 usageV2 accountingV1 accountingV2 authV1
		auditPostgresV1 apiTokensV1 accountingV3 authV2 newsV1 coreV2 accountingV4 projectsV3
		auditPostgresV2 authV3 grantsV1 projectsV4 jobSettingsV1 grantsV2 apiTokensV2 apiTokensV3
		privateNetworksV1 jobsV1 resourcesV1 stacksV1 accountingV5 grantsV3 grantsV4 projectsV5
		sshV1 containerRepositoriesV1 applicationVariantsV1 tasksV1 projectsV6 customApplicationsV1
		applicationEditorV1 customApplicationsV2 customApplicationsV3 customApplicationsV4 featuresV1
		grantsV5 privateNetworksV2 privateNetworkIpsV1 privateNetworkIpsV2 privateNetworksV3 servicesV1
		stacksDeletionV1 servicesV2 privateNetworkIpsV3 privateNetworksV4 privateNetworksV5 privateNetworksV6
		accountingV6 accountingAllocationPackagesV1 accountingModelV1
	`)
	if len(scripts) < len(expected) {
		t.Fatal("missing accounting migrations")
	}
	for index, id := range expected {
		if scripts[index].Id != id {
			t.Fatalf("migration %d = %s, expected %s", index, scripts[index].Id, id)
		}
	}
}

func TestAccountingMigrationUpgradePaths(t *testing.T) {
	if os.Getenv("UCLOUD_ACCOUNTING_MIGRATION_TEST") != "1" {
		t.Skip("requires disposable accounting_migration_* databases")
	}
	password := os.Getenv("UCLOUD_ACCOUNTING_MIGRATION_PASSWORD")
	if password == "" {
		t.Fatal("UCLOUD_ACCOUNTING_MIGRATION_PASSWORD must be set")
	}
	Init()
	for _, test := range []struct {
		name            string
		database        string
		seed            bool
		v6Complete      bool
		packageComplete bool
	}{
		{name: "fresh", database: "accounting_migration_fresh"},
		{name: "pre_v6", database: "accounting_migration_pre_v6", seed: true},
		{name: "v6_complete", database: "accounting_migration_v6_complete", seed: true, v6Complete: true},
		{name: "package_complete", database: "accounting_migration_package_complete", seed: true, v6Complete: true, packageComplete: true},
	} {
		t.Run(test.name, func(t *testing.T) {
			previousDatabase := db.Database
			pool := db.Connect("postgres", password, "127.0.0.1", 5432, test.database, false)
			db.Database = pool
			t.Cleanup(func() {
				pool.Connection.Close()
				db.Database = previousDatabase
			})
			t.Setenv("UCLOUD_ACCOUNTING_UPGRADE_DIR", t.TempDir())
			empty := db.NewTx(func(tx *db.Transaction) bool {
				row, ok := db.Get[struct{ Count int }](
					tx,
					`
						select count(*) as count
						from pg_catalog.pg_tables
						where schemaname not in ('pg_catalog', 'information_schema')
					`,
					db.Params{},
				)
				return ok && row.Count == 0
			})
			if !empty {
				t.Fatal("the migration test database must be empty")
			}

			var beforeFacts, beforeProof string
			if test.seed {
				accountingMigrationPreparePreV6(t)
				accountingMigrationSeed(t)
				if test.v6Complete {
					accountingMigrationApply(t, accountingV6())
					accountingMigrationAssertColumns(t, false, false)
					beforeFacts = accountingMigrationFacts()
					beforeProof = accountingMigrationProof()
					if test.packageComplete {
						accountingMigrationApply(t, accountingAllocationPackagesV1())
						accountingMigrationAssertColumns(t, false, true)
					}
				}
			}

			db.Migrate()
			accountingMigrationAssertColumns(t, true, true)
			accountingMigrationAssertCompleted(t)
			if test.seed {
				accountingMigrationAssertModels(t)
			}
			if test.v6Complete {
				if after := accountingMigrationFacts(); after != beforeFacts {
					t.Fatal("the appended migrations changed existing accounting facts")
				}
				if after := accountingMigrationProof(); after != beforeProof {
					t.Fatal("the appended migrations changed the completed v6 proof")
				}
			}

			facts := accountingMigrationFacts()
			proof := accountingMigrationProof()
			db.Migrate()
			if after := accountingMigrationFacts(); after != facts {
				t.Fatal("restarting migrations changed accounting facts")
			}
			if after := accountingMigrationProof(); after != proof {
				t.Fatal("restarting migrations reran v6")
			}
		})
	}
	t.Run("blocked_v6_then_retry", func(t *testing.T) {
		previousDatabase := db.Database
		pool := db.Connect("postgres", password, "127.0.0.1", 5432, "accounting_migration_blocked", false)
		db.Database = pool
		t.Cleanup(func() {
			pool.Connection.Close()
			db.Database = previousDatabase
		})
		t.Setenv("UCLOUD_ACCOUNTING_UPGRADE_DIR", t.TempDir())
		accountingMigrationPreparePreV6(t)
		accountingMigrationSeed(t)
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`insert into accounting.scoped_usage(key, usage) values ('migration-check-unmapped-scope', 10)`,
				db.Params{},
			)
		})
		before := accountingMigrationFacts()
		child := exec.Command(os.Args[0], "-test.run=^TestAccountingMigrationBlockedV6Child$", "-test.v")
		child.Env = append(os.Environ(), "UCLOUD_ACCOUNTING_MIGRATION_CHILD=1")
		output, failure := child.CombinedOutput()
		if failure == nil {
			t.Fatal("v6 accepted an unmapped accounting scope")
		}
		if !strings.Contains(string(output), "has no retained wallet identity") {
			t.Fatalf("v6 failed for an unexpected reason: %s", output)
		}
		accountingMigrationAssertColumns(t, false, false)
		if after := accountingMigrationFacts(); after != before {
			t.Fatal("failed v6 changed accounting facts")
		}
		blocked := db.NewTx(func(tx *db.Transaction) bool {
			row, ok := db.Get[struct {
				Completed  int
				HasProof   bool
				HasArchive bool
			}](
				tx,
				`
					select
						(select count(*) from completed_migrations where id = 'accountingV6') as completed,
						to_regclass('accounting.upgrade_runs') is not null as has_proof,
						to_regclass('accounting.scoped_usage_legacy_v6') is not null as has_archive
				`,
				db.Params{},
			)
			return ok && row.Completed == 0 && !row.HasProof && !row.HasArchive
		})
		if !blocked {
			t.Fatal("failed v6 committed a marker, proof, or scope archive")
		}
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`delete from accounting.scoped_usage where key = 'migration-check-unmapped-scope'`,
				db.Params{},
			)
		})
		db.Migrate()
		accountingMigrationAssertColumns(t, true, true)
		accountingMigrationAssertCompleted(t)
		accountingMigrationAssertModels(t)
	})
}

func TestAccountingMigrationBlockedV6Child(t *testing.T) {
	if os.Getenv("UCLOUD_ACCOUNTING_MIGRATION_CHILD") != "1" {
		t.Skip("runs only as the blocked migration subprocess")
	}
	db.Database = db.Connect("postgres", os.Getenv("UCLOUD_ACCOUNTING_MIGRATION_PASSWORD"), "127.0.0.1", 5432, "accounting_migration_blocked", false)
	Init()
	db.Migrate()
}

func TestAccountingMigrationOldV6Bridge(t *testing.T) {
	phase := os.Getenv("UCLOUD_ACCOUNTING_MIGRATION_OLD_V6")
	if phase != "prepare" && phase != "upgrade" {
		t.Skip("requires a v6 migration run by the previous release")
	}
	password := os.Getenv("UCLOUD_ACCOUNTING_MIGRATION_PASSWORD")
	if password == "" {
		t.Fatal("UCLOUD_ACCOUNTING_MIGRATION_PASSWORD must be set")
	}
	previousDatabase := db.Database
	pool := db.Connect("postgres", password, "127.0.0.1", 5432, "accounting_migration_old_v6", false)
	db.Database = pool
	t.Cleanup(func() {
		pool.Connection.Close()
		db.Database = previousDatabase
	})
	if phase == "prepare" {
		accountingMigrationPreparePreV6(t)
		accountingMigrationSeed(t)
		return
	}
	accountingMigrationAssertColumns(t, false, false)
	proof := accountingMigrationProof()
	if proof == "" {
		t.Fatal("the previous release must complete v6 before this check")
	}
	facts := accountingMigrationFacts()
	Init()
	db.Migrate()
	accountingMigrationAssertCompleted(t)
	accountingMigrationAssertColumns(t, true, true)
	accountingMigrationAssertModels(t)
	if after := accountingMigrationFacts(); after != facts {
		t.Fatal("upgrading the previous release changed accounting facts")
	}
	if after := accountingMigrationProof(); after != proof {
		t.Fatal("upgrading the previous release changed its v6 proof")
	}
	db.Migrate()
	if after := accountingMigrationFacts(); after != facts {
		t.Fatal("restarting after the previous-release upgrade changed accounting facts")
	}
	if after := accountingMigrationProof(); after != proof {
		t.Fatal("restarting after the previous-release upgrade reran v6")
	}
}

func accountingMigrationPreparePreV6(t *testing.T) {
	t.Helper()
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				create table migrations(id text primary key);
				create table completed_migrations(
					id text primary key references migrations,
					completed_at timestamp
				)
			`,
			db.Params{},
		)
	})
	for _, script := range migrationScripts() {
		if script.Id == "accountingV6" {
			break
		}
		accountingMigrationApply(t, script)
	}
	accountingMigrationAssertColumns(t, false, false)
}

func accountingMigrationApply(t *testing.T, script db.MigrationScript) {
	t.Helper()
	var failure error
	db.NewTx0(func(tx *db.Transaction) {
		tx.NoDevResetThisIsNotAHackIPromise = true
		script.Execute(tx)
		db.Exec(
			tx,
			`insert into migrations(id) values (:id)`,
			db.Params{
				"id": script.Id,
			},
		)
		db.Exec(
			tx,
			`insert into completed_migrations(id, completed_at) values (:id, now())`,
			db.Params{
				"id": script.Id,
			},
		)
		failure = tx.ConsumeError()
	})
	if failure != nil {
		t.Fatalf("migration %s failed: %v", script.Id, failure)
	}
}

func accountingMigrationSeed(t *testing.T) {
	t.Helper()
	var failure error
	db.NewTx0(func(tx *db.Transaction) {
		tx.NoDevResetThisIsNotAHackIPromise = true
		db.Exec(
			tx,
			`
				insert into provider.resource(id, type, confirmed_by_provider)
				values (99000, 'provider', true);

				insert into provider.providers(unique_name, domain, https, port, resource)
				values ('migration-check', 'invalid.example', false, 80, 99000);

				insert into project.projects(id, title)
				values
					('00000000-0000-4000-8000-000000000001', 'Migration root'),
					('00000000-0000-4000-8000-000000000002', 'Migration child');

				insert into accounting.wallet_owner(id, project_id)
				values
					(99001, '00000000-0000-4000-8000-000000000001'),
					(99002, '00000000-0000-4000-8000-000000000002');

				insert into accounting.accounting_units(id, name, name_plural, floating_point, display_frequency_suffix)
				values
					(99001, 'Core', 'Cores', false, true),
					(99002, 'GB', 'GB', false, false),
					(99003, 'Credit', 'Credits', true, false);

				insert into accounting.product_categories(
					id, provider, category, product_type, accounting_unit, accounting_frequency, charge_type
				)
				values
					(99001, 'migration-check', 'compute', 'COMPUTE', 99001, 'PERIODIC_HOUR', 'ABSOLUTE'),
					(99002, 'migration-check', 'storage', 'STORAGE', 99002, 'ONCE', 'DIFFERENTIAL_QUOTA'),
					(99003, 'migration-check', 'inference', 'INFERENCE', 99003, 'ONCE', 'ABSOLUTE');

				insert into accounting.products(id, name, category, price)
				values
					(99001, 'compute', 99001, 1),
					(99002, 'storage', 99002, 1),
					(99003, 'inference', 99003, 1);

				insert into accounting.wallets_v2(id, wallet_owner, product_category, local_usage)
				values
					(99001, 99001, 99001, 0),
					(99002, 99002, 99001, 35),
					(99003, 99001, 99002, 0),
					(99004, 99002, 99002, 25),
					(99005, 99001, 99003, 0),
					(99006, 99002, 99003, 35);

				insert into accounting.allocation_groups(id, associated_wallet, parent_wallet, tree_usage)
				values
					(99001, 99001, null, 35),
					(99002, 99002, 99001, 35),
					(99003, 99003, null, 25),
					(99004, 99004, 99003, 25),
					(99005, 99005, null, 35),
					(99006, 99006, 99005, 35);

				insert into accounting.wallet_allocations_v2(
					id, associated_allocation_group, quota, allocation_start_time, allocation_end_time,
					retired, retired_usage, retired_quota
				)
				values
					(99001, 99001, 1000, now() - interval '1 year', now() + interval '1 year', false, 0, 0),
					(99002, 99002, 100, now() - interval '1 day', now() + interval '1 year', false, 0, 0),
					(99003, 99002, 10, now() - interval '1 year', now() - interval '1 day', true, 10, 80),
					(99004, 99003, 1000, now() - interval '1 year', now() + interval '1 year', false, 0, 0),
					(99005, 99004, 100, now() - interval '1 day', now() + interval '1 year', false, 0, 0),
					(99006, 99004, 80, now() - interval '1 year', now() - interval '1 day', true, 10, 80),
					(99007, 99005, 1000, now() - interval '1 year', now() + interval '1 year', false, 0, 0),
					(99008, 99006, 100, now() - interval '1 day', now() + interval '1 year', false, 0, 0),
					(99009, 99006, 10, now() - interval '1 year', now() - interval '1 day', true, 10, 80);
			`,
			db.Params{},
		)
		failure = tx.ConsumeError()
	})
	if failure != nil {
		t.Fatalf("cannot seed pre-v6 accounting data: %v", failure)
	}
}

func accountingMigrationAssertColumns(t *testing.T, modelPresent, packagePresent bool) {
	t.Helper()
	row := db.NewTx(func(tx *db.Transaction) struct{ Model, Package bool } {
		result, _ := db.Get[struct{ Model, Package bool }](
			tx,
			`
				select
					exists(
						select 1 from information_schema.columns
						where table_schema = 'accounting' and table_name = 'product_categories' and column_name = 'accounting_model'
					) as model,
					exists(
						select 1 from information_schema.columns
						where table_schema = 'accounting' and table_name = 'wallet_allocations_v2' and column_name = 'allocation_package'
					) as package
			`,
			db.Params{},
		)
		return result
	})
	if row.Model != modelPresent || row.Package != packagePresent {
		t.Fatalf("schema columns model=%v package=%v, expected model=%v package=%v", row.Model, row.Package, modelPresent, packagePresent)
	}
}

func accountingMigrationAssertCompleted(t *testing.T) {
	t.Helper()
	count := db.NewTx(func(tx *db.Transaction) int {
		row, _ := db.Get[struct{ Count int }](
			tx,
			`
				select count(*) as count
				from completed_migrations
				where id in ('accountingV6', 'accountingAllocationPackagesV1', 'accountingModelV1')
			`,
			db.Params{},
		)
		return row.Count
	})
	if count != 3 {
		t.Fatalf("completed accounting migrations = %d, expected 3", count)
	}
	if accountingMigrationProof() == "" {
		t.Fatal("missing committed v6 proof")
	}
}

func accountingMigrationAssertModels(t *testing.T) {
	t.Helper()
	rows := db.NewTx(func(tx *db.Transaction) []struct {
		Category        string
		AccountingModel accapi.AccountingModel
	} {
		return db.Select[struct {
			Category        string
			AccountingModel accapi.AccountingModel
		}](
			tx,
			`
				select category, accounting_model
				from accounting.product_categories
				where provider = 'migration-check'
				order by category
			`,
			db.Params{},
		)
	})
	expected := map[string]accapi.AccountingModel{
		"compute":   accapi.AccountingModelConsumption,
		"storage":   accapi.AccountingModelCapacity,
		"inference": accapi.AccountingModelConsumption,
	}
	if len(rows) != len(expected) {
		t.Fatalf("model rows = %d, expected %d", len(rows), len(expected))
	}
	for _, row := range rows {
		if row.AccountingModel != expected[row.Category] {
			t.Fatalf("%s model = %s, expected %s", row.Category, row.AccountingModel, expected[row.Category])
		}
	}
}

func accountingMigrationFacts() string {
	return db.NewTx(func(tx *db.Transaction) string {
		row, _ := db.Get[struct{ Data string }](
			tx,
			`
				select jsonb_build_object(
					'wallets', (select jsonb_agg(to_jsonb(w) order by id) from accounting.wallets_v2 w),
					'groups', (select jsonb_agg(to_jsonb(g) order by id) from accounting.allocation_groups g),
					'allocations', (select jsonb_agg(to_jsonb(a) - 'allocation_package' order by id) from accounting.wallet_allocations_v2 a),
					'scopes', (select jsonb_agg(to_jsonb(s) order by key) from accounting.scoped_usage s)
				)::text as data
			`,
			db.Params{},
		)
		return row.Data
	})
}

func accountingMigrationProof() string {
	return db.NewTx(func(tx *db.Transaction) string {
		row, _ := db.Get[struct{ Data string }](
			tx,
			`
				select run_id || ':' || md5(encode(proof, 'hex')) as data
				from accounting.upgrade_runs
				where version = 'accountingV6'
			`,
			db.Params{},
		)
		return row.Data
	})
}
