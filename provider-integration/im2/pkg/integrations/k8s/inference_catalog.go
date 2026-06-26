package k8s

import (
	"database/sql"
	"encoding/json"
	"net/http"
	"slices"
	"sort"
	"strings"
	"sync"

	apm "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/util"
)

type InferenceCapability string

const (
	InferenceTextGeneration InferenceCapability = "TextGeneration"
	InferenceTextToImage    InferenceCapability = "TextToImage"
	InferenceSpeechToText   InferenceCapability = "SpeechToText"
)

type InferenceModel struct {
	Name            string                `json:"name"`
	Title           string                `json:"title"`
	TitleModelName  string                `json:"titleModelName"`
	Capabilities    []InferenceCapability `json:"capabilities"`
	PriceMultiplier InferencePricing      `json:"priceMultiplier"`
	Endpoint        InferenceEndpoint     `json:"endpoint"`
	Availability    InferenceAvailability `json:"availability"`
	ContextWindow   *int                  `json:"contextWindow,omitempty"`
	ChatSettings    InferenceChatSettings `json:"chatSettings"`
}

type InferenceChatSettings struct {
	Temperature         float64 `json:"temperature"`
	TopP                float64 `json:"topP"`
	MaxCompletionTokens int     `json:"maxCompletionTokens"`
	SystemPrompt        *string `json:"systemPrompt,omitempty"`
}

type InferencePricing struct {
	CachedInput int `json:"cachedInput"`
	Input       int `json:"input"`
	Output      int `json:"output"`
}

type InferenceEndpoint struct {
	BasePath         string `json:"basePath"`
	BackendModelName string `json:"backendModelName"`
}

type InferenceAvailability struct {
	Public      bool     `json:"public"`
	AvailableTo []string `json:"availableTo"`
}

var modelGlobals = struct {
	Mu     sync.RWMutex
	Models map[string]InferenceModel
}{
	Models: map[string]InferenceModel{},
}

type inferenceModelRow struct {
	Name                   string
	Title                  string
	TitleModelName         string
	Capabilities           []byte
	PriceCachedInput       int
	PriceInput             int
	PriceOutput            int
	InferenceEndpointPath  string
	InferenceEndpointModel string
	Public                 bool
	AvailableTo            []byte
	ContextWindow          sql.NullInt64
	Temperature            float64
	TopP                   float64
	MaxCompletionTokens    int
	SystemPrompt           sql.NullString
}

func inferenceModelCatalogLoad() {
	models := db.NewTx(func(tx *db.Transaction) map[string]InferenceModel {
		rows := db.Select[inferenceModelRow](
			tx,
			`
				select
					name,
					title,
					title_model_name,
					capabilities,
					price_cached_input,
					price_input,
					price_output,
					inference_endpoint_path,
					inference_endpoint_model,
					public,
					available_to,
					context_window,
					temperature,
					top_p,
					max_completion_tokens,
					system_prompt
				from inference_model
			`,
			db.Params{},
		)

		result := make(map[string]InferenceModel, len(rows))
		for _, row := range rows {
			var capabilities []InferenceCapability
			if err := json.Unmarshal(row.Capabilities, &capabilities); err != nil {
				continue
			}

			var availableTo []string
			if err := json.Unmarshal(row.AvailableTo, &availableTo); err != nil {
				continue
			}

			var contextWindow *int
			if row.ContextWindow.Valid {
				value := int(row.ContextWindow.Int64)
				contextWindow = &value
			}
			var systemPrompt *string
			if row.SystemPrompt.Valid {
				value := row.SystemPrompt.String
				systemPrompt = &value
			}

			result[row.Name] = inferenceModelNormalize(InferenceModel{
				Name:           row.Name,
				Title:          row.Title,
				TitleModelName: row.TitleModelName,
				Capabilities:   capabilities,
				PriceMultiplier: InferencePricing{
					CachedInput: row.PriceCachedInput,
					Input:       row.PriceInput,
					Output:      row.PriceOutput,
				},
				Endpoint: InferenceEndpoint{
					BasePath:         row.InferenceEndpointPath,
					BackendModelName: row.InferenceEndpointModel,
				},
				Availability: InferenceAvailability{
					Public:      row.Public,
					AvailableTo: availableTo,
				},
				ContextWindow: contextWindow,
				ChatSettings: InferenceChatSettings{
					Temperature:         row.Temperature,
					TopP:                row.TopP,
					MaxCompletionTokens: row.MaxCompletionTokens,
					SystemPrompt:        systemPrompt,
				},
			})
		}
		return result
	})

	modelGlobals.Mu.Lock()
	modelGlobals.Models = models
	modelGlobals.Mu.Unlock()
}

