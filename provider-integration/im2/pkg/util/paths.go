package util

import (
	"path/filepath"
	"strings"
)

func Components(path string) []string {
	clean := strings.ReplaceAll(filepath.Clean(path), "\\", "/")
	clean, _ = strings.CutPrefix(clean, "/")
	clean, _ = strings.CutSuffix(clean, "/")
	return strings.Split(clean, "/")
}

func Parents(path string) []string {
	var result []string
	components := Components(path)
	builder := "/"
	for i := 0; i < len(components)-1; i++ {
		builder += components[i]

		result = append(result, builder)

		if builder != "/" {
			builder += "/"
		}
	}
	return result
}

func Parent(path string) string {
	return filepath.Dir(path)
}

func FileName(path string) string {
	return filepath.Base(path)
}
