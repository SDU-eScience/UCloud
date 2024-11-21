package launcher

import "golang.org/x/crypto/ssh"

var disableRemoteFileWriting = false

type SSHConnection struct {
	username   string
	host       string
	ssh        *ssh.Client
	remoteRoot string
}

func newSSHConnection(username string, host string) SSHConnection {
	config := &ssh.ClientConfig{
		User:              username,
		Auth:              ssh.AuthMethod(
								ssh.),
		HostKeyCallback:   nil,
		BannerCallback:    nil,
		ClientVersion:     "",
		HostKeyAlgorithms: nil,
		Timeout:           0,
	}
	connector := ssh.Client{}
	return SSHConnection{
		username:   username,
		host:       host,
		ssh:        ssh,
		remoteRoot: remoteRoot,
	}
}


func SyncRepository() {
	//TODO()
}