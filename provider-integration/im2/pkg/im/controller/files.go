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

	"ucloud.dk/pkg/im/controller/upload"
	apm "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"

	ws "github.com/gorilla/websocket"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/ipc"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

var Files FileService

type FileService struct {
	BrowseFiles                 func(request orcapi.FilesProviderBrowseRequest) (fnd.PageV2[orcapi.ProviderFile], *util.HttpError)
	RetrieveFile                func(request orcapi.FilesProviderRetrieveRequest) (orcapi.ProviderFile, *util.HttpError)
	CreateFolder                func(request orcapi.FilesProviderCreateFolderRequest) *util.HttpError
	Move                        func(request orcapi.FilesProviderMoveOrCopyRequest) *util.HttpError
	Copy                        func(actor rpc.Actor, request orcapi.FilesProviderMoveOrCopyRequest) *util.HttpError
	MoveToTrash                 func(request orcapi.FilesProviderTrashRequest) *util.HttpError
	EmptyTrash                  func(request orcapi.FilesProviderTrashRequest) *util.HttpError
	CreateDownloadSession       func(request DownloadSession) *util.HttpError
	Download                    func(request DownloadSession) (io.ReadSeekCloser, int64, *util.HttpError)
	CreateUploadSession         func(request orcapi.FilesProviderCreateUploadRequest) (string, *util.HttpError)
	RetrieveProducts            func() []orcapi.FSSupport
	TransferSourceInitiate      func(request orcapi.FilesProviderTransferRequestInitiateSource) ([]string, *util.HttpError)
	TransferDestinationInitiate func(request orcapi.FilesProviderTransferRequestInitiateDestination) (orcapi.FilesProviderTransferResponse, *util.HttpError)
	TransferSourceBegin         func(request orcapi.FilesProviderTransferRequestStart, session TransferSession) *util.HttpError
	Search                      func(ctx context.Context, query, folder string, flags orcapi.FileFlags, outputChannel chan orcapi.ProviderFile)
	Uploader                    upload.ServerFileSystem

	CreateDrive func(drive orcapi.Drive) *util.HttpError
	DeleteDrive func(drive orcapi.Drive) *util.HttpError
	RenameDrive func(drive orcapi.Drive) *util.HttpError

	CreateShare func(share orcapi.Share) (driveId string, err *util.HttpError)
}

