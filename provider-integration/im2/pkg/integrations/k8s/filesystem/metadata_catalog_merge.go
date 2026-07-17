package filesystem

import (
	"bufio"
	"bytes"
	"container/heap"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"

	"github.com/cockroachdb/pebble/v2"
	"github.com/cockroachdb/pebble/v2/objstorage/objstorageprovider"
	"github.com/cockroachdb/pebble/v2/sstable"
	"github.com/cockroachdb/pebble/v2/vfs"
	"ucloud.dk/shared/pkg/log"
)

type metadataRunReader struct {
	file   *os.File
	reader *bufio.Reader
	record metadataRecord
	run    int
}

type metadataRunHeap []*metadataRunReader

func (h metadataRunHeap) Len() int { return len(h) }
func (h metadataRunHeap) Less(i, j int) bool {
	return bytes.Compare(h[i].record.key, h[j].record.key) < 0
}
func (h metadataRunHeap) Swap(i, j int)   { h[i], h[j] = h[j], h[i] }
func (h *metadataRunHeap) Push(value any) { *h = append(*h, value.(*metadataRunReader)) }
func (h *metadataRunHeap) Pop() any {
	old := *h
	last := old[len(old)-1]
	*h = old[:len(old)-1]
	return last
}

func metadataMergeRunsToSSTables(runs []string, tempDir string, entriesPerTable int) ([]string, error) {
	const maxMergeFanIn = 64
	for pass := 0; len(runs) > maxMergeFanIn; pass++ {
		next := make([]string, 0, (len(runs)+maxMergeFanIn-1)/maxMergeFanIn)
		for start := 0; start < len(runs); start += maxMergeFanIn {
			end := min(start+maxMergeFanIn, len(runs))
			path := filepath.Join(tempDir, fmt.Sprintf("merge-%03d-%06d", pass, len(next)))
			if err := metadataMergeRunBatch(runs[start:end], path); err != nil {
				return nil, err
			}
			next = append(next, path)
		}
		for _, path := range runs {
			if err := os.Remove(path); err != nil {
				return nil, err
			}
		}
		runs = next
	}

	readers := make([]*metadataRunReader, 0, len(runs))
	defer func() {
		for _, reader := range readers {
			_ = reader.file.Close()
		}
	}()
	queue := metadataRunHeap{}
	for index, path := range runs {
		file, err := os.Open(path)
		if err != nil {
			return nil, err
		}
		reader := &metadataRunReader{file: file, reader: bufio.NewReaderSize(file, 256*1024), run: index}
		readers = append(readers, reader)
		reader.record, err = metadataReadRecord(reader.reader)
		if err != nil {
			return nil, fmt.Errorf("read metadata run %d: %w", index, err)
		}
		heap.Push(&queue, reader)
	}

	var tableWriter *sstable.Writer
	tableEntries := 0
	tableNumber := 0
	result := []string{}
	closeTable := func() error {
		if tableWriter == nil {
			return nil
		}
		err := tableWriter.Close()
		tableWriter = nil
		tableEntries = 0
		return err
	}
	defer func() {
		if tableWriter != nil {
			_ = tableWriter.Close()
		}
	}()

	var previousKey []byte
	for queue.Len() > 0 {
		reader := heap.Pop(&queue).(*metadataRunReader)
		record := reader.record
		if previousKey != nil && bytes.Equal(previousKey, record.key) {
			return nil, fmt.Errorf("duplicate PATH key %x in completed scan", record.key)
		}
		if tableWriter == nil {
			path := filepath.Join(tempDir, fmt.Sprintf("scan-%06d.sst", tableNumber))
			tableNumber++
			file, err := vfs.Default.Create(path, vfs.WriteCategoryUnspecified)
			if err != nil {
				return nil, err
			}
			tableWriter = sstable.NewWriter(objstorageprovider.NewFileWritable(file), sstable.WriterOptions{TableFormat: sstable.TableFormatPebblev4})
			result = append(result, path)
		}
		if err := tableWriter.Set(record.key, record.value); err != nil {
			return nil, err
		}
		previousKey = append(previousKey[:0], record.key...)
		tableEntries++
		if tableEntries >= entriesPerTable {
			if err := closeTable(); err != nil {
				return nil, err
			}
		}

		next, err := metadataReadRecord(reader.reader)
		if err == nil {
			reader.record = next
			heap.Push(&queue, reader)
		} else if !errors.Is(err, io.EOF) {
			return nil, fmt.Errorf("read metadata run %d: %w", reader.run, err)
		}
	}
	if err := closeTable(); err != nil {
		return nil, err
	}
	return result, nil
}

