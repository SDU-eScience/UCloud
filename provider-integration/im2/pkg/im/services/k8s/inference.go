package k8s

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"sync/atomic"
	"time"

	cfg "ucloud.dk/pkg/im/config"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/im/gateway"
	"ucloud.dk/pkg/im/services/k8s/shared"
	apm "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

var inferenceGlobals struct {
	Ready         atomic.Bool
	BackendServer string
	Product       apm.ProductV2
}

type inferenceCompletion struct {
	Id      string                      `json:"id"`
	Object  string                      `json:"object"`
	Created int64                       `json:"created"`
	Choices []inferenceChoice           `json:"choices"`
	Usage   util.Option[inferenceUsage] `json:"usage"`
}

type inferenceChoice struct {
	Index   int `json:"index"`
	Message struct {
		Role    string `json:"role"`
		Content string `json:"content"`
	} `json:"message"`
	FinishReason string `json:"finish_reason"`
}

type inferenceUsage struct {
	PromptTokens     int `json:"prompt_tokens"`
	CompletionTokens int `json:"completion_tokens"`
}

func initInference() {
	inferenceCfg := &shared.ServiceConfig.Compute.Inference
	if !inferenceCfg.Enabled {
		return
	}

	if inferenceCfg.OllamaDevMode {
		inferenceGlobals.BackendServer = "http://ollama:11434/v1"

		pullRequest := `{"model": "tinyllama:1.1b"}`
		_, err := http.Post("http://ollama:11434/api/pull", "application/json", bytes.NewBufferString(pullRequest))
		if err != nil {
			panic(fmt.Sprintf("could not initialize ollama: %s", err))
		} else {
			inferenceGlobals.Ready.Store(true)
		}
	} else {
		inferenceGlobals.Ready.Store(true)
	}

	inferenceGlobals.Product = apm.ProductV2{
		Type: apm.ProductTypeCLicense, // TODO faking it a bit for now
		Category: apm.ProductCategory{
			Name:        "inference",
			Provider:    cfg.Provider.Id,
			ProductType: apm.ProductTypeLicense,
			AccountingUnit: apm.AccountingUnit{
				Name:       "Token",
				NamePlural: "Tokens",
			},
			AccountingFrequency: apm.AccountingFrequencyOnce,
			FreeToUse:           false,
			AllowSubAllocations: true,
		},
		Name:                      "inference",
		Description:               "Inference tokens",
		ProductType:               apm.ProductTypeLicense,
		Price:                     1,
		HiddenInGrantApplications: false,
	}

	ctrl.RegisterProducts([]apm.ProductV2{inferenceGlobals.Product})

	authority := fmt.Sprintf("chat%s", shared.ServiceConfig.Compute.Web.Suffix) // TODO Change for prod
	gateway.SendMessage(gateway.ConfigurationMessage{
		RouteUp: &gateway.EnvoyRoute{
			Cluster:      gateway.ServerClusterName,
			CustomDomain: authority,
			Type:         gateway.RouteTypeIngress,
		},
	})

	ctrl.Mux.HandleFunc(authority+"/v1/chat/completions", func(w http.ResponseWriter, r *http.Request) {
		authHeader := r.Header.Get("Authorization")
		log.Info("Auth header: %s", authHeader)
		apiKey, _ := strings.CutPrefix(authHeader, "Bearer ")
		apiKeyOwner, httpErr := inferenceApiKeyValidate(apiKey)
		if httpErr != nil {
			http.Error(w, httpErr.Why, httpErr.StatusCode)
			return
		}

		body, err := io.ReadAll(r.Body) // TODO limit
		if err != nil {
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}

		var parsedRequest map[string]json.RawMessage
		err = json.Unmarshal(body, &parsedRequest)
		if err != nil {
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}

		stream := false
		streamOpt, ok := parsedRequest["stream"]
		if ok {
			_ = json.Unmarshal(streamOpt, &stream)

			if stream {
				fixedOpts, _ := json.Marshal(map[string]any{
					"include_usage": true,
				})
				parsedRequest["stream_options"] = fixedOpts
			}
		}

		fixedRequest, _ := json.Marshal(parsedRequest)
		proxyReq, err := http.NewRequest(
			http.MethodPost,
			fmt.Sprintf("%s/chat/completions", inferenceGlobals.BackendServer),
			bytes.NewBuffer(fixedRequest),
		)
		if err != nil {
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}

		proxyReq.Header.Add("Authorization", "Bearer notused")

		resp, err := http.DefaultClient.Do(proxyReq)
		if err != nil {
			// TODO
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}

		for k, values := range resp.Header {
			for _, v := range values {
				w.Header().Add(k, v)
			}
		}
		w.WriteHeader(resp.StatusCode)

		if stream {
			flusher, ok := w.(http.Flusher)
			if !ok {
				http.Error(w, "streaming unsupported", http.StatusInternalServerError)
				return
			}

			reader := bufio.NewReader(resp.Body)
			var buf bytes.Buffer

			flushBuffer := func() {
				if buf.Len() > 0 {
					respData := buf.Bytes()
					_, writeErr := w.Write(respData)
					if writeErr == nil {
						// Client is likely dead, but keep going to get usage numbers
						flusher.Flush()
					}

					respString, _ := strings.CutPrefix(string(respData), "data: ")
					var completion inferenceCompletion
					_ = json.Unmarshal([]byte(respString), &completion)
					if usage := completion.Usage; usage.Present && len(completion.Choices) == 0 {
						inferenceReportUsage(apiKeyOwner, usage.Value.PromptTokens, usage.Value.CompletionTokens)
					}

					buf.Reset()
				}
			}

			for {
				line, readErr := reader.ReadBytes('\n')
				if len(line) > 0 {
					buf.Write(line)

					// SSE events are separated by a blank line: "\n\n".
					if bytes.HasSuffix(buf.Bytes(), []byte("\n\n")) {
						flushBuffer()
					}
				}

				if readErr != nil {
					if readErr == io.EOF {
						flushBuffer()
					}
					break
				}
			}
		} else {
			respData, err := io.ReadAll(resp.Body)
			if err == nil {
				_, _ = w.Write(respData)

				var completion inferenceCompletion
				_ = json.Unmarshal(respData, &completion)
				if usage := completion.Usage; usage.Present {
					inferenceReportUsage(apiKeyOwner, usage.Value.PromptTokens, usage.Value.CompletionTokens)
				}
			}
		}
	})
}

