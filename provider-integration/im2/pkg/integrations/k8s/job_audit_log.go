package k8s

import (
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"

	"ucloud.dk/pkg/gateway"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var jobAuditLogFolder = "/mnt/storage/audit"
var ucloudRank int64
var ucloudJobId string
var ucloudWorkspaceId string

func StartJobAuditLogServer() {
	ucloudRank = getIntEnv("UCLOUD_RANK")
	ucloudJobId = os.Getenv("UCLOUD_JOB_ID")
	ucloudWorkspaceId = os.Getenv("UCLOUD_WORKSPACE_ID")

	var ourArgs []string
	if len(os.Args) > 1 {
		ourArgs = os.Args[2:]
	}
	serverPort, err := strconv.ParseInt(ourArgs[0], 10, 64)
	if err != nil {
		log.Error("Could not parse port: %v", err)
		return
	}
	log.Info("Starting job audit log server on port %d", serverPort)
	startServer(serverPort)
}

func getIntEnv(env string) int64 {
	value := os.Getenv(env)
	if value != "" {
		value, err := strconv.ParseInt(value, 10, 64)
		if err == nil {
			return value
		}
	}
	return 0
}

func writeJobAuditLog(event JobAuditEvent) error {
	filename := fmt.Sprintf(
		"%s/audit-%d-%s.jsonl",
		jobAuditLogFolder,
		ucloudRank,
		time.Now().Format("2006-01-02"),
	)

	f, err := os.OpenFile(filename, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		return err
	}
	defer f.Close()

	encoder := json.NewEncoder(f)
	return encoder.Encode(event)
}

type JobAuditEvent struct {
	Ts          time.Time            `json:"ts"`
	JobID       string               `json:"jobId"`
	WorkspaceID string               `json:"workspaceId"`
	Event       string               `json:"event"`
	Message     string               `json:"message"`
	Meta        JobAuditMetadataType `json:"meta"`
}

type JobAuditMetadataType struct {
	Path string `json:"path"`
	Rows int    `json:"rows"`
}
type JobAuditLogAppendRequest struct {
	Event   string               `json:"event"`
	Message string               `json:"message"`
	Meta    JobAuditMetadataType `json:"meta"`
}

var JobAuditLogAppendLog = rpc.Call[JobAuditLogAppendRequest, util.Empty]{
	BaseContext: "",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "append",
}

var defaultServer rpc.Server

func startServer(serverPort int64) {
	defaultServer.Mux = http.NewServeMux()

	s := &http.Server{
		Addr: fmt.Sprintf(":%v", serverPort),
		Handler: collapseServerSlashes(
			http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
				handler, _ := rpc.DefaultServer.Mux.Handler(request)
				handler.ServeHTTP(writer, request)
			}),
		),
	}
	err := s.ListenAndServe()

	if err != nil {
		fmt.Printf("Failed to start listener on port %v\n", gateway.ServerClusterPort)
		os.Exit(1)
	}

	JobAuditLogAppendLog.Handler(func(info rpc.RequestInfo, request JobAuditLogAppendRequest) (util.Empty, *util.HttpError) {
		err := writeJobAuditLog(JobAuditEvent{
			Ts:          time.Now().UTC(),
			JobID:       ucloudJobId,
			WorkspaceID: ucloudWorkspaceId,
			Event:       request.Event,
			Message:     request.Message,
			Meta:        request.Meta,
		})

		if err != nil {
			return util.Empty{}, &util.HttpError{
				StatusCode: http.StatusInternalServerError,
				Why:        err.Error(),
			}
		}
		return util.Empty{}, nil
	})
}

func collapseServerSlashes(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		p := r.URL.Path
		if p == "" {
			next.ServeHTTP(w, r)
			return
		}

		// Preserve a trailing slash (except for root)
		trailing := strings.HasSuffix(p, "/") && p != "/"

		// Replace until stable
		clean := p
		for {
			newp := strings.ReplaceAll(clean, "//", "/")
			if newp == clean {
				break
			}
			clean = newp
		}
		if trailing && clean != "/" && !strings.HasSuffix(clean, "/") {
			clean += "/"
		}

		if clean == p {
			next.ServeHTTP(w, r)
			return
		}

		// Clone request and update path (leave query untouched)
		r2 := r.Clone(r.Context())
		u := *r.URL
		u.Path = clean
		r2.URL = &u
		next.ServeHTTP(w, r2)
	})
}
