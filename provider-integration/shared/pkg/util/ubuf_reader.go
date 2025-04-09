package util

import (
	"encoding/binary"
	"io"
)

// NOTE(Dan): This is a simple wrapper around a buffer providing convenient functions for dealing with binary messages
// written in big-endian byte order.

type UBufferReader struct {
	_buf  io.Reader
	Error error
}

func NewBufferWithReader(buf io.Reader) *UBufferReader {
	return &UBufferReader{_buf: buf}
}

func (b *UBufferReader) ReadU8() uint8 {
	res := make([]byte, 1)
	_, err := b._buf.Read(res)
	if err != nil {
		b.Error = err
	}
	return res[0]
}

func (b *UBufferReader) ReadU16() uint16 {
	var val uint16
	err := binary.Read(b._buf, binary.BigEndian, &val)
	if err != nil {
		b.Error = err
	}
	return val
}

func (b *UBufferReader) ReadU32() uint32 {
	var val uint32
	err := binary.Read(b._buf, binary.BigEndian, &val)
	if err != nil {
		b.Error = err
	}
	return val
}

func (b *UBufferReader) ReadU64() uint64 {
	var val uint64
	err := binary.Read(b._buf, binary.BigEndian, &val)
	if err != nil {
		b.Error = err
	}
	return val
}

func (b *UBufferReader) ReadS8() int8 {
	return int8(b.ReadU8())
}

func (b *UBufferReader) ReadS16() int16 {
	var val int16
	err := binary.Read(b._buf, binary.BigEndian, &val)
	if err != nil {
		b.Error = err
	}
	return val
}

func (b *UBufferReader) ReadS32() int32 {
	var val int32
	err := binary.Read(b._buf, binary.BigEndian, &val)
	if err != nil {
		b.Error = err
	}
	return val
}

func (b *UBufferReader) ReadS64() int64 {
	var val int64
	err := binary.Read(b._buf, binary.BigEndian, &val)
	if err != nil {
		b.Error = err
	}
	return val
}

func (b *UBufferReader) ReadRemainingBytes() []byte {
	result, err := io.ReadAll(b._buf)
	if err != nil {
		b.Error = err
	}
	return result
}

func (b *UBufferReader) ReadString() string {
	size := b.ReadU32()
	if size == 0 {
		return ""
	}

	output := make([]byte, size)
	_, err := b._buf.Read(output)
	if err != nil {
		b.Error = err
	}
	return string(output)
}

func (b *UBufferReader) ReadNext(count int) []byte {
	output := make([]byte, count)
	_, err := b._buf.Read(output)
	if err != nil {
		b.Error = err
	}
	return output
}
