package controller

import (
	"database/sql"
	"encoding/json"
	"net/http"
	"strings"
	"sync"
	"time"

	lru "github.com/hashicorp/golang-lru/v2/expirable"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/controller/fsearch"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/shared/pkg/apm"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

// This file contains an in-memory representation of drives which have been used recently. It is also used to store a
// copy of drives received from UCloud. The data is kept only in-memory by the server instance. User instances
// asynchronously notify the server instance about drives they believe to be new. The component is initialized via the
// InitDriveDatabase() function regardless of mode.
//
// The drive database does not reliably track all properties. These properties should never be used for anything but
// CLI tools as they might be out-of-date. Namely, the following properties are _not_ tracked reliably:
//
// - Changes to product (should not happen)
// - Updates (should not be used)
// - Changes in timestamps (IM generally has no use for these)

func InitDriveDatabase() {
	if !RunsServerCode() {
		return
	}

	fsearch.Init()

	db.NewTx0(func(tx *db.Transaction) {
		rows := db.Select[struct {
			DriveId  string
			Resource string
		}](
			tx,
			`
				select drive_id, resource
				from tracked_drives
		    `,
			db.Params{},
		)

		activeDrivesMutex.Lock()
		for _, row := range rows {
			drive := new(orc.Drive)
			err := json.Unmarshal([]byte(row.Resource), drive)
			if err == nil {
				activeDrives[row.DriveId] = drive
			}
		}
		activeDrivesMutex.Unlock()
	})

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into tracked_drives_scan_timer(zero, time)
				values (0, now())
				on conflict (zero) do update set time = excluded.time;
			`,
			db.Params{},
		)
	})

	trackDriveIpc.Handler(func(r *ipc.Request[fnd.FindByStringId]) ipc.Response[util.Empty] {
		// NOTE(Dan): Since we are not returning any information about the drive, we simply track it regardless if this
		// was a drive that belongs to the user or not.
		drive, err := orc.RetrieveDrive(r.Payload.Id)
		if err == nil {
			TrackDrive(&drive)
		}

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
		}
	})

	retrieveDriveIpc.Handler(func(r *ipc.Request[fnd.FindByStringId]) ipc.Response[orc.Drive] {
		result, ok := RetrieveDrive(r.Payload.Id)

		if !ok || !BelongsToWorkspace(orc.ResourceOwnerToWalletOwner(result.Resource), r.Uid) {
			return ipc.Response[orc.Drive]{
				StatusCode: http.StatusForbidden,
			}
		}

		return ipc.Response[orc.Drive]{
			StatusCode: http.StatusOK,
			Payload:    *result,
		}
	})

	retrieveDriveByProviderIdIpc.Handler(func(r *ipc.Request[util.Tuple2[[]string, []string]]) ipc.Response[orc.Drive] {
		result, ok := RetrieveDriveByProviderId(r.Payload.First, r.Payload.Second)

		if !ok || !BelongsToWorkspace(orc.ResourceOwnerToWalletOwner(result.Resource), r.Uid) {
			return ipc.Response[orc.Drive]{
				StatusCode: http.StatusForbidden,
			}
		}

		return ipc.Response[orc.Drive]{
			StatusCode: http.StatusOK,
			Payload:    *result,
		}
	})

	removeTrackedDriveIpc.Handler(func(r *ipc.Request[fnd.FindByStringId]) ipc.Response[util.Empty] {
		drive, err := orc.RetrieveDrive(r.Payload.Id)
		if err != nil || !BelongsToWorkspace(orc.ResourceOwnerToWalletOwner(drive.Resource), r.Uid) {
			return ipc.Response[util.Empty]{
				StatusCode: http.StatusForbidden,
			}
		} else {
			removeTrackedDrive(drive.Id)
			return ipc.Response[util.Empty]{
				StatusCode: http.StatusOK,
			}
		}
	})

}

// The activeDrives property stores all drives which have been seen this session in-memory. This structure is valid for
// both user mode and server mode. In both modes, it is updated via the TrackDrive() function. If the user mode realises
// that this drive has not been seen it its own session, then the server instance is also notified about the drive.
// Access to this property is not thread-safe and must be done while holding the activeDrivesMutex.
var activeDrives = map[string]*orc.Drive{}
var activeDrivesMutex = sync.Mutex{}

var (
	trackDriveIpc                = ipc.NewCall[fnd.FindByStringId, util.Empty]("ctrl.drives.track")
	removeTrackedDriveIpc        = ipc.NewCall[fnd.FindByStringId, util.Empty]("ctrl.drives.removeTrackedDrive")
	retrieveDriveIpc             = ipc.NewCall[fnd.FindByStringId, orc.Drive]("ctrl.drives.retrieve")
	retrieveDriveByProviderIdIpc = ipc.NewCall[util.Tuple2[[]string, []string], orc.Drive]("ctrl.drives.retrieveByProviderId")
)

func TrackDrive(drive *orc.Drive) {
	trackDrive(drive, true)
}

func trackDrive(drive *orc.Drive, allowRegistration bool) {
	activeDrivesMutex.Lock()

	copiedDrive := *drive
	activeDrives[drive.Id] = &copiedDrive

	if allowRegistration {
		go func() {
			if RunsServerCode() {
				jsonified, _ := json.Marshal(&copiedDrive)

				db.NewTx0(func(tx *db.Transaction) {
					db.Exec(
						tx,
						`
							insert into tracked_drives(drive_id, product_id, product_category, created_by,
							    project_id, provider_generated_id, resource) 
							values (:drive_id, :product_id, :product_category, :created_by,
								:project_id, :provider_generated_id, :resource) 
							on conflict (drive_id) do update set
							    product_id = excluded.product_id,
							    product_category = excluded.product_category,
							    created_by = excluded.created_by,
							    project_id = excluded.project_id,
							    provider_generated_id = excluded.provider_generated_id,
							    resource = excluded.resource
					    `,
						db.Params{
							"drive_id":              copiedDrive.Id,
							"product_id":            copiedDrive.Specification.Product.Id,
							"product_category":      copiedDrive.Specification.Product.Category,
							"created_by":            copiedDrive.Owner.CreatedBy,
							"project_id":            copiedDrive.Owner.Project,
							"provider_generated_id": copiedDrive.ProviderGeneratedId,
							"resource":              string(jsonified),
						},
					)
				})
			} else {
				_, _ = trackDriveIpc.Invoke(fnd.FindByStringId{Id: copiedDrive.Id})
			}
		}()
	}
	activeDrivesMutex.Unlock()
}

func EnumerateKnownDrives() []orc.Drive {
	var result []orc.Drive

	activeDrivesMutex.Lock()
	for _, d := range activeDrives {
		if d.Specification.Product.Provider != cfg.Provider.Id {
			continue
		}
		result = append(result, *d)
	}
	activeDrivesMutex.Unlock()
	return result
}

func RetrieveDrive(id string) (*orc.Drive, bool) {
	activeDrivesMutex.Lock()
	existing, ok := activeDrives[id]
	activeDrivesMutex.Unlock()

	if !ok {
		if RunsServerCode() {
			res, err := orc.RetrieveDrive(id)
			if err == nil {
				trackDrive(&res, true)
			}
			return &res, err == nil
		} else {
			res, err := retrieveDriveIpc.Invoke(fnd.FindByStringId{Id: id})
			if err == nil {
				trackDrive(&res, false) // Already registered in server mode
			}
			return &res, err == nil
		}
	}

	return existing, true
}

func TrackSearchIndex(id string, index *fsearch.SearchIndex, recommendedBucketCount int) {
	if !RunsServerCode() {
		panic("Not yet implemented in user mode")
	}

	if index == nil {
		return
	}

	_, ok := RetrieveDrive(id)
	if ok {
		indexBytes := index.Marshal()
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					update tracked_drives
					set
						search_index = :index,
						search_next_bucket_count = :count
					where
						drive_id = :id
			    `,
				db.Params{
					"id":    id,
					"index": indexBytes,
					"count": recommendedBucketCount,
				},
			)
		})

		searchIndexCache.Remove(id)
	}
}

