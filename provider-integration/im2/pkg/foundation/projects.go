package foundation

import (
	anyascii "github.com/anyascii/go"
	"regexp"
	"strings"
	"unicode"
)

var duplicateUnderscores = regexp.MustCompile("_+")

func GenerateProjectName(ucloudTitle string) string {
	cleanedTitle := anyascii.Transliterate(ucloudTitle)
	cleanedTitle = strings.ToLower(cleanedTitle)
	cleanedTitle = strings.ReplaceAll(cleanedTitle, " ", "_")
	cleanedTitle = strings.ToLower(cleaningRegex.ReplaceAllString(cleanedTitle, ""))
	cleanedTitle = duplicateUnderscores.ReplaceAllString(cleanedTitle, "_")
	cleanedTitle = cleanedTitle[:min(len(cleanedTitle), 28)]
	cleanedTitle = strings.TrimSuffix(cleanedTitle, "_")
	cleanedTitle = strings.TrimPrefix(cleanedTitle, "_")

	if len(cleanedTitle) == 0 {
		cleanedTitle = "unknown"
	}

	if unicode.IsDigit(rune(cleanedTitle[0])) {
		cleanedTitle = "p" + cleanedTitle
	}
	return cleanedTitle
}
