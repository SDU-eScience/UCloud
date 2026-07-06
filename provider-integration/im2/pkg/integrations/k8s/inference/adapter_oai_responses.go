package inference

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/util"
)

const inferenceResponseStoreTTL = 30 * time.Minute

var inferenceResponseGlobals = struct {
	Mu        sync.RWMutex
	Responses map[string]inferenceStoredResponse
	IdAcc     atomic.Int64
}{
	Responses: map[string]inferenceStoredResponse{},
}

type inferenceStoredResponse struct {
	Response  OaiResponse
	CreatedAt time.Time
}

type OaiResponseCreateRequest struct {
	Background        bool                       `json:"background,omitempty"`
	Include           []string                   `json:"include,omitempty"`
	Input             json.RawMessage            `json:"input,omitempty"`
	Instructions      json.RawMessage            `json:"instructions,omitempty"`
	MaxOutputTokens   util.Option[int]           `json:"max_output_tokens,omitempty"`
	Metadata          map[string]string          `json:"metadata,omitempty"`
	Model             string                     `json:"model,omitempty"`
	ParallelToolCalls util.Option[bool]          `json:"parallel_tool_calls,omitempty"`
	Reasoning         OaiResponseReasoningConfig `json:"reasoning,omitempty"`
	Stream            bool                       `json:"stream,omitempty"`
	Temperature       util.Option[float64]       `json:"temperature,omitempty"`
	Text              OaiResponseTextConfig      `json:"text,omitempty"`
	ToolChoice        json.RawMessage            `json:"tool_choice,omitempty"`
	Tools             []json.RawMessage          `json:"tools,omitempty"`
	TopLogprobs       util.Option[int]           `json:"top_logprobs,omitempty"`
	TopP              util.Option[float64]       `json:"top_p,omitempty"`
	Truncation        string                     `json:"truncation,omitempty"`
	User              string                     `json:"user,omitempty"`

	ContextManagement  json.RawMessage `json:"context_management,omitempty"`
	Conversation       json.RawMessage `json:"conversation,omitempty"`
	Moderation         json.RawMessage `json:"moderation,omitempty"`
	PreviousResponseID string          `json:"previous_response_id,omitempty"`
	Prompt             json.RawMessage `json:"prompt,omitempty"`
}

type OaiResponseReasoningConfig struct {
	Context         string              `json:"context,omitempty"`
	Effort          util.Option[string] `json:"effort,omitempty"`
	GenerateSummary string              `json:"generate_summary,omitempty"`
	Summary         string              `json:"summary,omitempty"`
}

type OaiResponseTextConfig struct {
	Format    json.RawMessage `json:"format,omitempty"`
	Verbosity string          `json:"verbosity,omitempty"`
}

type OaiResponse struct {
	Id                 string                     `json:"id"`
	Object             string                     `json:"object"`
	CreatedAt          int64                      `json:"created_at"`
	Status             string                     `json:"status,omitempty"`
	CompletedAt        *int64                     `json:"completed_at"`
	Error              any                        `json:"error"`
	IncompleteDetails  any                        `json:"incomplete_details"`
	Instructions       any                        `json:"instructions"`
	MaxOutputTokens    *int                       `json:"max_output_tokens"`
	Model              string                     `json:"model"`
	Output             []any                      `json:"output"`
	OutputText         string                     `json:"output_text,omitempty"`
	ParallelToolCalls  bool                       `json:"parallel_tool_calls"`
	PreviousResponseID any                        `json:"previous_response_id"`
	Reasoning          OaiResponseReasoningReturn `json:"reasoning"`
	Store              bool                       `json:"store"`
	Temperature        float64                    `json:"temperature"`
	Text               OaiResponseTextReturn      `json:"text"`
	ToolChoice         any                        `json:"tool_choice"`
	Tools              []any                      `json:"tools"`
	TopLogprobs        *int                       `json:"top_logprobs,omitempty"`
	TopP               float64                    `json:"top_p"`
	Truncation         string                     `json:"truncation"`
	Usage              *OaiResponseUsage          `json:"usage"`
	User               any                        `json:"user"`
	Metadata           map[string]string          `json:"metadata"`
	Background         bool                       `json:"background,omitempty"`
}

type OaiResponseReasoningReturn struct {
	Effort  any `json:"effort"`
	Summary any `json:"summary"`
}

type OaiResponseTextReturn struct {
	Format any `json:"format"`
}

type OaiResponseUsage struct {
	InputTokens         int                           `json:"input_tokens"`
	InputTokensDetails  OaiResponseInputTokenDetails  `json:"input_tokens_details"`
	OutputTokens        int                           `json:"output_tokens"`
	OutputTokensDetails OaiResponseOutputTokenDetails `json:"output_tokens_details"`
	TotalTokens         int                           `json:"total_tokens"`
}

type OaiResponseInputTokenDetails struct {
	CachedTokens int `json:"cached_tokens"`
}

type OaiResponseOutputTokenDetails struct {
	ReasoningTokens int `json:"reasoning_tokens"`
}

type OaiResponseOutputMessage struct {
	Id      string                  `json:"id"`
	Type    string                  `json:"type"`
	Status  string                  `json:"status"`
	Role    string                  `json:"role"`
	Content []OaiResponseOutputText `json:"content"`
}

type OaiResponseOutputText struct {
	Type        string `json:"type"`
	Text        string `json:"text"`
	Annotations []any  `json:"annotations"`
}

type OaiResponseReasoningItem struct {
	Id      string                     `json:"id"`
	Type    string                     `json:"type"`
	Status  string                     `json:"status"`
	Summary []OaiResponseReasoningText `json:"summary"`
	Content []OaiResponseReasoningText `json:"content,omitempty"`
}

type OaiResponseReasoningText struct {
	Type string `json:"type"`
	Text string `json:"text"`
}

type OaiResponseFunctionCall struct {
	Id        string `json:"id,omitempty"`
	Type      string `json:"type"`
	CallId    string `json:"call_id"`
	Name      string `json:"name"`
	Arguments string `json:"arguments"`
	Status    string `json:"status"`
}

type OaiResponseCustomToolCall struct {
	Id     string `json:"id,omitempty"`
	Type   string `json:"type"`
	CallId string `json:"call_id"`
	Name   string `json:"name"`
	Input  string `json:"input"`
	Status string `json:"status,omitempty"`
}

type OaiResponseCustomToolCallOutput struct {
	Id     string `json:"id,omitempty"`
	Type   string `json:"type"`
	CallId string `json:"call_id"`
	Name   string `json:"name,omitempty"`
	Output string `json:"output,omitempty"`
}

type OaiResponseFileSearchCall struct {
	Id      string   `json:"id,omitempty"`
	Type    string   `json:"type"`
	Queries []string `json:"queries"`
	Status  string   `json:"status"`
	Results []any    `json:"results"`
}

type OaiResponseWebSearchCall struct {
	Id     string `json:"id,omitempty"`
	Type   string `json:"type"`
	Action any    `json:"action"`
	Status string `json:"status"`
}

type OaiResponseApplyPatchCall struct {
	Id        string                  `json:"id,omitempty"`
	Type      string                  `json:"type"`
	CallId    string                  `json:"call_id"`
	Operation OaiResponseApplyPatchOp `json:"operation"`
	Status    string                  `json:"status"`
}

type OaiResponseApplyPatchOp struct {
	Type string `json:"type"`
	Path string `json:"path"`
	Diff string `json:"diff"`
}

type OaiResponseShellCall struct {
	Id          string                 `json:"id,omitempty"`
	Type        string                 `json:"type"`
	CallId      string                 `json:"call_id"`
	Action      OaiResponseShellAction `json:"action"`
	Environment any                    `json:"environment,omitempty"`
	Status      string                 `json:"status"`
}

type OaiResponseShellAction struct {
	Commands        []string `json:"commands"`
	MaxOutputLength *int     `json:"max_output_length,omitempty"`
	TimeoutMs       *int     `json:"timeout_ms,omitempty"`
}

type OaiResponseApplyPatchCallOutput struct {
	Id     string `json:"id,omitempty"`
	Type   string `json:"type"`
	CallId string `json:"call_id"`
	Status string `json:"status"`
	Output string `json:"output,omitempty"`
}

