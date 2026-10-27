package termio

import (
	"os"
	"regexp"
	"strings"
	"unicode/utf8"
)

type Frame struct {
	title        string
	fields       []frameField
	naturalWidth bool
	dimBorders   bool
}

type frameField struct {
	Title  string
	Value  string
	Styled *StyledString
}

func (f *Frame) Title(title string) {
	f.title = title
}

func (f *Frame) SetNaturalWidth(natural bool) {
	f.naturalWidth = natural
}

func (f *Frame) SetDimBorders(dim bool) {
	f.dimBorders = dim
}

func (f *Frame) AppendField(title string, value string) {
	lines := strings.Split(value, "\n")

	for i := 0; i < len(lines); i++ {
		if i > 0 {
			f.fields = append(f.fields, frameField{Value: lines[i]})
		} else {
			f.fields = append(f.fields, frameField{Title: title, Value: lines[i]})
		}
	}
}

func (f *Frame) AppendFieldStyled(title string, value StyledString) {
	lines := strings.Split(value.Text, "\n")

	for i := 0; i < len(lines); i++ {
		line := StyledString{
			Text:  lines[i],
			Style: value.Style,
			Fg:    value.Fg,
			Bg:    value.Bg,
		}
		if i > 0 {
			f.fields = append(f.fields, frameField{Styled: &line})
		} else {
			f.fields = append(f.fields, frameField{Title: title, Styled: &line})
		}
	}
}

func (f *Frame) AppendSeparator() {
	f.AppendField(frameSeparator, frameSeparator)
}

func (f *Frame) AppendTitle(title string) {
	if f.title == "" {
		f.Title(title)
	} else {
		f.AppendField(titleHint, title)
	}
}

const frameSeparator = "SEPSEPSEP"
const titleHint = "TTLTTLTTL"

var re, reErr = regexp.Compile("[\u001b\u009b][[()#;?]*(?:[0-9]{1,4}(?:;[0-9]{0,4})*)?[0-9A-ORZcf-nqry=><]")

func (field frameField) plainValue() string {
	if field.Styled != nil {
		return field.Styled.Text
	}

	if reErr == nil {
		return re.ReplaceAllString(field.Value, "")
	}
	return field.Value
}

func (field frameField) renderValue(isPty bool) string {
	if field.Styled != nil {
		return field.Styled.Render(isPty)
	}
	return field.Value
}

func (f *Frame) visualFields(valueWidth int) []frameField {
	result := make([]frameField, 0, len(f.fields))

	for _, field := range f.fields {
		if field.Title == frameSeparator || field.Title == titleHint {
			result = append(result, field)
			continue
		}

		if field.Styled != nil {
			lines := wrapText(field.Styled.Text, valueWidth)
			for i, line := range lines {
				styled := StyledString{
					Text:  line,
					Style: field.Styled.Style,
					Fg:    field.Styled.Fg,
					Bg:    field.Styled.Bg,
				}
				title := ""
				if i == 0 {
					title = field.Title
				}
				result = append(result, frameField{Title: title, Styled: &styled})
			}
		} else if reErr == nil && re.MatchString(field.Value) {
			result = append(result, field)
		} else {
			lines := wrapText(field.Value, valueWidth)
			for i, line := range lines {
				title := ""
				if i == 0 {
					title = field.Title
				}
				result = append(result, frameField{Title: title, Value: line})
			}
		}
	}

	return result
}

