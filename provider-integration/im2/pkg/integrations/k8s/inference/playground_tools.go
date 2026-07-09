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
		playgroundToolDefinition("glob", "Find files in the selected workspace using a glob pattern.", map[string]any{
			"pattern": map[string]any{"type": "string", "description": "Glob pattern, for example **/*.go."},
			"cwd":     map[string]any{"type": "string", "description": "Optional directory relative to the workspace root.", "default": "."},
			"limit":   map[string]any{"type": "integer", "description": "Maximum number of results to return.", "default": 100},
		}, []string{"pattern"}),
		playgroundToolDefinition("grep", "Search file contents in the selected workspace.", map[string]any{
			"pattern": map[string]any{"type": "string", "description": "Regular expression to search for."},
			"path":    map[string]any{"type": "string", "description": "Optional file or directory relative to the workspace root.", "default": "."},
			"include": map[string]any{"type": "string", "description": "Optional include glob."},
			"exclude": map[string]any{"type": "string", "description": "Optional exclude glob."},
			"limit":   map[string]any{"type": "integer", "description": "Maximum number of matches to return.", "default": 100},
		}, []string{"pattern"}),
		playgroundToolDefinition("read", "Read a text file from the selected workspace.", map[string]any{
			"path":   map[string]any{"type": "string", "description": "File path relative to the workspace root."},
			"offset": map[string]any{"type": "integer", "description": "Optional 1-based line offset.", "default": 1},
			"limit":  map[string]any{"type": "integer", "description": "Optional maximum number of lines to return.", "default": 200},
		}, []string{"path"}),
		playgroundToolDefinition("bash", "Run a non-interactive shell command in the selected workspace sandbox.", map[string]any{
			"command":    map[string]any{"type": "string", "description": "Command to run."},
			"cwd":        map[string]any{"type": "string", "description": "Optional directory relative to the workspace root.", "default": "."},
			"timeout_ms": map[string]any{"type": "integer", "description": "Optional timeout in milliseconds.", "default": 60000},
		}, []string{"command"}),
		playgroundToolDefinition("web_fetch", "Fetch a public http or https URL from inside the sandbox and return capped text or markdown.", map[string]any{
			"url":        map[string]any{"type": "string", "description": "Public http or https URL to fetch."},
			"format":     map[string]any{"type": "string", "description": "Output format: markdown or text.", "default": "markdown", "enum": []string{"markdown", "text"}},
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
	if strings.TrimSpace(app.Workspace.Path) == "" {
		return playgroundToolResult{Message: playgroundToolMessage(call.Id, playgroundToolJSON(map[string]any{"matches": []string{}, "count": 0, "warning": "no workspace folder is attached"})), Output: playgroundToolJSON(map[string]any{"matches": []string{}, "count": 0, "warning": "no workspace folder is attached"})}
	}
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
	cwd, err := playgroundToolContainerPath(args.Cwd)
	if err != "" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, err), Error: err}
	}

	payload := playgroundToolJSON(args)
	commandResult := playgroundRunSandboxCommand(func() *shared.TerminalCmd {
		return playgroundToolPython(sandbox, cwd, playgroundToolGlobScript, payload)
	}, playgroundToolDefaultTimeout)
	return playgroundToolCommandMessage(call.Id, commandResult)
}

func (app *InferencePlaygroundApp) inferenceToolGrep(sandbox *shared.InferenceSandbox, call InferenceChatToolCall) playgroundToolResult {
	if strings.TrimSpace(app.Workspace.Path) == "" {
		return playgroundToolResult{Message: playgroundToolMessage(call.Id, playgroundToolJSON(map[string]any{"matches": []any{}, "count": 0, "warning": "no workspace folder is attached"})), Output: playgroundToolJSON(map[string]any{"matches": []any{}, "count": 0, "warning": "no workspace folder is attached"})}
	}
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
	_, err := playgroundToolContainerPath(args.Path)
	if err != "" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, err), Error: err}
	}

	payload := playgroundToolJSON(args)
	commandResult := playgroundRunSandboxCommand(func() *shared.TerminalCmd {
		return playgroundToolPython(sandbox, playgroundToolWorkspaceRoot, playgroundToolGrepScript, payload)
	}, playgroundToolDefaultTimeout)
	return playgroundToolCommandMessage(call.Id, commandResult)
}