func InferenceModelList() []InferenceModel {
	modelGlobals.Mu.RLock()
	defer modelGlobals.Mu.RUnlock()

	result := make([]InferenceModel, 0, len(modelGlobals.Models))
	for _, model := range modelGlobals.Models {
		result = append(result, inferenceModelClone(model))
	}
	sort.Slice(result, func(i, j int) bool { return result[i].Name < result[j].Name })
	return result
}

func InferenceModelListForOwner(owner apm.WalletOwner) []InferenceModel {
	allModels := InferenceModelList()
	result := make([]InferenceModel, 0, len(allModels))
	for _, model := range allModels {
		if inferenceModelAvailableToOwner(model, owner) {
			result = append(result, model)
		}
	}
	return result
}

func InferenceCatalogModelByName(name string) (InferenceModel, bool) {
	name = strings.TrimSpace(name)
	if name == "" {
		return InferenceModel{}, false
	}

	modelGlobals.Mu.RLock()
	defer modelGlobals.Mu.RUnlock()

	model, ok := modelGlobals.Models[name]
	if !ok {
		return InferenceModel{}, false
	}
	return inferenceModelClone(model), true
}

func InferenceModelUpsert(model InferenceModel) *util.HttpError {
	model = inferenceModelNormalize(model)
	if err := inferenceModelValidate(model); err != nil {
		return err
	}

	modelGlobals.Mu.Lock()
	defer modelGlobals.Mu.Unlock()

	db.NewTx0(func(tx *db.Transaction) {
		inferenceModelUpsertTx(tx, model)
	})
	modelGlobals.Models[model.Name] = inferenceModelClone(model)
	return nil
}

func InferenceModelRename(oldName string, newName string) *util.HttpError {
	oldName = strings.TrimSpace(oldName)
	newName = strings.TrimSpace(newName)
	if oldName == "" || newName == "" {
		return util.HttpErr(http.StatusBadRequest, "invalid model name")
	}

	modelGlobals.Mu.Lock()
	defer modelGlobals.Mu.Unlock()

	model, ok := modelGlobals.Models[oldName]
	if !ok {
		return util.HttpErr(http.StatusNotFound, "model not found")
	}
	if model.Availability.Public {
		return util.HttpErr(http.StatusBadRequest, "public models cannot be renamed")
	}
	if _, exists := modelGlobals.Models[newName]; exists && oldName != newName {
		return util.HttpErr(http.StatusConflict, "model already exists")
	}

	model.Name = newName
	if model.TitleModelName == oldName {
		model.TitleModelName = newName
	}
	if err := inferenceModelValidate(model); err != nil {
		return err
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `update inference_model set title_model_name = :new_name where title_model_name = :old_name`, db.Params{"old_name": oldName, "new_name": newName})
		db.Exec(tx, `delete from inference_model where name = :name`, db.Params{"name": oldName})
		inferenceModelUpsertTx(tx, model)
	})

	for existingName, existing := range modelGlobals.Models {
		if existing.TitleModelName == oldName {
			existing.TitleModelName = newName
			modelGlobals.Models[existingName] = inferenceModelClone(existing)
		}
	}
	delete(modelGlobals.Models, oldName)
	modelGlobals.Models[newName] = inferenceModelClone(model)
	return nil
}

