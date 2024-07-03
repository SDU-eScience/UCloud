package termio

import (
	"os"
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
	f.fields = append(f.fields, frameField{title, value})
}

func (f *Frame) String() string {
	ptyCols, _, isPty := safeQueryPtySize()
	builder := strings.Builder{}

	maxTitleSize := 0
	maxValueSize := 0
	for _, field := range f.fields {
		titleLen := len(field.Title)
		valueLen := len(field.Value)

		if titleLen > maxTitleSize {
			maxTitleSize = titleLen
		}

		if valueLen > maxValueSize {
			maxValueSize = valueLen
		}
	}

	margin := 3
	spaceRequired := maxTitleSize + maxValueSize
	if ptyCols-margin > spaceRequired {
		remainingSpace := (ptyCols - spaceRequired) / 2

		maxTitleSize += remainingSpace
		maxValueSize += remainingSpace
	}

	spaceRequired = maxTitleSize + maxValueSize
	totalSpace := spaceRequired + margin

	{
		titleLength := len(f.title)
		padding := (totalSpace - margin - titleLength) / 2

		titleBuilder := strings.Builder{}
		if padding > 0 {
			titleBuilder.WriteString(strings.Repeat(" ", padding))
		}
		titleBuilder.WriteString(f.title)
		if padding > 0 {
			titleBuilder.WriteString(strings.Repeat(" ", padding))
		}
		titleBuilder.WriteString("\n")

		builder.WriteString(WriteStyledStringIfPty(isPty, Bold, 0, 0, titleBuilder.String()))
	}
	builder.WriteString(strings.Repeat("-", totalSpace-margin))
	builder.WriteString("\n")

	for _, field := range f.fields {
		builder.WriteString(WriteStyledStringIfPty(isPty, Bold, 0, 0, field.Title))
		titlePadding := maxTitleSize - len(field.Title)
		if titlePadding > 0 {
			builder.WriteString(strings.Repeat(" ", titlePadding))
		}
		builder.WriteString(WriteStyledStringIfPty(isPty, Bold, 0, 0, " | "))

		builder.WriteString(field.Value)
		builder.WriteString("\n")
	}

	return builder.String()
}

func (f *Frame) Print() {
	_, _ = os.Stdout.WriteString(f.String())
}
