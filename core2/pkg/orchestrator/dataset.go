package orchestrator

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"path/filepath"
	"slices"
	"strings"
	"sync"
	"time"

	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const datasetType = "dataset"

// Title and provider-generated id prefix of the drive which holds all datasets of a project. The prefix is assigned
// by the provider when the drive is created, similar to home drives ("h-") and project member folders ("pm-").
const datasetArchiveDriveTitle = "Data Archival"

// Guards the find-or-create flow of the data archival drive such that a project does not end up with several of them.
var datasetArchiveDriveMu sync.Mutex

func initDatasets() {
	InitResourceType(
		datasetType,
		0,
		datasetLoad,
		datasetPersist,
		datasetTransform,
		nil,
	)

	orcapi.DatasetsCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.DatasetSpecification]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		var responses []fndapi.FindByStringId
		for _, item := range request.Items {
			created, err := DatasetCreate(info.Actor, item)
			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			}
			responses = append(responses, fndapi.FindByStringId{Id: created.Id})
		}
		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: responses}, nil
	})

	orcapi.DatasetsUpdateEntries.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.DatasetsUpdateEntriesRequest]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := DatasetUpdateEntries(info.Actor, reqItem)
			if err != nil {
				return util.Empty{}, err
			}
		}
		return util.Empty{}, nil
	})

	orcapi.DatasetsDelete.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[fndapi.FindByStringId]) (fndapi.BulkResponse[util.Empty], *util.HttpError) {
		responses := make([]util.Empty, 0, len(request.Items))
		for _, reqItem := range request.Items {
			if err := DatasetDelete(info.Actor, reqItem.Id); err != nil {
				return fndapi.BulkResponse[util.Empty]{}, err
			}
			responses = append(responses, util.Empty{})
		}
		return fndapi.BulkResponse[util.Empty]{Responses: responses}, nil
	})
}