func InferenceModelDelete(name string) *util.HttpError {
	name = strings.TrimSpace(name)
	if name == "" {
		return util.HttpErr(http.StatusBadRequest, "invalid model name")
	}

	modelGlobals.Mu.Lock()
	defer modelGlobals.Mu.Unlock()

	if _, ok := modelGlobals.Models[name]; !ok {
		return util.HttpErr(http.StatusNotFound, "model not found")
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `delete from inference_model where name = :name`, db.Params{"name": name})
	})
	delete(modelGlobals.Models, name)
	return nil
}

func inferenceModelUpsertTx(tx *db.Transaction, model InferenceModel) {
	capabilities, _ := json.Marshal(model.Capabilities)
	availableTo, _ := json.Marshal(model.Availability.AvailableTo)
	contextWindow := sql.NullInt64{}
	if model.ContextWindow != nil {
		contextWindow = sql.NullInt64{Int64: int64(*model.ContextWindow), Valid: true}
	}
	systemPrompt := sql.NullString{}
	if model.ChatSettings.SystemPrompt != nil {
		systemPrompt = sql.NullString{String: *model.ChatSettings.SystemPrompt, Valid: true}
	}
	db.Exec(
		tx,
		`
			insert into inference_model(
				name,
				title,
				title_model_name,
				capabilities,
				price_cached_input,
				price_input,
				price_output,
				inference_endpoint_path,
				inference_endpoint_model,
				public,
				available_to,
				context_window,
				temperature,
				top_p,
				max_completion_tokens,
				system_prompt
			) values (
				:name,
				:title,
				:title_model_name,
				cast(:capabilities as jsonb),
				:price_cached_input,
				:price_input,
				:price_output,
				:inference_endpoint_path,
				:inference_endpoint_model,
				:public,
				cast(:available_to as jsonb),
				:context_window,
				:temperature,
				:top_p,
				:max_completion_tokens,
				:system_prompt
			) on conflict (name) do update set
				title = excluded.title,
				title_model_name = excluded.title_model_name,
				capabilities = excluded.capabilities,
				price_cached_input = excluded.price_cached_input,
				price_input = excluded.price_input,
				price_output = excluded.price_output,
				inference_endpoint_path = excluded.inference_endpoint_path,
				inference_endpoint_model = excluded.inference_endpoint_model,
				public = excluded.public,
				available_to = excluded.available_to,
				context_window = excluded.context_window,
				temperature = excluded.temperature,
				top_p = excluded.top_p,
				max_completion_tokens = excluded.max_completion_tokens,
				system_prompt = excluded.system_prompt
		`,
		db.Params{
			"name":                     model.Name,
			"title":                    model.Title,
			"title_model_name":         model.TitleModelName,
			"capabilities":             string(capabilities),
			"price_cached_input":       model.PriceMultiplier.CachedInput,
			"price_input":              model.PriceMultiplier.Input,
			"price_output":             model.PriceMultiplier.Output,
			"inference_endpoint_path":  model.Endpoint.BasePath,
			"inference_endpoint_model": model.Endpoint.BackendModelName,
			"public":                   model.Availability.Public,
			"available_to":             string(availableTo),
			"context_window":           contextWindow,
			"temperature":              model.ChatSettings.Temperature,
			"top_p":                    model.ChatSettings.TopP,
			"max_completion_tokens":    model.ChatSettings.MaxCompletionTokens,
			"system_prompt":            systemPrompt,
		},
	)
}

