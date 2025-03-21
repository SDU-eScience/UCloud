package termio

import (
	"os"
	"regexp"
	"strings"
)

type Frame struct {
	title  string
	fields []frameField
}

type frameField struct {
	Title string
	Value string
}

func (f *Frame) Title(title string) {
	f.title = title
}

func (f *Frame) AppendField(title string, value string) {
	lines := strings.Split(value, "\n")

	for i := 0; i < len(lines); i++ {
		if i > 0 {
			f.fields = append(f.fields, frameField{"", lines[i]})
		} else {
			f.fields = append(f.fields, frameField{title, lines[i]})
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

// Regex for stripping ANSI escape codes
var re, reErr = regexp.Compile("[\u001b\u009b][[()#;?]*(?:[0-9]{1,4}(?:;[0-9]{0,4})*)?[0-9A-ORZcf-nqry=><]")

func (f *Frame) String() string {
	ptyCols, _, isPty := safeQueryPtySize()
	builder := strings.Builder{}

	maxFieldSize := 0
	maxValueSize := 0
	for _, field := range f.fields {
		titleLen := len(field.Title)
		valueLen := len(field.Value)

		if titleLen > maxFieldSize {
			maxFieldSize = titleLen
		}

		if valueLen > maxValueSize {
			maxValueSize = valueLen
		}
	}

	if maxFieldSize < 25 {
		maxFieldSize = 25
	}

	builder.WriteString(boxNwCorner)
	builder.WriteString(strings.Repeat(boxHorizontalBar, ptyCols-2))
	builder.WriteString(boxNeCorner)
	builder.WriteString("\n")

	{
		titleLength := len(f.title)
		padding := (ptyCols - titleLength - 2) / 2
		paddingRem := (ptyCols - titleLength - 2) % 2

		titleBuilder := strings.Builder{}
		titleBuilder.WriteString(boxVerticalBar)
		if padding > 0 {
			titleBuilder.WriteString(strings.Repeat(" ", padding))
		}
		titleBuilder.WriteString(f.title)
		if padding > 0 {
			titleBuilder.WriteString(strings.Repeat(" ", padding+paddingRem))
		}
		titleBuilder.WriteString(boxVerticalBar)
		titleBuilder.WriteString("\n")

		builder.WriteString(WriteStyledStringIfPty(isPty, Bold, 0, 0, titleBuilder.String()))
	}
	builder.WriteString(boxVerticalRight)
	builder.WriteString(strings.Repeat(boxHorizontalBar, maxFieldSize+1))
	builder.WriteString(boxHorizontalDown)
	builder.WriteString(strings.Repeat(boxHorizontalBar, ptyCols-maxFieldSize-4))
	builder.WriteString(boxVerticalLeft)
	builder.WriteString("\n")

	for i, field := range f.fields {
		if field.Title == frameSeparator && field.Value == frameSeparator {
			if i == 0 || i == len(f.fields)-1 {
				// Do not do anything with separators on the first or last row. This would just result in a double
				// separator which looks weird and almost certainly not what was intended.
				continue
			}

			if i < len(f.fields)-1 && (f.fields[i+1].Title == titleHint || f.fields[i+1].Title == frameSeparator) {
				continue
			}

			builder.WriteString(boxVerticalRight)
			builder.WriteString(strings.Repeat(boxHorizontalBar, maxFieldSize+1))
			builder.WriteString(boxCross)
			builder.WriteString(strings.Repeat(boxHorizontalBar, ptyCols-maxFieldSize-4))
			builder.WriteString(boxVerticalLeft)
			builder.WriteString("\n")
		} else if field.Title == titleHint {
			builder.WriteString(boxVerticalRight)
			builder.WriteString(strings.Repeat(boxHorizontalBar, maxFieldSize+1))
			builder.WriteString(boxHorizontalUp)
			builder.WriteString(strings.Repeat(boxHorizontalBar, ptyCols-maxFieldSize-4))
			builder.WriteString(boxVerticalLeft)
			builder.WriteString("\n")

			{
				titleLength := len(field.Value)
				padding := (ptyCols - titleLength - 2) / 2
				paddingRem := (ptyCols - titleLength - 2) % 2

				titleBuilder := strings.Builder{}
				titleBuilder.WriteString(boxVerticalBar)
				if padding > 0 {
					titleBuilder.WriteString(strings.Repeat(" ", padding))
				}
				titleBuilder.WriteString(field.Value)
				if padding > 0 {
					titleBuilder.WriteString(strings.Repeat(" ", padding+paddingRem))
				}
				titleBuilder.WriteString(boxVerticalBar)
				titleBuilder.WriteString("\n")

				builder.WriteString(WriteStyledStringIfPty(isPty, Bold, 0, 0, titleBuilder.String()))
			}
			builder.WriteString(boxVerticalRight)
			builder.WriteString(strings.Repeat(boxHorizontalBar, maxFieldSize+1))
			builder.WriteString(boxHorizontalDown)
			builder.WriteString(strings.Repeat(boxHorizontalBar, ptyCols-maxFieldSize-4))
			builder.WriteString(boxVerticalLeft)
			builder.WriteString("\n")
		} else {
			builder.WriteString(boxVerticalBar)
			builder.WriteString(" ")

			builder.WriteString(WriteStyledStringIfPty(isPty, Bold, 0, 0, field.Title))
			titlePadding := maxFieldSize - len(field.Title)
			if titlePadding > 0 {
				builder.WriteString(strings.Repeat(" ", titlePadding))
			}

			builder.WriteString(boxVerticalBar)
			builder.WriteString(" ")
			builder.WriteString(field.Value)

			spaceRem := ptyCols - (len(field.Title) + 2 + titlePadding + len(field.Value) + 2)

			if reErr == nil {
				strippedValue := re.ReplaceAllString(field.Value, "")
				spaceRem = ptyCols - (len(field.Title) + 2 + titlePadding + len(strippedValue) + 2)
			}

			if spaceRem > 0 {
				builder.WriteString(strings.Repeat(" ", spaceRem-1))
			}
			builder.WriteString(boxVerticalBar)
			builder.WriteString("\n")
		}
	}

	builder.WriteString(boxSwCorner)
	builder.WriteString(strings.Repeat(boxHorizontalBar, maxFieldSize+1))
	builder.WriteString(boxHorizontalUp)
	builder.WriteString(strings.Repeat(boxHorizontalBar, ptyCols-maxFieldSize-4))
	builder.WriteString(boxSeCorner)
	builder.WriteString("\n")

	return builder.String()
}

func (f *Frame) Print() {
	_, _ = os.Stdout.WriteString(f.String())
}
