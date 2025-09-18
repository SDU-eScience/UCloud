package ucviz

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"slices"
	"strconv"
	"strings"
	"ucloud.dk/pkg/termio"
	"ucloud.dk/shared/pkg/util"
)

var cliIdBase = ""
var cliIdCounter = 0

func cliId() string {
	result := fmt.Sprintf("%s%d", cliIdBase, cliIdCounter)
	cliIdCounter++
	return result
}

func HandleCli(args []string, uiChannel io.Writer, dataChannel io.Writer, lock util.Option[string]) {
	if lock.Present {
		lock, err := acquireLock(lock.Value)
		if err != nil {
			panic(err)
		}

		defer lock.Release()
	}

	if len(args) == 0 {
		printCliHelp("")
	}

	cliIdBase = "anon-" + util.RandomTokenNoTs(8)

	switch args[0] {
	case "widget":
		if len(args) != 2 {
			printCliHelp("No widget specified")
		}

		widgetText := args[1]
		ui, data, err := cliWidget(widgetText)
		if err != nil {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Incorrect usage: %s", err.Error())
			os.Exit(1)
		}

		_, _ = uiChannel.Write(ui)
		_, _ = dataChannel.Write(data)

	case "progress":
		if len(args) != 3 {
			printCliHelp("Usage: ucviz progress <id> <percentage>")
		}

		id := args[1]
		percentageRaw := args[2]
		percentage, err := strconv.ParseFloat(percentageRaw, 64)
		if err != nil {
			printCliHelp(fmt.Sprintf("Invalid percentage: %s", err.Error()))
		}

		dataChannelB := &bytes.Buffer{}
		dataChannelS := NewWidgetStream(dataChannelB)
		dataChannelS.UpdateProgress(id, percentage)
		_, _ = dataChannel.Write(dataChannelB.Bytes())

	case "append-rows":
		if len(args) != 3 {
			printCliHelp("Usage: ucviz append-rows <id> <rows>")
		}

		id := args[1]
		widgetText := args[2]
		p := NewParser(widgetText)

		var rows []WidgetTableRow

		for {
			node, err, eof := p.Parse()

			if eof {
				break
			}

			if err != nil {
				termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Incorrect usage: %s", err.Error())
				os.Exit(1)
			}

			row, err := processRow(p, node)
			if err != nil {
				termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Incorrect usage: %s", err.Error())
				os.Exit(1)
			}

			rows = append(rows, row)
		}

		if len(rows) == 0 {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "No rows supplied")
			os.Exit(1)
		}

		dataChannelB := &bytes.Buffer{}
		dataChannelS := NewWidgetStream(dataChannelB)
		dataChannelS.AppendTableRows(id, rows)
		_, _ = dataChannel.Write(dataChannelB.Bytes())
	}
}

func printCliHelp(reason string) {
	if reason != "" {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Incorrect usage: %v", reason)
	}
	os.Exit(0)
}

func cliWidget(docText string) (ui []byte, data []byte, err error) {
	uiChannelB := &bytes.Buffer{}
	dataChannelB := &bytes.Buffer{}
	uiChannel := NewWidgetStream(uiChannelB)
	dataChannel := NewWidgetStream(dataChannelB)

	p := NewParser(docText)
	node, err, _ := p.Parse()
	if err != nil {
		termio.Write("%s", err.Error())
		os.Exit(1)
	}

	_, err = processNode(uiChannel, dataChannel, p, node, util.OptNone[DocNode](), util.OptNone[WidgetLocation]())
	return uiChannelB.Bytes(), dataChannelB.Bytes(), err
}

