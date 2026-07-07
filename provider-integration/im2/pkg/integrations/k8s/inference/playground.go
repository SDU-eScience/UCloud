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

	Route     string
	Developer bool

	Models             []InferenceModel
	Threads            []playgroundChatThread
	LoadingThreadIds   []string
	DeletedThreadIds   []string `ucx:"-"`
	DeletedThreadPaths []string `ucx:"-"`
	CurrentThreadId    string

	Chat InferencePlaygroundAppChat
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

func InferencePlayground(owner orcapi.ResourceOwner, sessionId string) *InferencePlaygroundApp {
	if !shared.ServiceConfig.Compute.Inference.Enabled {
		return nil
	}

	if owner.CreatedBy == "" {
		return nil
	}

	return &InferencePlaygroundApp{
		Owner:     owner,
		SessionId: sessionId,
		Developer: false,
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
	Input    int64
	Output   int64
	Reported int64
}

type playgroundChatMessage struct {
	Role           string
	Content        string
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
	Messages               []playgroundChatMessage `ucx:"-"`
	Dirty                  bool                    `ucx:"-"`
	Deleted                bool                    `ucx:"-"`
	StoragePath            string                  `ucx:"-"`
	TitleGenerated         bool                    `ucx:"-"`
	TitleGenerationStarted bool                    `ucx:"-"`
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
		if app.Chat.ModelId != app.Chat.AppliedDefaultsModelId {
			app.applyChatModelDefaults()
			ucx.AppUpdateModel(app)
		}
		app.Chat.Curl = app.buildChatCurl()
	}
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
}

