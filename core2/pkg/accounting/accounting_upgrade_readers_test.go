package accounting

import (
	"os"
	"testing"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
)

func TestAccountingMigrationRuntimeReaders(t *testing.T) {
	if os.Getenv("UCLOUD_ACCOUNTING_MIGRATION_TEST") != "1" {
		t.Skip("requires the disposable migrated accounting_migration_pre_v6 database")
	}
	password := os.Getenv("UCLOUD_ACCOUNTING_MIGRATION_PASSWORD")
	if password == "" {
		t.Fatal("UCLOUD_ACCOUNTING_MIGRATION_PASSWORD must be set")
	}
	previousDatabase := db.Database
	pool := db.Connect("postgres", password, "127.0.0.1", 5432, "accounting_migration_pre_v6", false)
	db.Database = pool
	t.Cleanup(func() {
		pool.Connection.Close()
		db.Database = previousDatabase
	})
	now := time.Now()
	var loadErrors []string
	var failure error
	db.NewTx0(func(tx *db.Transaction) {
		productsLoadFromTx(tx)
		accountingLoadFromTx(tx, now, func(message string) {
			loadErrors = append(loadErrors, message)
		})
		failure = tx.ConsumeError()
	})
	if failure != nil || len(loadErrors) != 0 {
		t.Fatalf("normal accounting readers failed: %v %v", failure, loadErrors)
	}
	expected := map[string]accapi.AccountingModel{
		"compute":   accapi.AccountingModelConsumption,
		"storage":   accapi.AccountingModelCapacity,
		"inference": accapi.AccountingModelConsumption,
	}
	for name, model := range expected {
		bucket := accGlobals.BucketsByCategory[accapi.ProductCategoryIdV2{Name: name, Provider: "migration-check"}]
		if bucket == nil {
			t.Fatalf("normal accounting reader did not load %s", name)
		}
		if bucket.Category.AccountingModel != model {
			t.Fatalf("%s model = %s, expected %s", name, bucket.Category.AccountingModel, model)
		}
		if bucket.IsCapacityBased() != (model == accapi.AccountingModelCapacity) {
			t.Fatalf("%s has the wrong quota behavior", name)
		}
		if invalid := lValidateAccountingTree(bucket, now); invalid != nil {
			t.Fatalf("%s runtime accounting invariants failed: %v", name, invalid)
		}
		for _, allocation := range bucket.AllocationsById {
			if allocation.AllocationPackage {
				t.Fatalf("legacy allocation %d became a split package", allocation.Id)
			}
		}
	}
}
