package controller

import (
	"encoding/json"
	"net/http"
	"strings"
	"sync"
	db "ucloud.dk/pkg/database"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

// This file contains an in-memory representation of drives which have been used recently. It is also used to store a
// copy of drives received from UCloud. The data is kept only in-memory by the server instance. User instances
// asynchronously notify the server instance about drives they believe to be new. The component is initialized via the
// InitDriveDatabase() function regardless of mode.
//
// The drive database does not reliably track all properties. These properties should never be used for anything but
// CLI tools as they might be out-of-date. Namely, the following properties are _not_ tracked reliably:
//
// - Permissions (should always be enforced by UCloud/Core)
// - Changes to product (should not happen)
// - Updates (should not be used)
// - Changes in timestamps (IM generally has no use for these)

func InitDriveDatabase() {
	if cfg.Mode != cfg.ServerModeServer {
		return
	}

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
}

// The activeDrives property stores all drives which have been seen this session in-memory. This structure is valid for
// both user mode and server mode. In both modes, it is updated via the TrackDrive() function. If the user mode realises
// that this drive has not been seen it its own session, then the server instance is also notified about the drive.
// Access to this property is not thread-safe and must be done while holding the activeDrivesMutex.
var activeDrives = map[string]*orc.Drive{}
var activeDrivesMutex = sync.Mutex{}

var (
	trackDriveIpc                = ipc.NewCall[fnd.FindByStringId, util.Empty]("ctrl.drives.track")
	retrieveDriveIpc             = ipc.NewCall[fnd.FindByStringId, orc.Drive]("ctrl.drives.retrieve")
	retrieveDriveByProviderIdIpc = ipc.NewCall[util.Tuple2[[]string, []string], orc.Drive]("ctrl.drives.retrieveByProviderId")
)

func TrackDrive(drive *orc.Drive) {
	trackDrive(drive, true)
}

func trackDrive(drive *orc.Drive, allowRegistration bool) {
	activeDrivesMutex.Lock()

	existing, ok := activeDrives[drive.Id]
	if !ok || drivesAreMeaningfullyDifferent(drive, existing) {
		copiedDrive := *drive
		activeDrives[drive.Id] = &copiedDrive

		if allowRegistration {
			go func() {
				if cfg.Mode == cfg.ServerModeServer {
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
							    product_id = excluded.project_id,
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
	}
	activeDrivesMutex.Unlock()
}

func drivesAreMeaningfullyDifferent(a, b *orc.Drive) bool {
	if a.Id != b.Id {
		return true
	}

	if a.Owner.Project != b.Owner.Project {
		return true
	}

	if a.Owner.CreatedBy != b.Owner.CreatedBy {
		return true
	}

	if a.ProviderGeneratedId != b.ProviderGeneratedId {
		return true
	}

	if a.Specification.Title != b.Specification.Title {
		return true
	}

	return false
}

func RetrieveDrive(id string) (*orc.Drive, bool) {
	activeDrivesMutex.Lock()
	existing, ok := activeDrives[id]
	activeDrivesMutex.Unlock()

	if !ok {
		if cfg.Mode == cfg.ServerModeServer {
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

			if strings.HasPrefix(id, prefix) && strings.HasSuffix(id, suffix) {
				result = drive
				break outer
			}
		}
	}

	activeDrivesMutex.Unlock()

	if result == nil {
		if cfg.Mode == cfg.ServerModeServer {
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