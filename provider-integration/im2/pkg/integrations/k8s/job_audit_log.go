package k8s

import (
	"fmt"
	"net/http"
	"os"
	"strings"

	"ucloud.dk/pkg/gateway"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func EnableJobAuditLogging() {
	startServer()
}

func appendLog() {

}

type AuditMetadataType struct {
	Path string `json:"path"`
	Rows int    `json:"rows"`
}
type AuditLogAppendRequest struct {
	Event   string            `json:"event"`
	Message string            `json:"message"`
	Meta    AuditMetadataType `json:"meta"`
}

var AuditLogAppendLog = rpc.Call[AuditLogAppendRequest, util.Empty]{
	BaseContext: "",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "append",
}

var AuditLogTest = rpc.Call[util.Empty, util.Empty]{
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

	AuditLogAppendLog.Handler(func(info rpc.RequestInfo, request AuditLogAppendRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, nil
	})

	AuditLogTest.Handler(func(info rpc.RequestInfo, empty util.Empty) (util.Empty, *util.HttpError) {
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

func TestStuff() {
	_, err := AuditLogTest.Invoke(util.Empty{})
	if err != nil {
		log.Error("Failed to invoke AuditLogTest %+v", err)
	} else {
		log.Info("Successfully called")
	}
}
