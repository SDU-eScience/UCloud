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

//go:embed playground_system_prompt_simple.md
var playgroundSystemPromptTemplateSimple string

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

	basePrompt := playgroundSystemPromptTemplate
	if model.ChatSettings.DisableTools {
		basePrompt = playgroundSystemPromptTemplateSimple
	}

	prompt := strings.ReplaceAll(basePrompt, "$MODEL_TITLE", title)
	prompt = strings.ReplaceAll(prompt, "$MODEL_PROVIDER", provider)
	prompt += fmt.Sprintf("\n\nCurrent date is: %s", time.Now().Format("Mon January _2 2006"))
	return strings.TrimSpace(prompt)
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
