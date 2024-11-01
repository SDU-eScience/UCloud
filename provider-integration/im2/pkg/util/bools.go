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
