package inference

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"html"
	"mime"
	"net/url"
	"path/filepath"
	"slices"
	"sort"
	"strings"
	"sync"
	"time"

	"ucloud.dk/pkg/integrations/k8s/shared"
	apm "ucloud.dk/shared/pkg/accounting"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

// App state and initialization
// =====================================================================================================================

const (
	playgroundModeChat           = "Chat"
	playgroundGlobalSystemPrompt = "You are a helpful assistant."
)

type InferencePlaygroundApp struct {
	mu             sync.Mutex   `ucx:"-"`
	session        *ucx.Session `ucx:"-"`
	flusherStarted bool         `ucx:"-"`

	Owner     orcapi.ResourceOwner `ucx:"-"`
	SessionId string               `ucx:"-"`

	Route           string
	Developer       bool
	DevelopmentMode bool

	Models             []InferenceModel
	Threads            []playgroundChatThread
	LoadingThreadIds   []string
	DeletedThreadIds   []string `ucx:"-"`
	DeletedThreadPaths []string `ucx:"-"`
	CurrentThreadId    string

	Chat      InferencePlaygroundAppChat
	Workspace playgroundWorkspaceState
}

type InferencePlaygroundAppChat struct {
	Loading                bool
	Usage                  InferencePlaygroundTokenUsageState
	AppliedDefaultsModelId string `ucx:"-"`

	ModelId             string
	SystemPrompt        string
	Prompt              string
	Streaming           bool
	Temperature         float64
	TopP                float64
	PresencePenalty     float64
	FrequencyPenalty    float64
	MaxCompletionTokens int64
	Logprobs            bool
	TopLogprobs         int64
	Messages            []playgroundChatMessage
	Curl                string
}

type playgroundWorkspaceState struct {
	Path         string
	AppliedPath  string `ucx:"-"`
	SandboxJobId string
	ETag         string `ucx:"-"`
	Loading      bool
	Error        string
	Warnings     []string
}

func InferencePlayground(owner orcapi.ResourceOwner, sessionId string) *InferencePlaygroundApp {
	if !shared.ServiceConfig.Compute.Inference.Enabled {
		return nil
	}

	if owner.CreatedBy == "" {
		return nil
	}

	return &InferencePlaygroundApp{
		Owner:           owner,
		SessionId:       sessionId,
		Developer:       false,
		DevelopmentMode: util.DevelopmentModeEnabled(),
		Chat: InferencePlaygroundAppChat{
			Streaming:           true,
			Temperature:         0.8,
			TopP:                0.1,
			PresencePenalty:     0,
			FrequencyPenalty:    0,
			MaxCompletionTokens: 65536,
			TopLogprobs:         0,
			SystemPrompt:        playgroundGlobalSystemPrompt,
		},
	}
}

type InferencePlaygroundTokenUsageState struct {
	Session   InferencePlaygroundTokenUsage
	LastQuery InferencePlaygroundTokenUsage
}

type InferencePlaygroundTokenUsage struct {
	Input       int64
	CachedInput int64
	Output      int64
	Reported    int64
}

type playgroundChatMessage struct {
	Role           string
	Content        string
	Synthetic      bool
	Reasoning      string
	ReasoningTitle string
	Parts          []playgroundChatMessagePart
	GeneratedAt    int64
	ModelName      string
	StartedAt      int64
	FirstTokenAt   int64
	FinishedAt     int64
	OutputTokens   int64
	MessageIndex   int64
}

type playgroundChatMessagePart struct {
	Kind     string
	Text     string
	Summary  string
	Body     string
	Open     bool
	FileName string
	Url      string
	ToolName string
	Status   string
}

type playgroundChatAttachment struct {
	Kind         string
	AttachmentId string
	FileName     string
	Url          string
	Text         string
}

type playgroundChatThread struct {
	Id                     string
	Title                  string
	CreatedAt              int64
	UpdatedAt              int64
	Usage                  InferencePlaygroundTokenUsage
	WorkspacePath          string
	LastQuery              InferencePlaygroundTokenUsage `ucx:"-"`
	Messages               []playgroundChatMessage       `ucx:"-"`
	Dirty                  bool                          `ucx:"-"`
	Deleted                bool                          `ucx:"-"`
	StoragePath            string                        `ucx:"-"`
	TitleGenerated         bool                          `ucx:"-"`
	TitleGenerationStarted bool                          `ucx:"-"`
}

type playgroundAttachmentCreateRequest struct {
	Filename string
}

type playgroundAttachmentCreateResponse struct {
	Id string
}

type playgroundAttachmentAppendRequest struct {
	Id   string
	Data []byte
}

type playgroundAttachmentDeleteRequest struct {
	Id string
}

type playgroundAttachmentConvertRequest struct {
	Id string
}

type playgroundAttachmentConvertResponse struct {
	Id string
}

var playgroundAttachmentCreateRpc = ucx.Rpc[playgroundAttachmentCreateRequest, playgroundAttachmentCreateResponse]{CallName: "inferenceAttachmentCreate"}
var playgroundAttachmentAppendRpc = ucx.Rpc[playgroundAttachmentAppendRequest, util.Empty]{CallName: "inferenceAttachmentAppend"}
var playgroundAttachmentDeleteRpc = ucx.Rpc[playgroundAttachmentDeleteRequest, util.Empty]{CallName: "inferenceAttachmentDelete"}
var playgroundAttachmentConvertRpc = ucx.Rpc[playgroundAttachmentConvertRequest, playgroundAttachmentConvertResponse]{CallName: "inferenceAttachmentConvertToMarkdown"}

// App (global) event handlers and init
// =====================================================================================================================

func (app *InferencePlaygroundApp) Mutex() *sync.Mutex     { return &app.mu }
func (app *InferencePlaygroundApp) Session() **ucx.Session { return &app.session }

func (app *InferencePlaygroundApp) OnInit() {
	app.refreshModels()
	app.loadThreads()
	app.registerAttachmentRpcs()
	app.Chat.ModelId = app.firstModelFor(InferenceTextGeneration)
	app.applyChatModelDefaults()
	app.startThreadFlusher()

	app.Chat.Curl = app.buildChatCurl()
}

func (app *InferencePlaygroundApp) registerAttachmentRpcs() {
	playgroundAttachmentCreateRpc.Handler(app.session, func(ctx context.Context, request playgroundAttachmentCreateRequest) (playgroundAttachmentCreateResponse, error) {
		attachment, err := AttachmentCreate(app.Owner.CreatedBy, app.Owner.Project, request.Filename)
		if err != nil {
			return playgroundAttachmentCreateResponse{}, err
		}
		return playgroundAttachmentCreateResponse{Id: attachment.Id}, nil
	})

	playgroundAttachmentAppendRpc.Handler(app.session, func(ctx context.Context, request playgroundAttachmentAppendRequest) (util.Empty, error) {
		if err := AttachmentAppend(request.Id, bytes.NewReader(request.Data)); err != nil {
			return util.Empty{}, err
		}
		return util.Empty{}, nil
	})

	playgroundAttachmentDeleteRpc.Handler(app.session, func(ctx context.Context, request playgroundAttachmentDeleteRequest) (util.Empty, error) {
		if err := AttachmentDelete(request.Id); err != nil {
			return util.Empty{}, err
		}
		return util.Empty{}, nil
	})

	playgroundAttachmentConvertRpc.Handler(app.session, func(ctx context.Context, request playgroundAttachmentConvertRequest) (playgroundAttachmentConvertResponse, error) {
		attachment, err := AttachmentConvertToMarkdown(ctx, request.Id)
		if err != nil {
			return playgroundAttachmentConvertResponse{}, err
		}
		return playgroundAttachmentConvertResponse{Id: attachment.Id}, nil
	})
}

