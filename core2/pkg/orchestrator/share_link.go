package orchestrator

import (
	"net/http"
	"path/filepath"
	"sync"
	"time"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initShareLinks() {
	shareLinkLoadAll()

	orcapi.ShareLinkRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.ShareLinkRetrieveRequest) (orcapi.ShareLinkRetrieveResponse, *util.HttpError) {
		result, err := ShareLinkRetrieve(request.Token)
		if err != nil {
			return orcapi.ShareLinkRetrieveResponse{}, err
		} else {
			return orcapi.ShareLinkRetrieveResponse{
				Token:    result.Token,
				Path:     result.Path,
				SharedBy: result.SharedBy,
			}, nil
		}
	})

	orcapi.ShareLinkBrowse.Handler(func(info rpc.RequestInfo, request orcapi.ShareLinkBrowseRequest) (fndapi.PageV2[orcapi.ShareLink], *util.HttpError) {
		items, err := ShareLinkBrowse(info.Actor, request.Path)
		if err != nil {
			return fndapi.PageV2[orcapi.ShareLink]{}, err
		} else {
			return fndapi.PageV2[orcapi.ShareLink]{
				Items:        items,
				ItemsPerPage: len(items),
			}, nil
		}
	})

	orcapi.ShareLinkCreate.Handler(func(info rpc.RequestInfo, request orcapi.ShareLinkCreateRequest) (orcapi.ShareLink, *util.HttpError) {
		return ShareLinkCreate(info.Actor, request.Path)
	})

	orcapi.ShareLinkUpdate.Handler(func(info rpc.RequestInfo, request orcapi.ShareLinkUpdateRequest) (util.Empty, *util.HttpError) {
		err := ShareLinkUpdate(info.Actor, request.Token, request.Permissions)
		return util.Empty{}, err
	})

	orcapi.ShareLinkAccept.Handler(func(info rpc.RequestInfo, request orcapi.ShareLinkAcceptRequest) (orcapi.Share, *util.HttpError) {
		return ShareLinkAccept(info.Actor, request.Token)
	})

	orcapi.ShareLinkDelete.Handler(func(info rpc.RequestInfo, request orcapi.ShareLinkDeleteRequest) (util.Empty, *util.HttpError) {
		err := ShareLinkDelete(info.Actor, request.Token)
		return util.Empty{}, err
	})
}

// Internal state
// =====================================================================================================================

var shareLinkGlobals struct {
	Mu             sync.RWMutex
	ByPath         map[string][]string // path -> token
	ByToken        map[string]*internalShareLink
	TestingEnabled bool
}

type internalShareLink struct {
	Path        string
	Token       string
	Expires     fndapi.Timestamp
	Permissions []orcapi.Permission
	CreatedBy   string
}

// API
// =====================================================================================================================

func ShareLinkCreate(actor rpc.Actor, path string) (orcapi.ShareLink, *util.HttpError) {
	driveId, ok := orcapi.DriveIdFromUCloudPath(path)
	if !ok {
		return orcapi.ShareLink{}, util.HttpErr(http.StatusForbidden, "unable to share this file")
	}

	drive, _, _, err := ResourceRetrieveEx[orcapi.Drive](actor, driveType, ResourceParseId(driveId),
		orcapi.PermissionAdmin, orcapi.ResourceFlags{})

	if err != nil {
		return orcapi.ShareLink{}, util.HttpErr(http.StatusForbidden, "unable to share this file")
	}

	if drive.Owner.Project != "" {
		return orcapi.ShareLink{}, util.HttpErr(http.StatusForbidden, "unable to share files from a project")
	}

	ilink := &internalShareLink{
		Path:        filepath.Clean(path),
		Token:       util.SecureToken(),
		Expires:     fndapi.Timestamp(time.Now().Add(10 * 24 * time.Hour)),
		Permissions: []orcapi.Permission{orcapi.PermissionRead},
		CreatedBy:   actor.Username,
	}

	result := orcapi.ShareLink{
		Token:       ilink.Token,
		Expires:     ilink.Expires,
		Permissions: ilink.Permissions,
		Path:        ilink.Path,
		SharedBy:    ilink.CreatedBy,
	}

	g := &shareLinkGlobals
	g.Mu.Lock()
	g.ByPath[ilink.Path] = append(g.ByPath[ilink.Path], ilink.Token)
	g.ByToken[ilink.Token] = ilink
	g.Mu.Unlock()

	shareLinkPersist(result.Token)
	return result, nil
}

