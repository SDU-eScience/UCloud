package inference

import (
	"database/sql"
	"encoding/json"
	"maps"
	"net/http"
	"slices"
	"sort"
	"strings"
	"sync"

	apm "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/util"
)

type InferenceCapability string

const (
	InferenceTextGeneration InferenceCapability = "TextGeneration"
	InferenceTextToImage    InferenceCapability = "TextToImage"
	InferenceSpeechToText   InferenceCapability = "SpeechToText"
	InferenceVision         InferenceCapability = "Vision"
	InferenceVideoVision    InferenceCapability = "VideoVision"
	InferenceAudio          InferenceCapability = "Audio"
)

type InferenceModel struct {
	Name                   string                 `json:"name"`
	Title                  string                 `json:"title"`
	Capabilities           []InferenceCapability  `json:"capabilities"`
	ReasoningEfforts       []InferenceModelOption `json:"reasoningEfforts,omitempty"`
	DefaultReasoningEffort string                 `json:"defaultReasoningEffort,omitempty"`
	PricePerMillion        InferencePricing       `json:"pricePerMillion"`
	Endpoint               InferenceEndpoint      `json:"endpoint"`
	Availability           InferenceAvailability  `json:"availability"`
	ContextWindow          *int                   `json:"contextWindow,omitempty"`
	ChatSettings           InferenceChatSettings  `json:"chatSettings"`
	Page                   *InferenceModelPage    `json:"page,omitempty"`
}

type InferenceModelOption struct {
	Name  string `json:"name"`
	Value string `json:"value"`
}

type InferenceModelPage struct {
	ShortDescription string                      `json:"shortDescription,omitempty"`
	DocumentationUrl string                      `json:"documentationUrl,omitempty"`
	ReleaseDate      *fnd.Timestamp              `json:"releaseDate,omitempty"`
	About            InferenceModelPageAbout     `json:"about,omitempty"`
	BenchmarkScores  map[string]string           `json:"benchmarkScores,omitempty"`
	Datasheet        InferenceModelPageDatasheet `json:"datasheet,omitempty"`
}

type InferenceModelPageAbout struct {
	Description string                  `json:"description,omitempty"`
	Highlights  []string                `json:"highlights,omitempty"`
	KeyStats    []InferenceModelKeyStat `json:"keyStats,omitempty"`
}

type InferenceModelKeyStat struct {
	Label       string `json:"label"`
	Value       string `json:"value"`
	Description string `json:"description,omitempty"`
}

type InferenceModelPageDatasheet struct {
	Parameters          string `json:"parameters,omitempty"`
	ActivatedParameters string `json:"activatedParameters,omitempty"`
	Quantization        string `json:"quantization,omitempty"`
}

type InferenceBenchmark struct {
	Id             string   `json:"id"`
	Title          string   `json:"title"`
	Description    string   `json:"description,omitempty"`
	HigherIsBetter bool     `json:"higherIsBetter"`
	ModelNames     []string `json:"modelNames"`
}

type InferenceChatSettings struct {
	Temperature         float64 `json:"temperature"`
	TopP                float64 `json:"topP"`
	MaxCompletionTokens int     `json:"maxCompletionTokens"`
	SystemPrompt        *string `json:"systemPrompt,omitempty"`
	DisableTools        bool    `json:"disableTools"`
}

type InferencePricing struct {
	CachedInput int64 `json:"cachedInput"`
	Input       int64 `json:"input"`
	Output      int64 `json:"output"`
}

const InferencePriceScale int64 = 1_000_000

type InferenceEndpoint struct {
	BasePath         string `json:"basePath"`
	BackendModelName string `json:"backendModelName"`
}

type InferenceAvailability struct {
	Public      bool     `json:"public"`
	AvailableTo []string `json:"availableTo"`
}

var modelGlobals = struct {
	Mu         sync.RWMutex
	Models     map[string]InferenceModel
	Benchmarks []InferenceBenchmark
}{
	Models: map[string]InferenceModel{},
}