func (app *InferencePlaygroundApp) OnMessage(message ucx.Frame) {
	if message.Opcode == ucx.OpUiEvent {
		switch message.UiEvent.NodeId {
		case "newThread":
			app.createThread()
			ucx.AppUpdateUi(app)
		case "openThread":
			app.openThread(message.UiEvent.Value.String)
			ucx.AppUpdateModel(app)
		case "renameThreadFromMenu":
			id := message.UiEvent.Value.Object["id"].String
			title := message.UiEvent.Value.Object["title"].String
			app.renameThread(id, title)
			ucx.AppUpdateModel(app)
		case "deleteThread":
			app.deleteThread(message.UiEvent.Value.String)
			ucx.AppUpdateUi(app)
		case "chatComposer":
			prompt, attachments := playgroundChatComposerEvent(message.UiEvent.Value)
			app.Chat.Prompt = prompt
			if !app.currentThreadLoading() {
				app.runChat(attachments)
				ucx.AppUpdateModel(app)
			}
		case "regenerateChat":
			if !app.currentThreadLoading() {
				modelId := message.UiEvent.Value.String
				messageIndex := int64(-1)
				if message.UiEvent.Value.Kind == ucx.ValueObject {
					modelId = message.UiEvent.Value.Object["modelId"].String
					messageIndex = message.UiEvent.Value.Object["messageIndex"].S64
				}
				app.regenerateChat(modelId, messageIndex)
				ucx.AppUpdateModel(app)
			}
		}
		return
	}
	if message.Opcode == ucx.OpModelInput {
		if message.ModelInput.Path == "currentThreadId" {
			if !app.Developer {
				threadId := strings.TrimSpace(app.CurrentThreadId)
				if threadId != "" {
					app.openThread(threadId)
				}
				if app.CurrentThreadId == "" || strings.TrimSpace(app.CurrentThreadId) == threadId {
					if _, ok := app.currentThread(); !ok {
						app.createThread()
					}
				}
			}
			app.Chat.Curl = app.buildChatCurl()
			return
		}
		if message.ModelInput.Path == "developer" {
			if !app.Developer {
				app.ensureCurrentThread()
			}
			ucx.AppUpdateUi(app)
			return
		}
		if message.ModelInput.Path == "workspace.path" {
			if app.currentThreadLoading() {
				app.Workspace.Path = app.Workspace.AppliedPath
				app.Workspace.Error = "Workspace cannot be changed while a response is running."
				ucx.AppUpdateModel(app)
				return
			}
			app.configureWorkspace()
			ucx.AppUpdateModel(app)
			return
		}
		if app.Chat.ModelId != app.Chat.AppliedDefaultsModelId {
			app.applyChatModelDefaults()
			ucx.AppUpdateModel(app)
		}
		app.Chat.Curl = app.buildChatCurl()
	}
}

func (app *InferencePlaygroundApp) runDeveloperSlashCommand(prompt string) bool {
	if !util.DevelopmentModeEnabled() {
		return false
	}
	call, ok, parseErr := playgroundDeveloperSlashToolCall(prompt)
	if !ok {
		return false
	}

	now := time.Now().UnixMilli()
	app.Chat.Loading = true
	app.Chat.Prompt = ""
	app.materializeCurrentThread()
	app.Chat.Messages = append(app.Chat.Messages,
		playgroundChatMessage{Role: "user", Content: prompt, Parts: playgroundChatMessageParts(prompt, "", "", false), GeneratedAt: now},
		playgroundChatMessage{Role: "assistant", Content: "", Parts: playgroundChatMessageParts("", "", "", false), GeneratedAt: now, ModelName: app.Chat.ModelId, StartedAt: now},
	)
	assistantIndex := len(app.Chat.Messages) - 1
	threadId := app.CurrentThreadId
	modelId := app.Chat.ModelId
	app.markCurrentThreadDirty()
	app.setThreadLoading(threadId, true)

	go func() {
		startedAt := time.Now().UnixMilli()
		if parseErr != "" {
			app.appendThreadAssistantPart(threadId, assistantIndex, playgroundChatMessagePart{Kind: "tool", Summary: call.Function.Name, ToolName: call.Function.Name, Status: "error", Body: "Error:\n" + parseErr}, modelId, startedAt)
		} else {
			app.appendThreadAssistantPart(threadId, assistantIndex, playgroundToolChatPart(call, "running", ""), modelId, startedAt)
			result := app.playgroundToolDispatchForDeveloper(call)
			app.appendThreadAssistantPart(threadId, assistantIndex, playgroundToolChatPart(call, playgroundToolStatus(result), playgroundToolPartBody(call, result)), modelId, startedAt)
		}

		app.mu.Lock()
		defer app.mu.Unlock()
		finishedAt := time.Now().UnixMilli()
		app.updateThreadAssistant(threadId, assistantIndex, "Developer tool command completed.", "", "", false, modelId, startedAt, startedAt, finishedAt, 0)
		app.Chat.Loading = false
		app.setThreadLoading(threadId, false)
		app.Chat.Curl = app.buildChatCurl()
		ucx.AppUpdateUi(app)
	}()

	return true
}

func (app *InferencePlaygroundApp) configureWorkspace() {
	workspacePath := strings.TrimSpace(app.Workspace.Path)
	app.Workspace.Path = workspacePath
	app.Workspace.Error = ""
	app.Workspace.Warnings = nil

	if app.Developer {
		app.Workspace.Path = ""
		app.Workspace.AppliedPath = ""
		app.Workspace.SandboxJobId = ""
		app.Workspace.ETag = ""
		return
	}
	app.appendWorkspaceHistoryMessage(workspacePath)

	app.Workspace.Loading = true
	defer func() {
		app.Workspace.Loading = false
	}()

	folders := []string{}
	if workspacePath != "" {
		folders = append(folders, workspacePath)
	}
	sandbox, err := shared.InferenceSandboxSetFolders(app.Owner, util.OptStringIfNotEmpty(app.Workspace.ETag), folders)
	if err != nil {
		app.Workspace.Error = err.Why
		return
	}

	app.Workspace.AppliedPath = workspacePath
	app.Workspace.SandboxJobId = sandbox.JobId
	app.Workspace.ETag = sandbox.ETag
	app.Workspace.Warnings = append([]string{}, sandbox.Warnings...)
	if workspacePath != "" && len(sandbox.Folders) == 0 {
		app.Workspace.AppliedPath = ""
		app.Workspace.SandboxJobId = ""
		app.Workspace.Error = "The selected folder could not be mounted."
	}
	app.updateCurrentThreadWorkspacePath(workspacePath)
}

func (app *InferencePlaygroundApp) appendWorkspaceHistoryMessage(workspacePath string) {
	message := "Workspace changed: no workspace is selected."
	if workspacePath != "" {
		message = "Workspace changed: " + workspacePath
	}
	for i := len(app.Chat.Messages) - 1; i >= 0; i-- {
		if app.Chat.Messages[i].Synthetic {
			if app.Chat.Messages[i].Content == message {
				return
			}
			break
		}
	}
	now := time.Now().UnixMilli()
	app.Chat.Messages = append(app.Chat.Messages, playgroundChatMessage{Role: "user", Content: message, Synthetic: true, GeneratedAt: now})
	app.markCurrentThreadDirty()
}

// App user-interface and core data management
// =====================================================================================================================

func (app *InferencePlaygroundApp) UserInterface() ucx.UiNode {
	return ucx.Box().
		Sx(
			ucx.SxDisplayNone,
		).
		Children(
			ucx.Router("route"),
			ucx.QueryParamEx("", "currentThreadId", "threadId", false, true, true, true, []string{"model"}),
			ucx.QueryParamReadOnlyWhenPresent("chat.modelId", "model"),
		)
}

func (app *InferencePlaygroundApp) refreshModels() {
	resp := InferenceModelListForOwner(app.walletOwner())
	app.Models = resp
}

func (app *InferencePlaygroundApp) loadThreads() {
	app.Threads = inferencePlaygroundThreadsLoad(app.Owner.CreatedBy, app.Owner.Project)
}

func (app *InferencePlaygroundApp) startThreadFlusher() {
	if app.flusherStarted || app.session == nil {
		return
	}
	app.flusherStarted = true
	ctx := app.session.Context()
	go func() {
		ticker := time.NewTicker(5 * time.Second)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				app.mu.Lock()
				app.flushThreadsLocked()
				app.mu.Unlock()
				return
			case <-ticker.C:
				app.mu.Lock()
				app.flushThreadsLocked()
				app.mu.Unlock()
			}
		}
	}()
}

func (app *InferencePlaygroundApp) ensureCurrentThread() {
	if app.Developer {
		return
	}
	if app.CurrentThreadId != "" {
		if thread, ok := app.currentThread(); ok && !thread.Deleted {
			app.Chat.Messages = slices.Clone(thread.Messages)
			return
		}
	}
	if len(app.Threads) > 0 {
		app.openThread(app.Threads[0].Id)
		return
	}
	app.createThread()
}

