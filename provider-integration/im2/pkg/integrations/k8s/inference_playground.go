package k8s

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"mime"
	"path/filepath"
	"slices"
	"sort"
	"strings"
	"sync"
	"time"

	"golang.org/x/sys/unix"
	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/integrations/k8s/shared"
	apm "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/ucx/ucxsvc"
	"ucloud.dk/shared/pkg/util"
)

// App state and initialization
// =====================================================================================================================

const (
	playgroundModeChat            = "Chat"
	playgroundModeTranscription   = "Transcription"
	playgroundModeImageGeneration = "ImageGeneration"
	playgroundGlobalSystemPrompt  = "You are a helpful assistant."
)

type InferencePlaygroundApp struct {
	mu             sync.Mutex   `ucx:"-"`
	session        *ucx.Session `ucx:"-"`
	flusherStarted bool         `ucx:"-"`

	Owner     orcapi.ResourceOwner `ucx:"-"`
	SessionId string               `ucx:"-"`

	Route     string
	Developer bool

	Models           []InferenceModel
	Threads          []playgroundChatThread
	DeletedThreadIds []string `ucx:"-"`
	CurrentThreadId  string

	Chat          InferencePlaygroundAppChat
	Transcription InferencePlaygroundTranscription
	Image         InferencePlaygroundImageGeneration
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

type InferencePlaygroundTranscription struct {
	ModelId   string
	FilePath  string
	Prompt    string
	Language  string
	Streaming bool
	Format    string
	Loading   bool
	Usage     InferencePlaygroundTokenUsageState
	Output    string
	Curl      string
}

type InferencePlaygroundImageGeneration struct {
	ModelId        string
	Prompt         string
	Streaming      bool
	Count          int64
	Size           string
	ResponseFormat string
	Quality        string
	Style          string
	Background     string
	Loading        bool
	Usage          InferencePlaygroundTokenUsageState
	Output         string
	Curl           string
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
		Transcription: InferencePlaygroundTranscription{
			Format:    string(InferenceTranscriptionRespJson),
			Streaming: false,
			Output:    "You have not requested anything yet.",
		},
		Image: InferencePlaygroundImageGeneration{
			Streaming:      false,
			Count:          1,
			Size:           "1024x1024",
			ResponseFormat: "url",
			Quality:        "auto",
			Style:          "vivid",
			Background:     "auto",
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
	Role        string
	Content     string
	GeneratedAt int64
}

type playgroundChatThread struct {
	Id        string
	Title     string
	CreatedAt int64
	UpdatedAt int64
	Messages  []playgroundChatMessage `ucx:"-"`
	Dirty     bool                    `ucx:"-"`
	Deleted   bool                    `ucx:"-"`
}

// App (global) event handlers and init
// =====================================================================================================================

func (app *InferencePlaygroundApp) Mutex() *sync.Mutex     { return &app.mu }
func (app *InferencePlaygroundApp) Session() **ucx.Session { return &app.session }

func (app *InferencePlaygroundApp) OnInit() {
	app.refreshModels()
	app.loadThreads()
	if !app.Developer {
		app.createThread()
	}
	app.startThreadFlusher()

	app.Chat.ModelId = app.firstModelFor(InferenceTextGeneration)
	app.applyChatModelDefaults()
	app.Transcription.ModelId = app.firstModelFor(InferenceSpeechToText)
	app.Image.ModelId = app.firstModelFor(InferenceTextToImage)

	app.Chat.Curl = app.buildChatCurl()
	app.Transcription.Curl = app.buildTranscriptionCurl()
	app.Image.Curl = app.buildImageCurl()
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
		}
		return
	}
	if message.Opcode == ucx.OpModelInput {
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
		app.Transcription.Curl = app.buildTranscriptionCurl()
		app.Image.Curl = app.buildImageCurl()
	}
}

// App user-interface and core data management
// =====================================================================================================================

func (app *InferencePlaygroundApp) tabWrapper(child ucx.UiNode) ucx.UiNode {
	return ucx.Flex(ucx.FlexProps{}).Sx(
		ucx.SxHeightRaw("calc(100vh - 174px)"),
		ucx.SxWidthPercent(100),
		ucx.SxMt(16),
	).Children(child)
}