// DatasetCreate creates a new dataset from a selection of files and folders. The selection is snapshotted (copied)
// into a folder of the data archival drive of the project. The dataset starts out in the DRAFT state and transitions
// to READY once every copy has finished.
func DatasetCreate(actor rpc.Actor, spec orcapi.DatasetSpecification) (orcapi.Dataset, *util.HttpError) {
	// Datasets live in the data archival drive of a project, they cannot be created in a personal workspace.
	if !actor.Project.Present {
		return orcapi.Dataset{}, util.HttpErr(http.StatusBadRequest, "datasets must be created from a project")
	}

	title := strings.TrimSpace(spec.Title)
	if title == "" || strings.Contains(title, "\n") {
		return orcapi.Dataset{}, util.HttpErr(http.StatusBadRequest, "invalid title specified")
	}

	if len(spec.Entries) == 0 {
		return orcapi.Dataset{}, util.HttpErr(http.StatusBadRequest, "a dataset must contain at least one file or folder")
	}

	// Validate and normalize the selection of files and folders.
	entries := make([]orcapi.DatasetEntry, 0, len(spec.Entries))
	seen := map[string]util.Empty{}
	for _, entry := range spec.Entries {
		path := filepath.Clean(entry.Path)
		driveId, ok := orcapi.DriveIdFromUCloudPath(path)
		if !ok || path == "" || path == "/" || path == "/"+driveId {
			return orcapi.Dataset{}, util.HttpErr(http.StatusBadRequest, "invalid file or folder selected: %s", entry.Path)
		}

		if _, duplicate := seen[path]; duplicate {
			continue
		}
		seen[path] = util.Empty{}
		entries = append(entries, orcapi.DatasetEntry{Id: entry.Id, Path: path})
	}

	// A selection must not contain a folder along with something inside of it (it would be copied twice).
	for _, selected := range entries {
		for _, other := range entries {
			if selected.Path != other.Path && datasetPathIsWithin(other.Path, selected.Path) {
				return orcapi.Dataset{}, util.HttpErr(http.StatusBadRequest, "a folder and its content are both selected: %s", selected.Path)
			}
		}
	}

	// Fetch the drives of every selected file/folder. This also verifies that the actor is allowed to read them.
	sourcePaths := make([]string, 0, len(entries))
	for _, entry := range entries {
		sourcePaths = append(sourcePaths, entry.Path)
	}
	sourceDrives, err := filesFetchDrives(actor, sourcePaths, orcapi.PermissionRead)
	if err != nil {
		return orcapi.Dataset{}, err
	}

	// Every selection must originate from drives owned by this project and from a single provider. The copy
	// operation of the file system does not work across providers.
	providerId := ""
	product := accapi.ProductReference{}
	for _, entry := range entries {
		driveId, _ := orcapi.DriveIdFromUCloudPath(entry.Path)
		drive := sourceDrives[driveId]

		if !drive.Owner.Project.Present || drive.Owner.Project.Value != string(actor.Project.Value) {
			return orcapi.Dataset{}, util.HttpErr(http.StatusForbidden, "you can only select files and folders owned by this project: %s", entry.Path)
		}

		p := drive.Specification.Product
		if providerId == "" {
			providerId = p.Provider
			product = p
		} else if providerId != p.Provider {
			return orcapi.Dataset{}, util.HttpErr(http.StatusBadRequest, "all selected files and folders must be located at the same provider")
		}
	}

	if !featureSupported(driveType, product, orcapi.FeaturePublication) {
		return orcapi.Dataset{}, featureNotSupportedError
	}

	// Deny usage of files and folders which are currently in use by another dataset which is not READY yet.
	// Copying the same files concurrently would risk corrupting the snapshot.
	err = datasetSelectionInUse(actor, entries, 0)
	if err != nil {
		return orcapi.Dataset{}, err
	}

	// Resolve the drive which holds all datasets of this project, creating it through the provider if it does not
	// exist yet (similar to how home folders and project member folders are created).
	archiveDrive, err := datasetArchiveDrive(actor, providerId, product)
	if err != nil {
		return orcapi.Dataset{}, err
	}

	for _, entry := range entries {
		driveId, _ := orcapi.DriveIdFromUCloudPath(entry.Path)
		if driveId == archiveDrive.Id {
			return orcapi.Dataset{}, util.HttpErr(http.StatusBadRequest, "you cannot create a dataset from the data archival drive itself")
		}
	}

	// Create the dataset resource itself, it starts out in the DRAFT state.
	id, _, err := ResourceCreate[orcapi.Dataset](
		actor,
		datasetType,
		orcapi.ResourceSpecification{
			Product: archiveDrive.Specification.Product,
			Labels:  spec.ResourceSpecification.Labels,
		},
		&internalDataset{
			Metadata: orcapi.DatasetMetadata{
				Title:       title,
				Description: strings.TrimSpace(spec.Description),
				Keywords:    spec.Keywords,
			},
			Entries: entries,
			State:   orcapi.DatasetStateDraft,
		},
	)
	if err != nil {
		return orcapi.Dataset{}, err
	}
	ResourceConfirm(datasetType, id)

	datasetFolder := fmt.Sprintf("/%s/%d", archiveDrive.Id, id)
	updateOk := ResourceUpdate(actor, datasetType, id, orcapi.PermissionEdit, func(r *resource, mapped orcapi.Dataset) {
		d := r.Extra.(*internalDataset)
		d.Path = util.OptValue(datasetFolder)
	})
	if !updateOk {
		return orcapi.Dataset{}, util.HttpErr(http.StatusInternalServerError, "unable to initialize the dataset")
	}

	// Copy every selected file and folder into the dataset folder using the regular copy functionality of the file
	// system. The provider performs the actual copy asynchronously and tracks the progress with the task system.
	err = datasetCopyEntries(actor, datasetFolder, entries)
	if err != nil {
		datasetPostState(id, orcapi.DatasetStateFailure, err.Why)
		return orcapi.Dataset{}, err
	}

	// Follow the copy tasks and transition the dataset to READY once every copy has finished.
	go datasetTrackCopies(id, actor.Username, providerId)

	return ResourceRetrieve[orcapi.Dataset](actor, datasetType, id, orcapi.ResourceFlags{
		IncludeOthers:  true,
		IncludeUpdates: true,
		IncludeSupport: true,
		IncludeProduct: true,
	})
}

func datasetPathIsWithin(path string, ancestor string) bool {
	return strings.HasPrefix(path, ancestor+"/")
}

