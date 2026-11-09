package inference

import (
	"testing"

	"ucloud.dk/shared/pkg/util"
)

func TestInferenceChatUsageMissingIsNotEstimated(t *testing.T) {
	usage := inferenceChatUsage(util.OptNone[InferenceChatUsage]())

	if usage != (InferenceChatUsage{}) {
		t.Fatalf("expected zero usage, got %+v", usage)
	}
}

func TestInferenceRejectsNegativeUsage(t *testing.T) {
	if inferenceChatUsageValid(InferenceChatUsage{PromptTokens: -1}) {
		t.Fatal("negative chat usage was accepted")
	}
}

func TestInferenceUsesReportedUsage(t *testing.T) {
	chat := inferenceChatUsage(util.OptValue(InferenceChatUsage{PromptTokens: 10, CompletionTokens: 5}))
	if chat.TotalTokens != 15 {
		t.Fatalf("expected chat total to be completed, got %+v", chat)
	}
}

func TestInferenceUsageArithmeticSplitsAndSaturates(t *testing.T) {
	if got := inferenceUsageMultiply(1, 1999); got != 1999 {
		t.Fatalf("unexpected multiplication result: %d", got)
	}
	if got := inferenceUsageAdd(inferenceUsageMultiply(int(^uint(0)>>1), int64(^uint64(0)>>1)), 1); got <= 0 {
		t.Fatalf("expected saturated positive result, got %d", got)
	}
	weighted := int64(1_999_999)
	if usage, remainder := weighted/InferencePriceScale, weighted%InferencePriceScale; usage != 1 || remainder != 999_999 {
		t.Fatalf("expected 1 complete microcredit and a remainder of 999999, got %d and %d", usage, remainder)
	}
}

func TestInferenceChatDeltaContentParts(t *testing.T) {
	var delta InferenceChatDelta
	if err := delta.UnmarshalJSON([]byte(`{"content":[{"type":"text","text":"hello"},{"type":"text","text":" world"}],"reasoning_content":[{"type":"reasoning_text","text":"thinking"}]}`)); err != nil {
		t.Fatalf("unexpected unmarshal error: %v", err)
	}
	if delta.Content != "hello world" {
		t.Fatalf("unexpected content: %q", delta.Content)
	}
	if delta.Reasoning != "thinking" {
		t.Fatalf("unexpected reasoning: %q", delta.Reasoning)
	}
}

func TestInferenceSSEDataPayloadWithEvent(t *testing.T) {
	payload := inferenceSSEDataPayload("event: chat.completion.chunk\ndata: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n")
	if payload != `{"choices":[{"delta":{"content":"hi"}}]}` {
		t.Fatalf("unexpected payload: %q", payload)
	}
}
