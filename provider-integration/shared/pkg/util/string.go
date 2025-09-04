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
