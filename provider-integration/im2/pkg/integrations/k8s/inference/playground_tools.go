package inference

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"ucloud.dk/pkg/integrations/k8s/shared"
	"ucloud.dk/shared/pkg/util"
)

const (
	playgroundToolMaxIterations        = 10
	playgroundToolOutputLimit          = 64 * 1024
	playgroundToolDefaultTimeout       = 60 * time.Second
	playgroundToolMaxTimeout           = 2 * time.Minute
	playgroundWebFetchDefaultTimeoutMs = 15000
	playgroundWebFetchMaxTimeoutMs     = 30000
)

type playgroundToolResult struct {
	Message InferenceChatMessage
	Output  string
	Error   string
}

type playgroundCommandResult struct {
	Stdout   string
	Stderr   string
	TimedOut bool
	Err      *util.HttpError
}

type playgroundCappedBuffer struct {
	buf       bytes.Buffer
	limit     int
	truncated bool
}

func (b *playgroundCappedBuffer) Write(p []byte) (int, error) {
	if b.limit <= 0 {
		b.truncated = true
		return len(p), nil
	}
	remaining := b.limit - b.buf.Len()
	if remaining <= 0 {
		b.truncated = true
		return len(p), nil
	}
	if len(p) > remaining {
		_, _ = b.buf.Write(p[:remaining])
		b.truncated = true
		return len(p), nil
	}
	_, _ = b.buf.Write(p)
	return len(p), nil
}

func (b *playgroundCappedBuffer) String() string {
	result := b.buf.String()
	if b.truncated {
		result += "\n[output truncated]"
	}
	return result
}

const playgroundInternetToolWebFetch = "web_fetch"
const playgroundInternetToolRequestAccess = "request_internet_access"

func (app *InferencePlaygroundApp) playgroundToolDefinitions() []InferenceChatTool {
	if app == nil || app.Developer {
		return nil
	}

	tools := []InferenceChatTool{
		playgroundToolDefinition("bash", "Run a non-interactive shell command in a sandboxed environment for calculations and deterministic analysis.", map[string]any{
			"command":    map[string]any{"type": "string", "description": "Command to run."},
			"cwd":        map[string]any{"type": "string", "description": "Optional working directory.", "default": "/"},
			"timeout_ms": map[string]any{"type": "integer", "description": "Optional timeout in milliseconds.", "default": 60000},
		}, []string{"command"}),
	}

	if app.currentThreadInternetDenied() {
		tools = append(tools, playgroundToolDefinition(playgroundInternetToolRequestAccess, "Ask the user for permission to access the internet. Call this tool when answering the question requires internet access.", map[string]any{
			"reason": map[string]any{"type": "string", "description": "Short explanation of why internet access is needed."},
		}, []string{"reason"}),
		)
	} else {
		tools = append(tools, playgroundToolDefinition(playgroundInternetToolWebFetch, "Fetch a public http or https URL from inside the sandbox and return capped markdown or HTML.", map[string]any{
			"url":        map[string]any{"type": "string", "description": "Public http or https URL to fetch."},
			"format":     map[string]any{"type": "string", "description": "Output format: markdown or html.", "default": "markdown", "enum": []string{"markdown", "html"}},
			"timeout_ms": map[string]any{"type": "integer", "description": "Optional timeout in milliseconds, capped server-side.", "default": playgroundWebFetchDefaultTimeoutMs},
		}, []string{"url"}),
		)
	}

	return tools
}

func playgroundToolDefinition(name string, description string, properties map[string]any, required []string) InferenceChatTool {
	return InferenceChatTool{Type: "function", Function: InferenceChatToolFunction{Name: name, Description: description, Strict: util.OptValue(true), Parameters: map[string]any{"type": "object", "properties": properties, "required": required, "additionalProperties": false}}}
}

func (app *InferencePlaygroundApp) playgroundToolDispatch(ctx context.Context, threadId string, call InferenceChatToolCall) playgroundToolResult {
	return app.playgroundToolDispatchWithMode(ctx, threadId, call, false)
}

func (app *InferencePlaygroundApp) playgroundToolDispatchForDeveloper(ctx context.Context, call InferenceChatToolCall) playgroundToolResult {
	return app.playgroundToolDispatchWithMode(ctx, "", call, true)
}

