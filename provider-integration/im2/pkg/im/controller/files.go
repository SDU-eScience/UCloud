package controller

import (
    "fmt"
    "net/http"
    fnd "ucloud.dk/pkg/foundation"
    cfg "ucloud.dk/pkg/im/config"
    "ucloud.dk/pkg/orchestrators"
)

type FileService struct {
    BrowseFiles  func(request BrowseFilesRequest) (fnd.PageV2[orchestrators.ProviderFile], error)
    CreateFolder func(request CreateFolderRequest) error
}

type FilesBrowseFlags struct {
    Path string `json:"path"`

    IncludePermissions bool `json:"includePermissions,omitempty"`
    IncludeTimestamps  bool `json:"includeTimestamps,omitempty"`
    IncludeSizes       bool `json:"includeSizes,omitempty"`
    IncludeUnixInfo    bool `json:"includeUnixInfo,omitempty"`
    IncludeMetadata    bool `json:"includeMetadata,omitempty"`

    FilterByFileExtension string `json:"filterByFileExtension,omitempty"`
}

type BrowseFilesRequest struct {
    Path          string
    Drive         orchestrators.Drive
    SortBy        string
    SortDirection orchestrators.SortDirection
    Next          string
    ItemsPerPage  int
    Flags         FilesBrowseFlags
}

type CreateFolderRequest struct {
    Path           string
    Drive          orchestrators.Drive
    ConflictPolicy orchestrators.WriteConflictPolicy
}

func controllerFiles(mux *http.ServeMux) {
    fileContext := fmt.Sprintf("/ucloud/%v/files/", cfg.Provider.Id)
    driveContext := fmt.Sprintf("/ucloud/%v/files/collections/", cfg.Provider.Id)

    type filesProviderBrowseRequest struct {
        ResolvedCollection orchestrators.Drive
        Browse             orchestrators.ResourceBrowseRequest[FilesBrowseFlags]
    }

    mux.HandleFunc(fileContext+"browse", HttpUpdateHandler[filesProviderBrowseRequest](
        0,
        func(w http.ResponseWriter, r *http.Request, request filesProviderBrowseRequest) {
            resp, err := Files.BrowseFiles(
                BrowseFilesRequest{
                    Path:          request.Browse.Flags.Path,
                    Drive:         request.ResolvedCollection,
                    SortBy:        request.Browse.SortBy,
                    SortDirection: request.Browse.SortDirection,
                    Next:          request.Browse.Next,
                    ItemsPerPage:  request.Browse.ItemsPerPage,
                    Flags:         request.Browse.Flags,
                },
            )

            sendResponseOrError(w, resp, err)
        },
    ))

    type createFolderRequestItem struct {
        ResolvedCollection orchestrators.Drive
        Id                 string
        ConflictPolicy     orchestrators.WriteConflictPolicy
    }
    type createFolderRequest fnd.BulkRequest[createFolderRequestItem]

    mux.HandleFunc(fileContext+"folder", HttpUpdateHandler[createFolderRequest](
        0,
        func(w http.ResponseWriter, r *http.Request, request createFolderRequest) {
            var err error = nil
            for _, item := range request.Items {
                err = Files.CreateFolder(CreateFolderRequest{
                    Path:           item.Id,
                    Drive:          item.ResolvedCollection,
                    ConflictPolicy: item.ConflictPolicy,
                })

                if err != nil {
                    break
                }
            }

            sendStaticJsonOrError(w, `{"responses":[]}`, err)
        },
    ))

    type retrieveProductsRequest struct{}
    mux.HandleFunc(
        driveContext+"retrieveProducts",
        HttpRetrieveHandler(0, func(w http.ResponseWriter, r *http.Request, request retrieveProductsRequest) {
            var support orchestrators.FSSupport

            support.Product.Provider = cfg.Provider.Id
            support.Product.Id = "storage"
            support.Product.Category = "storage"

            support.Stats.SizeInBytes = true
            support.Stats.ModifiedAt = true
            support.Stats.CreatedAt = true
            support.Stats.AccessedAt = true
            support.Stats.UnixPermissions = true
            support.Stats.UnixOwner = true
            support.Stats.UnixGroup = true

            support.Collection.AclModifiable = false
            support.Collection.UsersCanCreate = false
            support.Collection.UsersCanDelete = false
            support.Collection.UsersCanRename = false

            support.Files.AclModifiable = false
            support.Files.TrashSupport = true
            support.Files.IsReadOnly = false
            support.Files.SearchSupported = false
            support.Files.StreamingSearchSupported = true
            support.Files.SharesSupported = false

            sendResponseOrError(
                w,
                fnd.BulkResponse[orchestrators.FSSupport]{
                    Responses: []orchestrators.FSSupport{support},
                },
                nil,
            )
        }),
    )

    mux.HandleFunc(fileContext+"retrieve", func(w http.ResponseWriter, r *http.Request) {})
    mux.HandleFunc(fileContext+"move", func(w http.ResponseWriter, r *http.Request) {})
    mux.HandleFunc(fileContext+"copy", func(w http.ResponseWriter, r *http.Request) {})
    mux.HandleFunc(fileContext+"trash", func(w http.ResponseWriter, r *http.Request) {})
    mux.HandleFunc(fileContext+"emptyTrash", func(w http.ResponseWriter, r *http.Request) {})
    mux.HandleFunc(fileContext+"upload", func(w http.ResponseWriter, r *http.Request) {})
    mux.HandleFunc(fileContext+"download", func(w http.ResponseWriter, r *http.Request) {})
    mux.HandleFunc(fileContext+"streamingSearch", func(w http.ResponseWriter, r *http.Request) {})
}
