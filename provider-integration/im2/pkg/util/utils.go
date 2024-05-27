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

func RandomToken(byteCount int) string {
    bytes := make([]byte, byteCount)
    _, _ = rand.Read(bytes)
    return fmt.Sprintf("%v%v", time.Now().UnixNano(), hex.EncodeToString(bytes))
}
