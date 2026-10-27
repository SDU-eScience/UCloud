package termio

import (
	"os"
	"strings"
	"unicode"
	"unicode/utf8"
)

type Table struct {
	header     []TableHeader
	rows       [][]StyledString
	fill       bool
	dimBorders bool
}

type TableHeaderFlags = uint64

const (
	TableHeaderAlignRight TableHeaderFlags = 1 << iota
	TableHeaderFlex
)

type TableHeader struct {
	Title      string
	Flags      TableHeaderFlags
	WidthGroup string
	MaxWidth   int
}

func (t *Table) AppendHeader(title string) {
	t.header = append(t.header, TableHeader{Title: title})
}

func (t *Table) AppendHeaderEx(title string, flags TableHeaderFlags) {
	t.header = append(t.header, TableHeader{Title: title, Flags: flags})
}

func (t *Table) SetColumnWidthGroup(column int, group string) {
	if column < 0 || column >= len(t.header) {
		panic("termio: table column index out of range")
	}
	t.header[column].WidthGroup = group
}

func (t *Table) SetColumnMaxWidth(column int, width int) {
	if column < 0 || column >= len(t.header) {
		panic("termio: table column index out of range")
	}
	t.header[column].MaxWidth = width
}

func (t *Table) SetFill(fill bool) {
	t.fill = fill
}

func (t *Table) SetDimBorders(dim bool) {
	t.dimBorders = dim
}

func (t *Table) Cell(formatString string, args ...any) {
	t.CellValue(Plain(formatString, args...))
}

func (t *Table) CellValue(value StyledString) {
	if len(t.rows) == 0 {
		t.rows = append(t.rows, []StyledString{})
	} else {
		currentRow := t.rows[len(t.rows)-1]
		if len(currentRow) == len(t.header) {
			t.rows = append(t.rows, []StyledString{})
		}
	}

	t.rows[len(t.rows)-1] = append(t.rows[len(t.rows)-1], value)
}

func (t *Table) Print() {
	os.Stdout.WriteString(t.String())
}

func (t *Table) String() string {
	ptyCols, _, isPty := safeQueryPtySize()
	return t.stringWithWidth(ptyCols, isPty)
}

func (t *Table) stringWithWidth(ptyCols int, isPty bool) string {
	fillColumn := -1
	if t.fill {
		fillColumn = t.fillColumn()
	}
	return t.stringWithWidths(ptyCols, isPty, t.preferredWidths(), fillColumn, t.dimBorders)
}

func (t *Table) preferredWidths() []int {
	widths := make([]int, len(t.header))
	for col, header := range t.header {
		widths[col] = t.columnWidth(header, header.Title)
	}

	for _, row := range t.rows {
		for col, cell := range row {
			if col >= len(widths) {
				continue
			}
			if width := t.columnWidth(t.header[col], cell.Text); width > widths[col] {
				widths[col] = width
			}
		}
	}

	for col := range widths {
		widths[col] = max(1, widths[col])
	}
	return widths
}

func (t *Table) columnWidth(header TableHeader, value string) int {
	width := utf8.RuneCountInString(value)
	if header.MaxWidth > 0 && width > header.MaxWidth {
		width = header.MaxWidth
	}
	return width
}

func (t *Table) fillColumn() int {
	for col, header := range t.header {
		if header.Flags&TableHeaderFlex != 0 {
			return col
		}
	}
	return -1
}