type OaiResponseShellCallOutput struct {
	Id              string                        `json:"id,omitempty"`
	Type            string                        `json:"type"`
	CallId          string                        `json:"call_id"`
	Output          []OaiResponseShellOutputChunk `json:"output"`
	MaxOutputLength *int                          `json:"max_output_length,omitempty"`
	Status          string                        `json:"status"`
}

type OaiResponseLocalShellCall struct {
	Id     string                      `json:"id,omitempty"`
	Type   string                      `json:"type"`
	CallId string                      `json:"call_id"`
	Action OaiResponseLocalShellAction `json:"action"`
	Status string                      `json:"status"`
}

type OaiResponseLocalShellAction struct {
	Type             string         `json:"type"`
	Command          []string       `json:"command"`
	Env              map[string]any `json:"env,omitempty"`
	TimeoutMs        *int           `json:"timeout_ms,omitempty"`
	User             string         `json:"user,omitempty"`
	WorkingDirectory string         `json:"working_directory,omitempty"`
}

type OaiResponseLocalShellCallOutput struct {
	Id     string `json:"id,omitempty"`
	Type   string `json:"type"`
	CallId string `json:"call_id,omitempty"`
	Output string `json:"output"`
	Status string `json:"status,omitempty"`
}

type OaiResponseShellOutputChunk struct {
	Stdout  string         `json:"stdout"`
	Stderr  string         `json:"stderr"`
	Outcome map[string]any `json:"outcome"`
}

type OaiResponseDeleteResponse struct {
	Id      string `json:"id"`
	Object  string `json:"object"`
	Deleted bool   `json:"deleted"`
}

func InferenceResponseCreate(owner apm.WalletOwner, request OaiResponseCreateRequest) (OaiResponse, *util.HttpError) {
	if httpErr := inferenceResponseValidateRequest(request); httpErr != nil {
		return OaiResponse{}, httpErr
	}

	id := inferenceResponseNewId("resp")
	createdAt := time.Now().Unix()
	if request.Background {
		queued := inferenceResponseBase(id, createdAt, request)
		queued.Status = "queued"
		queued.Background = true
		inferenceResponseStoreSet(queued)

		go func() {
			inProgress := queued
			inProgress.Status = "in_progress"
			inferenceResponseStoreSet(inProgress)

			chatRequest, httpErr := inferenceResponseChatRequest(request)
			if httpErr != nil {
				failed := inferenceResponseFailed(id, createdAt, request, httpErr.Why)
				failed.Background = true
				inferenceResponseStoreSet(failed)
				return
			}

			chatResponse, httpErr := InferenceChat(owner, chatRequest)
			if httpErr != nil {
				failed := inferenceResponseFailed(id, createdAt, request, httpErr.Why)
				failed.Background = true
				inferenceResponseStoreSet(failed)
				return
			}

			resp := inferenceResponseFromChat(id, request, chatResponse)
			resp.Background = true
			inferenceResponseStoreSet(resp)
		}()

		return queued, nil
	}

	chatRequest, httpErr := inferenceResponseChatRequest(request)
	if httpErr != nil {
		return OaiResponse{}, httpErr
	}

	chatResponse, httpErr := InferenceChat(owner, chatRequest)
	if httpErr != nil {
		return OaiResponse{}, httpErr
	}

	resp := inferenceResponseFromChat(id, request, chatResponse)
	inferenceResponseStoreSet(resp)
	return resp, nil
}

