package inference

import (
	"context"
	"encoding/json"
	"strings"
	"sync"

	"ucloud.dk/pkg/integrations/k8s/inference/tools"
	"ucloud.dk/pkg/integrations/k8s/shared"
)

const playgroundWebFetchConcurrency = 3

type playgroundWebFetchSandboxKey struct{}

func playgroundWebFetchSandboxFromContext(ctx context.Context) *shared.InferenceSandbox {
	sandbox, _ := ctx.Value(playgroundWebFetchSandboxKey{}).(*shared.InferenceSandbox)
	return sandbox
}

func (app *InferencePlaygroundApp) playgroundWebFetchBudget(request *InferenceChatRequest, count int) int {
	app.playgroundPruneToolHistory(request)
	app.mu.Lock()
	model, ok := app.modelByName(request.Model)
	app.mu.Unlock()
	if !ok || model.ContextWindow == nil {
		return tools.WebFetchMaxTokens
	}
	encoded, _ := json.Marshal(request)
	reserve := max(4096, request.MaxCompletionTokens.GetOrDefault(4096))
	remaining := *model.ContextWindow - tools.WebFetchEstimateTokens(string(encoded)) - reserve - count*1024
	return max(0, min(tools.WebFetchMaxTokens, remaining/(2*count)))
}

func playgroundWebFetchLimitCall(call InferenceChatToolCall, budget int) InferenceChatToolCall {
	var args map[string]any
	if json.Unmarshal([]byte(call.Function.Arguments), &args) != nil || args == nil {
		return call
	}
	limit := tools.WebFetchDefaultTokens
	if value, ok := args["max_tokens"].(float64); ok && value > 0 {
		limit = int(min(value, float64(tools.WebFetchMaxTokens)))
	}
	args["max_tokens"] = min(limit, budget)
	call.Function.Arguments = playgroundToolJSON(args)
	return call
}

func (app *InferencePlaygroundApp) playgroundRunToolCalls(ctx context.Context, request *InferenceChatRequest, threadId string, assistantIndex int, startedAt int64, calls []InferenceChatToolCall) {
	for start := 0; start < len(calls); {
		if ctx.Err() != nil {
			return
		}
		end := start + 1
		webFetch := strings.TrimSpace(calls[start].Function.Name) == playgroundInternetToolWebFetch
		if webFetch {
			for end < len(calls) && strings.TrimSpace(calls[end].Function.Name) == playgroundInternetToolWebFetch {
				end++
			}
		}
		group := calls[start:end]
		results := make([]playgroundToolResult, len(group))
		ready := make([]bool, len(group))
		budget := 0
		if webFetch {
			budget = app.playgroundWebFetchBudget(request, len(group))
		}
		for i, call := range group {
			app.appendThreadAssistantPart(threadId, assistantIndex, playgroundToolChatPart(call, "running", ""), request.Model, startedAt)
			message := ""
			if ctx.Err() != nil {
				message = "tool call cancelled"
			} else if webFetch && budget < 128 {
				message = "not enough model context remains for web content; answer using existing results"
			} else if webFetch {
				var args struct {
					URL string `json:"url"`
				}
				_ = json.Unmarshal([]byte(call.Function.Arguments), &args)
				message = app.ensureInternetAccessForTool(ctx, threadId, strings.TrimSpace(args.URL))
			}
			if message != "" {
				results[i] = playgroundToolResult{Message: playgroundToolError(call.Id, message), Error: message}
			} else {
				ready[i] = true
			}
		}
		groupContext := ctx
		if webFetch {
			for _, allowed := range ready {
				if !allowed {
					continue
				}
				sandbox, sandboxErr := app.playgroundToolSandbox()
				if sandboxErr != "" {
					for i, call := range group {
						if ready[i] {
							results[i] = playgroundToolResult{Message: playgroundToolError(call.Id, sandboxErr), Error: sandboxErr}
							ready[i] = false
						}
					}
				} else {
					groupContext = context.WithValue(ctx, playgroundWebFetchSandboxKey{}, sandbox)
				}
				break
			}
		}
		var wait sync.WaitGroup
		workers := 1
		if webFetch {
			workers = min(playgroundWebFetchConcurrency, len(group))
		}
		for worker := 0; worker < workers; worker++ {
			wait.Add(1)
			go func(worker int) {
				defer wait.Done()
				for i := worker; i < len(group); i += workers {
					if !ready[i] {
						continue
					}
					call := group[i]
					if ctx.Err() != nil {
						results[i] = playgroundToolResult{Message: playgroundToolError(call.Id, "tool call cancelled"), Error: "tool call cancelled"}
						continue
					}
					if webFetch {
						call = playgroundWebFetchLimitCall(call, budget)
					}
					results[i] = app.playgroundToolDispatch(groupContext, threadId, call)
				}
			}(worker)
		}
		wait.Wait()
		for i, result := range results {
			app.appendThreadAssistantPart(threadId, assistantIndex, playgroundToolChatPart(group[i], playgroundToolStatus(result), playgroundToolPartBody(group[i], result)), request.Model, startedAt)
			request.Messages = append(request.Messages, result.Message)
		}
		start = end
	}
}
