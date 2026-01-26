package util

import (
	"slices"
	"sort"

	"golang.org/x/exp/constraints"
)

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

func PopHead[T any](slice []T) (T, []T) {
	if len(slice) == 0 {
		var def T
		return def, nil
	} else {
		return slice[0], slice[1:]
	}
}

func SampleElements[T any](slice []T, stepSize float64) []T {
	var result []T
	acc := 0.0

	for i := 0; i < len(slice); i++ {
		acc += 1
		if acc >= stepSize {
			result = append(result, slice[i])
			acc -= stepSize
		}
	}

	return result
}

func TopNKeys[K comparable, V constraints.Ordered](m map[K]V, n int) []K {
	type kv struct {
		k K
		v V
	}

	pairs := make([]kv, 0, len(m))
	for k, v := range m {
		pairs = append(pairs, kv{k, v})
	}

	sort.Slice(pairs, func(i, j int) bool {
		return pairs[i].v > pairs[j].v
	})

	if n > len(pairs) {
		n = len(pairs)
	}
	out := make([]K, 0, n)
	for i := 0; i < n; i++ {
		out = append(out, pairs[i].k)
	}
	return out
}

func GroupBy[T any, K comparable](slice []T, groupKeyFn func(element T) K) map[K][]T {
	result := map[K][]T{}
	for _, element := range slice {
		key := groupKeyFn(element)
		result[key] = append(result[key], element)
	}
	return result
}
