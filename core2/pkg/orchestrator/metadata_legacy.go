package orchestrator

import (
	"encoding/json"
	"fmt"
	"net/http"
	"path/filepath"
	"runtime"
	"slices"
	"strings"
	"sync"
	"time"

	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initMetadata() {
	for i := 0; i < runtime.NumCPU(); i++ {
		metadataGlobals.Buckets = append(metadataGlobals.Buckets, &metadataBucket{
			ByOwner:  map[string]*metadataIndex{},
			ByPath:   map[string]*internalMetadataDocument{},
			ByFolder: map[string]*metadataIndex{},
		})
	}

	{
		ns := &metadataGlobals.FavoriteNs
		ns.Id = "1"
		ns.Specification.Name = "favorite"
		ns.Specification.NamespaceType = "PER_USER"
		ns.Status.LatestTitle = "Favorite"
	}

	{
		ns := &metadataGlobals.SensitivityNs
		ns.Id = "2"
		ns.Specification.Name = "sensitivity"
		ns.Specification.NamespaceType = "COLLABORATORS"
		ns.Status.LatestTitle = "UCloud File Sensitivity"
	}

	if !metadataGlobals.TestingEnabled {
		orcapi.FileMetadataDocBrowse.Handler(func(
			info rpc.RequestInfo,
			request orcapi.FileMetadataDocBrowseRequest,
		) (fndapi.PageV2[orcapi.FileMetadataAttached], *util.HttpError) {
			flags := MetadataFlag(0)
			if !request.FilterTemplate.Present {
				flags |= MetadataIncludeSensitivity
				flags |= MetadataIncludeFavorite
			} else if strings.EqualFold(request.FilterTemplate.Value, "sensitivity") {
				flags |= MetadataIncludeSensitivity
			} else if strings.EqualFold(request.FilterTemplate.Value, "favorite") {
				flags |= MetadataIncludeFavorite
			}

			resultPage := MetadataBrowseOwner(info.Actor, request.ItemsPerPage, request.Next, flags)
			result := fndapi.PageV2[orcapi.FileMetadataAttached]{
				Next:         resultPage.Next,
				ItemsPerPage: resultPage.ItemsPerPage,
			}

			for _, item := range resultPage.Items {
				result.Items = util.Combined(result.Items, MetadataDocToApi(info.Actor, item, flags))
			}

			return result, nil
		})

		orcapi.FileMetadataDocCreate.Handler(func(
			info rpc.RequestInfo,
			request fndapi.BulkRequest[orcapi.FileMetadataDocCreateRequest],
		) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
			var result fndapi.BulkResponse[fndapi.FindByStringId]

			for _, item := range request.Items {
				path := filepath.Clean(item.FilePath)

				switch item.Metadata.TemplateId {
				case metadataGlobals.FavoriteNs.Id:
					err := MetadataMarkAsFavorite(info.Actor, path, true)
					if err != nil {
						return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
					}

					result.Responses = append(
						result.Responses,
						fndapi.FindByStringId{
							Id: fmt.Sprintf("favorite\n%s", path),
						},
					)

				case metadataGlobals.SensitivityNs.Id:
					var sensitivityDoc struct {
						Sensitivity SensitivityLevel `json:"sensitivity"`
					}

					_ = json.Unmarshal(item.Metadata.Document, &sensitivityDoc)
					_, ok := util.VerifyEnum(sensitivityDoc.Sensitivity, SensitivityLevelOptions)
					if ok {
						err := MetadataSetSensitivity(info.Actor, path, util.OptValue(sensitivityDoc.Sensitivity))
						if err != nil {
							return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
						}

						result.Responses = append(
							result.Responses,
							fndapi.FindByStringId{
								Id: fmt.Sprintf("sensitivity\n%s", path),
							},
						)
					} else {
						return fndapi.BulkResponse[fndapi.FindByStringId]{}, util.HttpErr(
							http.StatusBadRequest,
							"unknown sensitivity level requested",
						)
					}
				}
			}

			return result, nil
		})

		orcapi.FileMetadataDocDelete.Handler(func(
			info rpc.RequestInfo,
			request fndapi.BulkRequest[fndapi.FindByStringId],
		) (util.Empty, *util.HttpError) {
			for _, item := range request.Items {
				splitIdx := strings.Index(item.Id, "\n")
				if splitIdx >= 0 && len(item.Id) > splitIdx+1 {
					nsType := item.Id[:splitIdx]
					path := item.Id[splitIdx+1:]

					switch nsType {
					case "favorite":
						err := MetadataMarkAsFavorite(info.Actor, path, false)
						if err != nil {
							return util.Empty{}, err
						}

					case "sensitivity":
						err := MetadataSetSensitivity(info.Actor, path, util.OptNone[SensitivityLevel]())
						if err != nil {
							return util.Empty{}, err
						}
					}
				}
			}

			return util.Empty{}, nil
		})

		orcapi.FileMetadataNamespaceBrowse.Handler(func(
			info rpc.RequestInfo,
			request orcapi.FileMetadataNamespaceBrowseRequest,
		) (fndapi.PageV2[orcapi.FileMetadataTemplateNamespace], *util.HttpError) {
			result := fndapi.PageV2[orcapi.FileMetadataTemplateNamespace]{
				ItemsPerPage: fndapi.ItemsPerPage(request.ItemsPerPage),
				Next:         util.OptNone[string](),
			}

			if !request.FilterName.Present || request.FilterName.Value == "sensitivity" {
				result.Items = append(result.Items, metadataGlobals.SensitivityNs)
			}

			if !request.FilterName.Present || request.FilterName.Value == "favorite" {
				result.Items = append(result.Items, metadataGlobals.FavoriteNs)
			}

			return result, nil
		})

		db.NewTx0(func(tx *db.Transaction) {
			row, ok := db.Get[struct{ Resource int }](
				tx,
				`
					select resource
					from file_orchestrator.metadata_template_namespaces
					where uname = 'favorite'
			    `,
				db.Params{},
			)

			if ok {
				metadataGlobals.FavoriteNs.Id = fmt.Sprint(row.Resource)
			}

			row, ok = db.Get[struct{ Resource int }](
				tx,
				`
					select resource
					from file_orchestrator.metadata_template_namespaces
					where uname = 'sensitivity'
			    `,
				db.Params{},
			)

			if ok {
				metadataGlobals.SensitivityNs.Id = fmt.Sprint(row.Resource)
			}
		})
	}
}

