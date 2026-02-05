package im

import (
	"net/http"

	cfg "ucloud.dk/pkg/im/config"
	db "ucloud.dk/shared/pkg/database"
)

type ModuleArgs struct {
	Mode                 cfg.ServerMode
	GatewayConfigChannel chan []byte
	Database             *db.Pool
	ConfigDir            string
	UserModeSecret       string
	IpcMultiplexer       *http.ServeMux
	MetricsHandler       *func(writer http.ResponseWriter, request *http.Request)
}

var Args *ModuleArgs // Only valid after ModuleMain has finished initialization
