package util

var FalsePointer = (func() *bool {
	f := false
	return &f
})()

var TruePointer = (func() *bool {
	f := true
	return &f
})()

func BoolPointer(value bool) *bool {
	if value {
		return TruePointer
	} else {
		return FalsePointer
	}
}

func StringPointer(value string) *string {
	return &value
}

func UintPointer(value uint) *uint {
	return &value
}