func InferenceResponseCreateStreaming(owner apm.WalletOwner, request OaiResponseCreateRequest) (chan OaiResponseStreamEvent, *util.HttpError) {
	ch := make(chan OaiResponseStreamEvent)
	if httpErr := inferenceResponseValidateRequest(request); httpErr != nil {
		close(ch)
		return ch, httpErr
	}

	chatRequest, httpErr := inferenceResponseChatRequest(request)
	if httpErr != nil {
		close(ch)
		return ch, httpErr
	}

	chatChunks, httpErr := InferenceChatStreaming(owner, chatRequest)
	if httpErr != nil {
		close(ch)
		return ch, httpErr
	}

	id := inferenceResponseNewId("resp")
	createdAt := time.Now().Unix()

	go func() {
		defer close(ch)

		resp := inferenceResponseBase(id, createdAt, request)
		resp.Status = "in_progress"
		resp.Usage = nil
		ch <- OaiResponseStreamEvent{Type: "response.created", Response: &resp}
		ch <- OaiResponseStreamEvent{Type: "response.in_progress", Response: &resp}

		var text strings.Builder
		var pendingText strings.Builder
		var reasoning strings.Builder
		var usage InferenceChatUsage
		customTools := inferenceResponseCustomToolNames(request.Tools)
		toolCalls := map[int]*inferenceResponseStreamingToolCall{}
		toolCallOrder := []int{}
		outputCount := 0
		messageId := ""
		messageIndex := -1
		reasoningId := ""
		reasoningIndex := -1
		startMessage := func() {
			if messageId != "" {
				return
			}
			messageId = inferenceResponseNewId("msg")
			messageIndex = outputCount
			outputCount++
			ch <- OaiResponseStreamEvent{
				Type:        "response.output_item.added",
				OutputIndex: util.Pointer(messageIndex),
				Item:        OaiResponseOutputMessage{Id: messageId, Type: "message", Status: "in_progress", Role: "assistant", Content: []OaiResponseOutputText{}},
			}
			ch <- OaiResponseStreamEvent{
				Type:         "response.content_part.added",
				ItemId:       messageId,
				OutputIndex:  util.Pointer(messageIndex),
				ContentIndex: util.Pointer(0),
				Part:         OaiResponseOutputText{Type: "output_text", Text: "", Annotations: []any{}},
			}
		}
		startReasoning := func() {
			if reasoningId != "" {
				return
			}
			reasoningId = inferenceResponseNewId("rs")
			reasoningIndex = outputCount
			outputCount++
			ch <- OaiResponseStreamEvent{
				Type:        "response.output_item.added",
				OutputIndex: util.Pointer(reasoningIndex),
				Item:        OaiResponseReasoningItem{Id: reasoningId, Type: "reasoning", Status: "in_progress", Summary: []OaiResponseReasoningText{}},
			}
		}
		for chunk := range chatChunks {
			if chunk.Usage.TotalTokens != 0 || chunk.Usage.PromptTokens != 0 || chunk.Usage.CompletionTokens != 0 {
				usage = chunk.Usage
			}
			if len(chunk.Choices) == 0 {
				continue
			}

			delta := chunk.Choices[0].Delta
			if delta.Reasoning != "" {
				startReasoning()
				reasoning.WriteString(delta.Reasoning)
				ch <- OaiResponseStreamEvent{
					Type:        "response.reasoning_text.delta",
					ItemId:      reasoningId,
					OutputIndex: util.Pointer(reasoningIndex),
					Delta:       delta.Reasoning,
				}
			}
			if delta.Content != "" {
				text.WriteString(delta.Content)
				if messageId != "" {
					ch <- OaiResponseStreamEvent{
						Type:         "response.output_text.delta",
						ItemId:       messageId,
						OutputIndex:  util.Pointer(messageIndex),
						ContentIndex: util.Pointer(0),
						Delta:        delta.Content,
					}
				} else {
					pendingText.WriteString(delta.Content)
					if strings.TrimSpace(pendingText.String()) != "" {
						startMessage()
						ch <- OaiResponseStreamEvent{
							Type:         "response.output_text.delta",
							ItemId:       messageId,
							OutputIndex:  util.Pointer(messageIndex),
							ContentIndex: util.Pointer(0),
							Delta:        pendingText.String(),
						}
						pendingText.Reset()
					}
				}
			}
			for _, toolCallDelta := range delta.ToolCalls {
				toolCall := toolCalls[toolCallDelta.Index]
				if toolCall == nil {
					toolCall = &inferenceResponseStreamingToolCall{
						OutputIndex: outputCount,
						Id:          toolCallDelta.Id,
						CallId:      toolCallDelta.Id,
					}
					if toolCall.Id == "" {
						toolCall.Id = inferenceResponseNewId("fc")
					}
					if toolCall.CallId == "" {
						toolCall.CallId = toolCall.Id
					}
					toolCalls[toolCallDelta.Index] = toolCall
					toolCallOrder = append(toolCallOrder, toolCallDelta.Index)
					outputCount++
				}
				if toolCallDelta.Id != "" {
					toolCall.Id = toolCallDelta.Id
					toolCall.CallId = toolCallDelta.Id
				}
				if toolCallDelta.Function != nil {
					if toolCallDelta.Function.Name != "" {
						toolCall.Name = toolCallDelta.Function.Name
						toolCall.Custom = customTools[toolCall.Name]
					}
					if !toolCall.Added && toolCall.Name != "" && !inferenceResponseBuiltinCallName(toolCall.Name) {
						toolCall.Added = true
						ch <- OaiResponseStreamEvent{Type: "response.output_item.added", OutputIndex: util.Pointer(toolCall.OutputIndex), Item: toolCall.ResponseItem("in_progress")}
					}
					if toolCallDelta.Function.Arguments != "" {
						toolCall.Arguments.WriteString(toolCallDelta.Function.Arguments)
						if !inferenceResponseBuiltinCallName(toolCall.Name) {
							ch <- OaiResponseStreamEvent{
								Type:        "response.function_call_arguments.delta",
								ItemId:      toolCall.Id,
								OutputIndex: util.Pointer(toolCall.OutputIndex),
								Delta:       toolCallDelta.Function.Arguments,
							}
						}
					}
				} else if !toolCall.Added && toolCall.Name != "" {
					toolCall.Added = true
					ch <- OaiResponseStreamEvent{Type: "response.output_item.added", OutputIndex: util.Pointer(toolCall.OutputIndex), Item: toolCall.ResponseItem("in_progress")}
				}
			}
		}

		finalText := text.String()
		if strings.TrimSpace(finalText) != "" || outputCount == 0 {
			startMessage()
			if pendingText.Len() > 0 {
				ch <- OaiResponseStreamEvent{
					Type:         "response.output_text.delta",
					ItemId:       messageId,
					OutputIndex:  util.Pointer(messageIndex),
					ContentIndex: util.Pointer(0),
					Delta:        pendingText.String(),
				}
				pendingText.Reset()
			}
		}
		if reasoningId != "" {
			ch <- OaiResponseStreamEvent{Type: "response.reasoning_text.done", ItemId: reasoningId, OutputIndex: util.Pointer(reasoningIndex), Text: reasoning.String()}
			ch <- OaiResponseStreamEvent{Type: "response.output_item.done", OutputIndex: util.Pointer(reasoningIndex), Item: OaiResponseReasoningItem{Id: reasoningId, Type: "reasoning", Status: "completed", Summary: []OaiResponseReasoningText{}, Content: []OaiResponseReasoningText{{Type: "reasoning_text", Text: reasoning.String()}}}}
		}
		var message OaiResponseOutputMessage
		if messageId != "" {
			ch <- OaiResponseStreamEvent{Type: "response.output_text.done", ItemId: messageId, OutputIndex: util.Pointer(messageIndex), ContentIndex: util.Pointer(0), Text: finalText}
			part := OaiResponseOutputText{Type: "output_text", Text: finalText, Annotations: []any{}}
			ch <- OaiResponseStreamEvent{Type: "response.content_part.done", ItemId: messageId, OutputIndex: util.Pointer(messageIndex), ContentIndex: util.Pointer(0), Part: part}
			message = OaiResponseOutputMessage{Id: messageId, Type: "message", Status: "completed", Role: "assistant", Content: []OaiResponseOutputText{part}}
			ch <- OaiResponseStreamEvent{Type: "response.output_item.done", OutputIndex: util.Pointer(messageIndex), Item: message}
		}
		for _, toolCallIndex := range toolCallOrder {
			toolCall := toolCalls[toolCallIndex]
			if toolCall.Custom {
				inferenceResponseStreamCustomToolCall(ch, toolCall)
				continue
			}
			if !toolCall.Added {
				toolCall.Added = true
				ch <- OaiResponseStreamEvent{Type: "response.output_item.added", OutputIndex: util.Pointer(toolCall.OutputIndex), Item: toolCall.ResponseItem("in_progress")}
			}
			item := toolCall.ResponseItem("completed")
			if !inferenceResponseBuiltinCallName(toolCall.Name) {
				ch <- OaiResponseStreamEvent{Type: "response.function_call_arguments.done", ItemId: toolCall.Id, OutputIndex: util.Pointer(toolCall.OutputIndex), Arguments: toolCall.Arguments.String()}
			}
			ch <- OaiResponseStreamEvent{Type: "response.output_item.done", OutputIndex: util.Pointer(toolCall.OutputIndex), Item: item}
		}

		completedAt := time.Now().Unix()
		resp.Status = "completed"
		resp.CompletedAt = &completedAt
		if messageId != "" {
			resp.OutputText = finalText
		}
		resp.Output = make([]any, outputCount)
		if reasoning.String() != "" {
			resp.Output[reasoningIndex] = OaiResponseReasoningItem{Id: reasoningId, Type: "reasoning", Status: "completed", Summary: []OaiResponseReasoningText{}, Content: []OaiResponseReasoningText{{Type: "reasoning_text", Text: reasoning.String()}}}
		}
		if messageId != "" {
			resp.Output[messageIndex] = message
		}
		for _, toolCallIndex := range toolCallOrder {
			toolCall := toolCalls[toolCallIndex]
			resp.Output[toolCall.OutputIndex] = toolCall.ResponseItem("completed")
		}
		resp.Usage = inferenceResponseUsage(usage, reasoning.String())
		inferenceResponseStoreSet(resp)
		ch <- OaiResponseStreamEvent{Type: "response.completed", Response: &resp}
	}()

	return ch, nil
}

type OaiResponseStreamEvent struct {
	Type         string       `json:"type"`
	ResponseId   string       `json:"response_id,omitempty"`
	Response     *OaiResponse `json:"response,omitempty"`
	OutputIndex  *int         `json:"output_index,omitempty"`
	ContentIndex *int         `json:"content_index,omitempty"`
	ItemId       string       `json:"item_id,omitempty"`
	Item         any          `json:"item,omitempty"`
	Part         any          `json:"part,omitempty"`
	Delta        string       `json:"delta,omitempty"`
	Text         string       `json:"text,omitempty"`
	Arguments    string       `json:"arguments,omitempty"`
}

type inferenceResponseStreamingToolCall struct {
	OutputIndex int
	Id          string
	CallId      string
	Name        string
	Arguments   strings.Builder
	Added       bool
	Custom      bool
}

func (c *inferenceResponseStreamingToolCall) ResponseItem(status string) any {
	return inferenceResponseToolCallItem(c.Id, c.CallId, c.Name, c.Arguments.String(), status, c.Custom)
}

func inferenceResponseStreamCustomToolCall(ch chan OaiResponseStreamEvent, toolCall *inferenceResponseStreamingToolCall) {
	input := inferenceResponseCustomToolInput(toolCall.Name, toolCall.Arguments.String())
	if !toolCall.Added {
		toolCall.Added = true
		ch <- OaiResponseStreamEvent{Type: "response.output_item.added", OutputIndex: util.Pointer(toolCall.OutputIndex), Item: OaiResponseCustomToolCall{Id: toolCall.Id, Type: "custom_tool_call", CallId: toolCall.CallId, Name: toolCall.Name, Input: "", Status: "in_progress"}}
	}
	if input != "" {
		ch <- OaiResponseStreamEvent{Type: "response.custom_tool_call_input.delta", ItemId: toolCall.Id, OutputIndex: util.Pointer(toolCall.OutputIndex), Delta: input}
	}
	ch <- OaiResponseStreamEvent{Type: "response.output_item.done", OutputIndex: util.Pointer(toolCall.OutputIndex), Item: OaiResponseCustomToolCall{Id: toolCall.Id, Type: "custom_tool_call", CallId: toolCall.CallId, Name: toolCall.Name, Input: input, Status: "completed"}}
}

