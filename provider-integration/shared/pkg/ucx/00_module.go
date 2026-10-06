package ucx

import (
	"bytes"
	"errors"
	"fmt"

	"ucloud.dk/shared/pkg/util"
)

const maxSysHelloPayloadBytes = 1024 * 1024 * 8

type Opcode uint8

const (
	OpSysHello     Opcode = 0x01
	OpPing         Opcode = 0x02
	OpPong         Opcode = 0x03
	OpUiEvent      Opcode = 0x11
	OpUiMount      Opcode = 0x12
	OpModelPatch   Opcode = 0x13
	OpModelInput   Opcode = 0x14
	OpTableUpdate  Opcode = 0x15
	OpStringAppend Opcode = 0x16
	OpRpcRequest   Opcode = 0x20
	OpRpcResponse  Opcode = 0x21
)

type Frame struct {
	Seq        int64
	ReplyToSeq int64
	Opcode     Opcode

	SysHello       SysHello
	UiEvent        UiEvent
	UiMount        UiMount
	ModelPatch     ModelPatch
	ModelInput     ModelInput
	TableUpdate    TableUpdate
	StringAppend   StringAppend
	RpcRequestName string
	RpcPayload     map[string]Value
	RpcStatus      int
}

type SysHello struct {
	Payload string
}

type StringAppend struct {
	Path  string
	Chunk string
}

type UiMount struct {
	// InterfaceId identifies the mounted UCX interface instance.
	InterfaceId string
	Root        UiNode
	Model       map[string]Value
}

type ModelPatch struct {
	Changes map[string]Value
}

type ModelInput struct {
	EventId int64
	NodeId  string
	Path    string
	Value   Value
}

type TableColumnSortType uint8

const (
	TableColumnSortText     TableColumnSortType = 0
	TableColumnSortNumber   TableColumnSortType = 1
	TableColumnSortDuration TableColumnSortType = 2
	TableColumnSortRatio    TableColumnSortType = 3
	TableColumnSortCapacity TableColumnSortType = 4
	TableColumnSortIp       TableColumnSortType = 5
	TableColumnSortBool     TableColumnSortType = 6
)

const (
	TableColumnFlagCopy uint8 = 1 << iota
)

type TableColumn struct {
	Key      string
	Label    string
	JsonPath string
	SortType TableColumnSortType
	Copy     bool
}

type TableRowAction struct {
	Id             string
	Enabled        bool
	DisabledReason string
	Text           string
}

type TableRow struct {
	Key     string
	Group   string
	Cells   []string
	Actions []TableRowAction
	Busy    bool
}

type TableUpdate struct {
	TableId  string
	Revision int64
	Snapshot bool
	Columns  []TableColumn
	Upserts  []TableRow
	Removed  []string
}

type UiEventType string

const (
	UiEventClick    UiEventType = "click"
	UiEventAction   UiEventType = "action"
	UiEventSubmit   UiEventType = "submit"
	UiEventChange   UiEventType = "change"
	UiEventFocus    UiEventType = "focus"
	UiEventBlur     UiEventType = "blur"
	UiEventActivate UiEventType = "activate"
	UiEventClose    UiEventType = "close"
	UiEventSave     UiEventType = "save"
)

type UiEvent struct {
	NodeId string
	Event  string
	Value  Value
}

