package util

import (
	"bytes"
	"context"
	"encoding/binary"
	"errors"
	"fmt"
	"hash/crc32"
	"io"
	"os"
	"time"

	"golang.org/x/sys/unix"
)

// Introduction and types
// =====================================================================================================================
// This file implements a file-backed ring buffer of a fixed capacity. The intended purpose of this data structure is
// for communication between processes when we can only rely on the distributed file-system as a communication
// mechanism. This is, in particular, useful when attempting to communicate with user-jobs as we do not want to rely
// on sockets for this.
//
// If possible, you should always use a socket or shared memory. Do not use this data structure as a first resort since
// it is likely slower than any other alternative.
//
// The data-structure has the following goals:
//
// - Store all state in a single file on a distributed file system.
// - Implement a ring buffer with a fixed capacity. This will overwrite the oldest data when it is full.
// - Low-latency follow (as much as possible, still higher than alternatives).
// - Crash-safe and resilience against partial writes.
//
// The file uses the following layout:
//
// +------------------+ 0
// |                  |
// | Superblock       | 4 KiB (page-aligned)
// |                  |
// +------------------+
// |                  |
// | Index[Slots]     | Slots * 8 bytes (u64 seq per slot)
// |                  |
// +------------------+
// |                  |
// | Data Ring        | Slots * SlotSize bytes
// |                  |
// +------------------+ EOF
//
// The super block is described in the types below.

var (
	ringMagic  = [4]byte{'R', 'I', 'N', 'G'}
	ringCrcTab = crc32.MakeTable(crc32.Castagnoli)
)

type ringSuperblock struct {
	Magic    [4]byte
	Version  uint32
	SlotSize uint32
	Slots    uint32
	Created  int64
	_pad     [4096 - 4 - 4 - 4 - 4 - 8]byte
}

const (
	ringSuperSize    = 4096
	FsRingHeaderSize = 16
)

// Writer implementation
// =====================================================================================================================

type FsRingSerializer[T any] struct {
	Serialize   func(item T, buf *UBufferWriter)
	Deserialize func(buf *UBufferReader) T
}

type FsRingWriter[T any] struct {
	f          *os.File
	slots      uint64
	slotSize   uint32
	indexBase  int64
	dataBase   int64
	nextSeq    uint64
	scratch    []byte
	serializer FsRingSerializer[T]
}

// FsRingCreate initializes a new ring file at path with the given geometry.
// `slots` should be a power of two; `slotSize` >= FsRingHeaderSize + worst-case payload.
func FsRingCreate[T any](
	path string,
	slots int,
	slotSize int,
	serializer FsRingSerializer[T],
) (*FsRingWriter[T], error) {
	if slots <= 0 || (slots&(slots-1)) != 0 {
		return nil, errors.New("slots must be power of two > 0")
	}
	if slotSize < FsRingHeaderSize+64 {
		return nil, errors.New("slot too small")
	}

	fd, err := unix.Open(path, unix.O_CREAT|unix.O_RDWR, 0644)
	if err != nil {
		return nil, err
	}
	f := os.NewFile(uintptr(fd), path)

	sb := ringSuperblock{
		Magic:    ringMagic,
		Version:  1,
		SlotSize: uint32(slotSize),
		Slots:    uint32(slots),
		Created:  time.Now().Unix(),
	}
	indexBytes := int64(slots) * 8
	dataBase := int64(ringSuperSize) + indexBytes
	total := dataBase + int64(slots*slotSize)

	err2 := fsRingPrepareFile(fd, total, f)
	if err2 != nil {
		return nil, err2
	}

	// Write ringSuperblock
	buf := make([]byte, ringSuperSize)
	copy(buf[0:4], sb.Magic[:])
	binary.BigEndian.PutUint32(buf[4:8], sb.Version)
	binary.BigEndian.PutUint32(buf[8:12], sb.SlotSize)
	binary.BigEndian.PutUint32(buf[12:16], sb.Slots)
	binary.BigEndian.PutUint64(buf[24:32], uint64(sb.Created))
	if _, err := f.WriteAt(buf, 0); err != nil {
		_ = f.Close()
		return nil, err
	}
	if err := f.Sync(); err != nil {
		_ = f.Close()
		return nil, err
	}

	return &FsRingWriter[T]{
		f:          f,
		slots:      uint64(slots),
		slotSize:   uint32(slotSize),
		indexBase:  ringSuperSize,
		dataBase:   dataBase,
		nextSeq:    1,
		scratch:    make([]byte, slotSize-FsRingHeaderSize),
		serializer: serializer,
	}, nil
}

// Write appends one record to the ring.
func (w *FsRingWriter[T]) Write(item T) error {
	buf := bytes.NewBuffer(w.scratch[:0])
	bufWriter := NewBufferWithWriter(buf)
	w.serializer.Serialize(item, bufWriter)

	payload := buf.Bytes()
	if len(payload) > int(w.slotSize-FsRingHeaderSize) {
		return errors.New(fmt.Sprintf("payload too large for slot %v > %v", len(payload), w.slotSize-FsRingHeaderSize))
	}

	seq := w.nextSeq
	slot := seq & (w.slots - 1)
	dataOff := w.dataBase + int64(slot)*int64(w.slotSize)

	// Prepare header
	hdr := make([]byte, FsRingHeaderSize)
	binary.BigEndian.PutUint64(hdr[0:8], seq)
	binary.BigEndian.PutUint32(hdr[8:12], uint32(len(payload)))
	crc := crc32.Checksum(payload, ringCrcTab)
	binary.BigEndian.PutUint32(hdr[12:16], crc)

	// Commit the message, by going through following steps:
	// 1. Write payload
	if _, err := w.f.WriteAt(payload, dataOff+FsRingHeaderSize); err != nil {
		return err
	}

	// 2. Write header
	if _, err := w.f.WriteAt(hdr, dataOff); err != nil {
		return err
	}

	// 3. Invoke sync
	if err := w.f.Sync(); err != nil {
		return err
	}

	// 4. Update index
	idxOff := w.indexBase + int64(slot)*8
	idx := make([]byte, 8)
	binary.BigEndian.PutUint64(idx, seq)
	if _, err := w.f.WriteAt(idx, idxOff); err != nil {
		return err
	}

	// 5. Invoke sync
	if err := w.f.Sync(); err != nil {
		return err
	}

	w.nextSeq++
	return nil
}

