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
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const jobAuditLogFolder = "/audit"

var auditLogServer struct {
	JobId       string
	Rank        int64
	WorkspaceId string
}

var jobAuditLogChannel chan JobAuditEvent

func JobAuditLogServerStart() {
	jobAuditLogChannel = make(chan JobAuditEvent, 1000) // buffered
	go jobAuditLogWriter()

	auditLogServer.Rank = getIntEnv("UCLOUD_RANK")
	auditLogServer.JobId = os.Getenv("UCLOUD_JOB_ID")
	auditLogServer.WorkspaceId = os.Getenv("UCLOUD_WORKSPACE_ID")
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

	rpc.DefaultServer.Mux = http.NewServeMux()
	rpc.ServerAuthenticator = func(r *http.Request) (rpc.Actor, *util.HttpError) {
		return rpc.Actor{Role: rpc.RoleGuest}, nil
	}

	JobAuditLogAppendLog.Handler(func(info rpc.RequestInfo, request JobAuditLogAppendRequest) (util.Empty, *util.HttpError) {
		err := jobAuditLogAppend(JobAuditEvent{
			Ts:          fnd.Timestamp(time.Now()),
			JobID:       auditLogServer.JobId,
			WorkspaceID: auditLogServer.WorkspaceId,
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

	s := &http.Server{
		Addr: fmt.Sprintf("127.0.0.1:%v", serverPort),
		Handler: collapseServerSlashes(
			http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
				handler, _ := rpc.DefaultServer.Mux.Handler(request)
				handler.ServeHTTP(writer, request)
			}),
		),
	}

	listenErr := s.ListenAndServe()
	log.Fatal("Failed to start listener on port %v: %s\n", gateway.ServerClusterPort, listenErr)
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

func jobAuditLogAppend(event JobAuditEvent) error {
	select {
	case jobAuditLogChannel <- event:
		return nil
	default:
		return fmt.Errorf("audit log channel full")
	}
}

func jobAuditLogWriter() {
	var (
		currentDate string
		f           *os.File
		encoder     *json.Encoder
		err         error
	)

	for event := range jobAuditLogChannel {

		date := time.Now().Format("2006-01-02")

		if f == nil || date != currentDate {
			if f != nil {
				f.Close()
			}

			filename := fmt.Sprintf(
				"%s/audit-%d-%s.jsonl",
				jobAuditLogFolder,
				auditLogServer.Rank,
				date,
			)

			f, err = os.OpenFile(filename, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
			if err != nil {
				log.Error("audit log open error: %v", err)
				continue
			}

			encoder = json.NewEncoder(f)
			currentDate = date
		}

		if err := encoder.Encode(event); err != nil {
			log.Error("audit log write error: %v", err)
		}
	}
}

type JobAuditEvent struct {
	Ts          fnd.Timestamp   `json:"ts"`
	JobID       string          `json:"jobId"`
	WorkspaceID string          `json:"workspaceId"`
	Event       string          `json:"event"`
	Message     string          `json:"message"`
	Meta        json.RawMessage `json:"meta"`
}

type JobAuditLogAppendRequest struct {
	Event   string          `json:"event"`
	Message string          `json:"message"`
	Meta    json.RawMessage `json:"meta"`
}

var JobAuditLogAppendLog = rpc.Call[JobAuditLogAppendRequest, util.Empty]{
	BaseContext: "",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "append",
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
