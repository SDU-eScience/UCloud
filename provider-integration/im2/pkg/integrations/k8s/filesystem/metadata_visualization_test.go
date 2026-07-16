package filesystem

import (
	"path/filepath"
	"testing"
	"time"

	"github.com/cockroachdb/pebble/v2"
	orc "ucloud.dk/shared/pkg/orchestrators"
)

func TestMetadataVisualizePrioritizesLargestEntries(t *testing.T) {
	db, err := pebble.Open(filepath.Join(t.TempDir(), "catalog"), &pebble.Options{})
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()

	entries := map[string]MetadataEntry{
		"":                {EntryType: MetaEntryDirectory, RecursiveLogicalBytes: 160, AggregateObservedAt: 123},
		"small":           {EntryType: MetaEntryDirectory, RecursiveLogicalBytes: 10},
		"small/child":     {EntryType: MetaEntryRegular, LogicalSize: 9},
		"large":           {EntryType: MetaEntryDirectory, RecursiveLogicalBytes: 100},
		"large/child":     {EntryType: MetaEntryRegular, LogicalSize: 90},
		"medium-file.bin": {EntryType: MetaEntryRegular, LogicalSize: 50},
	}
	for path, entry := range entries {
		if err = db.Set(metadataPathKey(metadataRelativeComponents(path)), entry.Encode(), pebble.NoSync); err != nil {
			t.Fatal(err)
		}
	}
	if err = db.Flush(); err != nil {
		t.Fatal(err)
	}

	result, observedAt, complete, found, err := metadataVisualizeInDB(db, "drive-1", nil, 1000, time.Second)
	if err != nil {
		t.Fatal(err)
	}
	if !found || !complete || observedAt != 123 {
		t.Fatalf("unexpected traversal metadata: found=%v complete=%v observedAt=%d", found, complete, observedAt)
	}
	wantPaths := []string{
		"/drive-1",
		"/drive-1/large",
		"/drive-1/large/child",
		"/drive-1/medium-file.bin",
		"/drive-1/small",
		"/drive-1/small/child",
	}
	if len(result) != len(wantPaths) {
		t.Fatalf("expected %d entries, got %#v", len(wantPaths), result)
	}
	for i, wantPath := range wantPaths {
		if result[i].Path != wantPath {
			t.Fatalf("entry %d: expected %q, got %q", i, wantPath, result[i].Path)
		}
	}
	if result[0].Type != orc.FileTypeDirectory || result[0].SizeInBytes != 160 {
		t.Fatalf("unexpected root usage: %#v", result[0])
	}
	if result[2].Type != orc.FileTypeFile || result[2].SizeInBytes != 90 {
		t.Fatalf("unexpected file usage: %#v", result[2])
	}
}

func TestMetadataVisualizeHonorsEntryLimit(t *testing.T) {
	db, err := pebble.Open(filepath.Join(t.TempDir(), "catalog"), &pebble.Options{})
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()

	root := MetadataEntry{EntryType: MetaEntryDirectory, RecursiveLogicalBytes: 6}
	if err = db.Set(metadataPathKey(nil), root.Encode(), pebble.NoSync); err != nil {
		t.Fatal(err)
	}
	for i, size := range []uint64{1, 2, 3} {
		entry := MetadataEntry{EntryType: MetaEntryRegular, LogicalSize: size}
		if err = db.Set(metadataPathKey([]string{string(rune('a' + i))}), entry.Encode(), pebble.NoSync); err != nil {
			t.Fatal(err)
		}
	}
	if err = db.Flush(); err != nil {
		t.Fatal(err)
	}

	result, _, complete, found, err := metadataVisualizeInDB(db, "drive-1", nil, 3, time.Second)
	if err != nil {
		t.Fatal(err)
	}
	if !found || complete || len(result) != 3 {
		t.Fatalf("unexpected limited traversal: found=%v complete=%v entries=%#v", found, complete, result)
	}
	if result[1].SizeInBytes != 3 || result[2].SizeInBytes != 2 {
		t.Fatalf("entry budget did not prioritize largest files: %#v", result)
	}
}
