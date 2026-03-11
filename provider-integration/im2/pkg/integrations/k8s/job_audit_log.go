package k8s

import (
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"

	"ucloud.dk/pkg/gateway"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var jobAuditLogFolder = "/mnt/storage/audit"

func StartJobAuditLogging() {
	startServer()
}

func writeJobAuditLog(event JobAuditEvent, rank string) error {
	filename := fmt.Sprintf(
		"%s/audit-%s-%s.jsonl",
		jobAuditLogFolder,
		time.Now().Format("2006-01-02"), rank,
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

var JobAuditLogTest = rpc.Call[util.Empty, util.Empty]{
	BaseContext: "",
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesPublic,
	Operation:   "test",
}

var defaultServer rpc.Server

func startServer() {
	defaultServer.Mux = http.NewServeMux()
	serverPort := 48291

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
			JobID:       "MyJOB",
			WorkspaceID: "MyWorkspace",
			Event:       request.Event,
			Message:     request.Message,
			Meta:        request.Meta,
		}, "UCLOUD_RANK")

		if err != nil {
			return util.Empty{}, &util.HttpError{
				StatusCode: http.StatusInternalServerError,
				Why:        err.Error(),
			}
		}
		return util.Empty{}, nil
	})

	JobAuditLogTest.Handler(func(info rpc.RequestInfo, empty util.Empty) (util.Empty, *util.HttpError) {
		log.Info("Simple call")
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
