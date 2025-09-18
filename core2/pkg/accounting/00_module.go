package accounting

import (
	"net/http"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"

	ws "github.com/gorilla/websocket"
)

func Init() {
	initProducts()
	initAccounting()
	initGrants()

	wsUpgrader := ws.Upgrader{
		ReadBufferSize:  1024 * 4,
		WriteBufferSize: 1024 * 4,
		Subprotocols:    []string{"binary"},
	}
	wsUpgrader.CheckOrigin = func(r *http.Request) bool { return true }

	rpc.DefaultServer.Mux.HandleFunc("/api/accounting/notifications", func(w http.ResponseWriter, r *http.Request) {
		// TODO temporary endpoint to make provider happy
		conn, err := wsUpgrader.Upgrade(w, r, nil)
		if err != nil {
			w.WriteHeader(http.StatusBadRequest)
			_, _ = w.Write(nil)
		} else {
			for {
				_, _, err := conn.ReadMessage()
				if err != nil {
					break
				}
			}

			util.SilentClose(conn)
		}
	})
}
