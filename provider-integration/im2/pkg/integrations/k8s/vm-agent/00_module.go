package vm_agent

import (
	"bytes"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	ws "github.com/gorilla/websocket"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

var providerHost string

func Launch() {
	_ = log.SetLogFile("/tmp/vm-agent.log")
	startExecutableUpdateWatcher(5 * time.Second)

	ipBytes, err := os.ReadFile("/opt/ucloud/provider-ip.txt")
	if err != nil {
		log.Fatal("Could not find provider IP")
	}

	tokBytes, err := os.ReadFile("/etc/ucloud/token")
	if err != nil {
		log.Fatal("Could not find token")
	}
	tokString := string(tokBytes)
	tokLines := strings.Split(tokString, "\n")
	if len(tokLines) != 2 {
		log.Fatal("invalid token")
	}
	token := tokLines[0]
	srvTok := tokLines[1]

	providerHost = fmt.Sprintf("ws://%s:8889", string(ipBytes))

	for {
		log.Info("Connecting to %s", providerHost)
		url := fmt.Sprintf("%s/api/%s", providerHost, VmaStream.BaseContext)

		c, _, err := ws.DefaultDialer.Dial(url, nil)
		if err != nil {
			log.Warn("Failed to establish WebSocket connection: %v %v", url, err)
			time.Sleep(5 * time.Second)
			continue
		}

		s := &vmaSession{
			Conn: c,
			Ok:   true,
		}
		handleSession(s, token, srvTok)
		time.Sleep(5 * time.Second)
	}
}

func startExecutableUpdateWatcher(interval time.Duration) {
	exePath, err := os.Executable()
	if err != nil {
		log.Warn("Unable to resolve executable path: %v", err)
		return
	}

	resolvedExePath, err := filepath.EvalSymlinks(exePath)
	if err != nil {
		log.Warn("Unable to resolve executable symlinks, using original path (%s): %v", exePath, err)
		resolvedExePath = exePath
	}

	initialModTime, err := executableModTime(resolvedExePath)
	if err != nil {
		log.Warn("Unable to read executable timestamp (%s): %v", resolvedExePath, err)
		return
	}

	checkForUpdate := func() {
		currentModTime, err := executableModTime(resolvedExePath)
		if err != nil {
			log.Warn("Unable to read executable timestamp (%s): %v", resolvedExePath, err)
			return
		}

		if !currentModTime.Equal(initialModTime) {
			log.Info("Executable update detected (%s). Exiting.", resolvedExePath)
			os.Exit(0)
		}
	}

	checkForUpdate()

	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()

		for range ticker.C {
			checkForUpdate()
		}
	}()
}

func executableModTime(path string) (time.Time, error) {
	info, err := os.Stat(path)
	if err != nil {
		return time.Time{}, err
	}

	return info.ModTime(), nil
}

func handleSession(s *vmaSession, token string, serverToken string) {
	socketMessages := make(chan []byte)
	go func() {
		for s.Ok {
			message := s.ReadMessage()

			if s.Ok {
				socketMessages <- message
			} else {
				break
			}
		}

		socketMessages <- nil
	}()

	{
		_ = s.SendText(token)
		msg := <-socketMessages
		if string(msg) != serverToken {
			return
		}
	}

	ticker := time.NewTicker(1 * time.Second)

	for {
		select {
		case message := <-socketMessages:
			if message == nil {
				return
			}

			buf := util.NewBuffer(bytes.NewBuffer(message))
			switch VmaServerOpCode(buf.ReadU8()) {
			case VmaSrvSshKeys:
				count := int(buf.ReadS32())
				var keys []string
				for i := 0; i < count; i++ {
					key := buf.ReadString()
					keys = append(keys, key)
				}

				err := installSshKeys(keys)
				if err != nil {
					log.Warn("Failed to install ssh keys: %s", err)
				}
			}
		case <-ticker.C:
			rawBuf := &bytes.Buffer{}
			b := util.NewBuffer(rawBuf)
			b.WriteU8(uint8(VmaAgentHeartbeat))
			s.SendBinary(rawBuf.Bytes())
		}
	}
}

type vmaSession struct {
	Conn *ws.Conn
	Ok   bool
}

func (s *vmaSession) SendBinary(data []byte) bool {
	if s.Ok {
		s.Ok = s.Conn.WriteMessage(ws.BinaryMessage, data) == nil
	}

	return s.Ok
}

func (s *vmaSession) SendText(data string) bool {
	if s.Ok {
		s.Ok = s.Conn.WriteMessage(ws.TextMessage, []byte(data)) == nil
	}

	return s.Ok
}

func (s *vmaSession) ReadMessage() []byte {
	if s.Ok {
		_, message, err := s.Conn.ReadMessage()
		if err == nil {
			return message
		} else {
			s.Ok = false
			return nil
		}
	} else {
		return nil
	}
}

func installSshKeys(keys []string) *util.HttpError {
	homeDir, err := os.UserHomeDir()
	if err != nil {
		return util.UserHttpError("getting home directory: %v", err)
	}

	sshDir := filepath.Join(homeDir, ".ssh")
	if err := os.MkdirAll(sshDir, 0700); err != nil {
		return util.UserHttpError("creating SSH directory: %v", err)
	}

	authorizedKeysFile := filepath.Join(sshDir, "authorized_keys")
	info, err := os.Stat(authorizedKeysFile)
	if err == nil && info.IsDir() {
		return util.UserHttpError("~/.ssh/authorized_keys is a directory, not a file")
	}

	// Read existing authorized keys
	var authorizedKeys []string
	if data, err := os.ReadFile(authorizedKeysFile); err == nil {
		lines := strings.Split(string(data), "\n")
		for _, line := range lines {
			trimmed := strings.TrimSpace(line)
			if trimmed != "" {
				authorizedKeys = append(authorizedKeys, trimmed)
			}
		}
	} else if !os.IsNotExist(err) {
		return util.UserHttpError("reading authorized_keys: %v", err)
	}

	// Start building the new file. First skipping all UCloud marked keys and then adding in the new keys.
	newFile := ""
	for _, key := range authorizedKeys {
		if !strings.HasSuffix(key, ucloudIntegrationMarker) {
			newFile += key
			newFile += "\n"
		}
	}

	for _, key := range keys {
		withMarker := strings.TrimSpace(key) + " " + ucloudIntegrationMarker
		newFile += withMarker
		newFile += "\n"
	}

	// Write updated keys to a temporary file in order to perform an atomic replacement later
	tempFile := filepath.Join(sshDir, fmt.Sprintf("authorized_keys-%d.in-progress", time.Now().Unix()))

	if err := os.WriteFile(tempFile, []byte(newFile), 0600); err != nil {
		return util.UserHttpError("writing to temporary file: %v", err)
	}

	if err := os.Rename(tempFile, authorizedKeysFile); err != nil {
		_ = os.Remove(tempFile) // Cleanup temporary file
		return util.UserHttpError("replacing authorized_keys file: %v", err)
	}

	log.Info("Successfully installed %d SSH key(s)", len(keys))
	return nil
}

const ucloudIntegrationMarker = "ucloud-managed-key"
