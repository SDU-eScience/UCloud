package ucx

import (
	"bytes"
	"errors"
	"fmt"

	"ucloud.dk/shared/pkg/util"
)

type Opcode uint8

const (
	OpSysHello      Opcode = 0x01
	OpUiEvent       Opcode = 0x11
	OpUiMount       Opcode = 0x12
	OpModelPatch    Opcode = 0x13
	OpModelInput    Opcode = 0x14
	OpFormActionReq Opcode = 0x30
	OpFormActionRes Opcode = 0x31
)

type Frame struct {
	Seq        int64
	ReplyToSeq int64
	Opcode     Opcode

	SysHello      SysHello
	UiEvent       UiEvent
	UiMount       UiMount
	ModelPatch    ModelPatch
	ModelInput    ModelInput
	FormActionReq FormActionReq
	FormActionRes FormActionRes
}

type SysHello struct {
	Host     string
	Features []string
}

type UiMount struct {
	// InterfaceId identifies the mounted UCX interface instance.
	// Clients must echo this value in FormActionReq to scope actions to the
	// currently mounted interface.
	InterfaceId string
	Root        UiNode
	Version     int64
	Model       map[string]Value
}

type ModelPatch struct {
	Version int64
	Changes map[string]Value
}

type ModelInput struct {
	EventId     int64
	NodeId      string
	Path        string
	Value       Value
	BaseVersion int64
}

type UiEvent struct {
	NodeId string
	Event  string
	Value  Value
}

type FormActionReq struct {
	// InterfaceId must match the interface id received in UiMount.
	InterfaceId string
	Action      string
	Fields      map[string]Value
	Context     map[string]Value
}

type FormActionRes struct {
	Ok           bool
	ErrorCode    string
	ErrorMessage string
	FieldErrors  map[string]string
	Result       map[string]Value
}

func FrameEncode(f Frame) ([]byte, error) {
	buf := util.NewBuffer(&bytes.Buffer{})
	buf.WriteU8(uint8(f.Opcode))
	buf.WriteS64(f.Seq)
	buf.WriteS64(f.ReplyToSeq)

	switch f.Opcode {
	case OpSysHello:
		SysHelloEncode(buf, f.SysHello)
	case OpUiEvent:
		UiEventEncode(buf, f.UiEvent)
	case OpUiMount:
		UiMountEncode(buf, f.UiMount)
	case OpModelPatch:
		ModelPatchEncode(buf, f.ModelPatch)
	case OpModelInput:
		ModelInputEncode(buf, f.ModelInput)
	case OpFormActionReq:
		FormActionRequestEncode(buf, f.FormActionReq)
	case OpFormActionRes:
		FormActionResponseEncode(buf, f.FormActionRes)
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
	case OpUiEvent:
		result.UiEvent = UiEventDecode(buf)
	case OpUiMount:
		result.UiMount = UiMountDecode(buf)
	case OpModelPatch:
		result.ModelPatch = ModelPatchDecode(buf)
	case OpModelInput:
		result.ModelInput = ModelInputDecode(buf)
	case OpFormActionReq:
		result.FormActionReq = FormActionRequestDecode(buf)
	case OpFormActionRes:
		result.FormActionRes = FormActionResponseDecode(buf)
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
	buf.WriteString(msg.Host)
	buf.WriteU32(uint32(len(msg.Features)))
	for _, it := range msg.Features {
		buf.WriteString(it)
	}
}

func SysHelloDecode(buf *util.UBuffer) SysHello {
	result := SysHello{}
	result.Host = buf.ReadString()
	count := buf.ReadU32()
	result.Features = make([]string, count)
	for i := uint32(0); i < count; i++ {
		result.Features[i] = buf.ReadString()
	}
	return result
}

func UiMountEncode(buf *util.UBuffer, msg UiMount) {
	buf.WriteString(msg.InterfaceId)
	UiNodeEncode(buf, msg.Root)
	buf.WriteS64(msg.Version)
	ValueMapEncode(buf, msg.Model)
}

func UiMountDecode(buf *util.UBuffer) UiMount {
	result := UiMount{}
	result.InterfaceId = buf.ReadString()
	result.Root = UiNodeDecode(buf)
	result.Version = buf.ReadS64()
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
	buf.WriteS64(msg.Version)
	ValueMapEncode(buf, msg.Changes)
}

func ModelPatchDecode(buf *util.UBuffer) ModelPatch {
	return ModelPatch{
		Version: buf.ReadS64(),
		Changes: ValueMapDecode(buf),
	}
}

func ModelInputEncode(buf *util.UBuffer, msg ModelInput) {
	buf.WriteS64(msg.EventId)
	buf.WriteString(msg.NodeId)
	buf.WriteString(msg.Path)
	ValueEncode(buf, msg.Value)
	buf.WriteS64(msg.BaseVersion)
}

func ModelInputDecode(buf *util.UBuffer) ModelInput {
	return ModelInput{
		EventId:     buf.ReadS64(),
		NodeId:      buf.ReadString(),
		Path:        buf.ReadString(),
		Value:       ValueDecode(buf),
		BaseVersion: buf.ReadS64(),
	}
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

func FormActionRequestEncode(buf *util.UBuffer, msg FormActionReq) {
	buf.WriteString(msg.InterfaceId)
	buf.WriteString(msg.Action)
	ValueMapEncode(buf, msg.Fields)
	ValueMapEncode(buf, msg.Context)
}

func FormActionRequestDecode(buf *util.UBuffer) FormActionReq {
	return FormActionReq{
		InterfaceId: buf.ReadString(),
		Action:      buf.ReadString(),
		Fields:      ValueMapDecode(buf),
		Context:     ValueMapDecode(buf),
	}
}

func FormActionResponseEncode(buf *util.UBuffer, msg FormActionRes) {
	if msg.Ok {
		buf.WriteU8(1)
	} else {
		buf.WriteU8(0)
	}
	buf.WriteString(msg.ErrorCode)
	buf.WriteString(msg.ErrorMessage)
	StringMapEncode(buf, msg.FieldErrors)
	ValueMapEncode(buf, msg.Result)
}

func FormActionResponseDecode(buf *util.UBuffer) FormActionRes {
	return FormActionRes{
		Ok:           buf.ReadU8() != 0,
		ErrorCode:    buf.ReadString(),
		ErrorMessage: buf.ReadString(),
		FieldErrors:  StringMapDecode(buf),
		Result:       ValueMapDecode(buf),
	}
}