func processNode(uiChannel *WidgetStream, dataChannel *WidgetStream, p *Parser, node DocNode, parent util.Option[DocNode], parentLoc util.Option[WidgetLocation]) (string, error) {
	idAttr := node.Attribute("id")
	if !idAttr.Present {
		if !parent.Present {
			return "", p.errorAt(node.Location, "root components must have an ID specified")
		} else {
			idAttr.Set(cliId())
		}
	}

	loc := WidgetLocation{
		Window: WidgetWindowMain,
		Tab:    "",
	}

	if parentLoc.Present {
		loc = parentLoc.Value
	} else {
		tab := node.Attribute("tab")
		if tab.Present {
			loc.Tab = tab.Value
		}

		icon := node.AttributeEnum("icon", iconNames)
		if icon.Present {
			loc.Icon = WidgetIcon(slices.Index(iconNames, icon.Value))
		}
	}

	id := idAttr.Value

	childrenAllowed := false
	textExpected := false

	switch node.Tag {
	case "Box":
		childrenAllowed = true
		c := WidgetContainer{}

		if minWidth := node.AttributeInt("min-width"); minWidth.Present {
			c.Width.Minimum = uint16(minWidth.Value)
		}

		if maxWidth := node.AttributeInt("min-width"); maxWidth.Present {
			c.Width.Maximum = uint16(maxWidth.Value)
		}

		if minHeight := node.AttributeInt("min-height"); minHeight.Present {
			c.Height.Minimum = uint16(minHeight.Value)
		}

		if maxHeight := node.AttributeInt("max-height"); maxHeight.Present {
			c.Height.Maximum = uint16(maxHeight.Value)
		}

		if grow := node.AttributeInt("grow"); grow.Present {
			c.Grow = uint8(grow.Value)
		}

		if gap := node.AttributeInt("gap"); gap.Present {
			c.Gap = uint8(gap.Value)
		}

		if direction := node.AttributeEnum("direction", []string{"row", "column"}); direction.Present {
			if direction.Value == "row" {
				c.Direction = WidgetDirectionRow
			} else if direction.Value == "column" {
				c.Direction = WidgetDirectionColumn
			}
		}

		if fg := parseColor(node.Attribute("color")); fg.Present {
			c.Foreground = fg.Value
		}

		if bg := parseColor(node.Attribute("background")); bg.Present {
			c.Background = bg.Value
		}

		for _, child := range node.Children {
			childId, err := processNode(uiChannel, dataChannel, p, child, util.OptValue(node), util.OptValue(loc))
			if err != nil {
				return "", err
			}

			c.Children = append(c.Children, WidgetContainerOrId{Id: util.OptValue(WidgetId{Id: childId})})
		}

		uiChannel.CreateContainer(id, loc, c)

	case "Widget":
		childrenAllowed = false

	case "Table":
		childrenAllowed = true
		t := WidgetTable{}
		for _, child := range node.Children {
			row, err := processRow(p, child)
			if err != nil {
				return "", err
			}
			t.Rows = append(t.Rows, row)
		}
		uiChannel.CreateTable(id, loc, t)

	case "Text":
		childrenAllowed = false
		textExpected = true

		label := WidgetLabel{}
		if align := node.AttributeEnum("align", []string{"begin", "center", "end"}); align.Present {
			switch align.Value {
			case "begin":
				label.Align = WidgetLabelAlignBegin
			case "center":
				label.Align = WidgetLabelAlignCenter
			case "end":
				label.Align = WidgetLabelAlignEnd
			}
		}

		label.Text = node.Text
		uiChannel.CreateLabel(id, loc, label)

	case "Progress":
		childrenAllowed = false

		progress := WidgetProgressBar{}
		if value := node.AttributeFloat("value"); value.Present {
			progress.Progress = value.Value / 100.0
		}
		uiChannel.CreateProgressBar(id, loc, progress)

	case "LineChart":
		textExpected = true
		childrenAllowed = false

		var definition WidgetLineChartDefinition
		err := json.Unmarshal([]byte(node.Text), &definition)
		if err != nil {
			return "", p.errorAt(node.Location, "Invalid definition provided: %s", err)
		}

		uiChannel.CreateLineChart(id, loc, definition)

	case "CodeSnippet":
		childrenAllowed = false
		textExpected = true

		uiChannel.CreateSnippet(id, loc, WidgetSnippet{Text: node.Text})
	}

	if !childrenAllowed && len(node.Children) > 0 {
		return "", p.errorAt(node.Location, "this node cannot have any children")
	}

	if !textExpected && len(node.Text) > 0 {
		return "", p.errorAt(node.Location, "this node should not have any text")
	}

	return id, nil
}

