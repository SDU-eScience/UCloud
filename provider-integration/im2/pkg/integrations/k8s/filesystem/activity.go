package filesystem

import (
	"path/filepath"
	"time"

	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type ActivityKind int

const (
	ActivityDirect ActivityKind = iota
	ActivityMount
)

type ActivityOperation string

const (
	ActivityOperationUpload   ActivityOperation = "upload"
	ActivityOperationDownload ActivityOperation = "download"
	ActivityOperationCreate   ActivityOperation = "create"
	ActivityOperationMove     ActivityOperation = "move"
	ActivityOperationCopy     ActivityOperation = "copy"
	ActivityOperationTrash    ActivityOperation = "trash"
	ActivityOperationDelete   ActivityOperation = "delete"
	ActivityOperationMount    ActivityOperation = "mount"
)

type ActivityTarget struct {
	DriveId    string
	UCloudPath string
	Role       string
}

type ActivityEvent struct {
	Kind        ActivityKind
	Operation   ActivityOperation
	Targets     []ActivityTarget
	StartedAt   time.Time
	ReadOnly    bool
	PerformedBy util.Option[string]
	JobId       util.Option[string]
	Pod         util.Option[string]
	Node        util.Option[string]
}

func activityTargetFromUCloudPath(value string) (ActivityTarget, bool) {
	normalized := filepath.Clean(value)

	internalPath, ok, originDrive := UCloudToInternal(normalized)
	if !ok || originDrive == nil {
		return ActivityTarget{}, false
	}

	// TODO Deal with shares
	canonicalPath := normalized
	_ = internalPath

	return ActivityTarget{
		DriveId:    originDrive.Id,
		UCloudPath: canonicalPath,
	}, true
}

type ActivityHeat string

const (
	ActivityHeatHot  ActivityHeat = "Hot"
	ActivityHeatWarm ActivityHeat = "Warm"
	ActivityHeatCold ActivityHeat = "Cold"
)

const (
	ActivityHotWindow     = 1 * time.Hour
	ActivityWarmWindow    = 24 * time.Hour
	ActivityMinimumHotOps = 32
)

func ActivityClassifyHeat(ucloudPath string) ActivityHeat {
	target, ok := activityTargetFromUCloudPath(ucloudPath)
	if !ok {
		return ActivityHeatCold
	}

	type rowType struct {
		EventCount     int
		LastMount      time.Time
		LastDirectOp   time.Time
		MountCount     int
		OperationCount int
	}

	row, ok := db.NewTx2(func(tx *db.Transaction) (rowType, bool) {
		ancestorsAndSelf := util.Parents(target.UCloudPath)
		if len(ancestorsAndSelf) == 0 {
			return rowType{}, false
		}

		for i, parent := range ancestorsAndSelf {
			t, _ := activityTargetFromUCloudPath(parent)
			ancestorsAndSelf[i] = t.UCloudPath
		}
		ancestorsAndSelf = append(ancestorsAndSelf, target.UCloudPath)

		return db.Get[rowType](
			tx,
			`
				select
					count(distinct e.event_id)::int as event_count,
					coalesce(max(started_at) filter (where kind = 1), timestamp 'epoch') as last_mount,
					coalesce(max(started_at) filter (where kind = 0), timestamp 'epoch') as last_direct_op,
					count(distinct e.event_id) filter (where kind = 1)::int as mount_count,
					count(distinct e.event_id) filter (where kind = 0)::int as operation_count
				from 
				    fs_activity_events e
					join fs_activity_event_targets t on t.event_id = e.event_id
				where 
				    t.ucloud_path = some(:ancestors_and_self)
			`,
			db.Params{
				"ancestors_and_self": ancestorsAndSelf,
			},
		)
	})

	if !ok || row.EventCount == 0 {
		return ActivityHeatCold
	}

	latest := row.LastDirectOp
	if row.LastMount.After(latest) {
		latest = row.LastMount
	}

	age := time.Since(latest)
	if age < 0 {
		return ActivityHeatCold
	}

	opsCount := row.MountCount + row.EventCount
	if age <= ActivityHotWindow && opsCount >= ActivityMinimumHotOps {
		return ActivityHeatHot
	}

	if opsCount > 0 && age <= ActivityWarmWindow {
		return ActivityHeatWarm
	}

	return ActivityHeatCold
}

func ActivityRecord(actor rpc.Actor, event ActivityEvent) {
	event.PerformedBy = util.OptStringIfNotEmpty(actor.Username)

	targets := make([]ActivityTarget, 0, len(event.Targets))
	for _, target := range event.Targets {
		path := target.UCloudPath
		resolved, ok := activityTargetFromUCloudPath(path)
		if !ok {
			return
		}
		resolved.Role = target.Role
		targets = append(targets, resolved)
	}

	if event.StartedAt.IsZero() {
		event.StartedAt = time.Now()
	}

	db.NewTx0(func(tx *db.Transaction) {
		row, _ := db.Get[struct{ EventId int64 }](
			tx,
			`
				insert into fs_activity_events(kind, operation, started_at, read_only, performed_by, job_id, pod, node)
				values (:kind, :operation, :started_at, :read_only, :performed_by, :job_id, :pod, :node)
				returning event_id
			`,
			db.Params{
				"kind":         event.Kind,
				"operation":    event.Operation,
				"started_at":   event.StartedAt,
				"read_only":    event.ReadOnly,
				"performed_by": event.PerformedBy.Sql(),
				"job_id":       event.JobId.Sql(),
				"pod":          event.Pod.Sql(),
				"node":         event.Node.Sql(),
			},
		)

		for _, target := range targets {
			db.Exec(tx, `
				insert into fs_activity_event_targets(event_id, target_role, drive_id, ucloud_path) 
				values (:event_id, :target_role, :drive_id, :ucloud_path)
			`, db.Params{
				"event_id":    row.EventId,
				"target_role": target.Role,
				"drive_id":    target.DriveId,
				"ucloud_path": target.UCloudPath,
			})
		}
	})
}

func activityDeleteExpiredEvents() {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from fs_activity_events
				where started_at < now() - interval '14 days'
			`,
			db.Params{},
		)
	})
}
