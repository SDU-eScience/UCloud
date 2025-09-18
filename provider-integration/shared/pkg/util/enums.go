package util

import "slices"

func VerifyEnum[T comparable](value T, options []T) (T, bool) {
	if slices.Contains(options, value) {
		return value, true
	} else {
		return value, false
	}
}

func EnumOrDefault[T comparable](value T, options []T, defaultValue T) T {
	value, ok := VerifyEnum(value, options)
	if !ok {
		return defaultValue
	} else {
		return value
	}
}