func LoadNextSearchBucketCount(id string) int {
	if !RunsServerCode() {
		panic("Not yet implemented in user mode")
	}

	return db.NewTx(func(tx *db.Transaction) int {
		row, _ := db.Get[struct{ SearchNextBucketCount int }](
			tx,
			`
				select
					search_next_bucket_count
				from
					tracked_drives
				where
					drive_id = :id
		    `,
			db.Params{
				"id": id,
			},
		)

		return min(fsearch.MaxBucketsPerIndex, row.SearchNextBucketCount)
	})
}

var searchIndexCache = lru.NewLRU[string, fsearch.SearchIndex](32, nil, 30*time.Minute)
var searchIndexLock = util.NewScopedMutex() // Ensure that only one goroutine attempts to read a given index. Not needed for the cache.

func RetrieveSearchIndex(id string) (fsearch.SearchIndex, bool) {
	cached, ok := searchIndexCache.Get(id)
	if ok {
		return cached, true
	} else {
		searchIndexLock.Lock(id)
		result, ok := searchIndexCache.Get(id)
		if !ok {
			result, ok = db.NewTx2(func(tx *db.Transaction) (fsearch.SearchIndex, bool) {
				index, ok := db.Get[struct{ SearchIndex sql.RawBytes }](
					tx,
					`
						select search_index
						from tracked_drives
						where drive_id = :id
					`,
					db.Params{
						"id": id,
					},
				)

				if ok {
					return *fsearch.LoadIndex(index.SearchIndex), true
				} else {
					return *fsearch.NewIndexBuilder(16), false
				}
			})
		}
		searchIndexLock.Unlock(id)

		return result, ok
	}
}

