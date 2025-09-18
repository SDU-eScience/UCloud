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

func AppendUnique[T comparable](slice []T, element T) []T {
	if !slices.Contains(slice, element) {
		return append(slice, element)
	} else {
		return slice
	}
}

func RemoveAtIndex[T any](slice []T, index int) []T {
	return append(slice[:index], slice[index+1:]...)
}

func RemoveFirst[T comparable](slice []T, element T) []T {
	idx := slices.Index(slice, element)
	if idx != -1 {
		return RemoveAtIndex(slice, idx)
	} else {
		return slice
	}
}

func RemoveElementFunc[T any](slice []T, condition func(element T) bool) []T {
	idx := slices.IndexFunc(slice, condition)
	if idx == -1 {
		return slice
	} else {
		return RemoveAtIndex(slice, idx)
	}
}

func NonNilSlice[T any](slice []T) []T {
	if slice == nil {
		return []T{}
	} else {
		return slice
	}
}

func Combined[T any](slices ...[]T) []T {
	totalLen := 0
	for _, s := range slices {
		totalLen += len(s)
	}

	result := make([]T, totalLen)
	pos := 0
	for _, s := range slices {
		n := copy(result[pos:], s)
		pos += n
	}
	return result
}
