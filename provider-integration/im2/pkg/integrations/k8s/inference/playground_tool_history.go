package inference

import (
	"encoding/json"
	"strings"
	"time"

	"ucloud.dk/pkg/integrations/k8s/inference/tools"
)

func playgroundToolHistoryJson(text string) map[string]any {
	var value map[string]any
	_ = json.Unmarshal([]byte(text), &value)
	return value
}

func playgroundToolHistoryResult(text string) map[string]any {
	value := playgroundToolHistoryJson(text)
	if stdout, ok := value["stdout"].(string); ok {
		if result := playgroundToolHistoryJson(stdout); result != nil {
			return result
		}
	}
	return value
}

func playgroundToolHistoryRecord(name, arguments, output, status string) map[string]any {
	args := playgroundToolHistoryJson(arguments)
	result := playgroundToolHistoryResult(output)
	if status == "" {
		status = "completed"
		if result["error"] != nil || result["timed_out"] == true {
			status = "error"
		}
	}
	record := map[string]any{
		"tool":           name,
		"output_omitted": true,
		"notice":         "The tool already ran. Its result body was omitted to save model context. This record is not source content.",
	}
	if status != "" {
		record["tool_status"] = status
	}
	for _, key := range []string{"url", "document_id", "query", "format"} {
		if value, ok := args[key].(string); ok && value != "" {
			record[key] = value
		}
	}
	for _, key := range []string{"url", "document_id", "query", "format", "status", "error", "timed_out", "expires_at", "next_cursor", "download_truncated", "truncated"} {
		if value, ok := result[key]; ok {
			record[key] = value
		}
	}
	if message, ok := result["stderr"].(string); ok && message != "" {
		record["error"] = message
	}
	if message, ok := record["error"].(string); ok {
		characters := []rune(message)
		if len(characters) > 512 {
			record["error"] = string(characters[:512]) + "…"
		}
	}
	if expires, ok := record["expires_at"].(string); ok {
		if expiry, err := time.Parse(time.RFC3339, expires); err == nil && !time.Now().Before(expiry) {
			record["cache_expired"] = true
			record["notice"] = "The result body was omitted. The cached document has expired. Fetch its URL again if source content is needed."
		}
	}
	return record
}

func playgroundToolHistorySourceRecords(parts []playgroundChatMessagePart) []map[string]any {
	var records []map[string]any
	positions := map[string]int{}
	for _, part := range parts {
		if part.Kind != "tool" || part.ToolName != playgroundInternetToolWebFetch || part.Status == "running" {
			continue
		}
		output := ""
		if _, after, ok := strings.Cut(part.Body, "Result:\n"); ok {
			output, _, _ = strings.Cut(after, "\n\nError:\n")
		}
		record := playgroundToolHistoryRecord(part.ToolName, part.Text, strings.TrimSpace(output), part.Status)
		if _, ok := record["error"]; !ok && part.Status == "error" {
			if _, after, found := strings.Cut(part.Body, "Error:\n"); found {
				record["error"] = strings.TrimSpace(after)
			}
		}
		identity := playgroundToolJSON([]any{record["url"], record["document_id"], record["query"]})
		if index, ok := positions[identity]; ok {
			records[index] = record
		} else {
			positions[identity] = len(records)
			records = append(records, record)
		}
	}
	return records
}

func (app *InferencePlaygroundApp) playgroundPruneToolHistory(request *InferenceChatRequest) {
	app.mu.Lock()
	model, ok := app.modelByName(request.Model)
	app.mu.Unlock()
	if !ok || model.ContextWindow == nil {
		return
	}
	inputBudget := max(0, *model.ContextWindow-max(4096, request.MaxCompletionTokens.GetOrDefault(4096)))
	threshold := inputBudget * 80 / 100
	encoded, _ := json.Marshal(request)
	estimate := tools.WebFetchEstimateTokens(string(encoded))
	if estimate <= threshold {
		return
	}
	latestBatch := -1
	calls := map[string]InferenceChatToolCall{}
	completed := map[string]bool{}
	for _, message := range request.Messages {
		if message.Role == "tool" {
			completed[message.ToolCallID] = true
		}
	}
	for i, message := range request.Messages {
		if message.Role != "assistant" || len(message.ToolCalls) == 0 {
			continue
		}
		complete := true
		for _, call := range message.ToolCalls {
			calls[call.Id] = call
			complete = complete && completed[call.Id]
		}
		if complete {
			latestBatch = i
		}
	}
	for i := 0; i < latestBatch && estimate > threshold; i++ {
		message := &request.Messages[i]
		call, found := calls[message.ToolCallID]
		if message.Role != "tool" || !found {
			continue
		}
		output := message.Content.String()
		if playgroundToolHistoryJson(output)["output_omitted"] == true {
			continue
		}
		record := playgroundToolHistoryRecord(call.Function.Name, call.Function.Arguments, output, "")
		compact := inferenceChatTextContent(playgroundToolJSON(record))
		before, _ := json.Marshal(message.Content)
		after, _ := json.Marshal(compact)
		saving := tools.WebFetchEstimateTokens(string(before)) - tools.WebFetchEstimateTokens(string(after))
		if saving <= 0 {
			continue
		}
		message.Content = compact
		estimate -= saving
	}
}