func CanUseDrive(actor orc.ResourceOwner, driveId string, readOnly bool) bool {
	if !RunsServerCode() {
		panic("Not implemented for user mode")
	}

	drive, ok := RetrieveDrive(driveId)
	if !ok {
		return false
	}

	if drive.Owner.Project != "" {
		project, ok := RetrieveProject(drive.Owner.Project)
		if !ok {
			return false
		}

		username := actor.CreatedBy
		if username != "" {
			isMember := false
			for _, member := range project.Status.Members {
				if member.Username == username {
					isMember = true
					if member.Role == apm.ProjectRolePI || member.Role == apm.ProjectRoleAdmin {
						return true
					}
				}
			}

			if !isMember {
				return false
			}

			if username == drive.Owner.CreatedBy {
				return true
			}

			for _, entry := range drive.Permissions.Others {
				entryIsRelevant := false
				if readOnly {
					entryIsRelevant = orc.PermissionsHas(entry.Permissions, orc.PermissionRead)
				} else {
					entryIsRelevant = orc.PermissionsHas(entry.Permissions, orc.PermissionEdit)
				}

				if entryIsRelevant {
					if entry.Entity.Type == orc.AclEntityTypeUser {
						if entry.Entity.Username == username {
							return true
						}
					} else if entry.Entity.Type == orc.AclEntityTypeProjectGroup {
						if apm.IsMemberOfGroup(project, entry.Entity.Group, username) {
							return true
						}
					}
				}
			}

			return false
		} else {
			return project.Id == actor.Project
		}
	} else {
		for _, entry := range drive.Permissions.Others {
			entryIsRelevant := false
			if readOnly {
				entryIsRelevant = orc.PermissionsHas(entry.Permissions, orc.PermissionRead)
			} else {
				entryIsRelevant = orc.PermissionsHas(entry.Permissions, orc.PermissionEdit)
			}

			if entryIsRelevant {
				if entry.Entity.Type == orc.AclEntityTypeUser {
					if entry.Entity.Username == actor.CreatedBy {
						return true
					}
				}
			}
		}

		return actor.CreatedBy == drive.Owner.CreatedBy
	}
}

func RemoveTrackedDrive(id string) bool {
	activeDrivesMutex.Lock()
	delete(activeDrives, id)
	activeDrivesMutex.Unlock()

	if RunsServerCode() {
		removeTrackedDrive(id)
		return true
	} else {
		_, err := removeTrackedDriveIpc.Invoke(fnd.FindByStringId{Id: id})
		return err == nil
	}
}

