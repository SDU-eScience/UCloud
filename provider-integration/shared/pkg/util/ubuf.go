package util

import (
	"bytes"
	"encoding/binary"
	"errors"
)

// NOTE(Dan): This is a simple wrapper around a buffer providing convenient functions for dealing with binary messages
// written in big-endian byte order.

type UBuffer struct {
	_buf  *bytes.Buffer
	Error error
}

func NewBufferBytes(buf []byte) *UBuffer {
	return &UBuffer{_buf: bytes.NewBuffer(buf)}
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

func (b *UBuffer) ReadNext(count int) []byte {
	output := make([]byte, count)
	next := b._buf.Next(count)
	copy(output, next)
	return output
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

func (b *UBuffer) WriteUvarint(val uint64) {
	var tmp [binary.MaxVarintLen64]byte
	n := binary.PutUvarint(tmp[:], val)
	b.WriteBytes(tmp[:n])
}

func (b *UBuffer) ReadUvarint() uint64 {
	var result uint64
	var shift uint
	for {
		next := b.ReadU8()
		if b.Error != nil {
			return 0
		}

		result |= uint64(next&0x7f) << shift
		if next&0x80 == 0 {
			return result
		}

		shift += 7
		if shift >= 64 {
			b.Error = errors.New("uvarint overflow")
			return 0
		}
	}
}

func (b *UBuffer) WriteStringVarint(val string) {
	valBytes := []byte(val)
	b.WriteUvarint(uint64(len(valBytes)))
	_, err := b._buf.Write(valBytes)
	if err != nil {
		b.Error = err
	}
}

func (b *UBuffer) ReadStringVarint() string {
	size := b.ReadUvarint()
	if b.Error != nil || size == 0 {
		return ""
	}

	output := make([]byte, size)
	next := b._buf.Next(int(size))
	copy(output, next)
	return string(output)
}

func (b *UBuffer) WriteBytes(val []byte) {
	_, err := b._buf.Write(val)
	if err != nil {
		b.Error = err
	}
}
