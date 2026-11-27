package terminalproto

import (
	"bytes"
	"compress/flate"
	"encoding/binary"
	"errors"
	"sync"
)

const (
	TerminalProtoVersion = uint8(1)

	TerminalProtoFlagCompressed = uint8(0x80)
	TerminalProtoOpcodeMask     = uint8(0x7F)

	TerminalProtoOpHello        = uint8(0x01)
	TerminalProtoOpHelloAck     = uint8(0x02)
	TerminalProtoOpPing         = uint8(0x03)
	TerminalProtoOpPong         = uint8(0x04)
	TerminalProtoOpPaneAttach   = uint8(0x10)
	TerminalProtoOpPaneReady    = uint8(0x11)
	TerminalProtoOpPaneData     = uint8(0x12)
	TerminalProtoOpPaneResize   = uint8(0x13)
	TerminalProtoOpPaneDetach   = uint8(0x14)
	TerminalProtoOpPaneShutdown = uint8(0x15)
	TerminalProtoOpPaneStatus   = uint8(0x16)
	TerminalProtoOpPaneCredit   = uint8(0x17)
	TerminalProtoOpPaneMeta     = uint8(0x18)

	TerminalProtoAttachFlagReplay = uint8(1 << 0)

	TerminalProtoReadyPersistent    = uint8(1 << 0)
	TerminalProtoReadyUcloudPty     = uint8(1 << 1)
	TerminalProtoReadyVmAgentPty    = uint8(1 << 2)
	TerminalProtoReadyFallbackShell = uint8(1 << 3)
	TerminalProtoReadyResetRequired = uint8(1 << 4)

	TerminalProtoMetaBusy        = uint8(1 << 0)
	TerminalProtoMetaCommandDone = uint8(1 << 1)

	TerminalProtoStatusExited   = uint8(1)
	TerminalProtoStatusError    = uint8(2)
	TerminalProtoStatusJobEnded = uint8(3)
	TerminalProtoStatusReplaced = uint8(4)

	TerminalProtoHeaderBytes       = 3
	TerminalProtoInitialCredit     = uint32(256 * 1024)
	TerminalProtoCompressThreshold = 512
)

type TerminalProtoFrame struct {
	Opcode     uint8
	Compressed bool
	ChannelId  uint16
	Payload    []byte
}

type TerminalProtoHello struct {
	Version uint8
	Token   string
}

type TerminalProtoPaneAttach struct {
	Rank         uint16
	Cols         uint16
	Rows         uint16
	Flags        uint8
	Handle       string
	LastOffset   uint64
	SourceHandle string
}

var terminalProtoFlatePool = sync.Pool{
	New: func() any {
		writer, _ := flate.NewWriter(nil, flate.BestSpeed)
		return writer
	},
}

func TerminalProtoDecodeHeader(raw []byte) (TerminalProtoFrame, error) {
	if len(raw) < TerminalProtoHeaderBytes {
		return TerminalProtoFrame{}, errors.New("frame too short")
	}
	first := raw[0]
	return TerminalProtoFrame{
		Opcode:     first & TerminalProtoOpcodeMask,
		Compressed: (first & TerminalProtoFlagCompressed) != 0,
		ChannelId:  binary.LittleEndian.Uint16(raw[1:3]),
		Payload:    raw[3:],
	}, nil
}

func TerminalProtoDecodeHello(payload []byte) (TerminalProtoHello, error) {
	if len(payload) < 3 {
		return TerminalProtoHello{}, errors.New("hello payload too short")
	}
	version := payload[0]
	tokenLen := int(binary.LittleEndian.Uint16(payload[1:3]))
	if len(payload) < 3+tokenLen {
		return TerminalProtoHello{}, errors.New("hello token truncated")
	}
	return TerminalProtoHello{
		Version: version,
		Token:   string(payload[3 : 3+tokenLen]),
	}, nil
}

func TerminalProtoDecodePaneAttach(payload []byte) (TerminalProtoPaneAttach, error) {
	if len(payload) < 8 {
		return TerminalProtoPaneAttach{}, errors.New("pane attach payload too short")
	}
	rank := binary.LittleEndian.Uint16(payload[0:2])
	cols := binary.LittleEndian.Uint16(payload[2:4])
	rows := binary.LittleEndian.Uint16(payload[4:6])
	flags := payload[6]
	handleLen := int(payload[7])
	if len(payload) < 8+handleLen {
		return TerminalProtoPaneAttach{}, errors.New("pane attach handle truncated")
	}
	handle := string(payload[8 : 8+handleLen])

	var lastOffset uint64
	var sourceHandle string
	extIdx := 8 + handleLen
	if len(payload) >= extIdx+9 {
		lastOffset = binary.LittleEndian.Uint64(payload[extIdx : extIdx+8])
		srcLen := int(payload[extIdx+8])
		if len(payload) >= extIdx+9+srcLen {
			sourceHandle = string(payload[extIdx+9 : extIdx+9+srcLen])
		}
	}

	return TerminalProtoPaneAttach{
		Rank:         rank,
		Cols:         cols,
		Rows:         rows,
		Flags:        flags,
		Handle:       handle,
		LastOffset:   lastOffset,
		SourceHandle: sourceHandle,
	}, nil
}

