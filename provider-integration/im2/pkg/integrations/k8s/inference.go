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

	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/gateway"
	"ucloud.dk/pkg/integrations/k8s/shared"
	apm "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
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

	controller.ProductsRegister([]apm.ProductV2{inferenceGlobals.Product})

	authority := fmt.Sprintf("chat%s", shared.ServiceConfig.Compute.Web.Suffix) // TODO Change for prod
	gateway.SendMessage(gateway.ConfigurationMessage{
		RouteUp: &gateway.EnvoyRoute{
			Cluster:      gateway.ServerClusterName,
			CustomDomain: authority,
			Type:         gateway.RouteTypeIngress,
		},
	})

	controller.Mux.HandleFunc(authority+"/v1/chat/completions", func(w http.ResponseWriter, r *http.Request) {
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

var inferenceApiKeysCache = util.NewCache[string, string](1 * time.Hour)
var inferenceTokenIdToKey = util.NewCache[string, string](1 * time.Hour)

func inferenceApiKeyValidate(key string) (apm.WalletOwner, *util.HttpError) {
	tokenId, secret, ok := inferenceParseToken(key)
	if !ok {
		return apm.WalletOwner{}, util.HttpErr(http.StatusForbidden, "invalid key")
	}

	ownerRef, ok := inferenceApiKeysCache.Get(key, func() (string, error) {
		type rowType struct {
			Owner     string
			TokenHash []byte
			TokenSalt []byte
		}
		row, ok := db.NewTx2(func(tx *db.Transaction) (rowType, bool) {
			return db.Get[rowType](
				tx,
				`
					select owner, token_hash, token_salt
					from inference_api_keys
					where token_id = :token_id and now() <= expires_at
				`,
				db.Params{
					"token_id": tokenId,
				},
			)
		})

		if !ok || !util.CheckPassword(row.TokenHash, row.TokenSalt, secret) {
			return "", util.HttpErr(http.StatusForbidden, "invalid key").AsError()
		}

		db.NewTx0(func(tx *db.Transaction) {
			db.Exec(
				tx,
				`update inference_api_keys set last_used_at = now() where token_id = :token_id`,
				db.Params{
					"token_id": tokenId,
				},
			)
		})

		if !ok {
			return "", util.HttpErr(http.StatusForbidden, "invalid key").AsError()
		} else {
			inferenceTokenIdToKey.Set(tokenId, key)
			return row.Owner, nil
		}
	})

	if !ok {
		return apm.WalletOwner{}, util.HttpErr(http.StatusForbidden, "invalid key")
	}

	inferenceTokenIdToKey.Set(tokenId, key)

	owner := apm.WalletOwnerFromReference(ownerRef)
	if controller.WalletIsLocked(owner, inferenceGlobals.Product.Category.Name) {
		return apm.WalletOwner{}, util.HttpErr(http.StatusPaymentRequired, "no more resources available")
	} else {
		return owner, nil
	}
}

func inferenceInitApiTokens() controller.ApiTokenService {
	return controller.ApiTokenService{
		Create:          inferenceCreateApiToken,
		Revoke:          inferenceRevokeApiToken,
		RetrieveOptions: inferenceRetrieveApiTokenOptions,
	}
}

func inferenceCreateApiToken(info rpc.RequestInfo, request orcapi.ApiToken) (orcapi.ApiTokenStatus, *util.HttpError) {
	_ = info

	if !inferenceGlobals.Ready.Load() {
		return orcapi.ApiTokenStatus{}, util.HttpErr(http.StatusServiceUnavailable, "inference service is not available")
	}

	if request.Specification.ExpiresAt.Time().Before(time.Now()) {
		return orcapi.ApiTokenStatus{}, util.HttpErr(http.StatusBadRequest, "requested token has already expired")
	}

	if err := inferenceValidateRequestedPermissions(request.Specification.RequestedPermissions); err != nil {
		return orcapi.ApiTokenStatus{}, err
	}

	secret := util.SecureToken()
	hashedToken := util.HashPassword(secret, util.GenSalt())

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into inference_api_keys(token_id, owner, token_hash, token_salt, expires_at)
				values (:token_id, :owner, :token_hash, :token_salt, :expires_at)
				on conflict (token_id) do update
				set
					owner = excluded.owner,
					token_hash = excluded.token_hash,
					token_salt = excluded.token_salt,
					expires_at = excluded.expires_at
			`,
			db.Params{
				"token_id":   request.Id,
				"owner":      request.Owner.Project.GetOrDefault(request.Owner.CreatedBy),
				"token_hash": hashedToken.HashedPassword,
				"token_salt": hashedToken.Salt,
				"expires_at": request.Specification.ExpiresAt.Time(),
			},
		)
	})

	status := orcapi.ApiTokenStatus{Server: inferenceServerBase()}
	status.Token.Set(fmt.Sprintf("uci-%s-%s", request.Id, secret))
	return status, nil
}

func inferenceServerBase() string {
	scheme, _, ok := strings.Cut(cfg.Provider.Hosts.SelfPublic.ToURL(), "://")
	if !ok {
		scheme = "https"
	}

	return fmt.Sprintf("%s://chat%s/v1", scheme, shared.ServiceConfig.Compute.Web.Suffix)
}

func inferenceParseToken(raw string) (tokenId string, secret string, ok bool) {
	payload, hasPrefix := strings.CutPrefix(raw, "uci-")
	if !hasPrefix {
		return "", "", false
	}

	tokenId, secret, ok = strings.Cut(payload, "-")
	if !ok || tokenId == "" || secret == "" {
		return "", "", false
	}

	return tokenId, secret, true
}

func inferenceValidateRequestedPermissions(perms []orcapi.ApiTokenPermission) *util.HttpError {
	for _, perm := range perms {
		if perm.Name != "inference" {
			return util.HttpErr(
				http.StatusBadRequest,
				"invalid token requested, %s/%s is not available",
				perm.Name,
				perm.Action,
			)
		}
	}

	return nil
}

func inferenceRevokeApiToken(info rpc.RequestInfo, request fnd.FindByStringId) (util.Empty, *util.HttpError) {
	log.Info("Revoking inference API token: tokenId=%s user=%s", request.Id, info.Actor.Username)

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
			delete from inference_api_keys where token_id = :token_id
		    `,
			db.Params{
				"token_id": request.Id,
			},
		)
	})

	if cacheKey, ok := inferenceTokenIdToKey.GetNow(request.Id); ok {
		inferenceApiKeysCache.Invalidate(cacheKey)
	}

	inferenceTokenIdToKey.Invalidate(request.Id)

	return util.Empty{}, nil
}

func inferenceRetrieveApiTokenOptions(info rpc.RequestInfo, request util.Empty) (orcapi.ApiTokenOptions, *util.HttpError) {
	_ = info
	_ = request

	return orcapi.ApiTokenOptions{
		AvailablePermissions: []orcapi.ApiTokenPermissionSpecification{
			{
				Name:        "inference",
				Title:       "Inference",
				Description: "API token required for inference services",
				Actions: map[string]string{
					"use": "Use",
				},
			},
		},
	}, nil
}