func (app *InferencePlaygroundApp) playgroundToolDispatchWithMode(ctx context.Context, threadId string, call InferenceChatToolCall, allowDeveloper bool) playgroundToolResult {
	name := strings.TrimSpace(call.Function.Name)
	if call.Id == "" {
		call.Id = name
	}
	if !app.playgroundToolAvailable(name, allowDeveloper) {
		return playgroundToolResult{Message: playgroundToolError(call.Id, fmt.Sprintf("tool %q is not available", name)), Error: "tool is not available"}
	}

	if name == playgroundInternetToolRequestAccess {
		return app.inferenceToolRequestInternetAccess(ctx, threadId, call)
	}
	if name == playgroundInternetToolWebFetch {
		var args struct {
			URL string `json:"url"`
		}
		_ = json.Unmarshal([]byte(call.Function.Arguments), &args)
		if !app.ensureInternetAccessForTool(ctx, threadId, strings.TrimSpace(args.URL)) {
			return playgroundToolResult{Message: playgroundToolError(call.Id, "internet access is not enabled in this session"), Error: "internet access is not enabled in this session"}
		}
	}

	var result playgroundToolResult
	switch name {
	case "bash":
		result = app.inferenceToolBash(call)
	case playgroundInternetToolWebFetch:
		result = app.inferenceToolWebFetch(call)
	default:
		result = playgroundToolResult{Message: playgroundToolError(call.Id, fmt.Sprintf("tool %q is not supported", name)), Error: "tool is not supported"}
	}
	return result
}

func (app *InferencePlaygroundApp) playgroundToolSandbox() (*shared.InferenceSandbox, string) {
	app.mu.Lock()
	owner := app.Owner
	app.mu.Unlock()

	sandbox, err := shared.InferenceSandboxSetFolders(owner, util.OptNone[string](), []string{})
	if err != nil {
		return nil, err.Why
	}
	sandbox.AutoLease = true
	return sandbox, ""
}

func playgroundDeveloperSlashToolCall(prompt string) (InferenceChatToolCall, bool, string) {
	prompt = strings.TrimSpace(prompt)
	if !strings.HasPrefix(prompt, "/") {
		return InferenceChatToolCall{}, false, ""
	}
	command, rest, _ := strings.Cut(strings.TrimPrefix(prompt, "/"), " ")
	command = strings.TrimSpace(command)
	rest = strings.TrimSpace(rest)
	if command == "" {
		return InferenceChatToolCall{}, false, ""
	}

	toolName := command
	arguments := ""
	switch command {
	case "tool":
		var rawArgs string
		toolName, rawArgs, _ = strings.Cut(rest, " ")
		toolName = strings.TrimSpace(toolName)
		arguments = strings.TrimSpace(rawArgs)
	case "bash":
		arguments = playgroundToolJSON(map[string]any{"command": rest})
	case playgroundInternetToolWebFetch:
		arguments = playgroundToolJSON(map[string]any{"url": rest})
	default:
		return InferenceChatToolCall{}, false, ""
	}

	if toolName == "" {
		return InferenceChatToolCall{}, true, "missing tool name"
	}
	if arguments == "" {
		return InferenceChatToolCall{}, true, "missing tool arguments"
	}
	if !json.Valid([]byte(arguments)) {
		return InferenceChatToolCall{}, true, "tool arguments must be JSON"
	}
	return InferenceChatToolCall{Id: "dev-" + util.SecureToken(), Type: "function", Function: InferenceChatToolCallFunction{Name: toolName, Arguments: arguments}}, true, ""
}

func (app *InferencePlaygroundApp) inferenceToolBash(call InferenceChatToolCall) playgroundToolResult {
	var args struct {
		Command   string `json:"command"`
		Cwd       string `json:"cwd"`
		TimeoutMs int    `json:"timeout_ms"`
	}
	if err := playgroundToolDecodeArgs(call, &args); err != "" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, err), Error: err}
	}
	internetEnabled := shared.InferenceSandboxInternetEnabledFor(app.Owner)
	if err := playgroundToolBashValidate(args.Command, internetEnabled); err != "" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, err), Error: err}
	}
	cwd := strings.TrimSpace(args.Cwd)
	if cwd == "" || cwd == "." {
		cwd = "/"
	}
	if !filepath.IsAbs(cwd) {
		return playgroundToolResult{Message: playgroundToolError(call.Id, "cwd must be an absolute path"), Error: "cwd must be an absolute path"}
	}
	timeout := playgroundToolTimeout(args.TimeoutMs)
	sandbox, sandboxErr := app.playgroundToolSandbox()
	if sandboxErr != "" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, sandboxErr), Error: sandboxErr}
	}
	commandResult := playgroundRunSandboxCommand(func() *shared.TerminalCmd {
		cmd := sandbox.Command("/bin/bash", "-lc", args.Command)
		cmd.Dir = cwd
		return cmd
	}, timeout)
	return playgroundToolCommandMessage(call.Id, commandResult)
}

