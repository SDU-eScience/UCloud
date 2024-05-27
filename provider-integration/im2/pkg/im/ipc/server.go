package ipc

import (
    "errors"
    "net"
    "net/http"
    "os"
    "path/filepath"
    "syscall"
    cfg "ucloud.dk/pkg/im/config"
    "ucloud.dk/pkg/log"
    "ucloud.dk/pkg/util"
)

func InitIpc() {
    socketPath := filepath.Join(cfg.Provider.Ipc.Directory, "ucloud.sock")
    listener, err := net.Listen("unix", socketPath)
    if err != nil {
        log.Error("Failed to create IPC socket at %v", socketPath)
        os.Exit(1)
    }

    l := &ipcListener{Listener: listener}
    server := &http.Server{
        Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
            w.WriteHeader(http.StatusOK)
        }),
    }

    log.Info("IPC SERVER RUNNIGN!")
    server.Serve(l)
}

type ipcListener struct {
    net.Listener
}

func (l *ipcListener) Accept() (net.Conn, error) {
    log.Info("Accept 1")
    conn, err := l.Listener.Accept()
    if err != nil {
        log.Info("Accept 2")
        return nil, err
    }

    unixConn, ok := conn.(*net.UnixConn)
    if !ok {
        log.Info("Accept 3")
        return nil, errors.New("failed to convert to UnixConn")
    }

    file, err := unixConn.File()
    if err != nil {
        log.Info("Accept 4")
        return nil, err
    }

    log.Info("Accept 5")
    fd := int(file.Fd())
    defer util.SilentClose(file)

    err = syscall.SetsockoptInt(int(fd), syscall.SOL_SOCKET, syscall.SO_PASSCRED, 1)
    if err != nil {
        log.Info("Accept 6 %v", err)
        util.SilentClose(unixConn)
        return nil, err
    }

    cred, err := syscall.GetsockoptUcred(fd, syscall.SOL_SOCKET, syscall.SO_PEERCRED)
    if err != nil {
        log.Info("Accept 6 %v", err)
        util.SilentClose(unixConn)
        return nil, err
    }

    log.Info("Accept 7")
    log.Info("UID is %v", cred.Uid)

    return unixConn, nil
}
