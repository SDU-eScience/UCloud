package ipc

import (
	"context"
	"fmt"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
	"ucloud.dk/pkg/im"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/log"
)

func InitIpc() {
	socketPath := filepath.Join(cfg.Provider.Ipc.Directory, "ucloud.sock")
	_ = os.Remove(socketPath)
	listener, err := net.Listen("unix", socketPath)
	if err != nil {
		log.Error("Failed to create IPC socket at %v", socketPath)
		os.Exit(1)
	}

	err = os.Chmod(socketPath, 0777)
	if err != nil {
		log.Error("Failed to chmod socket at %v (%v)", socketPath, err)
		os.Exit(1)
	}

	l := &ipcListener{Listener: listener}
	server := &http.Server{
		Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			im.Args.IpcMultiplexer.ServeHTTP(w, r)
		}),
	}

	err = server.Serve(l)
	log.Error("IPC server has failed! %v", err)
	os.Exit(1)
}

type ipcListener struct {
	net.Listener
}

func GetConnectionUid(r *http.Request) (uint32, bool) {
	// NOTE(Dan): We will never return 0 when the user is unknown since this could easily lead to bugs where we think
	//that the user is authenticated as UID 0 (i.e. root).

	addr := r.RemoteAddr
	if strings.HasPrefix(addr, IpcAddrPrefix) {
		parsed, err := strconv.ParseInt(strings.TrimPrefix(addr, IpcAddrPrefix), 10, 32)
		if err != nil {
			return IpcUnknownUser, false
		}

		return uint32(parsed), true
	} else {
		return IpcUnknownUser, false
	}
}

type ipcConn struct {
	net.Conn
	uid uint32
}

func (c *ipcConn) Read(b []byte) (int, error) {
	return c.Conn.Read(b)
}

func (c *ipcConn) Write(b []byte) (int, error) {
	return c.Conn.Write(b)
}

func (c *ipcConn) Close() error {
	return c.Conn.Close()
}

func (c *ipcConn) LocalAddr() net.Addr {
	return &IpcAddr{Uid: c.uid}
}

func (c *ipcConn) RemoteAddr() net.Addr {
	return c.LocalAddr()
}

type IpcAddr struct {
	Uid uint32
}

func (a *IpcAddr) Network() string {
	return "ipc"
}

func (a *IpcAddr) String() string {
	return fmt.Sprintf("%v%v", IpcAddrPrefix, a.Uid)
}

const IpcAddrPrefix = "IPC_UID="
const IpcUnknownUser = 11400

func (c *ipcConn) SetDeadline(t time.Time) error {
	return c.Conn.SetDeadline(t)
}

func (c *ipcConn) SetReadDeadline(t time.Time) error {
	return c.Conn.SetReadDeadline(t)
}

func (c *ipcConn) SetWriteDeadline(t time.Time) error {
	return c.Conn.SetWriteDeadline(t)
}

func (c *ipcConn) Context() context.Context {
	// TODO Doesn't seem like the HTTP server actually calls this for the base context. Making this code basically
	//   useless.
	return context.WithValue(c.Context(), "uid", c.uid)
}
