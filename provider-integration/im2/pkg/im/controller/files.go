package controller

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"slices"
	"strings"
	"time"

	"ucloud.dk/pkg/apm"
	db "ucloud.dk/pkg/database"
	"ucloud.dk/pkg/im/controller/upload"

	ws "github.com/gorilla/websocket"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
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
	BrowseFiles                 func(request BrowseFilesRequest) (fnd.PageV2[orc.ProviderFile], error)
	RetrieveFile                func(request RetrieveFileRequest) (orc.ProviderFile, error)
	CreateFolder                func(request CreateFolderRequest) error
	Move                        func(request MoveFileRequest) error
	Copy                        func(request CopyFileRequest) error
	MoveToTrash                 func(request MoveToTrashRequest) error
	EmptyTrash                  func(request EmptyTrashRequest) error
	CreateDownloadSession       func(request DownloadSession) error
	Download                    func(request DownloadSession) (io.ReadSeekCloser, int64, error)
	CreateUploadSession         func(request CreateUploadRequest) (string, error)
	RetrieveProducts            func() []orc.FSSupport
	TransferSourceInitiate      func(request FilesTransferRequestInitiateSource) ([]string, error)
	TransferDestinationInitiate func(request FilesTransferRequestInitiateDestination) (FilesTransferResponse, error)
	TransferSourceBegin         func(request FilesTransferRequestStart, session TransferSession) error
	Search                      func(ctx context.Context, query, folder string, flags FileFlags, outputChannel chan orc.ProviderFile)
	Uploader                    upload.ServerFileSystem

	CreateDrive func(drive orc.Drive) error
	DeleteDrive func(drive orc.Drive) error
	RenameDrive func(drive orc.Drive) error

	CreateShare func(share orc.Share) (driveId string, err error)
}

type FilesBrowseFlags struct {
	Path string `json:"path"`
	FileFlags
}