func (app *InferencePlaygroundApp) inferenceToolWebFetch(call InferenceChatToolCall) playgroundToolResult {
	var args struct {
		URL       string `json:"url"`
		Format    string `json:"format"`
		TimeoutMs int    `json:"timeout_ms"`
	}
	if err := playgroundToolDecodeArgs(call, &args); err != "" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, err), Error: err}
	}
	args.URL = strings.TrimSpace(args.URL)
	if args.URL == "" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, "url must not be empty"), Error: "url must not be empty"}
	}
	args.Format = strings.ToLower(strings.TrimSpace(args.Format))
	if args.Format == "" {
		args.Format = "markdown"
	}
	if args.Format != "markdown" && args.Format != "html" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, "format must be markdown or html"), Error: "format must be markdown or html"}
	}
	args.TimeoutMs = playgroundWebToolTimeoutMs(args.TimeoutMs)

	payload := playgroundToolJSON(args)
	sandbox, sandboxErr := app.playgroundToolSandbox()
	if sandboxErr != "" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, sandboxErr), Error: sandboxErr}
	}
	commandResult := playgroundRunSandboxCommand(func() *shared.TerminalCmd {
		return playgroundToolExecutable(sandbox, "/", "web_fetch", payload)
	}, time.Duration(args.TimeoutMs+1000)*time.Millisecond)
	return playgroundToolCommandMessage(call.Id, commandResult)
}

func (app *InferencePlaygroundApp) inferenceToolRequestInternetAccess(ctx context.Context, threadId string, call InferenceChatToolCall) playgroundToolResult {
	granted := app.requestInternetPermission(ctx, threadId, "")
	if granted {
		return playgroundToolResult{Message: playgroundToolMessage(call.Id, playgroundToolJSON(map[string]any{"status": "granted"}))}
	}
	return playgroundToolResult{
		Message: playgroundToolMessage(call.Id, playgroundToolJSON(map[string]any{"status": "denied"})),
		Error:   "internet access was declined",
	}
}

func playgroundToolDecodeArgs(call InferenceChatToolCall, target any) string {
	if !json.Valid([]byte(call.Function.Arguments)) {
		return "tool arguments must be valid JSON"
	}
	if err := json.Unmarshal([]byte(call.Function.Arguments), target); err != nil {
		return "tool arguments do not match the expected schema"
	}
	return ""
}

func playgroundToolExecutable(sandbox *shared.InferenceSandbox, cwd string, tool string, payload string) *shared.TerminalCmd {
	cmd := sandbox.Command("/mnt/exe/ucloud-inference-tools", tool, payload)
	cmd.Dir = cwd
	return cmd
}

func playgroundRunSandboxCommand(newCommand func() *shared.TerminalCmd, timeout time.Duration) playgroundCommandResult {
	deadline := time.Now().Add(timeout)
	var lastResult playgroundCommandResult
	for attempt := 0; attempt < 6; attempt++ {
		remaining := time.Until(deadline)
		if remaining <= 0 {
			lastResult.TimedOut = true
			return lastResult
		}

		result := playgroundRunSandboxCommandOnce(newCommand(), remaining)
		lastResult = result
		if result.Err == nil || !playgroundToolTransientCommandError(result.Err) || result.TimedOut {
			break
		}
		time.Sleep(500 * time.Millisecond)
	}
	return lastResult
}

func playgroundToolTimeout(timeoutMs int) time.Duration {
	if timeoutMs <= 0 {
		return playgroundToolDefaultTimeout
	}
	timeout := time.Duration(timeoutMs) * time.Millisecond
	if timeout > playgroundToolMaxTimeout {
		return playgroundToolMaxTimeout
	}
	if timeout < time.Second {
		return time.Second
	}
	return timeout
}

func playgroundWebToolTimeoutMs(timeoutMs int) int {
	if timeoutMs <= 0 {
		return playgroundWebFetchDefaultTimeoutMs
	}
	if timeoutMs > playgroundWebFetchMaxTimeoutMs {
		return playgroundWebFetchMaxTimeoutMs
	}
	if timeoutMs < 1000 {
		return 1000
	}
	return timeoutMs
}

