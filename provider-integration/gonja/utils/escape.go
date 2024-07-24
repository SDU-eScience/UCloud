package utils

import "strings"

type ModeOfEscape string

const (
	EscapeModeBash = "Bash"
	EscapeModeHtml = "Html"
)

var EscapeMode ModeOfEscape = EscapeModeBash

func Escape(in string) string {
	switch EscapeMode {
	case EscapeModeBash:
		builder := &strings.Builder{}
		builder.WriteRune('\'')
		for _, c := range []rune(in) {
			if c == '\'' {
				builder.WriteString("'\"'\"'")
			} else {
				builder.WriteRune(c)
			}
		}
		builder.WriteRune('\'')
		return builder.String()
	case EscapeModeHtml:
		output := strings.Replace(in, "&", "&amp;", -1)
		output = strings.Replace(output, ">", "&gt;", -1)
		output = strings.Replace(output, "<", "&lt;", -1)
		output = strings.Replace(output, "\"", "&quot;", -1)
		output = strings.Replace(output, "'", "&#39;", -1)
		return output

	default:
		return in
	}
}
