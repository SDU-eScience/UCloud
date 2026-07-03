package k8s

import (
	"encoding/json"
	"testing"

	"ucloud.dk/shared/pkg/util"
)

func TestInferenceResponseStringInputToChat(t *testing.T) {
	request := OaiResponseCreateRequest{
		Model:        "model-a",
		Instructions: json.RawMessage(`"be helpful"`),
		Input:        json.RawMessage(`"hello"`),
	}

	chatRequest, httpErr := inferenceResponseChatRequest(request)
	if httpErr != nil {
		t.Fatalf("unexpected error: %v", httpErr)
	}
	if chatRequest.Model != "model-a" {
		t.Fatalf("unexpected model: %s", chatRequest.Model)
	}
	if len(chatRequest.Messages) != 2 {
		t.Fatalf("expected 2 messages, got %+v", chatRequest.Messages)
	}
	if chatRequest.Messages[0].Role != "system" || chatRequest.Messages[0].Content.String() != "be helpful" {
		t.Fatalf("unexpected instructions message: %+v", chatRequest.Messages[0])
	}
	if chatRequest.Messages[1].Role != "user" || chatRequest.Messages[1].Content.String() != "hello" {
		t.Fatalf("unexpected input message: %+v", chatRequest.Messages[1])
	}
}

func TestInferenceResponseFunctionToolToChat(t *testing.T) {
	request := OaiResponseCreateRequest{
		Model: "model-a",
		Input: json.RawMessage(`"hello"`),
		Tools: []json.RawMessage{
			json.RawMessage(`{"type":"web_search_preview"}`),
			json.RawMessage(`{"type":"function","name":"get_weather","description":"weather","parameters":{"type":"object"},"strict":true}`),
		},
		ToolChoice: json.RawMessage(`{"type":"function","name":"get_weather"}`),
	}

	chatRequest, httpErr := inferenceResponseChatRequest(request)
	if httpErr != nil {
		t.Fatalf("unexpected error: %v", httpErr)
	}
	if len(chatRequest.Tools) != 1 {
		t.Fatalf("expected only function tool, got %+v", chatRequest.Tools)
	}
	if chatRequest.Tools[0].Function.Name != "get_weather" || !chatRequest.Tools[0].Function.Strict.GetOrDefault(false) {
		t.Fatalf("unexpected tool: %+v", chatRequest.Tools[0])
	}
	if chatRequest.ToolChoice == nil {
		t.Fatal("expected converted tool choice")
	}
}

func TestInferenceResponseNamespaceToolToChat(t *testing.T) {
	request := OaiResponseCreateRequest{
		Model: "model-a",
		Input: json.RawMessage(`"hello"`),
		Tools: []json.RawMessage{
			json.RawMessage(`{"type":"namespace","name":"weather","tools":[{"type":"web_search_preview"},{"type":"function","name":"get_forecast","description":"forecast","parameters":{"type":"object"}}]}`),
		},
	}

	chatRequest, httpErr := inferenceResponseChatRequest(request)
	if httpErr != nil {
		t.Fatalf("unexpected error: %v", httpErr)
	}
	if len(chatRequest.Tools) != 1 {
		t.Fatalf("expected one flattened function tool, got %+v", chatRequest.Tools)
	}
	if chatRequest.Tools[0].Function.Name != "get_forecast" {
		t.Fatalf("unexpected flattened tool: %+v", chatRequest.Tools[0])
	}
}

