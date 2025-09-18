package util

import (
	"crypto/sha256"
	"encoding/base64"
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

func Sha256(input []byte) string {
	h := sha256.New()
	_, err := h.Write(input)
	if err != nil {
		panic("hash fail: " + err.Error())
	}

	return base64.URLEncoding.EncodeToString(h.Sum(nil))
}