func (w *FsRingWriter[T]) Close() error {
	return w.f.Close()
}

// Reader implementation
// =====================================================================================================================

type FsRingReader[T any] struct {
	f          *os.File
	slots      uint64
	slotSize   uint32
	indexBase  int64
	dataBase   int64
	nextSeq    uint64
	serializer FsRingSerializer[T]
	cancelFn   func()
}

// FsRingOpen opens an existing ring and positions nextSeq to follow new data.
func FsRingOpen[T any](path string, serializer FsRingSerializer[T]) (*FsRingReader[T], error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}

	// Superblock
	sb := make([]byte, ringSuperSize)
	if _, err := io.ReadFull(io.NewSectionReader(f, 0, ringSuperSize), sb); err != nil {
		_ = f.Close()
		return nil, err
	}
	if string(sb[0:4]) != "RING" {
		_ = f.Close()
		return nil, errors.New("bad ringMagic")
	}
	slotSize := binary.BigEndian.Uint32(sb[8:12])
	slots := binary.BigEndian.Uint32(sb[12:16])

	indexBase := int64(ringSuperSize)
	dataBase := int64(ringSuperSize) + int64(slots)*8

	r := &FsRingReader[T]{
		f:          f,
		slots:      uint64(slots),
		slotSize:   slotSize,
		indexBase:  indexBase,
		dataBase:   dataBase,
		serializer: serializer,
	}

	// Bootstrap nextSeq by scanning index for max
	var maxSeq uint64
	buf := make([]byte, 8)
	for i := uint64(0); i < r.slots; i++ {
		if _, err := f.ReadAt(buf, indexBase+int64(i*8)); err == nil {
			s := binary.BigEndian.Uint64(buf)
			if s > maxSeq {
				maxSeq = s
			}
		}
	}
	if maxSeq == 0 {
		r.nextSeq = 1
	} else {
		r.nextSeq = maxSeq + 1
	}

	return r, nil
}

// Follow blocks, emitting items to `out` in order.
// If startFromTail > 0, it starts N records back from the tail (if available).
func (r *FsRingReader[T]) Follow(ctx context.Context, out chan<- T, startFromTail int) error {
	realCtx, cancel := context.WithCancel(ctx)
	defer cancel()
	r.cancelFn = cancel

	if startFromTail > 0 {
		if r.nextSeq > uint64(startFromTail) {
			r.nextSeq -= uint64(startFromTail)
		} else {
			r.nextSeq = 1
		}
	}

	hdr := make([]byte, FsRingHeaderSize)
	payload := make([]byte, r.slotSize-FsRingHeaderSize)
	idx := make([]byte, 8)

	for {
		select {
		case <-realCtx.Done():
			return realCtx.Err()
		default:
		}

		seq := r.nextSeq
		slot := seq & (r.slots - 1)
		idxOff := r.indexBase + int64(slot)*8

		if _, err := r.f.ReadAt(idx, idxOff); err != nil {
			time.Sleep(5 * time.Millisecond)
			continue
		}
		cur := binary.BigEndian.Uint64(idx)

		if cur == 0 || cur < seq {
			// Not yet written. Wait and retry.
			time.Sleep(2 * time.Millisecond)
			continue
		}
		if cur > seq {
			// Overrun: writer lapped us. Jump forward.
			r.nextSeq = cur
			continue
		}

		dataOff := r.dataBase + int64(slot)*int64(r.slotSize)

		if _, err := r.f.ReadAt(hdr, dataOff); err != nil {
			time.Sleep(2 * time.Millisecond)
			continue
		}
		hseq := binary.BigEndian.Uint64(hdr[0:8])
		hlen := binary.BigEndian.Uint32(hdr[8:12])
		hcrc := binary.BigEndian.Uint32(hdr[12:16])

		if hseq != seq || hlen > uint32(len(payload)) {
			time.Sleep(2 * time.Millisecond)
			continue
		}
		if _, err := r.f.ReadAt(payload[:hlen], dataOff+FsRingHeaderSize); err != nil {
			time.Sleep(2 * time.Millisecond)
			continue
		}
		if crc32.Checksum(payload[:hlen], ringCrcTab) != hcrc {
			// Likely stale read. Retry.
			time.Sleep(2 * time.Millisecond)
			continue
		}

		reader := NewBufferWithReader(bytes.NewBuffer(payload))
		item := r.serializer.Deserialize(reader)

		select {
		case out <- item:
			r.nextSeq++
		case <-ctx.Done():
			return ctx.Err()
		}
	}
}

func (r *FsRingReader[T]) Close() error {
	err := r.f.Close()
	r.cancelFn()
	return err
}
