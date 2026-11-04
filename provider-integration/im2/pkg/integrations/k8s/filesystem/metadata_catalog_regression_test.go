package filesystem

import (
	"container/heap"
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"

	"github.com/cockroachdb/pebble/v2"
	"golang.org/x/time/rate"
	"ucloud.dk/pkg/config"
	"ucloud.dk/pkg/integrations/k8s/shared"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func TestMetadataVisiblePathStaysWithinShare(t *testing.T) {
	root := []string{"projects", "team", "shared"}
	tests := []struct {
		components []string
		path       string
		ok         bool
	}{
		{components: []string{"projects", "team", "shared"}, path: "/share", ok: true},
		{components: []string{"projects", "team", "shared", "folder", "file"}, path: "/share/folder/file", ok: true},
		{components: []string{"projects", "team", "shared-sibling", "file"}, ok: false},
		{components: []string{"projects", "team"}, ok: false},
	}
	for _, test := range tests {
		path, ok := metadataVisiblePath("share", root, test.components)
		if path != test.path || ok != test.ok {
			t.Fatalf("components %q: got %q, %t; want %q, %t", test.components, path, ok, test.path, test.ok)
		}
	}
}

func TestMetadataSnapshotSurvivesPublication(t *testing.T) {
	driveRoot := t.TempDir()
	databasePath := filepath.Join(t.TempDir(), "catalog")
	limiter := rate.NewLimiter(rate.Inf, 1)
	oldPath := filepath.Join(driveRoot, "old.txt")
	mustWriteMetadataTestFile(t, oldPath, "old")
	err := metadataScanAndPublish(context.Background(), driveRoot, driveRoot, databasePath, limiter, 10)
	if err != nil {
		t.Fatal(err)
	}
	db, release, err := metadataAcquireDatabase(databasePath, false)
	if err != nil {
		t.Fatal(err)
	}
	defer release()
	snapshot := metadataNewQuerySnapshot(db)
	defer snapshot.Close()

	err = os.Remove(oldPath)
	if err != nil {
		t.Fatal(err)
	}
	mustWriteMetadataTestFile(t, filepath.Join(driveRoot, "new.txt"), "new")
	err = metadataScanAndPublish(context.Background(), driveRoot, driveRoot, databasePath, limiter, 10)
	if err != nil {
		t.Fatal(err)
	}
	_, oldFound, err := metadataReadEntry(snapshot, metadataPathKey([]string{"old.txt"}))
	if err != nil || !oldFound {
		t.Fatalf("snapshot lost old entry: found=%t err=%v", oldFound, err)
	}
	_, newFound, err := metadataReadEntry(snapshot, metadataPathKey([]string{"new.txt"}))
	if err != nil || newFound {
		t.Fatalf("snapshot observed new entry: found=%t err=%v", newFound, err)
	}
	complete, err := metadataAggregatesComplete(snapshot)
	if err != nil || !complete {
		t.Fatalf("snapshot lost aggregate completeness: complete=%t err=%v", complete, err)
	}
	_, oldFound, err = metadataReadEntry(db, metadataPathKey([]string{"old.txt"}))
	if err != nil || oldFound {
		t.Fatalf("current catalog retained old entry: found=%t err=%v", oldFound, err)
	}
	_, newFound, err = metadataReadEntry(db, metadataPathKey([]string{"new.txt"}))
	if err != nil || !newFound {
		t.Fatalf("current catalog missed new entry: found=%t err=%v", newFound, err)
	}
}

func TestMetadataMissingAncestorRequiresRepair(t *testing.T) {
	db := openMetadataTestDB(t, filepath.Join(t.TempDir(), "catalog"))
	defer db.Close()
	root := MetadataEntry{EntryType: MetaEntryDirectory, RecursiveFileCount: 3}
	err := db.Set(metadataPathKey(nil), root.Encode(), pebble.Sync)
	if err != nil {
		t.Fatal(err)
	}
	complete, err := metadataAggregatesComplete(db)
	if err != nil || complete {
		t.Fatalf("unverified catalog complete=%t err=%v", complete, err)
	}
	err = db.Set(metadataAggregateVersionKey, metadataAggregateVersion, pebble.Sync)
	if err != nil {
		t.Fatal(err)
	}
	complete, err = metadataAggregatesComplete(db)
	if err != nil || !complete {
		t.Fatalf("verified catalog complete=%t err=%v", complete, err)
	}
	err = db.Set(metadataPendingAggregateKey, []byte("pending"), pebble.Sync)
	if err != nil {
		t.Fatal(err)
	}
	err = metadataUpdateAncestors(db, []string{"missing", "child"}, metadataAggregate{}, metadataAggregate{files: 1}, 1)
	if !errors.Is(err, errMetadataAggregateRepairRequired) {
		t.Fatalf("expected repair error, got %v", err)
	}
	assertMetadataTestKey(t, db, metadataPendingAggregateKey, true)
	complete, err = metadataAggregatesComplete(db)
	if err != nil || complete {
		t.Fatalf("pending catalog complete=%t err=%v", complete, err)
	}
	root = readMetadataTestEntry(t, db, metadataPathKey(nil))
	if root.RecursiveFileCount != 3 {
		t.Fatalf("root was modified despite missing ancestor: %d", root.RecursiveFileCount)
	}
}

func TestMetadataCoverageTracksPendingNameRefresh(t *testing.T) {
	oldConfig := shared.ServiceConfig
	shared.ServiceConfig = &config.ServicesConfigurationKubernetes{}
	shared.ServiceConfig.FileSystem.MountPoint = t.TempDir()
	defer func() { shared.ServiceConfig = oldConfig }()
	driveId := "coverage-test"
	databasePath := metadataDatabasePath(driveId)
	err := os.MkdirAll(filepath.Dir(databasePath), 0o700)
	if err != nil {
		t.Fatal(err)
	}
	db, release, err := metadataAcquireDatabase(databasePath, true)
	if err != nil {
		t.Fatal(err)
	}
	defer release()
	root := MetadataEntry{EntryType: MetaEntryDirectory}
	err = db.Set(metadataPathKey(nil), root.Encode(), pebble.Sync)
	if err != nil {
		t.Fatal(err)
	}
	for _, item := range []struct{ key, value []byte }{
		{key: metadataAggregateVersionKey, value: metadataAggregateVersion},
		{key: metadataSearchIndexVersionKey, value: metadataSearchIndexVersion},
		{key: metadataPendingNameKey, value: metadataPathKey(nil)},
	} {
		err = db.Set(item.key, item.value, pebble.Sync)
		if err != nil {
			t.Fatal(err)
		}
	}
	coverage, searchReady := metadataCatalogCoverage(driveId)
	if !coverage || searchReady {
		t.Fatalf("pending refresh: coverage=%t searchReady=%t", coverage, searchReady)
	}
	err = db.Delete(metadataPendingNameKey, pebble.Sync)
	if err != nil {
		t.Fatal(err)
	}
	coverage, searchReady = metadataCatalogCoverage(driveId)
	if !coverage || !searchReady {
		t.Fatalf("completed refresh: coverage=%t searchReady=%t", coverage, searchReady)
	}
}

func TestMetadataSearchOnlyBlocksOverlappingPublications(t *testing.T) {
	db := openMetadataTestDB(t, filepath.Join(t.TempDir(), "catalog"))
	defer db.Close()
	root := MetadataEntry{EntryType: MetaEntryDirectory}
	err := db.Set(metadataPathKey(nil), root.Encode(), pebble.Sync)
	if err != nil {
		t.Fatal(err)
	}
	for _, item := range []struct{ key, value []byte }{
		{key: metadataAggregateVersionKey, value: metadataAggregateVersion},
		{key: metadataSearchIndexVersionKey, value: metadataSearchIndexVersion},
		{key: metadataPendingNameKey, value: metadataPathKey([]string{"a"})},
	} {
		err = db.Set(item.key, item.value, pebble.Sync)
		if err != nil {
			t.Fatal(err)
		}
	}
	pending := metadataPendingAggregate{rootKey: metadataPathKey([]string{"a"})}
	err = db.Set(metadataPendingAggregateKey, pending.encode(), pebble.Sync)
	if err != nil {
		t.Fatal(err)
	}
	for _, test := range []struct {
		folder   []string
		affected bool
	}{
		{folder: nil, affected: true},
		{folder: []string{"a"}, affected: true},
		{folder: []string{"a", "nested"}, affected: true},
		{folder: []string{"b"}, affected: false},
		{folder: []string{"ab"}, affected: false},
	} {
		complete, aggregateErr := metadataAggregatesCompleteForSearch(db, test.folder)
		affected, nameErr := metadataNameRefreshAffectsSearch(db, test.folder)
		if aggregateErr != nil || nameErr != nil || complete == test.affected || affected != test.affected {
			t.Fatalf("folder %q: complete=%t affected=%t aggregateErr=%v nameErr=%v", test.folder, complete, affected, aggregateErr, nameErr)
		}
	}
	err = db.Delete(metadataAggregateVersionKey, pebble.Sync)
	if err != nil {
		t.Fatal(err)
	}
	complete, err := metadataAggregatesCompleteForSearch(db, []string{"b"})
	if err != nil || complete {
		t.Fatalf("unverified catalog: complete=%t err=%v", complete, err)
	}
	err = db.Delete(metadataSearchIndexVersionKey, pebble.Sync)
	if err != nil {
		t.Fatal(err)
	}
	affected, err := metadataNameRefreshAffectsSearch(db, []string{"b"})
	if err != nil || !affected {
		t.Fatalf("unverified search index: affected=%t err=%v", affected, err)
	}
}

func TestMetadataDeletionCancelsAndDrains(t *testing.T) {
	oldConfig := shared.ServiceConfig
	shared.ServiceConfig = &config.ServicesConfigurationKubernetes{}
	shared.ServiceConfig.FileSystem.MountPoint = t.TempDir()
	defer func() { shared.ServiceConfig = oldConfig }()
	driveId := "deletion-test"
	databasePath := metadataDatabasePath(driveId)
	err := os.MkdirAll(filepath.Dir(databasePath), 0o700)
	if err != nil {
		t.Fatal(err)
	}
	_, release, err := metadataAcquireDatabase(databasePath, true)
	if err != nil {
		t.Fatal(err)
	}
	var releaseOnce sync.Once
	defer releaseOnce.Do(release)
	ctx, finish, err := metadataBeginScan(databasePath)
	if err != nil {
		t.Fatal(err)
	}
	var finishOnce sync.Once
	defer finishOnce.Do(finish)
	deleted := make(chan error, 1)
	go func() { deleted <- metadataBeginCatalogDeletion(context.Background(), driveId) }()
	select {
	case <-ctx.Done():
	case <-time.After(5 * time.Second):
		t.Fatal("deletion did not cancel the scan")
	}
	_, _, err = metadataAcquireDatabase(databasePath, false)
	if !errors.Is(err, errMetadataCatalogDeleting) {
		t.Fatalf("expected new opens to be blocked, got %v", err)
	}
	finishOnce.Do(finish)
	select {
	case err = <-deleted:
		t.Fatalf("deletion finished while a database user remained: %v", err)
	default:
	}
	releaseOnce.Do(release)
	select {
	case err = <-deleted:
		if err != nil {
			t.Fatal(err)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("deletion did not drain")
	}
	metadataFinishCatalogDeletion(driveId, false)
	_, release, err = metadataAcquireDatabase(databasePath, false)
	if err != nil {
		t.Fatalf("failed deletion did not restore access: %v", err)
	}
	release()
	err = metadataBeginCatalogDeletion(context.Background(), driveId)
	if err != nil {
		t.Fatal(err)
	}
	metadataFinishCatalogDeletion(driveId, true)
	_, err = os.Stat(databasePath)
	if !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("catalog was not removed: %v", err)
	}
	_, _, err = metadataAcquireDatabase(databasePath, true)
	if !errors.Is(err, errMetadataCatalogDeleting) {
		t.Fatalf("deleted catalog reopened: %v", err)
	}
	err = metadataBeginCatalogDeletion(context.Background(), driveId)
	if !errors.Is(err, errMetadataCatalogDeleted) {
		t.Fatalf("deleted catalog did not return the idempotent deletion state: %v", err)
	}
}

func TestMetadataDeletionCancellationRestoresAccess(t *testing.T) {
	oldConfig := shared.ServiceConfig
	shared.ServiceConfig = &config.ServicesConfigurationKubernetes{}
	shared.ServiceConfig.FileSystem.MountPoint = t.TempDir()
	defer func() { shared.ServiceConfig = oldConfig }()
	driveId := "cancel-deletion-test"
	databasePath := metadataDatabasePath(driveId)
	err := os.MkdirAll(databasePath, 0o700)
	if err != nil {
		t.Fatal(err)
	}
	marker := filepath.Join(databasePath, "marker")
	mustWriteMetadataTestFile(t, marker, "data")
	scanCtx, finish, err := metadataBeginScan(databasePath)
	if err != nil {
		t.Fatal(err)
	}
	defer finish()
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	result := make(chan error, 1)
	go func() { result <- metadataBeginCatalogDeletion(ctx, driveId) }()
	select {
	case <-scanCtx.Done():
	case <-time.After(5 * time.Second):
		t.Fatal("deletion did not cancel the scan")
	}
	cancel()
	select {
	case err = <-result:
		if !errors.Is(err, context.Canceled) {
			t.Fatalf("expected cancellation, got %v", err)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("canceled deletion did not return")
	}
	_, finishNext, err := metadataBeginScan(databasePath)
	if err != nil {
		t.Fatalf("catalog remained blocked after cancellation: %v", err)
	}
	finishNext()
	_, err = os.Stat(marker)
	if err != nil {
		t.Fatalf("canceled deletion changed the catalog: %v", err)
	}
}

func TestMetadataCanceledDriveDeletionDoesNotStageData(t *testing.T) {
	oldConfig := shared.ServiceConfig
	shared.ServiceConfig = &config.ServicesConfigurationKubernetes{}
	shared.ServiceConfig.FileSystem.MountPoint = t.TempDir()
	shared.ServiceConfig.FileSystem.TrashStagingArea = filepath.Join(shared.ServiceConfig.FileSystem.MountPoint, "trash")
	defer func() { shared.ServiceConfig = oldConfig }()
	drive := orc.Drive{Resource: orc.Resource{Id: "cancel-drive-test"}}
	drivePath, ok, _ := DriveToLocalPath(&drive)
	if !ok {
		t.Fatal("unable to resolve test drive")
	}
	err := os.MkdirAll(drivePath, 0o700)
	if err != nil {
		t.Fatal(err)
	}
	err = os.MkdirAll(shared.ServiceConfig.FileSystem.TrashStagingArea, 0o700)
	if err != nil {
		t.Fatal(err)
	}
	scanCtx, finish, err := metadataBeginScan(metadataDatabasePath(drive.Id))
	if err != nil {
		t.Fatal(err)
	}
	defer finish()
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	result := make(chan *util.HttpError, 1)
	go func() { result <- deleteDrive(ctx, rpc.Actor{}, drive) }()
	select {
	case <-scanCtx.Done():
	case <-time.After(5 * time.Second):
		t.Fatal("deletion did not cancel the scan")
	}
	cancel()
	select {
	case deleteErr := <-result:
		if deleteErr == nil {
			t.Fatal("canceled drive deletion succeeded")
		}
	case <-time.After(5 * time.Second):
		t.Fatal("canceled drive deletion did not return")
	}
	_, err = os.Stat(drivePath)
	if err != nil {
		t.Fatalf("canceled deletion staged the drive: %v", err)
	}
	staged, err := os.ReadDir(shared.ServiceConfig.FileSystem.TrashStagingArea)
	if err != nil || len(staged) != 0 {
		t.Fatalf("canceled deletion changed the staging area: entries=%d err=%v", len(staged), err)
	}
}

func TestMetadataInitializationReusesActiveRootScan(t *testing.T) {
	requests := make(chan metadataScanRequest)
	ready := make(chan metadataScanRequest)
	done := make(chan metadataScanResult)
	stopped := make(chan struct{})
	go func() {
		metadataDispatchScans(requests, ready, done)
		close(stopped)
	}()
	defer func() {
		close(requests)
		<-stopped
	}()
	drive := &orc.Drive{Resource: orc.Resource{Id: "drive"}}
	requests <- metadataScanRequest{drive: drive, internalPath: "/root"}
	<-ready
	completed := make(chan error, 1)
	requests <- metadataScanRequest{
		drive:        drive,
		internalPath: "/root",
		ensure:       true,
		completions:  []func(error, time.Time){func(err error, _ time.Time) { completed <- err }},
	}
	requests <- metadataScanRequest{drive: drive, internalPath: "/root/changed"}
	done <- metadataScanResult{driveId: drive.Id}
	select {
	case err := <-completed:
		if err != nil {
			t.Fatal(err)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("initialization did not reuse the active scan")
	}
	select {
	case next := <-ready:
		if next.internalPath != "/root/changed" {
			t.Fatalf("expected mutation rescan, got %q", next.internalPath)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("mutation rescan was lost")
	}
	done <- metadataScanResult{driveId: drive.Id}
}

func TestMetadataScanCallbacksDoNotBlockOtherDrives(t *testing.T) {
	requests := make(chan metadataScanRequest)
	ready := make(chan metadataScanRequest)
	done := make(chan metadataScanResult)
	stopped := make(chan struct{})
	go func() {
		metadataDispatchScans(requests, ready, done)
		close(stopped)
	}()
	blocked := make(chan struct{})
	var unblock sync.Once
	defer func() {
		unblock.Do(func() { close(blocked) })
		close(requests)
		<-stopped
	}()
	first := &orc.Drive{Resource: orc.Resource{Id: "first"}}
	second := &orc.Drive{Resource: orc.Resource{Id: "second"}}
	started := make(chan struct{})
	requests <- metadataScanRequest{
		drive:        first,
		internalPath: "/first",
		completions: []func(error, time.Time){func(error, time.Time) {
			close(started)
			<-blocked
		}},
	}
	<-ready
	done <- metadataScanResult{driveId: first.Id}
	<-started
	select {
	case requests <- metadataScanRequest{drive: second, internalPath: "/second"}:
	case <-time.After(5 * time.Second):
		t.Fatal("blocked callback prevented another drive from being submitted")
	}
	select {
	case next := <-ready:
		if next.drive.Id != second.Id {
			t.Fatalf("expected second drive, got %q", next.drive.Id)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("blocked callback prevented another drive from running")
	}
	done <- metadataScanResult{driveId: second.Id}
	requests <- metadataScanRequest{drive: first, internalPath: "/first/changed"}
	select {
	case next := <-ready:
		t.Fatalf("same-drive scan started before the callback completed: %q", next.internalPath)
	case <-time.After(50 * time.Millisecond):
	}
	unblock.Do(func() { close(blocked) })
	select {
	case next := <-ready:
		if next.internalPath != "/first/changed" {
			t.Fatalf("unexpected pending scan %q", next.internalPath)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("same-drive scan did not resume after the callback")
	}
	done <- metadataScanResult{driveId: first.Id}
}

func TestMetadataInitializationReusesScanWhileCallbackFinishes(t *testing.T) {
	requests := make(chan metadataScanRequest)
	ready := make(chan metadataScanRequest)
	done := make(chan metadataScanResult)
	stopped := make(chan struct{})
	go func() {
		metadataDispatchScans(requests, ready, done)
		close(stopped)
	}()
	blocked := make(chan struct{})
	var unblock sync.Once
	defer func() {
		unblock.Do(func() { close(blocked) })
		close(requests)
		<-stopped
	}()
	drive := &orc.Drive{Resource: orc.Resource{Id: "drive"}}
	started := make(chan struct{})
	requests <- metadataScanRequest{
		drive:        drive,
		internalPath: "/root",
		completions: []func(error, time.Time){func(error, time.Time) {
			close(started)
			<-blocked
		}},
	}
	<-ready
	finishedAt := time.Unix(123, 0)
	done <- metadataScanResult{driveId: drive.Id, finishedAt: finishedAt}
	<-started
	completed := make(chan struct {
		err        error
		finishedAt time.Time
	}, 1)
	requests <- metadataScanRequest{
		drive:        drive,
		internalPath: "/root",
		ensure:       true,
		completions: []func(error, time.Time){func(err error, at time.Time) {
			completed <- struct {
				err        error
				finishedAt time.Time
			}{err: err, finishedAt: at}
		}},
	}
	select {
	case result := <-completed:
		if result.err != nil || !result.finishedAt.Equal(finishedAt) {
			t.Fatalf("completion err=%v finishedAt=%v", result.err, result.finishedAt)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("initialization did not reuse the completed scan")
	}
	unblock.Do(func() { close(blocked) })
}

func TestMetadataSubmissionCoverageInvalidates(t *testing.T) {
	oldConfig := shared.ServiceConfig
	shared.ServiceConfig = &config.ServicesConfigurationKubernetes{}
	shared.ServiceConfig.FileSystem.MountPoint = t.TempDir()
	defer func() { shared.ServiceConfig = oldConfig }()
	driveId := "coverage-cache-test"
	databasePath := metadataDatabasePath(driveId)
	driveRoot := t.TempDir()
	err := metadataScanAndPublish(context.Background(), driveRoot, driveRoot, databasePath, rate.NewLimiter(rate.Inf, 1), 10)
	if err != nil {
		t.Fatal(err)
	}
	if !metadataHasCoverageForSubmission(driveId) {
		t.Fatal("expected a verified catalog")
	}
	metadataDatabases.Lock()
	lifecycle := metadataCatalogLifecycleLocked(databasePath)
	cached := lifecycle.coverageValid
	metadataDatabases.Unlock()
	if !cached {
		t.Fatal("expected verified coverage to be cached")
	}
	err = metadataScanAndPublish(context.Background(), driveRoot, driveRoot, databasePath, rate.NewLimiter(rate.Inf, 1), 10)
	if err != nil {
		t.Fatal(err)
	}
	metadataDatabases.Lock()
	cached = lifecycle.coverageValid
	metadataDatabases.Unlock()
	if cached {
		t.Fatal("publication did not invalidate the coverage cache")
	}
	if !metadataHasCoverageForSubmission(driveId) {
		t.Fatal("expected coverage to be restored after publication")
	}
	db, release, err := metadataAcquireDatabase(databasePath, false)
	if err != nil {
		t.Fatal(err)
	}
	defer release()
	pending := metadataPendingAggregate{rootKey: metadataPathKey(nil)}
	err = db.Set(metadataPendingAggregateKey, pending.encode(), pebble.Sync)
	if err != nil {
		t.Fatal(err)
	}
	metadataInvalidateCoverage(databasePath)
	if metadataHasCoverageForSubmission(driveId) {
		t.Fatal("pending aggregate was ignored after cache invalidation")
	}
}

func TestMetadataConcurrentPublicationsKeepBothIndexes(t *testing.T) {
	driveRoot := t.TempDir()
	databasePath := filepath.Join(t.TempDir(), "catalog")
	limiter := rate.NewLimiter(rate.Inf, 1)
	for _, directory := range []string{"a", "b"} {
		err := os.Mkdir(filepath.Join(driveRoot, directory), 0o700)
		if err != nil {
			t.Fatal(err)
		}
	}
	err := metadataScanAndPublish(context.Background(), driveRoot, driveRoot, databasePath, limiter, 10)
	if err != nil {
		t.Fatal(err)
	}
	for iteration := 0; iteration < 5; iteration++ {
		for _, directory := range []string{"a", "b"} {
			if iteration > 0 {
				oldPath := filepath.Join(driveRoot, directory, fmt.Sprintf("entry-%d.txt", iteration-1))
				err = os.Remove(oldPath)
				if err != nil {
					t.Fatal(err)
				}
			}
			name := fmt.Sprintf("entry-%d.txt", iteration)
			mustWriteMetadataTestFile(t, filepath.Join(driveRoot, directory, name), "data")
		}
		results := make(chan error, 2)
		for _, directory := range []string{"a", "b"} {
			path := filepath.Join(driveRoot, directory)
			go func() {
				results <- metadataScanAndPublish(context.Background(), path, driveRoot, databasePath, limiter, 10)
			}()
		}
		for range 2 {
			err = <-results
			if err != nil {
				t.Fatal(err)
			}
		}
		func() {
			db := openMetadataTestDB(t, databasePath)
			defer db.Close()
			pending, pendingErr := metadataNameRefreshPending(db)
			if pendingErr != nil || pending {
				t.Fatalf("name index pending=%t err=%v", pending, pendingErr)
			}
			assertMetadataTestKey(t, db, metadataPendingAggregateKey, false)
			root := readMetadataTestEntry(t, db, metadataPathKey(nil))
			if root.RecursiveFileCount != 2 {
				t.Fatalf("root file count is %d, want 2", root.RecursiveFileCount)
			}
			for _, directory := range []string{"a", "b"} {
				key := metadataPathKey([]string{directory, fmt.Sprintf("entry-%d.txt", iteration)})
				assertMetadataTestKey(t, db, key, true)
				indexKeys, keyErr := metadataSearchIndexKeys(key)
				if keyErr != nil {
					t.Fatal(keyErr)
				}
				for _, indexKey := range indexKeys {
					assertMetadataTestKey(t, db, indexKey, true)
				}
			}
		}()
	}
}

func TestMetadataVisualizationBoundsCandidates(t *testing.T) {
	db := openMetadataTestDB(t, filepath.Join(t.TempDir(), "catalog"))
	defer db.Close()
	for i := range 100 {
		entry := MetadataEntry{EntryType: MetaEntryRegular, LogicalSize: uint64(i)}
		key := metadataPathKey([]string{fmt.Sprintf("file-%03d", i)})
		err := db.Set(key, entry.Encode(), pebble.NoSync)
		if err != nil {
			t.Fatal(err)
		}
	}
	queue := &filesVisualizationQueue{}
	heap.Init(queue)
	complete, err := metadataVisualizationChildren(db, "drive", nil, time.Now().Add(time.Second), queue, 3)
	if err != nil || complete || queue.Len() != 3 {
		t.Fatalf("complete=%t candidates=%d err=%v", complete, queue.Len(), err)
	}
	for _, expected := range []uint64{97, 98, 99} {
		candidate := heap.Pop(queue).(filesVisualizationCandidate)
		if candidate.entry.LogicalSize != expected {
			t.Fatalf("candidate size is %d, want %d", candidate.entry.LogicalSize, expected)
		}
	}
}