func removeTrackedDrive(id string) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from tracked_drives where drive_id = :drive_id
			`,
			db.Params{
				"drive_id": id,
			},
		)
	})
}

func RetrieveDriveByProviderId(prefixes []string, suffixes []string) (*orc.Drive, bool) {
	if len(prefixes) != len(suffixes) {
		log.Warn("RetrieveDriveByProviderId invoked incorrectly from %v", util.GetCaller())
		return nil, false
	}

	count := len(prefixes)

	activeDrivesMutex.Lock()

	var result *orc.Drive

outer:
	for _, drive := range activeDrives {
		id := drive.ProviderGeneratedId
		if id == "" {
			continue
		}

		for i := 0; i < count; i++ {
			prefix := prefixes[i]
			suffix := suffixes[i]

			if suffix != "" {
				if strings.HasPrefix(id, prefix) && strings.HasSuffix(id, suffix) {
					result = drive
					break outer
				}
			} else {
				if id == prefix {
					result = drive
					break outer
				}
			}
		}
	}

	activeDrivesMutex.Unlock()

	if result == nil {
		if RunsServerCode() {
			lookupFromDatabase := func(tx *db.Transaction) *orc.Drive {
				rows := db.Select[struct{ Resource string }](
					tx,
					`
						with
							lookup as (
								select
									unnest(cast(:prefixes as text[])) as prefix,
									unnest(cast(:suffixes as text[])) as suffix
							)
						select resource
						from
							tracked_drives d
							join lookup l on
								d.provider_generated_id like l.prefix || '%'
								and d.provider_generated_id like '%' || l.suffix
								and (
									l.suffix != ''
									or d.provider_generated_id = l.prefix
								)
					`,
					db.Params{
						"prefixes": prefixes,
						"suffixes": suffixes,
					},
				)

				bestLength := 0
				var bestMatch *orc.Drive
				for _, row := range rows {
					var drive orc.Drive
					err := json.Unmarshal([]byte(row.Resource), &drive)
					if err != nil {
						continue
					}

					thisLength := len(drive.ProviderGeneratedId)
					if thisLength > bestLength {
						bestMatch = &drive
						bestLength = thisLength
					}
				}

				return bestMatch
			}

			res := db.NewTx(lookupFromDatabase)
			if res != nil {
				return res, true
			}
		} else {
			res, err := retrieveDriveByProviderIdIpc.Invoke(util.Tuple2[[]string, []string]{prefixes, suffixes})
			if err == nil {
				trackDrive(&res, false) // Already registered in server mode
			}
			return &res, err == nil
		}
	}

	return result, result != nil
}

func RetrieveDrivesNeedingScan() []orc.Drive {
	if RunsServerCode() {
		return db.NewTx[[]orc.Drive](func(tx *db.Transaction) []orc.Drive {
			var result []orc.Drive

			rows := db.Select[struct{ Resource string }](
				tx,
				`
					with
						drives as (
							select drive_id
							from
								tracked_drives,
								tracked_drives_scan_timer session
							where
								last_scan_completed_at < now() - cast('12 hour' as interval)
								and (
									last_scan_submitted_at < now() - cast('12 hour' as interval)
									or last_scan_submitted_at < session.time
								)
								and product_id != 'share' -- TODO Should probably go somewhere else
							order by last_scan_completed_at
							limit 256   
						)
					update tracked_drives td
					set last_scan_submitted_at = now()
					from drives d
					where td.drive_id = d.drive_id
					returning td.resource
			    `,
				db.Params{},
			)

			for _, row := range rows {
				var drive orc.Drive
				err := json.Unmarshal([]byte(row.Resource), &drive)
				if err != nil {
					continue
				}

				result = append(result, drive)
			}

			return result
		})
	} else {
		return nil
	}
}

func UpdateDriveScannedAt(driveId string) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update tracked_drives
				set last_scan_completed_at = now()
				where drive_id = :drive_id
		    `,
			db.Params{
				"drive_id": driveId,
			},
		)
	})
}
