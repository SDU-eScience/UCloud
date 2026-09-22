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
	OpSysHello    Opcode = 0x01
	OpPing        Opcode = 0x02
	OpPong        Opcode = 0x03
	OpUiEvent     Opcode = 0x11
	OpUiMount     Opcode = 0x12
	OpModelPatch  Opcode = 0x13
	OpModelInput  Opcode = 0x14
	OpTableUpdate Opcode = 0x15
	OpRpcRequest  Opcode = 0x20
	OpRpcResponse Opcode = 0x21
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
	RpcRequestName string
	RpcPayload     map[string]Value
	RpcStatus      int
}

type SysHello struct {
	Payload string
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

type TableColumn struct {
	Key      string
	Label    string
	JsonPath string
}

type TableRow struct {
	Key   string
	Group string
	Cells []string
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
	UiEventClick  UiEventType = "click"
	UiEventSubmit UiEventType = "submit"
	UiEventChange UiEventType = "change"
	UiEventFocus  UiEventType = "focus"
	UiEventBlur   UiEventType = "blur"
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
	case OpRpcRequest:
		buf.WriteString(f.RpcRequestName)
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
	case OpRpcRequest:
		result.RpcRequestName = buf.ReadString()
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

	buf.WriteString(msg.Payload)
}

func SysHelloDecode(buf *util.UBuffer) SysHello {
	result := SysHello{Payload: buf.ReadString()}
	if len([]byte(result.Payload)) >= maxSysHelloPayloadBytes {
		buf.Error = fmt.Errorf("ucx syshello payload too large: %d bytes", len([]byte(result.Payload)))
	}
	return result
}

func UiMountEncode(buf *util.UBuffer, msg UiMount) {
	buf.WriteString(msg.InterfaceId)
	UiNodeEncode(buf, msg.Root)
	ValueMapEncode(buf, msg.Model)
}

func UiMountDecode(buf *util.UBuffer) UiMount {
	result := UiMount{}
	result.InterfaceId = buf.ReadString()
	result.Root = UiNodeDecode(buf)
	result.Model = ValueMapDecode(buf)
	return result
}

func UiNodeEncode(buf *util.UBuffer, node UiNode) {
	buf.WriteString(node.Id)
	buf.WriteString(node.Component)
	ValueMapEncode(buf, node.Props)
	buf.WriteString(node.BindPath)
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
	result.Id = buf.ReadString()
	result.Component = buf.ReadString()
	result.Props = ValueMapDecode(buf)
	result.BindPath = buf.ReadString()
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

func ModelInputEncode(buf *util.UBuffer, msg ModelInput) {
	buf.WriteS64(msg.EventId)
	buf.WriteString(msg.NodeId)
	buf.WriteString(msg.Path)
	ValueEncode(buf, msg.Value)
}

func ModelInputDecode(buf *util.UBuffer) ModelInput {
	return ModelInput{
		EventId: buf.ReadS64(),
		NodeId:  buf.ReadString(),
		Path:    buf.ReadString(),
		Value:   ValueDecode(buf),
	}
}

func TableUpdateEncode(buf *util.UBuffer, msg TableUpdate) {
	buf.WriteString(msg.TableId)
	buf.WriteS64(msg.Revision)
	if msg.Snapshot {
		buf.WriteU8(1)
	} else {
		buf.WriteU8(0)
	}

	buf.WriteU32(uint32(len(msg.Columns)))
	for _, col := range msg.Columns {
		buf.WriteString(col.Key)
		buf.WriteString(col.Label)
		buf.WriteString(col.JsonPath)
	}

	buf.WriteU32(uint32(len(msg.Upserts)))
	for _, row := range msg.Upserts {
		buf.WriteString(row.Key)
		buf.WriteString(row.Group)
		buf.WriteU32(uint32(len(row.Cells)))
		for _, cell := range row.Cells {
			buf.WriteString(cell)
		}
	}

	buf.WriteU32(uint32(len(msg.Removed)))
	for _, key := range msg.Removed {
		buf.WriteString(key)
	}
}

func TableUpdateDecode(buf *util.UBuffer) TableUpdate {
	result := TableUpdate{
		TableId:  buf.ReadString(),
		Revision: buf.ReadS64(),
		Snapshot: buf.ReadU8() != 0,
	}

	columnCount := buf.ReadU32()
	result.Columns = make([]TableColumn, columnCount)
	for i := uint32(0); i < columnCount; i++ {
		result.Columns[i] = TableColumn{
			Key:      buf.ReadString(),
			Label:    buf.ReadString(),
			JsonPath: buf.ReadString(),
		}
	}

	rowCount := buf.ReadU32()
	result.Upserts = make([]TableRow, rowCount)
	for i := uint32(0); i < rowCount; i++ {
		row := TableRow{Key: buf.ReadString(), Group: buf.ReadString()}
		cellCount := buf.ReadU32()
		row.Cells = make([]string, cellCount)
		for j := uint32(0); j < cellCount; j++ {
			row.Cells[j] = buf.ReadString()
		}
		result.Upserts[i] = row
	}

	removedCount := buf.ReadU32()
	result.Removed = make([]string, removedCount)
	for i := uint32(0); i < removedCount; i++ {
		result.Removed[i] = buf.ReadString()
	}

	return result
}

func UiEventEncode(buf *util.UBuffer, msg UiEvent) {
	buf.WriteString(msg.NodeId)
	buf.WriteString(msg.Event)
	ValueEncode(buf, msg.Value)
}

func UiEventDecode(buf *util.UBuffer) UiEvent {
	return UiEvent{
		NodeId: buf.ReadString(),
		Event:  buf.ReadString(),
		Value:  ValueDecode(buf),
	}
}

func RpcPayloadEncode(buf *util.UBuffer, msg map[string]Value) {
	ValueMapEncode(buf, msg)
}

func RpcPayloadDecode(buf *util.UBuffer) map[string]Value {
	return ValueMapDecode(buf)
}
