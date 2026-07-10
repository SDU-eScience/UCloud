package inference

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"ucloud.dk/pkg/integrations/k8s/shared"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

const playgroundToolMaxIterations = 20
const playgroundToolWorkspaceRoot = "/mnt/workspace"
const playgroundToolOutputLimit = 64 * 1024
const playgroundToolDefaultTimeout = 60 * time.Second
const playgroundToolMaxTimeout = 2 * time.Minute
const playgroundWebFetchDefaultTimeoutMs = 15000
const playgroundWebFetchMaxTimeoutMs = 30000

var playgroundWorkspaceToolNames = []string{"glob", "grep", "read", "bash", "web_fetch", "wikipedia_search"}

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

func (app *InferencePlaygroundApp) playgroundToolDefinitions() []InferenceChatTool {
	if app == nil || app.Developer || !app.playgroundWorkspaceReady() {
		return nil
	}

	return []InferenceChatTool{
		playgroundToolDefinition("glob", "Find files using a glob pattern. It defaults to the selected workspace; an absolute cwd may be used outside it.", map[string]any{
			"pattern": map[string]any{"type": "string", "description": "Glob pattern, for example **/*.go."},
			"cwd":     map[string]any{"type": "string", "description": "Optional directory. Defaults to the workspace; absolute paths may be used outside it.", "default": "."},
			"limit":   map[string]any{"type": "integer", "description": "Maximum number of results to return.", "default": 100},
		}, []string{"pattern"}),
		playgroundToolDefinition("grep", "Search file contents. It defaults to the selected workspace; an absolute path may be used outside it.", map[string]any{
			"pattern": map[string]any{"type": "string", "description": "Regular expression to search for."},
			"path":    map[string]any{"type": "string", "description": "Optional file or directory. Defaults to the workspace; absolute paths may be used outside it.", "default": "."},
			"include": map[string]any{"type": "string", "description": "Optional include glob."},
			"exclude": map[string]any{"type": "string", "description": "Optional exclude glob."},
			"limit":   map[string]any{"type": "integer", "description": "Maximum number of matches to return.", "default": 100},
		}, []string{"pattern"}),
		playgroundToolDefinition("read", "Read a text file. Relative paths use the selected workspace; absolute paths may be used outside it.", map[string]any{
			"path":   map[string]any{"type": "string", "description": "File path relative to the workspace root, or an absolute path outside it."},
			"offset": map[string]any{"type": "integer", "description": "Optional 1-based line offset.", "default": 1},
			"limit":  map[string]any{"type": "integer", "description": "Optional maximum number of lines to return.", "default": 200},
		}, []string{"path"}),
		playgroundToolDefinition("bash", "Run a non-interactive shell command in the selected workspace sandbox.", map[string]any{
			"command":    map[string]any{"type": "string", "description": "Command to run."},
			"cwd":        map[string]any{"type": "string", "description": "Optional directory relative to the workspace root.", "default": "."},
			"timeout_ms": map[string]any{"type": "integer", "description": "Optional timeout in milliseconds.", "default": 60000},
		}, []string{"command"}),
		playgroundToolDefinition("web_fetch", "Fetch a public http or https URL from inside the sandbox and return capped markdown or HTML.", map[string]any{
			"url":        map[string]any{"type": "string", "description": "Public http or https URL to fetch."},
			"format":     map[string]any{"type": "string", "description": "Output format: markdown or html.", "default": "markdown", "enum": []string{"markdown", "html"}},
			"timeout_ms": map[string]any{"type": "integer", "description": "Optional timeout in milliseconds, capped server-side.", "default": playgroundWebFetchDefaultTimeoutMs},
		}, []string{"url"}),
		playgroundToolDefinition("wikipedia_search", "Search Wikipedia and return compact result metadata.", map[string]any{
			"query": map[string]any{"type": "string", "description": "Search query."},
			"limit": map[string]any{"type": "integer", "description": "Maximum number of results to return.", "default": 5},
		}, []string{"query"}),
	}
}

