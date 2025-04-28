package foundation

import (
	"fmt"
	anyascii "github.com/anyascii/go"
	"regexp"
	"strings"
	"time"
	"ucloud.dk/shared/pkg/log"
	"unicode"
)

type ProjectTitleStrategy int

const (
	ProjectTitleDefault ProjectTitleStrategy = iota
	ProjectTitleDate
	ProjectTitleUuid
)

var duplicateUnderscores = regexp.MustCompile("_+")

func GenerateProjectName(ucloudId string, ucloudTitle string, strategy ProjectTitleStrategy, prefix string) (title string, serialDigitsRequested int) {
	switch strategy {
	case ProjectTitleDefault:
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

	case ProjectTitleDate:
		now := time.Now()
		return fmt.Sprintf("%s%d-%d-", prefix, now.Year(), now.Month()), 4

	case ProjectTitleUuid:
		return "p" + strings.Split(ucloudId, "-")[0] + "-", 4
	}

	log.Warn("Unhandled project title strategy: %v", strategy)
	return "p" + ucloudId, 0
}
