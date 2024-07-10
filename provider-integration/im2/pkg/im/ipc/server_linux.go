package ipc

func (l *ipcListener) Accept() (net.Conn, error) {
	conn, err := l.Listener.Accept()
	if err != nil {
		return nil, err
	}

	unixConn, ok := conn.(*net.UnixConn)
	if !ok {
		return nil, errors.New("failed to convert to UnixConn")
	}

	file, err := unixConn.File()
	if err != nil {
		return nil, err
	}

	fd := int(file.Fd())
	defer util.SilentClose(file)

	err = syscall.SetsockoptInt(int(fd), syscall.SOL_SOCKET, syscall.SO_PASSCRED, 1)
	if err != nil {
		util.SilentClose(unixConn)
		return nil, err
	}

	cred, err := syscall.GetsockoptUcred(fd, syscall.SOL_SOCKET, syscall.SO_PEERCRED)
	if err != nil {
		return nil, err
	}

	wrapped := &ipcConn{Conn: conn, uid: cred.Uid}
	return wrapped, nil
}