func TestInferenceResponseBuiltinToolInjection(t *testing.T) {
	request := OaiResponseCreateRequest{
		Model: "model-a",
		Input: json.RawMessage(`"hello"`),
		Tools: []json.RawMessage{
			json.RawMessage(`{"type":"apply_patch"}`),
			json.RawMessage(`{"type":"shell"}`),
			json.RawMessage(`{"type":"local_shell"}`),
			json.RawMessage(`{"type":"apply_patch"}`),
		},
		ToolChoice: json.RawMessage(`{"type":"apply_patch"}`),
	}

	chatRequest, httpErr := inferenceResponseChatRequest(request)
	if httpErr != nil {
		t.Fatalf("unexpected error: %v", httpErr)
	}
	if len(chatRequest.Tools) != 3 {
		t.Fatalf("expected deduplicated builtin tools, got %+v", chatRequest.Tools)
	}
	if chatRequest.Tools[0].Function.Name != "apply_patch" || chatRequest.Tools[1].Function.Name != "shell" || chatRequest.Tools[2].Function.Name != "local_shell" {
		t.Fatalf("unexpected tools: %+v", chatRequest.Tools)
	}
	if !chatRequest.Tools[0].Function.Strict.GetOrDefault(false) {
		t.Fatalf("expected injected tool to be strict: %+v", chatRequest.Tools[0])
	}

	choice, ok := chatRequest.ToolChoice.(map[string]any)
	if !ok {
		t.Fatalf("expected converted tool choice, got %+v", chatRequest.ToolChoice)
	}
	function, ok := choice["function"].(map[string]any)
	if !ok || function["name"] != "apply_patch" {
		t.Fatalf("unexpected tool choice: %+v", chatRequest.ToolChoice)
	}
}

func TestInferenceResponseOutputTextInputToChat(t *testing.T) {
	request := OaiResponseCreateRequest{
		Model: "model-a",
		Input: json.RawMessage(`[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"previous answer"}]},{"type":"message","role":"user","content":[{"type":"input_text","text":"next question"}]}]`),
	}

	chatRequest, httpErr := inferenceResponseChatRequest(request)
	if httpErr != nil {
		t.Fatalf("unexpected error: %v", httpErr)
	}
	if len(chatRequest.Messages) != 2 {
		t.Fatalf("expected 2 messages, got %+v", chatRequest.Messages)
	}
	if chatRequest.Messages[0].Role != "assistant" || chatRequest.Messages[0].Content.String() != "previous answer" {
		t.Fatalf("unexpected assistant history message: %+v", chatRequest.Messages[0])
	}
	if chatRequest.Messages[1].Role != "user" || chatRequest.Messages[1].Content.String() != "next question" {
		t.Fatalf("unexpected user message: %+v", chatRequest.Messages[1])
	}
}

func TestInferenceResponseFunctionCallInputToChat(t *testing.T) {
	request := OaiResponseCreateRequest{
		Model: "model-a",
		Input: json.RawMessage(`[{"type":"function_call","id":"fc_1","call_id":"call_1","name":"get_weather","arguments":"{\"city\":\"Aalborg\"}"},{"type":"function_call_output","call_id":"call_1","output":{"temperature":12}}]`),
	}

	chatRequest, httpErr := inferenceResponseChatRequest(request)
	if httpErr != nil {
		t.Fatalf("unexpected error: %v", httpErr)
	}
	if len(chatRequest.Messages) != 2 {
		t.Fatalf("expected 2 messages, got %+v", chatRequest.Messages)
	}
	toolCallMessage := chatRequest.Messages[0]
	if toolCallMessage.Role != "assistant" || len(toolCallMessage.ToolCalls) != 1 {
		t.Fatalf("unexpected tool call message: %+v", toolCallMessage)
	}
	if toolCallMessage.ToolCalls[0].Id != "call_1" || toolCallMessage.ToolCalls[0].Function.Name != "get_weather" || toolCallMessage.ToolCalls[0].Function.Arguments != `{"city":"Aalborg"}` {
		t.Fatalf("unexpected tool call: %+v", toolCallMessage.ToolCalls[0])
	}
	toolOutputMessage := chatRequest.Messages[1]
	if toolOutputMessage.Role != "tool" || toolOutputMessage.ToolCallID != "call_1" || toolOutputMessage.Content.String() != `{"temperature":12}` {
		t.Fatalf("unexpected tool output message: %+v", toolOutputMessage)
	}
}

