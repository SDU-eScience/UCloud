package inference

import (
	_ "embed"
	"fmt"
	"strings"
	"time"
	"unicode"
)

//go:embed playground_system_prompt.md
var playgroundSystemPromptTemplate string

func (app *InferencePlaygroundApp) chatSystemPrompt() string {
	if app == nil || app.Developer {
		return strings.TrimSpace(app.Chat.SystemPrompt)
	}

	model, ok := app.modelByName(app.Chat.ModelId)
	if !ok {
		model = InferenceModel{Name: app.Chat.ModelId, Title: app.Chat.ModelId}
	}
	title := strings.TrimSpace(model.Title)
	if title == "" {
		title = strings.TrimSpace(model.Name)
	}
	if title == "" {
		title = "the selected model"
	}
	provider := playgroundModelProvider(model)

	prompt := strings.ReplaceAll(playgroundSystemPromptTemplate, "$MODEL_TITLE", title)
	prompt = strings.ReplaceAll(prompt, "$MODEL_PROVIDER", provider)
	prompt += app.chatToolPromptSections(model)
	prompt += fmt.Sprintf("\n\nCurrent date is: %s", time.Now().Format("Mon January _2 2006"))
	return strings.TrimSpace(prompt)
}

func (app *InferencePlaygroundApp) chatToolPromptSections(model InferenceModel) string {
	if model.ChatSettings.DisableTools {
		return ""
	}

	var sections []string

	tools := app.playgroundToolDefinitions()
	if len(tools) == 0 {
		return ""
	}

	var available []string
	for _, tool := range tools {
		available = append(available, tool.Function.Name)
	}
	sections = append(sections, fmt.Sprintf("\n## Available tools\n\nThe following tools are available in this session: %s. Only these tools can be called. Any other tool mentioned elsewhere does not exist in this session.\n", strings.Join(available, ", ")))
	sections = append(sections, "\n## Tool history\n\n* Previous turns can include compact source records instead of page content. Records are not evidence for new factual claims.\n* Older tool results can have `output_omitted` set to true. Use recent evidence or recover the source content when needed.\n* Do not repeat commands with side effects just to recover omitted output.\n")

	if app.currentThreadInternetDenied() {
		sections = append(sections, "\n## Internet access\n\nThe user has not granted internet access in this conversation. Web tools are unavailable and network commands in the sandbox will fail.\n\n* Do not attempt to fetch URLs or run network commands.\n* If answering the question requires internet access, call the `request_internet_access` tool with a short reason. The user will decide whether to allow it.\n* If access is declined or unavailable, say so briefly and answer from existing knowledge, stating that current information could not be retrieved.\n")
	} else {
		sections = append(sections, "\n## Web content\n\n* Use `query` to select relevant sections from long documents. This does not summarize the document.\n* The default content budget is 8,000 estimated tokens. Request up to 24,000 only when needed.\n* Token counts are estimates. The server can reduce the budget to reserve model context for the answer.\n* If `next_cursor` is present, continue with `document_id`, that cursor, and the unchanged query.\n* Cached documents expire after 10 minutes. Fetch the URL again if the document expires or becomes unavailable.\n* Check `download_truncated` before treating a document as complete. Continuation cannot recover bytes excluded by the download limit.\n* If a query does not match, the tool returns ordinary document content. Try different terms if needed.\n* Request independent URLs in one tool batch. Up to three web fetches can run concurrently.\n* Do not repeatedly fetch the same URL to read more content. Use the cached document and cursor.\n* For a live cached document, use `document_id` without a cursor to read it again. Use its URL if the cache expires.\n")
		sections = append(sections, "\n## Internet access\n\nUse current web information when the answer depends on facts that may have changed since the knowledge cutoff, including:\n\n* recent releases or active projects,\n* policies, laws, or regulations,\n* current events,\n* schedules,\n* rapidly changing technical documentation.\n\nRules:\n\n* Never invent or guess a URL.\n* Use only:\n\n    * URLs supplied by the user, or\n    * URLs returned by a search tool.\n* Use `web_fetch` for a specific public URL supplied by the user.\n* Prefer Markdown output from `web_fetch`; request HTML only when HTML itself is needed.\n* Mention briefly when current web information materially affected the answer.\n\nDo not use web tools when the user only asks for writing, rewriting, summarization, translation, brainstorming, or analysis of content already provided.\n")
	}

	sections = append(sections, "\n## Calculations and deterministic analysis\n\nUse the `bash` tool when a result is error-prone, tedious, data-dependent, or benefits from reproducibility, such as:\n\n* nontrivial arithmetic,\n* statistics,\n* data transformation,\n* validating generated output,\n* repeated calculations.\n\nFor simple calculations that can be answered reliably without a tool, answer directly.\n\nWhen using `bash` for computation:\n\n* Prefer a short Python script or another deterministic command.\n* Keep execution bounded.\n* Show the result and the essential method, not irrelevant runtime details.\n* Do not present computed values as verified unless execution succeeded.\n")

	return strings.Join(sections, "")
}

func playgroundModelProvider(model InferenceModel) string {
	candidates := []string{model.Endpoint.BackendModelName, model.Name}
	for _, candidate := range candidates {
		provider := playgroundProviderFromModelName(candidate)
		if provider != "" {
			return provider
		}
	}
	return "the model provider"
}

func playgroundProviderFromModelName(name string) string {
	name = strings.TrimSpace(name)
	if name == "" {
		return ""
	}
	provider, _, ok := strings.Cut(name, "/")
	if !ok {
		return ""
	}
	provider = strings.TrimSpace(provider)
	if provider == "" {
		return ""
	}
	if normalized := playgroundKnownProviderName(provider); normalized != "" {
		return normalized
	}
	provider = strings.NewReplacer("-", " ", "_", " ").Replace(provider)
	words := strings.Fields(provider)
	for i, word := range words {
		words[i] = playgroundTitleWord(word)
	}
	return strings.Join(words, " ")
}

func playgroundKnownProviderName(provider string) string {
	switch strings.ToLower(strings.TrimSpace(provider)) {
	case "openai":
		return "OpenAI"
	case "anthropic":
		return "Anthropic"
	case "google":
		return "Google"
	case "meta", "meta-llama":
		return "Meta"
	case "mistral", "mistralai":
		return "Mistral AI"
	case "deepseek":
		return "DeepSeek"
	case "qwen":
		return "Qwen"
	}
	return ""
}

func playgroundTitleWord(word string) string {
	if word == "" {
		return ""
	}
	runes := []rune(strings.ToLower(word))
	runes[0] = unicode.ToUpper(runes[0])
	return string(runes)
}