func (t *Table) stringWithWidths(
	ptyCols int,
	isPty bool,
	preferredWidths []int,
	fillColumn int,
	dimBorders bool,
) string {
	if len(t.header) == 0 {
		return ""
	}

	spaceAssignment := assignColumnWidths(ptyCols, preferredWidths, fillColumn)
	builder := strings.Builder{}

	tableWidth := 1 + 3*len(t.header)
	for _, width := range spaceAssignment {
		tableWidth += width
	}

	border := func(value string) string {
		if !dimBorders {
			return value
		}
		return WriteStyledStringIfPty(isPty, 0, Muted, DefaultColor, "%s", value)
	}

	builder.WriteString(border(boxNwCorner))
	for col := range t.header {
		builder.WriteString(border(strings.Repeat(boxHorizontalBar, spaceAssignment[col]+2)))
		if col == len(t.header)-1 {
			builder.WriteString(border(boxNeCorner))
		} else {
			builder.WriteString(border(boxHorizontalDown))
		}
	}
	builder.WriteString("\n")

	headerLines := make([][]string, len(t.header))
	headerHeight := 1

	for col, header := range t.header {
		wrapped := wrapText(header.Title, spaceAssignment[col])
		headerLines[col] = wrapped
		headerHeight = max(headerHeight, len(wrapped))
	}

	for row := 0; row < headerHeight; row++ {
		builder.WriteString(border(boxVerticalBar))
		for col := range t.header {
			line := ""
			if row < len(headerLines[col]) {
				line = headerLines[col][row]
			}

			paddingRequired := max(0, spaceAssignment[col]-utf8.RuneCountInString(line))
			colFlags := t.header[col].Flags

			builder.WriteString(" ")
			if colFlags&TableHeaderAlignRight != 0 {
				if paddingRequired > 0 {
					builder.WriteString(strings.Repeat(" ", paddingRequired))
				}
				builder.WriteString(WriteStyledStringIfPty(isPty, Bold, DefaultColor, DefaultColor, "%s", line))
			} else {
				builder.WriteString(WriteStyledStringIfPty(isPty, Bold, DefaultColor, DefaultColor, "%s", line))
				if paddingRequired > 0 {
					builder.WriteString(strings.Repeat(" ", paddingRequired))
				}
			}
			builder.WriteString(" ")
			builder.WriteString(border(boxVerticalBar))
		}
		builder.WriteString("\n")
	}

	builder.WriteString(border(boxVerticalRight))
	for col := range t.header {
		builder.WriteString(border(strings.Repeat(boxHorizontalBar, spaceAssignment[col]+2)))
		if col == len(t.header)-1 {
			builder.WriteString(border(boxVerticalLeft))
		} else {
			if len(t.rows) == 0 {
				builder.WriteString(border(boxHorizontalUp))
			} else {
				builder.WriteString(border(boxCross))
			}
		}
	}
	builder.WriteString("\n")

	if len(t.rows) == 0 {
		message := " No data available"
		padding := max(0, tableWidth-2-utf8.RuneCountInString(message))
		builder.WriteString(border(boxVerticalBar))
		builder.WriteString(WriteStyledStringIfPty(isPty, 0, Muted, DefaultColor, "%s", message))
		builder.WriteString(strings.Repeat(" ", padding))
		builder.WriteString(border(boxVerticalBar))
		builder.WriteString("\n")
	} else {
		for _, row := range t.rows {
			wrappedCells := make([][]StyledString, len(t.header))
			rowHeight := 1

			for col := range t.header {
				value := StyledString{}
				if col < len(row) {
					value = row[col]
				}

				wrapped := wrapStyledText(value, spaceAssignment[col])
				wrappedCells[col] = wrapped
				rowHeight = max(rowHeight, len(wrapped))
			}

			for lineIdx := 0; lineIdx < rowHeight; lineIdx++ {
				builder.WriteString(border(boxVerticalBar))
				for col := range t.header {
					value := StyledString{}
					if lineIdx < len(wrappedCells[col]) {
						value = wrappedCells[col][lineIdx]
					}

					colFlags := t.header[col].Flags
					paddingRequired := max(0, spaceAssignment[col]-utf8.RuneCountInString(value.Text))

					if colFlags&TableHeaderAlignRight != 0 {
						builder.WriteString(" ")
						if paddingRequired > 0 {
							builder.WriteString(strings.Repeat(" ", paddingRequired))
						}
						builder.WriteString(value.Render(isPty))
						builder.WriteString(" ")
						builder.WriteString(border(boxVerticalBar))
					} else {
						builder.WriteString(" ")
						builder.WriteString(value.Render(isPty))
						if paddingRequired > 0 {
							builder.WriteString(strings.Repeat(" ", paddingRequired))
						}
						builder.WriteString(" ")
						builder.WriteString(border(boxVerticalBar))
					}
				}
				builder.WriteString("\n")
			}
		}
	}

	builder.WriteString(border(boxSwCorner))
	for col := range t.header {
		builder.WriteString(border(strings.Repeat(boxHorizontalBar, spaceAssignment[col]+2)))
		if col == len(t.header)-1 {
			builder.WriteString(border(boxSeCorner))
		} else {
			if len(t.rows) == 0 {
				builder.WriteString(border(boxHorizontalBar))
			} else {
				builder.WriteString(border(boxHorizontalUp))
			}
		}
	}
	builder.WriteString("\n")

	return builder.String()
}

type tableSetSection struct {
	title string
	table *Table
}

type TableSet struct {
	sections   []tableSetSection
	fill       bool
	dimBorders bool
}

