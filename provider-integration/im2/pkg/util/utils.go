package util

import "io"

func SilentClose(closer io.Closer) {
    if closer != nil {
        _ = closer.Close()
    }
}
