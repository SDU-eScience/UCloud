package ptyhost

import (
	"encoding/binary"
	"errors"
	"io"
	"regexp"
)

const (
	PtyHostMuxOpAttach = uint8(0x01)
	PtyHostMuxOpInput  = uint8(0x02)
	PtyHostMuxOpResize = uint8(0x03)
	PtyHostMuxOpDetach = uint8(0x04)
	PtyHostMuxOpKill   = uint8(0x05)
	PtyHostMuxOpCredit = uint8(0x06)

	PtyHostMuxOpReady  = uint8(0x10)
	PtyHostMuxOpOutput = uint8(0x11)
	PtyHostMuxOpExited = uint8(0x12)
	PtyHostMuxOpError  = uint8(0x13)
	PtyHostMuxOpMeta   = uint8(0x14)

	PtyHostAttachFlagReplay = uint8(1 << 0)

	PtyHostReadyFlagReset = uint8(1 << 0)

	PtyHostMetaFlagBusy        = uint8(1 << 0)
	PtyHostMetaFlagCommandDone = uint8(1 << 1)

	PtyHostMuxHeaderBytes = 7
)

const ptyHostMaxFrameBytes = 4 * 1024 * 1024

var ptyHostSafeHandle = regexp.MustCompile(`^[a-zA-Z0-9_-]{1,48}$`)

type PtyHostMuxFrame struct {
	Op        uint8
	ChannelId uint16
	Payload   []byte
}

type PtyHostAttachPayload struct {
	Cols         int
	Rows         int
	Replay       bool
	LastOffset   uint64
	Handle       string
	SourceHandle string
}

type PtyHostReadyPayload struct {
	ResetRequired bool
	BaseOffset    uint64
	ReplayBytes   uint32
}

type PtyHostMetaPayload struct {
	Busy        bool
	CommandDone bool
	Command     string
	Cwd         string
}

func PtyHostValidateHandle(handle string) bool {
	return ptyHostSafeHandle.MatchString(handle)
}

func PtyHostEncodeMuxFrame(op uint8, channelId uint16, payload []byte) []byte {
	buf := make([]byte, PtyHostMuxHeaderBytes+len(payload))
	buf[0] = op
	binary.BigEndian.PutUint16(buf[1:3], channelId)
	binary.BigEndian.PutUint32(buf[3:7], uint32(len(payload)))
	if len(payload) > 0 {
		copy(buf[PtyHostMuxHeaderBytes:], payload)
	}
	return buf
}

func PtyHostDecodeMuxFrame(raw []byte) (PtyHostMuxFrame, error) {
	if len(raw) < PtyHostMuxHeaderBytes {
		return PtyHostMuxFrame{}, errors.New("mux frame too short")
	}
	op := raw[0]
	channelId := binary.BigEndian.Uint16(raw[1:3])
	length := binary.BigEndian.Uint32(raw[3:7])
	if length > ptyHostMaxFrameBytes {
		return PtyHostMuxFrame{}, errors.New("mux frame exceeds max size")
	}
	if uint64(len(raw)) < uint64(PtyHostMuxHeaderBytes)+uint64(length) {
		return PtyHostMuxFrame{}, errors.New("mux frame truncated")
	}
	return PtyHostMuxFrame{
		Op:        op,
		ChannelId: channelId,
		Payload:   raw[PtyHostMuxHeaderBytes : PtyHostMuxHeaderBytes+int(length)],
	}, nil
}

func PtyHostWriteMuxFrame(w io.Writer, op uint8, channelId uint16, payload []byte) error {
	var header [PtyHostMuxHeaderBytes]byte
	header[0] = op
	binary.BigEndian.PutUint16(header[1:3], channelId)
	binary.BigEndian.PutUint32(header[3:7], uint32(len(payload)))
	_, err := w.Write(header[:])
	if err != nil {
		return err
	}
	if len(payload) > 0 {
		_, err = w.Write(payload)
		return err
	}
	return nil
}