func (app *InferencePlaygroundApp) createThread() {
	app.CurrentThreadId = ""
	app.Chat.Messages = nil
	app.Chat.Loading = false
	app.Chat.Usage = InferencePlaygroundTokenUsageState{}
	app.Workspace.Path = ""
	app.Workspace.AppliedPath = ""
	app.Workspace.SandboxJobId = ""
	app.Workspace.ETag = ""
	app.Workspace.Error = ""
	app.Workspace.Warnings = nil
}

func (app *InferencePlaygroundApp) materializeCurrentThread() {
	if app.Developer || app.CurrentThreadId != "" || len(app.Chat.Messages) == 0 {
		return
	}

	now := time.Now().UnixMilli()
	thread := playgroundChatThread{
		Id:            "thread-" + util.SecureToken(),
		Title:         "New thread",
		CreatedAt:     now,
		UpdatedAt:     now,
		WorkspacePath: strings.TrimSpace(app.Workspace.Path),
		Dirty:         true,
	}
	app.Threads = append([]playgroundChatThread{thread}, app.Threads...)
	app.CurrentThreadId = thread.Id
}

func (app *InferencePlaygroundApp) mostRecentThreadModel() string {
	for _, thread := range app.Threads {
		if modelId := playgroundMostRecentMessageModel(thread.Messages); modelId != "" {
			return modelId
		}
	}
	return ""
}

func playgroundMostRecentMessageModel(messages []playgroundChatMessage) string {
	for i := len(messages) - 1; i >= 0; i-- {
		if strings.TrimSpace(messages[i].ModelName) != "" {
			return messages[i].ModelName
		}
	}
	return ""
}

func (app *InferencePlaygroundApp) currentThread() (*playgroundChatThread, bool) {
	for i := range app.Threads {
		if app.Threads[i].Id == app.CurrentThreadId {
			return &app.Threads[i], true
		}
	}
	return nil, false
}

func (app *InferencePlaygroundApp) openThread(id string) {
	for i := range app.Threads {
		if app.Threads[i].Id == id && !app.Threads[i].Deleted {
			app.CurrentThreadId = id
			app.Chat.Messages = slices.Clone(app.Threads[i].Messages)
			app.Workspace.Path = app.Threads[i].WorkspacePath
			app.Workspace.AppliedPath = ""
			app.Workspace.SandboxJobId = ""
			app.Workspace.ETag = ""
			app.Workspace.Error = ""
			app.Workspace.Warnings = nil
			app.configureWorkspace()
			app.Chat.Usage.Session = app.Threads[i].Usage
			app.Chat.Usage.LastQuery = app.Threads[i].LastQuery
			app.Chat.Loading = app.threadLoading(id)
			if modelId := playgroundMostRecentMessageModel(app.Chat.Messages); modelId != "" {
				app.Chat.ModelId = modelId
				app.applyChatModelDefaults()
			}
			app.prepareChatMessagesForUi()
			app.Chat.Curl = app.buildChatCurl()
			return
		}
	}
}

func (app *InferencePlaygroundApp) markCurrentThreadDirty() {
	if app.Developer {
		return
	}
	thread, ok := app.currentThread()
	if !ok {
		app.materializeCurrentThread()
		thread, ok = app.currentThread()
		if !ok {
			return
		}
	}
	thread.Messages = slices.Clone(app.Chat.Messages)
	thread.WorkspacePath = strings.TrimSpace(app.Workspace.Path)
	thread.UpdatedAt = time.Now().UnixMilli()
	thread.Dirty = true
	if thread.Title == "New thread" {
		for _, msg := range thread.Messages {
			if msg.Role == "user" && !msg.Synthetic && !playgroundMessageIsAttachmentOnly(msg) {
				thread.Title = playgroundThreadTitle(msg.Content)
				if !thread.TitleGenerated && !thread.TitleGenerationStarted {
					thread.TitleGenerationStarted = true
					app.generateThreadTitle(thread.Id, app.titleGenerationModelId(app.Chat.ModelId), msg.Content)
				}
				break
			}
		}
	}
	app.sortThreads()
}

func (app *InferencePlaygroundApp) updateCurrentThreadWorkspacePath(path string) {
	if app.Developer {
		return
	}
	thread, ok := app.currentThread()
	if !ok {
		return
	}
	if thread.WorkspacePath == path {
		return
	}
	thread.WorkspacePath = path
	thread.UpdatedAt = time.Now().UnixMilli()
	thread.Dirty = true
	app.sortThreads()
}

func (app *InferencePlaygroundApp) renameThread(id string, requestedTitle string) {
	thread, ok := app.currentThread()
	if id != app.CurrentThreadId {
		for i := range app.Threads {
			if app.Threads[i].Id == id {
				thread = &app.Threads[i]
				ok = true
				break
			}
		}
	}
	if !ok {
		return
	}
	title := strings.TrimSpace(requestedTitle)
	if title == "" {
		title = "New thread"
	}
	thread.Title = title
	thread.UpdatedAt = time.Now().UnixMilli()
	thread.Dirty = true
	thread.TitleGenerated = true
	thread.TitleGenerationStarted = true
	app.sortThreads()
}

func (app *InferencePlaygroundApp) titleGenerationModelId(chatModelId string) string {
	model, ok := app.modelByName(chatModelId)
	if !ok || strings.TrimSpace(model.TitleModelName) == "" {
		return chatModelId
	}
	if _, ok := app.modelByName(model.TitleModelName); !ok {
		return chatModelId
	}
	return model.TitleModelName
}

func (app *InferencePlaygroundApp) generateThreadTitle(threadId string, modelId string, prompt string) {
	owner := app.walletOwner()
	ctx := app.session.Context()
	go func() {
		resp, err := InferenceChat(owner, InferenceChatRequest{
			Model: modelId,
			Messages: []InferenceChatMessage{
				{Role: "system", Content: inferenceChatTextContent("Generate a short chat thread title. Return only the title, without quotes or punctuation at the end. Maximum five words.")},
				{Role: "user", Content: inferenceChatTextContent(prompt[:min(len(prompt), 240)])},
			},
			Temperature:         util.OptValue(0.2),
			TopP:                util.OptValue(0.5),
			MaxCompletionTokens: util.OptValue(16),
		})
		if err != nil || len(resp.Choices) == 0 {
			return
		}

		title := playgroundNormalizeGeneratedThreadTitle(resp.Choices[0].Message.Content.String())
		if title == "" {
			return
		}

		app.mu.Lock()
		defer app.mu.Unlock()
		for i := range app.Threads {
			thread := &app.Threads[i]
			if thread.Id != threadId || thread.Deleted || thread.TitleGenerated {
				continue
			}
			thread.Title = title
			thread.TitleGenerated = true
			thread.UpdatedAt = time.Now().UnixMilli()
			thread.Dirty = true
			app.sortThreads()
			select {
			case <-ctx.Done():
				app.flushThreadsLocked()
			default:
				ucx.AppUpdateModel(app)
			}
			return
		}
	}()
}

func (app *InferencePlaygroundApp) generateThinkingTitle(threadId string, assistantIndex int, modelId string, reasoning string) {
	excerpt := playgroundReasoningTitlePrompt(reasoning)
	if excerpt == "" {
		return
	}
	owner := app.walletOwner()
	ctx := app.session.Context()
	go func() {
		resp, err := InferenceChat(owner, InferenceChatRequest{
			Model: modelId,
			Messages: []InferenceChatMessage{
				{Role: "system", Content: inferenceChatTextContent("Generate a short title for this model reasoning. Return only the title, without quotes or punctuation at the end. Maximum five words.")},
				{Role: "user", Content: inferenceChatTextContent(excerpt)},
			},
			Temperature:         util.OptValue(0.2),
			TopP:                util.OptValue(0.5),
			MaxCompletionTokens: util.OptValue(16),
		})
		if err != nil || len(resp.Choices) == 0 {
			return
		}

		title := playgroundNormalizeGeneratedThreadTitle(resp.Choices[0].Message.Content.String())
		if title == "" {
			return
		}

		app.mu.Lock()
		defer app.mu.Unlock()
		app.updateThreadAssistantReasoningTitle(threadId, assistantIndex, title)
		select {
		case <-ctx.Done():
			app.flushThreadsLocked()
		default:
			ucx.AppUpdateModel(app)
		}
	}()
}