func inferenceResponseToolCallItem(id string, callId string, name string, arguments string, status string, custom bool) any {
	if callId == "" {
		callId = id
	}
	if custom {
		return OaiResponseCustomToolCall{Id: id, Type: "custom_tool_call", CallId: callId, Name: name, Input: inferenceResponseCustomToolInput(name, arguments), Status: status}
	}
	switch name {
	case "file_search":
		return OaiResponseFileSearchCall{Id: id, Type: "file_search_call", Queries: inferenceResponseSearchQueries(arguments), Status: status, Results: []any{}}
	case "web_search", "web_search_2025_08_26", "web_search_preview", "web_search_preview_2025_03_11":
		return OaiResponseWebSearchCall{Id: id, Type: "web_search_call", Action: inferenceResponseWebSearchAction(arguments), Status: status}
	case "apply_patch":
		return OaiResponseApplyPatchCall{Id: id, Type: "apply_patch_call", CallId: callId, Operation: inferenceResponseApplyPatchOperation(arguments), Status: status}
	case "shell":
		return OaiResponseShellCall{Id: id, Type: "shell_call", CallId: callId, Action: inferenceResponseShellAction(arguments), Status: status}
	case "local_shell":
		return OaiResponseLocalShellCall{Id: id, Type: "local_shell_call", CallId: callId, Action: inferenceResponseLocalShellAction(arguments), Status: status}
	default:
		return OaiResponseFunctionCall{
			Id:        id,
			Type:      "function_call",
			CallId:    callId,
			Name:      name,
			Arguments: arguments,
			Status:    status,
		}
	}
}

func inferenceResponseBuiltinCallName(name string) bool {
	return name == "apply_patch" || name == "shell" || name == "local_shell" || name == "file_search" || strings.HasPrefix(name, "web_search")
}

func inferenceResponseSearchQueries(arguments string) []string {
	var parsed struct {
		Query   string   `json:"query"`
		Queries []string `json:"queries"`
	}
	_ = json.Unmarshal([]byte(arguments), &parsed)
	if len(parsed.Queries) > 0 {
		return parsed.Queries
	}
	if parsed.Query != "" {
		return []string{parsed.Query}
	}
	return []string{}
}

func inferenceResponseWebSearchAction(arguments string) any {
	queries := inferenceResponseSearchQueries(arguments)
	query := ""
	if len(queries) > 0 {
		query = queries[0]
	}
	return map[string]any{"type": "search", "query": query, "queries": queries, "sources": []any{}}
}

func inferenceResponseCustomToolInput(name string, arguments string) string {
	if name == "apply_patch" {
		var parsed struct {
			Patch string `json:"patch"`
		}
		if err := json.Unmarshal([]byte(arguments), &parsed); err == nil {
			return inferenceResponseNormalizeApplyPatchDocument(parsed.Patch)
		}
	}
	var parsed struct {
		Input string `json:"input"`
	}
	if err := json.Unmarshal([]byte(arguments), &parsed); err == nil && parsed.Input != "" {
		return parsed.Input
	}
	return arguments
}

func inferenceResponseNormalizeApplyPatchDocument(patch string) string {
	if !strings.Contains(patch, "*** Add File: ") {
		return patch
	}

	lines := strings.Split(strings.ReplaceAll(patch, "\r\n", "\n"), "\n")
	result := make([]string, 0, len(lines))
	inAddFile := false
	for _, line := range lines {
		trimmed := strings.TrimSpace(line)
		if strings.HasPrefix(line, "*** Add File: ") {
			inAddFile = true
			result = append(result, line)
			continue
		}
		if strings.HasPrefix(line, "*** ") {
			inAddFile = false
			result = append(result, line)
			continue
		}
		if inAddFile && trimmed != "" && !strings.HasPrefix(line, "+") {
			result = append(result, "+"+line)
			continue
		}
		if inAddFile && trimmed == "" {
			result = append(result, "+")
			continue
		}
		result = append(result, line)
	}
	return strings.Join(result, "\n")
}

func inferenceResponseApplyPatchOperation(arguments string) OaiResponseApplyPatchOp {
	var parsed struct {
		Patch string `json:"patch"`
	}
	if err := json.Unmarshal([]byte(arguments), &parsed); err != nil {
		return inferenceResponseParseApplyPatchOperation("")
	}
	operation := inferenceResponseParseApplyPatchOperation(parsed.Patch)
	return operation
}

func inferenceResponseParseApplyPatchOperation(patch string) OaiResponseApplyPatchOp {
	operation := OaiResponseApplyPatchOp{Type: "update_file", Diff: patch}
	lines := strings.Split(strings.ReplaceAll(patch, "\r\n", "\n"), "\n")
	foundHeader := false
	body := make([]string, 0, len(lines))
	pseudoFileStart := -1
	pseudoFileEnd := -1

	for i, line := range lines {
		trimmed := strings.TrimSpace(line)
		if trimmed == "*** Begin Patch" || trimmed == "*** End Patch" {
			continue
		}
		if strings.HasPrefix(trimmed, "*** Add File: ") {
			operation.Type = "create_file"
			operation.Path = strings.TrimSpace(strings.TrimPrefix(trimmed, "*** Add File: "))
			foundHeader = true
			continue
		}
		if strings.HasPrefix(trimmed, "*** Begin File: ") {
			operation.Type = "create_file"
			operation.Path = strings.TrimSpace(strings.TrimPrefix(trimmed, "*** Begin File: "))
			foundHeader = true
			pseudoFileStart = i
			continue
		}
		if trimmed == "*** End File" {
			pseudoFileEnd = i
			continue
		}
		if strings.HasPrefix(trimmed, "*** Update File: ") {
			operation.Type = "update_file"
			operation.Path = strings.TrimSpace(strings.TrimPrefix(trimmed, "*** Update File: "))
			foundHeader = true
			continue
		}
		if strings.HasPrefix(trimmed, "*** Delete File: ") {
			operation.Type = "delete_file"
			operation.Path = strings.TrimSpace(strings.TrimPrefix(trimmed, "*** Delete File: "))
			foundHeader = true
			continue
		}
		if foundHeader {
			body = append(body, line)
		}
	}

	if pseudoFileStart >= 0 && operation.Path != "" {
		end := len(lines)
		if pseudoFileEnd > pseudoFileStart {
			end = pseudoFileEnd
		}
		operation.Diff = inferenceResponseCreateFileDiff(lines[pseudoFileStart+1 : end])
		return operation
	}
	if foundHeader {
		operation.Diff = strings.TrimRight(strings.Join(body, "\n"), "\n")
	}
	if foundHeader && operation.Type == "create_file" {
		operation.Diff = inferenceResponseCanonicalizeCreateFileDiff(operation.Diff)
	}
	if foundHeader && operation.Type == "delete_file" {
		operation.Diff = ""
	}
	return operation
}

func inferenceResponseCanonicalizeCreateFileDiff(diff string) string {
	if diff == "" {
		return diff
	}
	lines := strings.Split(diff, "\n")
	hasPlusLine := false
	for _, line := range lines {
		if strings.HasPrefix(line, "+") {
			hasPlusLine = true
			break
		}
	}
	if hasPlusLine {
		return strings.TrimRight(diff, "\n")
	}
	return inferenceResponseCreateFileDiff(lines)
}

func inferenceResponseCreateFileDiff(lines []string) string {
	result := make([]string, 0, len(lines))
	for _, line := range lines {
		result = append(result, "+"+line)
	}
	return strings.TrimRight(strings.Join(result, "\n"), "\n")
}

func inferenceResponseShellAction(arguments string) OaiResponseShellAction {
	var parsed struct {
		Command         string `json:"command"`
		TimeoutMs       *int   `json:"timeout_ms"`
		MaxOutputLength *int   `json:"max_output_length"`
	}
	_ = json.Unmarshal([]byte(arguments), &parsed)
	commands := []string{}
	if parsed.Command != "" {
		commands = append(commands, parsed.Command)
	}
	return OaiResponseShellAction{Commands: commands, TimeoutMs: parsed.TimeoutMs, MaxOutputLength: parsed.MaxOutputLength}
}

