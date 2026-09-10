package shared

import (
	"strings"

	anyascii "github.com/anyascii/go"
)

func RepositoryProjectName(title string) string {
	transliterated := strings.ToLower(anyascii.Transliterate(title))
	var result strings.Builder
	separator := false
	for _, r := range transliterated {
		if r >= 'a' && r <= 'z' || r >= '0' && r <= '9' {
			if separator && result.Len() > 0 {
				result.WriteByte('-')
			}
			result.WriteRune(r)
			separator = false
		} else {
			separator = true
		}
	}
	name := strings.Trim(result.String(), "-")
	if name == "" {
		name = "project"
	}
	if len(name) > 32 {
		name = strings.TrimRight(name[:32], "-")
	}
	return name
}