func PtyHostReadMuxFrame(r io.Reader) (PtyHostMuxFrame, error) {
	var header [PtyHostMuxHeaderBytes]byte
	_, err := io.ReadFull(r, header[:])
	if err != nil {
		return PtyHostMuxFrame{}, err
	}
	op := header[0]
	channelId := binary.BigEndian.Uint16(header[1:3])
	length := binary.BigEndian.Uint32(header[3:7])
	if length > ptyHostMaxFrameBytes {
		return PtyHostMuxFrame{}, errors.New("mux frame exceeds max size")
	}
	if length == 0 {
		return PtyHostMuxFrame{
			Op:        op,
			ChannelId: channelId,
		}, nil
	}
	payload := make([]byte, length)
	_, err = io.ReadFull(r, payload)
	if err != nil {
		return PtyHostMuxFrame{}, err
	}
	return PtyHostMuxFrame{
		Op:        op,
		ChannelId: channelId,
		Payload:   payload,
	}, nil
}

func PtyHostEncodeAttachPayload(p PtyHostAttachPayload) []byte {
	handleBytes := []byte(p.Handle)
	sourceBytes := []byte(p.SourceHandle)
	buf := make([]byte, 15+len(handleBytes)+len(sourceBytes))
	binary.BigEndian.PutUint16(buf[0:2], uint16(max(p.Cols, 1)))
	binary.BigEndian.PutUint16(buf[2:4], uint16(max(p.Rows, 1)))
	if p.Replay {
		buf[4] = PtyHostAttachFlagReplay
	}
	binary.BigEndian.PutUint64(buf[5:13], p.LastOffset)
	buf[13] = uint8(min(len(handleBytes), 255))
	copy(buf[14:14+int(buf[13])], handleBytes[:int(buf[13])])
	srcIdx := 14 + int(buf[13])
	buf[srcIdx] = uint8(min(len(sourceBytes), 255))
	copy(buf[srcIdx+1:], sourceBytes[:int(buf[srcIdx])])
	return buf
}

func PtyHostDecodeAttachPayload(payload []byte) (PtyHostAttachPayload, error) {
	if len(payload) < 15 {
		return PtyHostAttachPayload{}, errors.New("attach payload too short")
	}
	cols := int(binary.BigEndian.Uint16(payload[0:2]))
	rows := int(binary.BigEndian.Uint16(payload[2:4]))
	replay := (payload[4] & PtyHostAttachFlagReplay) != 0
	lastOffset := binary.BigEndian.Uint64(payload[5:13])
	handleLen := int(payload[13])
	if len(payload) < 15+handleLen {
		return PtyHostAttachPayload{}, errors.New("attach handle truncated")
	}
	handle := string(payload[14 : 14+handleLen])
	srcIdx := 14 + handleLen
	srcLen := int(payload[srcIdx])
	if len(payload) < srcIdx+1+srcLen {
		return PtyHostAttachPayload{}, errors.New("attach source handle truncated")
	}
	sourceHandle := string(payload[srcIdx+1 : srcIdx+1+srcLen])
	return PtyHostAttachPayload{
		Cols:         cols,
		Rows:         rows,
		Replay:       replay,
		LastOffset:   lastOffset,
		Handle:       handle,
		SourceHandle: sourceHandle,
	}, nil
}

func PtyHostEncodeResizePayload(cols int, rows int) []byte {
	var buf [4]byte
	binary.BigEndian.PutUint16(buf[0:2], uint16(max(cols, 1)))
	binary.BigEndian.PutUint16(buf[2:4], uint16(max(rows, 1)))
	return buf[:]
}

func PtyHostDecodeResizePayload(payload []byte) (int, int, error) {
	if len(payload) < 4 {
		return 0, 0, errors.New("resize payload too short")
	}
	cols := int(binary.BigEndian.Uint16(payload[0:2]))
	rows := int(binary.BigEndian.Uint16(payload[2:4]))
	return cols, rows, nil
}

func PtyHostEncodeCreditPayload(delta uint32) []byte {
	var buf [4]byte
	binary.BigEndian.PutUint32(buf[0:4], delta)
	return buf[:]
}

func PtyHostDecodeCreditPayload(payload []byte) (uint32, error) {
	if len(payload) < 4 {
		return 0, errors.New("credit payload too short")
	}
	return binary.BigEndian.Uint32(payload[0:4]), nil
}