func ShareLinkUpdate(actor rpc.Actor, token string, newPermissions []orcapi.Permission) *util.HttpError {
	if len(newPermissions) == 0 {
		return util.HttpErr(http.StatusBadRequest, "the share link must have some permissions assigned to it")
	}

	for _, perm := range newPermissions {
		if _, ok := util.VerifyEnum(perm, shareValidPermissions); !ok {
			return util.HttpErr(http.StatusBadRequest, "invalid permissions specified")
		}
	}

	g := &shareLinkGlobals

	g.Mu.Lock()
	ilink, exists := g.ByToken[token]
	hasPermissions := false

	if exists {
		hasPermissions = ilink.CreatedBy == actor.Username
	}

	if exists && hasPermissions {
		ilink.Permissions = newPermissions
	}

	g.Mu.Unlock()

	if !exists || !hasPermissions {
		return util.HttpErr(http.StatusForbidden, "unable to update this link")
	}

	shareLinkPersist(token)
	return nil
}

func ShareLinkRetrieve(token string) (orcapi.ShareLink, *util.HttpError) {
	result := orcapi.ShareLink{}

	g := &shareLinkGlobals
	g.Mu.RLock()
	ilink, ok := g.ByToken[token]
	if ok {
		result = orcapi.ShareLink{
			Token:       ilink.Token,
			Expires:     ilink.Expires,
			Permissions: ilink.Permissions,
			Path:        ilink.Path,
			SharedBy:    ilink.CreatedBy,
		}
	}
	g.Mu.RUnlock()

	if !ok || time.Now().After(ilink.Expires.Time()) {
		return orcapi.ShareLink{}, util.HttpErr(http.StatusNotFound, "not found")
	} else {
		return result, nil
	}
}

func ShareLinkAccept(actor rpc.Actor, token string) (orcapi.Share, *util.HttpError) {
	link, err := ShareLinkRetrieve(token)
	if err != nil {
		return orcapi.Share{}, err
	}

	sharedByActor, ok := rpc.LookupActor(link.SharedBy)
	if !ok {
		return orcapi.Share{}, util.HttpErr(http.StatusNotFound, "unable to share this file")
	}

	driveId, ok := orcapi.DriveIdFromUCloudPath(link.Path)
	if !ok {
		return orcapi.Share{}, util.HttpErr(http.StatusNotFound, "unable to share this file")
	}

	drive, err := ResourceRetrieve[orcapi.Drive](sharedByActor, driveType, ResourceParseId(driveId),
		orcapi.ResourceFlags{})

	if err != nil {
		return orcapi.Share{}, util.HttpErr(http.StatusNotFound, "unable to share this file")
	}

	shareId, err := ShareCreate(sharedByActor, orcapi.ShareSpecification{
		SharedWith:     actor.Username,
		SourceFilePath: link.Path,
		Permissions:    link.Permissions,
		Product:        drive.Specification.Product,
	})

	if err != nil {
		return orcapi.Share{}, util.HttpErr(http.StatusBadRequest, "unable to share file, %s", err.Error())
	}

	err = ShareApprove(actor, shareId)
	if err != nil {
		_ = ShareReject(sharedByActor, shareId)
		return orcapi.Share{}, util.HttpErr(http.StatusBadRequest, "unable to share file, %s", err.Error())
	}

	share, err := ResourceRetrieve[orcapi.Share](actor, shareType, ResourceParseId(shareId), orcapi.ResourceFlags{})
	if err != nil {
		return orcapi.Share{}, util.HttpErr(
			http.StatusInternalServerError,
			"unable to retrieve share after accept: %s",
			err,
		)
	}

	return share, nil
}

