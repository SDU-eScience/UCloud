package util

import (
	"fmt"
	"hash/fnv"
)

func NonCryptographicHash(key any) int {
	h := fnv.New32a()
	_, err := h.Write([]byte(fmt.Sprint(key)))
	if err != nil {
		panic("hash fail: " + err.Error())
	}
	return int(h.Sum32())
}
