package termio

import (
	"fmt"
	"io"
	"os"
	"strings"
)

type ColorMode int

const (
	ColorAuto ColorMode = iota
	ColorAlways
	ColorNever
)

var colorMode = ColorAuto

func SetColorMode(mode ColorMode) {
	colorMode = mode
}

func ColorModeFromString(value string) (ColorMode, error) {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "", "auto":
		return ColorAuto, nil
	case "always":
		return ColorAlways, nil
	case "never":
		return ColorNever, nil
	default:
		return ColorAuto, fmt.Errorf("unknown color mode %q; use auto, always or never", value)
	}
}

func ColorEnabled(w io.Writer) bool {
	switch colorMode {
	case ColorAlways:
		return true
	case ColorNever:
		return false
	}

	if os.Getenv("NO_COLOR") != "" {
		return false
	}
	if os.Getenv("CLICOLOR_FORCE") != "" && os.Getenv("CLICOLOR_FORCE") != "0" {
		return true
	}

	return isPtyWriter(w)
}

func Sgr(n int) string {
	// Select graphic rendition
	return "\u001B[" + fmt.Sprint(n) + "m"
}

func Escape256Color(n int) string {
	return "\u001B[38;5;" + fmt.Sprint(n) + "m"
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
	Muted        Color = 1244
)

func WriteStyledStringIf(color bool, style IoStyle, fg, bg Color, formatString string, args ...any) string {
	if color {
		return WriteStyledString(style, fg, bg, formatString, args...)
	}
	return fmt.Sprintf(formatString, args...)
}

func WriteStyledStringIfPty(pty bool, style IoStyle, fg, bg Color, formatString string, args ...any) string {
	return WriteStyledStringIf(pty, style, fg, bg, formatString, args...)
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
		if fg >= 1000 {
			builder.WriteString(Escape256Color(fg - 1000))
		} else {
			builder.WriteString(Sgr(29 + fg))
		}
	}
	if bg != DefaultColor {
		builder.WriteString(Sgr(39 + bg))
	}

	builder.WriteString(fmt.Sprintf(formatString, args...))

	builder.WriteString(Sgr(0))
	return builder.String()
}

func WriteStyled(style IoStyle, fg, bg Color, formatString string, args ...any) {
	os.Stdout.WriteString(WriteStyledString(style, fg, bg, formatString, args...))
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

func moveCursorUp(lines int) {
	fmt.Printf("\033[%dA", lines)
}

func clearLine() {
	fmt.Printf("\033[2K")
}

func hideCursor() {
	fmt.Printf("\033[?25l")
}

func showCursor() {
	fmt.Printf("\033[?25h")
}