// Internal state
// =====================================================================================================================
// Lock order is: Bucket -> ByOwner -> ByFolder -> ByPath (doc).

var metadataGlobals struct {
	TestingEnabled bool
	Buckets        []*metadataBucket
	FavoriteNs     orcapi.FileMetadataTemplateNamespace
	SensitivityNs  orcapi.FileMetadataTemplateNamespace
}

type metadataIndex struct {
	Mu       sync.RWMutex
	Elements map[string]util.Empty
}

type metadataBucket struct {
	Mu       sync.RWMutex
	ByOwner  map[string]*metadataIndex // drive owner (project/username) -> path
	ByPath   map[string]*internalMetadataDocument
	ByFolder map[string]*metadataIndex // folder -> paths
}

type SensitivityLevel string

const (
	SensitivityPrivate      SensitivityLevel = "PRIVATE"
	SensitivityConfidential SensitivityLevel = "CONFIDENTIAL"
	SensitivitySensitive    SensitivityLevel = "SENSITIVE"
)

var SensitivityLevelOptions = []SensitivityLevel{
	SensitivityPrivate,
	SensitivityConfidential,
	SensitivitySensitive,
}

type internalMetadataDocument struct {
	Mu                   sync.RWMutex
	Path                 string
	Sensitivity          util.Option[SensitivityLevel]
	SensitivityUpdatedBy string
	Favorite             map[string]util.Empty // set of users with favorite status
}

type MetadataDocument struct {
	Path        string
	Sensitivity util.Option[SensitivityLevel]
	Favorite    bool
}

func metadataBucketByKey(key any) *metadataBucket {
	return metadataGlobals.Buckets[util.NonCryptographicHash(key)%len(metadataGlobals.Buckets)]
}

// Metadata read operations
// =====================================================================================================================