type inferenceModelRow struct {
	Name                       string
	Title                      string
	Capabilities               []byte
	ReasoningEfforts           []byte
	DefaultReasoningEffort     sql.NullString
	PricePerMillionCachedInput int64
	PricePerMillionInput       int64
	PricePerMillionOutput      int64
	InferenceEndpointPath      string
	InferenceEndpointModel     string
	Public                     bool
	AvailableTo                []byte
	ContextWindow              sql.NullInt64
	Temperature                float64
	TopP                       float64
	MaxCompletionTokens        int
	SystemPrompt               sql.NullString
	DisableTools               bool
	PageMetadata               []byte
}

type inferenceBenchmarkRow struct {
	Id             string
	Title          string
	Description    string
	HigherIsBetter bool
	ModelNames     []byte
}

func inferenceModelCatalogLoad() {
	models, benchmarks := db.NewTx2(func(tx *db.Transaction) (map[string]InferenceModel, []InferenceBenchmark) {
		rows := db.Select[inferenceModelRow](
			tx,
			`
				select
					name,
					title,
					capabilities,
					reasoning_efforts,
					default_reasoning_effort,
					price_per_million_cached_input,
					price_per_million_input,
					price_per_million_output,
					inference_endpoint_path,
					inference_endpoint_model,
					public,
					available_to,
					context_window,
					temperature,
					top_p,
					max_completion_tokens,
					system_prompt,
					disable_tools,
					page_metadata
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
			var reasoningEfforts []InferenceModelOption
			if err := json.Unmarshal(row.ReasoningEfforts, &reasoningEfforts); err != nil {
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
			var page *InferenceModelPage
			if len(row.PageMetadata) > 0 && string(row.PageMetadata) != "null" {
				var parsed InferenceModelPage
				if err := json.Unmarshal(row.PageMetadata, &parsed); err == nil {
					page = &parsed
				}
			}

			result[row.Name] = inferenceModelNormalize(InferenceModel{
				Name:             row.Name,
				Title:            row.Title,
				Capabilities:     capabilities,
				ReasoningEfforts: reasoningEfforts,
				DefaultReasoningEffort: func() string {
					if row.DefaultReasoningEffort.Valid {
						return row.DefaultReasoningEffort.String
					}
					return ""
				}(),
				PricePerMillion: InferencePricing{
					CachedInput: row.PricePerMillionCachedInput,
					Input:       row.PricePerMillionInput,
					Output:      row.PricePerMillionOutput,
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
					DisableTools:        row.DisableTools,
				},
				Page: page,
			})
		}

		benchmarkRows := db.Select[inferenceBenchmarkRow](
			tx,
			`
				select id, title, description, higher_is_better, model_names
				from inference_benchmark
				order by id
			`,
			db.Params{},
		)
		benchmarks := make([]InferenceBenchmark, 0, len(benchmarkRows))
		for _, row := range benchmarkRows {
			var modelNames []string
			if err := json.Unmarshal(row.ModelNames, &modelNames); err != nil {
				continue
			}
			benchmarks = append(benchmarks, InferenceBenchmark{
				Id:             row.Id,
				Title:          row.Title,
				Description:    row.Description,
				HigherIsBetter: row.HigherIsBetter,
				ModelNames:     modelNames,
			})
		}
		return result, benchmarks
	})

	modelGlobals.Mu.Lock()
	modelGlobals.Models = models
	modelGlobals.Benchmarks = benchmarks
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

func InferenceBenchmarkList() []InferenceBenchmark {
	modelGlobals.Mu.RLock()
	defer modelGlobals.Mu.RUnlock()

	result := make([]InferenceBenchmark, 0, len(modelGlobals.Benchmarks))
	for _, benchmark := range modelGlobals.Benchmarks {
		result = append(result, inferenceBenchmarkClone(benchmark))
	}
	return result
}

func InferenceBenchmarkReplace(benchmarks []InferenceBenchmark) *util.HttpError {
	next := make([]InferenceBenchmark, 0, len(benchmarks))
	seen := map[string]bool{}
	for _, benchmark := range benchmarks {
		benchmark = inferenceBenchmarkNormalize(benchmark)
		if err := inferenceBenchmarkValidate(benchmark); err != nil {
			return err
		}
		if seen[benchmark.Id] {
			return util.HttpErr(http.StatusBadRequest, "duplicate benchmark id")
		}
		seen[benchmark.Id] = true
		next = append(next, benchmark)
	}
	sort.Slice(next, func(i, j int) bool { return next[i].Id < next[j].Id })

	modelGlobals.Mu.Lock()
	defer modelGlobals.Mu.Unlock()

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `delete from inference_benchmark`, db.Params{})
		for _, benchmark := range next {
			inferenceBenchmarkInsertTx(tx, benchmark)
		}
	})
	modelGlobals.Benchmarks = make([]InferenceBenchmark, 0, len(next))
	for _, benchmark := range next {
		modelGlobals.Benchmarks = append(modelGlobals.Benchmarks, inferenceBenchmarkClone(benchmark))
	}
	return nil
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
	if err := inferenceModelValidate(model); err != nil {
		return err
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`delete from inference_model where name = :name`,
			db.Params{"name": oldName},
		)
		inferenceModelUpsertTx(tx, model)
	})

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
	reasoningEfforts, _ := json.Marshal(model.ReasoningEfforts)
	availableTo, _ := json.Marshal(model.Availability.AvailableTo)
	pageMetadata := sql.NullString{}
	if model.Page != nil {
		page, _ := json.Marshal(model.Page)
		pageMetadata = sql.NullString{String: string(page), Valid: true}
	}
	contextWindow := sql.NullInt64{}
	if model.ContextWindow != nil {
		contextWindow = sql.NullInt64{Int64: int64(*model.ContextWindow), Valid: true}
	}
	systemPrompt := sql.NullString{}
	if model.ChatSettings.SystemPrompt != nil {
		systemPrompt = sql.NullString{String: *model.ChatSettings.SystemPrompt, Valid: true}
	}
	defaultReasoningEffort := sql.NullString{}
	if model.DefaultReasoningEffort != "" {
		defaultReasoningEffort = sql.NullString{String: model.DefaultReasoningEffort, Valid: true}
	}
	db.Exec(
		tx,
		`
			insert into inference_model(
				name,
				title,
				capabilities,
				reasoning_efforts,
				default_reasoning_effort,
				price_per_million_cached_input,
				price_per_million_input,
				price_per_million_output,
				inference_endpoint_path,
				inference_endpoint_model,
				public,
				available_to,
				context_window,
				temperature,
				top_p,
				max_completion_tokens,
				system_prompt,
				disable_tools,
				page_metadata
			) values (
				:name,
				:title,
				cast(:capabilities as jsonb),
				cast(:reasoning_efforts as jsonb),
				:default_reasoning_effort,
				:price_per_million_cached_input,
				:price_per_million_input,
				:price_per_million_output,
				:inference_endpoint_path,
				:inference_endpoint_model,
				:public,
				cast(:available_to as jsonb),
				:context_window,
				:temperature,
				:top_p,
				:max_completion_tokens,
				:system_prompt,
				:disable_tools,
				cast(:page_metadata as jsonb)
			) on conflict (name) do update set
				title = excluded.title,
				capabilities = excluded.capabilities,
				reasoning_efforts = excluded.reasoning_efforts,
				default_reasoning_effort = excluded.default_reasoning_effort,
				price_per_million_cached_input = excluded.price_per_million_cached_input,
				price_per_million_input = excluded.price_per_million_input,
				price_per_million_output = excluded.price_per_million_output,
				inference_endpoint_path = excluded.inference_endpoint_path,
				inference_endpoint_model = excluded.inference_endpoint_model,
				public = excluded.public,
				available_to = excluded.available_to,
				context_window = excluded.context_window,
				temperature = excluded.temperature,
				top_p = excluded.top_p,
				max_completion_tokens = excluded.max_completion_tokens,
				system_prompt = excluded.system_prompt,
				disable_tools = excluded.disable_tools,
				page_metadata = excluded.page_metadata
		`,
		db.Params{
			"name":                           model.Name,
			"title":                          model.Title,
			"capabilities":                   string(capabilities),
			"reasoning_efforts":              string(reasoningEfforts),
			"default_reasoning_effort":       defaultReasoningEffort,
			"price_per_million_cached_input": model.PricePerMillion.CachedInput,
			"price_per_million_input":        model.PricePerMillion.Input,
			"price_per_million_output":       model.PricePerMillion.Output,
			"inference_endpoint_path":        model.Endpoint.BasePath,
			"inference_endpoint_model":       model.Endpoint.BackendModelName,
			"public":                         model.Availability.Public,
			"available_to":                   string(availableTo),
			"context_window":                 contextWindow,
			"temperature":                    model.ChatSettings.Temperature,
			"top_p":                          model.ChatSettings.TopP,
			"max_completion_tokens":          model.ChatSettings.MaxCompletionTokens,
			"system_prompt":                  systemPrompt,
			"disable_tools":                  model.ChatSettings.DisableTools,
			"page_metadata":                  pageMetadata,
		},
	)
}

func inferenceBenchmarkInsertTx(tx *db.Transaction, benchmark InferenceBenchmark) {
	modelNames, _ := json.Marshal(benchmark.ModelNames)
	db.Exec(
		tx,
		`
			insert into inference_benchmark(id, title, description, higher_is_better, model_names)
			values (:id, :title, :description, :higher_is_better, cast(:model_names as jsonb))
		`,
		db.Params{
			"id":               benchmark.Id,
			"title":            benchmark.Title,
			"description":      benchmark.Description,
			"higher_is_better": benchmark.HigherIsBetter,
			"model_names":      string(modelNames),
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
	if len(model.Capabilities) == 0 {
		return util.HttpErr(http.StatusBadRequest, "model capabilities are required")
	}
	if model.PricePerMillion.CachedInput < 0 || model.PricePerMillion.Input < 0 || model.PricePerMillion.Output < 0 {
		return util.HttpErr(http.StatusBadRequest, "model prices per million tokens cannot be negative")
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
		case InferenceTextGeneration, InferenceVision, InferenceVideoVision, InferenceAudio:
		default:
			return util.HttpErr(http.StatusBadRequest, "invalid model capability")
		}
	}
	reasoningValues := map[string]bool{}
	for _, effort := range model.ReasoningEfforts {
		if effort.Name == "" || effort.Value == "" {
			return util.HttpErr(http.StatusBadRequest, "reasoning effort names and values are required")
		}
		if reasoningValues[effort.Value] {
			return util.HttpErr(http.StatusBadRequest, "reasoning effort values must be unique")
		}
		reasoningValues[effort.Value] = true
	}
	if len(model.ReasoningEfforts) > 0 && !reasoningValues[model.DefaultReasoningEffort] {
		return util.HttpErr(http.StatusBadRequest, "default reasoning effort must match a supported value")
	}
	if strings.TrimSpace(model.Endpoint.BasePath) == "" {
		return util.HttpErr(http.StatusBadRequest, "model endpoint base path is required")
	}
	if strings.TrimSpace(model.Endpoint.BackendModelName) == "" {
		return util.HttpErr(http.StatusBadRequest, "model endpoint backend model name is required")
	}
	if err := inferenceValidateBackendEndpoint(model.Endpoint.BasePath); err != nil {
		return err
	}
	return nil
}

func inferenceModelNormalize(model InferenceModel) InferenceModel {
	model.Name = strings.TrimSpace(model.Name)
	model.Title = strings.TrimSpace(model.Title)
	model.Endpoint.BasePath = strings.TrimRight(strings.TrimSpace(model.Endpoint.BasePath), "/")
	model.Endpoint.BackendModelName = strings.TrimSpace(model.Endpoint.BackendModelName)
	for idx := range model.ReasoningEfforts {
		model.ReasoningEfforts[idx].Name = strings.TrimSpace(model.ReasoningEfforts[idx].Name)
		model.ReasoningEfforts[idx].Value = strings.TrimSpace(model.ReasoningEfforts[idx].Value)
	}
	model.DefaultReasoningEffort = strings.TrimSpace(model.DefaultReasoningEffort)
	if len(model.ReasoningEfforts) == 0 {
		model.DefaultReasoningEffort = ""
	}
	if model.ContextWindow != nil && *model.ContextWindow <= 0 {
		model.ContextWindow = nil
	}
	if model.ChatSettings.MaxCompletionTokens == 0 {
		model.ChatSettings.MaxCompletionTokens = 128 * 1024
	}
	if model.ChatSettings.SystemPrompt != nil {
		value := strings.TrimSpace(*model.ChatSettings.SystemPrompt)
		if value == "" {
			model.ChatSettings.SystemPrompt = nil
		} else {
			model.ChatSettings.SystemPrompt = &value
		}
	}
	if model.Page != nil {
		model.Page.ShortDescription = strings.TrimSpace(model.Page.ShortDescription)
		model.Page.DocumentationUrl = strings.TrimSpace(model.Page.DocumentationUrl)
		model.Page.About.Description = strings.TrimSpace(model.Page.About.Description)
		model.Page.About.Highlights = trimNonEmptyStrings(model.Page.About.Highlights)
		for idx := range model.Page.About.KeyStats {
			model.Page.About.KeyStats[idx].Label = strings.TrimSpace(model.Page.About.KeyStats[idx].Label)
			model.Page.About.KeyStats[idx].Value = strings.TrimSpace(model.Page.About.KeyStats[idx].Value)
			model.Page.About.KeyStats[idx].Description = strings.TrimSpace(model.Page.About.KeyStats[idx].Description)
		}
		model.Page.Datasheet.Parameters = strings.TrimSpace(model.Page.Datasheet.Parameters)
		model.Page.Datasheet.ActivatedParameters = strings.TrimSpace(model.Page.Datasheet.ActivatedParameters)
		model.Page.Datasheet.Quantization = strings.TrimSpace(model.Page.Datasheet.Quantization)
		if len(model.Page.BenchmarkScores) == 0 &&
			model.Page.ShortDescription == "" &&
			model.Page.DocumentationUrl == "" &&
			model.Page.ReleaseDate == nil &&
			model.Page.About.Description == "" &&
			len(model.Page.About.Highlights) == 0 &&
			len(model.Page.About.KeyStats) == 0 &&
			model.Page.Datasheet.Parameters == "" &&
			model.Page.Datasheet.ActivatedParameters == "" &&
			model.Page.Datasheet.Quantization == "" {
			model.Page = nil
		}
	}
	model.Capabilities = slices.Clone(model.Capabilities)
	model.ReasoningEfforts = slices.Clone(model.ReasoningEfforts)
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
	if model.Page != nil {
		page := *model.Page
		page.About.Highlights = slices.Clone(page.About.Highlights)
		page.About.KeyStats = slices.Clone(page.About.KeyStats)
		if page.BenchmarkScores != nil {
			page.BenchmarkScores = maps.Clone(page.BenchmarkScores)
		}
		model.Page = &page
	}
	model.Capabilities = slices.Clone(model.Capabilities)
	model.ReasoningEfforts = slices.Clone(model.ReasoningEfforts)
	model.Availability.AvailableTo = slices.Clone(model.Availability.AvailableTo)
	return model
}

func inferenceBenchmarkNormalize(benchmark InferenceBenchmark) InferenceBenchmark {
	benchmark.Id = strings.TrimSpace(benchmark.Id)
	benchmark.Title = strings.TrimSpace(benchmark.Title)
	benchmark.Description = strings.TrimSpace(benchmark.Description)
	benchmark.ModelNames = trimNonEmptyStrings(benchmark.ModelNames)
	return benchmark
}

func inferenceBenchmarkValidate(benchmark InferenceBenchmark) *util.HttpError {
	if benchmark.Id == "" {
		return util.HttpErr(http.StatusBadRequest, "benchmark id is required")
	}
	if benchmark.Title == "" {
		return util.HttpErr(http.StatusBadRequest, "benchmark title is required")
	}
	return nil
}

func inferenceBenchmarkClone(benchmark InferenceBenchmark) InferenceBenchmark {
	benchmark.ModelNames = slices.Clone(benchmark.ModelNames)
	return benchmark
}

func trimNonEmptyStrings(values []string) []string {
	result := make([]string, 0, len(values))
	for _, value := range values {
		value = strings.TrimSpace(value)
		if value != "" {
			result = append(result, value)
		}
	}
	return result
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
