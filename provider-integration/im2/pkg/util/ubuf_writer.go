package util

import (
	"encoding/binary"
	"io"
)

// NOTE(Dan): This is a simple wrapper around a buffer providing convenient functions for dealing with binary messages
// written in big-endian byte order.

type UBufferWriter struct {
	_buf  io.Writer
	Error error
}

func NewBufferWithWriter(buf io.Writer) *UBufferWriter {
	return &UBufferWriter{_buf: buf}
}

func (b *UBufferWriter) WriteU8(val uint8) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.Error = err
	}
}

func (b *UBufferWriter) WriteU16(val uint16) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.Error = err
	}
}

func (b *UBufferWriter) WriteU32(val uint32) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.Error = err
	}
}

func (b *UBufferWriter) WriteU64(val uint64) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.Error = err
	}
}

func (b *UBufferWriter) WriteS8(val int8) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.Error = err
	}
}

func (b *UBufferWriter) WriteS16(val int16) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.Error = err
	}
}

func (b *UBufferWriter) WriteS32(val int32) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.Error = err
	}
}

func (b *UBufferWriter) WriteS64(val int64) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.Error = err
	}
}

func (b *UBufferWriter) WriteString(val string) {
	valBytes := []byte(val)
	b.WriteU32(uint32(len(valBytes)))
	_, err := b._buf.Write(valBytes)
	if err != nil {
		b.Error = err
	}
}

func (b *UBufferWriter) WriteBytes(val []byte) {
	_, err := b._buf.Write(val)
	if err != nil {
		b.Error = err
	}
}