func inferenceReportUsage(owner apm.WalletOwner, promptTokens int, completionTokens int) {
	log.Info("Prompt: %v | Completion: %v", promptTokens, completionTokens)

	_, _ = apm.ReportUsage.Invoke(fnd.BulkRequest[apm.ReportUsageRequest]{
		Items: []apm.ReportUsageRequest{
			{
				Owner:         owner,
				IsDeltaCharge: true,
				CategoryIdV2: apm.ProductCategoryIdV2{
					Name:     inferenceGlobals.Product.Category.Name,
					Provider: inferenceGlobals.Product.Category.Provider,
				},
				Usage:       int64(completionTokens),
				Description: apm.ChargeDescription{},
			},
		},
	})
}

func inferenceHandleApmEvent(update *ctrl.NotificationWalletUpdated) {
	if update.Category.Name == inferenceGlobals.Product.Category.Name {
		owner := update.Owner.ProjectId
		if owner == "" {
			owner = update.Owner.Username
		}

		db.NewTx0(func(tx *db.Transaction) {
			_, hasKey := db.Get[struct{ Owner string }](
				tx,
				`select owner from inference_api_keys where owner = :owner`,
				db.Params{
					"owner": owner,
				},
			)

			if !hasKey {
				newKey := util.SecureToken()
				db.Exec(
					tx,
					`
						insert into inference_api_keys(api_key, owner)
						values (:key, :owner)
				    `,
					db.Params{
						"owner": owner,
						"key":   newKey,
					},
				)
			}
		})
	}
}

var inferenceApiKeysCache = util.NewCache[string, string](1 * time.Hour)

func inferenceApiKeyValidate(key string) (apm.WalletOwner, *util.HttpError) {
	ownerRef, ok := inferenceApiKeysCache.Get(key, func() (string, error) {
		owner, ok := db.NewTx2(func(tx *db.Transaction) (string, bool) {
			owner, hasKey := db.Get[struct{ Owner string }](
				tx,
				`select owner from inference_api_keys where api_key = :key`,
				db.Params{
					"key": key,
				},
			)

			return owner.Owner, hasKey
		})

		if !ok {
			return "", util.HttpErr(http.StatusForbidden, "invalid key").AsError()
		} else {
			return owner, nil
		}
	})

	if !ok {
		return apm.WalletOwner{}, util.HttpErr(http.StatusForbidden, "invalid key")
	}

	owner := apm.WalletOwnerFromReference(ownerRef)
	if ctrl.IsLocked(owner, inferenceGlobals.Product.Category.Name) {
		return apm.WalletOwner{}, util.HttpErr(http.StatusPaymentRequired, "no more resources available")
	} else {
		return owner, nil
	}
}