// datasetSelectionInUse checks if any of the selected files and folders are currently in use by another dataset of
// this project which is still being populated (DRAFT). Usage is denied until that dataset reaches the READY state,
// copying the same files concurrently would risk corrupting them.
func datasetSelectionInUse(actor rpc.Actor, entries []orcapi.DatasetEntry, excludeDatasetId ResourceId) *util.HttpError {
	next := util.OptNone[string]()
	for {
		page := ResourceBrowse[orcapi.Dataset](actor, datasetType, next, 1000, orcapi.ResourceFlags{}, func(item orcapi.Dataset) bool {
			return item.Status.State == orcapi.DatasetStateDraft && ResourceParseId(item.Id) != excludeDatasetId
		}, nil)

		for _, dataset := range page.Items {
			for _, selected := range entries {
				for _, inUse := range dataset.Specification.Entries {
					if selected.Path == inUse.Path ||
						datasetPathIsWithin(selected.Path, inUse.Path) ||
						datasetPathIsWithin(inUse.Path, selected.Path) {
						return util.HttpErr(
							http.StatusConflict,
							"%s is currently in use by another dataset which is not ready yet",
							selected.Path,
						)
					}
				}
			}
		}

		if !page.Next.Present {
			return nil
		}
		next = page.Next
	}
}

// datasetArchiveDrive returns the drive which holds all datasets of the project of the actor. The drive is created
// through the provider if it does not exist yet, following the same process as home folders and project member
// folders. The provider generates an id prefixed with "da-" for this drive.
func datasetArchiveDrive(actor rpc.Actor, providerId string, product accapi.ProductReference) (orcapi.Drive, *util.HttpError) {
	datasetArchiveDriveMu.Lock()
	defer datasetArchiveDriveMu.Unlock()

	lookup := func() (orcapi.Drive, bool) {
		next := util.OptNone[string]()
		for {
			page := ResourceBrowse[orcapi.Drive](actor, driveType, next, 1000, orcapi.ResourceFlags{}, func(item orcapi.Drive) bool {
				isArchiveDrive := strings.HasPrefix(item.ProviderGeneratedId, "da-")
				return isArchiveDrive && item.Specification.Product.Provider == providerId
			}, nil)

			if len(page.Items) > 0 {
				return page.Items[0], true
			}

			if !page.Next.Present {
				return orcapi.Drive{}, false
			}
			next = page.Next
		}
	}

	drive, found := lookup()
	if found {
		return drive, nil
	}

	created, err := DriveCreate(actor, orcapi.DriveSpecification{
		Title:                 datasetArchiveDriveTitle,
		ResourceSpecification: orcapi.ResourceSpecification{Product: product},
	})
	return created, err
}

// datasetEntryDestinationPath returns the path at which the copy of an entry lives inside the folder backing the
// dataset. The drive id of the source is always part of the destination such that files from different drives
// cannot collide and such that the destination of an entry is stable across selection updates.
func datasetEntryDestinationPath(datasetFolder string, entryPath string) string {
	driveId, _ := orcapi.DriveIdFromUCloudPath(entryPath)
	relativePath := strings.TrimPrefix(entryPath, "/"+driveId)
	return datasetFolder + "/" + driveId + relativePath
}

// datasetCopyEntries copies the selected files and folders into the folder backing the dataset. The destination
// mirrors the structure of the selection relative to its source drive (prefixed with the drive id when the selection
// spans several drives) such that files with the same name do not collide.
func datasetCopyEntries(actor rpc.Actor, datasetFolder string, entries []orcapi.DatasetEntry) *util.HttpError {
	var folders []string
	var copies []orcapi.FilesSourceAndDestination

	seenFolders := map[string]util.Empty{}
	addFolder := func(folder string) {
		if folder == datasetFolder || folder == "/" {
			return
		}
		if _, ok := seenFolders[folder]; ok {
			return
		}
		seenFolders[folder] = util.Empty{}
		folders = append(folders, folder)
	}

	for _, entry := range entries {
		destinationPath := datasetEntryDestinationPath(datasetFolder, entry.Path)

		// Every folder holding a destination must exist before it can be copied into.
		dir := filepath.Dir(destinationPath)
		for dir != datasetFolder && dir != "/" && dir != "." {
			addFolder(dir)
			dir = filepath.Dir(dir)
		}

		copies = append(copies, orcapi.FilesSourceAndDestination{
			SourcePath:      entry.Path,
			DestinationPath: destinationPath,
			ConflictPolicy:  orcapi.WriteConflictPolicyReplace,
		})
	}

	// Parents must be created before their children (sorted by path length).
	slices.SortFunc(folders, func(a string, b string) int {
		return len(a) - len(b)
	})

	folderRequests := make([]orcapi.FilesCreateFolderRequest, 0, len(folders)+1)
	folderRequests = append(folderRequests, orcapi.FilesCreateFolderRequest{
		Id:             datasetFolder,
		ConflictPolicy: orcapi.WriteConflictPolicyReplace,
	})
	for _, folder := range folders {
		folderRequests = append(folderRequests, orcapi.FilesCreateFolderRequest{
			Id:             folder,
			ConflictPolicy: orcapi.WriteConflictPolicyReplace,
		})
	}

	_, err := FilesCreateFolder(actor, fndapi.BulkRequestOf(folderRequests...))
	if err != nil {
		return err
	}

	// The provider creates a task for the copy which the end-user can follow in the task system.
	_, err = FilesCopy(actor, fndapi.BulkRequestOf(copies...))
	return err
}