func inferenceResponseLocalShellAction(arguments string) OaiResponseLocalShellAction {
	var parsed struct {
		Command          []string       `json:"command"`
		CommandString    string         `json:"command_string"`
		Env              map[string]any `json:"env"`
		TimeoutMs        *int           `json:"timeout_ms"`
		User             string         `json:"user"`
		WorkingDirectory string         `json:"working_directory"`
	}
	_ = json.Unmarshal([]byte(arguments), &parsed)
	if len(parsed.Command) == 0 && parsed.CommandString != "" {
		parsed.Command = []string{parsed.CommandString}
	}
	return OaiResponseLocalShellAction{Type: "exec", Command: parsed.Command, Env: parsed.Env, TimeoutMs: parsed.TimeoutMs, User: parsed.User, WorkingDirectory: parsed.WorkingDirectory}
}

func InferenceResponsePoll(_ apm.WalletOwner, id string) (OaiResponse, *util.HttpError) {
	resp, ok := inferenceResponseStoreGet(id)
	if !ok {
		return OaiResponse{}, util.HttpErr(http.StatusNotFound, "response not found")
	}
	return resp, nil
}

func InferenceResponseCancel(owner apm.WalletOwner, id string) (OaiResponse, *util.HttpError) {
	return InferenceResponsePoll(owner, id)
}

func InferenceResponseDelete(_ apm.WalletOwner, id string) (OaiResponseDeleteResponse, *util.HttpError) {
	inferenceResponseStoreDelete(id)
	return OaiResponseDeleteResponse{Id: id, Object: "response", Deleted: true}, nil
}

func inferenceResponseValidateRequest(request OaiResponseCreateRequest) *util.HttpError {
	for _, include := range request.Include {
		if include == "reasoning.encrypted_content" {
			return util.HttpErr(http.StatusBadRequest, "reasoning.encrypted_content is not supported")
		}
	}
	if len(request.ContextManagement) > 0 && string(request.ContextManagement) != "null" {
		return util.HttpErr(http.StatusBadRequest, "context_management is not supported")
	}
	if len(request.Conversation) > 0 && string(request.Conversation) != "null" {
		return util.HttpErr(http.StatusBadRequest, "conversation is not supported")
	}
	if request.PreviousResponseID != "" {
		return util.HttpErr(http.StatusBadRequest, "previous_response_id is not supported")
	}
	if len(request.Prompt) > 0 && string(request.Prompt) != "null" {
		return util.HttpErr(http.StatusBadRequest, "prompt is not supported")
	}
	if len(request.Moderation) > 0 && string(request.Moderation) != "null" {
		return util.HttpErr(http.StatusBadRequest, "moderation is not supported")
	}
	return nil
}

func inferenceResponseChatRequest(request OaiResponseCreateRequest) (InferenceChatRequest, *util.HttpError) {
	messages, httpErr := inferenceResponseMessages(request)
	if httpErr != nil {
		return InferenceChatRequest{}, httpErr
	}

	tools, httpErr := inferenceResponseChatTools(request.Tools)
	if httpErr != nil {
		return InferenceChatRequest{}, httpErr
	}

	toolChoice, httpErr := inferenceResponseToolChoice(request.ToolChoice)
	if httpErr != nil {
		return InferenceChatRequest{}, httpErr
	}

	chatRequest := InferenceChatRequest{
		Model:               request.Model,
		Messages:            messages,
		MaxCompletionTokens: request.MaxOutputTokens,
		Metadata:            request.Metadata,
		ParallelToolCalls:   request.ParallelToolCalls,
		ReasoningEffort:     request.Reasoning.Effort,
		Stream:              request.Stream,
		Temperature:         request.Temperature,
		ToolChoice:          toolChoice,
		Tools:               tools,
		TopLogprobs:         request.TopLogprobs,
		TopP:                request.TopP,
	}

	if len(request.Text.Format) > 0 && string(request.Text.Format) != "null" {
		var format any
		if err := json.Unmarshal(request.Text.Format, &format); err != nil {
			return InferenceChatRequest{}, util.HttpErr(http.StatusBadRequest, "invalid text format")
		}
		chatRequest.ResponseFormat = format
	}

	return chatRequest, nil
}

func inferenceResponseMessages(request OaiResponseCreateRequest) ([]InferenceChatMessage, *util.HttpError) {
	messages := []InferenceChatMessage{}
	if len(request.Instructions) > 0 && string(request.Instructions) != "null" {
		var instruction string
		if err := json.Unmarshal(request.Instructions, &instruction); err != nil {
			return nil, util.HttpErr(http.StatusBadRequest, "instructions must be a string")
		}
		if strings.TrimSpace(instruction) != "" {
			messages = append(messages, InferenceChatMessage{Role: "system", Content: inferenceChatTextContent(instruction)})
		}
	}

	if len(request.Input) == 0 || string(request.Input) == "null" {
		return messages, nil
	}

	var inputString string
	if err := json.Unmarshal(request.Input, &inputString); err == nil {
		messages = append(messages, InferenceChatMessage{Role: "user", Content: inferenceChatTextContent(inputString)})
		return messages, nil
	}

	var items []json.RawMessage
	if err := json.Unmarshal(request.Input, &items); err == nil {
		for _, item := range items {
			message, ok, httpErr := inferenceResponseInputItemToMessage(item)
			if httpErr != nil {
				return nil, httpErr
			}
			if ok {
				messages = append(messages, message)
			}
		}
		return messages, nil
	}

	message, ok, httpErr := inferenceResponseInputItemToMessage(request.Input)
	if httpErr != nil {
		return nil, httpErr
	}
	if ok {
		messages = append(messages, message)
		return messages, nil
	}

	return nil, util.HttpErr(http.StatusBadRequest, "invalid input")
}

