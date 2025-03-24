package ucviz

import (
	"bytes"
	"fmt"
	"io"
	"os"
	"strings"
	"ucloud.dk/pkg/termio"
	"ucloud.dk/pkg/util"
)

var cliIdBase = ""
var cliIdCounter = 0

func cliId() string {
	result := fmt.Sprintf("%s%d", cliIdBase, cliIdCounter)
	cliIdCounter++
	return result
}

func HandleCli(args []string, uiChannel io.Writer, dataChannel io.Writer) {
	if len(args) == 0 {
		printCliHelp("")
	}

	cliIdBase = util.RandomToken(8)

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
	case "append-rows":
	case "append-data":

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
	uiChannel := NewWidgetStream(uiChannelB, WidgetStreamJson)
	dataChannel := NewWidgetStream(dataChannelB, WidgetStreamJson)

	p := NewParser(docText)
	node, err := p.Parse()
	if err != nil {
		termio.Write("%s", err.Error())
		os.Exit(1)
	}

	_, err = processNode(uiChannel, dataChannel, p, node, util.OptNone[DocNode]())
	return uiChannelB.Bytes(), dataChannelB.Bytes(), err
}

func processNode(uiChannel *WidgetStream, dataChannel *WidgetStream, p *Parser, node DocNode, parent util.Option[DocNode]) (string, error) {
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
			childId, err := processNode(uiChannel, dataChannel, p, child, util.OptValue(node))
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
	case "Row":
		childrenAllowed = true
	case "Cell":
		childrenAllowed = false
		textExpected = true

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
		if value := node.AttributeInt("value"); value.Present {
			progress.Progress = float64(value.Value) / 100.0
		}
		uiChannel.CreateProgressBar(id, loc, progress)

	case "Chart":
		childrenAllowed = true
	case "Def":
		childrenAllowed = false
		textExpected = true

	case "CodeSnippet":
		childrenAllowed = false
	}

	if !childrenAllowed && len(node.Children) > 0 {
		return "", p.errorAt(node.Location, "this node cannot have any children")
	}

	if !textExpected && len(node.Text) > 0 {
		return "", p.errorAt(node.Location, "this node should not have any text")
	}

	return id, nil
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
