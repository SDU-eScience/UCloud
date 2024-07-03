package termio

import (
	"fmt"
	"os"
	"strings"
)

func Sgr(n int) string {
	// Select graphic rendition
	return "\u001B[" + fmt.Sprint(n) + "m"
}

type IoStyle = int

const (
	NoStyle IoStyle = 1 << iota
	Bold
	Italics
	Underline
)

type Color = int

const (
	DefaultColor Color = 0
	Black        Color = 1
	Red          Color = 2
	Green        Color = 3
	Yellow       Color = 4
	Blue         Color = 5
	Magenta      Color = 6
	Cyan         Color = 7
	White        Color = 8
)

func WriteStyledStringIfPty(pty bool, style IoStyle, fg, bg Color, formatString string, args ...any) string {
	if pty {
		return WriteStyledString(style, fg, bg, formatString, args...)
	} else {
		return fmt.Sprintf(formatString, args...)
	}
}

func WriteStyledString(style IoStyle, fg, bg Color, formatString string, args ...any) string {
	builder := strings.Builder{}
	if style != 0 {
		if style&Bold != 0 {
			builder.WriteString(Sgr(1))
		}
		if style&Italics != 0 {
			builder.WriteString(Sgr(3))
		}
		if style&Underline != 0 {
			builder.WriteString(Sgr(4))
		}
	}

	// NOTE(Dan): Color values are offset by one to make it possible to use WriteStyled(0, 0, 0, "No styling")
	if fg != DefaultColor {
		builder.WriteString(Sgr(29 + fg))
	}
	if bg != DefaultColor {
		builder.WriteString(Sgr(39 + bg))
	}

	builder.WriteString(fmt.Sprintf(formatString, args...))

	builder.WriteString(Sgr(0))
	return builder.String()
}

func WriteStyled(style IoStyle, fg, bg Color, formatString string, args ...any) {
	_, _ = os.Stdout.WriteString(WriteStyledString(style, fg, bg, formatString, args...))
}

func WriteStyledLine(style IoStyle, fg, bg Color, formatString string, args ...any) {
	WriteStyled(style, fg, bg, formatString+"\n", args...)
}

func Write(formatString string, args ...any) {
	WriteStyled(0, 0, 0, fmt.Sprintf(formatString, args...))
}

func WriteLine(formatString string, args ...any) {
	Write(formatString+"\n", args...)
}