// DatasetUpdateEntries adds and removes files and folders from the selection of an existing dataset. Newly added
// entries are copied into the folder backing the dataset, entries which are already part of it are copied again
// (refreshing their copy). Removed entries are deleted from the folder. While new copies are in-flight the dataset
// is in the DRAFT state and transitions back to READY once they have all finished.
func DatasetUpdateEntries(actor rpc.Actor, request orcapi.DatasetsUpdateEntriesRequest) *util.HttpError {
	datasetId := ResourceParseId(request.Id)

	if len(request.AddedEntries) == 0 && len(request.DeletedEntries) == 0 {
		return util.HttpErr(http.StatusBadRequest, "no changes requested")
	}

	dataset, _, baseSpec, err := ResourceRetrieveEx[orcapi.Dataset](
		actor,
		datasetType,
		datasetId,
		orcapi.PermissionEdit,
		orcapi.ResourceFlags{IncludeUpdates: true},
	)
	if err != nil {
		return err
	}

	switch dataset.Status.State {
	case orcapi.DatasetStatePublishing:
		return util.HttpErr(http.StatusConflict, "this dataset is currently being published")
	case orcapi.DatasetStatePublished:
		return util.HttpErr(http.StatusConflict, "this dataset has been published and can no longer be modified")
	}

	datasetFolder := dataset.Status.Path
	if datasetFolder == "" {
		return util.HttpErr(http.StatusConflict, "this dataset is not ready to be modified, try again later")
	}

	archiveDriveId, _ := orcapi.DriveIdFromUCloudPath(datasetFolder)
	providerId := baseSpec.Product.Provider

	if !featureSupported(driveType, baseSpec.Product, orcapi.FeaturePublication) {
		return featureNotSupportedError
	}

	// Normalize the deletions. Entries which are not currently part of the selection are ignored such that retries
	// of the same request are idempotent.
	currentEntries := util.NonNilSlice(dataset.Specification.Entries)
	var deletions []orcapi.DatasetEntry
	for _, entry := range request.DeletedEntries {
		path := filepath.Clean(entry.Path)
		for _, existing := range currentEntries {
			if existing.Path == path {
				deletions = append(deletions, existing)
				break
			}
		}
	}

	// Normalize the additions.
	var additions []orcapi.DatasetEntry
	{
		seen := map[string]util.Empty{}
		for _, entry := range request.AddedEntries {
			path := filepath.Clean(entry.Path)
			driveId, ok := orcapi.DriveIdFromUCloudPath(path)
			if !ok || path == "" || path == "/" || path == "/"+driveId {
				return util.HttpErr(http.StatusBadRequest, "invalid file or folder selected: %s", entry.Path)
			}

			if _, duplicate := seen[path]; duplicate {
				continue
			}
			seen[path] = util.Empty{}
			additions = append(additions, orcapi.DatasetEntry{Id: entry.Id, Path: path})
		}
	}

	if len(additions) == 0 && len(deletions) == 0 {
		return nil
	}

	if len(additions) > 0 {
		// Deny usage of files and folders which are currently in use by another dataset which is not READY yet.
		if err := datasetSelectionInUse(actor, additions, datasetId); err != nil {
			return err
		}

		// Fetch the drives of every added file/folder. This also verifies that the actor is allowed to read them.
		sourcePaths := make([]string, 0, len(additions))
		for _, entry := range additions {
			sourcePaths = append(sourcePaths, entry.Path)
		}
		sourceDrives, err := filesFetchDrives(actor, sourcePaths, orcapi.PermissionRead)
		if err != nil {
			return err
		}

		for _, entry := range additions {
			driveId, _ := orcapi.DriveIdFromUCloudPath(entry.Path)
			drive := sourceDrives[driveId]

			if !drive.Owner.Project.Present || drive.Owner.Project.Value != string(actor.Project.Value) {
				return util.HttpErr(http.StatusForbidden, "you can only select files and folders owned by this project: %s", entry.Path)
			}

			// The copy operation of the file system does not work across providers, everything must be located at
			// the provider of the drive holding the datasets of the project.
			if drive.Specification.Product.Provider != providerId {
				return util.HttpErr(http.StatusBadRequest, "all selected files and folders must be located at the same provider as the dataset")
			}

			if driveId == archiveDriveId {
				return util.HttpErr(http.StatusBadRequest, "you cannot add files and folders from the data archival drive itself")
			}
		}
	}

	// Validate the selection which results from applying this request.
	projected := datasetMergeEntries(datasetRemoveEntries(currentEntries, deletions), additions)
	if len(projected) == 0 {
		return util.HttpErr(http.StatusBadRequest, "a dataset must contain at least one file or folder")
	}
	if datasetSelectionHasOverlap(projected) {
		return util.HttpErr(http.StatusBadRequest, "a folder and its content are both selected")
	}

	// Copy the additions into the dataset before they become part of the selection. The provider performs the
	// copies asynchronously and tracks the progress with the task system.
	copyStartedAt := time.Now()
	if len(additions) > 0 {
		if err := datasetCopyEntries(actor, datasetFolder, additions); err != nil {
			// The copies were refused: apply the deletions, keep the additions out of the selection and surface the
			// error. The state of the dataset is deliberately left untouched.
			ResourceUpdate(actor, datasetType, datasetId, orcapi.PermissionEdit, func(r *resource, mapped orcapi.Dataset) {
				d := r.Extra.(*internalDataset)
				d.Entries = datasetRemoveEntries(util.NonNilSlice(d.Entries), deletions)
				d.Updates = append(d.Updates, orcapi.DatasetUpdate{
					Timestamp: fndapi.Timestamp(time.Now()),
					Status:    util.OptValue(err.Why),
				})
			})
			return err
		}
	}

	// Apply the new selection.
	stateConflict := false
	updateOk := ResourceUpdate(actor, datasetType, datasetId, orcapi.PermissionEdit, func(r *resource, mapped orcapi.Dataset) {
		d := r.Extra.(*internalDataset)

		// NOTE(Dan): The state is re-checked here since the dataset could have started publishing between the
		// initial check and this mutation.
		switch d.State {
		case orcapi.DatasetStatePublishing, orcapi.DatasetStatePublished:
			stateConflict = true
			return
		}

		d.Entries = datasetMergeEntries(datasetRemoveEntries(util.NonNilSlice(d.Entries), deletions), additions)

		update := orcapi.DatasetUpdate{
			Timestamp: fndapi.Timestamp(time.Now()),
			Status:    util.OptValue("selection updated"),
		}
		if len(additions) > 0 {
			// The dataset is being populated again, it can no longer be published until every copy has finished.
			// The timestamp of this record marks the point in time from which copy tasks belong to this dataset,
			// see datasetCopyStartedAt.
			update.Timestamp = fndapi.Timestamp(copyStartedAt)
			if d.State != orcapi.DatasetStateDraft {
				update.State = util.OptValue(orcapi.DatasetStateDraft)
				d.State = orcapi.DatasetStateDraft
			}
		}
		d.Updates = append(d.Updates, update)
	})

	if !updateOk {
		return util.HttpErr(http.StatusNotFound, "not found or permission denied")
	}
	if stateConflict {
		return util.HttpErr(http.StatusConflict, "this dataset is currently being published")
	}

	// Remove the copies of the deleted entries from the folder backing the dataset.
	datasetDeleteEntryCopies(actor, datasetFolder, deletions)

	if len(additions) > 0 {
		go datasetTrackCopies(datasetId, actor.Username, providerId)
	}

	return nil
}