func playgroundToolDefinition(name string, description string, properties map[string]any, required []string) InferenceChatTool {
	return InferenceChatTool{Type: "function", Function: InferenceChatToolFunction{Name: name, Description: description, Strict: util.OptValue(true), Parameters: map[string]any{"type": "object", "properties": properties, "required": required, "additionalProperties": false}}}
}

func (app *InferencePlaygroundApp) playgroundWorkspaceReady() bool {
	return strings.TrimSpace(app.Workspace.SandboxJobId) != "" && strings.TrimSpace(app.Workspace.Error) == ""
}

func (app *InferencePlaygroundApp) playgroundToolAvailable(name string, allowDeveloper bool) bool {
	if app == nil || (!allowDeveloper && app.Developer) || !app.playgroundWorkspaceReady() {
		return false
	}
	for _, candidate := range playgroundWorkspaceToolNames {
		if name == candidate {
			return true
		}
	}
	return false
}

func (app *InferencePlaygroundApp) playgroundToolDispatch(call InferenceChatToolCall) playgroundToolResult {
	return app.playgroundToolDispatchWithMode(call, false)
}

func (app *InferencePlaygroundApp) playgroundToolDispatchForDeveloper(call InferenceChatToolCall) playgroundToolResult {
	return app.playgroundToolDispatchWithMode(call, true)
}

func (app *InferencePlaygroundApp) playgroundToolDispatchWithMode(call InferenceChatToolCall, allowDeveloper bool) playgroundToolResult {
	name := strings.TrimSpace(call.Function.Name)
	if call.Id == "" {
		call.Id = name
	}
	if !app.playgroundToolAvailable(name, allowDeveloper) {
		return playgroundToolResult{Message: playgroundToolError(call.Id, fmt.Sprintf("tool %q is not available", name)), Error: "tool is not available"}
	}

	sandbox, sandboxErr := app.playgroundToolSandbox()
	if sandboxErr != "" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, sandboxErr), Error: sandboxErr}
	}
	log.Info("Running in %v %v", sandbox.Folders, sandbox.JobId)
	var result playgroundToolResult
	switch name {
	case "glob":
		result = app.inferenceToolGlob(sandbox, call)
	case "grep":
		result = app.inferenceToolGrep(sandbox, call)
	case "read":
		result = app.inferenceToolRead(sandbox, call)
	case "bash":
		result = app.inferenceToolBash(sandbox, call)
	case "web_fetch":
		result = app.inferenceToolWebFetch(sandbox, call)
	case "wikipedia_search":
		result = app.inferenceToolWikipediaSearch(sandbox, call)
	default:
		result = playgroundToolResult{Message: playgroundToolError(call.Id, fmt.Sprintf("tool %q is not supported", name)), Error: "tool is not supported"}
	}
	return result
}

