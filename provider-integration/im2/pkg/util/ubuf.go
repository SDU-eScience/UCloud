package util

import (
	"bytes"
	"encoding/binary"
)

// NOTE(Dan): This is a simple wrapper around a buffer providing convenient functions for dealing with binary messages
// written in big-endian byte order.

type UBuffer struct {
	_buf  *bytes.Buffer
	Error error
}

func NewBuffer(buf *bytes.Buffer) *UBuffer {
	return &UBuffer{_buf: buf}
}

func (b *UBuffer) Reset() {
	b._buf.Reset()
}

func (b *UBuffer) IsEmpty() bool {
	return b.Error != nil || b._buf.Len() == 0
}

func (b *UBuffer) ReadU8() uint8 {
	next, err := b._buf.ReadByte()
	if err != nil {
		b.Error = err
	}
	return next
}

func (b *UBuffer) ReadU16() uint16 {
	var val uint16
	err := binary.Read(b._buf, binary.BigEndian, &val)
	if err != nil {
		b.Error = err
	}
	return val
}

func (b *UBuffer) ReadU32() uint32 {
	var val uint32
	err := binary.Read(b._buf, binary.BigEndian, &val)
	if err != nil {
		b.Error = err
	}
	return val
}

func (b *UBuffer) ReadU64() uint64 {
	var val uint64
	err := binary.Read(b._buf, binary.BigEndian, &val)
	if err != nil {
		b.Error = err
	}
	return val
}

func (b *UBuffer) ReadS8() int8 {
	next, err := b._buf.ReadByte()
	if err != nil {
		b.Error = err
	}
	return int8(next)
}

func (b *UBuffer) ReadS16() int16 {
	var val int16
	err := binary.Read(b._buf, binary.BigEndian, &val)
	if err != nil {
		b.Error = err
	}
	return val
}

func (b *UBuffer) ReadS32() int32 {
	var val int32
	err := binary.Read(b._buf, binary.BigEndian, &val)
	if err != nil {
		b.Error = err
	}
	return val
}

func (b *UBuffer) ReadS64() int64 {
	var val int64
	err := binary.Read(b._buf, binary.BigEndian, &val)
	if err != nil {
		b.Error = err
	}
	return val
}

func (b *UBuffer) ReadRemainingBytes() []byte {
	remaining := len(b._buf.Bytes())
	return b._buf.Next(remaining)
}

func (b *UBuffer) ReadString() string {
	size := b.ReadU32()
	if size == 0 {
		return ""
	}

	output := make([]byte, size)
	next := b._buf.Next(int(size))
	copy(output, next)
	return string(output)
}

func (b *UBuffer) WriteU8(val uint8) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.Error = err
	}
}

func (b *UBuffer) WriteU16(val uint16) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.Error = err
	}
}

func (b *UBuffer) WriteU32(val uint32) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.Error = err
	}
}

func (b *UBuffer) WriteU64(val uint64) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.Error = err
	}
}

func (b *UBuffer) WriteS8(val int8) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.Error = err
	}
}

func (b *UBuffer) WriteS16(val int16) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.Error = err
	}
}

func (b *UBuffer) WriteS32(val int32) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.Error = err
	}
}

func (b *UBuffer) WriteS64(val int64) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.Error = err
	}
}

func (b *UBuffer) WriteString(val string) {
	valBytes := []byte(val)
	b.WriteU32(uint32(len(valBytes)))
	_, err := b._buf.Write(valBytes)
	if err != nil {
		b.Error = err
	}
}

func (b *UBuffer) WriteBytes(val []byte) {
	_, err := b._buf.Write(val)
	if err != nil {
		b.Error = err
	}
}
