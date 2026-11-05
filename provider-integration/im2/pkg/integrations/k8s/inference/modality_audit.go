package inference

import "encoding/json"

func inferenceAuditTranscribeBody(
	model string,
	request InferenceTranscriptionRequest,
	usage *InferenceTranscriptionUsage,
	estimated bool,
	reason string,
) json.RawMessage {
	type auditTranscribeBody struct {
		Model          string                               `json:"model"`
		Stream         bool                                 `json:"stream"`
		Reason         string                               `json:"reason,omitempty"`
		ContentType    string                               `json:"contentType,omitempty"`
		FileBytes      int                                  `json:"fileBytes"`
		PromptBytes    int                                  `json:"promptBytes,omitempty"`
		Language       string                               `json:"language,omitempty"`
		ResponseFormat InferenceTranscriptionResponseFormat `json:"responseFormat"`
		Estimated      bool                                 `json:"estimated,omitempty"`
		Usage          *InferenceTranscriptionUsage         `json:"usage,omitempty"`
	}

	promptBytes := 0
	if request.Prompt.Present {
		promptBytes = len(request.Prompt.Value)
	}

	return mustMarshal(auditTranscribeBody{
		Model:          model,
		Stream:         request.Stream,
		Reason:         reason,
		ContentType:    request.File.ContentType,
		FileBytes:      len(request.File.Data),
		PromptBytes:    promptBytes,
		Language:       request.Language.GetOrDefault(""),
		ResponseFormat: request.ResponseFormat,
		Estimated:      estimated,
		Usage:          usage,
	})
}

func inferenceAuditImageBody(
	model string,
	request InferenceImageGenerationRequest,
	usage *InferenceImageGenerationUsage,
	estimated bool,
	reason string,
) json.RawMessage {
	type auditImageBody struct {
		Model       string                         `json:"model"`
		Stream      bool                           `json:"stream"`
		Reason      string                         `json:"reason,omitempty"`
		PromptBytes int                            `json:"promptBytes"`
		Size        string                         `json:"size,omitempty"`
		Quality     string                         `json:"quality,omitempty"`
		N           int                            `json:"n,omitempty"`
		Estimated   bool                           `json:"estimated,omitempty"`
		Usage       *InferenceImageGenerationUsage `json:"usage,omitempty"`
	}

	return mustMarshal(auditImageBody{
		Model:       model,
		Stream:      request.Stream.GetOrDefault(false),
		Reason:      reason,
		PromptBytes: len(request.Prompt),
		Size:        request.Size.GetOrDefault(""),
		Quality:     request.Quality.GetOrDefault(""),
		N:           request.N.GetOrDefault(0),
		Estimated:   estimated,
		Usage:       usage,
	})
}
