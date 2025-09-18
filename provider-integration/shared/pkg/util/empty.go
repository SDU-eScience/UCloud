package util

type Empty struct{}

var EmptyValue = Empty{}

type EmptyMarker interface {
	__EmptyMarkerFunc()
}

func (e *Empty) __EmptyMarkerFunc() {}
