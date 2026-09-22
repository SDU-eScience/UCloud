package inference

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"ucloud.dk/pkg/integrations/k8s/shared"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

// LocalAI discovery
// =====================================================================================================================

type inferenceLocalAIApplyRequest struct {
	Id   string `json:"id,omitempty"`
	Name string `json:"name,omitempty"`
}

func inferenceAutoConfigureLocalAI() error {
	base := strings.TrimRight(inferenceGlobals.BackendServer, "/")
	managementBase := strings.TrimSuffix(base, "/v1")

	err := inferenceWaitForModelEndpoint(fmt.Sprintf("%s/models", base))
	if err != nil {
		return err
	}

	inferenceApplyLocalAIFallbackModels(managementBase, "chat", []string{"localai@qwen3-0.6b"})
	inferenceDiscoverModelsFromEndpoint(base, shared.ServiceConfig.Compute.Inference.Access.Testers, true)

	return nil
}

func inferenceApplyLocalAIFallbackModels(base string, capability string, candidates []string) {
	for _, modelId := range candidates {
		if err := inferenceLocalAIApplyModel(base, modelId); err != nil {
			log.Warn("Could not auto-apply LocalAI model %s for %s: %v", modelId, capability, err)
			continue
		}

		log.Info("Auto-applied LocalAI model %s for %s", modelId, capability)
		return
	}

	log.Warn("No LocalAI models could be auto-applied for %s", capability)
}

func inferenceLocalAIApplyModel(base string, modelId string) error {
	client := http.Client{Timeout: 30 * time.Second}
	requestVariants := []inferenceLocalAIApplyRequest{
		{Id: modelId},
		{Name: modelId},
	}

	for _, request := range requestVariants {
		payload, _ := json.Marshal(request)
		resp, err := client.Post(
			fmt.Sprintf("%s/models/apply", strings.TrimRight(base, "/")),
			"application/json",
			bytes.NewBuffer(payload),
		)
		if err != nil {
			continue
		}

		_, _ = io.Copy(io.Discard, resp.Body)
		_ = resp.Body.Close()
		if resp.StatusCode >= 200 && resp.StatusCode < 300 {
			return nil
		}
	}

	return fmt.Errorf("model apply endpoint did not accept request")
}

// Dynamo
// =====================================================================================================================

func inferenceDiscoverDynamoModels() {
	inferenceCfg := &shared.ServiceConfig.Compute.Inference
	namespace := strings.TrimSpace(inferenceCfg.Dynamo.Namespace)
	if namespace == "" {
		return
	}
	if shared.K8sClient == nil {
		log.Warn("Could not discover Dynamo inference models: Kubernetes client is not initialized")
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	services, err := shared.K8sClient.CoreV1().Services(namespace).List(ctx, metav1.ListOptions{})
	if err != nil {
		log.Warn("Could not list Dynamo inference services in namespace %s: %v", namespace, err)
		return
	}

	for _, service := range services.Items {
		if !strings.HasSuffix(service.Name, "-frontend") {
			continue
		}
		if len(service.Spec.Ports) == 0 {
			continue
		}

		port := service.Spec.Ports[0].Port
		if port <= 0 {
			continue
		}

		base := fmt.Sprintf("http://%s.%s.svc.cluster.local:%d/v1", service.Name, service.Namespace, port)
		inferenceDiscoverModelsFromEndpoint(base, inferenceCfg.Access.Testers, false)
	}
}

// Shared
// =====================================================================================================================

type inferenceDiscoveredModel struct {
	Id            string `json:"id"`
	Object        string `json:"object"`
	ContextWindow *int   `json:"context_window,omitempty"`
}

type inferenceDiscoveredModelsResponse struct {
	Data []inferenceDiscoveredModel `json:"data"`
}

func inferenceDiscoverModelsFromEndpoint(base string, availableTo []string, disableTools bool) {
	base = strings.TrimRight(base, "/")
	client := http.Client{Timeout: 15 * time.Second}
	resp, err := client.Get(base + "/models")
	if err != nil {
		log.Warn("Could not discover inference models from %s: %v", base, err)
		return
	}
	defer util.SilentClose(resp.Body)

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		log.Warn("Could not discover inference models from %s: status=%d", base, resp.StatusCode)
		return
	}

	body, err := io.ReadAll(io.LimitReader(resp.Body, inferenceMaxJSONRequestBytes+1))
	if err != nil {
		log.Warn("Could not read inference model discovery response from %s: %v", base, err)
		return
	}
	if len(body) > inferenceMaxJSONRequestBytes {
		log.Warn("Inference model discovery response from %s exceeded the size limit", base)
		return
	}

	var models inferenceDiscoveredModelsResponse
	if err := json.Unmarshal(body, &models); err != nil {
		log.Warn("Could not parse inference model discovery response from %s: %v", base, err)
		return
	}

	for _, model := range models.Data {
		name := strings.TrimSpace(model.Id)
		if model.Object != "model" || name == "" {
			continue
		}

		catalogModel := inferenceModelNormalize(InferenceModel{
			Name:         name,
			Title:        name,
			Capabilities: []InferenceCapability{InferenceTextGeneration},
			PricePerMillion: InferencePricing{
				CachedInput: InferencePriceScale,
				Input:       InferencePriceScale,
				Output:      InferencePriceScale,
			},
			Endpoint: InferenceEndpoint{
				BasePath:         base,
				BackendModelName: name,
			},
			Availability: InferenceAvailability{
				Public:      false,
				AvailableTo: availableTo,
			},
			ContextWindow: model.ContextWindow,
			ChatSettings: InferenceChatSettings{
				Temperature:  0.8,
				TopP:         0.1,
				DisableTools: disableTools,
			},
		})
		if inferenceModelValidate(catalogModel) != nil {
			continue
		}

		inserted := false
		modelGlobals.Mu.Lock()
		knownBackendName := ""
		for existingName, existing := range modelGlobals.Models {
			if existing.Endpoint.BackendModelName == catalogModel.Endpoint.BackendModelName {
				knownBackendName = existingName
				break
			}
		}
		existing, knownName := modelGlobals.Models[catalogModel.Name]
		if !knownName && knownBackendName != "" {
			existing = modelGlobals.Models[knownBackendName]
		}
		if !knownName && knownBackendName == "" {
			db.NewTx0(func(tx *db.Transaction) {
				inferenceModelUpsertTx(tx, catalogModel)
			})
			modelGlobals.Models[catalogModel.Name] = inferenceModelClone(catalogModel)
			inserted = true
		} else if catalogModel.ContextWindow != nil && (existing.ContextWindow == nil || *existing.ContextWindow != *catalogModel.ContextWindow) {
			existing.ContextWindow = catalogModel.ContextWindow
			db.NewTx0(func(tx *db.Transaction) {
				inferenceModelUpsertTx(tx, existing)
			})
			modelGlobals.Models[existing.Name] = inferenceModelClone(existing)
		}
		modelGlobals.Mu.Unlock()

		if inserted {
			log.Info("Discovered inference model %s at %s", name, base)
		}
	}
}

func inferenceWaitForModelEndpoint(endpoint string) error {
	client := http.Client{Timeout: 15 * time.Second}
	for i := 0; i < 60; i++ {
		resp, err := client.Get(endpoint)
		if err == nil {
			_ = resp.Body.Close()
			if resp.StatusCode >= 200 && resp.StatusCode < 500 {
				return nil
			}
		}

		time.Sleep(1 * time.Second)
	}

	return fmt.Errorf("timed out waiting for inference backend model endpoint")
}