// DatasetDelete deletes a dataset which has not been published. The folder which backs the dataset is removed
// from the drive which holds the datasets of the project, afterwards the dataset itself is removed. Datasets
// which are currently being published or have already been published can no longer be deleted.
func DatasetDelete(actor rpc.Actor, id string) *util.HttpError {
	datasetId := ResourceParseId(id)

	dataset, _, baseSpec, err := ResourceRetrieveEx[orcapi.Dataset](
		actor,
		datasetType,
		datasetId,
		orcapi.PermissionEdit,
		orcapi.ResourceFlags{IncludeUpdates: true},
	)
	if err != nil {
		return err
	}

	switch dataset.Status.State {
	case orcapi.DatasetStatePublishing:
		return util.HttpErr(http.StatusConflict, "this dataset is currently being published")
	case orcapi.DatasetStatePublished:
		return util.HttpErr(http.StatusConflict, "this dataset has been published and can no longer be deleted")
	case orcapi.DatasetStateDraft, orcapi.DatasetStateFailure:
		// Deny deletion while files are still being copied into the dataset. Removing the folder mid-copy would
		// leave the copy tasks writing into a partially removed folder. The dataset becomes deletable again once
		// it reaches the READY state (or once the copies have failed).
		pending, _ := datasetCopyTasksPending(dataset.Owner.CreatedBy, baseSpec.Product.Provider, datasetCopyStartedAt(dataset))
		if pending > 0 {
			return util.HttpErr(http.StatusConflict, "files are currently being copied into this dataset, try again once it is ready")
		}
	}

	// Remove the folder which backs the dataset before removing the dataset itself: should this fail, then the
	// dataset is left untouched and the operation can simply be retried. A folder which is already gone (e.g. a
	// previous attempt was interrupted after removing it) is not treated as an error.
	datasetFolder := dataset.Status.Path
	if datasetFolder != "" {
		if _, err := FilesDelete(actor, fndapi.BulkRequestOf(fndapi.FindByStringId{Id: datasetFolder})); err != nil && err.StatusCode != http.StatusNotFound {
			return err
		}
	}

	ok := ResourceDelete(actor, datasetType, datasetId)
	if !ok {
		return util.HttpErr(http.StatusInternalServerError, "unable to delete the dataset")
	}

	return nil
}