func TestInferenceResponseBuiltinCallInputToChat(t *testing.T) {
	request := OaiResponseCreateRequest{
		Model: "model-a",
		Input: json.RawMessage(`[{"type":"apply_patch_call","id":"ap_1","call_id":"call_patch","operation":{"type":"update_file","path":"a.txt","diff":"*** Begin Patch\n*** End Patch"}},{"type":"apply_patch_call_output","call_id":"call_patch","status":"completed","output":"ok"},{"type":"shell_call","id":"sh_1","call_id":"call_shell","action":{"commands":["pwd"],"timeout_ms":1000}},{"type":"shell_call_output","call_id":"call_shell","output":[{"stdout":"/tmp\n","stderr":"","outcome":{"type":"exit","exit_code":0}}],"status":"completed"},{"type":"local_shell_call","id":"lsh_1","call_id":"call_local_shell","action":{"type":"exec","command":["pwd"],"timeout_ms":1000,"working_directory":"/tmp"}},{"type":"local_shell_call_output","id":"call_local_shell","output":"/tmp\n","status":"completed"}]`),
	}

	chatRequest, httpErr := inferenceResponseChatRequest(request)
	if httpErr != nil {
		t.Fatalf("unexpected error: %v", httpErr)
	}
	if len(chatRequest.Messages) != 6 {
		t.Fatalf("expected 4 messages, got %+v", chatRequest.Messages)
	}
	if chatRequest.Messages[0].ToolCalls[0].Function.Name != "apply_patch" || chatRequest.Messages[0].ToolCalls[0].Function.Arguments != `{"patch":"*** Begin Patch\n*** End Patch"}` {
		t.Fatalf("unexpected apply_patch call message: %+v", chatRequest.Messages[0])
	}
	if chatRequest.Messages[1].Role != "tool" || chatRequest.Messages[1].ToolCallID != "call_patch" || chatRequest.Messages[1].Content.String() != "ok" {
		t.Fatalf("unexpected apply_patch output message: %+v", chatRequest.Messages[1])
	}
	if chatRequest.Messages[2].ToolCalls[0].Function.Name != "shell" || chatRequest.Messages[2].ToolCalls[0].Function.Arguments != `{"command":"pwd","timeout_ms":1000}` {
		t.Fatalf("unexpected shell call message: %+v", chatRequest.Messages[2])
	}
	if chatRequest.Messages[3].Role != "tool" || chatRequest.Messages[3].ToolCallID != "call_shell" || chatRequest.Messages[3].Content.String() != "/tmp\n" {
		t.Fatalf("unexpected shell output message: %+v", chatRequest.Messages[3])
	}
	if chatRequest.Messages[4].ToolCalls[0].Function.Name != "local_shell" || chatRequest.Messages[4].ToolCalls[0].Function.Arguments != `{"command":["pwd"],"timeout_ms":1000,"working_directory":"/tmp"}` {
		t.Fatalf("unexpected local_shell call message: %+v", chatRequest.Messages[4])
	}
	if chatRequest.Messages[5].Role != "tool" || chatRequest.Messages[5].ToolCallID != "call_local_shell" || chatRequest.Messages[5].Content.String() != "/tmp\n" {
		t.Fatalf("unexpected local_shell output message: %+v", chatRequest.Messages[5])
	}
}