func metadataAtPath(path string, username string) (MetadataDocument, bool) {
	metadataLoadIfNeeded(path)

	path = filepath.Clean(path)
	b := metadataBucketByKey(path)
	b.Mu.RLock()
	doc, ok := b.ByPath[path]
	b.Mu.RUnlock()

	if ok {
		doc.Mu.RLock()
		_, isFavorite := doc.Favorite[username]
		sensitivityLevel := doc.Sensitivity
		doc.Mu.RUnlock()

		return MetadataDocument{
			Path:        path,
			Sensitivity: sensitivityLevel,
			Favorite:    isFavorite,
		}, true
	} else {
		return MetadataDocument{}, false
	}
}

func MetadataRetrieveAtPath(actor rpc.Actor, path string) (MetadataDocument, bool) {
	path = filepath.Clean(path)
	driveId, ok := orcapi.DriveIdFromUCloudPath(path)
	if !ok {
		return MetadataDocument{}, false
	}

	_, err := ResourceRetrieve[orcapi.Drive](actor, driveType, ResourceParseId(driveId), orcapi.ResourceFlags{})
	if err != nil {
		return MetadataDocument{}, false
	}

	return metadataAtPath(path, actor.Username)
}

func MetadataBrowseFolder(actor rpc.Actor, path string) (map[string]MetadataDocument, bool) {
	metadataLoadIfNeeded(path)

	path = filepath.Clean(path)
	driveId, ok := orcapi.DriveIdFromUCloudPath(path)
	if !ok {
		return map[string]MetadataDocument{}, false
	}

	_, err := ResourceRetrieve[orcapi.Drive](actor, driveType, ResourceParseId(driveId), orcapi.ResourceFlags{})
	if err != nil {
		return map[string]MetadataDocument{}, false
	}

	b := metadataBucketByKey(path)
	b.Mu.RLock()
	idx, ok := b.ByFolder[path]
	b.Mu.RUnlock()

	var paths []string
	if ok {
		idx.Mu.RLock()
		for p := range idx.Elements {
			paths = append(paths, p)
		}
		idx.Mu.RUnlock()
	}

	result := map[string]MetadataDocument{}
	for _, childPath := range paths {
		result[childPath], ok = metadataAtPath(childPath, actor.Username)
		if !ok {
			delete(result, childPath)
		}
	}

	return result, true
}

type MetadataFlag uint64

const (
	MetadataIncludeFavorite MetadataFlag = 1 << iota
	MetadataIncludeSensitivity
)

func MetadataBrowseOwner(
	actor rpc.Actor,
	itemsPerPage int,
	next util.Option[string],
	flags MetadataFlag,
) fndapi.PageV2[MetadataDocument] {
	ownerRef := string(actor.Project.GetOrDefault(rpc.ProjectId(actor.Username)))
	itemsPerPage = fndapi.ItemsPerPage(itemsPerPage)
	metadataLoadIfNeededByOwner(ownerRef)

	b := metadataBucketByKey(ownerRef)
	b.Mu.RLock()
	idx, ok := b.ByOwner[ownerRef]
	b.Mu.RUnlock()

	var paths []string
	if ok {
		idx.Mu.RLock()
		for p := range idx.Elements {
			paths = append(paths, p)
		}
		idx.Mu.RUnlock()
	}

	slices.Sort(paths)

	if next.Present {
		slot, ok := slices.BinarySearch(paths, next.Value)
		offset := 0
		if ok {
			offset = 1
		}

		if len(paths) > slot+offset {
			paths = paths[slot+offset:]
		}
	}

	var result fndapi.PageV2[MetadataDocument]
	for i, childPath := range paths {
		child, ok := metadataAtPath(childPath, actor.Username)
		shouldInclude := ok
		if shouldInclude {
			shouldInclude = (flags&MetadataIncludeFavorite != 0) && child.Favorite
			shouldInclude = shouldInclude || (flags&MetadataIncludeSensitivity != 0) && child.Sensitivity.Present
		}

		if shouldInclude {
			result.Items = append(result.Items, child)

			if len(result.Items) >= itemsPerPage && i+1 >= len(result.Items) {
				result.Next.Set(child.Path)
				break
			}
		}
	}

	return result
}