// datasetSelectionHasOverlap reports whether the selection contains a folder along with something inside of it.
func datasetSelectionHasOverlap(entries []orcapi.DatasetEntry) bool {
	for _, selected := range entries {
		for _, other := range entries {
			if selected.Path != other.Path && datasetPathIsWithin(other.Path, selected.Path) {
				return true
			}
		}
	}
	return false
}

func datasetRemoveEntries(entries []orcapi.DatasetEntry, removed []orcapi.DatasetEntry) []orcapi.DatasetEntry {
	result := make([]orcapi.DatasetEntry, 0, len(entries))
	for _, entry := range entries {
		isRemoved := false
		for _, toRemove := range removed {
			if entry.Path == toRemove.Path {
				isRemoved = true
				break
			}
		}
		if !isRemoved {
			result = append(result, entry)
		}
	}
	return result
}

// datasetMergeEntries merges added entries into a selection. An entry which is already present is replaced, this
// refreshes its copy.
func datasetMergeEntries(entries []orcapi.DatasetEntry, added []orcapi.DatasetEntry) []orcapi.DatasetEntry {
	result := make([]orcapi.DatasetEntry, 0, len(entries)+len(added))
	result = append(result, entries...)
	for _, entry := range added {
		replaced := false
		for i, existing := range result {
			if existing.Path == entry.Path {
				result[i] = entry
				replaced = true
				break
			}
		}
		if !replaced {
			result = append(result, entry)
		}
	}
	return result
}

// datasetDeleteEntryCopies removes the copies of entries which have been removed from the selection of a dataset.
// This is best-effort: should it fail, then the files are no longer referenced by the selection but they might
// remain in the folder backing the dataset.
func datasetDeleteEntryCopies(actor rpc.Actor, datasetFolder string, entries []orcapi.DatasetEntry) {
	if len(entries) == 0 {
		return
	}

	paths := make([]fndapi.FindByStringId, 0, len(entries))
	for _, entry := range entries {
		paths = append(paths, fndapi.FindByStringId{Id: datasetEntryDestinationPath(datasetFolder, entry.Path)})
	}

	if _, err := FilesDelete(actor, fndapi.BulkRequestOf(paths...)); err != nil {
		log.Warn("Failed to remove deleted entries from dataset: %s", err)
	}
}