func TestInferenceResponseBuiltinCallOutputTypes(t *testing.T) {
	chatResponse := InferenceChatResponse{
		Created: 123,
		Model:   "model-a",
		Choices: []InferenceChatChoice{{
			Message: InferenceChatMessage{Role: "assistant", ToolCalls: []InferenceChatToolCall{
				{Id: "call_patch", Type: "function", Function: InferenceChatToolCallFunction{Name: "apply_patch", Arguments: `{"patch":"*** Begin Patch\n*** End Patch"}`}},
				{Id: "call_shell", Type: "function", Function: InferenceChatToolCallFunction{Name: "shell", Arguments: `{"command":"pwd","timeout_ms":1000}`}},
				{Id: "call_local_shell", Type: "function", Function: InferenceChatToolCallFunction{Name: "local_shell", Arguments: `{"command":["pwd"],"timeout_ms":1000,"working_directory":"/tmp"}`}},
			}},
		}},
	}

	resp := inferenceResponseFromChat("resp_1", OaiResponseCreateRequest{Model: "model-a"}, chatResponse)
	if len(resp.Output) != 3 {
		t.Fatalf("expected two tool output items, got %+v", resp.Output)
	}
	if item, ok := resp.Output[0].(OaiResponseApplyPatchCall); !ok || item.Type != "apply_patch_call" || item.Operation.Diff == "" {
		t.Fatalf("unexpected apply_patch response item: %+v", resp.Output[0])
	}
	if item, ok := resp.Output[1].(OaiResponseShellCall); !ok || item.Type != "shell_call" || len(item.Action.Commands) != 1 || item.Action.Commands[0] != "pwd" {
		t.Fatalf("unexpected shell response item: %+v", resp.Output[1])
	}
	if item, ok := resp.Output[2].(OaiResponseLocalShellCall); !ok || item.Type != "local_shell_call" || len(item.Action.Command) != 1 || item.Action.Command[0] != "pwd" || item.Action.WorkingDirectory != "/tmp" {
		t.Fatalf("unexpected local_shell response item: %+v", resp.Output[2])
	}
}

func TestInferenceResponseParseApplyPatchOperation(t *testing.T) {
	update := inferenceResponseParseApplyPatchOperation("*** Begin Patch\n*** Update File: apply_patch_success.txt\n@@\n+apply_patch_success\n*** End Patch")
	if update.Type != "update_file" || update.Path != "apply_patch_success.txt" || update.Diff != "@@\n+apply_patch_success" {
		t.Fatalf("unexpected update operation: %+v", update)
	}

	create := inferenceResponseParseApplyPatchOperation("*** Begin Patch\n*** Add File: created.txt\n+hello\n*** End Patch")
	if create.Type != "create_file" || create.Path != "created.txt" || create.Diff != "+hello" {
		t.Fatalf("unexpected create operation: %+v", create)
	}

	deleteOp := inferenceResponseParseApplyPatchOperation("*** Begin Patch\n*** Delete File: old.txt\n*** End Patch")
	if deleteOp.Type != "delete_file" || deleteOp.Path != "old.txt" || deleteOp.Diff != "" {
		t.Fatalf("unexpected delete operation: %+v", deleteOp)
	}
}

func TestInferenceResponseApplyPatchCallOutputOperationIsStructured(t *testing.T) {
	chatResponse := InferenceChatResponse{
		Created: 123,
		Model:   "model-a",
		Choices: []InferenceChatChoice{{
			Message: InferenceChatMessage{Role: "assistant", ToolCalls: []InferenceChatToolCall{{
				Id:       "call_patch",
				Type:     "function",
				Function: InferenceChatToolCallFunction{Name: "apply_patch", Arguments: `{"patch":"*** Begin Patch\n*** Update File: apply_patch_success.txt\n@@\n+apply_patch_success\n*** End Patch"}`},
			}}},
		}},
	}

	resp := inferenceResponseFromChat("resp_1", OaiResponseCreateRequest{Model: "model-a"}, chatResponse)
	if len(resp.Output) != 1 {
		t.Fatalf("expected one output item, got %+v", resp.Output)
	}
	item, ok := resp.Output[0].(OaiResponseApplyPatchCall)
	if !ok {
		t.Fatalf("expected apply_patch_call, got %+v", resp.Output[0])
	}
	if item.Operation.Path != "apply_patch_success.txt" || item.Operation.Diff != "@@\n+apply_patch_success" {
		t.Fatalf("unexpected operation: %+v", item.Operation)
	}
}