func MetadataMigrateToNewPath(sourcePath string, destinationPath string) {
	// Moves metadata (if present) from sourcePath to destinationPath. Owner remains the same. Drive might change.

	sourcePath = filepath.Clean(sourcePath)
	destinationPath = filepath.Clean(destinationPath)
	if sourcePath == destinationPath {
		return
	}

	srcBucket := metadataBucketByKey(sourcePath)
	srcBucket.Mu.RLock()
	doc, ok := srcBucket.ByPath[sourcePath]
	srcBucket.Mu.RUnlock()
	if !ok || doc == nil {
		// Nothing to migrate.
		return
	}

	// Find owner from source or destination drive
	// -----------------------------------------------------------------------------------------------------------------
	ownerRef := ""
	if driveId, ok := orcapi.DriveIdFromUCloudPath(sourcePath); ok {
		drive, err := ResourceRetrieve[orcapi.Drive](rpc.ActorSystem, driveType, ResourceParseId(driveId), orcapi.ResourceFlags{})
		if err == nil {
			ownerRef = drive.Owner.Project.GetOrDefault(drive.Owner.CreatedBy)
		}
	}

	if ownerRef == "" {
		if driveId, ok := orcapi.DriveIdFromUCloudPath(destinationPath); ok {
			drive, err := ResourceRetrieve[orcapi.Drive](rpc.ActorSystem, driveType, ResourceParseId(driveId), orcapi.ResourceFlags{})
			if err == nil {
				ownerRef = drive.Owner.Project.GetOrDefault(drive.Owner.CreatedBy)
			}
		}
	}

	// Remove the old mapping and update owner mapping
	// -----------------------------------------------------------------------------------------------------------------
	srcBucket.Mu.Lock()
	if cur, exists := srcBucket.ByPath[sourcePath]; exists && cur == doc {
		delete(srcBucket.ByPath, sourcePath)
	}
	srcBucket.Mu.Unlock()

	srcParent := filepath.Dir(sourcePath)
	srcParentBucket := metadataBucketByKey(srcParent)
	srcParentBucket.Mu.Lock()
	if idx, ok := srcParentBucket.ByFolder[srcParent]; ok {
		idx.Mu.Lock()
		delete(idx.Elements, sourcePath)
		idx.Mu.Unlock()
	}
	srcParentBucket.Mu.Unlock()

	if ownerRef != "" {
		ownerBucket := metadataBucketByKey(ownerRef)
		ownerIdx := util.ReadOrInsertBucket(&ownerBucket.Mu, ownerBucket.ByOwner, ownerRef, func() *metadataIndex {
			return &metadataIndex{Elements: map[string]util.Empty{}}
		})

		ownerIdx.Mu.Lock()
		delete(ownerIdx.Elements, sourcePath)
		ownerIdx.Elements[destinationPath] = util.Empty{}
		ownerIdx.Mu.Unlock()
	}

	doc.Mu.Lock()
	doc.Path = destinationPath
	doc.Mu.Unlock()

	// Reindex
	// -----------------------------------------------------------------------------------------------------------------
	dstBucket := metadataBucketByKey(destinationPath)
	dstBucket.Mu.Lock()
	dstBucket.ByPath[destinationPath] = doc
	dstBucket.Mu.Unlock()

	dstParent := filepath.Dir(destinationPath)
	dstParentBucket := metadataBucketByKey(dstParent)
	dstIdx := util.ReadOrInsertBucket(&dstParentBucket.Mu, dstParentBucket.ByFolder, dstParent, func() *metadataIndex {
		return &metadataIndex{Elements: map[string]util.Empty{}}
	})
	dstIdx.Mu.Lock()
	dstIdx.Elements[destinationPath] = util.Empty{}
	dstIdx.Mu.Unlock()

	if !metadataGlobals.TestingEnabled {
		db.NewTx0(func(tx *db.Transaction) {
			b := db.BatchNew(tx)
			db.BatchExec(
				b,
				`delete from file_orchestrator.metadata_documents where path = :path`,
				db.Params{"path": sourcePath},
			)

			doc.Mu.RLock()
			lMetadataPersist(b, doc)
			doc.Mu.RUnlock()

			db.BatchSend(b)
		})
	}
}

