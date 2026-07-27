package filesystem

import (
	"hash/fnv"
	"os"
	"path/filepath"
	"sort"
	"time"

	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/util"
)

const (
	metadataScannerPollInterval = time.Minute
	metadataScannerRetryDelay   = 5 * time.Minute
	metadataActiveInterval      = 10 * time.Minute
	metadataHotInterval         = 3 * time.Hour
	metadataWarmInterval        = 24 * time.Hour
	metadataColdInterval        = 7 * 24 * time.Hour
	metadataMountActiveWindow   = 45 * time.Minute
	metadataDirectActiveWindow  = 15 * time.Minute
)

type metadataActivityRow struct {
	UCloudPath string
	Kind       ActivityKind
	StartedAt  time.Time
	EventCount int
}

type metadataScanCandidate struct {
	driveID    string
	ucloudPath string
	interval   time.Duration
	states     []metadataScanState
}

type metadataScanState struct {
	driveID    string
	ucloudPath string
}

func MetadataStartScanner() {
	go func() {
		for util.IsAlive {
			metadataScheduleScans(time.Now())
			time.Sleep(metadataScannerPollInterval)
		}
	}()
}

func metadataScheduleScans(now time.Time) {
	candidates := metadataActivityScanCandidates(now)
	candidates = append(candidates, metadataColdScanCandidates()...)
	claimed := candidates[:0]
	for _, candidate := range candidates {
		interval := candidate.interval + metadataScanJitter(candidate.ucloudPath, candidate.interval)
		if !metadataClaimScheduledScan(candidate, now, interval) {
			continue
		}
		candidate.states = append(candidate.states, metadataScanState{driveID: candidate.driveID, ucloudPath: candidate.ucloudPath})
		claimed = append(claimed, candidate)
	}

	for _, candidate := range metadataCollapseScanCandidates(claimed) {
		candidate := candidate
		metadataSubmitScanRequest(candidate.ucloudPath, func(scanErr error) {
			if scanErr == nil {
				metadataCompleteScheduledScans(candidate.states, time.Now())
			}
		})
	}
}

func metadataActivityScanCandidates(now time.Time) []metadataScanCandidate {
	rows := db.NewTx[[]metadataActivityRow](func(tx *db.Transaction) []metadataActivityRow {
		return db.Select[metadataActivityRow](tx, `
			select
				t.ucloud_path,
				e.kind,
				max(e.started_at) as started_at,
				count(distinct e.event_id)::int as event_count
			from
				fs_activity_events e
				join fs_activity_event_targets t on t.event_id = e.event_id
			where e.started_at >= :oldest
			group by t.ucloud_path, e.kind
			order by max(e.started_at) desc
		`, db.Params{"oldest": now.Add(-ActivityWarmWindow)})
	})

	result := make([]metadataScanCandidate, 0, len(rows))
	for _, row := range rows {
		interval := metadataWarmInterval
		age := now.Sub(row.StartedAt)
		if age < 0 {
			continue
		}
		if row.Kind == ActivityMount && age <= metadataMountActiveWindow {
			interval = metadataActiveInterval
		} else if row.Kind == ActivityDirect && age <= metadataDirectActiveWindow {
			interval = metadataActiveInterval
		} else if age <= ActivityHotWindow && row.EventCount >= ActivityMinimumHotOps {
			interval = metadataHotInterval
		}

		candidate, ok := metadataResolveScanCandidate(row.UCloudPath, row.Kind, interval)
		if ok {
			result = append(result, candidate)
		}
	}
	return result
}

func metadataColdScanCandidates() []metadataScanCandidate {
	driveIDs := db.NewTx[[]string](func(tx *db.Transaction) []string {
		rows := db.Select[struct{ DriveId string }](tx, `
			select drive_id
			from tracked_drives
			where product_id != 'share'
		`, db.Params{})
		result := make([]string, 0, len(rows))
		for _, row := range rows {
			result = append(result, row.DriveId)
		}
		return result
	})

	result := make([]metadataScanCandidate, 0, len(driveIDs))
	for _, driveID := range driveIDs {
		drive, ok := ResolveDrive(driveID)
		if !ok {
			continue
		}
		if _, ok, resolved := DriveToLocalPath(drive); !ok || resolved.Id != drive.Id {
			continue
		}
		result = append(result, metadataScanCandidate{
			driveID:    drive.Id,
			ucloudPath: "/" + drive.Id,
			interval:   metadataColdInterval,
		})
	}
	return result
}

