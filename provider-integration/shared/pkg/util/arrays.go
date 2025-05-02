package util

import "slices"

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

func RemoveAtIndex[T any](slice []T, index int) []T {
	return append(slice[:index], slice[index+1:]...)
}

func RemoveElementFunc[T any](slice []T, condition func(element T) bool) []T {
	idx := slices.IndexFunc(slice, condition)
	if idx == -1 {
		return slice
	} else {
		return RemoveAtIndex(slice, idx)
	}
}
