package foundation

import (
	anyascii "github.com/anyascii/go"
	"regexp"
	"strings"
	"unicode"
)

type ParsedUsername struct {
	FirstName         string
	LastName          string
	SuggestedUsername string
}

var cleaningRegex = regexp.MustCompile("\\W+")

func ParseUCloudUsername(username string) ParsedUsername {
	cleaned := anyascii.Transliterate(username)
	usernameSplit := strings.Split(cleaned, "#")

	namePart := []rune(usernameSplit[0])

	{
		allUpper := true
		for _, c := range namePart {
			if !unicode.IsUpper(c) {
				allUpper = false
				break
			}
		}

		if allUpper {
			namePart = []rune(strings.ToLower(string(namePart)))
		}
	}

	var names []string
	dash := false
	builder := strings.Builder{}

	for _, c := range namePart {
		if unicode.IsUpper(c) && !dash && builder.Len() > 0 {
			names = append(names, builder.String())
			builder.Reset()
			builder.WriteRune(c)
			dash = false
		} else if c == '-' || c == '\'' {
			dash = true
			builder.WriteRune(c)
		} else {
			builder.WriteRune(c)
			dash = false
		}
	}

	suggestedUsername := ""
	names = append(names, builder.String())
	if len(names) == 1 {
		names = append(names, "Unknown")
		suggestedUsername = names[0]
	} else {
		if len(names[1]) <= 1 {
			suggestedUsername = names[0]
		} else {
			suggestedUsername = names[0][:1] + names[len(names)-1]
		}
	}

	suggestedUsername = strings.ToLower(suggestedUsername)
	suggestedUsername = strings.ToLower(cleaningRegex.ReplaceAllString(suggestedUsername, ""))
	suggestedUsername = suggestedUsername[:min(len(suggestedUsername), 28)]

	for i, name := range names {
		names[i] = string(unicode.ToUpper([]rune(name)[0])) + name[1:]
	}

	return ParsedUsername{
		FirstName:         names[0],
		LastName:          names[len(names)-1],
		SuggestedUsername: suggestedUsername,
	}
}