func (app *InferencePlaygroundApp) UserInterface() ucx.UiNode {
	modes := app.availableModes()
	if len(modes) == 0 {
		modes = []string{playgroundModeChat, playgroundModeTranscription, playgroundModeImageGeneration}
	}

	tabs := make([]ucx.UiNode, 0, len(modes))
	for _, mode := range modes {
		switch mode {
		case playgroundModeChat:
			tabs = append(tabs, ucx.Tab("Chat", ucx.IconHeroChatBubbleLeftRight).Children(app.tabWrapper(app.chatTab())))
		case playgroundModeTranscription:
			tabs = append(tabs, ucx.Tab("Transcription", ucx.IconHeroMicrophone).Children(app.tabWrapper(app.transcriptionTab())))
		case playgroundModeImageGeneration:
			tabs = append(tabs, ucx.Tab("Image generation", ucx.IconHeroPhoto).Children(app.tabWrapper(app.imageTab())))
		}
	}

	return ucx.Box().
		Sx(
			ucx.SxHeightRaw("calc(100vh - 96px)"),
			ucx.SxMinHeight(0),
			ucx.SxOverflow("hidden"),
			ucx.SxFlexDirectionColumn,
			ucx.SxDisplayFlex,
		).
		Children(
			ucx.Router("route"),

			ucx.
				TabsWithRoute(true).
				Sx(
					ucx.SxFlexGrow(1),
					ucx.SxMinHeight(0),
					ucx.SxOverflow("hidden"),
				).
				Children(append(tabs, ucx.TabsRightControls(
					InferenceToggleInput("developerMode", "Developer", "developer", true),
				))...),
		)
}

func (app *InferencePlaygroundApp) refreshModels() {
	resp := InferenceModelListForOwner(app.walletOwner())
	app.Models = resp
}

func (app *InferencePlaygroundApp) threadOwner() string {
	return strings.TrimSpace(app.Owner.CreatedBy)
}

