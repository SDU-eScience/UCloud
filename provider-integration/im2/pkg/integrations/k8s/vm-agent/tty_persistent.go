package vm_agent

import (
	"errors"
	"fmt"
	"sync"

	ws "github.com/gorilla/websocket"
	"ucloud.dk/pkg/ptyhost"
	"ucloud.dk/shared/pkg/log"
)

var vmaPtyServer = ptyhost.PtyHostNewServer("/work")

func vmaHandlePersistentMux(s *vmaTtySession, firstRaw []byte, terminate <-chan struct{}) {
	done := make(chan struct{})
	defer close(done)

	go func() {
		select {
		case <-terminate:
			_ = s.Conn.Close()
		case <-done:
		}
	}()

	var writeMu sync.Mutex
	writeFrame := func(op uint8, channelId uint16, payload []byte) error {
		encoded := ptyhost.PtyHostEncodeMuxFrame(op, channelId, payload)
		writeMu.Lock()
		defer writeMu.Unlock()
		return s.Conn.WriteMessage(ws.BinaryMessage, encoded)
	}

	pendingFirst := firstRaw
	readFrame := func() (ptyhost.PtyHostMuxFrame, error) {
		if pendingFirst != nil {
			raw := pendingFirst
			pendingFirst = nil
			return ptyhost.PtyHostDecodeMuxFrame(raw)
		}
		msgType, raw, err := s.Conn.ReadMessage()
		if err != nil {
			return ptyhost.PtyHostMuxFrame{}, err
		}
		if msgType != ws.BinaryMessage {
			return ptyhost.PtyHostMuxFrame{}, errors.New("unexpected non-binary mux message")
		}
		return ptyhost.PtyHostDecodeMuxFrame(raw)
	}

	ptyhost.PtyHostServeMuxStream(vmaPtyServer, readFrame, writeFrame)
}

func vmaHandlePersistentTty(s *vmaTtySession, initial ShellEvent, socketMessages <-chan []byte, terminate <-chan struct{}) {
	if initial.Type == ShellEventTypeKill {
		ptyhost.PtyHostServerKillHandle(vmaPtyServer, initial.Handle)
		_ = s.SendText("KILLED")
		return
	}

	entry, _, err := ptyhost.PtyHostServerGetOrCreate(vmaPtyServer, initial.Handle, initial.Cols, initial.Rows, "")
	if err != nil {
		log.Warn("Failed to open persistent VM PTY %s: %v", initial.Handle, err)
		_ = s.SendText("ERROR:" + err.Error())
		return
	}

	attached := ptyhost.PtyHostAttach(entry, initial.Replay, 0)
	defer ptyhost.PtyHostDetach(entry, attached.Sink)

	if len(attached.Snapshot) > 0 {
		if !s.SendBinary(attached.Snapshot) {
			return
		}
	}

	for {
		select {
		case <-terminate:
			return
		case <-attached.Sink.Closed:
			return
		case exitCode := <-attached.Sink.Exited:
			_ = s.SendText(fmt.Sprintf("EXITED:%d", exitCode))
			return
		case output := <-attached.Sink.Output:
			if !s.SendBinary(output) {
				return
			}
			ptyhost.PtyHostGrantCredit(attached.Sink, int64(len(output)))
		case rawMessage, ok := <-socketMessages:
			if !ok {
				return
			}
			msg := ShellEvent{}
			unmarshalErr := unmarshalShellEvent(rawMessage, &msg)
			if unmarshalErr != nil {
				return
			}
			switch msg.Type {
			case ShellEventTypeInput:
				writeErr := ptyhost.PtyHostWrite(entry, []byte(msg.Data))
				if writeErr != nil {
					return
				}
			case ShellEventTypeResize:
				ptyhost.PtyHostResize(entry, msg.Cols, msg.Rows)
			case ShellEventTypeKill:
				ptyhost.PtyHostServerKillHandle(vmaPtyServer, entry.Handle)
				_ = s.SendText("KILLED")
				return
			case ShellEventTypeTerminate:
				return
			}
		}
	}
}
