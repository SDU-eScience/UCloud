package ipc

import (
	"fmt"
	"net"
)

func (l *ipcListener) Accept() (net.Conn, error) {
	return nil, fmt.Errorf("Not yet implemented for macOS")
}
