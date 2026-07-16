package filesystem

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"testing"

	"github.com/cockroachdb/pebble/v2"
	"golang.org/x/time/rate"
	"ucloud.dk/shared/pkg/util"
)

func TestMetadataEntryRoundTrip(t *testing.T) {
	original := MetadataEntry{
		EntryType:               MetaEntryDirectory,
		EntryGeneration:         42,
		ObservedAtUnixNano:      -123,
		LogicalSize:             4096,
		AllocatedSize:           util.OptValue(uint64(8192)),
		ModificationTime:        123456789,
		AccessTime:              util.OptValue(int64(-456)),
		DeviceID:                util.OptValue(uint64(7)),
		InodeID:                 util.OptValue(uint64(8)),
		Mode:                    util.OptValue(uint64(0o40755)),
		LinkCount:               util.OptValue(uint64(3)),
		AggregateGeneration:     43,
		AggregateObservedAt:     987654321,
		RecursiveLogicalBytes:   100_000,
		RecursiveAllocatedSize:  util.OptValue(uint64(120_000)),
		RecursiveFileCount:      55,
		RecursiveDirectoryCount: 6,
	}

	var decoded MetadataEntry
	if err := decoded.Decode(original.Encode()); err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(decoded, original) {
		t.Fatalf("round trip mismatch\nwant: %#v\n got: %#v", original, decoded)
	}
}

func TestMetadataEntryDecodeRejectsMalformedValues(t *testing.T) {
	entry := MetadataEntry{EntryType: MetaEntryRegular}
	encoded := entry.Encode()
	tests := [][]byte{
		nil,
		{MetadataFormatVersion + 1, byte(MetaEntryRegular), 0, 0},
		{MetadataFormatVersion, byte(MetaEntryUnknown), 0, 0},
		append(append([]byte(nil), encoded...), 0),
		encoded[:len(encoded)-1],
	}
	for _, value := range tests {
		if err := new(MetadataEntry).Decode(value); err == nil {
			t.Fatalf("expected decode failure for %x", value)
		}
	}
}

func TestMetadataPathAndNameKeys(t *testing.T) {
	root := metadataPathKey(nil)
	project := metadataPathKey([]string{"projects", "alpha"})
	child := metadataPathKey([]string{"projects", "alpha", "Report.CSV"})
	sibling := metadataPathKey([]string{"projects", "alpha-2"})
	if !bytes.Equal(root, []byte{MetaKeyspacePath}) {
		t.Fatalf("unexpected root key %x", root)
	}
	if !bytes.HasPrefix(child, project) {
		t.Fatalf("child key %x does not have directory prefix %x", child, project)
	}
	if bytes.HasPrefix(sibling, project) {
		t.Fatalf("sibling key %x has directory prefix %x", sibling, project)
	}
	name, err := metadataNameKey(child)
	if err != nil {
		t.Fatal(err)
	}
	wantNamePrefix := append([]byte{MetaKeyspaceName}, []byte("report.csv\x00")...)
	if !bytes.HasPrefix(name, wantNamePrefix) {
		t.Fatalf("unexpected normalized NAME key %x", name)
	}
	upper := metadataPrefixSuccessor(project)
	if bytes.Compare(child, upper) >= 0 || bytes.Compare(sibling, upper) < 0 {
		t.Fatalf("invalid subtree range [%x, %x)", project, upper)
	}
}

