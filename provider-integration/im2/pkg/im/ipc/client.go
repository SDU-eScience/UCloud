package ipc

import (
	"context"
	"net"
	"net/http"
	"path/filepath"
	cfg "ucloud.dk/pkg/im/config"
)

var Client = &http.Client{
	Transport: &http.Transport{
		DialContext: func(_ context.Context, _, _ string) (net.Conn, error) {
			socketPath := filepath.Join(cfg.Provider.Ipc.Directory, "ucloud.sock")
			return net.Dial("unix", socketPath)
		},
	},
}