func (app *InferencePlaygroundApp) inferenceToolRead(sandbox *shared.InferenceSandbox, call InferenceChatToolCall) playgroundToolResult {
	if strings.TrimSpace(app.Workspace.Path) == "" {
		msg := "no workspace folder is attached"
		return playgroundToolResult{Message: playgroundToolError(call.Id, msg), Error: msg}
	}
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
	_, err := playgroundToolContainerPath(args.Path)
	if err != "" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, err), Error: err}
	}

	payload := playgroundToolJSON(args)
	commandResult := playgroundRunSandboxCommand(func() *shared.TerminalCmd {
		return playgroundToolPython(sandbox, playgroundToolWorkspaceRoot, playgroundToolReadScript, payload)
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
	if strings.TrimSpace(app.Workspace.Path) == "" && (args.Cwd == "" || args.Cwd == ".") {
		cwd = "/"
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
	if args.Format != "markdown" && args.Format != "text" {
		return playgroundToolResult{Message: playgroundToolError(call.Id, "format must be markdown or text"), Error: "format must be markdown or text"}
	}
	args.TimeoutMs = playgroundWebToolTimeoutMs(args.TimeoutMs)

	payload := playgroundToolJSON(args)
	commandResult := playgroundRunSandboxCommand(func() *shared.TerminalCmd {
		return playgroundToolPython(sandbox, "/", playgroundToolWebFetchScript, payload)
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
		return playgroundToolPython(sandbox, "/", playgroundToolWikipediaSearchScript, payload)
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

func playgroundToolPython(sandbox *shared.InferenceSandbox, cwd string, script string, payload string) *shared.TerminalCmd {
	cmd := sandbox.Command("/usr/bin/python3", "-c", script, payload)
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
	blocked := []string{"rm -rf", "shred", ":(){", "mkfs", "dd if=", "chmod -r", "chown -r", "ncat", "apt-get", "pip install", "npm install"}
	for _, pattern := range blocked {
		if strings.Contains(lower, pattern) {
			return fmt.Sprintf("command is blocked by policy: %s", strings.TrimSpace(pattern))
		}
	}
	blockedCommands := []string{"curl", "wget", "nc", "ssh", "scp", "apt", "dnf", "yum", "apk"}
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

const playgroundToolGlobScript = `
import json, pathlib, sys
args = json.loads(sys.argv[1])
pattern = args.get("Pattern") or args.get("pattern") or ""
limit = int(args.get("Limit") or args.get("limit") or 100)
root = pathlib.Path("/mnt/workspace").resolve()
cwd = pathlib.Path.cwd().resolve()
results = []
for item in cwd.glob(pattern):
    try:
        resolved = item.resolve()
        resolved.relative_to(root)
    except Exception:
        continue
    results.append(str(resolved.relative_to(root)))
results = sorted(dict.fromkeys(results))[:limit]
print(json.dumps({"matches": results, "count": len(results)}))
`

const playgroundToolGrepScript = `
import fnmatch, json, os, pathlib, re, sys
args = json.loads(sys.argv[1])
pattern = args.get("Pattern") or args.get("pattern") or ""
target = args.get("Path") or args.get("path") or "."
include = args.get("Include") or args.get("include") or ""
exclude = args.get("Exclude") or args.get("exclude") or ""
limit = int(args.get("Limit") or args.get("limit") or 100)
root = pathlib.Path("/mnt/workspace").resolve()
start = (root / target).resolve()
start.relative_to(root)
rx = re.compile(pattern)
matches = []
paths = [start]
if start.is_dir():
    paths = [pathlib.Path(dp) / f for dp, _, files in os.walk(start) for f in files]
for path in paths:
    if len(matches) >= limit:
        break
    try:
        resolved = path.resolve(); rel = str(resolved.relative_to(root))
    except Exception:
        continue
    if include and not fnmatch.fnmatch(rel, include):
        continue
    if exclude and fnmatch.fnmatch(rel, exclude):
        continue
    try:
        data = resolved.read_bytes()
        if b"\0" in data[:4096]:
            continue
        text = data.decode("utf-8", "replace")
    except Exception:
        continue
    for line_no, line in enumerate(text.splitlines(), 1):
        if rx.search(line):
            matches.append({"path": rel, "line": line_no, "text": line[:1000]})
            if len(matches) >= limit:
                break
print(json.dumps({"matches": matches, "count": len(matches)}))
`

const playgroundToolReadScript = `
import json, pathlib, sys
args = json.loads(sys.argv[1])
rel_path = args.get("Path") or args.get("path") or ""
offset = int(args.get("Offset") or args.get("offset") or 1)
limit = int(args.get("Limit") or args.get("limit") or 200)
root = pathlib.Path("/mnt/workspace").resolve()
path = (root / rel_path).resolve()
path.relative_to(root)
if path.is_dir():
    raise SystemExit("path is a directory")
data = path.read_bytes()
if b"\0" in data[:4096]:
    raise SystemExit("file appears to be binary")
lines = data.decode("utf-8", "replace").splitlines()
start = max(1, offset)
end = min(len(lines), start + limit - 1)
body = "\n".join(f"{idx}: {lines[idx-1]}" for idx in range(start, end + 1))
print(json.dumps({"path": str(path.relative_to(root)), "offset": start, "lines": end - start + 1 if end >= start else 0, "content": body, "truncated": end < len(lines)}))
`

const playgroundToolWebFetchScript = `
import html.parser, ipaddress, json, re, socket, ssl, sys, urllib.error, urllib.parse, urllib.request
args = json.loads(sys.argv[1])
url = (args.get("URL") or args.get("url") or "").strip()
fmt = (args.get("Format") or args.get("format") or "markdown").strip().lower()
timeout = max(1, min(30, int(args.get("TimeoutMs") or args.get("timeout_ms") or 15000) / 1000))
limit = 65536
parsed = urllib.parse.urlparse(url)
if parsed.scheme not in ("http", "https") or not parsed.netloc:
    raise SystemExit("only http and https URLs are supported")
host = parsed.hostname or ""
try:
    for info in socket.getaddrinfo(host, parsed.port or (443 if parsed.scheme == "https" else 80), type=socket.SOCK_STREAM):
        ip = ipaddress.ip_address(info[4][0])
        if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_multicast or ip.is_reserved or ip.is_unspecified:
            raise SystemExit("resolved address is blocked by policy")
except SystemExit:
    raise
except Exception as exc:
    raise SystemExit("failed to resolve host: " + str(exc))

class Extractor(html.parser.HTMLParser):
    def __init__(self):
        super().__init__()
        self.skip = 0
        self.parts = []
        self.links = []
    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style", "noscript"):
            self.skip += 1
        if self.skip:
            return
        if tag in ("p", "div", "section", "article", "br", "li", "tr", "h1", "h2", "h3"):
            self.parts.append("\n")
        if tag == "a":
            href = dict(attrs).get("href", "").strip()
            if href:
                self.links.append(href)
    def handle_endtag(self, tag):
        if tag in ("script", "style", "noscript") and self.skip:
            self.skip -= 1
        if not self.skip and tag in ("p", "div", "section", "article", "li", "tr", "h1", "h2", "h3"):
            self.parts.append("\n")
    def handle_data(self, data):
        if not self.skip:
            text = " ".join(data.split())
            if text:
                self.parts.append(text + " ")

req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"})
try:
    with urllib.request.urlopen(req, timeout=timeout, context=ssl.create_default_context()) as resp:
        status = getattr(resp, "status", 0)
        content_type = resp.headers.get("content-type", "")
        raw = resp.read(limit + 1)
except urllib.error.HTTPError as exc:
    raise SystemExit("fetch failed: HTTP " + str(exc.code))
except Exception as exc:
    raise SystemExit("fetch failed: " + str(exc))
truncated = len(raw) > limit
raw = raw[:limit]
charset = "utf-8"
m = re.search(r"charset=([^;]+)", content_type, re.I)
if m:
    charset = m.group(1).strip()
text = raw.decode(charset, "replace")
if "html" in content_type.lower() or re.search(r"<\s*html|<\s*body", text[:1000], re.I):
    parser = Extractor(); parser.feed(text)
    text = "\n".join(line.strip() for line in "".join(parser.parts).splitlines() if line.strip())
if fmt == "text":
    output = text[:limit]
else:
    output = text[:limit]
print(json.dumps({"url": url, "status": status, "content_type": content_type, "format": fmt, "content": output, "truncated": truncated or len(text) > limit}))
`

const playgroundToolWikipediaSearchScript = `
import html, json, sys, urllib.parse, urllib.request
args = json.loads(sys.argv[1])
query = (args.get("Query") or args.get("query") or "").strip()
limit = max(1, min(10, int(args.get("Limit") or args.get("limit") or 5)))
params = urllib.parse.urlencode({"action": "query", "list": "search", "srsearch": query, "srlimit": limit, "format": "json", "origin": "*"})
url = "https://en.wikipedia.org/w/api.php?" + params
req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"})
try:
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read(65536).decode("utf-8", "replace"))
except Exception as exc:
    raise SystemExit("wikipedia search failed: " + str(exc))
results = []
for item in data.get("query", {}).get("search", [])[:limit]:
    title = item.get("title", "")
    snippet = html.unescape(item.get("snippet", ""))
    snippet = " ".join(snippet.replace("<span class=\"searchmatch\">", "").replace("</span>", "").split())
    results.append({"title": title, "snippet": snippet[:500], "url": "https://en.wikipedia.org/wiki/" + urllib.parse.quote(title.replace(" ", "_"))})
print(json.dumps({"query": query, "results": results, "count": len(results)}))
`

var _ io.Writer = (*playgroundCappedBuffer)(nil)
