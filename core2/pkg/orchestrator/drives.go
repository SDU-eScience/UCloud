package orchestrator

import (
	"net/http"
	"strings"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const (
	drive = "file_collection"

	driveStatsSize          featureKey = "drive.stats.size"
	driveStatsRecursiveSize featureKey = "drive.stats.recursiveSize"
	driveStatsTimestamps    featureKey = "drive.stats.timestamps"
	driveStatsUnix          featureKey = "drive.stats.unix"

	// NOTE(Dan): No acl for files
	driveOpsTrash           featureKey = "drive.ops.trash"
	driveOpsReadOnly        featureKey = "drive.ops.readOnly"
	driveOpsSearch          featureKey = "drive.ops.search"
	driveOpsStreamingSearch featureKey = "drive.ops.streamingSearch"
	driveOpsShares          featureKey = "drive.ops.shares"
	driveOpsTerminal        featureKey = "drive.ops.terminal"

	driveAcl        featureKey = "drive.acl"
	driveManagement featureKey = "drive.management"
)

func initDrives() {
	InitResourceType(
		drive,
		0,
		driveLoad,
		drivePersist,
		driveTransform,
	)

	orcapi.DrivesBrowse.Handler(func(info rpc.RequestInfo, request orcapi.DrivesBrowseRequest) (fndapi.PageV2[orcapi.Drive], *util.HttpError) {
		return ResourceBrowse(
			info.Actor,
			drive,
			request.Next,
			request.ItemsPerPage,
			request.ResourceFlags,
			func(item orcapi.Drive) bool {
				return true
			},
		), nil
	})

	orcapi.DrivesCreate.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.DriveSpecification]) (fndapi.BulkResponse[fndapi.FindByStringId], *util.HttpError) {
		var responses []fndapi.FindByStringId
		for _, reqItem := range request.Items {
			d, err := DriveCreate(info.Actor, reqItem)
			if err != nil {
				return fndapi.BulkResponse[fndapi.FindByStringId]{}, err
			} else {
				responses = append(responses, fndapi.FindByStringId{Id: d.Id})
			}
		}
		return fndapi.BulkResponse[fndapi.FindByStringId]{Responses: responses}, nil
	})

	orcapi.DrivesRename.Handler(func(info rpc.RequestInfo, request fndapi.BulkRequest[orcapi.DriveRenameRequest]) (util.Empty, *util.HttpError) {
		for _, reqItem := range request.Items {
			err := DriveRename(info.Actor, reqItem.Id, reqItem.NewTitle)
			if err != nil {
				return util.Empty{}, err
			}
		}

		return util.Empty{}, nil
	})

	orcapi.DrivesRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.SupportByProvider[orcapi.FSSupport], *util.HttpError) {
		return SupportRetrieveProducts[orcapi.FSSupport](drive), nil
	})
}

func DriveRename(actor rpc.Actor, id string, title string) *util.HttpError {
	title = strings.TrimSpace(title)
	if title == "" || strings.Contains("\n", title) {
		return util.HttpErr(http.StatusBadRequest, "invalid title specified")
	}

	resc, err := ResourceRetrieveEx[orcapi.Drive](actor, drive, ResourceParseId(id), orcapi.PermissionEdit, orcapi.ResourceFlags{})
	if err != nil {
		return err
	}

	p := resc.Specification.Product

	if !featureSupported(drive, p, driveManagement) {
		return featureNotSupportedError
	}

	_, err = InvokeProvider(
		p.Provider,
		orcapi.DrivesProviderRename,
		fndapi.BulkRequestOf(orcapi.DriveRenameRequest{
			Id:       id,
			NewTitle: title,
		}),
		ProviderCallOpts{
			Username: util.OptValue(actor.Username),
		},
	)

	if err != nil {
		return err
	}

	ResourceUpdate(actor, drive, ResourceParseId(id), orcapi.PermissionEdit, func(r *resource, mapped orcapi.Drive) {
		r.Extra.(*driveInfo).Title = title
	})
	return nil
}

func DriveCreate(actor rpc.Actor, item orcapi.DriveSpecification) (orcapi.Drive, *util.HttpError) {
	p := item.Product
	if !featureSupported(drive, p, driveManagement) {
		return orcapi.Drive{}, featureNotSupportedError
	}

	info := &driveInfo{Title: item.Title}
	return ResourceCreateThroughProvider(actor, drive, p, info, orcapi.DrivesProviderCreate)
}

type driveInfo struct {
	Title string
}

func driveLoad(tx *db.Transaction, ids []int64, resources map[ResourceId]*resource) {
	rows := db.Select[struct {
		Resource int64
		Title    string
	}](
		tx,
		`
			select resource, title
			from file_orchestrator.file_collections d
			where
				d.resource = some(cast(:ids as int8[]))
	    `,
		db.Params{
			"ids": ids,
		},
	)

	for _, row := range rows {
		resources[ResourceId(row.Resource)].Extra = &driveInfo{Title: row.Title}
	}
}

func driveTransform(r orcapi.Resource, product util.Option[accapi.ProductReference], extra any) any {
	info := extra.(*driveInfo)

	return orcapi.Drive{
		Resource: r,
		Specification: orcapi.DriveSpecification{
			Title:   info.Title,
			Product: product.Value,
		},
		Updates: make([]orcapi.ResourceUpdate, 0),
	}
}

func drivePersist(tx *db.Transaction, resources []*resource) {
	var ids []int64
	var titles []string
	for _, r := range resources {
		ids = append(ids, int64(r.Id))
		titles = append(titles, r.Extra.(*driveInfo).Title)
	}

	db.Exec(
		tx,
		`
			insert into file_orchestrator.file_collections(resource, title) 
			select unnest(cast(:ids as int8[])), unnest(cast(:titles as text[]))
			on conflict (resource) do update set title = excluded.title
	    `,
		db.Params{
			"ids":    ids,
			"titles": titles,
		},
	)
}
