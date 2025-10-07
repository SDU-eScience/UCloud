package util

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"io"
	"time"
)

func SilentClose(closer io.Closer) {
	if closer != nil {
		_ = closer.Close()
	}
}

func SilentCloseIfOk(closer io.Closer, err error) {
	if err == nil && closer != nil {
		_ = closer.Close()
	}
}

func RandomToken(byteCount int) string {
	bytes := make([]byte, byteCount)
	_, _ = rand.Read(bytes)
	return fmt.Sprintf("%v%v", time.Now().UnixNano(), hex.EncodeToString(bytes))
}

func RandomTokenNoTs(byteCount int) string {
	bytes := make([]byte, byteCount)
	_, _ = rand.Read(bytes)
	return hex.EncodeToString(bytes)
}

func SecureToken() string {
	return RandomTokenNoTs(32)
}
