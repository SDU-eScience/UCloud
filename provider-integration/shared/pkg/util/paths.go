package util

import (
	"os"
	"path/filepath"
	"strings"
)

func ReadFileTrimmed(path string) (string, bool) {
	data, err := os.ReadFile(path)
	if err != nil {
		return "", false
	}

	result := strings.TrimSpace(string(data))
	if result == "" {
		return "", false
	}
	return result, true
}

func Components(path string) []string {
	clean := filepath.Clean(path)
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
	allParents := Parents(path)
	if len(allParents) == 0 {
		return path
	}
	return allParents[len(allParents)-1]
}

func FileName(path string) string {
	lastIdx := strings.LastIndex(path, "/")
	if lastIdx == -1 {
		return path
	}
	return path[lastIdx+1:]
}
