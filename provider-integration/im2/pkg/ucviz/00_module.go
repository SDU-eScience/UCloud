package ucviz

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"ucloud.dk/pkg/util"
)

const jobVizHeader = "#- UCloud JobViz -#"

type WidgetId struct {
	Id string `json:"id"`
}

type WidgetPacketHeader struct {
	Action WidgetAction `json:"action"`
}

type WidgetType uint16

const (
	WidgetTypeLabel WidgetType = iota
	WidgetTypeProgressBar
	WidgetTypeTable
	WidgetTypeContainer
	WidgetTypeDiagram
)

type WidgetWindow uint8

const (
	WidgetWindowMain WidgetWindow = iota
	WidgetWindowAux1
	WidgetWindowAux2
)

type WidgetLocation struct {
	Window WidgetWindow `json:"window"`
	Tab    string       `json:"tab"`
}

type Widget struct {
	Id       string         `json:"id"`
	Type     WidgetType     `json:"type"`
	Location WidgetLocation `json:"location"`
}

type WidgetColorShade uint8

const (
	WidgetColorNone WidgetColorShade = iota
	WidgetColorPrimary
	WidgetColorSecondary
	WidgetColorError
	WidgetColorWarning
	WidgetColorInfo
	WidgetColorSuccess
	WidgetColorText
	WidgetColorPurple
	WidgetColorRed
	WidgetColorOrange
	WidgetColorYellow
	WidgetColorGreen
	WidgetColorGray
	WidgetColorBlue
)

type WidgetColorIntensity uint8

const (
	WidgetColorMain WidgetColorIntensity = iota
	WidgetColorLight
	WidgetColorDark
	WidgetColorContrast

	WidgetColor5
	WidgetColor10
	WidgetColor20
	WidgetColor30
	WidgetColor40
	WidgetColor50
	WidgetColor60
	WidgetColor70
	WidgetColor80
	WidgetColor90
)

type WidgetColor struct {
	Shade     WidgetColorShade     `json:"shade"`
	Intensity WidgetColorIntensity `json:"intensity"`
}

type WidgetDimensions struct {
	Minimum uint16 `json:"minimum"`
	Maximum uint16 `json:"maximum"`
}

type WidgetDirection uint8

const (
	WidgetDirectionRow WidgetDirection = iota
	WidgetDirectionColumn
)

type WidgetContainer struct {
	Foreground WidgetColor           `json:"foreground"`
	Background WidgetColor           `json:"background"`
	Width      WidgetDimensions      `json:"width"`
	Height     WidgetDimensions      `json:"height"`
	Direction  WidgetDirection       `json:"direction"`
	Grow       uint8                 `json:"grow"`
	Children   []WidgetContainerOrId `json:"children"`
}

type WidgetContainerOrId struct {
	Container util.Option[WidgetContainer] `json:"container"`
	Id        util.Option[WidgetId]        `json:"id"`
}

type WidgetLabelAlign uint8

const (
	WidgetLabelAlignBegin WidgetLabelAlign = iota
	WidgetLabelAlignCenter
	WidgetLabelAlignEnd
)

type WidgetLabel struct {
	Align WidgetLabelAlign `json:"align"`
	Text  string           `json:"text"`
}

type WidgetTable struct {
	Rows []WidgetTableRow `json:"rows"`
}

type WidgetTableRow struct {
	Cells []WidgetTableCell `json:"cells"`
}

type WidgetTableCellFlag uint8

const (
	WidgetTableCellHeader WidgetTableCellFlag = 1 << iota
)

type WidgetTableCell struct {
	Flags  WidgetTableCellFlag `json:"flags"`
	Width  WidgetDimensions    `json:"width"`
	Height WidgetDimensions    `json:"height"`
	Label  WidgetLabel         `json:"label"`
}

type WidgetVegaLiteDiagram struct {
	Definition json.RawMessage `json:"definition"`
	Data       json.RawMessage `json:"data"`
}

type WidgetProgressBar struct {
	Progress float64 `json:"progress"` // Value between 0 and 1
}

type WidgetAction uint8

const (
	WidgetActionCreate WidgetAction = iota
	WidgetActionUpdate
	WidgetActionDelete
)