// Metadata updates
// =====================================================================================================================

func MetadataMarkAsFavorite(actor rpc.Actor, path string, favorite bool) *util.HttpError {
	path = filepath.Clean(path)
	return metadataUpdate(actor, path, orcapi.PermissionRead, func(doc *internalMetadataDocument) {
		if favorite {
			doc.Favorite[actor.Username] = util.Empty{}
		} else {
			delete(doc.Favorite, actor.Username)
		}
	})
}

func MetadataSetSensitivity(actor rpc.Actor, path string, level util.Option[SensitivityLevel]) *util.HttpError {
	path = filepath.Clean(path)
	return metadataUpdate(actor, path, orcapi.PermissionEdit, func(doc *internalMetadataDocument) {
		doc.Sensitivity = level
		doc.SensitivityUpdatedBy = actor.Username
	})
}

func metadataUpdate(
	actor rpc.Actor,
	path string,
	permissionRequired orcapi.Permission,
	updater func(doc *internalMetadataDocument),
) *util.HttpError {
	path = filepath.Clean(path)
	driveId, ok := orcapi.DriveIdFromUCloudPath(path)
	if !ok {
		return util.HttpErr(http.StatusNotFound, "not found")
	}

	drive, _, _, err := ResourceRetrieveEx[orcapi.Drive](actor, driveType, ResourceParseId(driveId),
		permissionRequired, orcapi.ResourceFlags{})
	if err != nil {
		return err
	}

	metadataLoadIfNeeded(path)

	b := metadataBucketByKey(path)
	b.Mu.RLock()
	doc, ok := b.ByPath[path]
	b.Mu.RUnlock()

	if !ok {
		b.Mu.Lock()
		doc, ok = b.ByPath[path]
		if !ok {
			doc = &internalMetadataDocument{Path: path, Favorite: map[string]util.Empty{}}
			b.ByPath[path] = doc
		}
		b.Mu.Unlock()

		{
			parentPath := filepath.Dir(path)
			parentBucket := metadataBucketByKey(parentPath)

			idx := util.ReadOrInsertBucket(&parentBucket.Mu, parentBucket.ByFolder, parentPath, func() *metadataIndex {
				return &metadataIndex{Elements: map[string]util.Empty{}}
			})

			idx.Mu.Lock()
			idx.Elements[path] = util.Empty{}
			idx.Mu.Unlock()
		}

		{
			ownerRef := drive.Owner.Project.GetOrDefault(drive.Owner.CreatedBy)
			ownerBucket := metadataBucketByKey(ownerRef)
			idx := util.ReadOrInsertBucket(&ownerBucket.Mu, ownerBucket.ByOwner, ownerRef, func() *metadataIndex {
				return &metadataIndex{Elements: map[string]util.Empty{}}
			})

			idx.Mu.Lock()
			idx.Elements[path] = util.Empty{}
			idx.Mu.Unlock()
		}
	}

	doc.Mu.Lock()
	updater(doc)
	doc.Mu.Unlock()

	metadataPersist(doc)
	return nil
}

// API translation layer
// =====================================================================================================================

