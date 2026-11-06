package inference

import (
	"context"
	"encoding/base64"
	"fmt"
	_ "image/gif"
	"io"
	"net"
	"net/http"
	"net/url"
	"path/filepath"
	"slices"
	"strings"
	"time"
	"unicode"

	cfg "ucloud.dk/pkg/config"
	ctrl "ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/integrations/k8s/shared"
	apm "ucloud.dk/shared/pkg/accounting"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

const playgroundImageMaxCalls = 4
const playgroundImageMaxBytes = attachmentMaxFileBytes
const playgroundInlineImageBudget = 12 << 20

type playgroundImageTurnKey struct{}

type playgroundImageTurn struct {
	ThreadId       string
	AssistantIndex int
	ChatModel      string
	StartedAt      int64
	Reference      string
	Calls          int
}

type playgroundImageArgs struct {
	Prompt  string `json:"prompt"`
	ImageId string `json:"image_id"`
	Size    string `json:"size"`
	Model   string `json:"model"`
}

func playgroundImageCommand(prompt string) bool {
	command := strings.FieldsFunc(prompt, unicode.IsSpace)
	return len(command) > 0 && command[0] == "/image"
}

func playgroundImageDefault(owner apm.WalletOwner, capability InferenceCapability) string {
	configured := false
	for _, model := range InferenceModelList() {
		if slices.Contains(model.DefaultFor, capability) {
			configured = true
			if inferenceModelAvailableToOwner(model, owner) && slices.Contains(model.Capabilities, capability) {
				return model.Name
			}
		}
	}
	if configured {
		for _, model := range InferenceModelListForOwner(owner) {
			if slices.Contains(model.Capabilities, capability) {
				return model.Name
			}
		}
	}
	return ""
}

func playgroundImagePath(owner orcapi.ResourceOwner, path string) (string, *util.HttpError) {
	components := strings.Split(path, "/")
	if len(components) != 5 || filepath.Clean(path) != path || !strings.HasPrefix(components[3], "thread-") {
		return "", util.HttpErr(http.StatusNotFound, "image not found")
	}
	if _, err := time.Parse("2006/01/02", strings.Join(components[:3], "/")); err != nil {
		return "", util.HttpErr(http.StatusNotFound, "image not found")
	}
	for _, component := range components[3:] {
		for _, c := range component {
			if !(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' ||
				c >= '0' && c <= '9' || c == '-' || c == '_' || c == '.') {
				return "", util.HttpErr(http.StatusNotFound, "image not found")
			}
		}
	}
	switch filepath.Ext(path) {
	case ".png", ".jpg", ".webp", ".gif":
	default:
		return "", util.HttpErr(http.StatusNotFound, "image not found")
	}
	basePath, _, err := filesystem.InitializeMemberFiles(owner.CreatedBy, owner.Project)
	if err != nil {
		return "", err
	}
	return filepath.Join(playgroundChatsRoot(basePath), path), nil
}

func playgroundImageRead(owner orcapi.ResourceOwner, path string) ([]byte, *util.HttpError) {
	path, err := playgroundImagePath(owner, path)
	if err != nil {
		return nil, err
	}
	data, err := filesystem.ReadFile(path, playgroundImageMaxBytes)
	if err != nil {
		return nil, util.HttpErr(http.StatusNotFound, "image not found")
	}
	switch http.DetectContentType(data) {
	case "image/png", "image/jpeg", "image/webp", "image/gif":
	default:
		return nil, util.HttpErr(http.StatusNotFound, "image not found")
	}
	return data, nil
}

func playgroundImagesDirectory(root string, thread playgroundChatThread) string {
	return filepath.Join(filepath.Dir(playgroundThreadPath(root, thread)), thread.Id)
}

func playgroundImageThreadValid(id string) bool {
	if !strings.HasPrefix(id, "thread-") {
		return false
	}
	for _, c := range id {
		if !(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' ||
			c >= '0' && c <= '9' || c == '-' || c == '_') {
			return false
		}
	}
	return true
}

func playgroundImagesDelete(owner orcapi.ResourceOwner, thread playgroundChatThread) *util.HttpError {
	if !playgroundImageThreadValid(thread.Id) {
		return util.HttpErr(http.StatusBadRequest, "invalid thread ID")
	}
	basePath, drive, err := filesystem.InitializeMemberFiles(owner.CreatedBy, owner.Project)
	if err != nil {
		return err
	}
	if ctrl.ResourceIsLocked(drive.Resource, drive.Specification.Product) {
		return util.PaymentError()
	}
	directory := playgroundImagesDirectory(playgroundChatsRoot(basePath), thread)
	if !playgroundIsDir(directory) {
		return nil
	}
	return filesystem.DoDeleteFile(directory)
}

func (app *InferencePlaygroundApp) playgroundStoreImage(ctx context.Context, threadId string,
	part playgroundChatMessagePart, data []byte) (playgroundChatMessagePart, *util.HttpError) {
	if !playgroundImageThreadValid(threadId) {
		return part, util.HttpErr(http.StatusBadRequest, "invalid thread ID")
	}
	if len(data) > playgroundImageMaxBytes {
		return part, util.HttpErr(http.StatusBadGateway, "image is too large")
	}
	if part.ModelName != "" {
		if _, ok := inferenceImagePixelCount(data); !ok {
			return part, util.HttpErr(http.StatusBadGateway, "invalid image")
		}
	}
	ext := ""
	switch http.DetectContentType(data) {
	case "image/png":
		ext = ".png"
	case "image/jpeg":
		ext = ".jpg"
	case "image/webp":
		ext = ".webp"
	case "image/gif":
		ext = ".gif"
	default:
		return part, util.HttpErr(http.StatusBadRequest, "image must be PNG, JPEG, WebP, or GIF")
	}
	basePath, drive, err := filesystem.InitializeMemberFiles(app.Owner.CreatedBy, app.Owner.Project)
	if err != nil {
		return part, err
	}
	if ctrl.ResourceIsLocked(drive.Resource, drive.Specification.Product) {
		return part, util.PaymentError()
	}
	app.mu.Lock()
	defer app.mu.Unlock()
	if ctx.Err() != nil {
		return part, util.HttpErr(http.StatusRequestTimeout, "image operation cancelled")
	}
	for _, thread := range app.Threads {
		if thread.Id != threadId {
			continue
		}
		root := playgroundChatsRoot(basePath)
		path := filepath.Join(playgroundImagesDirectory(root, thread), "image-"+util.SecureToken()+ext)
		if err := filesystem.WriteFileAtomic(path, data, 0660); err != nil {
			return part, err
		}
		part.ImagePath, _ = filepath.Rel(root, path)
		part.Url = ""
		if part.ImageId == "" {
			part.ImageId = strings.TrimSuffix(filepath.Base(path), ext)
		}
		if part.FileName == "" {
			part.FileName = filepath.Base(path)
		}
		return part, nil
	}
	return part, util.HttpErr(http.StatusNotFound, "thread no longer exists")
}

func (app *InferencePlaygroundApp) playgroundPrepareImages(ctx context.Context, threadId string) *util.HttpError {
	app.mu.Lock()
	var messages []playgroundChatMessage
	for _, thread := range app.Threads {
		if thread.Id == threadId {
			messages = slices.Clone(thread.Messages)
			for i := range messages {
				messages[i].Parts = slices.Clone(messages[i].Parts)
			}
			break
		}
	}
	app.mu.Unlock()
	for messageIndex, message := range messages {
		for partIndex, part := range message.Parts {
			if part.Kind != "image" {
				continue
			}
			if part.ImagePath != "" {
				part.Status = ""
				path, err := playgroundImagePath(app.Owner, part.ImagePath)
				if err != nil {
					part.Status = "missing"
				} else if info, err := filesystem.Stat(path); err != nil || !info.Mode().IsRegular() {
					part.Status = "missing"
				}
				app.playgroundUpdateImagePart(threadId, messageIndex, partIndex, part)
				continue
			}
			parsed, parseErr := url.Parse(part.Url)
			if parseErr != nil ||
				parsed.Path != "/api/inference/attachments/download" {
				return util.HttpErr(http.StatusBadRequest, "invalid image attachment")
			}
			id := parsed.Query().Get("id")
			attachment, ok := attachmentLookup(id)
			if !ok ||
				attachment.CreatedBy != app.Owner.CreatedBy ||
				attachment.ProjectId != app.Owner.Project {
				part.Status = "missing"
				app.playgroundUpdateImagePart(threadId, messageIndex, partIndex, part)
				continue
			}
			path, _, err := attachmentPath(attachment)
			if err != nil {
				return err
			}
			data, err := filesystem.ReadFile(path, playgroundImageMaxBytes)
			if err != nil {
				return err
			}
			switch http.DetectContentType(data) {
			case "image/png", "image/jpeg", "image/webp", "image/gif":
			default:
				continue
			}
			part.ImageId = id
			part.Status = ""
			part, err = app.playgroundStoreImage(ctx, threadId, part, data)
			if err != nil {
				return err
			}
			app.playgroundUpdateImagePart(threadId, messageIndex, partIndex, part)
		}
	}
	return nil
}

func (app *InferencePlaygroundApp) playgroundUpdateImagePart(threadId string, messageIndex int,
	partIndex int, part playgroundChatMessagePart) {
	app.mu.Lock()
	defer app.mu.Unlock()
	for i := range app.Threads {
		thread := &app.Threads[i]
		if thread.Id != threadId || messageIndex >= len(thread.Messages) {
			continue
		}
		thread.Messages[messageIndex].Parts = slices.Clone(thread.Messages[messageIndex].Parts)
		thread.Messages[messageIndex].Parts[partIndex] = part
		thread.Dirty = true
		if app.CurrentThreadId == threadId {
			app.Chat.Messages = slices.Clone(thread.Messages)
			app.prepareChatMessagesForUi()
		}
	}
}

func (app *InferencePlaygroundApp) playgroundImageRequestMessages(original []InferenceChatMessage,
	history []playgroundChatMessage, vision bool, inlineImages bool) []InferenceChatMessage {
	messages := []InferenceChatMessage{}
	for _, message := range original {
		if message.Role == "system" {
			messages = append(messages, message)
		}
	}
	inlineBudget := int64(playgroundInlineImageBudget)
	for _, message := range history {
		images := []playgroundChatMessagePart{}
		for _, part := range message.Parts {
			if part.Kind == "image" {
				images = append(images, part)
			}
		}
		if len(images) == 0 {
			messages = append(messages, InferenceChatMessage{Role: message.Role, Content: playgroundInferenceContent(message)})
			if message.ImageReference != "" {
				messages = append(messages, InferenceChatMessage{
					Role:    "user",
					Content: inferenceChatTextContent("Selected image for editing: " + message.ImageReference),
				})
			}
			continue
		}
		content := InferenceChatMessageContent{}
		if !playgroundMessageIsAttachmentOnly(message) && message.Content != "" {
			content.Parts = append(content.Parts, InferenceChatContentPart{Type: "text", Text: message.Content})
		}
		for _, part := range images {
			note := fmt.Sprintf("Image present in this conversation. image_id: %s. "+
				"Use image_id only for edit_image. Never repeat image metadata in a reply.", part.ImageId)
			if part.Prompt != "" {
				note += "\nGeneration prompt: " + part.Prompt
			}
			if part.RevisedPrompt != "" {
				note += "\nRevised prompt: " + part.RevisedPrompt
			}
			includeImage := message.Role == "user" && vision && part.Status != "missing"
			imageURL := ""
			if includeImage {
				imageURL = app.playgroundImageVisionURL(part, inlineImages, &inlineBudget)
				includeImage = imageURL != ""
			}
			if includeImage {
				content.Parts = append(content.Parts, InferenceChatContentPart{
					Type:     "image_url",
					ImageUrl: &InferenceChatUrl{Url: imageURL},
				})
			} else if message.Role == "user" && vision && part.Status != "missing" {
				note += "\nThe image could not be included in this request. Do not claim to have seen it."
			} else if part.Status == "missing" {
				note += "\nThe source file is missing. Ask the user to attach it again before viewing or editing it."
			} else {
				note += "\nImage pixels are not included. Do not claim to have seen the image. Use its ID for editing."
			}
			content.Parts = append(content.Parts, InferenceChatContentPart{
				Type: "text",
				Text: note,
			})
		}
		messages = append(messages, InferenceChatMessage{Role: message.Role, Content: content})
	}
	return messages
}

func (app *InferencePlaygroundApp) playgroundImageVisionURL(part playgroundChatMessagePart, inline bool,
	budget *int64) string {
	if inline && part.ImagePath != "" && *budget > 0 {
		data, err := playgroundImageRead(app.Owner, part.ImagePath)
		if err == nil {
			contentType := http.DetectContentType(data)
			encodedLength := int64(base64.StdEncoding.EncodedLen(len(data)) +
				len("data:") + len(contentType) + len(";base64,"))
			if encodedLength <= *budget {
				*budget -= encodedLength
				return "data:" + contentType + ";base64," + base64.StdEncoding.EncodeToString(data)
			}
		}
		return ""
	}
	if part.ImagePath != "" {
		return ctrl.InferencePlaygroundImageURL(app.Owner, part.ImagePath)
	}
	return part.Url
}

func inferenceModelEndpointIsLocal(model InferenceModel) bool {
	endpoint, err := url.Parse(model.Endpoint.BasePath)
	if err != nil {
		return false
	}
	if inferenceExternalEndpointAllowed(endpoint, shared.ServiceConfig.Compute.Inference.ExternalHosts) {
		return false
	}
	host := strings.ToLower(endpoint.Hostname())
	if host == "localhost" || host == "127.0.0.1" || host == "::1" {
		return true
	}
	if strings.HasSuffix(host, ".svc.cluster.local") {
		return true
	}
	return strings.EqualFold(host, cfg.Provider.Hosts.SelfPublic.Address) ||
		strings.EqualFold(host, cfg.Provider.Hosts.Self.Address)
}

func (app *InferencePlaygroundApp) playgroundImageSource(threadId string, imageId string) (playgroundChatMessagePart, bool) {
	app.mu.Lock()
	defer app.mu.Unlock()
	for _, thread := range app.Threads {
		if thread.Id == threadId {
			for _, message := range thread.Messages {
				for _, part := range message.Parts {
					if part.Kind == "image" && part.ImageId == imageId && part.ImagePath != "" {
						return part, true
					}
				}
			}
		}
	}
	return playgroundChatMessagePart{}, false
}

func (app *InferencePlaygroundApp) playgroundHasImage(threadId string, assistantIndex int) bool {
	app.mu.Lock()
	defer app.mu.Unlock()
	for _, thread := range app.Threads {
		if thread.Id == threadId && assistantIndex < len(thread.Messages) {
			for _, part := range thread.Messages[assistantIndex].Parts {
				if part.Kind == "image" {
					return true
				}
			}
		}
	}
	return false
}

func (app *InferencePlaygroundApp) playgroundImageTool(ctx context.Context, call InferenceChatToolCall) playgroundToolResult {
	fail := func(message string) playgroundToolResult {
		return playgroundToolResult{Message: playgroundToolError(call.Id, message), Error: message}
	}
	turn, _ := ctx.Value(playgroundImageTurnKey{}).(*playgroundImageTurn)
	if turn == nil || ctx.Err() != nil {
		return fail("image operation cancelled")
	}
	if turn.Calls >= playgroundImageMaxCalls {
		return fail("image call limit reached: use the existing results and answer without more image calls")
	}
	var args playgroundImageArgs
	if err := playgroundToolDecodeArgs(call, &args); err != "" {
		return fail(err)
	}
	if strings.TrimSpace(args.Prompt) == "" {
		return fail("image prompt is required")
	}
	if args.Size != "" && args.Size != "auto" && args.Size != "1024x1024" &&
		args.Size != "1536x1024" && args.Size != "1024x1536" {
		return fail("unsupported image size")
	}
	capability := InferenceTextToImage
	if call.Function.Name == "edit_image" {
		capability = InferenceImageToImage
	}
	modelName := playgroundImageDefault(app.walletOwner(), capability)
	if modelName == "" {
		return fail("no accessible provider default is configured for this image operation")
	}
	if args.Model != "" {
		modelName = args.Model
	}
	model, ok := InferenceCatalogModelByName(modelName)
	if !ok || !inferenceModelAvailableToOwner(model, app.walletOwner()) ||
		!slices.Contains(model.Capabilities, capability) {
		return fail("image model is not available for this operation")
	}
	request := InferenceImageGenerationRequest{
		Model:          util.OptValue(model.Name),
		Prompt:         args.Prompt,
		N:              util.OptValue(1),
		ResponseFormat: util.OptValue("b64_json"),
		Size:           util.OptStringIfNotEmpty(args.Size),
	}
	if capability == InferenceImageToImage {
		source, ok := app.playgroundImageSource(turn.ThreadId, args.ImageId)
		if !ok {
			return fail("select an existing image_id from this thread")
		}
		data, err := playgroundImageRead(app.Owner, source.ImagePath)
		if err != nil {
			return fail(err.Why)
		}
		if len(data) > inferenceMaxImageEditFileBytes {
			return fail("edit source exceeds the 20 MiB image-edit limit")
		}
		if _, ok := inferenceImagePixelCount(data); !ok {
			return fail("invalid image-edit source dimensions")
		}
		switch http.DetectContentType(data) {
		case "image/png", "image/jpeg", "image/webp":
		default:
			return fail("image edits require PNG, JPEG, or WebP; this source can still be used for vision")
		}
		request.Image = &InferenceImageEditFile{
			Name: filepath.Base(source.ImagePath), ContentType: http.DetectContentType(data), Data: data,
		}
	}
	turn.Calls++
	response, err := InferenceGenerateImage(inferenceAuditSource(ctx, "playground"), app.walletOwner(),
		app.tokenUsername(), request)
	if err != nil {
		return fail(err.Why)
	}
	if len(response.Data) != 1 {
		return fail("image model did not return exactly one image")
	}
	element := response.Data[0]
	data, err := playgroundImageOutputBytes(ctx, model, element)
	if err != nil {
		return fail(err.Why)
	}
	part, err := app.playgroundStoreImage(
		ctx,
		turn.ThreadId,
		playgroundChatMessagePart{
			Kind:          "image",
			ModelName:     model.Title,
			Prompt:        args.Prompt,
			RevisedPrompt: element.RevisedPrompt.GetOrDefault(""),
		},
		data,
	)
	if err != nil {
		return fail(err.Why)
	}
	app.appendThreadAssistantPart(turn.ThreadId, turn.AssistantIndex, part, turn.ChatModel,
		turn.StartedAt)
	output := playgroundToolJSON(map[string]any{
		"status":         "ok",
		"model":          model.Name,
		"model_title":    model.Title,
		"image_id":       part.ImageId,
		"prompt":         args.Prompt,
		"revised_prompt": part.RevisedPrompt,
	})
	return playgroundToolResult{Message: playgroundToolMessage(call.Id, output), Output: output}
}

func playgroundImageOutputBytes(ctx context.Context, model InferenceModel,
	element InferenceImageGenerationResponseEl) ([]byte, *util.HttpError) {
	if element.B64JSON.Present {
		return inferenceImageBytesFromBase64OrDataURL(element.B64JSON.Value)
	}
	if data, ok := inferenceImageDataURLBytes(element.URL.GetOrDefault("")); ok {
		return data, nil
	}
	backend, _ := url.Parse(model.Endpoint.BasePath)
	backendPort := "80"
	if backend != nil {
		if backend.Scheme == "https" {
			backendPort = "443"
		}
		if backend.Port() != "" {
			backendPort = backend.Port()
		}
	}
	transport := &http.Transport{
		DialContext: func(ctx context.Context, network string, address string) (net.Conn, error) {
			host, port, err := net.SplitHostPort(address)
			if err != nil {
				return nil, err
			}
			addresses, err := net.DefaultResolver.LookupIPAddr(ctx, host)
			if err != nil {
				return nil, err
			}
			for _, resolved := range addresses {
				ip := resolved.IP
				trustedBackend := backend != nil && host == backend.Hostname() && port == backendPort
				sharedAddress := ip.To4() != nil && ip.To4()[0] == 100 && ip.To4()[1] >= 64 && ip.To4()[1] <= 127
				if !trustedBackend &&
					(!ip.IsGlobalUnicast() || ip.IsPrivate() || ip.IsLoopback() || ip.IsLinkLocalUnicast() || sharedAddress) {
					continue
				}
				return (&net.Dialer{}).DialContext(ctx, network, net.JoinHostPort(ip.String(), port))
			}
			return nil, fmt.Errorf("image URL address is not allowed")
		},
	}
	defer transport.CloseIdleConnections()
	client := &http.Client{
		Transport: transport,
		Timeout:   60 * time.Second,
		CheckRedirect: func(r *http.Request, via []*http.Request) error {
			if len(via) >= 3 || r.URL.User != nil || (r.URL.Scheme != "http" && r.URL.Scheme != "https") {
				return fmt.Errorf("too many image redirects")
			}
			return nil
		},
	}
	request, requestErr := http.NewRequestWithContext(ctx, http.MethodGet, element.URL.GetOrDefault(""), nil)
	if requestErr != nil || request.URL.User != nil || (request.URL.Scheme != "http" && request.URL.Scheme != "https") {
		return nil, util.HttpErr(http.StatusBadGateway, "invalid image URL")
	}
	response, fetchErr := client.Do(request)
	if fetchErr != nil {
		return nil, util.HttpErr(http.StatusBadGateway, "could not download generated image")
	}
	defer util.SilentClose(response.Body)
	if response.StatusCode != http.StatusOK {
		return nil, util.HttpErr(http.StatusBadGateway, "could not download generated image")
	}
	data, readErr := io.ReadAll(io.LimitReader(response.Body, playgroundImageMaxBytes+1))
	if readErr != nil || len(data) > playgroundImageMaxBytes {
		return nil, util.HttpErr(http.StatusBadGateway, "generated image is too large or incomplete")
	}
	return data, nil
}

func (app *InferencePlaygroundApp) playgroundRunDirectImage(ctx context.Context, turn *playgroundImageTurn,
	prompt string, attachments []playgroundChatAttachment) {
	args := playgroundImageArgs{Prompt: prompt, ImageId: turn.Reference}
	if args.ImageId == "" {
		for _, attachment := range attachments {
			if attachment.Kind != "image" {
				continue
			}
			if args.ImageId != "" {
				app.playgroundFinishImageTurn(turn, "Select one source image before using /image with several attachments.")
				return
			}
			args.ImageId = attachment.AttachmentId
		}
	}
	name := "generate_image"
	if args.ImageId != "" {
		name = "edit_image"
	}
	call := InferenceChatToolCall{
		Id:   "image-" + util.SecureToken(),
		Type: "function",
		Function: InferenceChatToolCallFunction{
			Name:      name,
			Arguments: playgroundToolJSON(args),
		},
	}
	app.appendThreadAssistantPart(turn.ThreadId, turn.AssistantIndex, playgroundToolChatPart(call, "running", ""),
		turn.ChatModel, turn.StartedAt)
	result := app.playgroundImageTool(ctx, call)
	app.appendThreadAssistantPart(turn.ThreadId, turn.AssistantIndex,
		playgroundToolChatPart(call, playgroundToolStatus(result), playgroundToolPartBody(call, result)),
		turn.ChatModel, turn.StartedAt)
	app.playgroundFinishImageTurn(turn, result.Error)
}

func (app *InferencePlaygroundApp) playgroundFinishImageTurn(turn *playgroundImageTurn, text string) {
	app.mu.Lock()
	app.updateThreadAssistant(turn.ThreadId, turn.AssistantIndex, text, "", "", false, turn.ChatModel,
		turn.StartedAt, turn.StartedAt, time.Now().UnixMilli(), 0)
	app.unregisterChatCancel(turn.ThreadId)
	app.setThreadLoading(turn.ThreadId, false)
	if app.Chat.StreamingThreadId == turn.ThreadId {
		app.Chat.StreamingThreadId = ""
		app.Chat.StreamingMessages = nil
	}
	session := app.session
	model := ucx.AppSnapshot(app)
	app.mu.Unlock()
	ucx.AppUpdateModelLocked(session, model)
}

func playgroundImageToolDefinitions(generation bool, editing bool) []InferenceChatTool {
	var tools []InferenceChatTool
	for _, operation := range []struct {
		Name        string
		Available   bool
		Description string
	}{
		{
			Name:      "generate_image",
			Available: generation,
			Description: "Generate one image from a detailed prompt. Use only when the user requests an image. " +
				"The image appears inline automatically. Do not embed an image URL in the reply. " +
				"Do not mention the file name, image ID, or tool output in the reply.",
		},
		{
			Name:      "edit_image",
			Available: editing,
			Description: "Edit one attached or previously generated image. Select image_id from this conversation. " +
				"Ask which source to use when unclear. The image appears inline automatically. " +
				"Do not mention the file name, image ID, or tool output in the reply.",
		},
	} {
		if !operation.Available {
			continue
		}
		properties := map[string]any{
			"prompt": map[string]any{"type": "string", "description": "Detailed image generation or edit instructions."},
			"size": map[string]any{
				"type":        "string",
				"enum":        []string{"auto", "1024x1024", "1536x1024", "1024x1536"},
				"description": "Optional output size; omit to use the model default.",
			},
			"model": map[string]any{
				"type":        "string",
				"description": "Optional accessible image model name. Omit to use the provider default.",
			},
		}
		required := []string{"prompt"}
		if operation.Name == "edit_image" {
			properties["image_id"] = map[string]any{"type": "string", "description": "Exact source image_id from this thread."}
			required = append(required, "image_id")
		}
		tool := playgroundToolDefinition(operation.Name, operation.Description, properties, required)
		tool.Function.Strict = util.OptValue(false)
		tools = append(tools, tool)
	}
	return tools
}
