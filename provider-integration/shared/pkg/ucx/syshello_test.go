package ucx

import (
	"bytes"
	"strings"
	"testing"

	"ucloud.dk/shared/pkg/util"
)

func TestSysHelloRoundTripPayload(t *testing.T) {
	original := Frame{
		Seq:        1,
		ReplyToSeq: 0,
		Opcode:     OpSysHello,
		SysHello:   SysHello{Payload: "hello"},
	}

	encoded, err := FrameEncode(original)
	if err != nil {
		t.Fatalf("unexpected encode error: %v", err)
	}

	decoded, err := FrameDecode(encoded)
	if err != nil {
		t.Fatalf("unexpected decode error: %v", err)
	}

	if decoded.SysHello.Payload != "hello" {
		t.Fatalf("unexpected payload: %q", decoded.SysHello.Payload)
	}
}

func TestSysHelloRejectsPayloadAt64KiB(t *testing.T) {
	payload := strings.Repeat("a", 64*1024)
	frame := Frame{
		Seq:        1,
		ReplyToSeq: 0,
		Opcode:     OpSysHello,
		SysHello:   SysHello{Payload: payload},
	}

	_, err := FrameEncode(frame)
	if err == nil {
		t.Fatal("expected encode error for oversized syshello payload")
	}
}

func TestSysHelloDecodeRejectsPayloadAt64KiB(t *testing.T) {
	payload := strings.Repeat("a", 64*1024)
	buf := util.NewBuffer(&bytes.Buffer{})
	buf.WriteU8(uint8(OpSysHello))
	buf.WriteS64(1)
	buf.WriteS64(0)
	buf.WriteString(payload)

	if buf.Error != nil {
		t.Fatalf("unexpected setup error: %v", buf.Error)
	}

	_, err := FrameDecode(buf.ReadRemainingBytes())
	if err == nil {
		t.Fatal("expected decode error for oversized syshello payload")
	}
}
