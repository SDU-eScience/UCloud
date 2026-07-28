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

func TestInferenceTranscriptionUsageMissingIsNotEstimated(t *testing.T) {
	usage := inferenceTranscriptionUsage(util.OptNone[InferenceTranscriptionUsage]())

	if usage != (InferenceTranscriptionUsage{}) {
		t.Fatalf("expected zero usage, got %+v", usage)
	}
}

func TestInferenceImageUsageMissingIsNotEstimated(t *testing.T) {
	usage := inferenceImageUsage(util.OptNone[InferenceImageGenerationUsage]())

	if usage != (InferenceImageGenerationUsage{}) {
		t.Fatalf("expected zero usage, got %+v", usage)
	}
}

func TestInferenceMockImageUsageIsEstimated(t *testing.T) {
	usage := inferenceEstimateImageUsage(InferenceImageGenerationRequest{N: util.OptValue(2), Size: util.OptValue("1024x1024")}, 0)
	if usage.OutputTokens <= 0 || usage.TotalTokens != usage.OutputTokens {
		t.Fatalf("expected locally estimated image usage, got %+v", usage)
	}
}

func TestInferenceRejectsNegativeUsage(t *testing.T) {
	if inferenceChatUsageValid(InferenceChatUsage{PromptTokens: -1}) {
		t.Fatal("negative chat usage was accepted")
	}
	if inferenceTranscriptionUsageValid(InferenceTranscriptionUsage{OutputTokens: -1}) {
		t.Fatal("negative transcription usage was accepted")
	}
	if inferenceImageUsageValid(InferenceImageGenerationUsage{TotalTokens: -1}) {
		t.Fatal("negative image usage was accepted")
	}
}

func TestInferenceUsesReportedUsage(t *testing.T) {
	chat := inferenceChatUsage(util.OptValue(InferenceChatUsage{PromptTokens: 10, CompletionTokens: 5}))
	if chat.TotalTokens != 15 {
		t.Fatalf("expected chat total to be completed, got %+v", chat)
	}
	transcription := inferenceTranscriptionUsage(util.OptValue(InferenceTranscriptionUsage{InputTokens: 3, OutputTokens: 4}))
	if transcription.TotalTokens != 7 {
		t.Fatalf("expected transcription total to be completed, got %+v", transcription)
	}
	image := inferenceImageUsage(util.OptValue(InferenceImageGenerationUsage{InputTokens: 2, OutputTokens: 8}))
	if image.TotalTokens != 10 {
		t.Fatalf("expected image total to be completed, got %+v", image)
	}
}

func TestInferenceUsageArithmeticRoundsUpAndSaturates(t *testing.T) {
	if got := inferenceUsageMultiply(1, 1999); got != 1999 {
		t.Fatalf("unexpected multiplication result: %d", got)
	}
	if got := inferenceUsageAdd(inferenceUsageMultiply(int(^uint(0)>>1), int(^uint(0)>>1)), 1); got <= 0 {
		t.Fatalf("expected saturated positive result, got %d", got)
	}
	if got := inferenceNormalizeUsage(1999); got != 2 {
		t.Fatalf("expected ceiling normalization to produce 2, got %d", got)
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
