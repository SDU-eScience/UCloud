package vm_agent

import (
	"context"
	"encoding/json"
	"os"
	"os/exec"
	"time"

	"github.com/creack/pty"
	ws "github.com/gorilla/websocket"
	"ucloud.dk/pkg/external/user"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

type vmaTtySession struct {
	Conn  *ws.Conn
	Ok    bool
	Token string
}

func (s *vmaTtySession) SendBinary(data []byte) bool {
	if s.Ok {
		s.Ok = s.Conn.WriteMessage(ws.BinaryMessage, data) == nil
	}

	return s.Ok
}

func (s *vmaTtySession) SendText(data string) bool {
	if s.Ok {
		s.Ok = s.Conn.WriteMessage(ws.TextMessage, []byte(data)) == nil
	}

	return s.Ok
}

func (s *vmaTtySession) ReadMessage() []byte {
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

func handleTtySession(s *vmaTtySession) {
	defer util.SilentClose(s.Conn)

	if !s.SendText(s.Token) {
		return
	}

	if string(s.ReadMessage()) != "OK" {
		return
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	ptyOutput := make(chan []byte, 32)
	socketMessages := make(chan []byte, 1)
	go func() {
		defer close(socketMessages)

		for s.Ok {
			message := s.ReadMessage()

			if s.Ok {
				select {
				case socketMessages <- message:
				case <-ctx.Done():
					return
				}
			} else {
				break
			}
		}
	}()

	ticker := time.NewTicker(1 * time.Second)
	defer ticker.Stop()

	var ptmx *os.File
	var cmd *exec.Cmd
	var cmdDone chan struct{}

	resize := func(rows int, cols int) {
		if ptmx == nil {
			return
		}

		_ = pty.Setsize(ptmx, &pty.Winsize{
			Rows: uint16(rows - 1),
			Cols: uint16(cols),
		})
	}

	defer func() {
		cancel()

		if ptmx != nil {
			util.SilentClose(ptmx)
		}

		if cmd != nil {
			if cmd.Process != nil {
				_ = cmd.Process.Kill()
			}

			if cmdDone != nil {
				select {
				case <-cmdDone:
				case <-time.After(1 * time.Second):
				}
			}
		}
	}()

	for {
		select {
		case <-ctx.Done():
			return

		case output, ok := <-ptyOutput:
			if !ok {
				return
			}

			if !s.SendBinary(output) {
				return
			}

		case rawMessage, ok := <-socketMessages:
			if !ok {
				return
			}

			message := ShellEvent{}
			err := json.Unmarshal(rawMessage, &message)
			if err != nil {
				log.Info("Failed to unmarshal websocket message: %v", err)
				return
			}

			switch message.Type {
			case ShellEventTypeInit:
				if cmd != nil {
					continue
				}

				preferredShell := "/bin/sh"
				if sh := os.Getenv("SHELL"); sh != "" {
					preferredShell = sh
				} else if u, err := user.Current(); err == nil && u.Shell != "" {
					preferredShell = u.Shell
				}

				cmd = exec.Command(preferredShell, "-l")
				cmd.Env = os.Environ()
				cmd.Env = append(cmd.Env, "TERM=xterm-256color")

				ptmx, err = pty.Start(cmd)
				if err != nil {
					log.Warn("pty.Start(...): %v", err)
					return
				}

				cmdDone = make(chan struct{})
				go func() {
					defer close(cmdDone)
					_ = cmd.Wait()
					cancel()
				}()

				go func() {
					defer close(ptyOutput)
					buf := make([]byte, 32*1024)
					for {
						n, rerr := ptmx.Read(buf)
						if n > 0 {
							copied := make([]byte, n)
							copy(copied, buf[:n])
							select {
							case <-ctx.Done():
								return
							case ptyOutput <- copied:
							}
						}
						if rerr != nil {
							return
						}
						select {
						case <-ctx.Done():
							return
						default:
						}
					}
				}()

				resize(message.Rows, message.Cols)

			case ShellEventTypeInput:
				if ptmx == nil {
					return
				}

				if _, err := ptmx.Write([]byte(message.Data)); err != nil {
					return
				}

			case ShellEventTypeResize:
				if ptmx == nil {
					return
				}

				resize(message.Rows, message.Cols)

			case ShellEventTypeTerminate:
				return
			}

		case <-ticker.C:
			// Do nothing (for now)
		}
	}
}