func MetadataDocToApi(actor rpc.Actor, item MetadataDocument, flags MetadataFlag) []orcapi.FileMetadataAttached {
	var items []orcapi.FileMetadataAttached

	if item.Favorite && flags&MetadataIncludeFavorite != 0 {
		attached := orcapi.FileMetadataAttached{
			Path: item.Path,
			Metadata: orcapi.FileMetadataDocument{
				Id:   fmt.Sprintf("favorite\n%s", item.Path),
				Type: "metadata",
				Specification: orcapi.FileMetadataDocumentSpec{
					TemplateId: metadataGlobals.FavoriteNs.Id,
					Version:    "1.0.0",
					Document:   json.RawMessage(`{"favorite":true}`),
					ChangeLog:  "update",
				},
				CreatedAt: fndapi.Timestamp(time.Now()),
				CreatedBy: actor.Username,
			},
		}
		attached.Metadata.Status.Approval.Type = "not_required"
		items = append(items, attached)
	}

	if item.Sensitivity.Present && flags&MetadataIncludeSensitivity != 0 {
		var sensitivityDoc struct {
			Sensitivity SensitivityLevel `json:"sensitivity"`
		}
		sensitivityDoc.Sensitivity = item.Sensitivity.Value

		attached := orcapi.FileMetadataAttached{
			Path: item.Path,
			Metadata: orcapi.FileMetadataDocument{
				Id:   fmt.Sprintf("sensitivity\n%s", item.Path),
				Type: "metadata",
				Specification: orcapi.FileMetadataDocumentSpec{
					TemplateId: metadataGlobals.SensitivityNs.Id,
					Version:    "1.0.0",
					ChangeLog:  "update",
				},
				CreatedAt: fndapi.Timestamp(time.Now()),
				CreatedBy: actor.Username,
			},
		}
		attached.Metadata.Status.Approval.Type = "not_required"
		attached.Metadata.Specification.Document, _ = json.Marshal(sensitivityDoc)
		items = append(items, attached)
	}

	return items
}

// Persistence
// =====================================================================================================================

func metadataPersist(doc *internalMetadataDocument) {
	if metadataGlobals.TestingEnabled {
		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		b := db.BatchNew(tx)
		doc.Mu.RLock()
		lMetadataPersist(b, doc)
		doc.Mu.RUnlock()
		db.BatchSend(b)
	})
}

func lMetadataPersist(b *db.Batch, doc *internalMetadataDocument) {
	db.BatchExec(
		b,
		`delete from file_orchestrator.metadata_documents where path = :path`,
		db.Params{"path": doc.Path},
	)

	for username := range doc.Favorite {
		docJson := `{"favorite": true}`
		db.BatchExec(
			b,
			`
				insert into file_orchestrator.metadata_documents(path, parent_path, is_deletion, document, 
					change_log, created_by, workspace, is_workspace_project, latest, approval_type, 
					approval_updated_by, created_at, template_version, template_id) 
				values
					(:path, :parent, false, :doc, 'Update', :user, :workspace, :is_project, true, 'not_required',
					null, now(), '1.0.0', :template_id::int8)
			`,
			db.Params{
				"path":        doc.Path,
				"parent":      filepath.Dir(doc.Path),
				"doc":         docJson,
				"user":        username,
				"workspace":   username,
				"is_project":  false,
				"template_id": metadataGlobals.FavoriteNs.Id,
			},
		)
	}

	if doc.Sensitivity.Present {
		driveId, _ := orcapi.DriveIdFromUCloudPath(doc.Path)

		var sensitivityDoc struct {
			Sensitivity SensitivityLevel `json:"sensitivity"`
		}
		sensitivityDoc.Sensitivity = doc.Sensitivity.Value

		sensitivityJson, _ := json.Marshal(sensitivityDoc)

		db.BatchExec(
			b,
			`
				insert into file_orchestrator.metadata_documents(path, parent_path, is_deletion, document, 
					change_log, created_by, workspace, is_workspace_project, latest, approval_type, 
					approval_updated_by, created_at, template_version, template_id) 
				select
					:path, 
					:parent, 
					false, 
					:doc, 
					'Update', 
					:user, 
					coalesce(r.project, r.created_by),
					r.project is not null,
					true, 
					'not_required',
					null, 
					now(), 
					'1.0.0', 
					:template_id::int8
				from
					provider.resource r
				where
					r.id = :drive_id
			`,
			db.Params{
				"path":        doc.Path,
				"parent":      filepath.Dir(doc.Path),
				"doc":         string(sensitivityJson),
				"user":        doc.SensitivityUpdatedBy,
				"drive_id":    driveId,
				"template_id": metadataGlobals.SensitivityNs.Id,
			},
		)
	}
}

func metadataLoadIfNeeded(path string) {
	path = filepath.Clean(path)
	driveId, ok := orcapi.DriveIdFromUCloudPath(path)
	if !ok {
		return
	}

	drive, err := ResourceRetrieve[orcapi.Drive](rpc.ActorSystem, driveType, ResourceParseId(driveId),
		orcapi.ResourceFlags{})
	if err != nil {
		return
	}

	ownerRef := drive.Owner.Project.GetOrDefault(drive.Owner.CreatedBy)
	metadataLoadIfNeededByOwner(ownerRef)
}