func FrameEncode(f Frame) ([]byte, error) {
	buf := util.NewBuffer(&bytes.Buffer{})
	buf.WriteU8(uint8(f.Opcode))
	buf.WriteS64(f.Seq)
	buf.WriteS64(f.ReplyToSeq)

	switch f.Opcode {
	case OpSysHello:
		SysHelloEncode(buf, f.SysHello)
	case OpPing, OpPong:
	case OpUiEvent:
		UiEventEncode(buf, f.UiEvent)
	case OpUiMount:
		UiMountEncode(buf, f.UiMount)
	case OpModelPatch:
		ModelPatchEncode(buf, f.ModelPatch)
	case OpModelInput:
		ModelInputEncode(buf, f.ModelInput)
	case OpTableUpdate:
		TableUpdateEncode(buf, f.TableUpdate)
	case OpStringAppend:
		StringAppendEncode(buf, f.StringAppend)
	case OpRpcRequest:
		buf.WriteStringVarint(f.RpcRequestName)
		RpcPayloadEncode(buf, f.RpcPayload)
	case OpRpcResponse:
		buf.WriteU8(uint8(f.RpcStatus))
		RpcPayloadEncode(buf, f.RpcPayload)
	default:
		return nil, fmt.Errorf("unknown opcode: %d", f.Opcode)
	}

	if buf.Error != nil {
		return nil, buf.Error
	}
	return buf.ReadRemainingBytes(), nil
}

func FrameDecode(data []byte) (Frame, error) {
	buf := util.NewBufferBytes(data)
	op := Opcode(buf.ReadU8())
	seq := buf.ReadS64()
	replyTo := buf.ReadS64()

	result := Frame{Seq: seq, ReplyToSeq: replyTo, Opcode: op}

	switch op {
	case OpSysHello:
		result.SysHello = SysHelloDecode(buf)
	case OpPing, OpPong:
	case OpUiEvent:
		result.UiEvent = UiEventDecode(buf)
	case OpUiMount:
		result.UiMount = UiMountDecode(buf)
	case OpModelPatch:
		result.ModelPatch = ModelPatchDecode(buf)
	case OpModelInput:
		result.ModelInput = ModelInputDecode(buf)
	case OpTableUpdate:
		result.TableUpdate = TableUpdateDecode(buf)
	case OpStringAppend:
		result.StringAppend = StringAppendDecode(buf)
	case OpRpcRequest:
		result.RpcRequestName = buf.ReadStringVarint()
		result.RpcPayload = RpcPayloadDecode(buf)
	case OpRpcResponse:
		result.RpcStatus = int(buf.ReadU8())
		result.RpcPayload = RpcPayloadDecode(buf)
	default:
		return Frame{}, fmt.Errorf("unknown opcode: %d", op)
	}

	if buf.Error != nil {
		return Frame{}, buf.Error
	}
	if !buf.IsEmpty() {
		return Frame{}, errors.New("trailing bytes in frame")
	}

	return result, nil
}

func SysHelloEncode(buf *util.UBuffer, msg SysHello) {
	payloadBytes := []byte(msg.Payload)
	if len(payloadBytes) >= maxSysHelloPayloadBytes {
		buf.Error = fmt.Errorf("ucx syshello payload too large: %d bytes", len(payloadBytes))
		return
	}

	buf.WriteStringVarint(msg.Payload)
}

func SysHelloDecode(buf *util.UBuffer) SysHello {
	result := SysHello{Payload: buf.ReadStringVarint()}
	if len([]byte(result.Payload)) >= maxSysHelloPayloadBytes {
		buf.Error = fmt.Errorf("ucx syshello payload too large: %d bytes", len([]byte(result.Payload)))
	}
	return result
}

func UiMountEncode(buf *util.UBuffer, msg UiMount) {
	buf.WriteStringVarint(msg.InterfaceId)
	UiNodeEncode(buf, msg.Root)
	ValueMapEncode(buf, msg.Model)
}

func UiMountDecode(buf *util.UBuffer) UiMount {
	result := UiMount{}
	result.InterfaceId = buf.ReadStringVarint()
	result.Root = UiNodeDecode(buf)
	result.Model = ValueMapDecode(buf)
	return result
}

func UiNodeEncode(buf *util.UBuffer, node UiNode) {
	buf.WriteStringVarint(node.Id)
	buf.WriteStringVarint(node.Component)
	ValueMapEncode(buf, node.Props)
	buf.WriteStringVarint(node.BindPath)
	if node.Optimistic {
		buf.WriteU8(1)
	} else {
		buf.WriteU8(0)
	}
	buf.WriteU32(uint32(len(node.ChildNodes)))
	for _, child := range node.ChildNodes {
		UiNodeEncode(buf, child)
	}
}