func playgroundToolBashValidate(command string, internetEnabled bool) string {
	command = strings.TrimSpace(command)
	if command == "" {
		return "command must not be empty"
	}
	lower := strings.ToLower(command)
	blocked := []string{"shred", ":(){", "mkfs", "dd if=", "chmod -r", "chown -r", "ncat"}
	for _, pattern := range blocked {
		if strings.Contains(lower, pattern) {
			return fmt.Sprintf("command is blocked by policy: %s", strings.TrimSpace(pattern))
		}
	}
	blockedCommands := []string{"ssh", "scp", "apk"}
	for _, cmd := range blockedCommands {
		if regexp.MustCompile(`(^|[;&|()\s])` + regexp.QuoteMeta(cmd) + `($|[;&|()\s])`).MatchString(lower) {
			return fmt.Sprintf("command is blocked by policy: %s", cmd)
		}
	}
	if !internetEnabled {
		blockedNetworkCommands := []string{"curl", "wget", "nc", "apt", "apt-get", "dnf", "yum", "pip", "pip3", "npm", "npx", "yarn", "pnpm", "gem", "cargo", "go"}
		for _, cmd := range blockedNetworkCommands {
			if regexp.MustCompile(`(^|[;&|()\s])` + regexp.QuoteMeta(cmd) + `($|[;&|()\s])`).MatchString(lower) {
				return fmt.Sprintf("network commands are unavailable in this session: %s. Ask the user to enable internet access if network access is required.", cmd)
			}
		}
	}
	interactive := []string{"vim", "vi", "nano", "less", "more", "top", "htop"}
	for _, cmd := range interactive {
		if lower == cmd || strings.HasPrefix(lower, cmd+" ") || strings.Contains(lower, "| "+cmd) {
			return fmt.Sprintf("interactive command is blocked: %s", cmd)
		}
	}
	return ""
}

func playgroundToolError(callId string, message string) InferenceChatMessage {
	return playgroundToolMessage(callId, playgroundToolJSON(map[string]any{"error": message}))
}

func playgroundToolMessage(callId string, output string) InferenceChatMessage {
	return InferenceChatMessage{Role: "tool", ToolCallID: callId, Content: inferenceChatTextContent(output)}
}

func playgroundToolJSON(value any) string {
	data, err := json.Marshal(value)
	if err != nil {
		return `{"error":"failed to encode tool result"}`
	}
	return string(data)
}

func (app *InferencePlaygroundApp) playgroundToolAvailable(name string, allowDeveloper bool) bool {
	if app == nil {
		return false
	}
	app.mu.Lock()
	developer := app.Developer
	currentThreadId := app.CurrentThreadId
	app.mu.Unlock()
	if !allowDeveloper && developer {
		return false
	}

	switch name {
	case "bash", playgroundInternetToolRequestAccess, playgroundInternetToolWebFetch:
	default:
		return false
	}

	state := app.internetAccessState(currentThreadId)
	if state == "" {
		app.mu.Lock()
		if thread, ok := app.currentThread(); ok {
			state = thread.InternetAccess
		}
		app.mu.Unlock()
	}
	if state == playgroundInternetDenied && name == playgroundInternetToolWebFetch {
		return false
	}
	if state != playgroundInternetDenied && name == playgroundInternetToolRequestAccess {
		return false
	}
	return true
}

func playgroundRunSandboxCommandOnce(cmd *shared.TerminalCmd, timeout time.Duration) playgroundCommandResult {
	stdout := &playgroundCappedBuffer{limit: playgroundToolOutputLimit}
	stderr := &playgroundCappedBuffer{limit: playgroundToolOutputLimit / 4}
	cmd.Stdout = stdout
	cmd.Stderr = stderr
	cmd.Start()

	done := make(chan struct{})
	go func() {
		cmd.Wait()
		close(done)
	}()

	timedOut := false
	select {
	case <-done:
	case <-time.After(timeout):
		timedOut = true
		cmd.Kill()
		<-done
	}

	return playgroundCommandResult{Stdout: stdout.String(), Stderr: stderr.String(), TimedOut: timedOut, Err: cmd.Err()}
}

func playgroundToolTransientCommandError(err *util.HttpError) bool {
	if err == nil {
		return false
	}
	message := strings.ToLower(err.Why)
	return strings.Contains(message, "failed to exec in container") ||
		strings.Contains(message, "failed to create exec") ||
		strings.Contains(message, "task ") && strings.Contains(message, " not found") ||
		strings.Contains(message, "container not found") ||
		strings.Contains(message, "pod is not running") ||
		strings.Contains(message, "sandbox did not become ready")
}

func playgroundToolCommandMessage(callId string, result playgroundCommandResult) playgroundToolResult {
	output := map[string]any{"stdout": result.Stdout}
	if result.Stderr != "" {
		output["stderr"] = result.Stderr
	}
	if result.TimedOut {
		output["timed_out"] = true
	}
	if result.Err != nil {
		output["error"] = result.Err.Why
	}
	encoded := playgroundToolJSON(output)
	toolResult := playgroundToolResult{Message: playgroundToolMessage(callId, encoded), Output: encoded}
	if result.TimedOut {
		toolResult.Error = "tool timed out"
	} else if result.Err != nil {
		toolResult.Error = result.Err.Why
	}
	return toolResult
}

var _ io.Writer = (*playgroundCappedBuffer)(nil)
