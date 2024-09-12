package upload

type messageType uint8

const (
	messageTypeOk        messageType = 0
	messageTypeChunk     messageType = 2
	messageTypeSkip      messageType = 3
	messageTypeListing   messageType = 4
	messageTypeCompleted messageType = 5

	// checksum = 1 has not yet been implemented
)

type message interface {
	// Unmarshal will read the binary version of this message from the buffer and into itself. This function _will not_
	// consume the opcode from the buffer, but instead assume that it has already been consumed.
	Unmarshal(buffer *ubuf)

	// Marshal will produce the binary version of this message and put it in the supplied buffer. This function _will_
	// put the opcode as the first byte in the buffer, but only if writeOpCode is true.
	Marshal(buf *ubuf, writeOpCode bool)
}

type messageOk struct {
	FileId int32
}

func (m *messageOk) Unmarshal(buf *ubuf) {
	m.FileId = buf.ReadS32()
}

func (m *messageOk) Marshal(buf *ubuf, writeOpCode bool) {
	if writeOpCode {
		buf.WriteU8(uint8(messageTypeOk))
	}
	buf.WriteS32(m.FileId)
}

type messageChunk struct {
	FileId int32
	Data   []byte
}

func (m *messageChunk) Unmarshal(buf *ubuf) {
	m.FileId = buf.ReadS32()
	m.Data = buf.ReadRemainingBytes()
}

func (m *messageChunk) Marshal(buf *ubuf, writeOpCode bool) {
	if writeOpCode {
		buf.WriteU8(uint8(messageTypeChunk))
	}
	buf.WriteS32(m.FileId)
	buf.WriteBytes(m.Data)
}

type messageSkip struct {
	FileId int32
}

func (m *messageSkip) Unmarshal(buf *ubuf) {
	m.FileId = buf.ReadS32()
}

func (m *messageSkip) Marshal(buf *ubuf, writeOpCode bool) {
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

func (m *messageListing) Unmarshal(buf *ubuf) {
	m.FileId = buf.ReadS32()
	m.Size = buf.ReadS64()
	m.ModifiedAt = buf.ReadS64()
	m.Path = buf.ReadString()
}

func (m *messageListing) Marshal(buf *ubuf, writeOpCode bool) {
	if writeOpCode {
		buf.WriteU8(uint8(messageTypeListing))
	}
	buf.WriteU8(uint8(messageTypeListing))
	buf.WriteS32(m.FileId)
	buf.WriteS64(m.Size)
	buf.WriteS64(m.ModifiedAt)
	buf.WriteString(m.Path)
}

type messageCompleted struct {
	NumberOfProcessedFiles int32
}

func (m *messageCompleted) Unmarshal(buf *ubuf) {
	buf.WriteS32(m.NumberOfProcessedFiles)
}

func (m *messageCompleted) Marshal(buf *ubuf, writeOpCode bool) {
	if writeOpCode {
		buf.WriteU8(uint8(messageTypeCompleted))
	}
	buf.WriteS32(m.NumberOfProcessedFiles)
}

func parseMessage(t messageType, buf *ubuf) message {
	if buf.err != nil {
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
	}

	if msg == nil || buf.err != nil {
		return nil
	}

	msg.Unmarshal(buf)
	if buf.err != nil {
		return nil
	}

	return msg
}