func metadataMergeRunBatch(paths []string, outputPath string) error {
	readers := make([]*metadataRunReader, 0, len(paths))
	defer func() {
		for _, reader := range readers {
			_ = reader.file.Close()
		}
	}()
	queue := metadataRunHeap{}
	for index, path := range paths {
		file, err := os.Open(path)
		if err != nil {
			return err
		}
		reader := &metadataRunReader{file: file, reader: bufio.NewReaderSize(file, 256*1024), run: index}
		readers = append(readers, reader)
		reader.record, err = metadataReadRecord(reader.reader)
		if err != nil {
			return err
		}
		heap.Push(&queue, reader)
	}

	file, err := os.OpenFile(outputPath, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
	if err != nil {
		return err
	}
	writer := bufio.NewWriterSize(file, 256*1024)
	var previousKey []byte
	for queue.Len() > 0 {
		reader := heap.Pop(&queue).(*metadataRunReader)
		if previousKey != nil && bytes.Equal(previousKey, reader.record.key) {
			_ = file.Close()
			return fmt.Errorf("duplicate PATH key %x in completed scan", reader.record.key)
		}
		if err = metadataWriteRecord(writer, reader.record); err != nil {
			_ = file.Close()
			return err
		}
		previousKey = append(previousKey[:0], reader.record.key...)
		next, readErr := metadataReadRecord(reader.reader)
		if readErr == nil {
			reader.record = next
			heap.Push(&queue, reader)
		} else if !errors.Is(readErr, io.EOF) {
			_ = file.Close()
			return readErr
		}
	}
	if err = writer.Flush(); err == nil {
		err = file.Sync()
	}
	closeErr := file.Close()
	if err == nil {
		err = closeErr
	}
	return err
}

func metadataSpoolOldNameKeys(db *pebble.DB, span pebble.KeyRange, path string) error {
	file, err := os.OpenFile(path, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
	if err != nil {
		return err
	}
	writer := bufio.NewWriterSize(file, 256*1024)
	iter, err := db.NewIter(&pebble.IterOptions{LowerBound: span.Start, UpperBound: span.End})
	if err != nil {
		_ = file.Close()
		return err
	}
	for valid := iter.First(); valid; valid = iter.Next() {
		indexKeys, keyErr := metadataSearchIndexKeys(iter.Key())
		if keyErr != nil {
			_ = iter.Close()
			_ = file.Close()
			return keyErr
		}
		for _, indexKey := range indexKeys {
			var length [10]byte
			n := binary.PutUvarint(length[:], uint64(len(indexKey)))
			if _, err = writer.Write(length[:n]); err == nil {
				_, err = writer.Write(indexKey)
			}
			if err != nil {
				_ = iter.Close()
				_ = file.Close()
				return err
			}
		}
	}
	if err = iter.Error(); err == nil {
		err = iter.Close()
	} else {
		_ = iter.Close()
	}
	if err == nil {
		err = writer.Flush()
	}
	if err == nil {
		err = file.Sync()
	}
	closeErr := file.Close()
	if err == nil {
		err = closeErr
	}
	return err
}

func metadataRefreshNameIndex(db *pebble.DB, span pebble.KeyRange, oldNamesPath string) error {
	batch := db.NewBatch()
	defer batch.Close()
	commitBatch := func() error {
		if batch.Empty() {
			return nil
		}
		if err := batch.Commit(pebble.Sync); err != nil {
			return err
		}
		batch.Reset()
		return nil
	}

	oldNames, err := os.Open(oldNamesPath)
	if err != nil {
		return err
	}
	reader := bufio.NewReaderSize(oldNames, 256*1024)
	for count := 0; ; count++ {
		length, readErr := binary.ReadUvarint(reader)
		if errors.Is(readErr, io.EOF) {
			break
		}
		if readErr != nil {
			_ = oldNames.Close()
			return readErr
		}
		key := make([]byte, int(length))
		if _, readErr = io.ReadFull(reader, key); readErr != nil {
			_ = oldNames.Close()
			return readErr
		}
		if err = batch.Delete(key, nil); err != nil {
			_ = oldNames.Close()
			return err
		}
		if count%10_000 == 9_999 {
			if err = commitBatch(); err != nil {
				_ = oldNames.Close()
				return err
			}
		}
	}
	if err = oldNames.Close(); err != nil {
		return err
	}
	if err = commitBatch(); err != nil {
		return err
	}

	iter, err := db.NewIter(&pebble.IterOptions{LowerBound: span.Start, UpperBound: span.End})
	if err != nil {
		return err
	}
	count := 0
	for valid := iter.First(); valid; valid = iter.Next() {
		indexKeys, keyErr := metadataSearchIndexKeys(iter.Key())
		if keyErr != nil {
			_ = iter.Close()
			return keyErr
		}
		for _, indexKey := range indexKeys {
			if err = batch.Set(indexKey, nil, nil); err != nil {
				_ = iter.Close()
				return err
			}
			count++
			if count%10_000 == 0 {
				if err = commitBatch(); err != nil {
					_ = iter.Close()
					return err
				}
			}
		}
	}
	if err = iter.Error(); err == nil {
		err = iter.Close()
	} else {
		_ = iter.Close()
	}
	if err != nil {
		return err
	}
	return commitBatch()
}

func metadataRecoverPendingNameIndex(db *pebble.DB) error {
	value, closer, err := db.Get(metadataPendingNameKey)
	if errors.Is(err, pebble.ErrNotFound) {
		return nil
	}
	if err != nil {
		return err
	}
	rootKey := append([]byte(nil), value...)
	_ = closer.Close()
	if len(rootKey) == 0 || rootKey[0] != MetaKeyspacePath {
		return errors.New("invalid pending NAME refresh root")
	}
	span := pebble.KeyRange{Start: rootKey, End: metadataPrefixSuccessor(rootKey)}
	batch := db.NewBatch()
	defer batch.Close()
	iter, err := db.NewIter(&pebble.IterOptions{LowerBound: span.Start, UpperBound: span.End})
	if err != nil {
		return err
	}
	count := 0
	for valid := iter.First(); valid; valid = iter.Next() {
		indexKeys, keyErr := metadataSearchIndexKeys(iter.Key())
		if keyErr != nil {
			_ = iter.Close()
			return keyErr
		}
		for _, indexKey := range indexKeys {
			if err = batch.Set(indexKey, nil, nil); err != nil {
				_ = iter.Close()
				return err
			}
			count++
			if count%10_000 == 0 {
				if err = batch.Commit(pebble.Sync); err != nil {
					_ = iter.Close()
					return err
				}
				batch.Reset()
			}
		}
	}
	if err = iter.Error(); err == nil {
		err = iter.Close()
	} else {
		_ = iter.Close()
	}
	if err != nil {
		return err
	}
	if len(rootKey) == 1 {
		if err = batch.Set(metadataSearchIndexVersionKey, metadataSearchIndexVersion, nil); err != nil {
			return err
		}
	}
	if err = batch.Delete(metadataPendingNameKey, nil); err != nil {
		return err
	}
	return batch.Commit(pebble.Sync)
}

func metadataRefreshNameIndexAsync(databasePath, driveID string, span pebble.KeyRange, oldNamesPath, tempDir, scanPath string) {
	defer func() {
		if value, ok := metadataNameRefreshes.LoadAndDelete(databasePath); ok {
			close(value.(chan struct{}))
		}
	}()
	defer os.RemoveAll(tempDir)
	db, releaseDatabase, err := metadataAcquireDatabase(databasePath, false)
	if err != nil {
		log.Warn("Metadata NAME refresh for %s failed: %s", scanPath, err)
		metadataRecordNameRefreshFinished(driveID, err)
		return
	}
	defer releaseDatabase()
	if err = metadataRefreshNameIndex(db, span, oldNamesPath); err == nil {
		batch := db.NewBatch()
		if len(span.Start) == 1 {
			err = batch.Set(metadataSearchIndexVersionKey, metadataSearchIndexVersion, nil)
		}
		if err == nil {
			err = batch.Delete(metadataPendingNameKey, nil)
		}
		if err == nil {
			err = batch.Commit(pebble.Sync)
		}
		_ = batch.Close()
	}
	if err != nil {
		// PATH is authoritative and already published. Search can verify and prune stale NAME hits.
		log.Warn("Metadata NAME refresh for %s failed: %s", scanPath, err)
		metadataRecordNameRefreshFinished(driveID, err)
		return
	}
	pathBytes, _ := db.EstimateDiskUsage([]byte{MetaKeyspacePath}, []byte{MetaKeyspaceName})
	nameBytes, _ := db.EstimateDiskUsage([]byte{MetaKeyspaceName}, []byte{MetaKeyspaceTrigram + 1})
	metadataRecordDatabaseSize(driveID, db.Metrics().DiskSpaceUsage(), pathBytes, nameBytes)
	metadataRecordNameRefreshFinished(driveID, nil)
}
