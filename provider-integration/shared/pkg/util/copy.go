package util

import (
	"bytes"
	"encoding/gob"
)

func SlowDeepCopy(source, dest any) {
	buf := bytes.Buffer{}
	if err := gob.NewEncoder(&buf).Encode(source); err != nil {
		panic("SlowDeepCopy was given a type which cannot be deep copied using this method")
	}
	_ = gob.NewDecoder(&buf).Decode(dest)
}
