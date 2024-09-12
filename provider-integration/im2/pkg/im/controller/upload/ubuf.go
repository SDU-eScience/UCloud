package upload

import (
	"bytes"
	"encoding/binary"
)

// NOTE(Dan): This is a simple wrapper around a buffer providing convenient functions for dealing with binary messages
// written in big-endian byte order. Feel free to put this in some other package if it becomes more useful.

type ubuf struct {
	_buf *bytes.Buffer
	err  error
}

func (b *ubuf) Reset() {
	b._buf.Reset()
}

func (b *ubuf) IsEmpty() bool {
	return b.err != nil || b._buf.Available() == 0
}

func (b *ubuf) ReadU8() uint8 {
	next, err := b._buf.ReadByte()
	if err != nil {
		b.err = err
	}
	return next
}

func (b *ubuf) ReadU16() uint16 {
	var val uint16
	err := binary.Read(b._buf, binary.BigEndian, &val)
	if err != nil {
		b.err = err
	}
	return val
}

func (b *ubuf) ReadU32() uint32 {
	var val uint32
	err := binary.Read(b._buf, binary.BigEndian, &val)
	if err != nil {
		b.err = err
	}
	return val
}

func (b *ubuf) ReadU64() uint64 {
	var val uint64
	err := binary.Read(b._buf, binary.BigEndian, &val)
	if err != nil {
		b.err = err
	}
	return val
}

func (b *ubuf) ReadS8() int8 {
	next, err := b._buf.ReadByte()
	if err != nil {
		b.err = err
	}
	return int8(next)
}

func (b *ubuf) ReadS16() int16 {
	var val int16
	err := binary.Read(b._buf, binary.BigEndian, &val)
	if err != nil {
		b.err = err
	}
	return val
}

func (b *ubuf) ReadS32() int32 {
	var val int32
	err := binary.Read(b._buf, binary.BigEndian, &val)
	if err != nil {
		b.err = err
	}
	return val
}

func (b *ubuf) ReadS64() int64 {
	var val int64
	err := binary.Read(b._buf, binary.BigEndian, &val)
	if err != nil {
		b.err = err
	}
	return val
}

func (b *ubuf) ReadRemainingBytes() []byte {
	remaining := len(b._buf.Bytes())
	return b._buf.Next(remaining)
}

func (b *ubuf) ReadString() string {
	size := b.ReadU32()
	if size == 0 {
		return ""
	}

	output := make([]byte, size)
	next := b._buf.Next(int(size))
	copy(output, next)
	return string(output)
}

func (b *ubuf) WriteU8(val uint8) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.err = err
	}
}

func (b *ubuf) WriteU16(val uint16) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.err = err
	}
}

func (b *ubuf) WriteU32(val uint32) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.err = err
	}
}

func (b *ubuf) WriteU64(val uint64) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.err = err
	}
}

func (b *ubuf) WriteS8(val int8) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.err = err
	}
}

func (b *ubuf) WriteS16(val int16) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.err = err
	}
}

func (b *ubuf) WriteS32(val int32) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.err = err
	}
}

func (b *ubuf) WriteS64(val int64) {
	err := binary.Write(b._buf, binary.BigEndian, val)
	if err != nil {
		b.err = err
	}
}

func (b *ubuf) WriteString(val string) {
	valBytes := []byte(val)
	b.WriteU32(uint32(len(valBytes)))
	_, err := b._buf.Write(valBytes)
	if err != nil {
		b.err = err
	}
}

func (b *ubuf) WriteBytes(val []byte) {
	_, err := b._buf.Write(val)
	if err != nil {
		b.err = err
	}
}