func (app *InferencePlaygroundApp) playgroundToolSandbox() (*shared.InferenceSandbox, string) {
	folders := []string{}
	if path := strings.TrimSpace(app.Workspace.Path); path != "" {
		folders = append(folders, path)
	}
	sandbox, err := shared.InferenceSandboxSetFolders(app.Owner, util.OptNone[string](), folders)
	if err != nil {
		return nil, err.Why
	}
	if strings.TrimSpace(app.Workspace.Path) != "" && len(sandbox.Folders) == 0 {
		return nil, "The selected folder could not be mounted."
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
	case "glob":
		arguments = playgroundToolJSON(map[string]any{"pattern": rest})
	case "grep":
		pattern, path, _ := strings.Cut(rest, " ")
		args := map[string]any{"pattern": strings.TrimSpace(pattern)}
		if strings.TrimSpace(path) != "" {
			args["path"] = strings.TrimSpace(path)
		}
		arguments = playgroundToolJSON(args)
	case "read":
		arguments = playgroundToolJSON(map[string]any{"path": rest})
	case "bash":
		arguments = playgroundToolJSON(map[string]any{"command": rest})
	case "web_fetch":
		arguments = playgroundToolJSON(map[string]any{"url": rest})
	case "wikipedia_search":
		arguments = playgroundToolJSON(map[string]any{"query": rest})
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

func (app *InferencePlaygroundApp) inferenceToolGlob(sandbox *shared.InferenceSandbox, call InferenceChatToolCall) playgroundToolResult {
	var args struct {
		Pattern string `json:"pattern"`
		Cwd     string `json:"cwd"`
		Limit   int    `json:"limit"`
	}
	if err := playgroundToolDecodeArgs(call, &args); err != "" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, err), Error: err}
	}
	if args.Cwd == "" {
		args.Cwd = "."
	}
	if args.Limit <= 0 || args.Limit > 1000 {
		args.Limit = 100
	}
	payload := playgroundToolJSON(args)
	commandResult := playgroundRunSandboxCommand(func() *shared.TerminalCmd {
		return playgroundToolExecutable(sandbox, app.playgroundToolWorkingDirectory(), "glob", payload)
	}, playgroundToolDefaultTimeout)
	return playgroundToolCommandMessage(call.Id, commandResult)
}

func (app *InferencePlaygroundApp) inferenceToolGrep(sandbox *shared.InferenceSandbox, call InferenceChatToolCall) playgroundToolResult {
	var args struct {
		Pattern string `json:"pattern"`
		Path    string `json:"path"`
		Include string `json:"include"`
		Exclude string `json:"exclude"`
		Limit   int    `json:"limit"`
	}
	if err := playgroundToolDecodeArgs(call, &args); err != "" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, err), Error: err}
	}
	if args.Path == "" {
		args.Path = "."
	}
	if args.Limit <= 0 || args.Limit > 1000 {
		args.Limit = 100
	}
	payload := playgroundToolJSON(args)
	commandResult := playgroundRunSandboxCommand(func() *shared.TerminalCmd {
		return playgroundToolExecutable(sandbox, app.playgroundToolWorkingDirectory(), "grep", payload)
	}, playgroundToolDefaultTimeout)
	return playgroundToolCommandMessage(call.Id, commandResult)
}

func (app *InferencePlaygroundApp) inferenceToolRead(sandbox *shared.InferenceSandbox, call InferenceChatToolCall) playgroundToolResult {
	var args struct {
		Path   string `json:"path"`
		Offset int    `json:"offset"`
		Limit  int    `json:"limit"`
	}
	if err := playgroundToolDecodeArgs(call, &args); err != "" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, err), Error: err}
	}
	if args.Offset <= 0 {
		args.Offset = 1
	}
	if args.Limit <= 0 || args.Limit > 1000 {
		args.Limit = 200
	}
	payload := playgroundToolJSON(args)
	commandResult := playgroundRunSandboxCommand(func() *shared.TerminalCmd {
		return playgroundToolExecutable(sandbox, app.playgroundToolWorkingDirectory(), "read", payload)
	}, playgroundToolDefaultTimeout)
	return playgroundToolCommandMessage(call.Id, commandResult)
}

func (app *InferencePlaygroundApp) inferenceToolBash(sandbox *shared.InferenceSandbox, call InferenceChatToolCall) playgroundToolResult {
	var args struct {
		Command   string `json:"command"`
		Cwd       string `json:"cwd"`
		TimeoutMs int    `json:"timeout_ms"`
	}
	if err := playgroundToolDecodeArgs(call, &args); err != "" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, err), Error: err}
	}
	if args.Cwd == "" {
		args.Cwd = "."
	}
	if err := playgroundToolBashValidate(args.Command); err != "" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, err), Error: err}
	}
	cwd, err := playgroundToolContainerPath(args.Cwd)
	if err != "" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, err), Error: err}
	}
	if strings.TrimSpace(app.Workspace.Path) == "" {
		cwd = "/"
		if args.Cwd != "." {
			cwd = filepath.Join(cwd, args.Cwd)
		}
	}
	timeout := playgroundToolTimeout(args.TimeoutMs)
	commandResult := playgroundRunSandboxCommand(func() *shared.TerminalCmd {
		cmd := sandbox.Command("/bin/bash", "-lc", args.Command)
		cmd.Dir = cwd
		return cmd
	}, timeout)
	return playgroundToolCommandMessage(call.Id, commandResult)
}

