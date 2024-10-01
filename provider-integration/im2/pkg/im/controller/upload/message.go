package upload

import "ucloud.dk/pkg/util"

type messageType uint8

const (
	messageTypeOk        messageType = 0
	messageTypeChunk     messageType = 2
	messageTypeSkip      messageType = 3
	messageTypeListing   messageType = 4
	messageTypeCompleted messageType = 5
	messageTypeClose     messageType = 6

	// checksum = 1 has not yet been implemented
)

type message interface {
	// Unmarshal will read the binary version of this message from the buffer and into itself. This function _will not_
	// consume the opcode from the buffer, but instead assume that it has already been consumed.
	Unmarshal(buffer *util.UBuffer)

	// Marshal will produce the binary version of this message and put it in the supplied buffer. This function _will_
	// put the opcode as the first byte in the buffer, but only if writeOpCode is true.
	Marshal(buf *util.UBuffer, writeOpCode bool)
}

type messageOk struct {
	FileId int32
}

func (m *messageOk) Unmarshal(buf *util.UBuffer) {
	m.FileId = buf.ReadS32()
}

func (m *messageOk) Marshal(buf *util.UBuffer, writeOpCode bool) {
	if writeOpCode {
		buf.WriteU8(uint8(messageTypeOk))
	}
	buf.WriteS32(m.FileId)
}

type messageChunk struct {
	FileId int32
	Data   []byte
}

func (m *messageChunk) Unmarshal(buf *util.UBuffer) {
	m.FileId = buf.ReadS32()
	m.Data = buf.ReadRemainingBytes()
}

func (m *messageChunk) Marshal(buf *util.UBuffer, writeOpCode bool) {
	if writeOpCode {
		buf.WriteU8(uint8(messageTypeChunk))
	}
	buf.WriteS32(m.FileId)
	buf.WriteBytes(m.Data)
}

type messageSkip struct {
	FileId int32
}

func (m *messageSkip) Unmarshal(buf *util.UBuffer) {
	m.FileId = buf.ReadS32()
}

func (m *messageSkip) Marshal(buf *util.UBuffer, writeOpCode bool) {
	if writeOpCode {
		buf.WriteU8(uint8(messageTypeSkip))
	}
	buf.WriteS32(m.FileId)
}

type messageListing struct {
	FileId     int32
	Size       int64
	ModifiedAt int64
	Path       string
}

func (m *messageListing) Unmarshal(buf *util.UBuffer) {
	m.FileId = buf.ReadS32()
	m.Size = buf.ReadS64()
	m.ModifiedAt = buf.ReadS64()
	m.Path = buf.ReadString()
}

func (m *messageListing) Marshal(buf *util.UBuffer, writeOpCode bool) {
	if writeOpCode {
		buf.WriteU8(uint8(messageTypeListing))
	}
	buf.WriteS32(m.FileId)
	buf.WriteS64(m.Size)
	buf.WriteS64(m.ModifiedAt)
	buf.WriteString(m.Path)
}

type messageCompleted struct {
	NumberOfProcessedFiles int32
}

func (m *messageCompleted) Unmarshal(buf *util.UBuffer) {
	m.NumberOfProcessedFiles = buf.ReadS32()
}

func (m *messageCompleted) Marshal(buf *util.UBuffer, writeOpCode bool) {
	if writeOpCode {
		buf.WriteU8(uint8(messageTypeCompleted))
	}
	buf.WriteS32(m.NumberOfProcessedFiles)
}

type messageClose struct {
}

func (m *messageClose) Unmarshal(buf *util.UBuffer) {
}

func (m *messageClose) Marshal(buf *util.UBuffer, writeOpCode bool) {
	if writeOpCode {
		buf.WriteU8(uint8(messageTypeClose))
	}
}

func parseMessage(t messageType, buf *util.UBuffer) message {
	if buf.Error != nil {
		return nil
	}

	var msg message = nil

	switch t {
	case messageTypeOk:
		msg = &messageOk{}
	case messageTypeChunk:
		msg = &messageChunk{}
	case messageTypeSkip:
		msg = &messageSkip{}
	case messageTypeListing:
		msg = &messageListing{}
	case messageTypeCompleted:
		msg = &messageCompleted{}
	case messageTypeClose:
		msg = &messageClose{}
	}

	if msg == nil || buf.Error != nil {
		return nil
	}

	msg.Unmarshal(buf)
	if buf.Error != nil {
		return nil
	}

	return msg
}