func processRow(p *Parser, node DocNode) (WidgetTableRow, error) {
	result := WidgetTableRow{}
	if node.Tag != "Row" {
		return WidgetTableRow{}, p.errorAt(node.Location, "Tables can only have <Row> children")
	}

	if node.Text != "" {
		return WidgetTableRow{}, p.errorAt(node.Location, "Table rows cannot have text associated with them")
	}

	for _, child := range node.Children {
		cell, err := processCell(p, child)
		if err != nil {
			return WidgetTableRow{}, err
		}

		result.Cells = append(result.Cells, cell)
	}

	return result, nil
}

func processCell(p *Parser, node DocNode) (WidgetTableCell, error) {
	result := WidgetTableCell{}

	if node.Tag != "Cell" {
		return WidgetTableCell{}, p.errorAt(node.Location, "Tables rows can only have <Cell> children")
	}

	if len(node.Children) > 0 {
		return WidgetTableCell{}, p.errorAt(node.Location, "Table cells cannot have any children")
	}

	headerAttr := node.Attribute("header")
	if headerAttr.Present && headerAttr.Value == "true" {
		result.Flags |= WidgetTableCellHeader
	}

	if minWidth := node.AttributeInt("min-width"); minWidth.Present {
		result.Width.Minimum = uint16(minWidth.Value)
	}

	if maxWidth := node.AttributeInt("min-width"); maxWidth.Present {
		result.Width.Maximum = uint16(maxWidth.Value)
	}

	if minHeight := node.AttributeInt("min-height"); minHeight.Present {
		result.Height.Minimum = uint16(minHeight.Value)
	}

	if maxHeight := node.AttributeInt("max-height"); maxHeight.Present {
		result.Height.Maximum = uint16(maxHeight.Value)
	}

	label := WidgetLabel{}
	if align := node.AttributeEnum("align", []string{"begin", "center", "end"}); align.Present {
		switch align.Value {
		case "begin":
			label.Align = WidgetLabelAlignBegin
		case "center":
			label.Align = WidgetLabelAlignCenter
		case "end":
			label.Align = WidgetLabelAlignEnd
		}
	}

	label.Text = node.Text

	result.Label = label
	return result, nil
}

var shadeNames = map[string]WidgetColorShade{
	"primary":   WidgetColorPrimary,
	"secondary": WidgetColorSecondary,
	"error":     WidgetColorError,
	"warning":   WidgetColorWarning,
	"info":      WidgetColorInfo,
	"success":   WidgetColorSuccess,
	"text":      WidgetColorText,
	"purple":    WidgetColorPurple,
	"red":       WidgetColorRed,
	"orange":    WidgetColorOrange,
	"yellow":    WidgetColorYellow,
	"green":     WidgetColorGreen,
	"gray":      WidgetColorGray,
	"blue":      WidgetColorBlue,
}

var intensityNames = map[string]WidgetColorIntensity{
	"Main":     WidgetColorMain,
	"Light":    WidgetColorLight,
	"Dark":     WidgetColorDark,
	"Contrast": WidgetColorContrast,
	"5":        WidgetColor5,
	"10":       WidgetColor10,
	"20":       WidgetColor20,
	"30":       WidgetColor30,
	"40":       WidgetColor40,
	"50":       WidgetColor50,
	"60":       WidgetColor60,
	"70":       WidgetColor70,
	"80":       WidgetColor80,
	"90":       WidgetColor90,
}

func parseColor(value util.Option[string]) util.Option[WidgetColor] {
	result := WidgetColor{}
	rem := value.Value
	if value.Present {
		for name, shade := range shadeNames {
			if strings.HasPrefix(rem, name) {
				result.Shade = shade
				rem = rem[len(name):]
				break
			}
		}

		if result.Shade != WidgetColorNone {
			for name, intensity := range intensityNames {
				if rem == name {
					result.Intensity = intensity
					return util.OptValue(result)
				}
			}
		}
	}

	return util.OptNone[WidgetColor]()
}