func playgroundReasoningTitlePrompt(reasoning string) string {
	reasoning = strings.TrimSpace(reasoning)
	if reasoning == "" {
		return ""
	}
	if newline := strings.IndexAny(reasoning, "\n"); newline >= 0 {
		reasoning = reasoning[:newline]
	}
	runes := []rune(reasoning)
	if len(runes) > 120 {
		runes = runes[:120]
	}
	return strings.TrimSpace(string(runes))
}

func (app *InferencePlaygroundApp) deleteThread(id string) {
	app.DeletedThreadIds = append(app.DeletedThreadIds, id)
	for _, thread := range app.Threads {
		if thread.Id == id && thread.StoragePath != "" {
			app.DeletedThreadPaths = append(app.DeletedThreadPaths, thread.StoragePath)
			break
		}
	}
	app.Threads = slices.DeleteFunc(app.Threads, func(thread playgroundChatThread) bool {
		return thread.Id == id
	})
	if app.CurrentThreadId == id {
		app.CurrentThreadId = ""
		app.Chat.Messages = nil
		app.ensureCurrentThread()
	}
}

func playgroundMessageIsAttachmentOnly(msg playgroundChatMessage) bool {
	if len(msg.Parts) == 1 {
		kind := msg.Parts[0].Kind
		return kind == "image" || kind == "video" || kind == "audio" || kind == "attachment"
	}
	_, ok := playgroundAttachmentPartFromUrl(msg.Content)
	return ok
}

func (app *InferencePlaygroundApp) sortThreads() {
	sort.SliceStable(app.Threads, func(i, j int) bool {
		return app.Threads[i].UpdatedAt > app.Threads[j].UpdatedAt
	})
}

func (app *InferencePlaygroundApp) flushThreadsLocked() {
	if inferencePlaygroundThreadsFlush(app.Owner.CreatedBy, app.Owner.Project, app.Threads, app.DeletedThreadIds, app.DeletedThreadPaths) {
		for i := range app.Threads {
			app.Threads[i].Dirty = false
		}
		app.DeletedThreadIds = nil
		app.DeletedThreadPaths = nil
	}
}

func playgroundThreadTitle(prompt string) string {
	prompt = strings.TrimSpace(prompt)
	if prompt == "" {
		return "New thread"
	}
	runes := []rune(prompt)
	if len(runes) > 80 {
		runes = runes[:80]
	}
	return string(runes)
}

func playgroundNormalizeGeneratedThreadTitle(title string) string {
	title = strings.TrimSpace(title)
	title = strings.Trim(title, "`\"' ")
	title = strings.TrimRight(title, ".:;!?")
	title = strings.Join(strings.Fields(title), " ")
	runes := []rune(title)
	if len(runes) > 80 {
		runes = runes[:80]
	}
	return strings.TrimSpace(string(runes))
}

func (app *InferencePlaygroundApp) availableModes() []string {
	if len(app.Models) == 0 {
		return []string{playgroundModeChat}
	}

	var modes []string
	if len(app.modelOptionsFor(InferenceTextGeneration)) > 0 {
		modes = append(modes, playgroundModeChat)
	}

	return modes
}

func (app *InferencePlaygroundApp) firstModelFor(capability InferenceCapability) string {
	options := app.modelOptionsFor(capability)
	if len(options) == 0 {
		return ""
	}
	return options[0].Key
}

func (app *InferencePlaygroundApp) modelOptionsFor(capability InferenceCapability) []ucx.Option {
	options := make([]ucx.Option, 0, len(app.Models))
	for _, model := range app.Models {
		if slices.Contains(model.Capabilities, capability) {
			options = append(options, ucx.Option{
				Key:   model.Name,
				Value: model.Title,
			})
		}
	}

	sort.Slice(options, func(i, j int) bool { return options[i].Key < options[j].Key })
	return options
}

func (app *InferencePlaygroundApp) modelByName(name string) (InferenceModel, bool) {
	for _, model := range app.Models {
		if model.Name == name {
			return model, true
		}
	}
	return InferenceModel{}, false
}

func (app *InferencePlaygroundApp) applyChatModelDefaults() {
	model, ok := app.modelByName(app.Chat.ModelId)
	if !ok {
		app.Chat.AppliedDefaultsModelId = app.Chat.ModelId
		return
	}

	app.Chat.Temperature = model.ChatSettings.Temperature
	app.Chat.TopP = model.ChatSettings.TopP
	app.Chat.MaxCompletionTokens = int64(model.ChatSettings.MaxCompletionTokens)
	if !app.Developer {
		app.Chat.SystemPrompt = app.chatSystemPrompt()
	} else if model.ChatSettings.SystemPrompt != nil {
		app.Chat.SystemPrompt = *model.ChatSettings.SystemPrompt
	} else {
		app.Chat.SystemPrompt = playgroundGlobalSystemPrompt
	}
	app.Chat.AppliedDefaultsModelId = app.Chat.ModelId
}

// Chat interface
// =====================================================================================================================

func (app *InferencePlaygroundApp) prepareChatMessagesForUi() {
	for i := range app.Chat.Messages {
		app.Chat.Messages[i].MessageIndex = int64(i)
	}
}

func (app *InferencePlaygroundApp) runChat(attachments []playgroundChatAttachment) {
	prompt := strings.TrimSpace(app.Chat.Prompt)
	prompt = playgroundPromptWithTextAttachments(prompt, attachments)

	app.Chat.Loading = true
	if !app.Developer && app.Workspace.SandboxJobId == "" {
		app.configureWorkspace()
	}

	request := InferenceChatRequest{
		Model:               app.Chat.ModelId,
		Stream:              app.Chat.Streaming,
		Messages:            app.chatRequestMessages(prompt, attachments),
		StreamOptions:       util.Option[InferenceChatStreamOptions]{},
		Temperature:         util.OptValue(app.Chat.Temperature),
		TopP:                util.OptValue(app.Chat.TopP),
		PresencePenalty:     util.OptValue(app.Chat.PresencePenalty),
		FrequencyPenalty:    util.OptValue(app.Chat.FrequencyPenalty),
		MaxCompletionTokens: util.OptValue(int(app.Chat.MaxCompletionTokens)),
		Logprobs:            util.OptValue(app.Chat.Logprobs),
		TopLogprobs:         util.OptValue(int(app.Chat.TopLogprobs)),
	}
	app.prepareChatTools(&request)
	if request.Stream {
		request.StreamOptions = util.OptValue(InferenceChatStreamOptions{IncludeUsage: true})
	}

	if strings.HasPrefix(prompt, "/") {
		if app.runDeveloperSlashCommand(prompt) {
			ucx.AppUpdateModel(app)
			return
		}
	} else {
		owner := app.walletOwner()
		now := time.Now().UnixMilli()
		app.Chat.Messages = append(app.Chat.Messages, playgroundAttachmentMessages(attachments, now)...)
		app.Chat.Messages = append(app.Chat.Messages, playgroundChatMessage{Role: "user", Content: prompt, Parts: playgroundChatMessageParts(prompt, "", "", false), GeneratedAt: now})
		app.Chat.Messages = append(app.Chat.Messages, playgroundChatMessage{Role: "assistant", Content: "", Parts: playgroundChatMessageParts("", "", "", false), GeneratedAt: now, ModelName: app.Chat.ModelId, StartedAt: now})
		assistantIndex := len(app.Chat.Messages) - 1
		app.Chat.Prompt = ""
		app.markCurrentThreadDirty()
		threadId := app.CurrentThreadId
		app.setThreadLoading(threadId, true)
		ucx.AppUpdateUi(app)

		go app.runChatResponse(owner, threadId, assistantIndex, request)
	}
}

