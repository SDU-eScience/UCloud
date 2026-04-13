package k8s

import (
	"testing"

	"ucloud.dk/shared/pkg/util"
)

func TestInferenceChatUsageAlwaysPresent(t *testing.T) {
	usage := inferenceChatUsageFromText(
		InferenceChatRequest{Messages: []InferenceChatMessage{{Content: "hello world"}}},
		"assistant reply",
		util.OptNone[InferenceChatUsage](),
	)

	if usage.PromptTokens == 0 || usage.CompletionTokens == 0 || usage.TotalTokens == 0 {
		t.Fatalf("expected non-zero usage, got %+v", usage)
	}
	if usage.TotalTokens != usage.PromptTokens+usage.CompletionTokens {
		t.Fatalf("expected total to match prompt+completion, got %+v", usage)
	}
}

func TestInferenceTranscriptionUsageAlwaysPresent(t *testing.T) {
	usage := inferenceTranscriptionUsageFromText("some transcript text", util.OptNone[InferenceTranscriptionUsage]())

	if usage.OutputTokens == 0 || usage.TotalTokens == 0 {
		t.Fatalf("expected non-zero usage, got %+v", usage)
	}
	if usage.TotalTokens != usage.InputTokens+usage.OutputTokens {
		t.Fatalf("expected total to match input+output, got %+v", usage)
	}
}

func TestInferenceImageUsageAlwaysPresent(t *testing.T) {
	usage := inferenceImageUsageFromPayload(
		InferenceImageGenerationRequest{Prompt: "an image", N: util.OptValue(2), Size: util.OptValue("1024x1024")},
		0,
		util.OptNone[InferenceImageGenerationUsage](),
	)

	if usage.OutputTokens == 0 || usage.TotalTokens == 0 {
		t.Fatalf("expected non-zero usage, got %+v", usage)
	}
	if usage.TotalTokens != usage.InputTokens+usage.OutputTokens {
		t.Fatalf("expected total to match input+output, got %+v", usage)
	}
}
