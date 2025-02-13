package util

func ChunkBy[T any](items []T, chunkSize int) (chunks [][]T) {
	for chunkSize < len(items) {
		items, chunks = items[chunkSize:], append(chunks, items[0:chunkSize:chunkSize])
	}
	return append(chunks, items)
}

func GetOptionalElement[T any](items []T, index int) Option[T] {
	if index >= 0 && index < len(items) {
		return OptValue(items[index])
	} else {
		return OptNone[T]()
	}
}