func TestInferenceResponseSuppressWhitespaceMessageBeforeToolCall(t *testing.T) {
	chatResponse := InferenceChatResponse{
		Created: 123,
		Model:   "model-a",
		Choices: []InferenceChatChoice{{
			Message: InferenceChatMessage{
				Role:    "assistant",
				Content: inferenceChatTextContent("\n\n"),
				ToolCalls: []InferenceChatToolCall{{
					Id:       "call_patch",
					Type:     "function",
					Function: InferenceChatToolCallFunction{Name: "apply_patch", Arguments: `{"patch":"*** Begin Patch\n*** End Patch"}`},
				}},
			},
		}},
	}

	resp := inferenceResponseFromChat("resp_1", OaiResponseCreateRequest{Model: "model-a"}, chatResponse)
	if len(resp.Output) != 1 {
		t.Fatalf("expected only tool call output, got %+v", resp.Output)
	}
	if _, ok := resp.Output[0].(OaiResponseApplyPatchCall); !ok {
		t.Fatalf("expected apply_patch_call, got %+v", resp.Output[0])
	}
	if resp.OutputText != "" {
		t.Fatalf("expected empty output_text, got %q", resp.OutputText)
	}
}

func TestInferenceResponseToolCallOnlyHasEmptyOutputText(t *testing.T) {
	chatResponse := InferenceChatResponse{
		Created: 123,
		Model:   "model-a",
		Choices: []InferenceChatChoice{{
			Message: InferenceChatMessage{
				Role:    "assistant",
				Content: inferenceChatTextContent("\n\n"),
				ToolCalls: []InferenceChatToolCall{{
					Id:       "call_1",
					Type:     "function",
					Function: InferenceChatToolCallFunction{Name: "exec_command", Arguments: `{"cmd":"ls"}`},
				}},
			},
		}},
	}

	resp := inferenceResponseFromChat("resp_1", OaiResponseCreateRequest{Model: "model-a"}, chatResponse)
	if resp.OutputText != "" {
		t.Fatalf("expected empty output_text, got %q", resp.OutputText)
	}
	if len(resp.Output) != 1 {
		t.Fatalf("expected only tool call output, got %+v", resp.Output)
	}
}

func TestInferenceResponseRejectEncryptedReasoning(t *testing.T) {
	httpErr := inferenceResponseValidateRequest(OaiResponseCreateRequest{Include: []string{"reasoning.encrypted_content"}})
	if httpErr == nil {
		t.Fatal("expected encrypted reasoning include to be rejected")
	}
}

func TestInferenceResponseFromChatIncludesReasoning(t *testing.T) {
	chatResponse := InferenceChatResponse{
		Id:      "chatcmpl_1",
		Created: 123,
		Model:   "model-a",
		Choices: []InferenceChatChoice{{
			Message: InferenceChatMessage{
				Role:      "assistant",
				Reasoning: inferenceChatTextContent("thinking"),
				Content:   inferenceChatTextContent("answer"),
			},
		}},
		Usage: InferenceChatUsage{PromptTokens: 2, CompletionTokens: 3, TotalTokens: 5, PromptTokensDetails: util.OptValue(InferenceChatTokenDetails{CachedTokens: 1})},
	}

	resp := inferenceResponseFromChat("resp_1", OaiResponseCreateRequest{Model: "model-a"}, chatResponse)
	if resp.OutputText != "answer" {
		t.Fatalf("unexpected output text: %q", resp.OutputText)
	}
	if len(resp.Output) != 2 {
		t.Fatalf("expected reasoning and message output, got %+v", resp.Output)
	}
	if resp.Usage == nil || resp.Usage.InputTokensDetails.CachedTokens != 1 || resp.Usage.OutputTokensDetails.ReasoningTokens == 0 {
		t.Fatalf("unexpected usage: %+v", resp.Usage)
	}
}