func UiNodeDecode(buf *util.UBuffer) UiNode {
	result := UiNode{}
	result.Id = buf.ReadStringVarint()
	result.Component = buf.ReadStringVarint()
	result.Props = ValueMapDecode(buf)
	result.BindPath = buf.ReadStringVarint()
	result.Optimistic = buf.ReadU8() != 0
	childrenCount := buf.ReadU32()
	result.ChildNodes = make([]UiNode, childrenCount)
	for i := uint32(0); i < childrenCount; i++ {
		result.ChildNodes[i] = UiNodeDecode(buf)
	}
	return result
}

func ModelPatchEncode(buf *util.UBuffer, msg ModelPatch) {
	ValueMapEncode(buf, msg.Changes)
}

func ModelPatchDecode(buf *util.UBuffer) ModelPatch {
	return ModelPatch{
		Changes: ValueMapDecode(buf),
	}
}

func StringAppendEncode(buf *util.UBuffer, msg StringAppend) {
	buf.WriteStringVarint(msg.Path)
	buf.WriteStringVarint(msg.Chunk)
}

func StringAppendDecode(buf *util.UBuffer) StringAppend {
	return StringAppend{
		Path:  buf.ReadStringVarint(),
		Chunk: buf.ReadStringVarint(),
	}
}

func ModelInputEncode(buf *util.UBuffer, msg ModelInput) {
	buf.WriteS64(msg.EventId)
	buf.WriteStringVarint(msg.NodeId)
	buf.WriteStringVarint(msg.Path)
	ValueEncode(buf, msg.Value)
}

func ModelInputDecode(buf *util.UBuffer) ModelInput {
	return ModelInput{
		EventId: buf.ReadS64(),
		NodeId:  buf.ReadStringVarint(),
		Path:    buf.ReadStringVarint(),
		Value:   ValueDecode(buf),
	}
}

func TableUpdateEncode(buf *util.UBuffer, msg TableUpdate) {
	buf.WriteStringVarint(msg.TableId)
	buf.WriteS64(msg.Revision)
	if msg.Snapshot {
		buf.WriteU8(1)
	} else {
		buf.WriteU8(0)
	}

	buf.WriteU32(uint32(len(msg.Columns)))
	for _, col := range msg.Columns {
		buf.WriteStringVarint(col.Key)
		buf.WriteStringVarint(col.Label)
		buf.WriteStringVarint(col.JsonPath)
		buf.WriteU8(uint8(col.SortType))

		var flags uint8
		if col.Copy {
			flags |= TableColumnFlagCopy
		}
		buf.WriteU8(flags)
	}

	buf.WriteU32(uint32(len(msg.Upserts)))
	for _, row := range msg.Upserts {
		buf.WriteStringVarint(row.Key)
		buf.WriteStringVarint(row.Group)
		buf.WriteU32(uint32(len(row.Cells)))
		for _, cell := range row.Cells {
			buf.WriteStringVarint(cell)
		}
	}

	buf.WriteU32(uint32(len(msg.Removed)))
	for _, key := range msg.Removed {
		buf.WriteStringVarint(key)
	}

	hasActions := false
	for _, row := range msg.Upserts {
		if len(row.Actions) > 0 {
			hasActions = true
			break
		}
	}

	if hasActions {
		buf.WriteU8(1)
		buf.WriteU32(uint32(len(msg.Upserts)))
		for _, row := range msg.Upserts {
			buf.WriteU32(uint32(len(row.Actions)))
			for _, action := range row.Actions {
				buf.WriteStringVarint(action.Id)
				if action.Enabled {
					buf.WriteU8(1)
				} else {
					buf.WriteU8(0)
				}
				buf.WriteStringVarint(action.DisabledReason)
				buf.WriteStringVarint(action.Text)
			}
		}
	} else {
		buf.WriteU8(0)
	}

	hasBusy := false
	for _, row := range msg.Upserts {
		if row.Busy {
			hasBusy = true
			break
		}
	}
	if hasBusy {
		buf.WriteU8(1)
		buf.WriteU32(uint32(len(msg.Upserts)))
		for _, row := range msg.Upserts {
			if row.Busy {
				buf.WriteU8(1)
			} else {
				buf.WriteU8(0)
			}
		}
	}
}