func (f *Frame) String() string {
	ptyCols, _, isPty := safeQueryPtySize()
	builder := strings.Builder{}

	maxFieldSize := 0
	for _, field := range f.fields {
		titleLen := utf8.RuneCountInString(field.Title)
		if titleLen > maxFieldSize {
			maxFieldSize = titleLen
		}
	}

	if maxFieldSize < 25 {
		maxFieldSize = 25
	}

	valueWidth := ptyCols - maxFieldSize - 6
	if valueWidth < 1 {
		valueWidth = 1
	}
	fields := f.visualFields(valueWidth)

	maxValueSize := 0
	for _, field := range fields {
		valueLen := utf8.RuneCountInString(field.plainValue())
		if valueLen > maxValueSize {
			maxValueSize = valueLen
		}
	}

	frameWidth := ptyCols
	if f.naturalWidth {
		frameWidth = maxFieldSize + maxValueSize + 6
		if frameWidth > ptyCols {
			frameWidth = ptyCols
		}
	}

	border := func(value string) string {
		if !f.dimBorders {
			return value
		}
		return WriteStyledStringIfPty(isPty, 0, Muted, DefaultColor, "%s", value)
	}

	builder.WriteString(border(boxNwCorner + strings.Repeat(boxHorizontalBar, frameWidth-2) + boxNeCorner))
	builder.WriteString("\n")

	appendTitle := func(title string) {
		titleLength := utf8.RuneCountInString(title)
		padding := (frameWidth - titleLength - 2) / 2
		paddingRem := (frameWidth - titleLength - 2) % 2

		builder.WriteString(border(boxVerticalBar))
		if padding > 0 {
			builder.WriteString(strings.Repeat(" ", padding))
		}
		builder.WriteString(WriteStyledStringIfPty(isPty, Bold, 0, 0, "%s", title))
		if padding > 0 {
			builder.WriteString(strings.Repeat(" ", padding+paddingRem))
		}
		builder.WriteString(border(boxVerticalBar))
		builder.WriteString("\n")
	}

	appendTitle(f.title)

	builder.WriteString(border(boxVerticalRight + strings.Repeat(boxHorizontalBar, maxFieldSize+1) + boxHorizontalDown + strings.Repeat(boxHorizontalBar, frameWidth-maxFieldSize-4) + boxVerticalLeft))
	builder.WriteString("\n")

	for i, field := range fields {
		if field.Title == frameSeparator && field.Value == frameSeparator {
			if i == 0 || i == len(fields)-1 {
				continue
			}

			if i < len(fields)-1 && (fields[i+1].Title == titleHint || fields[i+1].Title == frameSeparator) {
				continue
			}

			builder.WriteString(border(boxVerticalRight + strings.Repeat(boxHorizontalBar, maxFieldSize+1) + boxCross + strings.Repeat(boxHorizontalBar, frameWidth-maxFieldSize-4) + boxVerticalLeft))
			builder.WriteString("\n")
		} else if field.Title == titleHint {
			builder.WriteString(border(boxVerticalRight + strings.Repeat(boxHorizontalBar, maxFieldSize+1) + boxHorizontalUp + strings.Repeat(boxHorizontalBar, frameWidth-maxFieldSize-4) + boxVerticalLeft))
			builder.WriteString("\n")

			appendTitle(field.Value)

			builder.WriteString(border(boxVerticalRight + strings.Repeat(boxHorizontalBar, maxFieldSize+1) + boxHorizontalDown + strings.Repeat(boxHorizontalBar, frameWidth-maxFieldSize-4) + boxVerticalLeft))
			builder.WriteString("\n")
		} else {
			plainValue := field.plainValue()
			titleLength := utf8.RuneCountInString(field.Title)
			valueLength := utf8.RuneCountInString(plainValue)
			titlePadding := maxFieldSize - titleLength

			builder.WriteString(border(boxVerticalBar))
			builder.WriteString(" ")
			builder.WriteString(WriteStyledStringIfPty(isPty, Bold, 0, 0, "%s", field.Title))
			if titlePadding > 0 {
				builder.WriteString(strings.Repeat(" ", titlePadding))
			}

			builder.WriteString(border(boxVerticalBar))
			builder.WriteString(" ")
			builder.WriteString(field.renderValue(isPty))

			spaceRem := frameWidth - (titleLength + 2 + titlePadding + valueLength + 2)
			if spaceRem > 0 {
				builder.WriteString(strings.Repeat(" ", spaceRem-1))
			}
			builder.WriteString(border(boxVerticalBar))
			builder.WriteString("\n")
		}
	}

	builder.WriteString(border(boxSwCorner + strings.Repeat(boxHorizontalBar, maxFieldSize+1) + boxHorizontalUp + strings.Repeat(boxHorizontalBar, frameWidth-maxFieldSize-4) + boxSeCorner))
	builder.WriteString("\n")

	return builder.String()
}

func (f *Frame) Print() {
	os.Stdout.WriteString(f.String())
}