func (s *TableSet) Add(title string, table *Table) {
	s.sections = append(s.sections, tableSetSection{title: title, table: table})
}

func (s *TableSet) SetFill(fill bool) {
	s.fill = fill
}

func (s *TableSet) SetDimBorders(dim bool) {
	s.dimBorders = dim
}

func (s *TableSet) Print() {
	os.Stdout.WriteString(s.String())
}

func (s *TableSet) String() string {
	ptyCols, _, isPty := safeQueryPtySize()

	preferred := make([][]int, len(s.sections))
	groupWidths := map[string]int{}

	for sectionIdx, section := range s.sections {
		preferred[sectionIdx] = section.table.preferredWidths()
		for col, header := range section.table.header {
			if header.WidthGroup == "" {
				continue
			}
			width := preferred[sectionIdx][col]
			if current, ok := groupWidths[header.WidthGroup]; !ok || width > current {
				groupWidths[header.WidthGroup] = width
			}
		}
	}

	builder := strings.Builder{}
	for sectionIdx, section := range s.sections {
		if sectionIdx > 0 {
			builder.WriteString("\n")
		}
		if section.title != "" {
			builder.WriteString(WriteStyledStringIfPty(isPty, Bold, DefaultColor, DefaultColor, "%s", section.title))
			builder.WriteString("\n")
		}

		widths := append([]int(nil), preferred[sectionIdx]...)
		for col, header := range section.table.header {
			if header.WidthGroup != "" {
				widths[col] = groupWidths[header.WidthGroup]
			}
		}

		fillColumn := -1
		if s.fill {
			fillColumn = section.table.fillColumn()
		}

		builder.WriteString(section.table.stringWithWidths(ptyCols, isPty, widths, fillColumn, s.dimBorders))
	}

	return builder.String()
}

func assignColumnWidths(ptyCols int, preferred []int, fillColumn int) []int {
	widths := make([]int, len(preferred))
	copy(widths, preferred)

	if len(widths) == 0 {
		return widths
	}

	availableContent := max(1, ptyCols-(1+3*len(widths)))
	current := 0
	for i, w := range widths {
		widths[i] = max(1, w)
		current += widths[i]
	}

	minColumnWidth := 4
	if len(widths)*minColumnWidth > availableContent {
		minColumnWidth = 1
	}

	for current > availableContent {
		shrinkIdx := -1
		shrinkWidth := minColumnWidth

		for i, w := range widths {
			if w > shrinkWidth {
				shrinkIdx = i
				shrinkWidth = w
			}
		}

		if shrinkIdx < 0 {
			break
		}

		widths[shrinkIdx]--
		current--
	}

	if fillColumn >= 0 && fillColumn < len(widths) && current < availableContent {
		widths[fillColumn] += availableContent - current
	}

	return widths
}

func wrapStyledText(value StyledString, width int) []StyledString {
	lines := wrapText(value.Text, width)
	result := make([]StyledString, len(lines))
	for i, line := range lines {
		result[i] = StyledString{
			Text:  line,
			Style: value.Style,
			Fg:    value.Fg,
			Bg:    value.Bg,
		}
	}
	return result
}

func wrapText(value string, width int) []string {
	if width <= 0 {
		return []string{""}
	}

	lines := strings.Split(value, "\n")
	var result []string

	for _, line := range lines {
		if line == "" {
			result = append(result, "")
			continue
		}

		rest := line
		for utf8.RuneCountInString(rest) > width {
			chunk, tail := takeWrapChunk(rest, width)
			result = append(result, chunk)
			rest = strings.TrimLeftFunc(tail, unicode.IsSpace)
		}

		result = append(result, rest)
	}

	if len(result) == 0 {
		return []string{""}
	}

	return result
}

func takeWrapChunk(value string, width int) (string, string) {
	count := 0
	cutoff := len(value)
	lastSpaceEnd := -1

	for idx, r := range value {
		if unicode.IsSpace(r) {
			lastSpaceEnd = idx + utf8.RuneLen(r)
		}

		count++
		if count > width {
			cutoff = idx
			break
		}
	}

	if cutoff <= 0 {
		r, size := utf8.DecodeRuneInString(value)
		if r == utf8.RuneError && size == 0 {
			return "", ""
		}
		return value[:size], value[size:]
	}

	if lastSpaceEnd > 0 && lastSpaceEnd < cutoff {
		return strings.TrimRightFunc(value[:lastSpaceEnd], unicode.IsSpace), value[lastSpaceEnd:]
	}

	return value[:cutoff], value[cutoff:]
}