func inferenceResponseInputItemToMessage(raw json.RawMessage) (InferenceChatMessage, bool, *util.HttpError) {
	var item struct {
		Type    string          `json:"type"`
		Role    string          `json:"role"`
		Content json.RawMessage `json:"content"`
	}
	if err := json.Unmarshal(raw, &item); err != nil {
		return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid input item")
	}

	switch item.Type {
	case "", "message":
		role := item.Role
		if role == "developer" {
			role = "system"
		}
		if role == "" {
			role = "user"
		}
		if role != "user" && role != "assistant" && role != "system" && role != "tool" {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "unsupported input role")
		}
		content, httpErr := inferenceResponseInputContent(item.Content)
		if httpErr != nil {
			return InferenceChatMessage{}, false, httpErr
		}
		return InferenceChatMessage{Role: role, Content: inferenceChatTextContent(content)}, true, nil
	case "reasoning":
		var reasoning struct {
			Content []OaiResponseReasoningText `json:"content"`
		}
		if err := json.Unmarshal(raw, &reasoning); err != nil {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid reasoning item")
		}
		var builder strings.Builder
		for _, content := range reasoning.Content {
			builder.WriteString(content.Text)
		}
		if builder.Len() == 0 {
			return InferenceChatMessage{}, false, nil
		}
		return InferenceChatMessage{Role: "assistant", Reasoning: inferenceChatTextContent(builder.String())}, true, nil
	case "function_call":
		var functionCall struct {
			Id        string `json:"id"`
			CallId    string `json:"call_id"`
			Name      string `json:"name"`
			Arguments string `json:"arguments"`
		}
		if err := json.Unmarshal(raw, &functionCall); err != nil {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid function_call item")
		}
		callId := functionCall.CallId
		if callId == "" {
			callId = functionCall.Id
		}
		if callId == "" {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid function_call item")
		}
		return inferenceResponseToolCallMessage(callId, functionCall.Name, functionCall.Arguments), true, nil
	case "custom_tool_call":
		var customToolCall struct {
			Id     string `json:"id"`
			CallId string `json:"call_id"`
			Name   string `json:"name"`
			Input  string `json:"input"`
		}
		if err := json.Unmarshal(raw, &customToolCall); err != nil {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid custom_tool_call item")
		}
		callId := customToolCall.CallId
		if callId == "" {
			callId = customToolCall.Id
		}
		if callId == "" || customToolCall.Name == "" {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid custom_tool_call item")
		}
		arguments := customToolCall.Input
		if customToolCall.Name == "apply_patch" {
			encoded, err := json.Marshal(map[string]string{"patch": customToolCall.Input})
			if err != nil {
				return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid custom_tool_call item")
			}
			arguments = string(encoded)
		}
		return inferenceResponseToolCallMessage(callId, customToolCall.Name, arguments), true, nil
	case "apply_patch_call":
		var applyPatchCall struct {
			Id        string                  `json:"id"`
			CallId    string                  `json:"call_id"`
			Operation OaiResponseApplyPatchOp `json:"operation"`
		}
		if err := json.Unmarshal(raw, &applyPatchCall); err != nil {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid apply_patch_call item")
		}
		callId := applyPatchCall.CallId
		if callId == "" {
			callId = applyPatchCall.Id
		}
		if callId == "" {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid apply_patch_call item")
		}
		arguments, err := json.Marshal(map[string]string{"patch": applyPatchCall.Operation.Diff})
		if err != nil {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid apply_patch_call item")
		}
		return inferenceResponseToolCallMessage(callId, "apply_patch", string(arguments)), true, nil
	case "shell_call":
		var shellCall struct {
			Id     string                 `json:"id"`
			CallId string                 `json:"call_id"`
			Action OaiResponseShellAction `json:"action"`
		}
		if err := json.Unmarshal(raw, &shellCall); err != nil {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid shell_call item")
		}
		callId := shellCall.CallId
		if callId == "" {
			callId = shellCall.Id
		}
		if callId == "" {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid shell_call item")
		}
		command := ""
		if len(shellCall.Action.Commands) > 0 {
			command = shellCall.Action.Commands[0]
		}
		args := map[string]any{"command": command}
		if shellCall.Action.TimeoutMs != nil {
			args["timeout_ms"] = *shellCall.Action.TimeoutMs
		}
		arguments, err := json.Marshal(args)
		if err != nil {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid shell_call item")
		}
		return inferenceResponseToolCallMessage(callId, "shell", string(arguments)), true, nil
	case "local_shell_call":
		var localShellCall struct {
			Id     string                      `json:"id"`
			CallId string                      `json:"call_id"`
			Action OaiResponseLocalShellAction `json:"action"`
		}
		if err := json.Unmarshal(raw, &localShellCall); err != nil {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid local_shell_call item")
		}
		callId := localShellCall.CallId
		if callId == "" {
			callId = localShellCall.Id
		}
		if callId == "" {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid local_shell_call item")
		}
		args := map[string]any{"command": localShellCall.Action.Command}
		if localShellCall.Action.Env != nil {
			args["env"] = localShellCall.Action.Env
		}
		if localShellCall.Action.TimeoutMs != nil {
			args["timeout_ms"] = *localShellCall.Action.TimeoutMs
		}
		if localShellCall.Action.User != "" {
			args["user"] = localShellCall.Action.User
		}
		if localShellCall.Action.WorkingDirectory != "" {
			args["working_directory"] = localShellCall.Action.WorkingDirectory
		}
		arguments, err := json.Marshal(args)
		if err != nil {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid local_shell_call item")
		}
		return inferenceResponseToolCallMessage(callId, "local_shell", string(arguments)), true, nil
	case "function_call_output":
		var functionCallOutput struct {
			CallId string          `json:"call_id"`
			Output json.RawMessage `json:"output"`
		}
		if err := json.Unmarshal(raw, &functionCallOutput); err != nil {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid function_call_output item")
		}
		if functionCallOutput.CallId == "" {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid function_call_output item")
		}
		output, httpErr := inferenceResponseToolOutputContent(functionCallOutput.Output)
		if httpErr != nil {
			return InferenceChatMessage{}, false, httpErr
		}
		return InferenceChatMessage{Role: "tool", Content: inferenceChatTextContent(output), ToolCallID: functionCallOutput.CallId}, true, nil
	case "custom_tool_call_output":
		var customToolOutput OaiResponseCustomToolCallOutput
		if err := json.Unmarshal(raw, &customToolOutput); err != nil || customToolOutput.CallId == "" {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid custom_tool_call_output item")
		}
		return InferenceChatMessage{Role: "tool", Content: inferenceChatTextContent(customToolOutput.Output), ToolCallID: customToolOutput.CallId}, true, nil
	case "apply_patch_call_output":
		var applyPatchOutput OaiResponseApplyPatchCallOutput
		if err := json.Unmarshal(raw, &applyPatchOutput); err != nil || applyPatchOutput.CallId == "" {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid apply_patch_call_output item")
		}
		return InferenceChatMessage{Role: "tool", Content: inferenceChatTextContent(applyPatchOutput.Output), ToolCallID: applyPatchOutput.CallId}, true, nil
	case "shell_call_output":
		var shellOutput OaiResponseShellCallOutput
		if err := json.Unmarshal(raw, &shellOutput); err != nil || shellOutput.CallId == "" {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid shell_call_output item")
		}
		var builder strings.Builder
		for _, output := range shellOutput.Output {
			builder.WriteString(output.Stdout)
			builder.WriteString(output.Stderr)
		}
		return InferenceChatMessage{Role: "tool", Content: inferenceChatTextContent(builder.String()), ToolCallID: shellOutput.CallId}, true, nil
	case "local_shell_call_output":
		var localShellOutput OaiResponseLocalShellCallOutput
		if err := json.Unmarshal(raw, &localShellOutput); err != nil {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid local_shell_call_output item")
		}
		callId := localShellOutput.CallId
		if callId == "" {
			callId = localShellOutput.Id
		}
		if callId == "" {
			return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "invalid local_shell_call_output item")
		}
		return InferenceChatMessage{Role: "tool", Content: inferenceChatTextContent(localShellOutput.Output), ToolCallID: callId}, true, nil
	default:
		return InferenceChatMessage{}, false, util.HttpErr(http.StatusBadRequest, "unsupported input item: %v", item.Type)
	}
}

func inferenceResponseToolCallMessage(callId string, name string, arguments string) InferenceChatMessage {
	return InferenceChatMessage{
		Role: "assistant",
		ToolCalls: []InferenceChatToolCall{{
			Id:   callId,
			Type: "function",
			Function: InferenceChatToolCallFunction{
				Name:      name,
				Arguments: arguments,
			},
		}},
	}
}

func inferenceResponseToolOutputContent(raw json.RawMessage) (string, *util.HttpError) {
	if len(raw) == 0 || string(raw) == "null" {
		return "", nil
	}

	var text string
	if err := json.Unmarshal(raw, &text); err == nil {
		return text, nil
	}

	content, httpErr := inferenceResponseInputContent(raw)
	if httpErr == nil {
		return content, nil
	}

	var value any
	if err := json.Unmarshal(raw, &value); err != nil {
		return "", util.HttpErr(http.StatusBadRequest, "invalid function_call_output item")
	}
	encoded, err := json.Marshal(value)
	if err != nil {
		return "", util.HttpErr(http.StatusBadRequest, "invalid function_call_output item")
	}
	return string(encoded), nil
}

func inferenceResponseInputContent(raw json.RawMessage) (string, *util.HttpError) {
	if len(raw) == 0 || string(raw) == "null" {
		return "", nil
	}

	var text string
	if err := json.Unmarshal(raw, &text); err == nil {
		return text, nil
	}

	var parts []struct {
		Type string `json:"type"`
		Text string `json:"text"`
	}
	if err := json.Unmarshal(raw, &parts); err != nil {
		return "", util.HttpErr(http.StatusBadRequest, "invalid input content")
	}

	var builder strings.Builder
	for _, part := range parts {
		switch part.Type {
		case "input_text", "output_text", "text":
			builder.WriteString(part.Text)
		default:
			return "", util.HttpErr(http.StatusBadRequest, "unsupported input content: %v", part.Type)
		}
	}
	return builder.String(), nil
}

func inferenceResponseChatTools(rawTools []json.RawMessage) ([]InferenceChatTool, *util.HttpError) {
	result := []InferenceChatTool{}
	for _, raw := range rawTools {
		tools, httpErr := inferenceResponseChatTool(raw)
		if httpErr != nil {
			return nil, httpErr
		}
		result = append(result, tools...)
	}
	return inferenceResponseDeduplicateTools(result), nil
}