func (app *InferencePlaygroundApp) inferenceToolWebFetch(sandbox *shared.InferenceSandbox, call InferenceChatToolCall) playgroundToolResult {
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
	commandResult := playgroundRunSandboxCommand(func() *shared.TerminalCmd {
		return playgroundToolExecutable(sandbox, "/", "web_fetch", payload)
	}, time.Duration(args.TimeoutMs+1000)*time.Millisecond)
	return playgroundToolCommandMessage(call.Id, commandResult)
}

func (app *InferencePlaygroundApp) inferenceToolWikipediaSearch(sandbox *shared.InferenceSandbox, call InferenceChatToolCall) playgroundToolResult {
	var args struct {
		Query string `json:"query"`
		Limit int    `json:"limit"`
	}
	if err := playgroundToolDecodeArgs(call, &args); err != "" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, err), Error: err}
	}
	args.Query = strings.TrimSpace(args.Query)
	if args.Query == "" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, "query must not be empty"), Error: "query must not be empty"}
	}
	if args.Limit <= 0 || args.Limit > 10 {
		args.Limit = 5
	}

	payload := playgroundToolJSON(args)
	commandResult := playgroundRunSandboxCommand(func() *shared.TerminalCmd {
		return playgroundToolExecutable(sandbox, "/", "wikipedia_search", payload)
	}, 20*time.Second)
	return playgroundToolCommandMessage(call.Id, commandResult)
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

func playgroundToolContainerPath(path string) (string, string) {
	path = strings.TrimSpace(path)
	if path == "" || path == "." {
		return playgroundToolWorkspaceRoot, ""
	}
	if filepath.IsAbs(path) {
		return "", "absolute paths are not allowed"
	}
	clean := filepath.Clean(path)
	if clean == "." {
		return playgroundToolWorkspaceRoot, ""
	}
	if clean == ".." || strings.HasPrefix(clean, "../") {
		return "", "paths must stay inside the workspace"
	}
	return filepath.Join(playgroundToolWorkspaceRoot, clean), ""
}

func (app *InferencePlaygroundApp) playgroundToolWorkingDirectory() string {
	if strings.TrimSpace(app.Workspace.Path) == "" {
		return "/"
	}
	return playgroundToolWorkspaceRoot
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
			return result
		}

		sleep := time.Duration(attempt+1) * 2 * time.Second
		if time.Until(deadline) <= sleep {
			return result
		}
		time.Sleep(sleep)
	}
	return lastResult
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

func playgroundToolBashValidate(command string) string {
	command = strings.TrimSpace(command)
	if command == "" {
		return "command must not be empty"
	}
	lower := strings.ToLower(command)
	blocked := []string{"shred", ":(){", "mkfs", "dd if=", "chmod -r", "chown -r", "ncat", "apt-get"}
	for _, pattern := range blocked {
		if strings.Contains(lower, pattern) {
			return fmt.Sprintf("command is blocked by policy: %s", strings.TrimSpace(pattern))
		}
	}
	blockedCommands := []string{"nc", "ssh", "scp", "apt", "dnf", "yum", "apk"}
	for _, cmd := range blockedCommands {
		if regexp.MustCompile(`(^|[;&|()\s])` + regexp.QuoteMeta(cmd) + `($|[;&|()\s])`).MatchString(lower) {
			return fmt.Sprintf("command is blocked by policy: %s", cmd)
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

var _ io.Writer = (*playgroundCappedBuffer)(nil)
