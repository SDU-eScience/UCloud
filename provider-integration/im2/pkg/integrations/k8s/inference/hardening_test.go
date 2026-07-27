package inference

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/integrations/k8s/shared"
	apm "ucloud.dk/shared/pkg/accounting"
)

func TestInferenceDecodeJSONRejectsDeclaredOversize(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(`{"model":"test"}`))
	req.ContentLength = inferenceMaxJSONRequestBytes + 1
	recorder := httptest.NewRecorder()
	var request InferenceChatRequest

	if inferenceDecodeJSON(recorder, req, inferenceMaxJSONRequestBytes, &request) {
		t.Fatal("oversized request was accepted")
	}
	if recorder.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("expected 413, got %d", recorder.Code)
	}
}

func TestInferenceDecodeJSONRejectsUndeclaredOversize(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(`{"value":"`+strings.Repeat("x", 32)+`"}`))
	req.ContentLength = -1
	recorder := httptest.NewRecorder()
	var request map[string]string

	if inferenceDecodeJSON(recorder, req, 16, &request) {
		t.Fatal("oversized chunked request was accepted")
	}
	if recorder.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("expected 413, got %d", recorder.Code)
	}
}

func TestInferenceBackendEndpointDevelopmentRestriction(t *testing.T) {
	old := shared.ServiceConfig
	shared.ServiceConfig = &cfg.ServicesConfigurationKubernetes{}
	t.Cleanup(func() { shared.ServiceConfig = old })
	shared.ServiceConfig.Compute.Inference.Provider = cfg.KubernetesInferenceProviderDevelopment
	shared.ServiceConfig.Compute.Inference.BackendServer = "http://127.0.0.1:8080/v1"

	if err := inferenceValidateBackendEndpoint("http://127.0.0.1:8080/v1"); err != nil {
		t.Fatalf("configured development endpoint was rejected: %v", err)
	}
	if err := inferenceValidateBackendEndpoint("http://127.0.0.1:8081/v1"); err == nil {
		t.Fatal("unconfigured development endpoint was accepted")
	}
}

func TestInferenceBackendEndpointDynamoRestriction(t *testing.T) {
	old := shared.ServiceConfig
	shared.ServiceConfig = &cfg.ServicesConfigurationKubernetes{}
	t.Cleanup(func() { shared.ServiceConfig = old })
	shared.ServiceConfig.Compute.Inference.Provider = cfg.KubernetesInferenceProviderDynamo
	shared.ServiceConfig.Compute.Inference.Dynamo.Namespace = "models"

	if err := inferenceValidateBackendEndpoint("http://llm-frontend.models.svc.cluster.local:8000/v1"); err != nil {
		t.Fatalf("configured Dynamo namespace endpoint was rejected: %v", err)
	}
	for _, endpoint := range []string{
		"http://llm-frontend.other.svc.cluster.local:8000/v1",
		"http://llm.models.svc.cluster.local:8000/v1",
		"http://llm-frontend.models.svc.cluster.local.evil:8000/v1",
		"https://llm-frontend.models.svc.cluster.local:8000/v1",
	} {
		if err := inferenceValidateBackendEndpoint(endpoint); err == nil {
			t.Fatalf("disallowed Dynamo endpoint was accepted: %s", endpoint)
		}
	}
}

func TestInferenceBackendRequestHonorsCancellation(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		<-r.Context().Done()
	}))
	defer server.Close()

	old := shared.ServiceConfig
	shared.ServiceConfig = &cfg.ServicesConfigurationKubernetes{}
	t.Cleanup(func() { shared.ServiceConfig = old })
	shared.ServiceConfig.Compute.Inference.Provider = cfg.KubernetesInferenceProviderDevelopment
	shared.ServiceConfig.Compute.Inference.BackendServer = server.URL

	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if _, err := inferenceBackendRequest(ctx, server.URL, http.MethodPost, "/chat/completions", nil, "application/json"); err == nil {
		t.Fatal("canceled backend request succeeded")
	}
}

func TestInferenceStoredResponseIsOwnerBound(t *testing.T) {
	inferenceResponseGlobals.Mu.Lock()
	inferenceResponseGlobals.Responses = map[string]inferenceStoredResponse{}
	inferenceResponseGlobals.Mu.Unlock()

	owner := apm.WalletOwnerUser("alice")
	other := apm.WalletOwnerUser("bob")
	response := OaiResponse{Id: "resp-secret"}
	inferenceResponseStoreSet(owner, response)

	if _, ok := inferenceResponseStoreGet(owner, response.Id); !ok {
		t.Fatal("owner could not retrieve stored response")
	}
	if _, ok := inferenceResponseStoreGet(other, response.Id); ok {
		t.Fatal("different owner retrieved stored response")
	}
	if _, ok := inferenceResponseStoreGet(owner, response.Id); !ok {
		t.Fatal("different owner lookup removed stored response")
	}
}
