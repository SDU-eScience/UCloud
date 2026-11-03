package util

import "strings"

func ChunkString(input string, chunkSize int) string {
	var builder strings.Builder
	for i, c := range input {
		if i != 0 && i%chunkSize == 0 {
			builder.WriteString("\n")
		}
		builder.WriteRune(c)
	}
	return builder.String()
}

func Utf8Truncate(value string, byteLength int) string {
	if byteLength <= 0 {
		return ""
	}
	if len(value) <= byteLength {
		return value
	}

	cut := byteLength
	for cut > 0 {
		if (value[cut] & 0xC0) != 0x80 {
			break
		}
		cut--
	}
	return value[:cut]
}