type DownloadSession struct {
	Drive     orcapi.Drive
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

func FilesTransferResponseInitiateSource(session string, supportedProtocols []string) orcapi.FilesProviderTransferResponse {
	return orcapi.FilesProviderTransferResponse{
		Type: orcapi.FilesProviderTransferReqTypeInitiateSource,
		FilesProviderTransferResponseInitiateSource: orcapi.FilesProviderTransferResponseInitiateSource{
			Session:            session,
			SupportedProtocols: supportedProtocols,
		},
	}
}

func FilesTransferResponseInitiateDestination(selectedProtocol string, protocolParameters any) orcapi.FilesProviderTransferResponse {
	parameters, _ := json.Marshal(protocolParameters)
	return orcapi.FilesProviderTransferResponse{
		Type: orcapi.FilesProviderTransferReqTypeInitiateDestination,
		FilesProviderTransferResponseInitiateDestination: orcapi.FilesProviderTransferResponseInitiateDestination{
			SelectedProtocol:   selectedProtocol,
			ProtocolParameters: parameters,
		},
	}
}

func FilesTransferResponseStart() orcapi.FilesProviderTransferResponse {
	return orcapi.FilesProviderTransferResponse{
		Type: orcapi.FilesProviderTransferReqTypeStart,
	}
}

type TransferBuiltInParameters struct {
	Endpoint string
}

func controllerFiles() {
	uploadContext := fmt.Sprintf("/ucloud/%v/upload", cfg.Provider.Id)

	wsUpgrader := ws.Upgrader{
		ReadBufferSize:  1024 * 4,
		WriteBufferSize: 1024 * 4,
	}
	wsUpgrader.CheckOrigin = func(r *http.Request) bool { return true }

	if RunsUserCode() {
		orcapi.FilesProviderBrowse.Handler(func(info rpc.RequestInfo, request orcapi.FilesProviderBrowseRequest) (fnd.PageV2[orcapi.ProviderFile], *util.HttpError) {
			TrackDrive(&request.ResolvedCollection)
			return Files.BrowseFiles(request)
		})

		orcapi.FilesProviderCreateFolder.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.FilesProviderCreateFolderRequest]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			var err *util.HttpError = nil
			for _, item := range request.Items {
				TrackDrive(&item.ResolvedCollection)
				err = Files.CreateFolder(item)

				if err != nil {
					break
				}
			}

			return fnd.BulkResponse[util.Empty]{}, err
		})

		orcapi.FilesProviderRetrieve.Handler(func(info rpc.RequestInfo, request orcapi.FilesProviderRetrieveRequest) (orcapi.ProviderFile, *util.HttpError) {
			TrackDrive(&request.ResolvedCollection)
			return Files.RetrieveFile(request)
		})

		orcapi.FilesProviderMove.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.FilesProviderMoveOrCopyRequest]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			var errors []*util.HttpError
			for _, item := range request.Items {
				TrackDrive(&item.ResolvedOldCollection)
				TrackDrive(&item.ResolvedNewCollection)

				err := Files.Move(item)

				if err != nil {
					errors = append(errors, err)
				}
			}

			if len(errors) > 0 {
				return fnd.BulkResponse[util.Empty]{}, errors[0]
			} else {
				return fnd.BulkResponse[util.Empty]{}, nil
			}
		})

		orcapi.FilesProviderCreateDownload.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.FilesProviderCreateDownloadRequest]) (fnd.BulkResponse[orcapi.FilesProviderCreateDownloadResponse], *util.HttpError) {
			resp := fnd.BulkResponse[orcapi.FilesProviderCreateDownloadResponse]{}
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
					return fnd.BulkResponse[orcapi.FilesProviderCreateDownloadResponse]{}, err
				}

				downloadSessions.Set(sessionId, session)

				resp.Responses = append(
					resp.Responses,
					orcapi.FilesProviderCreateDownloadResponse{
						Endpoint: fmt.Sprintf(
							"%s/ucloud/%s/download?token=%s",
							cfg.Provider.Hosts.SelfPublic.ToURL(),
							cfg.Provider.Id,
							sessionId,
						),
					},
				)
			}

			return resp, nil
		})

		rpc.DefaultServer.Mux.HandleFunc(
			fmt.Sprintf("/ucloud/%v/download", cfg.Provider.Id),
			func(w http.ResponseWriter, r *http.Request) {
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
					sendError(w, err.AsError())
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
			},
		)

		orcapi.FilesProviderCopy.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.FilesProviderMoveOrCopyRequest]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			var errors []*util.HttpError
			for _, item := range request.Items {
				TrackDrive(&item.ResolvedOldCollection)
				TrackDrive(&item.ResolvedNewCollection)

				err := Files.Copy(info.Actor, item)

				if err != nil {
					errors = append(errors, err)
				}
			}

			if len(errors) > 0 {
				return fnd.BulkResponse[util.Empty]{}, errors[0]
			} else {
				return fnd.BulkResponse[util.Empty]{}, nil
			}
		})

		orcapi.FilesProviderTrash.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.FilesProviderTrashRequest]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			var errors []*util.HttpError
			for _, item := range request.Items {
				TrackDrive(&item.ResolvedCollection)

				err := Files.MoveToTrash(item)

				if err != nil {
					errors = append(errors, err)
				}
			}

			if len(errors) > 0 {
				return fnd.BulkResponse[util.Empty]{}, errors[0]
			} else {
				return fnd.BulkResponse[util.Empty]{}, nil
			}
		})

		orcapi.FilesProviderEmptyTrash.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.FilesProviderTrashRequest]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			var errors []*util.HttpError
			for _, item := range request.Items {
				err := Files.EmptyTrash(item)

				if err != nil {
					errors = append(errors, err)
				}
			}

			if len(errors) > 0 {
				return fnd.BulkResponse[util.Empty]{}, errors[0]
			} else {
				return fnd.BulkResponse[util.Empty]{}, nil
			}
		})

		orcapi.FilesProviderCreateUpload.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.FilesProviderCreateUploadRequest]) (fnd.BulkResponse[orcapi.FilesProviderCreateUploadResponse], *util.HttpError) {
			resp := fnd.BulkResponse[orcapi.FilesProviderCreateUploadResponse]{}
			for _, item := range request.Items {
				TrackDrive(&item.ResolvedCollection)

				sessionData, err := Files.CreateUploadSession(item)
				if err != nil {
					return fnd.BulkResponse[orcapi.FilesProviderCreateUploadResponse]{}, err
				}

				session, uploadPath := CreateFolderUpload(item.Id, item.ConflictPolicy, sessionData)

				resp.Responses = append(
					resp.Responses,
					orcapi.FilesProviderCreateUploadResponse{
						Endpoint: uploadPath,
						Protocol: orcapi.UploadProtocolWebSocketV2,
						Token:    session.Id,
					},
				)
			}

			return resp, nil
		})

		rpc.DefaultServer.Mux.HandleFunc(
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

		orcapi.FilesProviderTransfer.Handler(func(info rpc.RequestInfo, request orcapi.FilesProviderTransferRequest) (orcapi.FilesProviderTransferResponse, *util.HttpError) {
			if Files.TransferSourceBegin == nil || Files.TransferDestinationInitiate == nil || Files.TransferSourceInitiate == nil {
				return orcapi.FilesProviderTransferResponse{}, util.HttpErr(http.StatusBadRequest, "bad request")
			}

			switch request.Type {
			case orcapi.FilesProviderTransferReqTypeInitiateSource:
				TrackDrive(&request.SourceDrive)
				payload := request.FilesProviderTransferRequestInitiateSource

				protocols, err := Files.TransferSourceInitiate(payload)
				if err != nil {
					return orcapi.FilesProviderTransferResponse{}, err
				}

				id, ierr := createTransferSessionCall.Invoke(payload)
				err = util.HttpErrorFromErr(ierr)
				resp := FilesTransferResponseInitiateSource(id.Id, protocols)
				return resp, err

			case orcapi.FilesProviderTransferReqTypeInitiateDestination:
				TrackDrive(&request.DestinationDrive)
				payload := request.FilesProviderTransferRequestInitiateDestination

				resp, err := Files.TransferDestinationInitiate(payload)
				return FilesTransferResponseInitiateDestination(resp.SelectedProtocol, resp.ProtocolParameters), err

			case orcapi.FilesProviderTransferReqTypeStart:
				payload := request.FilesProviderTransferRequestStart

				username := info.Actor.Username
				_, ierr := selectTransferProtocolCall.Invoke(SelectTransferProtocolRequest{
					Id:                 request.Session,
					SelectedProtocol:   request.SelectedProtocol,
					ProtocolParameters: request.ProtocolParameters,
				})

				if ierr != nil {
					return orcapi.FilesProviderTransferResponse{}, util.HttpErrorFromErr(ierr)
				}

				session, ok := RetrieveTransferSession(request.Session)
				if !ok {
					return orcapi.FilesProviderTransferResponse{}, util.HttpErr(http.StatusNotFound, "unknown session")
				}

				session.Username = username

				err := Files.TransferSourceBegin(payload, session)
				return FilesTransferResponseStart(), err

			default:
				return orcapi.FilesProviderTransferResponse{}, util.HttpErr(http.StatusBadRequest, "bad request")
			}
		})

		searchCall := fmt.Sprintf("files.provider.%v.streamingSearch", cfg.Provider.Id)
		rpc.DefaultServer.Mux.HandleFunc(
			fmt.Sprintf("/ucloud/%v/files", cfg.Provider.Id),
			func(writer http.ResponseWriter, request *http.Request) {
				// Initialize websocket session
				// -----------------------------------------------------------------------------------------------------
				searchFn := Files.Search
				if searchFn == nil {
					sendError(writer, (&util.HttpError{
						StatusCode: http.StatusNotFound,
					}).AsError())
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
					Owner         orcapi.ResourceOwner    `json:"owner"`
					Flags         orcapi.FileFlags        `json:"flags"`
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

				outputChannel := make(chan orcapi.ProviderFile)
				go searchFn(ctx, wsRequest.Query, wsRequest.CurrentFolder.Value, wsRequest.Flags, outputChannel)

				type result struct {
					Type  string                `json:"type"`
					Batch []orcapi.ProviderFile `json:"batch"`
				}

				var outputBuffer []orcapi.ProviderFile
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

		orcapi.DrivesProviderCreate.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.Drive]) (fnd.BulkResponse[fnd.FindByStringId], *util.HttpError) {
			resp := fnd.BulkResponse[fnd.FindByStringId]{}
			for _, item := range request.Items {
				TrackDrive(&item)

				fn := Files.CreateDrive
				if fn == nil {
					return fnd.BulkResponse[fnd.FindByStringId]{}, util.ServerHttpError("Drive creation is not supported")
				} else {
					err := fn(item)
					if err != nil {
						return fnd.BulkResponse[fnd.FindByStringId]{}, err
					}
				}

				resp.Responses = append(
					resp.Responses,
					fnd.FindByStringId{},
				)
			}

			return resp, nil
		})

		orcapi.DrivesProviderDelete.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.Drive]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			resp := fnd.BulkResponse[util.Empty]{}
			for _, item := range request.Items {
				fn := Files.DeleteDrive
				if fn == nil {
					return fnd.BulkResponse[util.Empty]{}, util.ServerHttpError("Drive deletion is not supported")
				} else {
					err := fn(item)
					if err != nil {
						resp.Responses = append(resp.Responses, util.Empty{})
					} else {
						resp.Responses = append(resp.Responses, util.Empty{})
						RemoveTrackedDrive(item.Id)
					}
				}
			}

			return resp, nil
		})

		orcapi.DrivesProviderRename.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.DriveRenameRequest]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			resp := fnd.BulkResponse[util.Empty]{}
			for _, item := range request.Items {
				retrievedDrive, ok := RetrieveDrive(item.Id)
				if !ok {
					return fnd.BulkResponse[util.Empty]{}, util.ServerHttpError("Unknown drive")
				}
				driveCopy := *retrievedDrive
				oldTitle := driveCopy.Specification.Title
				driveCopy.Specification.Title = item.NewTitle
				TrackDrive(&driveCopy)

				fn := Files.RenameDrive
				if fn != nil {
					err := fn(driveCopy)
					if err != nil {
						return fnd.BulkResponse[util.Empty]{}, err
					} else {
						driveCopy.Specification.Title = oldTitle
						TrackDrive(&driveCopy)
					}
				}

				resp.Responses = append(resp.Responses, util.Empty{})
			}

			return resp, nil
		})

		orcapi.DrivesProviderUpdateAcl.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.UpdatedAclWithResource[orcapi.Drive]]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			resp := fnd.BulkResponse[util.Empty]{}
			for _, item := range request.Items {
				// TODO The Core needs to stop doing this. Why not just send the new ACL also?

				drive := item.Resource
				permissions := drive.Permissions.Value
				for _, toDelete := range item.Deleted {
					for i, entry := range permissions.Others {
						if entry.Entity == toDelete {
							slices.Delete(permissions.Others, i, i+1)
							break
						}
					}
				}
				for _, toAdd := range item.Added {
					found := false

					for i := 0; i < len(permissions.Others); i++ {
						entry := &permissions.Others[i]
						if entry.Entity == toAdd.Entity {
							for _, perm := range toAdd.Permissions {
								entry.Permissions = orcapi.PermissionsAdd(entry.Permissions, perm)
							}
							found = true
							break
						}
					}

					if !found {
						permissions.Others = append(permissions.Others, orcapi.ResourceAclEntry{
							Entity:      toAdd.Entity,
							Permissions: toAdd.Permissions,
						})
					}
				}

				TrackDrive(&drive)

				resp.Responses = append(resp.Responses, util.Empty{})
			}

			return resp, nil
		})

		orcapi.SharesProviderCreate.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.Share]) (fnd.BulkResponse[fnd.FindByStringId], *util.HttpError) {
			fn := Files.CreateShare
			if fn == nil {
				return fnd.BulkResponse[fnd.FindByStringId]{}, util.ServerHttpError("shares not supported")
			}

			var resp []fnd.FindByStringId
			var updates []orcapi.ResourceUpdateAndId[orcapi.ShareUpdate]

			for _, share := range request.Items {
				driveId, err := fn(share)
				if err != nil {
					return fnd.BulkResponse[fnd.FindByStringId]{}, err
				} else {
					updates = append(updates, orcapi.ResourceUpdateAndId[orcapi.ShareUpdate]{
						Id: share.Id,
						Update: orcapi.ShareUpdate{
							ShareAvailableAt: util.OptValue(fmt.Sprintf("/%s", driveId)),
							Timestamp:        fnd.Timestamp(time.Now()),
						},
					})
					resp = append(resp, fnd.FindByStringId{})
				}
			}

			_, err := orcapi.SharesControlAddUpdate.Invoke(fnd.BulkRequest[orcapi.ResourceUpdateAndId[orcapi.ShareUpdate]]{
				Items: updates,
			})

			if err != nil {
				log.Warn("Failed to update shares: %v", err)
				return fnd.BulkResponse[fnd.FindByStringId]{}, util.ServerHttpError("Failed to update shares")
			}

			return fnd.BulkResponse[fnd.FindByStringId]{Responses: resp}, nil
		})

		orcapi.SharesProviderDelete.Handler(func(info rpc.RequestInfo, request fnd.BulkRequest[orcapi.Share]) (fnd.BulkResponse[util.Empty], *util.HttpError) {
			// Do nothing
			var response fnd.BulkResponse[util.Empty]
			for _, item := range request.Items {
				_ = item
				response.Responses = append(response.Responses, util.Empty{})
			}
			return response, nil
		})
	}

	if RunsServerCode() {
		orcapi.DrivesProviderRetrieveProducts.Handler(func(info rpc.RequestInfo, request util.Empty) (fnd.BulkResponse[orcapi.FSSupport], *util.HttpError) {
			support := Files.RetrieveProducts()
			return fnd.BulkResponse[orcapi.FSSupport]{Responses: support}, nil
		})

		// TODO Doesn't exist any more for shares?
		/*
			mux.HandleFunc(
				shareContext+"retrieveProducts",
				HttpRetrieveHandler(0, func(w http.ResponseWriter, r *http.Request, request util.Empty) {
					support := Files.RetrieveProducts()
					var result []orcapi.ShareSupport
					for _, item := range support {
						if item.Files.SharesSupported {
							result = append(result, orcapi.ShareSupport{
								Product: item.Product,
								Type:    orcapi.ShareTypeManaged,
							})
						}
					}
					sendResponseOrError(w, fnd.BulkResponse[orcapi.ShareSupport]{Responses: result}, nil)
				}),
			)
		*/

		createTransferSessionCall.Handler(func(r *ipc.Request[orcapi.FilesProviderTransferRequestInitiateSource]) ipc.Response[fnd.FindByStringId] {
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
						FilesProviderTransferRequestInitiateSource: orcapi.FilesProviderTransferRequestInitiateSource{
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
						ConflictPolicy: orcapi.WriteConflictPolicy(row.ConflictPolicy),
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

func CreateFolderUpload(path string, conflictPolicy orcapi.WriteConflictPolicy, sessionData string) (upload.ServerSession, string) {
	session := upload.ServerSession{
		Id:             util.RandomToken(32),
		ConflictPolicy: conflictPolicy,
		Path:           path,
		UserData:       sessionData,
	}

	uploadPath := generateUploadPath(cfg.Provider, cfg.Provider.Id, orcapi.UploadProtocolWebSocketV2, session.Id, UCloudUsername)
	createUploadSession(session)
	return session, uploadPath
}

func generateUploadPath(
	config *cfg.ProviderConfiguration,
	providerId string,
	protocol orcapi.UploadProtocol,
	token string,
	username string,
) string {
	var hostPath string = ""
	if config != nil {
		switch protocol {
		case orcapi.UploadProtocolChunked:
			hostPath = config.Hosts.SelfPublic.ToURL()
		case orcapi.UploadProtocolWebSocketV2:
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
	orcapi.FilesProviderTransferRequestInitiateSource

	SelectedProtocol   string
	ProtocolParameters json.RawMessage
	Username           string
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

	createTransferSessionCall  = ipc.NewCall[orcapi.FilesProviderTransferRequestInitiateSource, fnd.FindByStringId]("ctrl.files.createTransferSession")
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