func PtyHostEncodeReadyPayload(p PtyHostReadyPayload) []byte {
	var buf [13]byte
	if p.ResetRequired {
		buf[0] = PtyHostReadyFlagReset
	}
	binary.BigEndian.PutUint64(buf[1:9], p.BaseOffset)
	binary.BigEndian.PutUint32(buf[9:13], p.ReplayBytes)
	return buf[:]
}

func PtyHostDecodeReadyPayload(payload []byte) (PtyHostReadyPayload, error) {
	if len(payload) < 9 {
		return PtyHostReadyPayload{}, errors.New("ready payload too short")
	}
	var replayBytes uint32
	if len(payload) >= 13 {
		replayBytes = binary.BigEndian.Uint32(payload[9:13])
	}
	return PtyHostReadyPayload{
		ResetRequired: (payload[0] & PtyHostReadyFlagReset) != 0,
		BaseOffset:    binary.BigEndian.Uint64(payload[1:9]),
		ReplayBytes:   replayBytes,
	}, nil
}

func PtyHostEncodeExitedPayload(exitCode int32) []byte {
	var buf [4]byte
	binary.BigEndian.PutUint32(buf[0:4], uint32(exitCode))
	return buf[:]
}

func PtyHostDecodeExitedPayload(payload []byte) int32 {
	if len(payload) < 4 {
		return 0
	}
	return int32(binary.BigEndian.Uint32(payload[0:4]))
}

func PtyHostEncodeMetaPayload(p PtyHostMetaPayload) []byte {
	cmdBytes := []byte(p.Command)
	cwdBytes := []byte(p.Cwd)
	if len(cmdBytes) > 4096 {
		cmdBytes = cmdBytes[:4096]
	}
	if len(cwdBytes) > 4096 {
		cwdBytes = cwdBytes[:4096]
	}
	buf := make([]byte, 5+len(cmdBytes)+len(cwdBytes))
	var flags uint8
	if p.Busy {
		flags |= PtyHostMetaFlagBusy
	}
	if p.CommandDone {
		flags |= PtyHostMetaFlagCommandDone
	}
	buf[0] = flags
	binary.BigEndian.PutUint16(buf[1:3], uint16(len(cmdBytes)))
	copy(buf[3:3+len(cmdBytes)], cmdBytes)
	cwdPos := 3 + len(cmdBytes)
	binary.BigEndian.PutUint16(buf[cwdPos:cwdPos+2], uint16(len(cwdBytes)))
	copy(buf[cwdPos+2:], cwdBytes)
	return buf
}

func PtyHostDecodeMetaPayload(payload []byte) (PtyHostMetaPayload, error) {
	if len(payload) < 5 {
		return PtyHostMetaPayload{}, errors.New("meta payload too short")
	}
	flags := payload[0]
	cmdLen := int(binary.BigEndian.Uint16(payload[1:3]))
	if len(payload) < 5+cmdLen {
		return PtyHostMetaPayload{}, errors.New("meta command truncated")
	}
	cmd := string(payload[3 : 3+cmdLen])
	cwdPos := 3 + cmdLen
	cwdLen := int(binary.BigEndian.Uint16(payload[cwdPos : cwdPos+2]))
	if len(payload) < cwdPos+2+cwdLen {
		return PtyHostMetaPayload{}, errors.New("meta cwd truncated")
	}
	cwd := string(payload[cwdPos+2 : cwdPos+2+cwdLen])
	return PtyHostMetaPayload{
		Busy:        (flags & PtyHostMetaFlagBusy) != 0,
		CommandDone: (flags & PtyHostMetaFlagCommandDone) != 0,
		Command:     cmd,
		Cwd:         cwd,
	}, nil
}

func PtyHostEncodeKillPayload(handle string) []byte {
	handleBytes := []byte(handle)
	buf := make([]byte, 1+len(handleBytes))
	buf[0] = uint8(min(len(handleBytes), 255))
	copy(buf[1:], handleBytes[:int(buf[0])])
	return buf
}

func PtyHostDecodeKillPayload(payload []byte) string {
	if len(payload) < 1 {
		return ""
	}
	n := int(payload[0])
	if len(payload) < 1+n {
		return ""
	}
	return string(payload[1 : 1+n])
}