func metadataResolveScanCandidate(ucloudPath string, kind ActivityKind, interval time.Duration) (metadataScanCandidate, bool) {
	internalPath, ok, drive := UCloudToInternal(ucloudPath)
	if !ok || drive == nil {
		return metadataScanCandidate{}, false
	}
	driveRoot, ok, resolvedDrive := DriveToLocalPath(drive)
	if !ok || resolvedDrive == nil {
		return metadataScanCandidate{}, false
	}

	scanRoot := filepath.Clean(internalPath)
	if kind == ActivityDirect {
		scanRoot = filepath.Dir(scanRoot)
	} else if info, err := os.Stat(scanRoot); err != nil || !info.IsDir() {
		scanRoot = filepath.Dir(scanRoot)
	}
	// A direct operation on the drive root selects its parent. Clamp it before doing any filesystem lookup so a scan
	// candidate can never escape the resolved drive, even temporarily.
	scanRoot = metadataClampScanRoot(driveRoot, scanRoot)
	scanRoot, ok = metadataNearestExistingDirectory(driveRoot, scanRoot)
	if !ok {
		return metadataScanCandidate{}, false
	}
	canonicalPath, ok := InternalToUCloudWithDrive(resolvedDrive, scanRoot)
	if !ok {
		return metadataScanCandidate{}, false
	}
	return metadataScanCandidate{driveID: resolvedDrive.Id, ucloudPath: canonicalPath, interval: interval}, true
}

func metadataClampScanRoot(driveRoot, scanRoot string) string {
	driveRoot = filepath.Clean(driveRoot)
	scanRoot = filepath.Clean(scanRoot)
	if !metadataPathContains(driveRoot, scanRoot) {
		return driveRoot
	}
	return scanRoot
}

func metadataNearestExistingDirectory(driveRoot, candidate string) (string, bool) {
	driveRoot = filepath.Clean(driveRoot)
	candidate = filepath.Clean(candidate)
	if !metadataPathContains(driveRoot, candidate) {
		candidate = driveRoot
	}
	for {
		if info, err := os.Stat(candidate); err == nil && info.IsDir() {
			return candidate, true
		}
		if candidate == driveRoot {
			return "", false
		}
		parent := filepath.Dir(candidate)
		if parent == candidate || !metadataPathContains(driveRoot, parent) {
			candidate = driveRoot
		} else {
			candidate = parent
		}
	}
}

func metadataCollapseScanCandidates(candidates []metadataScanCandidate) []metadataScanCandidate {
	sort.Slice(candidates, func(i, j int) bool {
		if candidates[i].driveID != candidates[j].driveID {
			return candidates[i].driveID < candidates[j].driveID
		}
		leftDepth := len(util.Components(candidates[i].ucloudPath))
		rightDepth := len(util.Components(candidates[j].ucloudPath))
		if leftDepth != rightDepth {
			return leftDepth < rightDepth
		}
		return candidates[i].ucloudPath < candidates[j].ucloudPath
	})

	result := make([]metadataScanCandidate, 0, len(candidates))
	for _, candidate := range candidates {
		covered := false
		for i := range result {
			if result[i].driveID == candidate.driveID && metadataUCloudPathContains(result[i].ucloudPath, candidate.ucloudPath) {
				result[i].interval = min(result[i].interval, candidate.interval)
				result[i].states = append(result[i].states, candidate.states...)
				covered = true
				break
			}
		}
		if !covered {
			result = append(result, candidate)
		}
	}
	return result
}

func metadataUCloudPathContains(parent, child string) bool {
	return metadataPathContains(filepath.FromSlash(parent), filepath.FromSlash(child))
}

func metadataClaimScheduledScan(candidate metadataScanCandidate, now time.Time, interval time.Duration) bool {
	return db.NewTx[bool](func(tx *db.Transaction) bool {
		_, ok := db.Get[struct{ DriveId string }](tx, `
			insert into fs_metadata_scan_state(
				drive_id, ucloud_path, last_submitted_at, last_completed_at
			)
			values (:drive_id, :ucloud_path, :now, timestamp 'epoch')
			on conflict (drive_id, ucloud_path) do update
			set last_submitted_at = excluded.last_submitted_at
			where
				fs_metadata_scan_state.last_completed_at < :completed_before
				and (
					fs_metadata_scan_state.last_submitted_at <= fs_metadata_scan_state.last_completed_at
					or fs_metadata_scan_state.last_submitted_at < :retry_before
				)
			returning drive_id
		`, db.Params{
			"drive_id":         candidate.driveID,
			"ucloud_path":      candidate.ucloudPath,
			"now":              now,
			"completed_before": now.Add(-interval),
			"retry_before":     now.Add(-metadataScannerRetryDelay),
		})
		return ok
	})
}

func metadataCompleteScheduledScans(states []metadataScanState, completedAt time.Time) {
	db.NewTx0(func(tx *db.Transaction) {
		for _, state := range states {
			db.Exec(tx, `
				update fs_metadata_scan_state
				set last_completed_at = :completed_at
				where drive_id = :drive_id and ucloud_path = :ucloud_path
			`, db.Params{
				"completed_at": completedAt,
				"drive_id":     state.driveID,
				"ucloud_path":  state.ucloudPath,
			})
		}
	})
}

func metadataScanJitter(path string, interval time.Duration) time.Duration {
	var maximum time.Duration
	switch interval {
	case metadataHotInterval:
		maximum = 30 * time.Minute
	case metadataWarmInterval:
		maximum = 2 * time.Hour
	case metadataColdInterval:
		maximum = 12 * time.Hour
	default:
		return 0
	}
	hash := fnv.New64a()
	_, _ = hash.Write([]byte(path))
	return time.Duration(hash.Sum64() % uint64(maximum))
}