type WidgetStreamEncoding string

const (
	WidgetStreamBinary WidgetStreamEncoding = "binary"
	WidgetStreamJson   WidgetStreamEncoding = "json"
)

type WidgetStream struct {
	writer     *bufio.Writer
	binEncoder *util.BinaryEncoder
	encoding   WidgetStreamEncoding
	Err        error
}

func NewWidgetStream(writer io.Writer, encoding WidgetStreamEncoding) *WidgetStream {
	s := &WidgetStream{
		writer: bufio.NewWriter(writer),
	}

	s.binEncoder = util.NewBinaryEncoder(s.writer)

	_, s.Err = s.writer.WriteString(fmt.Sprintf("%s encoding=%s\n", jobVizHeader, encoding))
	return s
}

func (s *WidgetStream) CreateLabel(id string, location WidgetLocation, label WidgetLabel) {
	s.writeData(WidgetPacketHeader{Action: WidgetActionCreate}, false)
	s.writeData(Widget{Id: id, Type: WidgetTypeLabel, Location: location}, false)
	s.writeData(label, true)
}

func (s *WidgetStream) CreateContainer(id string, location WidgetLocation, container WidgetContainer) {
	s.writeData(WidgetPacketHeader{Action: WidgetActionCreate}, false)
	s.writeData(Widget{Id: id, Type: WidgetTypeContainer, Location: location}, false)
	s.writeData(container, true)
}

func (s *WidgetStream) CreateProgressBar(id string, location WidgetLocation, progressBar WidgetProgressBar) {
	s.writeData(WidgetPacketHeader{Action: WidgetActionCreate}, false)
	s.writeData(Widget{Id: id, Type: WidgetTypeProgressBar, Location: location}, false)
	s.writeData(progressBar, true)
}

func (s *WidgetStream) CreateTable(id string, location WidgetLocation, table WidgetTable) {
	s.writeData(WidgetPacketHeader{Action: WidgetActionCreate}, false)
	s.writeData(Widget{Id: id, Type: WidgetTypeTable, Location: location}, false)
	s.writeData(table, true)
}

func (s *WidgetStream) CreateDiagram(id string, location WidgetLocation, diagram WidgetVegaLiteDiagram) {
	s.writeData(WidgetPacketHeader{Action: WidgetActionCreate}, false)
	s.writeData(Widget{Id: id, Type: WidgetTypeDiagram, Location: location}, false)
	s.writeData(diagram, true)
}

func (s *WidgetStream) AppendTableRows(id string, rows []WidgetTableRow) {
	s.writeData(WidgetPacketHeader{Action: WidgetActionUpdate}, false)
	s.writeData(Widget{Id: id, Type: WidgetTypeTable}, false)
	s.writeData(WidgetTable{Rows: rows}, true)
}

func (s *WidgetStream) AppendDiagramData(id string, data json.RawMessage) {
	s.writeData(WidgetPacketHeader{Action: WidgetActionUpdate}, false)
	s.writeData(Widget{Id: id, Type: WidgetTypeDiagram}, false)
	s.writeData(WidgetVegaLiteDiagram{Data: data}, true)
}

func (s *WidgetStream) UpdateProgress(id string, progress float64) {
	s.writeData(WidgetPacketHeader{Action: WidgetActionUpdate}, false)
	s.writeData(Widget{Id: id, Type: WidgetTypeProgressBar}, false)
	s.writeData(WidgetProgressBar{Progress: progress}, true)
}

func (s *WidgetStream) Delete(id string) {
	s.writeData(WidgetPacketHeader{Action: WidgetActionDelete}, false)
	s.writeData(WidgetId{Id: id}, true)
}

func (s *WidgetStream) writeData(data any, flush bool) {
	if s.Err == nil {
		if s.encoding == WidgetStreamBinary {
			s.Err = s.binEncoder.Encode(data)
		} else {
			data, err := json.Marshal(data)
			if err != nil {
				s.Err = err
			} else {
				_, s.Err = s.writer.Write(data)
				if s.Err == nil {
					_, s.Err = s.writer.Write([]byte("\n"))
				}
			}
		}

		if flush && s.Err == nil {
			s.Err = s.writer.Flush()
		}
	}
}