func (app *InferencePlaygroundApp) regenerateChat(modelId string, messageIndex int64) {
	if strings.TrimSpace(modelId) != "" {
		app.Chat.ModelId = modelId
		app.applyChatModelDefaults()
		if !app.Developer && app.Workspace.SandboxJobId == "" {
			app.configureWorkspace()
		}
	}

	assistantIndex := int(messageIndex)
	if assistantIndex < 0 || assistantIndex >= len(app.Chat.Messages) || app.Chat.Messages[assistantIndex].Role != "assistant" {
		assistantIndex = -1
		for i := len(app.Chat.Messages) - 1; i >= 0; i-- {
			if app.Chat.Messages[i].Role == "assistant" {
				assistantIndex = i
				break
			}
		}
	}
	if assistantIndex < 0 {
		return
	}

	lastUserIndex := -1
	for i := assistantIndex - 1; i >= 0; i-- {
		if app.Chat.Messages[i].Role == "user" && !app.Chat.Messages[i].Synthetic {
			lastUserIndex = i
			break
		}
	}
	if lastUserIndex < 0 {
		return
	}

	app.Chat.Messages = slices.Delete(app.Chat.Messages, assistantIndex, len(app.Chat.Messages))
	app.Chat.Loading = true
	ucx.AppUpdateUi(app)

	request := InferenceChatRequest{
		Model:               app.Chat.ModelId,
		Stream:              app.Chat.Streaming,
		Messages:            app.chatRequestMessagesFromHistory(),
		StreamOptions:       util.Option[InferenceChatStreamOptions]{},
		Temperature:         util.OptValue(app.Chat.Temperature),
		TopP:                util.OptValue(app.Chat.TopP),
		PresencePenalty:     util.OptValue(app.Chat.PresencePenalty),
		FrequencyPenalty:    util.OptValue(app.Chat.FrequencyPenalty),
		MaxCompletionTokens: util.OptValue(int(app.Chat.MaxCompletionTokens)),
		Logprobs:            util.OptValue(app.Chat.Logprobs),
		TopLogprobs:         util.OptValue(int(app.Chat.TopLogprobs)),
	}
	app.prepareChatTools(&request)
	if request.Stream {
		request.StreamOptions = util.OptValue(InferenceChatStreamOptions{IncludeUsage: true})
	}

	now := time.Now().UnixMilli()
	app.Chat.Messages = append(app.Chat.Messages, playgroundChatMessage{Role: "assistant", Content: "", Parts: playgroundChatMessageParts("", "", "", false), GeneratedAt: now, ModelName: app.Chat.ModelId, StartedAt: now})
	assistantIndex = len(app.Chat.Messages) - 1
	threadId := app.CurrentThreadId
	owner := app.walletOwner()
	app.Chat.Usage.LastQuery = InferencePlaygroundTokenUsage{}
	app.markCurrentThreadDirty()
	app.setThreadLoading(threadId, true)
	ucx.AppUpdateModel(app)

	go app.runChatResponse(owner, threadId, assistantIndex, request)
}

func (app *InferencePlaygroundApp) runChatResponse(owner apm.WalletOwner, threadId string, assistantIndex int, request InferenceChatRequest) {
	assistant := ""
	reasoning := ""
	usageSeen := InferenceChatUsage{}
	startedAt := time.Now().UnixMilli()
	firstTokenAt := int64(0)
	if request.Stream {
		var err *util.HttpError
		assistant, reasoning, usageSeen, firstTokenAt, err = app.runChatResponseStreaming(owner, &request, threadId, assistantIndex, startedAt)
		if err != nil {
			assistant = err.Why
		}
	} else {
		resp, err := InferenceChat(owner, request)
		if err != nil {
			assistant = err.Why
		} else {
			firstTokenAt = time.Now().UnixMilli()
			usageSeen = resp.Usage
			if len(resp.Choices) > 0 {
				assistant = resp.Choices[0].Message.Content.String()
				reasoning = resp.Choices[0].Message.Reasoning.String()
			}
			if util.DevelopmentModeEnabled() {
				reasoning = strings.Join(playgroundSyntheticReasoningDeltas(request), "") + reasoning
			}
		}
	}

	if assistant == "" {
		assistant = "(no response)"
	}

	app.mu.Lock()
	defer app.mu.Unlock()
	finishedAt := time.Now().UnixMilli()
	if firstTokenAt == 0 {
		firstTokenAt = finishedAt
	}
	app.updateThreadAssistant(threadId, assistantIndex, assistant, reasoning, "", false, request.Model, startedAt, firstTokenAt, finishedAt, int64(usageSeen.CompletionTokens))
	if reasoning != "" {
		app.generateThinkingTitle(threadId, assistantIndex, app.titleGenerationModelId(request.Model), reasoning)
	}
	app.Chat.Curl = app.buildChatCurl()
	app.Chat.Prompt = ""
	app.Chat.Loading = false
	app.setThreadLoading(threadId, false)
	app.applyChatUsage(threadId, usageSeen)
	ucx.AppUpdateUi(app)
}

func (app *InferencePlaygroundApp) prepareChatTools(request *InferenceChatRequest) {
	if !request.Stream {
		return
	}
	if playgroundToolsDisabledForModel(request.Model) {
		return
	}
	tools := app.playgroundToolDefinitions()
	if len(tools) == 0 {
		return
	}
	request.Tools = tools
	request.ParallelToolCalls = util.OptValue(true)
}

func playgroundToolsDisabledForModel(model string) bool {
	return util.DevelopmentModeEnabled() && model == "qwen3-0.6b"
}

type playgroundStreamingToolCall struct {
	Id        string
	Type      string
	Name      string
	Arguments strings.Builder
}

func (app *InferencePlaygroundApp) runChatResponseStreaming(owner apm.WalletOwner, request *InferenceChatRequest, threadId string, assistantIndex int, startedAt int64) (string, string, InferenceChatUsage, int64, *util.HttpError) {
	var builder strings.Builder
	var reasoningBuilder strings.Builder
	usageSeen := InferenceChatUsage{}
	currentStreamUsage := InferenceChatUsage{}
	firstTokenAt := int64(0)
	publishDelta := func(contentDelta string, reasoningDelta string) {
		if contentDelta != "" {
			builder.WriteString(contentDelta)
		}
		if reasoningDelta != "" {
			reasoningBuilder.WriteString(reasoningDelta)
		}
		if firstTokenAt == 0 {
			firstTokenAt = time.Now().UnixMilli()
		}
		app.mu.Lock()
		usageForUi := inferenceChatUsageAdd(usageSeen, currentStreamUsage)
		app.updateThreadAssistant(threadId, assistantIndex, builder.String(), reasoningBuilder.String(), "", strings.TrimSpace(builder.String()) == "", request.Model, startedAt, firstTokenAt, 0, int64(usageForUi.CompletionTokens))
		ucx.AppUpdateModel(app)
		app.mu.Unlock()
	}

	for iteration := 0; iteration < playgroundToolMaxIterations; iteration++ {
		chunks, err := InferenceChatStreaming(owner, *request)
		if err != nil {
			return "", "", usageSeen, firstTokenAt, err
		}

		toolCallsByIndex := map[int]*playgroundStreamingToolCall{}
		toolCallOrder := []int{}
		streamUsage := InferenceChatUsage{}
		if util.DevelopmentModeEnabled() && iteration == 0 {
			for _, delta := range playgroundSyntheticReasoningDeltas(*request) {
				publishDelta("", delta)
				time.Sleep(100 * time.Millisecond)
			}
		}
		for chunk := range chunks {
			streamUsage = chunk.Usage
			currentStreamUsage = streamUsage
			if len(chunk.Choices) == 0 {
				continue
			}
			choice := chunk.Choices[0]
			contentDelta := choice.Delta.Content
			reasoningDelta := choice.Delta.Reasoning
			for _, toolCallDelta := range choice.Delta.ToolCalls {
				call := toolCallsByIndex[toolCallDelta.Index]
				if call == nil {
					call = &playgroundStreamingToolCall{}
					toolCallsByIndex[toolCallDelta.Index] = call
					toolCallOrder = append(toolCallOrder, toolCallDelta.Index)
				}
				if toolCallDelta.Id != "" {
					call.Id = toolCallDelta.Id
				}
				if toolCallDelta.Type != "" {
					call.Type = toolCallDelta.Type
				}
				if toolCallDelta.Function != nil {
					if toolCallDelta.Function.Name != "" {
						call.Name = toolCallDelta.Function.Name
					}
					if toolCallDelta.Function.Arguments != "" {
						call.Arguments.WriteString(toolCallDelta.Function.Arguments)
					}
				}
			}
			if contentDelta == "" && reasoningDelta == "" {
				continue
			}
			publishDelta(contentDelta, reasoningDelta)
		}
		usageSeen = inferenceChatUsageAdd(usageSeen, streamUsage)

		if len(toolCallOrder) == 0 {
			return strings.TrimSpace(builder.String()), strings.TrimSpace(reasoningBuilder.String()), usageSeen, firstTokenAt, nil
		}

		toolCalls := playgroundStreamingToolCalls(toolCallsByIndex, toolCallOrder)
		request.Messages = append(request.Messages, InferenceChatMessage{Role: "assistant", Content: inferenceChatTextContent(""), ToolCalls: toolCalls})

		for _, call := range toolCalls {
			app.appendThreadAssistantPart(threadId, assistantIndex, playgroundToolChatPart(call, "running", ""), request.Model, startedAt)
			result := app.playgroundToolDispatch(call)
			app.appendThreadAssistantPart(threadId, assistantIndex, playgroundToolChatPart(call, playgroundToolStatus(result), playgroundToolPartBody(call, result)), request.Model, startedAt)
			request.Messages = append(request.Messages, result.Message)
		}
	}

	if builder.Len() == 0 {
		builder.WriteString("Tool call limit reached before a final answer was produced.")
	}
	return strings.TrimSpace(builder.String()), strings.TrimSpace(reasoningBuilder.String()), usageSeen, firstTokenAt, nil
}

