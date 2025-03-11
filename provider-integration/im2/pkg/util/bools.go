package util

func Pointer[T any](value T) *T {
	return &value
}

func BoolPointer(value bool) *bool {
	return &value
}

func StringPointer(value string) *string {
	return &value
}

func UintPointer(value uint) *uint {
	return &value
}