func (app *InferencePlaygroundApp) loadThreads() {
	owner := app.threadOwner()
	if owner == "" {
		return
	}
	app.Threads = inferencePlaygroundThreadsLoad(owner)
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
	if app.Developer || app.threadOwner() == "" {
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
	app.Chat.Messages = nil
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
		return
	}
	thread.Messages = slices.Clone(app.Chat.Messages)
	thread.UpdatedAt = time.Now().UnixMilli()
	thread.Dirty = true
	if thread.Title == "New thread" {
		for _, msg := range thread.Messages {
			if msg.Role == "user" {
				thread.Title = playgroundThreadTitle(msg.Content)
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
	app.sortThreads()
}

func (app *InferencePlaygroundApp) deleteThread(id string) {
	app.DeletedThreadIds = append(app.DeletedThreadIds, id)
	app.Threads = slices.DeleteFunc(app.Threads, func(thread playgroundChatThread) bool {
		return thread.Id == id
	})
	if app.CurrentThreadId == id {
		app.CurrentThreadId = ""
		app.Chat.Messages = nil
		app.ensureCurrentThread()
	}
}

func (app *InferencePlaygroundApp) sortThreads() {
	sort.SliceStable(app.Threads, func(i, j int) bool {
		return app.Threads[i].UpdatedAt > app.Threads[j].UpdatedAt
	})
}

func (app *InferencePlaygroundApp) flushThreadsLocked() {
	owner := app.threadOwner()
	if owner == "" {
		return
	}
	if inferencePlaygroundThreadsFlush(owner, app.Threads, app.DeletedThreadIds) {
		for i := range app.Threads {
			app.Threads[i].Dirty = false
		}
		app.DeletedThreadIds = nil
	}
}

func playgroundThreadTitle(prompt string) string {
	prompt = strings.TrimSpace(prompt)
	if prompt == "" {
		return "New thread"
	}
	runes := []rune(prompt)
	if len(runes) > 15 {
		runes = runes[:15]
	}
	return string(runes)
}

type inferencePlaygroundThreadRow struct {
	Id        string
	Title     string
	CreatedAt time.Time
	UpdatedAt time.Time
}

type inferencePlaygroundMessageRow struct {
	ThreadId    string
	Role        string
	Content     string
	GeneratedAt time.Time
}

func inferencePlaygroundThreadsLoad(owner string) []playgroundChatThread {
	return db.NewTx(func(tx *db.Transaction) []playgroundChatThread {
		threadRows := db.Select[inferencePlaygroundThreadRow](
			tx,
			`
				select id, title, created_at, updated_at
				from inference_playground_thread
				where owner_username = :owner
				order by updated_at desc
			`,
			db.Params{"owner": owner},
		)
		messageRows := db.Select[inferencePlaygroundMessageRow](
			tx,
			`
				select m.thread_id, m.role, m.content, m.generated_at
				from inference_playground_message m
				join inference_playground_thread t on t.id = m.thread_id
				where t.owner_username = :owner
				order by m.thread_id, m.message_index
			`,
			db.Params{"owner": owner},
		)

		messagesByThread := map[string][]playgroundChatMessage{}
		for _, row := range messageRows {
			messagesByThread[row.ThreadId] = append(messagesByThread[row.ThreadId], playgroundChatMessage{
				Role:        row.Role,
				Content:     row.Content,
				GeneratedAt: row.GeneratedAt.UnixMilli(),
			})
		}

		threads := make([]playgroundChatThread, 0, len(threadRows))
		for _, row := range threadRows {
			threads = append(threads, playgroundChatThread{
				Id:        row.Id,
				Title:     row.Title,
				CreatedAt: row.CreatedAt.UnixMilli(),
				UpdatedAt: row.UpdatedAt.UnixMilli(),
				Messages:  messagesByThread[row.Id],
			})
		}
		return threads
	})
}

func inferencePlaygroundThreadsFlush(owner string, threads []playgroundChatThread, deletedThreadIds []string) bool {
	dirty := len(deletedThreadIds) > 0
	for _, thread := range threads {
		if thread.Dirty {
			dirty = true
			break
		}
	}
	if !dirty {
		return true
	}

	db.NewTx0(func(tx *db.Transaction) {
		for _, id := range deletedThreadIds {
			db.Exec(
				tx,
				`delete from inference_playground_thread where owner_username = :owner and id = :id`,
				db.Params{"owner": owner, "id": id},
			)
		}

		for _, thread := range threads {
			if !thread.Dirty {
				continue
			}
			if len(thread.Messages) == 0 {
				continue
			}
			createdAt := time.UnixMilli(thread.CreatedAt)
			updatedAt := time.UnixMilli(thread.UpdatedAt)
			db.Exec(
				tx,
				`
					insert into inference_playground_thread(id, owner_username, title, created_at, updated_at)
					values (:id, :owner, :title, :created_at, :updated_at)
					on conflict (id) do update set
						title = excluded.title,
						updated_at = excluded.updated_at
					where inference_playground_thread.owner_username = :owner
				`,
				db.Params{
					"id":         thread.Id,
					"owner":      owner,
					"title":      thread.Title,
					"created_at": createdAt,
					"updated_at": updatedAt,
				},
			)
			db.Exec(
				tx,
				`delete from inference_playground_message where thread_id = :thread_id`,
				db.Params{"thread_id": thread.Id},
			)
			for idx, msg := range thread.Messages {
				generatedAt := time.UnixMilli(msg.GeneratedAt)
				if msg.GeneratedAt == 0 {
					generatedAt = time.Now()
				}
				db.Exec(
					tx,
					`
						insert into inference_playground_message(thread_id, message_index, role, content, generated_at)
						values (:thread_id, :message_index, :role, :content, :generated_at)
					`,
					db.Params{
						"thread_id":     thread.Id,
						"message_index": idx,
						"role":          msg.Role,
						"content":       msg.Content,
						"generated_at":  generatedAt,
					},
				)
			}
		}
	})
	return true
}

func (app *InferencePlaygroundApp) availableModes() []string {
	if len(app.Models) == 0 {
		return []string{playgroundModeChat, playgroundModeTranscription, playgroundModeImageGeneration}
	}

	var modes []string
	if len(app.modelOptionsFor(InferenceTextGeneration)) > 0 {
		modes = append(modes, playgroundModeChat)
	}
	if len(app.modelOptionsFor(InferenceSpeechToText)) > 0 {
		modes = append(modes, playgroundModeTranscription)
	}
	if len(app.modelOptionsFor(InferenceTextToImage)) > 0 {
		modes = append(modes, playgroundModeImageGeneration)
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
	chatControls := []ucx.UiNode{
		ucx.Select("chatModel", "Model", "chat.modelId", app.modelOptionsFor(InferenceTextGeneration)).Sx(ucx.SxWidthPercent(100)),
	}
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
			ucx.Button("newThread", "New thread", ucx.ColorSecondaryMain),
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
		).Children(
			inferenceChatBox().Sx(
				ucx.SxFlexGrow(1),
				ucx.SxMinHeight(0),
				ucx.SxOverflowY("auto"),
				ucx.SxP(16),
				ucx.SxBorderRadius(8),
				ucx.SxBorderColor(ucx.ColorBorderColor),
				ucx.SxBorderWidth(1),
				ucx.SxBorderSolid,
			).Children(
				ucx.List("chat.messages", "No messages yet.").Children(
					ucx.Flex(ucx.FlexProps{Direction: "column", Gap: 4}).Children(
						ucx.TextBoundEx("role", "./role").Sx(ucx.SxColor(ucx.ColorTextSecondary)),
						ucx.MarkdownBound("./content"),
					),
				),
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
				"Ask something",
				3,
				"heroPaperAirplane",
				app.Chat.Loading,
			).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
				app.Chat.Prompt = ev.Value.String
				if !app.Chat.Loading {
					app.runChat()
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
		).Children(
			chatControls...,
		),
	)
}

func (app *InferencePlaygroundApp) runChat() {
	prompt := strings.TrimSpace(app.Chat.Prompt)

	app.Chat.Loading = true
	ucx.AppUpdateUi(app)
	defer func() {
		app.Chat.Loading = false
		ucx.AppUpdateUi(app)
	}()

	request := InferenceChatRequest{
		Model:               app.Chat.ModelId,
		Stream:              app.Chat.Streaming,
		Messages:            app.chatRequestMessages(prompt),
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
		now := time.Now().UnixMilli()
		app.Chat.Messages = append(app.Chat.Messages, playgroundChatMessage{Role: "user", Content: prompt, GeneratedAt: now})
		app.Chat.Messages = append(app.Chat.Messages, playgroundChatMessage{Role: "assistant", Content: "", GeneratedAt: now})
		assistantIndex := len(app.Chat.Messages) - 1
		app.Chat.Prompt = ""
		app.Chat.Usage.LastQuery = InferencePlaygroundTokenUsage{}
		app.markCurrentThreadDirty()
		ucx.AppUpdateModel(app)

		assistant := ""
		if app.Chat.Streaming {
			var builder strings.Builder
			usageSeen := InferenceChatUsage{}
			chunks, err := InferenceChatStreaming(app.walletOwner(), request)
			if err != nil {
				assistant = err.Why
				ucxsvc.UiSendFailure(app, fmt.Sprintf("Chat failed: %s", err))
			} else {
				for chunk := range chunks {
					usageSeen = chunk.Usage
					if len(chunk.Choices) == 0 {
						continue
					}
					delta := chunk.Choices[0].Delta.Content
					if delta == "" {
						continue
					}
					builder.WriteString(delta)
					app.Chat.Messages[assistantIndex].Content = builder.String()
					app.markCurrentThreadDirty()
					ucx.AppUpdateModel(app)
				}
				assistant = strings.TrimSpace(builder.String())
				app.applyChatUsage(usageSeen)
			}
		} else {
			resp, err := InferenceChat(app.walletOwner(), request)
			if err != nil {
				assistant = err.Why
				ucxsvc.UiSendFailure(app, fmt.Sprintf("Chat failed: %s", err))
			} else {
				app.applyChatUsage(resp.Usage)
				if len(resp.Choices) > 0 {
					assistant = resp.Choices[0].Message.Content.String()
				}
			}
		}

		if assistant == "" {
			assistant = "(no response)"
		}

		app.Chat.Messages[assistantIndex].Content = assistant
		app.Chat.Curl = app.buildChatCurl()
		app.Chat.Prompt = ""
		app.markCurrentThreadDirty()
	}
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
	ucx.AppUpdateModel(app)
}

func (app *InferencePlaygroundApp) chatRequestMessages(prompt string) []InferenceChatMessage {
	messages := make([]InferenceChatMessage, 0, len(app.Chat.Messages)+2)
	if strings.TrimSpace(app.Chat.SystemPrompt) != "" {
		messages = append(messages, InferenceChatMessage{Role: "system", Content: inferenceChatTextContent(app.Chat.SystemPrompt)})
	}
	for _, msg := range app.Chat.Messages {
		messages = append(messages, InferenceChatMessage{Role: msg.Role, Content: inferenceChatTextContent(msg.Content)})
	}
	messages = append(messages, InferenceChatMessage{Role: "user", Content: inferenceChatTextContent(prompt)})
	return messages
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

func InferenceChatComposerNode(bindPath string, placeholder string, rows int64, sendIcon string, disabled bool) ucx.UiNode {
	return ucx.UiNode{
		Id:         "chatComposer",
		Component:  "inference_chat_composer",
		BindPath:   bindPath,
		Optimistic: true,
		Props: map[string]ucx.Value{
			"placeholder": ucx.VString(placeholder),
			"rows":        ucx.VS64(rows),
			"sendIcon":    ucx.VString(sendIcon),
			"disabled":    ucx.VBool(disabled),
		},
	}
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

// Transcription interface
// =====================================================================================================================

func (app *InferencePlaygroundApp) transcriptionTab() ucx.UiNode {
	return ucx.Box().
		Sx(
			ucx.SxAlignSelf("stretch"),
			ucx.SxFlexDirectionRow,
			ucx.SxDisplayFlex,
			ucx.SxGap(32),
			ucx.SxWidthPercent(100),
		).
		Children(
			ucx.Box().
				Sx(
					ucx.SxFlexGrow(1),
					ucx.SxMinWidth(0),
					ucx.SxMinHeight(0),
					ucx.SxOverflow("hidden"),
					ucx.SxGap(16),
					ucx.SxFlexDirectionColumn,
					ucx.SxDisplayFlex,
				).
				Children(
					func() ucx.UiNode {
						if app.Transcription.Loading {
							return ucx.Box().Sx(ucx.SxFlexGrow(1)).Children(ucx.Spinner(28))
						}
						return InferenceTranscriptionOutputNode("transcription.output").Sx(
							ucx.SxFlexGrow(1),
							ucx.SxMinHeight(0),
							ucx.SxOverflowY("auto"),
							ucx.SxP(16),
							ucx.SxBorderRadius(8),
							ucx.SxBorderColor(ucx.ColorBorderColor),
							ucx.SxBorderWidth(1),
							ucx.SxBorderSolid,
						)
					}(),
					InferenceTranscriptionComposerNode(
						"transcription.filePath",
						"transcription.prompt",
						"Audio file path",
						"Optional prompt",
						4,
						"heroPaperAirplane",
						app.Transcription.Loading,
					).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
						if !app.Transcription.Loading {
							app.runTranscription()
							ucx.AppUpdateModel(app)
						}
					}),
				),
			ucx.Box().
				Sx(
					ucx.SxWidth(320),
					ucx.SxFlexShrink(0),
					ucx.SxMinHeight(0),
					ucx.SxOverflowY("auto"),
					ucx.SxDisplayFlex,
					ucx.SxFlexDirectionColumn,
					ucx.SxGap(16),
				).
				Children(
					ucx.Select(
						"transcriptionModel",
						"Model",
						"transcription.modelId",
						app.modelOptionsFor(InferenceSpeechToText),
					).Sx(
						ucx.SxWidthPercent(100),
					),

					ucx.AccordionNode("Settings", true).Children(ucx.Box().Sx(ucx.SxDisplayFlex, ucx.SxFlexDirectionColumn, ucx.SxGap(8)).Children(
						InferenceToggleInput("transcription.streaming", "Streaming", "transcription.streaming", true),
						ucx.InputText("transcription.language", "Language", "en", "transcription.language"),
						ucx.Select("transcription.format", "Response format", "transcription.format", []ucx.Option{
							{Key: string(InferenceTranscriptionRespJson), Value: string(InferenceTranscriptionRespJson)},
							{Key: string(InferenceTranscriptionRespDiarizedJson), Value: string(InferenceTranscriptionRespDiarizedJson)},
							{Key: string(InferenceTranscriptionRespVerboseJson), Value: string(InferenceTranscriptionRespVerboseJson)},
						}),
					)),

					app.usageBox("transcription"),

					ucx.AccordionNode("Curl", false).Children(
						ucx.CodeBound("transcription.curl"),
					),
				),
		)
}

func (app *InferencePlaygroundApp) runTranscription() {
	app.Transcription.Loading = true
	app.Transcription.Usage.LastQuery.Input = 0
	app.Transcription.Usage.LastQuery.Output = 0
	app.Transcription.Usage.LastQuery.Reported = 0
	ucx.AppUpdateUi(app)
	defer func() {
		app.Transcription.Loading = false
		ucx.AppUpdateUi(app)
	}()

	file, err := app.readTranscriptionFile()
	if err != nil {
		app.Transcription.Output = err.Error()
		ucxsvc.UiSendFailure(app, fmt.Sprintf("Transcription failed: %s", err))
		return
	}

	request := InferenceTranscriptionRequest{
		File:                   file,
		Model:                  app.Transcription.ModelId,
		Stream:                 app.Transcription.Streaming,
		ResponseFormat:         InferenceTranscriptionResponseFormat(app.Transcription.Format),
		TimestampGranularities: []string{},
	}
	if strings.TrimSpace(app.Transcription.Prompt) != "" {
		request.Prompt = util.OptValue(app.Transcription.Prompt)
	}
	if strings.TrimSpace(app.Transcription.Language) != "" {
		request.Language = util.OptValue(app.Transcription.Language)
	}
	if app.Transcription.Streaming {
		request.Stream = true
	}

	transcriptionText := func(text string) string {
		text = strings.TrimSpace(text)
		if text == "" {
			return "(no response)"
		} else {
			return text
		}
	}

	if app.Transcription.Streaming {
		var builder strings.Builder
		usageSeen := InferenceTranscriptionUsage{}
		events, err := InferenceTranscribeStreaming(app.walletOwner(), request)
		if err != nil {
			app.Transcription.Output = err.Why
			ucxsvc.UiSendFailure(app, fmt.Sprintf("Transcription failed: %s", err))
			return
		}
		for event := range events {
			usageSeen = event.Usage
			if event.Delta != "" {
				builder.WriteString(event.Delta)
				app.Transcription.Output = builder.String()
				ucx.AppUpdateModel(app)
			}
			if event.Text != "" {
				builder.Reset()
				builder.WriteString(event.Text)
				app.Transcription.Output = builder.String()
				ucx.AppUpdateModel(app)
			}
		}

		app.Transcription.Output = transcriptionText(builder.String())
		app.applyTranscriptionUsage(request, usageSeen, app.Transcription.Output)
		ucx.AppUpdateModel(app)
	} else {
		resp, err := InferenceTranscribe(app.walletOwner(), request)
		if err != nil {
			app.Transcription.Output = err.Why
			ucxsvc.UiSendFailure(app, fmt.Sprintf("Transcription failed: %s", err))
			return
		}

		if resp.VerboseJson != nil {
			app.Transcription.Output = transcriptionText(resp.VerboseJson.Text)
			app.applyTranscriptionUsageFromResponse(request, resp.VerboseJson.Usage, resp.VerboseJson.Text)
		} else if resp.DiarizedJson != nil {
			app.Transcription.Output = transcriptionText(resp.DiarizedJson.Text)
			app.applyTranscriptionUsageFromResponse(request, resp.DiarizedJson.Usage, resp.DiarizedJson.Text)
		} else if resp.Json != nil {
			app.Transcription.Output = transcriptionText(resp.Json.Text)
			app.applyTranscriptionUsageFromResponse(request, resp.Json.Usage, resp.Json.Text)
		}
		ucx.AppUpdateModel(app)
	}

	app.Transcription.Curl = app.buildTranscriptionCurl()
}

func (app *InferencePlaygroundApp) applyTranscriptionUsage(request InferenceTranscriptionRequest, usage InferenceTranscriptionUsage, text string) {
	inputTokens := int64(usage.InputTokens)
	outputTokens := int64(usage.OutputTokens)
	reportedTokens := int64(usage.TotalTokens)
	if reportedTokens == 0 {
		reportedTokens = inputTokens + outputTokens
	}
	app.Transcription.Usage.LastQuery.Input = inputTokens
	app.Transcription.Usage.LastQuery.Output = outputTokens
	app.Transcription.Usage.LastQuery.Reported = reportedTokens
	app.Transcription.Usage.Session.Input += inputTokens
	app.Transcription.Usage.Session.Output += outputTokens
	app.Transcription.Usage.Session.Reported += reportedTokens
}

func (app *InferencePlaygroundApp) applyTranscriptionUsageFromResponse(request InferenceTranscriptionRequest, usage InferenceTranscriptionUsage, text string) {
	app.applyTranscriptionUsage(request, usage, text)
}

func (app *InferencePlaygroundApp) readTranscriptionFile() (InferenceTranscriptionFile, error) {
	path := strings.TrimSpace(app.Transcription.FilePath)
	if path == "" {
		return InferenceTranscriptionFile{}, fmt.Errorf("audio file path is empty")
	}

	internalPath, ok, drive := filesystem.UCloudToInternal(path)
	if !ok {
		return InferenceTranscriptionFile{}, fmt.Errorf("forbidden")
	}

	if !ctrl.ResourceCanUse(app.Owner, drive.Owner, drive.Permissions, true) {
		return InferenceTranscriptionFile{}, fmt.Errorf("forbidden")
	}

	fd, ok := filesystem.OpenFile(internalPath, unix.O_RDONLY, 0)
	if !ok {
		return InferenceTranscriptionFile{}, fmt.Errorf("audio file path does not exist")
	}
	defer util.SilentClose(fd)

	data, err := io.ReadAll(fd)

	if err != nil {
		return InferenceTranscriptionFile{}, err
	}

	contentType := mime.TypeByExtension(filepath.Ext(path))
	if contentType == "" {
		contentType = "application/octet-stream"
	}

	return InferenceTranscriptionFile{
		Name:        filepath.Base(path),
		ContentType: contentType,
		Data:        data,
	}, nil
}

func (app *InferencePlaygroundApp) buildTranscriptionCurl() string {
	parts := []string{
		fmt.Sprintf("curl %s \\", inferenceServerBase()+"/audio/transcriptions"),
		"  -H 'Authorization: Bearer uci-...' \\",
		fmt.Sprintf("  -F 'file=@%s' \\", orcapi.EscapeBash(strings.TrimSpace(app.Transcription.FilePath))),
	}
	if strings.TrimSpace(app.Transcription.ModelId) != "" {
		parts = append(parts, fmt.Sprintf("  -F model=%s \\", orcapi.EscapeBash(app.Transcription.ModelId)))
	}
	if strings.TrimSpace(app.Transcription.Prompt) != "" {
		parts = append(parts, fmt.Sprintf("  -F prompt=%s \\", orcapi.EscapeBash(app.Transcription.Prompt)))
	}
	if strings.TrimSpace(app.Transcription.Language) != "" {
		parts = append(parts, fmt.Sprintf("  -F language=%s \\", orcapi.EscapeBash(app.Transcription.Language)))
	}
	if strings.TrimSpace(app.Transcription.Format) != "" {
		parts = append(parts, fmt.Sprintf("  -F response_format=%s \\", orcapi.EscapeBash(app.Transcription.Format)))
	}
	if app.Transcription.Streaming {
		parts = append(parts, "  -F stream=true \\")
	}
	parts = append(parts, "  --no-buffer")
	return strings.Join(parts, "\n")
}

func InferenceTranscriptionComposerNode(filePathBindPath string, promptBindPath string, filePathPlaceholder string, promptPlaceholder string, rows int64, sendIcon string, disabled bool) ucx.UiNode {
	return ucx.UiNode{
		Id:         "transcriptionComposer",
		Component:  "inference_transcription_composer",
		Optimistic: true,
		Props: map[string]ucx.Value{
			"filePathBindPath":    ucx.VString(filePathBindPath),
			"promptBindPath":      ucx.VString(promptBindPath),
			"filePathPlaceholder": ucx.VString(filePathPlaceholder),
			"promptPlaceholder":   ucx.VString(promptPlaceholder),
			"rows":                ucx.VS64(rows),
			"sendIcon":            ucx.VString(sendIcon),
			"disabled":            ucx.VBool(disabled),
		},
	}
}

func InferenceTranscriptionOutputNode(bindPath string) ucx.UiNode {
	return ucx.UiNode{
		Component: "inference_chat_box",
		ChildNodes: []ucx.UiNode{
			ucx.TextBound(bindPath).Sx(ucx.SxWhiteSpace("pre-wrap")),
		},
	}
}

// Image generation interface
// =====================================================================================================================

func (app *InferencePlaygroundApp) imageTab() ucx.UiNode {
	return ucx.Box().
		Sx(
			ucx.SxAlignSelf("stretch"),
			ucx.SxFlexDirectionRow,
			ucx.SxDisplayFlex,
			ucx.SxGap(32),
			ucx.SxWidthPercent(100),
		).
		Children(
			ucx.Box().
				Sx(
					ucx.SxFlexGrow(1),
					ucx.SxMinWidth(0),
					ucx.SxMinHeight(0),
					ucx.SxOverflow("hidden"),
					ucx.SxFlexDirectionColumn,
					ucx.SxDisplayFlex,
					ucx.SxGap(16),
				).
				Children(
					func() ucx.UiNode {
						if app.Image.Loading {
							return ucx.Box().Sx(ucx.SxFlexGrow(1)).Children(
								ucx.Spinner(28),
							)
						} else {
							return InferenceImagePreviewNode("image.output").Sx(
								ucx.SxFlexGrow(1),
								ucx.SxMinHeight(0),
								ucx.SxOverflowY("auto"),
								ucx.SxP(16),
								ucx.SxBorderRadius(8),
								ucx.SxBorderColor(ucx.ColorBorderColor),
								ucx.SxBorderWidth(1),
								ucx.SxBorderSolid,
							)
						}
					}(),
					InferenceImageComposerNode(
						"image.prompt",
						"Describe the image to generate",
						4,
						"heroPaperAirplane",
						app.Image.Loading,
					).On(ucx.UiEventClick, func(ev ucx.UiEvent) {
						app.Image.Prompt = ev.Value.String
						if !app.Image.Loading {
							app.runImageGeneration()
							ucx.AppUpdateModel(app)
						}
					}),
				),

			ucx.Box().
				Sx(
					ucx.SxWidth(320),
					ucx.SxFlexShrink(0),
					ucx.SxMinHeight(0),
					ucx.SxOverflowY("auto"),
					ucx.SxDisplayFlex,
					ucx.SxFlexDirectionColumn,
					ucx.SxGap(16),
				).
				Children(
					ucx.Select(
						"imageModel",
						"Model",
						"image.modelId",
						app.modelOptionsFor(InferenceTextToImage),
					).Sx(
						ucx.SxWidthPercent(100),
					),

					ucx.AccordionNode("Settings", true).Children(
						ucx.Box().Sx(ucx.SxDisplayFlex, ucx.SxFlexDirectionColumn, ucx.SxGap(8)).Children(
							InferenceToggleInput("image.streaming", "Streaming", "image.streaming", true),
							ucx.InputNumber("image.count", "Images", "image.count", 1, 10),
							ucx.Select("image.size", "Size", "image.size", []ucx.Option{
								{Key: "1024x1024", Value: "1024x1024"},
								{Key: "1536x1024", Value: "1536x1024"},
								{Key: "1024x1536", Value: "1024x1536"},
								{Key: "256x256", Value: "256x256"},
								{Key: "512x512", Value: "512x512"},
								{Key: "1792x1024", Value: "1792x1024"},
								{Key: "1024x1792", Value: "1024x1792"},
								{Key: "auto", Value: "auto"},
							}),
						),
					),

					app.usageBox("image"),

					ucx.AccordionNode("Advanced settings", false).Children(
						ucx.Box().Sx(ucx.SxDisplayFlex, ucx.SxFlexDirectionColumn, ucx.SxGap(8)).Children(
							ucx.Select("image.responseFormat", "Response format", "image.responseFormat", []ucx.Option{
								{Key: "url", Value: "url"},
								{Key: "b64_json", Value: "b64_json"},
							}),
							ucx.Select("image.quality", "Quality", "image.quality", []ucx.Option{
								{Key: "auto", Value: "auto"},
								{Key: "low", Value: "low"},
								{Key: "medium", Value: "medium"},
								{Key: "high", Value: "high"},
							}),
							ucx.Select("image.style", "Style", "image.style", []ucx.Option{
								{Key: "vivid", Value: "vivid"},
								{Key: "natural", Value: "natural"},
							}),
							ucx.Select("image.background", "Background", "image.background", []ucx.Option{
								{Key: "auto", Value: "auto"},
								{Key: "opaque", Value: "opaque"},
								{Key: "transparent", Value: "transparent"},
							}),
						),
					),

					ucx.AccordionNode("Curl", false).Children(
						ucx.CodeBound("image.curl"),
					),
				),
		)
}

func (app *InferencePlaygroundApp) runImageGeneration() {
	app.Image.Loading = true
	app.Image.Usage.LastQuery = InferencePlaygroundTokenUsage{}

	ucx.AppUpdateUi(app)
	defer func() {
		app.Image.Loading = false
		ucx.AppUpdateUi(app)
	}()

	if app.Image.Count <= 0 {
		app.Image.Count = 1
	}

	request := InferenceImageGenerationRequest{
		Prompt:         app.Image.Prompt,
		Model:          util.OptValue(app.Image.ModelId),
		Size:           util.OptValue(app.Image.Size),
		ResponseFormat: util.OptValue(app.Image.ResponseFormat),
		Quality:        util.OptValue(app.Image.Quality),
		Style:          util.OptValue(app.Image.Style),
		Background:     util.OptValue(app.Image.Background),
		Stream:         util.OptValue(app.Image.Streaming),
		N:              util.OptValue(int(app.Image.Count)),
	}

	if app.Image.Streaming {
		last := InferenceImageGenerationStreamEvent{Usage: inferenceImageUsageFromPayload(request, 0, util.OptNone[InferenceImageGenerationUsage]())}
		events, err := InferenceGenerateImageStreaming(app.walletOwner(), request)
		if err != nil {
			app.Image.Output = err.Why
			ucxsvc.UiSendFailure(app, fmt.Sprintf("Image generation failed: %s", err))
			return
		}
		for event := range events {
			last = event
		}

		if last.B64JSON.Present {
			app.Image.Output = last.B64JSON.Value
		} else {
			app.Image.Output = mustJSON(last)
		}
		app.applyImageUsage(last.Usage)
	} else {
		resp, err := InferenceGenerateImage(app.walletOwner(), request)
		if err != nil {
			app.Image.Output = err.Why
			ucxsvc.UiSendFailure(app, fmt.Sprintf("Image generation failed: %s", err))
			return
		}
		app.Image.Output = mustJSON(resp)
		app.applyImageUsage(resp.Usage)
	}

	app.Image.Curl = app.buildImageCurl()
}

func (app *InferencePlaygroundApp) applyImageUsage(usage InferenceImageGenerationUsage) {
	inputTokens := int64(usage.InputTokens)
	outputTokens := int64(usage.OutputTokens)
	if outputTokens == 0 {
		outputTokens = int64(usage.TotalTokens)
	}
	reportedTokens := int64(usage.TotalTokens)
	if reportedTokens == 0 {
		reportedTokens = inputTokens + outputTokens
	}

	app.Image.Usage.LastQuery.Input = inputTokens
	app.Image.Usage.LastQuery.Output = outputTokens
	app.Image.Usage.LastQuery.Reported = reportedTokens

	app.Image.Usage.Session.Input += inputTokens
	app.Image.Usage.Session.Output += outputTokens
	app.Image.Usage.Session.Reported += reportedTokens
}

func (app *InferencePlaygroundApp) buildImageCurl() string {
	payload := map[string]any{
		"prompt": app.Image.Prompt,
	}
	if strings.TrimSpace(app.Image.ModelId) != "" {
		payload["model"] = app.Image.ModelId
	}
	if app.Image.Count > 0 {
		payload["n"] = app.Image.Count
	}
	if strings.TrimSpace(app.Image.Size) != "" {
		payload["size"] = app.Image.Size
	}
	if strings.TrimSpace(app.Image.ResponseFormat) != "" {
		payload["response_format"] = app.Image.ResponseFormat
	}
	if strings.TrimSpace(app.Image.Quality) != "" {
		payload["quality"] = app.Image.Quality
	}
	if strings.TrimSpace(app.Image.Style) != "" {
		payload["style"] = app.Image.Style
	}
	if strings.TrimSpace(app.Image.Background) != "" {
		payload["background"] = app.Image.Background
	}
	if app.Image.Streaming {
		payload["stream"] = true
	}
	return curlJSONCommand(inferenceServerBase()+"/images/generations", payload, app.Image.Streaming)
}

func InferenceImagePreviewNode(bindPath string) ucx.UiNode {
	return ucx.UiNode{
		Component: "inference_image_preview",
		BindPath:  bindPath,
	}
}

func InferenceImageComposerNode(bindPath string, placeholder string, rows int64, sendIcon string, disabled bool) ucx.UiNode {
	return ucx.UiNode{
		Id:         "imageComposer",
		Component:  "inference_image_composer",
		BindPath:   bindPath,
		Optimistic: true,
		Props: map[string]ucx.Value{
			"placeholder": ucx.VString(placeholder),
			"rows":        ucx.VS64(rows),
			"sendIcon":    ucx.VString(sendIcon),
			"disabled":    ucx.VBool(disabled),
		},
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
	if strings.TrimSpace(app.Owner.CreatedBy) != "" {
		return apm.WalletOwnerUser(app.Owner.CreatedBy)
	}
	return apm.WalletOwnerUser("user")
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

func mustJSON(v any) string {
	body, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return fmt.Sprintf("%#v", v)
	}
	return string(body)
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