func playgroundStreamingToolCalls(callsByIndex map[int]*playgroundStreamingToolCall, order []int) []InferenceChatToolCall {
	result := make([]InferenceChatToolCall, 0, len(order))
	for _, index := range order {
		call := callsByIndex[index]
		if call == nil {
			continue
		}
		callType := call.Type
		if callType == "" {
			callType = "function"
		}
		result = append(result, InferenceChatToolCall{Id: call.Id, Type: callType, Function: InferenceChatToolCallFunction{Name: call.Name, Arguments: call.Arguments.String()}})
	}
	return result
}

func playgroundToolStatus(result playgroundToolResult) string {
	if result.Error != "" {
		return "error"
	}
	return "completed"
}

func playgroundToolChatPart(call InferenceChatToolCall, status string, body string) playgroundChatMessagePart {
	name := strings.TrimSpace(call.Function.Name)
	return playgroundChatMessagePart{
		Kind:     "tool",
		Summary:  name,
		Text:     call.Function.Arguments,
		Body:     body,
		ToolName: name,
		Status:   status,
	}
}

func playgroundToolPartBody(call InferenceChatToolCall, result playgroundToolResult) string {
	var builder strings.Builder
	arguments := strings.TrimSpace(call.Function.Arguments)
	if arguments != "" {
		builder.WriteString("Arguments:\n")
		builder.WriteString(arguments)
	}
	if result.Output != "" {
		if builder.Len() > 0 {
			builder.WriteString("\n\n")
		}
		builder.WriteString("Result:\n")
		builder.WriteString(result.Output)
	}
	if result.Error != "" {
		if builder.Len() > 0 {
			builder.WriteString("\n\n")
		}
		builder.WriteString("Error:\n")
		builder.WriteString(result.Error)
	}
	return builder.String()
}

func (app *InferencePlaygroundApp) appendThreadAssistantPart(threadId string, assistantIndex int, part playgroundChatMessagePart, modelName string, startedAt int64) {
	app.mu.Lock()
	defer app.mu.Unlock()
	now := time.Now().UnixMilli()
	for i := range app.Threads {
		thread := &app.Threads[i]
		if thread.Id != threadId || assistantIndex < 0 || assistantIndex >= len(thread.Messages) {
			continue
		}
		msg := &thread.Messages[assistantIndex]
		msg.Parts = playgroundUpsertToolPart(msg.Parts, part)
		msg.ModelName = modelName
		msg.StartedAt = startedAt
		msg.FirstTokenAt = now
		thread.UpdatedAt = now
		thread.Dirty = true
		if app.CurrentThreadId == threadId {
			app.Chat.Messages = slices.Clone(thread.Messages)
			app.prepareChatMessagesForUi()
		}
		ucx.AppUpdateModel(app)
		return
	}
	if threadId == "" && assistantIndex >= 0 && assistantIndex < len(app.Chat.Messages) {
		msg := &app.Chat.Messages[assistantIndex]
		msg.Parts = playgroundUpsertToolPart(msg.Parts, part)
		msg.ModelName = modelName
		msg.StartedAt = startedAt
		msg.FirstTokenAt = now
		ucx.AppUpdateModel(app)
	}
}

func playgroundUpsertToolPart(parts []playgroundChatMessagePart, part playgroundChatMessagePart) []playgroundChatMessagePart {
	if part.Kind != "tool" {
		return append(parts, part)
	}
	if part.Status != "running" {
		for i := len(parts) - 1; i >= 0; i-- {
			existing := parts[i]
			if existing.Kind == "tool" && existing.Status == "running" && existing.ToolName == part.ToolName && existing.Text == part.Text {
				updated := slices.Clone(parts)
				updated[i] = part
				return updated
			}
		}
	}
	insertAt := len(parts)
	for i, existing := range parts {
		if existing.Kind != "tool" {
			insertAt = i
			break
		}
	}
	updated := make([]playgroundChatMessagePart, 0, len(parts)+1)
	updated = append(updated, parts[:insertAt]...)
	updated = append(updated, part)
	updated = append(updated, parts[insertAt:]...)
	return updated
}

func inferenceChatUsageAdd(a InferenceChatUsage, b InferenceChatUsage) InferenceChatUsage {
	a.PromptTokens += b.PromptTokens
	a.CompletionTokens += b.CompletionTokens
	a.TotalTokens += b.TotalTokens
	if b.PromptTokensDetails.Present {
		if !a.PromptTokensDetails.Present {
			a.PromptTokensDetails = b.PromptTokensDetails
		} else {
			a.PromptTokensDetails.Value.CachedTokens += b.PromptTokensDetails.Value.CachedTokens
		}
	}
	return a
}

func (app *InferencePlaygroundApp) setThreadLoading(threadId string, loading bool) {
	if strings.TrimSpace(threadId) == "" {
		app.Chat.Loading = false
		return
	}
	if loading {
		if !slices.Contains(app.LoadingThreadIds, threadId) {
			app.LoadingThreadIds = append(app.LoadingThreadIds, threadId)
		}
	} else {
		app.LoadingThreadIds = slices.DeleteFunc(app.LoadingThreadIds, func(id string) bool {
			return id == threadId
		})
	}
	app.Chat.Loading = app.currentThreadLoading()
}

func (app *InferencePlaygroundApp) threadLoading(threadId string) bool {
	return strings.TrimSpace(threadId) != "" && slices.Contains(app.LoadingThreadIds, threadId)
}

func (app *InferencePlaygroundApp) currentThreadLoading() bool {
	return app.threadLoading(app.CurrentThreadId)
}

func playgroundSyntheticReasoningDeltas(request InferenceChatRequest) []string {
	prompt := "test thinking summary"
	for i := len(request.Messages) - 1; i >= 0; i-- {
		if request.Messages[i].Role == "user" {
			prompt = strings.TrimSpace(request.Messages[i].Content.String())
			break
		}
	}
	if prompt == "" {
		prompt = "test thinking summary"
	}

	var result []string
	for word := range strings.SplitSeq(prompt, " ") {
		if len(result) > 30 {
			break
		}
		result = append(result, word+" ")
	}
	result = append(result, "\n\n")
	for word := range strings.SplitSeq(prompt, " ") {
		if len(result) > 30 {
			break
		}
		result = append(result, word+" ")
	}

	return result
}

func (app *InferencePlaygroundApp) updateThreadAssistant(threadId string, assistantIndex int, content string, reasoning string, reasoningTitle string, reasoningOpen bool, modelName string, startedAt int64, firstTokenAt int64, finishedAt int64, outputTokens int64) {
	for i := range app.Threads {
		thread := &app.Threads[i]
		if thread.Id != threadId || assistantIndex < 0 || assistantIndex >= len(thread.Messages) {
			continue
		}
		msg := &thread.Messages[assistantIndex]
		parts := playgroundToolParts(msg.Parts)
		parts = append(parts, playgroundChatMessageParts(content, reasoning, reasoningTitle, reasoningOpen)...)
		msg.Content = content
		msg.Reasoning = reasoning
		msg.ReasoningTitle = reasoningTitle
		msg.Parts = parts
		msg.ModelName = modelName
		msg.StartedAt = startedAt
		msg.FirstTokenAt = firstTokenAt
		msg.FinishedAt = finishedAt
		msg.OutputTokens = outputTokens
		thread.UpdatedAt = time.Now().UnixMilli()
		thread.Dirty = true
		if app.CurrentThreadId == threadId {
			app.Chat.Messages = slices.Clone(thread.Messages)
			app.prepareChatMessagesForUi()
		}
		return
	}
	if threadId == "" && assistantIndex >= 0 && assistantIndex < len(app.Chat.Messages) {
		msg := &app.Chat.Messages[assistantIndex]
		parts := playgroundToolParts(msg.Parts)
		parts = append(parts, playgroundChatMessageParts(content, reasoning, reasoningTitle, reasoningOpen)...)
		msg.Content = content
		msg.Reasoning = reasoning
		msg.ReasoningTitle = reasoningTitle
		msg.Parts = parts
		msg.ModelName = modelName
		msg.StartedAt = startedAt
		msg.FirstTokenAt = firstTokenAt
		msg.FinishedAt = finishedAt
		msg.OutputTokens = outputTokens
	}
}