func ShareLinkDelete(actor rpc.Actor, token string) *util.HttpError {
	g := &shareLinkGlobals
	g.Mu.Lock()

	ilink, ok := g.ByToken[token]
	if ok {
		ok = ilink.CreatedBy == actor.Username
	}

	if ok {
		g.ByPath[ilink.Path] = util.RemoveFirst(g.ByPath[ilink.Path], ilink.Path)
		delete(g.ByToken, token)
	}

	g.Mu.Unlock()

	if !ok {
		return util.HttpErr(http.StatusForbidden, "you are not allowed to delete this link")
	} else {
		if !g.TestingEnabled {
			db.NewTx0(func(tx *db.Transaction) {
				db.Exec(
					tx,
					`delete from file_orchestrator.shares_links where token = :token`,
					db.Params{
						"token": token,
					},
				)
			})
		}
		return nil
	}
}

func ShareLinkBrowse(actor rpc.Actor, path string) ([]orcapi.ShareLink, *util.HttpError) {
	driveId, ok := orcapi.DriveIdFromUCloudPath(path)
	if !ok {
		return nil, util.HttpErr(http.StatusNotFound, "unable to browse share links for this file")
	}

	_, _, _, err := ResourceRetrieveEx[orcapi.Drive](actor, driveType, ResourceParseId(driveId),
		orcapi.PermissionAdmin, orcapi.ResourceFlags{})

	if err != nil {
		return nil, util.HttpErr(http.StatusNotFound, "unable to browse share links for this file")
	}

	var result []orcapi.ShareLink

	g := &shareLinkGlobals
	g.Mu.RLock()
	tokens := g.ByPath[filepath.Clean(path)]
	for _, tok := range tokens {
		ilink, ok := g.ByToken[tok]
		if ok {
			result = append(result, orcapi.ShareLink{
				Token:       ilink.Token,
				Expires:     ilink.Expires,
				Permissions: ilink.Permissions,
				Path:        ilink.Path,
				SharedBy:    ilink.CreatedBy,
			})
		}
	}
	g.Mu.RUnlock()

	return result, nil
}

// Persistence
// =====================================================================================================================

func shareLinkLoadAll() {
	shareLinkGlobals.ByPath = map[string][]string{}
	shareLinkGlobals.ByToken = map[string]*internalShareLink{}

	if shareLinkGlobals.TestingEnabled {
		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		shareLinkGlobals.ByPath = map[string][]string{}
		shareLinkGlobals.ByToken = map[string]*internalShareLink{}

		rows := db.Select[struct {
			Token       string
			FilePath    string
			SharedBy    string
			Expires     time.Time
			Permissions []string
		}](
			tx,
			`
				select token, file_path, shared_by,  expires, permissions
				from file_orchestrator.shares_links
		    `,
			db.Params{},
		)

		for _, row := range rows {
			var perms []orcapi.Permission
			for _, perm := range row.Permissions {
				if v, ok := util.VerifyEnum(orcapi.Permission(perm), shareValidPermissions); ok {
					perms = append(perms, v)
				}
			}

			shareLinkGlobals.ByPath[row.FilePath] = append(shareLinkGlobals.ByPath[row.FilePath], row.Token)
			shareLinkGlobals.ByToken[row.Token] = &internalShareLink{
				Path:        row.FilePath,
				Token:       row.Token,
				Expires:     fndapi.Timestamp(row.Expires),
				Permissions: perms,
				CreatedBy:   row.SharedBy,
			}
		}
	})
}

func shareLinkPersist(linkToken string) {
	if shareLinkGlobals.TestingEnabled {
		return
	}

	var (
		path        string
		token       string
		expires     time.Time
		permissions []string
		createdBy   string
	)

	shareLinkGlobals.Mu.RLock()
	value, ok := shareLinkGlobals.ByToken[linkToken]
	if ok {
		path = value.Path
		token = value.Token
		expires = value.Expires.Time()
		createdBy = value.CreatedBy

		for _, perm := range value.Permissions {
			permissions = append(permissions, string(perm))
		}
	}
	shareLinkGlobals.Mu.RUnlock()

	if ok {
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					insert into file_orchestrator.shares_links(token, file_path, shared_by, expires, permissions) 
					values (:token, :file_path, :shared_by, :expires, :permissions)
					on conflict (token) do update set
						expires = excluded.expires,
						permissions = excluded.permissions
			    `,
				db.Params{
					"file_path":   path,
					"token":       token,
					"expires":     expires,
					"permissions": permissions,
					"shared_by":   createdBy,
				},
			)
		})
	}
}