func TestMetadataScanPublishesAndReplacesSubtree(t *testing.T) {
	driveRoot := t.TempDir()
	databasePath := filepath.Join(t.TempDir(), "catalog")
	alpha := filepath.Join(driveRoot, "alpha")
	if err := os.MkdirAll(filepath.Join(alpha, "nested"), 0o755); err != nil {
		t.Fatal(err)
	}
	mustWriteMetadataTestFile(t, filepath.Join(driveRoot, "root.txt"), "root")
	mustWriteMetadataTestFile(t, filepath.Join(alpha, "Old.TXT"), "old contents")

	limiter := rate.NewLimiter(rate.Inf, 1)
	if err := metadataScanAndPublish(context.Background(), driveRoot, driveRoot, databasePath, limiter, 2); err != nil {
		t.Fatal(err)
	}

	db := openMetadataTestDB(t, databasePath)
	root := readMetadataTestEntry(t, db, metadataPathKey(nil))
	if root.RecursiveFileCount != 2 || root.RecursiveDirectoryCount != 2 {
		t.Fatalf("unexpected full-scan root counts: files=%d directories=%d", root.RecursiveFileCount, root.RecursiveDirectoryCount)
	}
	assertMetadataTestKey(t, db, metadataPathKey([]string{"alpha", "Old.TXT"}), true)
	oldName, err := metadataNameKey(metadataPathKey([]string{"alpha", "Old.TXT"}))
	if err != nil {
		t.Fatal(err)
	}
	assertMetadataTestKey(t, db, oldName, true)
	if err = db.Close(); err != nil {
		t.Fatal(err)
	}

	if err = os.RemoveAll(filepath.Join(alpha, "nested")); err != nil {
		t.Fatal(err)
	}
	if err = os.Remove(filepath.Join(alpha, "Old.TXT")); err != nil {
		t.Fatal(err)
	}
	mustWriteMetadataTestFile(t, filepath.Join(alpha, "new.txt"), "new")
	if err = metadataScanAndPublish(context.Background(), alpha, driveRoot, databasePath, limiter, 2); err != nil {
		t.Fatal(err)
	}

	db = openMetadataTestDB(t, databasePath)
	defer db.Close()
	root = readMetadataTestEntry(t, db, metadataPathKey(nil))
	if root.RecursiveFileCount != 2 || root.RecursiveDirectoryCount != 1 {
		t.Fatalf("unexpected replacement root counts: files=%d directories=%d", root.RecursiveFileCount, root.RecursiveDirectoryCount)
	}
	assertMetadataTestKey(t, db, metadataPathKey([]string{"alpha", "Old.TXT"}), false)
	assertMetadataTestKey(t, db, metadataPathKey([]string{"alpha", "nested"}), false)
	assertMetadataTestKey(t, db, oldName, false)
	newPath := metadataPathKey([]string{"alpha", "new.txt"})
	assertMetadataTestKey(t, db, newPath, true)
	newName, err := metadataNameKey(newPath)
	if err != nil {
		t.Fatal(err)
	}
	assertMetadataTestKey(t, db, newName, true)
	staleName, err := metadataNameKey(metadataPathKey([]string{"missing", "newer.txt"}))
	if err != nil {
		t.Fatal(err)
	}
	if err = db.Set(staleName, nil, pebble.Sync); err != nil {
		t.Fatal(err)
	}
	var results []MetadataSearchResult
	err = metadataSearchByNamePrefixInDB(context.Background(), db, "drive-1", nil, "NE", 10, func(result MetadataSearchResult) bool {
		results = append(results, result)
		return true
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(results) != 1 || results[0].Path != "/drive-1/alpha/new.txt" {
		t.Fatalf("unexpected verified prefix results: %#v", results)
	}
}

func TestMetadataSearchByNamePrefixInDBUsesPrefixAndFolderScope(t *testing.T) {
	driveRoot := t.TempDir()
	databasePath := filepath.Join(t.TempDir(), "catalog")
	if err := os.MkdirAll(filepath.Join(driveRoot, "selected"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.MkdirAll(filepath.Join(driveRoot, "other"), 0o755); err != nil {
		t.Fatal(err)
	}
	mustWriteMetadataTestFile(t, filepath.Join(driveRoot, "selected", "Report Annual.CSV"), "selected")
	mustWriteMetadataTestFile(t, filepath.Join(driveRoot, "selected", "Annual Report.CSV"), "not a prefix")
	mustWriteMetadataTestFile(t, filepath.Join(driveRoot, "other", "Report Annual.CSV"), "other")
	if err := metadataScanAndPublish(context.Background(), driveRoot, driveRoot, databasePath, rate.NewLimiter(rate.Inf, 1), 2); err != nil {
		t.Fatal(err)
	}

	db := openMetadataTestDB(t, databasePath)
	defer db.Close()
	var results []MetadataSearchResult
	err := metadataSearchByNamePrefixInDB(context.Background(), db, "drive-1", []string{"selected"}, "REPORT", 1, func(result MetadataSearchResult) bool {
		results = append(results, result)
		return true
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(results) != 1 || results[0].Path != "/drive-1/selected/Report Annual.CSV" {
		t.Fatalf("unexpected search results: %#v", results)
	}
}

func TestMetadataScanRejectsFileRootWithoutPublishing(t *testing.T) {
	driveRoot := t.TempDir()
	databasePath := filepath.Join(t.TempDir(), "catalog")
	file := filepath.Join(driveRoot, "file")
	mustWriteMetadataTestFile(t, file, "data")
	err := metadataScanAndPublish(context.Background(), file, driveRoot, databasePath, rate.NewLimiter(rate.Inf, 1), 10)
	if err == nil {
		t.Fatal("expected file scan root to be rejected")
	}
	db := openMetadataTestDB(t, databasePath)
	defer db.Close()
	_, closer, getErr := db.Get(metadataPathKey([]string{"file"}))
	if closer != nil {
		_ = closer.Close()
	}
	if !errors.Is(getErr, pebble.ErrNotFound) {
		t.Fatalf("file root was unexpectedly published: %v", getErr)
	}
}

func TestMetadataRecoversPendingAncestorDelta(t *testing.T) {
	databasePath := filepath.Join(t.TempDir(), "catalog")
	db := openMetadataTestDB(t, databasePath)
	defer db.Close()
	root := MetadataEntry{EntryType: MetaEntryDirectory, RecursiveFileCount: 1, RecursiveAllocatedSize: util.OptValue(uint64(0))}
	alpha := MetadataEntry{EntryType: MetaEntryDirectory, EntryGeneration: 99, RecursiveFileCount: 2, RecursiveAllocatedSize: util.OptValue(uint64(0))}
	if err := db.Set(metadataPathKey(nil), root.Encode(), pebble.Sync); err != nil {
		t.Fatal(err)
	}
	alphaKey := metadataPathKey([]string{"alpha"})
	if err := db.Set(alphaKey, alpha.Encode(), pebble.Sync); err != nil {
		t.Fatal(err)
	}
	pending := metadataPendingAggregate{
		generation: 99,
		rootKey:    alphaKey,
		old:        metadataAggregate{files: 1, directories: 1},
		new:        metadataAggregate{files: 2, directories: 1},
	}
	if err := db.Set(metadataPendingAggregateKey, pending.encode(), pebble.Sync); err != nil {
		t.Fatal(err)
	}
	if err := metadataRecoverPendingAggregate(db); err != nil {
		t.Fatal(err)
	}
	root = readMetadataTestEntry(t, db, metadataPathKey(nil))
	if root.RecursiveFileCount != 2 {
		t.Fatalf("recovered file count is %d, want 2", root.RecursiveFileCount)
	}
	assertMetadataTestKey(t, db, metadataPendingAggregateKey, false)
	if err := metadataRecoverPendingAggregate(db); err != nil {
		t.Fatal(err)
	}
	root = readMetadataTestEntry(t, db, metadataPathKey(nil))
	if root.RecursiveFileCount != 2 {
		t.Fatalf("recovery was applied twice: %d", root.RecursiveFileCount)
	}
}

func TestMetadataExternalMergeUsesBoundedFanIn(t *testing.T) {
	driveRoot := t.TempDir()
	databasePath := filepath.Join(t.TempDir(), "catalog")
	for i := range 70 {
		mustWriteMetadataTestFile(t, filepath.Join(driveRoot, fmt.Sprintf("file-%03d", i)), "x")
	}
	if err := metadataScanAndPublish(context.Background(), driveRoot, driveRoot, databasePath, rate.NewLimiter(rate.Inf, 1), 1); err != nil {
		t.Fatal(err)
	}
	db := openMetadataTestDB(t, databasePath)
	defer db.Close()
	root := readMetadataTestEntry(t, db, metadataPathKey(nil))
	if root.RecursiveFileCount != 70 {
		t.Fatalf("merged file count is %d, want 70", root.RecursiveFileCount)
	}
}

func mustWriteMetadataTestFile(t *testing.T, path, contents string) {
	t.Helper()
	if err := os.WriteFile(path, []byte(contents), 0o644); err != nil {
		t.Fatal(err)
	}
}

func openMetadataTestDB(t *testing.T, path string) *pebble.DB {
	t.Helper()
	metadataWaitForNameRefresh(path)
	db, err := pebble.Open(path, &pebble.Options{FormatMajorVersion: pebble.FormatNewest})
	if err != nil {
		t.Fatal(err)
	}
	return db
}

func readMetadataTestEntry(t *testing.T, db *pebble.DB, key []byte) MetadataEntry {
	t.Helper()
	entry, present, err := metadataReadEntry(db, key)
	if err != nil {
		t.Fatal(err)
	}
	if !present {
		t.Fatalf("missing metadata key %x", key)
	}
	return entry
}

func assertMetadataTestKey(t *testing.T, db *pebble.DB, key []byte, expected bool) {
	t.Helper()
	_, closer, err := db.Get(key)
	if closer != nil {
		_ = closer.Close()
	}
	present := err == nil
	if err != nil && !errors.Is(err, pebble.ErrNotFound) {
		t.Fatal(err)
	}
	if present != expected {
		t.Fatalf("key %x presence: got %t, want %t", key, present, expected)
	}
}