func playgroundToolParts(parts []playgroundChatMessagePart) []playgroundChatMessagePart {
	result := make([]playgroundChatMessagePart, 0)
	for _, part := range parts {
		if part.Kind == "tool" {
			result = append(result, part)
		}
	}
	return result
}

func (app *InferencePlaygroundApp) updateThreadAssistantReasoningTitle(threadId string, assistantIndex int, reasoningTitle string) {
	for i := range app.Threads {
		thread := &app.Threads[i]
		if thread.Id != threadId || assistantIndex < 0 || assistantIndex >= len(thread.Messages) {
			continue
		}
		msg := &thread.Messages[assistantIndex]
		msg.ReasoningTitle = reasoningTitle
		parts := playgroundToolParts(msg.Parts)
		parts = append(parts, playgroundChatMessageParts(msg.Content, msg.Reasoning, msg.ReasoningTitle, false)...)
		msg.Parts = parts
		thread.UpdatedAt = time.Now().UnixMilli()
		thread.Dirty = true
		if app.CurrentThreadId == threadId {
			app.Chat.Messages = slices.Clone(thread.Messages)
		}
		return
	}
	if threadId == "" && assistantIndex >= 0 && assistantIndex < len(app.Chat.Messages) {
		msg := &app.Chat.Messages[assistantIndex]
		msg.ReasoningTitle = reasoningTitle
		parts := playgroundToolParts(msg.Parts)
		parts = append(parts, playgroundChatMessageParts(msg.Content, msg.Reasoning, msg.ReasoningTitle, false)...)
		msg.Parts = parts
	}
}

func playgroundChatComposerEvent(value ucx.Value) (string, []playgroundChatAttachment) {
	if value.Kind != ucx.ValueObject {
		return value.String, nil
	}
	prompt := value.Object["prompt"].String
	attachmentsValue := value.Object["attachments"]
	attachments := make([]playgroundChatAttachment, 0, len(attachmentsValue.List))
	if attachmentsValue.Kind == ucx.ValueList {
		for _, item := range attachmentsValue.List {
			if item.Kind != ucx.ValueObject {
				continue
			}
			attachments = append(attachments, playgroundChatAttachment{
				Kind:         item.Object["kind"].String,
				AttachmentId: item.Object["attachmentId"].String,
				FileName:     item.Object["fileName"].String,
				Url:          item.Object["url"].String,
				Text:         item.Object["text"].String,
			})
		}
	}
	return prompt, attachments
}

func playgroundPromptWithTextAttachments(prompt string, attachments []playgroundChatAttachment) string {
	var builder strings.Builder
	builder.WriteString(prompt)
	for _, attachment := range attachments {
		if attachment.Kind != "text" {
			continue
		}
		if builder.Len() > 0 && !strings.HasSuffix(builder.String(), "\n") {
			builder.WriteString("\n")
		}
		builder.WriteString("<attachment name=\"")
		builder.WriteString(html.EscapeString(attachment.FileName))
		builder.WriteString("\">\n")
		builder.WriteString(attachment.Text)
		if !strings.HasSuffix(attachment.Text, "\n") {
			builder.WriteString("\n")
		}
		builder.WriteString("</attachment>")
	}
	return builder.String()
}

func playgroundAttachmentMessages(attachments []playgroundChatAttachment, generatedAt int64) []playgroundChatMessage {
	messages := []playgroundChatMessage{}
	for _, attachment := range attachments {
		if attachment.Kind != "image" && attachment.Kind != "video" && attachment.Kind != "audio" {
			continue
		}
		part := playgroundChatMessagePart{Kind: attachment.Kind, FileName: attachment.FileName, Url: attachment.Url}
		messages = append(messages, playgroundChatMessage{Role: "user", Content: attachment.Url, Parts: []playgroundChatMessagePart{part}, GeneratedAt: generatedAt})
	}
	return messages
}

func playgroundChatMessageParts(content string, reasoning string, reasoningTitle string, reasoningOpen bool) []playgroundChatMessagePart {
	parts := []playgroundChatMessagePart{}
	if reasoning != "" {
		body := strings.TrimLeft(reasoning, "\n")

		parts = append(parts, playgroundChatMessagePart{
			Kind:    "thinking",
			Summary: strings.TrimSpace(reasoningTitle),
			Body:    body,
			Open:    reasoningOpen,
		})
	}
	if attachmentPart, ok := playgroundAttachmentPartFromUrl(content); ok {
		parts = append(parts, attachmentPart)
		return parts
	}
	parts = appendPlaygroundContentParts(parts, content)

	return parts
}

func appendPlaygroundContentParts(parts []playgroundChatMessagePart, content string) []playgroundChatMessagePart {
	for {
		start := strings.Index(content, "<attachment name=\"")
		if start < 0 {
			return appendPlaygroundTextPart(parts, content)
		}
		parts = appendPlaygroundTextPart(parts, content[:start])
		nameStart := start + len("<attachment name=\"")
		nameEnd := strings.Index(content[nameStart:], "\">")
		if nameEnd < 0 {
			return appendPlaygroundTextPart(parts, content[start:])
		}
		nameEnd += nameStart
		bodyStart := nameEnd + len("\">\n")
		endTag := "\n</attachment>"
		bodyEnd := strings.Index(content[bodyStart:], endTag)
		if bodyEnd < 0 {
			return appendPlaygroundTextPart(parts, content[start:])
		}
		bodyEnd += bodyStart
		parts = append(parts, playgroundChatMessagePart{Kind: "attachment", Text: content[bodyStart:bodyEnd], FileName: html.UnescapeString(content[nameStart:nameEnd])})
		content = content[bodyEnd+len(endTag):]
	}
}

func playgroundAttachmentPartFromUrl(raw string) (playgroundChatMessagePart, bool) {
	parsed, err := url.Parse(strings.TrimSpace(raw))
	if err != nil || parsed.Scheme != "https" || parsed.Path != "/api/inference/attachments/download" {
		return playgroundChatMessagePart{}, false
	}
	id := parsed.Query().Get("id")
	if id == "" {
		return playgroundChatMessagePart{Kind: "attachment", FileName: "Unknown", Url: raw}, true
	}
	fileName := id
	if attachment, ok := attachmentLookup(id); ok && strings.TrimSpace(attachment.Filename) != "" {
		fileName = attachment.Filename
	}
	kind := playgroundAttachmentKindFromName(id)
	if kind == "" {
		kind = "attachment"
	}
	return playgroundChatMessagePart{Kind: kind, FileName: fileName, Url: raw}, true
}

func playgroundAttachmentKindFromName(name string) string {
	switch kind := strings.Split(mime.TypeByExtension(filepath.Ext(name)), "/")[0]; kind {
	case "image", "video", "audio":
		return kind
	default:
		return ""
	}
}

func appendPlaygroundTextPart(parts []playgroundChatMessagePart, text string) []playgroundChatMessagePart {
	if text == "" {
		return parts
	}
	return append(parts, playgroundChatMessagePart{Kind: "text", Text: text})
}