func TableUpdateDecode(buf *util.UBuffer) TableUpdate {
	result := TableUpdate{
		TableId:  buf.ReadStringVarint(),
		Revision: buf.ReadS64(),
		Snapshot: buf.ReadU8() != 0,
	}

	columnCount := buf.ReadU32()
	result.Columns = make([]TableColumn, columnCount)
	for i := uint32(0); i < columnCount; i++ {
		key := buf.ReadStringVarint()
		label := buf.ReadStringVarint()
		jsonPath := buf.ReadStringVarint()
		sortType := buf.ReadU8()
		flags := buf.ReadU8()
		result.Columns[i] = TableColumn{
			Key:      key,
			Label:    label,
			JsonPath: jsonPath,
			SortType: TableColumnSortType(sortType),
			Copy:     flags&TableColumnFlagCopy != 0,
		}
	}

	rowCount := buf.ReadU32()
	result.Upserts = make([]TableRow, rowCount)
	for i := uint32(0); i < rowCount; i++ {
		row := TableRow{Key: buf.ReadStringVarint(), Group: buf.ReadStringVarint()}
		cellCount := buf.ReadU32()
		row.Cells = make([]string, cellCount)
		for j := uint32(0); j < cellCount; j++ {
			row.Cells[j] = buf.ReadStringVarint()
		}
		result.Upserts[i] = row
	}

	removedCount := buf.ReadU32()
	result.Removed = make([]string, removedCount)
	for i := uint32(0); i < removedCount; i++ {
		result.Removed[i] = buf.ReadStringVarint()
	}

	if !buf.IsEmpty() && buf.ReadU8() != 0 {
		actionRowCount := buf.ReadU32()
		for i := uint32(0); i < actionRowCount && int(i) < len(result.Upserts); i++ {
			actionCount := buf.ReadU32()
			actions := make([]TableRowAction, actionCount)
			for j := uint32(0); j < actionCount; j++ {
				actions[j] = TableRowAction{
					Id:             buf.ReadStringVarint(),
					Enabled:        buf.ReadU8() != 0,
					DisabledReason: buf.ReadStringVarint(),
					Text:           buf.ReadStringVarint(),
				}
			}
			result.Upserts[i].Actions = actions
		}
	}

	if !buf.IsEmpty() && buf.ReadU8() != 0 {
		busyRowCount := buf.ReadU32()
		for i := uint32(0); i < busyRowCount && int(i) < len(result.Upserts); i++ {
			result.Upserts[i].Busy = buf.ReadU8() != 0
		}
	}

	return result
}

func UiEventEncode(buf *util.UBuffer, msg UiEvent) {
	buf.WriteStringVarint(msg.NodeId)
	buf.WriteStringVarint(msg.Event)
	ValueEncode(buf, msg.Value)
}

func UiEventDecode(buf *util.UBuffer) UiEvent {
	return UiEvent{
		NodeId: buf.ReadStringVarint(),
		Event:  buf.ReadStringVarint(),
		Value:  ValueDecode(buf),
	}
}

func RpcPayloadEncode(buf *util.UBuffer, msg map[string]Value) {
	ValueMapEncode(buf, msg)
}

func RpcPayloadDecode(buf *util.UBuffer) map[string]Value {
	return ValueMapDecode(buf)
}
