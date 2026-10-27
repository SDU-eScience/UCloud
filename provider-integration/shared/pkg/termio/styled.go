package termio

import "fmt"

type StyledString struct {
	Text  string
	Style IoStyle
	Fg    Color
	Bg    Color
}

func (s StyledString) String() string {
	return s.Text
}

func (s StyledString) Render(pty bool) string {
	if !pty || (s.Style == 0 && s.Fg == DefaultColor && s.Bg == DefaultColor) {
		return s.Text
	}
	return WriteStyledString(s.Style, s.Fg, s.Bg, "%s", s.Text)
}

func Plain(format string, args ...any) StyledString {
	return StyledString{Text: fmt.Sprintf(format, args...)}
}

func StyledWith(style IoStyle, fg Color, format string, args ...any) StyledString {
	return StyledString{
		Text:  fmt.Sprintf(format, args...),
		Style: style,
		Fg:    fg,
	}
}

func Emphasis(format string, args ...any) StyledString {
	return StyledWith(Bold, DefaultColor, format, args...)
}

func Success(format string, args ...any) StyledString {
	return StyledWith(0, Green, format, args...)
}

func Warning(format string, args ...any) StyledString {
	return StyledWith(0, Yellow, format, args...)
}

func Danger(format string, args ...any) StyledString {
	return StyledWith(0, Red, format, args...)
}

func Subtle(format string, args ...any) StyledString {
	return StyledWith(0, Muted, format, args...)
}