type FileFlags struct {
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
	UCloudUsername string
	OldDrive       orc.Drive
	NewDrive       orc.Drive
	OldPath        string
	NewPath        string
	Policy         orc.WriteConflictPolicy
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

type FilesTransferRequestType string

const (
	FilesTransferRequestTypeInitiateSource      FilesTransferRequestType = "InitiateSource"
	FilesTransferRequestTypeInitiateDestination FilesTransferRequestType = "InitiateDestination"
	FilesTransferRequestTypeStart               FilesTransferRequestType = "Start"
)

type FilesTransferRequest struct {
	Type    FilesTransferRequestType
	Payload any
}

var (
	metricFilesUploadSessionsCurrent = promauto.NewGauge(prometheus.GaugeOpts{
		Namespace: "ucloud_im",
		Subsystem: "files",
		Name:      "upload_sessions_current",
		Help:      "Number of upload sessions that are currently open",
	})
	metricFilesUploadSessionsTotal = promauto.NewCounter(prometheus.CounterOpts{
		Namespace: "ucloud_im",
		Subsystem: "files",
		Name:      "upload_sessions_total",
		Help:      "Total number of upload sessions that has been opened",
	})
	metricFilesDownloadSessionsCurrent = promauto.NewGauge(prometheus.GaugeOpts{
		Namespace: "ucloud_im",
		Subsystem: "files",
		Name:      "download_sessions_current",
		Help:      "The number of download sessions that are currently open",
	})
	metricFilesDownloadSessionsTotal = promauto.NewCounter(prometheus.CounterOpts{
		Namespace: "ucloud_im",
		Subsystem: "files",
		Name:      "download_sessions_total",
		Help:      "Total number of download sessions that has been opened",
	})
)

func (t *FilesTransferRequest) InitiateSource() *FilesTransferRequestInitiateSource {
	if t.Type == FilesTransferRequestTypeInitiateSource {
		return t.Payload.(*FilesTransferRequestInitiateSource)
	}
	return nil
}

func (t *FilesTransferRequest) InitiateDestination() *FilesTransferRequestInitiateDestination {
	if t.Type == FilesTransferRequestTypeInitiateDestination {
		return t.Payload.(*FilesTransferRequestInitiateDestination)
	}
	return nil
}

func (t *FilesTransferRequest) Start() *FilesTransferRequestStart {
	if t.Type == FilesTransferRequestTypeStart {
		return t.Payload.(*FilesTransferRequestStart)
	}
	return nil
}

type FilesTransferRequestInitiateSource struct {
	SourcePath          string
	DestinationProvider string
}

type FilesTransferRequestInitiateDestination struct {
	DestinationPath    string
	SourceProvider     string
	SupportedProtocols []string
}

type FilesTransferRequestStart struct {
	Session            string
	SelectedProtocol   string
	ProtocolParameters json.RawMessage
}

type FilesTransferResponseType string

const (
	FilesTransferResponseTypeInitiateSource      FilesTransferResponseType = "InitiateSource"
	FilesTransferResponseTypeInitiateDestination FilesTransferResponseType = "InitiateDestination"
	FilesTransferResponseTypeStart               FilesTransferResponseType = "Start"
)

type FilesTransferResponse struct {
	Type               FilesTransferResponseType `json:"type"`
	Session            string                    `json:"session,omitempty"`
	SupportedProtocols []string                  `json:"supportedProtocols,omitempty"`
	SelectedProtocol   string                    `json:"selectedProtocol,omitempty"`
	ProtocolParameters json.RawMessage           `json:"protocolParameters,omitempty"`
}

func FilesTransferResponseInitiateSource(session string, supportedProtocols []string) FilesTransferResponse {
	return FilesTransferResponse{
		Type:               FilesTransferResponseTypeInitiateSource,
		Session:            session,
		SupportedProtocols: supportedProtocols,
	}
}

func FilesTransferResponseInitiateDestination(selectedProtocol string, protocolParameters any) FilesTransferResponse {
	parameters, _ := json.Marshal(protocolParameters)
	return FilesTransferResponse{
		Type:               FilesTransferResponseTypeInitiateDestination,
		SelectedProtocol:   selectedProtocol,
		ProtocolParameters: parameters,
	}
}

func FilesTransferResponseStart() FilesTransferResponse {
	return FilesTransferResponse{
		Type: FilesTransferResponseTypeStart,
	}
}

type TransferBuiltInParameters struct {
	Endpoint string
}

func controllerFiles(mux *http.ServeMux) {
	fileContext := fmt.Sprintf("/ucloud/%v/files/", cfg.Provider.Id)
	uploadContext := fmt.Sprintf("/ucloud/%v/upload", cfg.Provider.Id)
	driveContext := fmt.Sprintf("/ucloud/%v/files/collections/", cfg.Provider.Id)
	shareContext := fmt.Sprintf("/ucloud/%v/shares/", cfg.Provider.Id)

	type filesProviderBrowseRequest struct {
		ResolvedCollection orc.Drive
		Browse             orc.ResourceBrowseRequest[FilesBrowseFlags]
	}

	wsUpgrader := ws.Upgrader{
		ReadBufferSize:  1024 * 4,
		WriteBufferSize: 1024 * 4,
	}
	wsUpgrader.CheckOrigin = func(r *http.Request) bool { return true }

	if RunsUserCode() {
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
					owner := uint32(os.Getuid())

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
			uid := uint32(os.Getuid())
			metricFilesDownloadSessionsCurrent.Inc()
			metricFilesDownloadSessionsTotal.Inc()
			defer metricFilesDownloadSessionsCurrent.Dec()

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
							UCloudUsername: GetUCloudUsername(r),
							OldDrive:       item.OldDrive,
							NewDrive:       item.NewDrive,
							OldPath:        item.OldPath,
							NewPath:        item.NewPath,
							Policy:         item.ConflictPolicy,
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
					resp := fnd.BulkResponse[createUploadResponse]{}
					for _, item := range request.Items {
						TrackDrive(&item.Drive)

						sessionData, err := Files.CreateUploadSession(item)
						if err != nil {
							sendError(w, err)
							return
						}

						session, uploadPath := CreateFolderUpload(item.Path, item.ConflictPolicy, sessionData)

						resp.Responses = append(
							resp.Responses,
							createUploadResponse{
								Endpoint: uploadPath,
								Protocol: orc.UploadProtocolWebSocketV2,
								Token:    session.Id,
							},
						)

					}

					sendResponseOrError(w, resp, nil)
				},
			),
		)

		mux.HandleFunc(
			uploadContext,
			func(w http.ResponseWriter, r *http.Request) {
				if ok := checkEnvoySecret(w, r); !ok {
					return
				}
				conn, err := wsUpgrader.Upgrade(w, r, nil)
				defer util.SilentCloseIfOk(conn, err)
				token := r.URL.Query().Get("token")

				metricFilesUploadSessionsCurrent.Inc()
				metricFilesUploadSessionsTotal.Inc()
				defer metricFilesUploadSessionsCurrent.Dec()

				if err != nil {
					log.Debug("Expected a websocket connection, but couldn't upgrade: %v", err)
					return
				}

				session, ok := retrieveUploadSession(token)

				if !ok {
					w.WriteHeader(http.StatusNotFound)
					return
				}

				if upload.ProcessServer(conn, Files.Uploader, session) {
					invalidateUploadSession(token)
				}
			},
		)

		type transferRequest struct {
			Type                  FilesTransferRequestType `json:"type"`
			SourcePath            string                   `json:"sourcePath"`
			DestinationPath       string                   `json:"destinationPath"`
			SourceProvider        string                   `json:"sourceProvider"`
			DestinationProvider   string                   `json:"destinationProvider"`
			SupportedProtocols    []string                 `json:"supportedProtocols"`
			Session               string                   `json:"session"`
			SelectedProtocol      string                   `json:"selectedProtocol"`
			ProtocolParameters    json.RawMessage          `json:"protocolParameters"`
			SourceCollection      *orc.Drive               `json:"sourceCollection"`
			DestinationCollection *orc.Drive               `json:"destinationCollection"`
		}

		mux.HandleFunc(
			fileContext+"transfer",
			HttpUpdateHandler(0, func(w http.ResponseWriter, r *http.Request, request transferRequest) {
				if Files.TransferSourceBegin == nil || Files.TransferDestinationInitiate == nil || Files.TransferSourceInitiate == nil {
					sendError(w, &util.HttpError{
						StatusCode: http.StatusBadRequest,
						Why:        "Bad request",
					})
					return
				}

				switch request.Type {
				case FilesTransferRequestTypeInitiateSource:
					TrackDrive(request.SourceCollection)
					payload := FilesTransferRequestInitiateSource{
						SourcePath:          request.SourcePath,
						DestinationProvider: request.DestinationPath,
					}

					protocols, err := Files.TransferSourceInitiate(payload)
					if err != nil {
						sendError(w, err)
						return
					}

					id, err := createTransferSessionCall.Invoke(payload)
					resp := FilesTransferResponseInitiateSource(id.Id, protocols)
					sendResponseOrError(w, resp, err)

				case FilesTransferRequestTypeInitiateDestination:
					TrackDrive(request.DestinationCollection)
					payload := FilesTransferRequestInitiateDestination{
						DestinationPath:    request.DestinationPath,
						SourceProvider:     request.SourceProvider,
						SupportedProtocols: request.SupportedProtocols,
					}

					resp, err := Files.TransferDestinationInitiate(payload)
					sendResponseOrError(w, resp, err)

				case FilesTransferRequestTypeStart:
					payload := FilesTransferRequestStart{
						Session:            request.Session,
						SelectedProtocol:   request.SelectedProtocol,
						ProtocolParameters: request.ProtocolParameters,
					}

					_, err := selectTransferProtocolCall.Invoke(SelectTransferProtocolRequest{
						Id:                 request.Session,
						SelectedProtocol:   request.SelectedProtocol,
						ProtocolParameters: request.ProtocolParameters,
					})

					if err != nil {
						sendError(w, err)
						return
					}

					session, ok := RetrieveTransferSession(request.Session)
					if !ok {
						sendError(w, &util.HttpError{
							StatusCode: http.StatusNotFound,
							Why:        "unknown session",
						})
						return
					}

					err = Files.TransferSourceBegin(payload, session)
					sendResponseOrError(w, FilesTransferResponseStart(), err)

				default:
					sendError(w, &util.HttpError{
						StatusCode: http.StatusBadRequest,
						Why:        "Bad request",
					})
					return
				}
			}),
		)

		searchCall := fmt.Sprintf("files.provider.%v.streamingSearch", cfg.Provider.Id)
		mux.HandleFunc(
			fmt.Sprintf("/ucloud/%v/files", cfg.Provider.Id),
			func(writer http.ResponseWriter, request *http.Request) {
				// Initialize websocket session
				// -----------------------------------------------------------------------------------------------------
				searchFn := Files.Search
				if searchFn == nil {
					sendError(writer, &util.HttpError{
						StatusCode: http.StatusNotFound,
					})
					return
				}

				conn, err := HttpUpgradeToWebSocketAuthenticated(writer, request)
				defer util.SilentCloseIfOk(conn, err)
				if err != nil {
					log.Debug("Expected a websocket connection, but couldn't upgrade: %v", err)
					return
				}

				// Initialize reader goroutine
				// -----------------------------------------------------------------------------------------------------
				ctx, cancel := context.WithCancel(context.Background())
				socketMessages := make(chan []byte)

				go func() {
					for util.IsAlive {
						_, message, err := conn.ReadMessage()
						if err != nil {
							break
						}

						socketMessages <- message
					}

					socketMessages <- nil
				}()

				// Read initial message and parse it
				// -----------------------------------------------------------------------------------------------------
				data := <-socketMessages
				if data == nil {
					cancel()
					return
				}

				requestMessage := WebSocketRequest{}
				err = json.Unmarshal(data, &requestMessage)
				if err != nil {
					log.Info("Failed to unmarshal websocket message: %v", err)
					cancel()
					return
				}

				if requestMessage.Call != searchCall {
					log.Info("Unexpected call on stream: %v", requestMessage.Call)
					cancel()
					return
				}

				type req struct {
					Query         string                  `json:"query"`
					Owner         orc.ResourceOwner       `json:"owner"`
					Flags         FileFlags               `json:"flags"`
					Category      apm.ProductCategoryIdV2 `json:"category"`
					CurrentFolder util.Option[string]     `json:"currentFolder"`
				}
				wsRequest := req{}
				err = json.Unmarshal(requestMessage.Payload, &wsRequest)
				if err != nil {
					log.Info("Failed to unmarshal follow message: %v", err)
					cancel()
					return
				}

				if !wsRequest.CurrentFolder.Present {
					cancel()
					return
				}

				// Begin processing of search results
				// -----------------------------------------------------------------------------------------------------

				outputChannel := make(chan orc.ProviderFile)
				go searchFn(ctx, wsRequest.Query, wsRequest.CurrentFolder.Value, wsRequest.Flags, outputChannel)

				type result struct {
					Type  string             `json:"type"`
					Batch []orc.ProviderFile `json:"batch"`
				}

				var outputBuffer []orc.ProviderFile
				ticker := time.NewTicker(100 * time.Millisecond)
				flushBuffer := func() bool {
					if len(outputBuffer) == 0 {
						return true
					}

					output := result{
						Type:  "result",
						Batch: outputBuffer,
					}
					outputJson, _ := json.Marshal(output)

					msg := WebSocketResponseMessage{
						Type:     "message",
						Payload:  outputJson,
						StreamId: requestMessage.StreamId,
					}

					msgBytes, _ := json.Marshal(msg)
					_ = conn.SetWriteDeadline(time.Now().Add(2 * time.Second))
					err = conn.WriteMessage(ws.TextMessage, msgBytes)

					outputBuffer = nil
					return err == nil
				}

			outer:
				for util.IsAlive {
					select {
					case <-ctx.Done():
						break outer

					case <-ticker.C:
						if !flushBuffer() {
							break outer
						}

					case m := <-socketMessages:
						if m == nil {
							break outer
						}

					case output, ok := <-outputChannel:
						if !ok {
							break outer
						}

						outputBuffer = append(outputBuffer, output)
					}
				}

				_ = flushBuffer()

				cancel()
			},
		)

		// Drives
		driveCreateHandler := HttpUpdateHandler[fnd.BulkRequest[orc.Drive]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[orc.Drive]) {
				resp := fnd.BulkResponse[util.Option[fnd.FindByStringId]]{}
				for _, item := range request.Items {
					TrackDrive(&item)

					fn := Files.CreateDrive
					if fn == nil {
						sendResponseOrError(w, nil, util.ServerHttpError("Drive creation is not supported"))
						return
					} else {
						err := fn(item)
						if err != nil {
							sendResponseOrError(w, nil, err)
							return
						}
					}

					resp.Responses = append(
						resp.Responses,
						util.Option[fnd.FindByStringId]{},
					)
				}

				sendResponseOrError(w, resp, nil)
			},
		)

		driveDeleteHandler := HttpUpdateHandler[fnd.BulkRequest[orc.Drive]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[orc.Drive]) {
				resp := fnd.BulkResponse[util.Option[util.Empty]]{}
				for _, item := range request.Items {
					fn := Files.DeleteDrive
					if fn == nil {
						sendResponseOrError(w, nil, util.ServerHttpError("Drive deletion is not supported"))
						return
					} else {
						err := fn(item)
						if err != nil {
							resp.Responses = append(
								resp.Responses,
								util.Option[util.Empty]{
									Present: false,
								},
							)
						} else {
							resp.Responses = append(
								resp.Responses,
								util.Option[util.Empty]{
									Present: true,
								},
							)

							RemoveTrackedDrive(item.Id)
						}
					}
				}

				sendResponseOrError(w, resp, nil)
			},
		)

		driveEndpoint, _ := strings.CutSuffix(driveContext, "/")
		mux.HandleFunc(driveEndpoint, func(w http.ResponseWriter, r *http.Request) {
			if r.Method == http.MethodDelete {
				driveDeleteHandler(w, r)
			} else if r.Method == http.MethodPost {
				driveCreateHandler(w, r)
			} else {
				sendResponseOrError(w, nil, util.ServerHttpError("Unknown endpoint"))
			}
		})

		type driveRenameRequestItem struct {
			Id       string `json:"id"`
			NewTitle string `json:"newTitle"`
		}

		mux.HandleFunc(driveContext+"rename",
			HttpUpdateHandler[fnd.BulkRequest[driveRenameRequestItem]](
				0,
				func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[driveRenameRequestItem]) {
					resp := fnd.BulkResponse[util.Option[util.Empty]]{}
					for _, item := range request.Items {
						retrievedDrive, ok := RetrieveDrive(item.Id)
						if !ok {
							sendResponseOrError(w, nil, util.ServerHttpError("Unknown drive"))
							return
						}
						driveCopy := *retrievedDrive
						oldTitle := driveCopy.Specification.Title
						driveCopy.Specification.Title = item.NewTitle
						TrackDrive(&driveCopy)

						fn := Files.RenameDrive
						if fn != nil {
							err := fn(driveCopy)
							if err != nil {
								sendResponseOrError(w, nil, err)
								return
							} else {
								driveCopy.Specification.Title = oldTitle
								TrackDrive(&driveCopy)
							}
						}

						resp.Responses = append(
							resp.Responses,
							util.Option[util.Empty]{
								Present: true,
							},
						)
					}

					sendResponseOrError(w, resp, nil)
				},
			),
		)

		type driveUpdateAclRequest struct {
			Resource orc.Drive              `json:"resource"`
			Added    []orc.ResourceAclEntry `json:"added"`
			Deleted  []orc.AclEntity        `json:"deleted"`
		}

		mux.HandleFunc(driveContext+"updateAcl",
			HttpUpdateHandler[fnd.BulkRequest[driveUpdateAclRequest]](
				0,
				func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[driveUpdateAclRequest]) {
					resp := fnd.BulkResponse[util.Option[util.Empty]]{}
					for _, item := range request.Items {
						// TODO The Core needs to stop doing this. Why not just send the new ACL also?

						drive := item.Resource
						for _, toDelete := range item.Deleted {
							for i, entry := range drive.Permissions.Others {
								if entry.Entity == toDelete {
									slices.Delete(drive.Permissions.Others, i, i+1)
									break
								}
							}
						}
						for _, toAdd := range item.Added {
							found := false

							for i := 0; i < len(drive.Permissions.Others); i++ {
								entry := &drive.Permissions.Others[i]
								if entry.Entity == toAdd.Entity {
									for _, perm := range toAdd.Permissions {
										entry.Permissions = orc.PermissionsAdd(entry.Permissions, perm)
									}
									found = true
									break
								}
							}

							if !found {
								drive.Permissions.Others = append(drive.Permissions.Others, orc.ResourceAclEntry{
									Entity:      toAdd.Entity,
									Permissions: toAdd.Permissions,
								})
							}
						}

						TrackDrive(&drive)

						resp.Responses = append(
							resp.Responses,
							util.Option[util.Empty]{
								Present: true,
							},
						)
					}

					sendResponseOrError(w, resp, nil)
				},
			),
		)

		shareEndpoint, _ := strings.CutSuffix(shareContext, "/")
		shareCreateHandler := HttpUpdateHandler[fnd.BulkRequest[orc.Share]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[orc.Share]) {
				fn := Files.CreateShare
				if fn == nil {
					sendResponseOrError(w, nil, util.ServerHttpError("shares not supported"))
					return
				}

				var resp []util.Option[fnd.FindByStringId]
				var updates []orc.ResourceUpdateAndId[orc.ShareUpdate]

				for _, share := range request.Items {
					driveId, err := fn(share)
					if err != nil {
						sendResponseOrError(w, r, err)
						return
					} else {
						updates = append(updates, orc.ResourceUpdateAndId[orc.ShareUpdate]{
							Id: share.Id,
							Update: orc.ShareUpdate{
								NewState:         orc.ShareStatePending,
								ShareAvailableAt: util.OptValue(fmt.Sprintf("/%s", driveId)),
								Timestamp:        fnd.Timestamp(time.Now()),
							},
						})
						resp = append(resp, util.Option[fnd.FindByStringId]{})
					}
				}

				err := orc.UpdateShares(fnd.BulkRequest[orc.ResourceUpdateAndId[orc.ShareUpdate]]{
					Items: updates,
				})

				if err != nil {
					log.Warn("Failed to update shares: %v", err)
					sendResponseOrError(w, r, util.ServerHttpError("Failed to update shares"))
					return
				}

				sendResponseOrError(w, fnd.BulkResponse[util.Option[fnd.FindByStringId]]{Responses: resp}, nil)
			},
		)

		shareDeleteHandler := HttpUpdateHandler[fnd.BulkRequest[orc.Share]](
			0,
			func(w http.ResponseWriter, r *http.Request, request fnd.BulkRequest[orc.Share]) {
				// Do nothing
			},
		)

		mux.HandleFunc(shareEndpoint, func(w http.ResponseWriter, r *http.Request) {
			if r.Method == http.MethodPost {
				shareCreateHandler(w, r)
			} else if r.Method == http.MethodDelete {
				shareDeleteHandler(w, r)
			} else {
				sendResponseOrError(w, nil, util.ServerHttpError("unknown endpoint"))
			}
		})
	}

	if RunsServerCode() {
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

		mux.HandleFunc(
			shareContext+"retrieveProducts",
			HttpRetrieveHandler(0, func(w http.ResponseWriter, r *http.Request, request util.Empty) {
				support := Files.RetrieveProducts()
				var result []orc.ShareSupport
				for _, item := range support {
					if item.Files.SharesSupported {
						result = append(result, orc.ShareSupport{
							Product: item.Product,
							Type:    orc.ShareTypeManaged,
						})
					}
				}
				sendResponseOrError(w, fnd.BulkResponse[orc.ShareSupport]{Responses: result}, nil)
			}),
		)

		createTransferSessionCall.Handler(func(r *ipc.Request[FilesTransferRequestInitiateSource]) ipc.Response[fnd.FindByStringId] {
			token := util.RandomToken(16)

			db.NewTx0(func(tx *db.Transaction) {
				db.Exec(
					tx,
					`
						insert into file_transfers(id, owner_uid, ucloud_source, destination_provider,
							selected_protocol, protocol_parameters) 
						values (:token, :uid, :source, :dest, null, null)
				    `,
					db.Params{
						"token":  token,
						"uid":    r.Uid,
						"source": r.Payload.SourcePath,
						"dest":   r.Payload.DestinationProvider,
					},
				)
			})

			return ipc.Response[fnd.FindByStringId]{
				StatusCode: http.StatusOK,
				Payload:    fnd.FindByStringId{Id: token},
			}
		})

		selectTransferProtocolCall.Handler(func(r *ipc.Request[SelectTransferProtocolRequest]) ipc.Response[util.Empty] {
			db.NewTx0(func(tx *db.Transaction) {
				db.Exec(
					tx,
					`
						update file_transfers
						set
							selected_protocol = :protocol,
							protocol_parameters = cast(:parameters as jsonb)
						where
							id = :id
							and owner_uid = :uid
				    `,
					db.Params{
						"id":         r.Payload.Id,
						"protocol":   r.Payload.SelectedProtocol,
						"parameters": string(r.Payload.ProtocolParameters),
						"uid":        r.Uid,
					},
				)
			})

			return ipc.Response[util.Empty]{
				StatusCode: http.StatusOK,
			}
		})

		retrieveTransferSession.Handler(func(r *ipc.Request[fnd.FindByStringId]) ipc.Response[TransferSession] {
			session, ok := db.NewTx2(func(tx *db.Transaction) (TransferSession, bool) {
				row, ok := db.Get[struct {
					UCloudSource        string
					DestinationProvider string
					SelectedProtocol    string
					ProtocolParameters  string
				}](
					tx,
					`
						select
							ucloud_source,
							destination_provider,
							selected_protocol,
							protocol_parameters
						from
							file_transfers
						where
							owner_uid = :uid
							and id = :id
				    `,
					db.Params{
						"uid": r.Uid,
						"id":  r.Payload.Id,
					},
				)

				if !ok {
					return TransferSession{}, false
				} else {
					return TransferSession{
						Id: r.Payload.Id,
						FilesTransferRequestInitiateSource: FilesTransferRequestInitiateSource{
							SourcePath:          row.UCloudSource,
							DestinationProvider: row.DestinationProvider,
						},
						SelectedProtocol:   row.SelectedProtocol,
						ProtocolParameters: []byte(row.ProtocolParameters),
					}, true
				}
			})

			log.Info("retrieve transfer session %v %v %v", r.Uid, r.Payload.Id, ok)

			if !ok {
				return ipc.Response[TransferSession]{
					StatusCode: http.StatusNotFound,
				}
			}

			return ipc.Response[TransferSession]{
				StatusCode: http.StatusOK,
				Payload:    session,
			}
		})

		createUploadSessionCall.Handler(func(r *ipc.Request[upload.ServerSession]) ipc.Response[util.Empty] {
			db.NewTx0(func(tx *db.Transaction) {
				db.Exec(
					tx,
					`
						delete from upload_sessions
						where created_at < now() - interval '7 days'
				    `,
					db.Params{},
				)

				db.Exec(
					tx,
					`
						insert into upload_sessions(session, owner_uid, conflict_policy, path, user_data)
						values (:session, :owner_uid, :conflict_policy, :path, :user_data)
						on conflict do nothing
				    `,
					db.Params{
						"session":         r.Payload.Id,
						"owner_uid":       r.Uid,
						"conflict_policy": r.Payload.ConflictPolicy,
						"path":            r.Payload.Path,
						"user_data":       r.Payload.UserData,
					},
				)
			})

			return ipc.Response[util.Empty]{
				StatusCode: http.StatusOK,
			}
		})

		retrieveUploadSessionCall.Handler(func(r *ipc.Request[fnd.FindByStringId]) ipc.Response[upload.ServerSession] {
			res, ok := db.NewTx2(func(tx *db.Transaction) (upload.ServerSession, bool) {
				row, ok := db.Get[struct {
					ConflictPolicy string
					Path           string
					UserData       string
				}](
					tx,
					`
						select conflict_policy, path, user_data
						from upload_sessions
						where
							owner_uid = :uid
							and session = :session
				    `,
					db.Params{
						"uid":     r.Uid,
						"session": r.Payload.Id,
					},
				)

				if !ok {
					return upload.ServerSession{}, false
				} else {
					return upload.ServerSession{
						Id:             r.Payload.Id,
						ConflictPolicy: orc.WriteConflictPolicy(row.ConflictPolicy),
						Path:           row.Path,
						UserData:       row.UserData,
					}, true
				}
			})

			if !ok {
				return ipc.Response[upload.ServerSession]{
					StatusCode: http.StatusNotFound,
				}
			}

			return ipc.Response[upload.ServerSession]{
				StatusCode: http.StatusOK,
				Payload:    res,
			}
		})
	}

	invalidateUploadSessionCall.Handler(func(r *ipc.Request[fnd.FindByStringId]) ipc.Response[util.Empty] {
		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					delete from upload_sessions
					where
						owner_uid = :uid
						and session = :session
			    `,
				db.Params{
					"uid":     r.Uid,
					"session": r.Payload.Id,
				},
			)
		})

		return ipc.Response[util.Empty]{
			StatusCode: http.StatusOK,
		}
	})
}

func CreateFolderUpload(path string, conflictPolicy orc.WriteConflictPolicy, sessionData string) (upload.ServerSession, string) {
	session := upload.ServerSession{
		Id:             util.RandomToken(32),
		ConflictPolicy: conflictPolicy,
		Path:           path,
		UserData:       sessionData,
	}

	uploadPath := generateUploadPath(cfg.Provider, cfg.Provider.Id, orc.UploadProtocolWebSocketV2, session.Id, UCloudUsername)
	createUploadSession(session)
	return session, uploadPath
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
		case orc.UploadProtocolWebSocketV2:
			hostPath = config.Hosts.SelfPublic.ToWebSocketUrl()
		}
	}

	if LaunchUserInstances {
		return fmt.Sprintf("%s/ucloud/%s/upload?token=%s&usernameHint=%s", hostPath, providerId, token, base64.URLEncoding.EncodeToString([]byte(username)))
	} else {
		return fmt.Sprintf("%s/ucloud/%s/upload?token=%s", hostPath, providerId, token)
	}
}

func createUploadSession(session upload.ServerSession) {
	_, _ = createUploadSessionCall.Invoke(session)
}

func retrieveUploadSession(id string) (upload.ServerSession, bool) {
	session, err := retrieveUploadSessionCall.Invoke(fnd.FindByStringId{id})
	return session, err == nil
}

func invalidateUploadSession(id string) {
	_, _ = invalidateUploadSessionCall.Invoke(fnd.FindByStringId{id})
}

var downloadSessions = util.NewCache[string, DownloadSession](48 * time.Hour)

type TransferSession struct {
	Id string
	FilesTransferRequestInitiateSource

	SelectedProtocol   string
	ProtocolParameters json.RawMessage
}

type SelectTransferProtocolRequest struct {
	Id                 string
	SelectedProtocol   string
	ProtocolParameters json.RawMessage
}

var (
	createUploadSessionCall     = ipc.NewCall[upload.ServerSession, util.Empty]("ctrl.files.createUploadSession")
	retrieveUploadSessionCall   = ipc.NewCall[fnd.FindByStringId, upload.ServerSession]("ctrl.files.retrieveUploadSession")
	invalidateUploadSessionCall = ipc.NewCall[fnd.FindByStringId, util.Empty]("ctrl.files.invalidateUploadSession")

	createTransferSessionCall  = ipc.NewCall[FilesTransferRequestInitiateSource, fnd.FindByStringId]("ctrl.files.createTransferSession")
	selectTransferProtocolCall = ipc.NewCall[SelectTransferProtocolRequest, util.Empty]("ctrl.files.selectTransferProtocol")
	retrieveTransferSession    = ipc.NewCall[fnd.FindByStringId, TransferSession]("ctrl.files.retrieveTransferSession")
)

func RetrieveTransferSession(id string) (TransferSession, bool) {
	session, err := retrieveTransferSession.Invoke(fnd.FindByStringId{Id: id})
	if err != nil {
		return TransferSession{}, false
	}
	return session, true
}