func inferenceModelValidate(model InferenceModel) *util.HttpError {
	if strings.TrimSpace(model.Name) == "" {
		return util.HttpErr(http.StatusBadRequest, "model name is required")
	}
	if strings.TrimSpace(model.Title) == "" {
		return util.HttpErr(http.StatusBadRequest, "model title is required")
	}
	if strings.TrimSpace(model.TitleModelName) == "" {
		return util.HttpErr(http.StatusBadRequest, "model title model name is required")
	}
	if len(model.Capabilities) == 0 {
		return util.HttpErr(http.StatusBadRequest, "model capabilities are required")
	}
	if model.PriceMultiplier.CachedInput < 0 || model.PriceMultiplier.Input < 0 || model.PriceMultiplier.Output < 0 {
		return util.HttpErr(http.StatusBadRequest, "model price multipliers cannot be negative")
	}
	if model.ChatSettings.Temperature < 0 || model.ChatSettings.Temperature > 2 {
		return util.HttpErr(http.StatusBadRequest, "model temperature must be between 0 and 2")
	}
	if model.ChatSettings.TopP < 0 || model.ChatSettings.TopP > 1 {
		return util.HttpErr(http.StatusBadRequest, "model top P must be between 0 and 1")
	}
	if model.ChatSettings.MaxCompletionTokens <= 0 {
		return util.HttpErr(http.StatusBadRequest, "model max completion tokens must be positive")
	}
	for _, capability := range model.Capabilities {
		switch capability {
		case InferenceTextGeneration, InferenceTextToImage, InferenceSpeechToText:
		default:
			return util.HttpErr(http.StatusBadRequest, "invalid model capability")
		}
	}
	if strings.TrimSpace(model.Endpoint.BasePath) == "" {
		return util.HttpErr(http.StatusBadRequest, "model endpoint base path is required")
	}
	if strings.TrimSpace(model.Endpoint.BackendModelName) == "" {
		return util.HttpErr(http.StatusBadRequest, "model endpoint backend model name is required")
	}
	return nil
}

func inferenceModelNormalize(model InferenceModel) InferenceModel {
	model.Name = strings.TrimSpace(model.Name)
	model.Title = strings.TrimSpace(model.Title)
	model.TitleModelName = strings.TrimSpace(model.TitleModelName)
	if model.TitleModelName == "" {
		model.TitleModelName = model.Name
	}
	model.Endpoint.BasePath = strings.TrimRight(strings.TrimSpace(model.Endpoint.BasePath), "/")
	model.Endpoint.BackendModelName = strings.TrimSpace(model.Endpoint.BackendModelName)
	if model.ContextWindow != nil && *model.ContextWindow <= 0 {
		model.ContextWindow = nil
	}
	if model.ChatSettings.MaxCompletionTokens == 0 {
		model.ChatSettings.MaxCompletionTokens = 65536
	}
	if model.ChatSettings.SystemPrompt != nil {
		value := strings.TrimSpace(*model.ChatSettings.SystemPrompt)
		if value == "" {
			model.ChatSettings.SystemPrompt = nil
		} else {
			model.ChatSettings.SystemPrompt = &value
		}
	}
	model.Capabilities = slices.Clone(model.Capabilities)
	model.Availability.AvailableTo = slices.Clone(model.Availability.AvailableTo)
	return model
}

func inferenceModelClone(model InferenceModel) InferenceModel {
	if model.ContextWindow != nil {
		value := *model.ContextWindow
		model.ContextWindow = &value
	}
	if model.ChatSettings.SystemPrompt != nil {
		value := *model.ChatSettings.SystemPrompt
		model.ChatSettings.SystemPrompt = &value
	}
	model.Capabilities = slices.Clone(model.Capabilities)
	model.Availability.AvailableTo = slices.Clone(model.Availability.AvailableTo)
	return model
}

func inferenceModelAvailableToOwner(model InferenceModel, owner apm.WalletOwner) bool {
	if model.Availability.Public {
		return true
	}
	if owner.ProjectId == "" {
		return false
	}
	return slices.Contains(model.Availability.AvailableTo, owner.ProjectId)
}