func metadataLoadIfNeededByOwner(ownerRef string) {
	ownerBucket := metadataBucketByKey(ownerRef)
	ownerBucket.Mu.RLock()
	ownerIndex, loaded := ownerBucket.ByOwner[ownerRef]
	ownerBucket.Mu.RUnlock()

	if !loaded {
		ownerBucket.Mu.Lock()
		ownerIndex, loaded = ownerBucket.ByOwner[ownerRef]
		if !loaded {
			// NOTE(Dan): This will be filled now without inserting. We insert if we won the race to fill it.
			ownerIndex = &metadataIndex{Elements: map[string]util.Empty{}}
		}
		ownerBucket.Mu.Unlock()

		if !loaded {
			type loadResult struct {
				Path               string
				Document           string
				CreatedBy          string
				Workspace          string
				IsWorkspaceProject bool
				TemplateId         int
			}

			rows := db.NewTx(func(tx *db.Transaction) []loadResult {
				return db.Select[loadResult](
					tx,
					`
						select
							docs.path,
							docs.document,
							docs.created_by,
							docs.workspace,
							docs.is_workspace_project,
							docs.template_id
						from
							file_orchestrator.file_collections drive
							join provider.resource r on drive.resource = r.id,
							file_orchestrator.metadata_documents docs
						where 
							(
								path like '/' || r.id || '/%'
								or path = '/' || r.id
							)
							and not is_deletion
							and latest
							and (
								approval_type = 'not_required'
								or approval_type = 'approved'
							)
							and (
								r.project = :ref
								or (r.project is null and r.created_by = :ref)
							)
					`,
					db.Params{
						"ref": ownerRef,
					},
				)
			})

			for _, row := range rows {
				// Insert document
				// -----------------------------------------------------------------------------------------------------
				pathBucket := metadataBucketByKey(row.Path)
				pathBucket.Mu.Lock()
				doc, exists := pathBucket.ByPath[row.Path]
				if !exists {
					doc = &internalMetadataDocument{
						Path:     row.Path,
						Favorite: map[string]util.Empty{},
					}
					pathBucket.ByPath[row.Path] = doc
				}
				doc.Mu.Lock()

				switch fmt.Sprint(row.TemplateId) {
				case metadataGlobals.FavoriteNs.Id:
					doc.Favorite[row.CreatedBy] = util.Empty{}

				case metadataGlobals.SensitivityNs.Id:
					var sensitivityDoc struct {
						Sensitivity SensitivityLevel `json:"sensitivity"`
					}

					_ = json.Unmarshal([]byte(row.Document), &sensitivityDoc)
					_, ok := util.VerifyEnum(sensitivityDoc.Sensitivity, SensitivityLevelOptions)
					if ok {
						doc.Sensitivity.Set(sensitivityDoc.Sensitivity)
						doc.SensitivityUpdatedBy = row.CreatedBy
					}
				}

				doc.Mu.Unlock()
				pathBucket.Mu.Unlock()

				// Index into parent
				// -----------------------------------------------------------------------------------------------------
				parentPath := filepath.Dir(row.Path)
				parentBucket := metadataBucketByKey(parentPath)
				parentBucket.Mu.Lock()
				parentIndex, ok := parentBucket.ByFolder[parentPath]
				if !ok {
					parentIndex = &metadataIndex{Elements: map[string]util.Empty{}}
					parentBucket.ByFolder[parentPath] = parentIndex
				}
				parentIndex.Mu.Lock()
				parentIndex.Elements[row.Path] = util.Empty{}
				parentIndex.Mu.Unlock()
				parentBucket.Mu.Unlock()

				// Index into owner
				// -----------------------------------------------------------------------------------------------------
				ownerIndex.Elements[row.Path] = util.Empty{}
			}

			ownerBucket.Mu.Lock()
			_, loaded = ownerBucket.ByOwner[ownerRef]
			if !loaded {
				ownerBucket.ByOwner[ownerRef] = ownerIndex
			}
			ownerBucket.Mu.Unlock()
		}
	}
}
