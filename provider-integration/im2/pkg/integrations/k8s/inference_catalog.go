package k8s

import (
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
	Capabilities    []InferenceCapability `json:"capabilities"`
	PriceMultiplier InferencePricing      `json:"priceMultiplier"`
	Endpoint        InferenceEndpoint     `json:"endpoint"`
	Availability    InferenceAvailability `json:"availability"`
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
	Capabilities           []byte
	PriceCachedInput       int
	PriceInput             int
	PriceOutput            int
	InferenceEndpointPath  string
	InferenceEndpointModel string
	Public                 bool
	AvailableTo            []byte
}

func inferenceModelCatalogLoad() {
	models := db.NewTx(func(tx *db.Transaction) map[string]InferenceModel {
		rows := db.Select[inferenceModelRow](
			tx,
			`
				select
					name,
					title,
					capabilities,
					price_cached_input,
					price_input,
					price_output,
					inference_endpoint_path,
					inference_endpoint_model,
					public,
					available_to
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

			result[row.Name] = inferenceModelNormalize(InferenceModel{
				Name:         row.Name,
				Title:        row.Title,
				Capabilities: capabilities,
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
	if err := inferenceModelValidate(model); err != nil {
		return err
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx, `delete from inference_model where name = :name`, db.Params{"name": oldName})
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
	availableTo, _ := json.Marshal(model.Availability.AvailableTo)
	db.Exec(
		tx,
		`
			insert into inference_model(
				name,
				title,
				capabilities,
				price_cached_input,
				price_input,
				price_output,
				inference_endpoint_path,
				inference_endpoint_model,
				public,
				available_to
			) values (
				:name,
				:title,
				cast(:capabilities as jsonb),
				:price_cached_input,
				:price_input,
				:price_output,
				:inference_endpoint_path,
				:inference_endpoint_model,
				:public,
				cast(:available_to as jsonb)
			) on conflict (name) do update set
				title = excluded.title,
				capabilities = excluded.capabilities,
				price_cached_input = excluded.price_cached_input,
				price_input = excluded.price_input,
				price_output = excluded.price_output,
				inference_endpoint_path = excluded.inference_endpoint_path,
				inference_endpoint_model = excluded.inference_endpoint_model,
				public = excluded.public,
				available_to = excluded.available_to
		`,
		db.Params{
			"name":                     model.Name,
			"title":                    model.Title,
			"capabilities":             string(capabilities),
			"price_cached_input":       model.PriceMultiplier.CachedInput,
			"price_input":              model.PriceMultiplier.Input,
			"price_output":             model.PriceMultiplier.Output,
			"inference_endpoint_path":  model.Endpoint.BasePath,
			"inference_endpoint_model": model.Endpoint.BackendModelName,
			"public":                   model.Availability.Public,
			"available_to":             string(availableTo),
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
	model.Endpoint.BasePath = strings.TrimRight(strings.TrimSpace(model.Endpoint.BasePath), "/")
	model.Endpoint.BackendModelName = strings.TrimSpace(model.Endpoint.BackendModelName)
	model.Capabilities = slices.Clone(model.Capabilities)
	model.Availability.AvailableTo = slices.Clone(model.Availability.AvailableTo)
	return model
}

func inferenceModelClone(model InferenceModel) InferenceModel {
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