func inferenceResponseCustomToolNames(rawTools []json.RawMessage) map[string]bool {
	result := map[string]bool{}
	for _, raw := range rawTools {
		var tool struct {
			Type  string            `json:"type"`
			Name  string            `json:"name"`
			Tools []json.RawMessage `json:"tools"`
		}
		if json.Unmarshal(raw, &tool) != nil {
			continue
		}
		if tool.Type == "custom" && tool.Name != "" {
			result[tool.Name] = true
		}
		if tool.Type == "namespace" {
			for name := range inferenceResponseCustomToolNames(tool.Tools) {
				result[name] = true
			}
		}
	}
	return result
}

func inferenceResponseChatTool(raw json.RawMessage) ([]InferenceChatTool, *util.HttpError) {
	var tool struct {
		Type        string            `json:"type"`
		Name        string            `json:"name"`
		Description string            `json:"description"`
		Format      json.RawMessage   `json:"format"`
		Parameters  json.RawMessage   `json:"parameters"`
		Strict      util.Option[bool] `json:"strict"`
		Tools       []json.RawMessage `json:"tools"`
	}
	if err := json.Unmarshal(raw, &tool); err != nil {
		return nil, util.HttpErr(http.StatusBadRequest, "invalid tool")
	}
	switch tool.Type {
	case "function":
		var parameters any
		if len(tool.Parameters) > 0 && string(tool.Parameters) != "null" {
			if err := json.Unmarshal(tool.Parameters, &parameters); err != nil {
				return nil, util.HttpErr(http.StatusBadRequest, "invalid tool parameters")
			}
		}
		return []InferenceChatTool{{Type: "function", Function: InferenceChatToolFunction{Name: tool.Name, Description: tool.Description, Parameters: parameters, Strict: tool.Strict}}}, nil
	case "custom":
		return []InferenceChatTool{inferenceResponseCustomToolAsFunction(tool.Name, tool.Description)}, nil
	case "namespace":
		return inferenceResponseChatTools(tool.Tools)
	case "apply_patch", "shell", "local_shell":
		builtin, ok := inferenceResponseBuiltinTool(tool.Type)
		if !ok {
			return nil, util.HttpErr(http.StatusBadRequest, "unsupported tool: %v", tool.Type)
		}
		return []InferenceChatTool{builtin}, nil
	case "file_search", "web_search", "web_search_2025_08_26", "web_search_preview", "web_search_preview_2025_03_11":
		return []InferenceChatTool{inferenceResponseHostedSearchTool(tool.Type)}, nil
	case "computer", "computer_use", "computer_use_preview", "code_interpreter", "image_generation":
		return nil, nil
	default:
		return nil, util.HttpErr(http.StatusBadRequest, "unsupported tool: %v", tool.Type)
	}
}

func inferenceResponseHostedSearchTool(toolType string) InferenceChatTool {
	parameters := map[string]any{
		"type": "object",
		"properties": map[string]any{
			"query": map[string]any{"type": "string"},
		},
		"required":             []string{"query"},
		"additionalProperties": false,
	}
	description := "Search is emulated by this Responses compatibility shim and always returns zero results."
	if toolType == "file_search" {
		description = "File search is emulated by this Responses compatibility shim and always returns zero results."
	}
	return InferenceChatTool{Type: "function", Function: InferenceChatToolFunction{Name: toolType, Description: description, Parameters: parameters, Strict: util.OptValue(true)}}
}

func inferenceResponseCustomToolAsFunction(name string, description string) InferenceChatTool {
	if name == "apply_patch" {
		parameters := map[string]any{
			"type": "object",
			"properties": map[string]any{
				"patch": map[string]any{"type": "string", "description": "Complete raw apply_patch document. Do not include markdown fences or prose. Begin with *** Begin Patch and end with *** End Patch. For *** Add File: <path>, every file content line MUST start with '+', for example '+#include <stdio.h>' and '+int main(void) {'."},
			},
			"required":             []string{"patch"},
			"additionalProperties": false,
		}
		description = strings.TrimSpace(description + `

When calling this function, the patch field must contain a complete apply_patch document only.

Valid examples:
*** Begin Patch
*** Add File: main.c
+#include <stdio.h>
+
+int main(void) {
+    printf("Hello, world!\n");
+    return 0;
+}
*** End Patch

For Add File operations, every line of file content must be prefixed with '+'. Do not write raw file contents without '+'. Do not wrap the patch in markdown fences.`)
		return InferenceChatTool{Type: "function", Function: InferenceChatToolFunction{Name: name, Description: description, Parameters: parameters, Strict: util.OptValue(true)}}
	}

	parameters := map[string]any{
		"type": "object",
		"properties": map[string]any{
			"input": map[string]any{"type": "string"},
		},
		"required":             []string{"input"},
		"additionalProperties": false,
	}
	return InferenceChatTool{Type: "function", Function: InferenceChatToolFunction{Name: name, Description: description, Parameters: parameters, Strict: util.OptValue(true)}}
}

func inferenceResponseDeduplicateTools(tools []InferenceChatTool) []InferenceChatTool {
	seen := map[string]bool{}
	result := make([]InferenceChatTool, 0, len(tools))
	for _, tool := range tools {
		name := tool.Function.Name
		if name == "" {
			result = append(result, tool)
			continue
		}
		if seen[name] {
			continue
		}
		seen[name] = true
		result = append(result, tool)
	}
	return result
}

func inferenceResponseBuiltinTool(toolType string) (InferenceChatTool, bool) {
	tool := InferenceChatTool{Type: "function"}
	tool.Function.Strict = util.OptValue(true)
	switch toolType {
	case "apply_patch":
		tool.Function.Name = "apply_patch"
		tool.Function.Description = `Apply one or more file modifications using the standard apply_patch format. The patch may create, update, rename, or delete files. Always return a complete, valid patch. Do not describe the changes in prose.\n\nThe patch must begin with '*** Begin Patch' and end with '*** End Patch'.\n\nSupported operations:\n- Create a file:\n  *** Add File: <path>\n  followed by one or more lines beginning with '+'.\n\n- Update a file:\n  *** Update File: <path>\n  followed by one or more unified-diff hunks beginning with '@@'. Context lines begin with a space, removed lines with '-', and added lines with '+'.\n\n- Delete a file:\n  *** Delete File: <path>\n\nA single patch may contain multiple file operations. Use relative paths. Never include markdown code fences or any explanatory text."`
		tool.Function.Parameters = map[string]any{
			"type": "object",
			"properties": map[string]any{
				"patch": map[string]any{"type": "string", "description": "The complete apply_patch document"},
			},
			"required":             []string{"patch"},
			"additionalProperties": false,
		}
	case "shell":
		tool.Function.Name = "shell"
		tool.Function.Description = "Run a non-interactive shell command in the workspace sandbox."
		tool.Function.Parameters = map[string]any{
			"type": "object",
			"properties": map[string]any{
				"command":    map[string]any{"type": "string"},
				"cwd":        map[string]any{"type": "string", "default": "."},
				"timeout_ms": map[string]any{"type": "integer", "default": 60000},
			},
			"required":             []string{"command"},
			"additionalProperties": false,
		}
	case "local_shell":
		tool.Function.Name = "local_shell"
		tool.Function.Description = "Run a command on the local shell."
		tool.Function.Parameters = map[string]any{
			"type": "object",
			"properties": map[string]any{
				"command":           map[string]any{"type": "array", "items": map[string]any{"type": "string"}},
				"env":               map[string]any{"type": "object"},
				"timeout_ms":        map[string]any{"type": "integer"},
				"user":              map[string]any{"type": "string"},
				"working_directory": map[string]any{"type": "string"},
			},
			"required":             []string{"command"},
			"additionalProperties": false,
		}
	default:
		return InferenceChatTool{}, false
	}
	return tool, true
}