// datasetTrackCopies follows the copy tasks created by the provider and transitions the dataset to the READY state
// once every copy has finished. The dataset ends up in the FAILURE state if one of the copies could not complete.
func datasetTrackCopies(datasetId ResourceId, ownerUsername string, providerId string) {
	const pollInterval = 10 * time.Second
	const maxDuration = 24 * time.Hour

	deadline := time.Now().Add(maxDuration)
	for time.Now().Before(deadline) {
		time.Sleep(pollInterval)

		dataset, err := ResourceRetrieve[orcapi.Dataset](rpc.ActorSystem, datasetType, datasetId, orcapi.ResourceFlags{
			IncludeUpdates: true,
		})
		if err != nil {
			// The dataset is gone, nothing left to do.
			return
		}

		pending, failed := datasetCopyTasksPending(ownerUsername, providerId, datasetCopyStartedAt(dataset))
		if pending == 0 {
			if failed {
				datasetPostState(datasetId, orcapi.DatasetStateFailure, "one or more of the selected files and folders could not be copied")
			} else {
				datasetPostState(datasetId, orcapi.DatasetStateReady, "")
			}
			return
		}
	}

	datasetPostState(datasetId, orcapi.DatasetStateFailure, "copying the dataset timed out")
}

// datasetCopyStartedAt returns the point in time at which the dataset last started (re-)populating its selection.
// Copy tasks created after this point in time belong to the dataset: for a new dataset this is its creation time,
// every later entry addition records a new DRAFT state update at the time its copies were issued.
func datasetCopyStartedAt(dataset orcapi.Dataset) time.Time {
	startedAt := dataset.CreatedAt.Time()
	for _, update := range dataset.Updates {
		if update.State.Present && update.State.Value == orcapi.DatasetStateDraft {
			startedAt = update.Timestamp.Time()
		}
	}
	return startedAt
}

// datasetCopyTasksPending counts the copy tasks which belong to this dataset and are still running. Tasks are matched
// on their owner, provider and the point in time at which the copies were started.
//
// NOTE(Henrik): The copy operations of the file system do not return the ids of the tasks which they create, tasks are
// matched by owner, provider and creation time instead. Tasks unrelated to this dataset (e.g. a manual copy started
// by the same user at the same provider) will delay the transition to READY. Providers can tag their tasks with the
// dataset id (through the meta field) to make this matching exact.
func datasetCopyTasksPending(ownerUsername string, providerId string, startedAt time.Time) (pending int, failed bool) {
	next := util.OptNone[string]()
	for {
		page, err := TasksBrowseForUser(ownerUsername, 1000, next)
		if err != nil {
			// The tasks could not be read: report them as pending such that the dataset does not transition to
			// READY prematurely. The tracker retries and eventually gives up with a timeout.
			log.Warn("Could not browse the tasks of %s: %s", ownerUsername, err)
			pending++
			return pending, failed
		}

		for _, task := range page.Items {
			if task.Provider != providerId {
				continue
			}
			if task.CreatedAt.Time().Before(startedAt) {
				continue
			}

			switch task.Status.State {
			case fndapi.TaskStateSuccess:
				// finished successfully
			case fndapi.TaskStateFailure:
				failed = true
			default:
				pending++
			}
		}
		if !page.Next.Present {
			return pending, failed
		}
		next = page.Next
	}
}

// datasetPostState appends an update to the dataset and moves it to a new state. Datasets which have started
// publishing are immutable, their state can no longer be changed.
func datasetPostState(datasetId ResourceId, state orcapi.DatasetState, message string) bool {
	return ResourceSystemUpdate[orcapi.Dataset](datasetType, datasetId, func(r *resource, mapped orcapi.Dataset) {
		d := r.Extra.(*internalDataset)

		switch d.State {
		case orcapi.DatasetStatePublishing, orcapi.DatasetStatePublished:
			return
		}

		update := orcapi.DatasetUpdate{
			Timestamp: fndapi.Timestamp(time.Now()),
			State:     util.OptValue(state),
		}
		if message != "" {
			update.Status = util.OptValue(message)
		}
		d.Updates = append(d.Updates, update)

		d.State = state
	})
}

type internalDataset struct {
	Metadata  orcapi.DatasetMetadata
	Entries   []orcapi.DatasetEntry
	State     orcapi.DatasetState
	Path      util.Option[string]
	TargetId  string
	TargetUrl string
	Updates   []orcapi.DatasetUpdate
}

func datasetLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		Resource     int64
		CurrentState string
		Entries      []byte
		Metadata     []byte
		Path         sql.Null[string]
		TargetId     sql.Null[string]
		TargetUrl    sql.Null[string]
		Updates      []byte
	}](
		tx,
		`
			select resource, current_state, entries, metadata, path, target_id, target_url, updates
			from data_archival.datasets
			where resource = some(cast(:ids as int8[]))
		`,
		db.Params{
			"ids": ids,
		},
	)

	for _, row := range rows {
		result := &internalDataset{
			State:     orcapi.DatasetState(row.CurrentState),
			Path:      util.SqlNullToOpt(row.Path),
			TargetId:  row.TargetId.V,
			TargetUrl: row.TargetUrl.V,
		}

		_ = json.Unmarshal(row.Entries, &result.Entries)
		_ = json.Unmarshal(row.Metadata, &result.Metadata)
		_ = json.Unmarshal(row.Updates, &result.Updates)

		resources[ResourceId(row.Resource)].Extra = result
	}
}

func datasetPersist(b *db.Batch, r *resource) {
	if r.MarkedForDeletion {
		db.BatchExec(
			b,
			`delete from data_archival.publications where dataset = :id`,
			db.Params{
				"id": r.Id,
			},
		)

		db.BatchExec(
			b,
			`delete from data_archival.datasets where resource = :id`,
			db.Params{
				"id": r.Id,
			},
		)
	} else {
		d := r.Extra.(*internalDataset)

		driveId, _ := orcapi.DriveIdFromUCloudPath(d.Path.GetOrDefault(""))
		entries, _ := json.Marshal(util.NonNilSlice(d.Entries))
		metadata, _ := json.Marshal(d.Metadata)
		updates, _ := json.Marshal(util.NonNilSlice(d.Updates))

		db.BatchExec(
			b,
			`
				insert into data_archival.datasets
					(resource, drive, created_by, source_project_id, current_state, entries, path, metadata, target_id, target_url, updates)
				values (:id, :drive, :created_by, :project, :state, :entries, :path, :metadata, :target_id, :target_url, :updates)
				on conflict (resource) do update set
					current_state = excluded.current_state,
					entries = excluded.entries,
					path = excluded.path,
					metadata = excluded.metadata,
					target_id = excluded.target_id,
					target_url = excluded.target_url,
					updates = excluded.updates
			`,
			db.Params{
				"id":         r.Id,
				"drive":      driveId,
				"created_by": r.Owner.CreatedBy,
				"project":    r.Owner.Project.GetOrDefault(""),
				"state":      d.State,
				"entries":    entries,
				"path":       d.Path.Sql(),
				"metadata":   metadata,
				"target_id":  d.TargetId,
				"target_url": d.TargetUrl,
				"updates":    updates,
			},
		)
	}
}

func datasetTransform(
	r orcapi.Resource,
	specification orcapi.ResourceSpecification,
	extra any,
	flags orcapi.ResourceFlags,
	actor rpc.Actor,
) any {
	d := extra.(*internalDataset)

	result := orcapi.Dataset{
		Resource: r,
		Specification: orcapi.DatasetSpecification{
			DatasetMetadata:       d.Metadata,
			Entries:               util.NonNilSlice(d.Entries),
			ResourceSpecification: specification,
		},
		Status: orcapi.DatasetStatus{
			State:     d.State,
			Path:      d.Path.GetOrDefault(""),
			TargetId:  d.TargetId,
			TargetUrl: d.TargetUrl,
		},
	}

	if flags.IncludeUpdates {
		result.Updates = util.NonNilSlice(d.Updates)
	}

	if (flags.IncludeProduct || flags.IncludeSupport) && resourceSpecificationHasProduct(specification) {
		// NOTE(Dan): The product of a dataset references the storage product of the drive holding the datasets of
		// the project, support is resolved from it.
		support, _ := SupportByProduct[orcapi.FSSupport](driveType, specification.Product)
		result.Status.ResourceStatus = orcapi.ResourceStatus[orcapi.FSSupport]{
			ResolvedSupport: util.OptValue(support.ToApi()),
			ResolvedProduct: util.OptValue(support.Product),
		}
	}

	return result
}
