package controller

import (
	"encoding/base64"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	ws "github.com/gorilla/websocket"
	fnd "ucloud.dk/pkg/foundation"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var Files FileService

type UploadType string

const (
	UploadTypeFile   UploadType = "FILE"
	UploadTypeFolder UploadType = "FOLDER"
)

type CreateUploadRequest struct {
	Drive              orc.Drive               `json:"resolvedCollection"`
	Path               string                  `json:"id"`
	Type               UploadType              `json:"type"`
	SupportedProtocols []orc.UploadProtocol    `json:"supportedProtocols"`
	ConflictPolicy     orc.WriteConflictPolicy `json:"conflictPolicy"`
}

type FileService struct {
	BrowseFiles           func(request BrowseFilesRequest) (fnd.PageV2[orc.ProviderFile], error)
	RetrieveFile          func(request RetrieveFileRequest) (orc.ProviderFile, error)
	CreateFolder          func(request CreateFolderRequest) error
	Move                  func(request MoveFileRequest) error
	Copy                  func(request CopyFileRequest) error
	MoveToTrash           func(request MoveToTrashRequest) error
	EmptyTrash            func(request EmptyTrashRequest) error
	CreateDownloadSession func(request DownloadSession) error
	Download              func(request DownloadSession) (io.ReadSeekCloser, int64, error)
	CreateUploadSession   func(request CreateUploadRequest) ([]byte, error)
	HandleUploadWs        func(request UploadDataWs) error
	RetrieveProducts      func() []orc.FSSupport
}

type FilesBrowseFlags struct {
	Path string `json:"path"`

	IncludePermissions bool `json:"includePermissions,omitempty"`
	IncludeTimestamps  bool `json:"includeTimestamps,omitempty"`
	IncludeSizes       bool `json:"includeSizes,omitempty"`
	IncludeUnixInfo    bool `json:"includeUnixInfo,omitempty"`
	IncludeMetadata    bool `json:"includeMetadata,omitempty"`
	FilterHiddenFiles  bool `json:"filterHiddenFiles,omitempty"`

	FilterByFileExtension string `json:"filterByFileExtension,omitempty"`
}

type DownloadSession struct {
	Drive     orc.Drive
	Path      string
	OwnedBy   uint32
	SessionId string
}

type UploadSession struct {
	Id      string
	OwnedBy uint32
	Data    []byte
}

type UploadDataWs struct {
	Session UploadSession
	Socket  *ws.Conn
}

type MoveFileRequest struct {
	OldDrive orc.Drive
	NewDrive orc.Drive
	OldPath  string
	NewPath  string
	Policy   orc.WriteConflictPolicy
}

type CopyFileRequest struct {
	OldDrive orc.Drive
	NewDrive orc.Drive
	OldPath  string
	NewPath  string
	Policy   orc.WriteConflictPolicy
}

type MoveToTrashRequest struct {
	Drive orc.Drive
	Path  string
}

type EmptyTrashRequest struct {
	Path string
}

type RetrieveFileRequest struct {
	Path  string
	Drive orc.Drive
	Flags FilesBrowseFlags
}

type BrowseFilesRequest struct {
	Path          string
	Drive         orc.Drive
	SortBy        string
	SortDirection orc.SortDirection
	Next          string
	ItemsPerPage  int
	Flags         FilesBrowseFlags
}

type CreateFolderRequest struct {
	Path           string
	Drive          orc.Drive
	ConflictPolicy orc.WriteConflictPolicy
}

func controllerFiles(mux *http.ServeMux) {
	fileContext := fmt.Sprintf("/ucloud/%v/files/", cfg.Provider.Id)
	uploadContext := fmt.Sprintf("/ucloud/%v/upload", cfg.Provider.Id)
	driveContext := fmt.Sprintf("/ucloud/%v/files/collections/", cfg.Provider.Id)

	type filesProviderBrowseRequest struct {
		ResolvedCollection orc.Drive
		Browse             orc.ResourceBrowseRequest[FilesBrowseFlags]
	}

	wsUpgrader := ws.Upgrader{
		ReadBufferSize:  1024 * 4,
		WriteBufferSize: 1024 * 4,
	}
	wsUpgrader.CheckOrigin = func(r *http.Request) bool { return true }

	if cfg.Mode == cfg.ServerModeUser {
		mux.HandleFunc(fileContext+"browse", HttpUpdateHandler[filesProviderBrowseRequest](
			0,
			func(w http.ResponseWriter, r *http.Request, request filesProviderBrowseRequest) {
				TrackDrive(&request.ResolvedCollection)

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
			ResolvedCollection orc.Drive
			Id                 string
			ConflictPolicy     orc.WriteConflictPolicy
		}
		type createFolderRequest fnd.BulkRequest[createFolderRequestItem]

		mux.HandleFunc(fileContext+"folder", HttpUpdateHandler[createFolderRequest](
			0,
			func(w http.ResponseWriter, r *http.Request, request createFolderRequest) {

				var err error = nil
				for _, item := range request.Items {
					TrackDrive(&item.ResolvedCollection)

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

		type retrieveRequest struct {
			ResolvedCollection orc.Drive
			Retrieve           orc.ResourceRetrieveRequest[FilesBrowseFlags]
		}

		mux.HandleFunc(fileContext+"retrieve", HttpUpdateHandler[retrieveRequest](
			0,
			func(w http.ResponseWriter, r *http.Request, request retrieveRequest) {
				TrackDrive(&request.ResolvedCollection)

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
			ResolvedOldCollection orc.Drive
			ResolvedNewCollection orc.Drive
			OldId                 string
			NewId                 string
			ConflictPolicy        orc.WriteConflictPolicy
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
						TrackDrive(&item.ResolvedOldCollection)
						TrackDrive(&item.ResolvedNewCollection)

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
			ResolvedCollection orc.Drive
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
					owner, _ := ipc.GetConnectionUid(r)

					for _, item := range request.Items {
						TrackDrive(&item.ResolvedCollection)

						sessionId := util.RandomToken(32)

						session := DownloadSession{
							Drive:     item.ResolvedCollection,
							Path:      item.Id,
							OwnedBy:   owner,
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
			uid, _ := ipc.GetConnectionUid(r) // TODO Is this even an IPC call ???

			token := r.URL.Query().Get("token")
			session, ok := downloadSessions.GetNow(token)
			if !ok || uid != session.OwnedBy {
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

		type copyRequest struct {
			OldDrive       orc.Drive               `json:"resolvedOldCollection"`
			NewDrive       orc.Drive               `json:"resolvedNewCollection"`
			OldPath        string                  `json:"oldId"`
			NewPath        string                  `json:"newId"`
			ConflictPolicy orc.WriteConflictPolicy `json:"conflictPolicy"`
		}

		mux.HandleFunc(fileContext+"copy",
			HttpUpdateHandler[fnd.BulkRequest[copyRequest]](
				0,
				func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[copyRequest]) {
					var errors []error
					for _, item := range request.Items {
						TrackDrive(&item.OldDrive)
						TrackDrive(&item.NewDrive)

						err := Files.Copy(CopyFileRequest{
							OldDrive: item.OldDrive,
							NewDrive: item.NewDrive,
							OldPath:  item.OldPath,
							NewPath:  item.NewPath,
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

		type trashRequest struct {
			Drive orc.Drive `json:"resolvedCollection"`
			Path  string    `json:"id"`
		}

		mux.HandleFunc(fileContext+"trash",
			HttpUpdateHandler[fnd.BulkRequest[trashRequest]](
				0,
				func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[trashRequest]) {
					var errors []error
					for _, item := range request.Items {
						TrackDrive(&item.Drive)

						err := Files.MoveToTrash(MoveToTrashRequest{
							Drive: item.Drive,
							Path:  item.Path,
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

		type emptyTrashRequest struct {
			Path string `json:"id"`
		}

		mux.HandleFunc(fileContext+"emptyTrash",
			HttpUpdateHandler[fnd.BulkRequest[emptyTrashRequest]](
				0,
				func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[emptyTrashRequest]) {
					var errors []error
					for _, item := range request.Items {
						err := Files.EmptyTrash(EmptyTrashRequest{
							Path: item.Path,
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

		type createUploadResponse struct {
			Endpoint string             `json:"endpoint"`
			Protocol orc.UploadProtocol `json:"protocol"`
			Token    string             `json:"token"`
		}

		mux.HandleFunc(fileContext+"upload",
			HttpUpdateHandler[fnd.BulkRequest[CreateUploadRequest]](
				0,
				func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[CreateUploadRequest]) {
					owner, _ := ipc.GetConnectionUid(r)

					resp := fnd.BulkResponse[createUploadResponse]{}
					for _, item := range request.Items {
						TrackDrive(&item.Drive)

						sessionData, err := Files.CreateUploadSession(item)
						if err != nil {
							sendError(w, err)
							return
						}

						session := UploadSession{
							Id:      util.RandomToken(32),
							OwnedBy: owner,
							Data:    sessionData,
						}

						if item.Type == UploadTypeFolder {
							Files.CreateFolder(CreateFolderRequest{Path: item.Path, Drive: item.Drive, ConflictPolicy: item.ConflictPolicy})
						}

						uploadPath := generateUploadPath(cfg.Provider, cfg.Provider.Id, orc.UploadProtocolWebSocket, session.Id, UCloudUsername)

						resp.Responses = append(
							resp.Responses,
							createUploadResponse{
								Endpoint: uploadPath,
								Protocol: orc.UploadProtocolWebSocket,
								Token:    session.Id,
							},
						)

						uploadSessions.Set(session.Id, session)
					}

					sendResponseOrError(w, resp, nil)
				},
			),
		)

		mux.HandleFunc(fileContext+"streamingSearch", func(w http.ResponseWriter, r *http.Request) {})

		mux.HandleFunc(
			uploadContext,
			func(w http.ResponseWriter, r *http.Request) {
				uid, _ := ipc.GetConnectionUid(r) // TODO Is this even an IPC endpoint?

				conn, err := wsUpgrader.Upgrade(w, r, nil)
				defer util.SilentCloseIfOk(conn, err)
				token := r.URL.Query().Get("token")

				if err != nil {
					log.Debug("Expected a websocket connection, but couldn't upgrade: %v", err)
					return
				}

				session, ok := uploadSessions.GetNow(token)

				if !ok || uid != session.OwnedBy {
					w.WriteHeader(http.StatusNotFound)
					return
				}

				err = Files.HandleUploadWs(UploadDataWs{
					Session: session,
					Socket:  conn,
				})

				if err != nil {
					sendError(w, err)
					return
				}
			},
		)
	} else if cfg.Mode == cfg.ServerModeServer {
		type retrieveProductsRequest struct{}
		mux.HandleFunc(
			driveContext+"retrieveProducts",
			HttpRetrieveHandler(0, func(w http.ResponseWriter, r *http.Request, request retrieveProductsRequest) {
				support := Files.RetrieveProducts()
				sendResponseOrError(
					w,
					fnd.BulkResponse[orc.FSSupport]{
						Responses: support,
					},
					nil,
				)
			}),
		)
	}
}

func generateUploadPath(
	config *cfg.ProviderConfiguration,
	providerId string,
	protocol orc.UploadProtocol,
	token string,
	username string,
) string {
	var hostPath string = ""
	if config != nil {
		switch protocol {
		case orc.UploadProtocolChunked:
			hostPath = config.Hosts.SelfPublic.ToURL()
		case orc.UploadProtocolWebSocket:
			hostPath = config.Hosts.SelfPublic.ToWebSocketUrl()
		}
	}

	return fmt.Sprintf("%s/ucloud/%s/upload?token=%s&usernameHint=%s", hostPath, providerId, token, base64.URLEncoding.EncodeToString([]byte(username)))
}

var downloadSessions = util.NewCache[string, DownloadSession](48 * time.Hour)
var uploadSessions = util.NewCache[string, UploadSession](48 * time.Hour)