func inferenceResponseToolChoice(raw json.RawMessage) (any, *util.HttpError) {
	if len(raw) == 0 || string(raw) == "null" {
		return nil, nil
	}
	var choiceString string
	if err := json.Unmarshal(raw, &choiceString); err == nil {
		return choiceString, nil
	}

	var choice struct {
		Type string `json:"type"`
		Name string `json:"name"`
	}
	if err := json.Unmarshal(raw, &choice); err != nil {
		return nil, util.HttpErr(http.StatusBadRequest, "invalid tool_choice")
	}
	if choice.Type == "function" && choice.Name != "" {
		return map[string]any{"type": "function", "function": map[string]any{"name": choice.Name}}, nil
	}
	if choice.Type == "custom" && choice.Name != "" {
		return map[string]any{"type": "function", "function": map[string]any{"name": choice.Name}}, nil
	}
	if _, ok := inferenceResponseBuiltinTool(choice.Type); ok {
		return map[string]any{"type": "function", "function": map[string]any{"name": choice.Type}}, nil
	}
	if choice.Type == "file_search" || strings.HasPrefix(choice.Type, "web_search") {
		return map[string]any{"type": "function", "function": map[string]any{"name": choice.Type}}, nil
	}
	if strings.HasPrefix(choice.Type, "computer") || choice.Type == "code_interpreter" || choice.Type == "image_generation" {
		return nil, nil
	}
	return nil, util.HttpErr(http.StatusBadRequest, "unsupported tool_choice")
}

func inferenceResponseFromChat(id string, request OaiResponseCreateRequest, chatResponse InferenceChatResponse) OaiResponse {
	createdAt := chatResponse.Created
	if createdAt == 0 {
		createdAt = time.Now().Unix()
	}
	completedAt := time.Now().Unix()
	resp := inferenceResponseBase(id, createdAt, request)
	resp.Status = "completed"
	resp.CompletedAt = &completedAt
	resp.Model = chatResponse.Model

	var output []any
	var outputText strings.Builder
	var reasoningText strings.Builder
	customTools := inferenceResponseCustomToolNames(request.Tools)
	if len(chatResponse.Choices) > 0 {
		message := chatResponse.Choices[0].Message
		reasoning := message.Reasoning.String()
		if reasoning != "" {
			reasoningText.WriteString(reasoning)
			output = append(output, inferenceResponseReasoningItem(reasoning))
		}

		for _, toolCall := range message.ToolCalls {
			output = append(output, inferenceResponseToolCallItem(toolCall.Id, toolCall.Id, toolCall.Function.Name, toolCall.Function.Arguments, "completed", customTools[toolCall.Function.Name]))
		}

		text := message.Content.String()
		if strings.TrimSpace(text) != "" || len(message.ToolCalls) == 0 {
			outputText.WriteString(text)
			output = append(output, OaiResponseOutputMessage{
				Id:      inferenceResponseNewId("msg"),
				Type:    "message",
				Status:  "completed",
				Role:    "assistant",
				Content: []OaiResponseOutputText{{Type: "output_text", Text: text, Annotations: []any{}}},
			})
		}
	}

	resp.Output = output
	resp.OutputText = outputText.String()
	resp.Usage = inferenceResponseUsage(chatResponse.Usage, reasoningText.String())
	return resp
}

func inferenceResponseBase(id string, createdAt int64, request OaiResponseCreateRequest) OaiResponse {
	metadata := request.Metadata
	if metadata == nil {
		metadata = map[string]string{}
	}
	format := any(map[string]any{"type": "text"})
	if len(request.Text.Format) > 0 && string(request.Text.Format) != "null" {
		_ = json.Unmarshal(request.Text.Format, &format)
	}
	truncation := request.Truncation
	if truncation == "" {
		truncation = "disabled"
	}
	toolChoice := any("auto")
	if len(request.ToolChoice) > 0 && string(request.ToolChoice) != "null" {
		_ = json.Unmarshal(request.ToolChoice, &toolChoice)
	}
	tools := make([]any, 0, len(request.Tools))
	for _, raw := range request.Tools {
		var tool any
		if json.Unmarshal(raw, &tool) == nil {
			tools = append(tools, tool)
		}
	}
	var instructions any
	if len(request.Instructions) > 0 && string(request.Instructions) != "null" {
		_ = json.Unmarshal(request.Instructions, &instructions)
	}
	var user any
	if request.User != "" {
		user = request.User
	}

	return OaiResponse{
		Id:                 id,
		Object:             "response",
		CreatedAt:          createdAt,
		Status:             "completed",
		CompletedAt:        nil,
		Error:              nil,
		IncompleteDetails:  nil,
		Instructions:       instructions,
		MaxOutputTokens:    request.MaxOutputTokens.GetPtrOrNil(),
		Model:              request.Model,
		Output:             []any{},
		ParallelToolCalls:  request.ParallelToolCalls.GetOrDefault(true),
		PreviousResponseID: nil,
		Reasoning:          OaiResponseReasoningReturn{Effort: request.Reasoning.Effort.GetPtrOrNil(), Summary: nil},
		Store:              true,
		Temperature:        request.Temperature.GetOrDefault(1),
		Text:               OaiResponseTextReturn{Format: format},
		ToolChoice:         toolChoice,
		Tools:              tools,
		TopLogprobs:        request.TopLogprobs.GetPtrOrNil(),
		TopP:               request.TopP.GetOrDefault(1),
		Truncation:         truncation,
		Usage:              nil,
		User:               user,
		Metadata:           metadata,
		Background:         request.Background,
	}
}

func inferenceResponseFailed(id string, createdAt int64, request OaiResponseCreateRequest, message string) OaiResponse {
	resp := inferenceResponseBase(id, createdAt, request)
	resp.Status = "failed"
	resp.Error = map[string]string{"code": "server_error", "message": message}
	return resp
}

func inferenceResponseReasoningItem(text string) OaiResponseReasoningItem {
	return OaiResponseReasoningItem{
		Id:      inferenceResponseNewId("rs"),
		Type:    "reasoning",
		Status:  "completed",
		Summary: []OaiResponseReasoningText{},
		Content: []OaiResponseReasoningText{{Type: "reasoning_text", Text: text}},
	}
}

func inferenceResponseUsage(usage InferenceChatUsage, reasoningText string) *OaiResponseUsage {
	reasoningTokens := inferenceEstimateTokensFromText(reasoningText)
	cachedTokens := 0
	if usage.PromptTokensDetails.Present {
		cachedTokens = usage.PromptTokensDetails.Value.CachedTokens
	}
	return &OaiResponseUsage{
		InputTokens:         usage.PromptTokens,
		InputTokensDetails:  OaiResponseInputTokenDetails{CachedTokens: cachedTokens},
		OutputTokens:        usage.CompletionTokens,
		OutputTokensDetails: OaiResponseOutputTokenDetails{ReasoningTokens: reasoningTokens},
		TotalTokens:         usage.TotalTokens,
	}
}

func inferenceResponseNewId(prefix string) string {
	return fmt.Sprintf("%s_%d_%d", prefix, time.Now().UnixNano(), inferenceResponseGlobals.IdAcc.Add(1))
}

func inferenceResponseStoreSet(response OaiResponse) {
	inferenceResponseGlobals.Mu.Lock()
	defer inferenceResponseGlobals.Mu.Unlock()
	inferenceResponseStoreCleanLocked()
	inferenceResponseGlobals.Responses[response.Id] = inferenceStoredResponse{Response: response, CreatedAt: time.Now()}
}

func inferenceResponseStoreGet(id string) (OaiResponse, bool) {
	inferenceResponseGlobals.Mu.RLock()
	stored, ok := inferenceResponseGlobals.Responses[id]
	inferenceResponseGlobals.Mu.RUnlock()
	if !ok || time.Since(stored.CreatedAt) > inferenceResponseStoreTTL {
		if ok {
			inferenceResponseStoreDelete(id)
		}
		return OaiResponse{}, false
	}
	return stored.Response, true
}

func inferenceResponseStoreDelete(id string) {
	inferenceResponseGlobals.Mu.Lock()
	delete(inferenceResponseGlobals.Responses, id)
	inferenceResponseGlobals.Mu.Unlock()
}

func inferenceResponseStoreCleanLocked() {
	threshold := time.Now().Add(-inferenceResponseStoreTTL)
	for id, stored := range inferenceResponseGlobals.Responses {
		if stored.CreatedAt.Before(threshold) {
			delete(inferenceResponseGlobals.Responses, id)
		}
	}
}