func (app *InferencePlaygroundApp) materializeCurrentThread() {
	if app.Developer || app.CurrentThreadId != "" || len(app.Chat.Messages) == 0 {
		return
	}

	now := time.Now().UnixMilli()
	thread := playgroundChatThread{
		Id:        "thread-" + util.SecureToken(),
		Title:     "New thread",
		CreatedAt: now,
		UpdatedAt: now,
		Dirty:     true,
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
	thread.UpdatedAt = time.Now().UnixMilli()
	thread.Dirty = true
	if thread.Title == "New thread" {
		for _, msg := range thread.Messages {
			if msg.Role == "user" && !playgroundMessageIsAttachmentOnly(msg) {
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
	if model.ChatSettings.SystemPrompt != nil {
		app.Chat.SystemPrompt = *model.ChatSettings.SystemPrompt
	} else {
		app.Chat.SystemPrompt = playgroundGlobalSystemPrompt
	}
	app.Chat.AppliedDefaultsModelId = app.Chat.ModelId
}

// Chat interface
// =====================================================================================================================

func (app *InferencePlaygroundApp) chatTab() ucx.UiNode {
	app.prepareChatMessagesForUi()
	chatControls := []ucx.UiNode{}
	if app.Developer {
		chatControls = append(chatControls,
			ucx.AccordionNode("Settings", true).Children(ucx.Box().Sx(ucx.SxDisplayFlex, ucx.SxFlexDirectionColumn, ucx.SxGap(8)).Children(
				InferenceToggleInput("chat.streaming", "Streaming", "chat.streaming", true),
				ucx.InputSlider("Max completion tokens", "chat.maxCompletionTokens", 1, 1024*256, 1024, 1024*64, true),
				ucx.InputSlider("Temperature", "chat.temperature", 0, 2, 0.1, 0.8, true),
				ucx.InputSlider("Top P", "chat.topP", 0, 1, 0.1, 0.1, true),
				ucx.TextArea("chat.systemPrompt", "System prompt", "System prompt", "chat.systemPrompt", 4),
			)),

			app.usageBox("chat"),

			ucx.AccordionNode("Advanced settings", false).Children(ucx.Box().Sx(ucx.SxDisplayFlex, ucx.SxFlexDirectionColumn, ucx.SxGap(8)).Children(
				ucx.InputSlider("Presence penalty", "chat.presencePenalty", -2, 2, 0.1, 0, true),
				ucx.InputSlider("Frequency penalty", "chat.frequencyPenalty", -2, 2, 0.1, 0, true),
				InferenceToggleInput("chat.logprobs", "Logprobs", "chat.logprobs", true),
				ucx.InputSlider("Top log probs", "chat.topLogprobs", 0, 20, 1, 0, true),
			)),

			ucx.AccordionNode("Curl", false).Children(
				ucx.CodeBound("chat.curl"),
			),
		)
	} else {
		chatControls = append(chatControls,
			ucx.ButtonEx("newThread", "New thread", ucx.ColorSecondaryMain, ucx.IconHeroPlus, "", ""),
			InferenceThreadListNode(),
		)
	}

	return ucx.Box().
		Sx(
			ucx.SxDisplayFlex,
			ucx.SxFlexDirectionRow,
			ucx.SxGap(32),
			ucx.SxAlignSelf("stretch"),
			ucx.SxWidthPercent(100),
		).Children(
		ucx.Box().Sx(
			ucx.SxFlexGrow(1),
			ucx.SxMinWidth(0),
			ucx.SxMinHeight(0),
			ucx.SxOverflow("hidden"),
			ucx.SxFlexDirectionColumn,
			ucx.SxDisplayFlex,
			ucx.SxGap(16),
			ucx.SxP(16),
			ucx.SxBorderRadius(18),
			ucx.SxBackground("var(--playground-panel, transparent)"),
		).Children(
			inferenceChatBox().Sx(
				ucx.SxFlexGrow(1),
				ucx.SxMinHeight(0),
				ucx.SxOverflowY("auto"),
				ucx.SxPx(8),
				ucx.SxPy(16),
			).Children(
				ucx.List("chat.messages", "No messages yet.").Children(InferenceChatMessageNode()),
				func() ucx.UiNode {
					if app.Chat.Loading {
						return ucx.Box().Sx(ucx.SxDisplayFlex, ucx.SxGap(8)).Children(
							ucx.Spinner(28),
						)
					}
					return ucx.Box()
				}(),
			),
			InferenceChatComposerNode(
				"chat.prompt",
				"Ask anything",
				3,
				"heroArrowUp",
				app.Chat.Loading,
				app.modelOptionsFor(InferenceTextGeneration),
			).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				prompt, attachments := playgroundChatComposerEvent(ev.Value)
				app.Chat.Prompt = prompt
				if !app.Chat.Loading {
					app.runChat(attachments)
					ucx.AppUpdateModel(app)
				}
			}),
		),

		ucx.Box().Sx(
			ucx.SxWidth(320),
			ucx.SxFlexShrink(0),
			ucx.SxMinHeight(0),
			ucx.SxOverflowY("auto"),
			ucx.SxDisplayFlex,
			ucx.SxFlexDirectionColumn,
			ucx.SxGap(16),
			ucx.SxBorderRadius(16),
			ucx.SxP(16),
			ucx.SxBorderColor(ucx.ColorBorderColor),
			ucx.SxBorderWidth(1),
			ucx.SxBorderSolid,
		).Children(
			chatControls...,
		),
	)
}

func (app *InferencePlaygroundApp) prepareChatMessagesForUi() {
	for i := range app.Chat.Messages {
		app.Chat.Messages[i].MessageIndex = int64(i)
	}
}

func (app *InferencePlaygroundApp) runChat(attachments []playgroundChatAttachment) {
	prompt := strings.TrimSpace(app.Chat.Prompt)
	prompt = playgroundPromptWithTextAttachments(prompt, attachments)

	app.Chat.Loading = true

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
	if app.Chat.Streaming {
		request.StreamOptions = util.OptValue(InferenceChatStreamOptions{IncludeUsage: true})
	}

	if strings.HasPrefix(prompt, "/") {
		ucx.AppUpdateUi(app)
		defer func() {
			app.Chat.Loading = false
			ucx.AppUpdateUi(app)
		}()
		if strings.HasPrefix(prompt, "/python") {
			script := strings.TrimPrefix(prompt, "/python ")
			sandbox, err := shared.TerminalOpen(app.Owner, nil)
			if err == nil {
				app.Chat.Prompt = ""
				ucx.AppUpdateModel(app)

				stdout := &bytes.Buffer{}
				stderr := &bytes.Buffer{}
				cmd := sandbox.Command("/usr/bin/python3", "-c", script)
				cmd.Stdout = stdout
				cmd.Stderr = stderr
				cmd.Run()
				app.Chat.Messages = append(app.Chat.Messages, playgroundChatMessage{
					Role:        "assistant",
					Content:     stdout.String() + stderr.String(),
					GeneratedAt: time.Now().UnixMilli(),
				})
				app.markCurrentThreadDirty()
			}
		}
	} else {
		owner := app.walletOwner()
		now := time.Now().UnixMilli()
		app.Chat.Messages = append(app.Chat.Messages, playgroundAttachmentMessages(attachments, now)...)
		app.Chat.Messages = append(app.Chat.Messages, playgroundChatMessage{Role: "user", Content: prompt, Parts: playgroundChatMessageParts(prompt, "", "", false), GeneratedAt: now})
		app.Chat.Messages = append(app.Chat.Messages, playgroundChatMessage{Role: "assistant", Content: "", Parts: playgroundChatMessageParts("", "", "", false), GeneratedAt: now, ModelName: app.Chat.ModelId, StartedAt: now})
		assistantIndex := len(app.Chat.Messages) - 1
		app.Chat.Prompt = ""
		app.Chat.Usage.LastQuery = InferencePlaygroundTokenUsage{}
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
		if app.Chat.Messages[i].Role == "user" {
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
	if app.Chat.Streaming {
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
		var builder strings.Builder
		var reasoningBuilder strings.Builder
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
			app.updateThreadAssistant(threadId, assistantIndex, builder.String(), reasoningBuilder.String(), "", strings.TrimSpace(builder.String()) == "", request.Model, startedAt, firstTokenAt, 0, int64(usageSeen.CompletionTokens))
			ucx.AppUpdateModel(app)
			app.mu.Unlock()
		}
		chunks, err := InferenceChatStreaming(owner, request)
		if err != nil {
			assistant = err.Why
		} else {
			if util.DevelopmentModeEnabled() {
				for _, delta := range playgroundSyntheticReasoningDeltas(request) {
					publishDelta("", delta)
					time.Sleep(100 * time.Millisecond)
				}
			}
			for chunk := range chunks {
				usageSeen = chunk.Usage
				if len(chunk.Choices) == 0 {
					continue
				}
				contentDelta := chunk.Choices[0].Delta.Content
				reasoningDelta := chunk.Choices[0].Delta.Reasoning
				if contentDelta == "" && reasoningDelta == "" {
					continue
				}
				publishDelta(contentDelta, reasoningDelta)
			}
			assistant = strings.TrimSpace(builder.String())
			reasoning = strings.TrimSpace(reasoningBuilder.String())
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
	app.applyChatUsage(usageSeen)
	ucx.AppUpdateUi(app)
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
		msg.Content = content
		msg.Reasoning = reasoning
		msg.ReasoningTitle = reasoningTitle
		msg.Parts = playgroundChatMessageParts(content, reasoning, reasoningTitle, reasoningOpen)
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
		msg.Content = content
		msg.Reasoning = reasoning
		msg.ReasoningTitle = reasoningTitle
		msg.Parts = playgroundChatMessageParts(content, reasoning, reasoningTitle, reasoningOpen)
		msg.ModelName = modelName
		msg.StartedAt = startedAt
		msg.FirstTokenAt = firstTokenAt
		msg.FinishedAt = finishedAt
		msg.OutputTokens = outputTokens
	}
}

func (app *InferencePlaygroundApp) updateThreadAssistantReasoningTitle(threadId string, assistantIndex int, reasoningTitle string) {
	for i := range app.Threads {
		thread := &app.Threads[i]
		if thread.Id != threadId || assistantIndex < 0 || assistantIndex >= len(thread.Messages) {
			continue
		}
		msg := &thread.Messages[assistantIndex]
		msg.ReasoningTitle = reasoningTitle
		msg.Parts = playgroundChatMessageParts(msg.Content, msg.Reasoning, msg.ReasoningTitle, false)
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
		msg.Parts = playgroundChatMessageParts(msg.Content, msg.Reasoning, msg.ReasoningTitle, false)
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

func (app *InferencePlaygroundApp) applyChatUsage(usage InferenceChatUsage) {
	inputTokens := int64(usage.PromptTokens)
	outputTokens := int64(usage.CompletionTokens)
	reportedTokens := int64(usage.TotalTokens)
	if reportedTokens == 0 {
		reportedTokens = inputTokens + outputTokens
	}

	app.Chat.Usage.LastQuery.Input = inputTokens
	app.Chat.Usage.LastQuery.Output = outputTokens
	app.Chat.Usage.LastQuery.Reported = reportedTokens
	app.Chat.Usage.Session.Input += inputTokens
	app.Chat.Usage.Session.Output += outputTokens
	app.Chat.Usage.Session.Reported += reportedTokens
	app.prepareChatMessagesForUi()
	ucx.AppUpdateModel(app)
}

func (app *InferencePlaygroundApp) chatRequestMessages(prompt string, attachments []playgroundChatAttachment) []InferenceChatMessage {
	messages := make([]InferenceChatMessage, 0, len(app.Chat.Messages)+2)
	if strings.TrimSpace(app.Chat.SystemPrompt) != "" {
		messages = append(messages, InferenceChatMessage{Role: "system", Content: inferenceChatTextContent(app.Chat.SystemPrompt)})
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
	if strings.TrimSpace(app.Chat.SystemPrompt) != "" {
		messages = append(messages, InferenceChatMessage{Role: "system", Content: inferenceChatTextContent(app.Chat.SystemPrompt)})
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
	payload := map[string]any{
		"model":    app.Chat.ModelId,
		"stream":   app.Chat.Streaming,
		"messages": app.chatCurlMessages(),
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
	return curlJSONCommand(inferenceServerBase()+"/chat/completions", payload, app.Chat.Streaming)
}

func (app *InferencePlaygroundApp) chatCurlMessages() []map[string]string {
	messages := make([]map[string]string, 0, len(app.Chat.Messages)+2)
	if strings.TrimSpace(app.Chat.SystemPrompt) != "" {
		messages = append(messages, map[string]string{"role": "system", "content": app.Chat.SystemPrompt})
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

func InferenceChatComposerNode(bindPath string, placeholder string, rows int64, sendIcon string, disabled bool, modelOptions []ucx.Option) ucx.UiNode {
	return ucx.UiNode{
		Id:         "chatComposer",
		Component:  "inference_chat_composer",
		BindPath:   bindPath,
		Optimistic: true,
		Props: map[string]ucx.Value{
			"placeholder":  ucx.VString(placeholder),
			"rows":         ucx.VS64(rows),
			"sendIcon":     ucx.VString(sendIcon),
			"disabled":     ucx.VBool(disabled),
			"modelOptions": inferenceOptionsValue(modelOptions),
		},
	}
}

func inferenceOptionsValue(options []ucx.Option) ucx.Value {
	list := make([]ucx.Value, 0, len(options))
	for _, option := range options {
		list = append(list, ucx.VObject(map[string]ucx.Value{
			"key":   ucx.VString(option.Key),
			"value": ucx.VString(option.Value),
		}))
	}
	return ucx.VList(list)
}

func InferenceChatMessageNode() ucx.UiNode {
	return ucx.UiNode{Component: "inference_chat_message"}
}

func inferenceChatBox() ucx.UiNode {
	return ucx.UiNode{
		Component: "inference_chat_box",
	}
}

func InferenceThreadListNode() ucx.UiNode {
	return ucx.UiNode{
		Component: "inference_thread_list",
		BindPath:  "threads",
	}
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

func InferenceToggleInput(id string, label string, bindPath string, optimistic bool) ucx.UiNode {
	return ucx.UiNode{
		Id:         id,
		Component:  "inference_toggle",
		BindPath:   bindPath,
		Optimistic: optimistic,
		Props: map[string]ucx.Value{
			"label": ucx.VString(label),
		},
	}
}
