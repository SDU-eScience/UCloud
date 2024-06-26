package controller

import (
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var Files FileService

type FileService struct {
	BrowseFiles  func(request BrowseFilesRequest) (fnd.PageV2[orchestrators.ProviderFile], error)
	RetrieveFile func(request RetrieveFileRequest) (orchestrators.ProviderFile, error)
	CreateFolder func(request CreateFolderRequest) error
	Move         func(request MoveFileRequest) error

	CreateDownloadSession func(request DownloadSession) error
	Download              func(request DownloadSession) (io.ReadSeekCloser, int64, error)
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

type DownloadSession struct {
	Drive     orchestrators.Drive
	Path      string
	SessionId string
}

type MoveFileRequest struct {
	OldDrive orchestrators.Drive
	NewDrive orchestrators.Drive
	OldPath  string
	NewPath  string
	Policy   orchestrators.WriteConflictPolicy
}

type RetrieveFileRequest struct {
	Path  string
	Drive orchestrators.Drive
	Flags FilesBrowseFlags
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

	type retrieveRequest struct {
		ResolvedCollection orchestrators.Drive
		Retrieve           orchestrators.ResourceRetrieveRequest[FilesBrowseFlags]
	}

	mux.HandleFunc(fileContext+"retrieve", HttpUpdateHandler[retrieveRequest](
		0,
		func(w http.ResponseWriter, r *http.Request, request retrieveRequest) {
			resp, err := Files.RetrieveFile(
				RetrieveFileRequest{
					Path:  request.Retrieve.Id,
					Drive: request.ResolvedCollection,
					Flags: request.Retrieve.Flags,
				},
			)

			sendResponseOrError(w, resp, err)
		}),
	)

	type moveRequest struct {
		ResolvedOldCollection orchestrators.Drive
		ResolvedNewCollection orchestrators.Drive
		OldId                 string
		NewId                 string
		ConflictPolicy        orchestrators.WriteConflictPolicy
	}

	type longRunningTask struct {
		Type string `json:"type"`
	}

	mux.HandleFunc(
		fileContext+"move",
		HttpUpdateHandler[fnd.BulkRequest[moveRequest]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[moveRequest]) {
				var errors []error
				for _, item := range request.Items {
					err := Files.Move(MoveFileRequest{
						OldDrive: item.ResolvedOldCollection,
						NewDrive: item.ResolvedNewCollection,
						OldPath:  item.OldId,
						NewPath:  item.NewId,
						Policy:   item.ConflictPolicy,
					})

					if err != nil {
						errors = append(errors, err)
					}
				}

				if len(errors) > 0 {
					sendError(w, errors[0])
				} else {
					var response fnd.BulkResponse[longRunningTask]
					for i := 0; i < len(request.Items); i++ {
						response.Responses = append(response.Responses, longRunningTask{Type: "complete"})
					}

					sendResponseOrError(w, response, nil)
				}
			},
		),
	)

	type downloadRequest struct {
		ResolvedCollection orchestrators.Drive
		Id                 string
	}
	type downloadResponse struct {
		Endpoint string `json:"endpoint"`
	}
	mux.HandleFunc(
		fileContext+"download",
		HttpUpdateHandler[fnd.BulkRequest[downloadRequest]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[downloadRequest]) {
				resp := fnd.BulkResponse[downloadResponse]{}
				for _, item := range request.Items {
					sessionId := util.RandomToken(32)

					session := DownloadSession{
						Drive:     item.ResolvedCollection,
						Path:      item.Id,
						SessionId: sessionId,
					}

					err := Files.CreateDownloadSession(session)
					if err != nil {
						sendError(w, err)
						return
					}

					downloadSessions.Set(sessionId, session)

					resp.Responses = append(
						resp.Responses,
						downloadResponse{
							Endpoint: fmt.Sprintf(
								"%s/ucloud/%s/download?token=%s",
								cfg.Provider.Hosts.SelfPublic.ToURL(),
								cfg.Provider.Id,
								sessionId,
							),
						},
					)
				}

				sendResponseOrError(w, resp, nil)
			},
		),
	)

	mux.HandleFunc(fmt.Sprintf("/ucloud/%v/download", cfg.Provider.Id), func(w http.ResponseWriter, r *http.Request) {
		if cfg.Mode == cfg.ServerModeServer {
			// NOTE(Dan): For some reason the Envoy endpoint was not ready yet. Try to refresh via redirect a few time
			// to see if it becomes ready.
			attempt, _ := strconv.Atoi(r.URL.Query().Get("attempt"))
			if attempt > 5 {
				w.WriteHeader(http.StatusNotFound)
			} else {
				time.Sleep(100 * time.Millisecond)
				w.Header().Set("Location", fmt.Sprintf("/ucloud/%v/download?attempt=%v", cfg.Provider.Id, attempt+1))
				w.WriteHeader(http.StatusTemporaryRedirect)
			}
			return
		}

		token := r.URL.Query().Get("token")
		session, ok := downloadSessions.GetNow(token)
		if !ok {
			w.WriteHeader(http.StatusNotFound)
			return
		}

		// TODO content range would be nice
		stream, size, err := Files.Download(session)
		if err != nil {
			sendError(w, err)
			return
		}
		defer util.SilentClose(stream)

		// I suspect that it will auto-detect content-type if we do not set it explicitly
		// w.Header().Set("Content-Type", "application/octet-stream")
		w.Header().Set("Content-Length", fmt.Sprintf("%d", size))
		w.Header().Set("Access-Control-Allow-Origin", "*")
		realFileName := util.FileName(session.Path)
		encodedFileName := "UTF-8''" + strings.ReplaceAll(url.QueryEscape(realFileName), "+", "%20")
		w.Header().Set("Content-Disposition", fmt.Sprintf("attachment; filename*=%s", encodedFileName))
		w.WriteHeader(http.StatusOK)

		_, _ = io.Copy(w, stream)
	})

	mux.HandleFunc(fileContext+"copy", func(w http.ResponseWriter, r *http.Request) {})
	mux.HandleFunc(fileContext+"trash", func(w http.ResponseWriter, r *http.Request) {})
	mux.HandleFunc(fileContext+"emptyTrash", func(w http.ResponseWriter, r *http.Request) {})
	mux.HandleFunc(fileContext+"upload", func(w http.ResponseWriter, r *http.Request) {})
	mux.HandleFunc(fileContext+"streamingSearch", func(w http.ResponseWriter, r *http.Request) {})
}

var downloadSessions = util.NewCache[string, DownloadSession](48 * time.Hour)