func (app *InferencePlaygroundApp) applyChatUsage(threadId string, usage InferenceChatUsage) {
	cachedInputTokens := int64(0)
	if usage.PromptTokensDetails.Present {
		cachedInputTokens = int64(usage.PromptTokensDetails.Value.CachedTokens)
	}
	if cachedInputTokens < 0 {
		cachedInputTokens = 0
	}
	if cachedInputTokens > int64(usage.PromptTokens) {
		cachedInputTokens = int64(usage.PromptTokens)
	}
	inputTokens := int64(usage.PromptTokens) - cachedInputTokens
	outputTokens := int64(usage.CompletionTokens)
	reportedTokens := int64(usage.TotalTokens)
	if reportedTokens == 0 {
		reportedTokens = inputTokens + cachedInputTokens + outputTokens
	}

	lastQuery := InferencePlaygroundTokenUsage{
		Input:       inputTokens,
		CachedInput: cachedInputTokens,
		Output:      outputTokens,
		Reported:    reportedTokens,
	}
	updatedThread := false
	for i := range app.Threads {
		thread := &app.Threads[i]
		if thread.Id != threadId {
			continue
		}
		updatedThread = true
		thread.Usage.Input += inputTokens
		thread.Usage.CachedInput += cachedInputTokens
		thread.Usage.Output += outputTokens
		thread.Usage.Reported += reportedTokens
		thread.LastQuery = lastQuery
		thread.Dirty = true
		if app.CurrentThreadId == threadId {
			app.Chat.Usage.Session = thread.Usage
			app.Chat.Usage.LastQuery = lastQuery
		}
		break
	}
	if !updatedThread {
		app.Chat.Usage.LastQuery = lastQuery
		app.Chat.Usage.Session.Input += inputTokens
		app.Chat.Usage.Session.CachedInput += cachedInputTokens
		app.Chat.Usage.Session.Output += outputTokens
		app.Chat.Usage.Session.Reported += reportedTokens
	}
	app.prepareChatMessagesForUi()
	ucx.AppUpdateModel(app)
}

func (app *InferencePlaygroundApp) chatRequestMessages(prompt string, attachments []playgroundChatAttachment) []InferenceChatMessage {
	messages := make([]InferenceChatMessage, 0, len(app.Chat.Messages)+2)
	if systemPrompt := app.chatSystemPrompt(); systemPrompt != "" {
		messages = append(messages, InferenceChatMessage{Role: "system", Content: inferenceChatTextContent(systemPrompt)})
	}
	for _, msg := range app.Chat.Messages {
		messages = append(messages, InferenceChatMessage{Role: msg.Role, Content: playgroundInferenceContent(msg)})
	}
	for _, attachment := range attachments {
		if attachment.Kind != "image" && attachment.Kind != "video" && attachment.Kind != "audio" {
			continue
		}
		messages = append(messages, InferenceChatMessage{Role: "user", Content: playgroundInferenceAttachmentContent(attachment.Kind, attachment.Url)})
	}
	messages = append(messages, InferenceChatMessage{Role: "user", Content: inferenceChatTextContent(prompt)})
	return messages
}

func (app *InferencePlaygroundApp) chatRequestMessagesFromHistory() []InferenceChatMessage {
	messages := make([]InferenceChatMessage, 0, len(app.Chat.Messages)+1)
	if systemPrompt := app.chatSystemPrompt(); systemPrompt != "" {
		messages = append(messages, InferenceChatMessage{Role: "system", Content: inferenceChatTextContent(systemPrompt)})
	}
	for _, msg := range app.Chat.Messages {
		messages = append(messages, InferenceChatMessage{Role: msg.Role, Content: playgroundInferenceContent(msg)})
	}
	return messages
}

func playgroundInferenceContent(msg playgroundChatMessage) InferenceChatMessageContent {
	if len(msg.Parts) == 1 {
		part := msg.Parts[0]
		if part.Kind == "image" || part.Kind == "video" || part.Kind == "audio" {
			return playgroundInferenceAttachmentContent(part.Kind, part.Url)
		}
	}
	if attachmentPart, ok := playgroundAttachmentPartFromUrl(msg.Content); ok && (attachmentPart.Kind == "image" || attachmentPart.Kind == "video" || attachmentPart.Kind == "audio") {
		return playgroundInferenceAttachmentContent(attachmentPart.Kind, attachmentPart.Url)
	}
	return inferenceChatTextContent(msg.Content)
}

func playgroundInferenceAttachmentContent(kind string, rawUrl string) InferenceChatMessageContent {
	part := InferenceChatContentPart{Type: kind + "_url"}
	switch kind {
	case "image":
		part.ImageUrl = &rawUrl
	case "video":
		part.VideoUrl = &rawUrl
	case "audio":
		part.AudioUrl = &rawUrl
	}
	return InferenceChatMessageContent{Parts: []InferenceChatContentPart{part}}
}

func (app *InferencePlaygroundApp) buildChatCurl() string {
	stream := app.Chat.Streaming
	var tools []InferenceChatTool
	if stream && !playgroundToolsDisabledForModel(app.Chat.ModelId) {
		tools = app.playgroundToolDefinitions()
	}
	payload := map[string]any{
		"model":    app.Chat.ModelId,
		"stream":   stream,
		"messages": app.chatCurlMessages(),
	}
	if len(tools) > 0 {
		payload["tools"] = tools
		payload["parallel_tool_calls"] = false
	}
	payload["temperature"] = app.Chat.Temperature
	payload["top_p"] = app.Chat.TopP
	payload["presence_penalty"] = app.Chat.PresencePenalty
	payload["frequency_penalty"] = app.Chat.FrequencyPenalty
	if app.Chat.MaxCompletionTokens > 0 {
		payload["max_completion_tokens"] = app.Chat.MaxCompletionTokens
	}
	if app.Chat.Logprobs {
		payload["logprobs"] = true
	}
	if app.Chat.TopLogprobs > 0 {
		payload["top_logprobs"] = app.Chat.TopLogprobs
	}
	return curlJSONCommand(inferenceServerBase()+"/chat/completions", payload, stream)
}

func (app *InferencePlaygroundApp) chatCurlMessages() []map[string]string {
	messages := make([]map[string]string, 0, len(app.Chat.Messages)+2)
	if systemPrompt := app.chatSystemPrompt(); systemPrompt != "" {
		messages = append(messages, map[string]string{"role": "system", "content": systemPrompt})
	}
	for _, msg := range app.Chat.Messages {
		messages = append(messages, map[string]string{"role": msg.Role, "content": msg.Content})
	}
	if strings.TrimSpace(app.Chat.Prompt) != "" {
		messages = append(messages, map[string]string{"role": "user", "content": app.Chat.Prompt})
	}
	if len(messages) == 0 {
		messages = append(messages, map[string]string{"role": "user", "content": "Say hello."})
	}
	return messages
}

// Shared UI components
// =====================================================================================================================

func (app *InferencePlaygroundApp) usageBox(prefixBindPath string) ucx.UiNode {
	return ucx.AccordionNode("Usage", true).
		Children(
			ucx.Box().
				Sx(ucx.SxDisplayFlex, ucx.SxFlexDirectionColumn, ucx.SxGap(8)).
				Children(
					usageRow("Session input tokens", ucx.TextBound(fmt.Sprintf("%s.usage.session.input", prefixBindPath))),
					usageRow("Session output tokens", ucx.TextBound(fmt.Sprintf("%s.usage.session.output", prefixBindPath))),
					usageRow("Session tokens reported for usage", ucx.TextBound(fmt.Sprintf("%s.usage.session.reported", prefixBindPath))),
					ucx.DividerNode(),
					usageRow("Latest input tokens", ucx.TextBound(fmt.Sprintf("%s.usage.lastQuery.input", prefixBindPath))),
					usageRow("Latest output tokens", ucx.TextBound(fmt.Sprintf("%s.usage.lastQuery.output", prefixBindPath))),
					usageRow("Latest tokens reported for usage", ucx.TextBound(fmt.Sprintf("%s.usage.lastQuery.reported", prefixBindPath))),
				),
		)
}

func usageRow(label string, value ucx.UiNode) ucx.UiNode {
	return ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 8}).Sx(ucx.SxJustifySpaceBetween, ucx.SxAlignItemsCenter).Children(
		ucx.Text(label),
		value.Sx(ucx.SxTextAlignRight),
	)
}

func (app *InferencePlaygroundApp) walletOwner() apm.WalletOwner {
	if app.Owner.Project.Present {
		return apm.WalletOwnerProject(app.Owner.Project.Value)
	}
	return apm.WalletOwnerUser(app.Owner.CreatedBy)
}

func curlJSONCommand(url string, payload any, streaming bool) string {
	body, _ := json.MarshalIndent(payload, "", "  ")
	parts := []string{
		fmt.Sprintf("curl %s \\", url),
		"  -H 'Content-Type: application/json' \\",
		"  -H 'Authorization: Bearer uci-...' \\",
	}
	if streaming {
		parts = append(parts, "  --no-buffer \\")
	}
	parts = append(parts,
		"  --data-binary @- <<'EOF'",
		string(body),
		"EOF",
	)
	return strings.Join(parts, "\n")
}