func TerminalProtoDecodeResize(payload []byte) (uint16, uint16, error) {
	if len(payload) < 4 {
		return 0, 0, errors.New("resize payload too short")
	}
	cols := binary.LittleEndian.Uint16(payload[0:2])
	rows := binary.LittleEndian.Uint16(payload[2:4])
	return cols, rows, nil
}

func TerminalProtoDecodeCredit(payload []byte) (uint32, error) {
	if len(payload) < 4 {
		return 0, errors.New("credit payload too short")
	}
	return binary.LittleEndian.Uint32(payload[0:4]), nil
}

func TerminalProtoEncodeHelloAck(capabilities uint8, initialCredit uint32) []byte {
	buf := make([]byte, TerminalProtoHeaderBytes+6)
	buf[0] = TerminalProtoOpHelloAck
	binary.LittleEndian.PutUint16(buf[1:3], 0)
	buf[3] = TerminalProtoVersion
	buf[4] = capabilities
	binary.LittleEndian.PutUint32(buf[5:9], initialCredit)
	return buf
}

func TerminalProtoEncodePong(payload []byte) []byte {
	buf := make([]byte, TerminalProtoHeaderBytes+len(payload))
	buf[0] = TerminalProtoOpPong
	binary.LittleEndian.PutUint16(buf[1:3], 0)
	copy(buf[3:], payload)
	return buf
}

func TerminalProtoEncodePaneReady(channelId uint16, flags uint8, baseOffset uint64, replayBytes uint32, warning string) []byte {
	warningBytes := []byte(warning)
	if len(warningBytes) > 65535 {
		warningBytes = warningBytes[:65535]
	}
	buf := make([]byte, TerminalProtoHeaderBytes+15+len(warningBytes))
	buf[0] = TerminalProtoOpPaneReady
	binary.LittleEndian.PutUint16(buf[1:3], channelId)
	buf[3] = flags
	binary.LittleEndian.PutUint64(buf[4:12], baseOffset)
	binary.LittleEndian.PutUint32(buf[12:16], replayBytes)
	binary.LittleEndian.PutUint16(buf[16:18], uint16(len(warningBytes)))
	copy(buf[18:], warningBytes)
	return buf
}

func TerminalProtoEncodePaneMeta(channelId uint16, busy bool, commandDone bool, command string, cwd string) []byte {
	cmdBytes := []byte(command)
	cwdBytes := []byte(cwd)
	if len(cmdBytes) > 4096 {
		cmdBytes = cmdBytes[:4096]
	}
	if len(cwdBytes) > 4096 {
		cwdBytes = cwdBytes[:4096]
	}
	buf := make([]byte, TerminalProtoHeaderBytes+5+len(cmdBytes)+len(cwdBytes))
	buf[0] = TerminalProtoOpPaneMeta
	binary.LittleEndian.PutUint16(buf[1:3], channelId)
	var flags uint8
	if busy {
		flags |= TerminalProtoMetaBusy
	}
	if commandDone {
		flags |= TerminalProtoMetaCommandDone
	}
	buf[3] = flags
	binary.LittleEndian.PutUint16(buf[4:6], uint16(len(cmdBytes)))
	copy(buf[6:6+len(cmdBytes)], cmdBytes)
	cwdPos := 6 + len(cmdBytes)
	binary.LittleEndian.PutUint16(buf[cwdPos:cwdPos+2], uint16(len(cwdBytes)))
	copy(buf[cwdPos+2:], cwdBytes)
	return buf
}

func TerminalProtoEncodePaneStatus(channelId uint16, statusCode uint8, exitCode int32, message string) []byte {
	msgBytes := []byte(message)
	if len(msgBytes) > 65535 {
		msgBytes = msgBytes[:65535]
	}
	buf := make([]byte, TerminalProtoHeaderBytes+7+len(msgBytes))
	buf[0] = TerminalProtoOpPaneStatus
	binary.LittleEndian.PutUint16(buf[1:3], channelId)
	buf[3] = statusCode
	binary.LittleEndian.PutUint32(buf[4:8], uint32(exitCode))
	binary.LittleEndian.PutUint16(buf[8:10], uint16(len(msgBytes)))
	copy(buf[10:], msgBytes)
	return buf
}

func TerminalProtoEncodePaneData(channelId uint16, data []byte) []byte {
	if len(data) > TerminalProtoCompressThreshold {
		var compressed bytes.Buffer
		compressed.Grow(len(data)/2 + TerminalProtoHeaderBytes)
		compressed.WriteByte(TerminalProtoOpPaneData | TerminalProtoFlagCompressed)
		var chanBytes [2]byte
		binary.LittleEndian.PutUint16(chanBytes[:], channelId)
		compressed.Write(chanBytes[:])

		writer := terminalProtoFlatePool.Get().(*flate.Writer)
		writer.Reset(&compressed)
		_, writeErr := writer.Write(data)
		closeErr := writer.Close()
		terminalProtoFlatePool.Put(writer)

		if writeErr == nil && closeErr == nil && compressed.Len() < TerminalProtoHeaderBytes+len(data) {
			return compressed.Bytes()
		}
	}

	buf := make([]byte, TerminalProtoHeaderBytes+len(data))
	buf[0] = TerminalProtoOpPaneData
	binary.LittleEndian.PutUint16(buf[1:3], channelId)
	copy(buf[3:], data)
	return buf
}
