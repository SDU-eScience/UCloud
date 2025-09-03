package orchestrator

import (
	"encoding/json"
	"net/http"
	"slices"
	"strings"
	"sync"
	"time"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/util"
)

type featureKey string

type providerSupport struct {
	Type      string
	AppliesTo accapi.ProductReference
	Features  map[featureKey]util.Empty
}

var providerSupportGlobals struct {
	Mu     sync.RWMutex
	ByType map[string]providerSupportByType
}

type providerSupportByType struct {
	Type       string
	ByProvider map[string][]providerSupport
}

func initFeatures() {
	providerSupportGlobals.ByType = map[string]providerSupportByType{}

	go func() {
		for {
			providers := db.NewTx(func(tx *db.Transaction) []string {
				rows := db.Select[struct{ ProviderId string }](
					tx,
					`
						select unique_name as provider_id
						from provider.providers
				    `,
					db.Params{},
				)

				result := make([]string, len(rows))
				for i, row := range rows {
					result[i] = row.ProviderId
				}
				return result
			})

			wg := sync.WaitGroup{}
			wg.Add(len(providers))

			// provider -> type -> support
			newSupport := map[string]map[string][]providerSupport{}

			for _, provider := range providers {
				supportMap := map[string][]providerSupport{}
				newSupport[provider] = supportMap
				go func() {
					{
						resp, err := InvokeProvider(provider, orcapi.DrivesProviderRetrieveProducts, util.Empty{}, ProviderCallOpts{
							Reason: util.OptValue("Periodic pull for supported features"),
						})

						if err == nil {
							var driveSupportItems []providerSupport
							for _, item := range resp.Responses {
								p := item.Product
								obj, _ := json.Marshal(item)

								support := providerSupport{}
								support.Type = driveType
								support.Features = readSupportFromLegacy(obj)
								support.AppliesTo = p
								driveSupportItems = append(driveSupportItems, support)
							}
							supportMap[driveType] = driveSupportItems
						}
					}

					{
						resp, err := InvokeProvider(provider, orcapi.JobsProviderRetrieveProducts, util.Empty{}, ProviderCallOpts{
							Reason: util.OptValue("Periodic pull for supported features"),
						})

						if err == nil {
							var driveSupportItems []providerSupport
							for _, item := range resp.Responses {
								p := item.Product
								obj, _ := json.Marshal(item)

								support := providerSupport{}
								support.Type = jobType
								support.Features = readSupportFromLegacy(obj)
								support.AppliesTo = p
								driveSupportItems = append(driveSupportItems, support)
							}
							supportMap[jobType] = driveSupportItems
						}
					}

					wg.Done()
				}()
			}

			wg.Wait()

			providerSupportGlobals.Mu.Lock()
			for provider, info := range newSupport {
				for typeName, support := range info {
					typeMap, ok := providerSupportGlobals.ByType[typeName]
					if !ok {
						typeMap = providerSupportByType{
							Type:       typeName,
							ByProvider: map[string][]providerSupport{},
						}
						providerSupportGlobals.ByType[typeName] = typeMap
					}

					typeMap.ByProvider[provider] = support
				}
			}
			providerSupportGlobals.Mu.Unlock()

			time.Sleep(10 * time.Second)
		}
	}()
}

func featureSupported(typeName string, product accapi.ProductReference, feature featureKey) bool {
	var s []providerSupport

	providerSupportGlobals.Mu.RLock()
	typeMap, ok := providerSupportGlobals.ByType[typeName]
	if ok {
		s = typeMap.ByProvider[product.Provider]
	}
	providerSupportGlobals.Mu.RUnlock()

	for _, item := range s {
		if item.AppliesTo == product {
			if item.Features != nil {
				_, hasFeature := item.Features[feature]
				return hasFeature
			} else {
				return false
			}
		}
	}

	return false
}

func walkFeatureObject(node json.RawMessage, path string, output map[featureKey]util.Empty) {
	if path != "product" {
		// Leaf
		var b bool
		if err := json.Unmarshal(node, &b); err == nil {
			if b {
				for _, l := range featureMapperLegacy {
					if l.Path == path {
						output[l.Key] = util.Empty{}
						break
					}
				}
			}
		} else {
			// Node
			var obj map[string]json.RawMessage
			if err := json.Unmarshal(node, &obj); err == nil {
				for key, child := range obj {
					childPath := key
					if path != "" {
						childPath = path + "." + key
					}
					walkFeatureObject(child, childPath, output)
				}
			}
		}
	}
}

func readSupportFromLegacy(obj json.RawMessage) map[featureKey]util.Empty {
	result := map[featureKey]util.Empty{}
	walkFeatureObject(obj, "", result)
	return result
}

func supportToApi(provider string, supportItems []providerSupport) []orcapi.ResolvedSupport[json.RawMessage] {
	allProducts := productsByProvider(provider)

	var result []orcapi.ResolvedSupport[json.RawMessage]
	for _, product := range allProducts {
		ref := product.ToReference()
		for _, support := range supportItems {
			if support.AppliesTo == ref {
				legacyMap := map[string]any{}
				for _, feature := range featureMapperLegacy {
					if feature.Type == support.Type {
						_, hasFeature := support.Features[feature.Key]
						components := strings.Split(feature.Path, ".")
						currentMap := legacyMap
						for i, comp := range components {
							leaf := i == len(components)-1
							if leaf {
								currentMap[comp] = hasFeature
							} else {
								child, hasMap := currentMap[comp]
								if !hasMap {
									newMap := map[string]any{}
									currentMap[comp] = newMap
									currentMap = newMap
								} else {
									currentMap = child.(map[string]any)
								}
							}
						}
					}
				}

				var features []string
				for feature, _ := range support.Features {
					features = append(features, string(feature))
				}
				slices.Sort(features)

				productRelevant := false
				switch support.Type {
				case driveType:
					productRelevant = product.Type == accapi.ProductTypeCStorage
				case jobType:
					productRelevant = product.Type == accapi.ProductTypeCCompute
				}

				if !productRelevant {
					continue
				}

				item := orcapi.ResolvedSupport[json.RawMessage]{
					Product:  product,
					Features: features,
				}

				legacyMap["product"] = product.ToReference()
				legacyData, _ := json.Marshal(legacyMap)
				item.Support = legacyData

				result = append(result, item)
				break
			}
		}
	}

	return result
}

func SupportRetrieveProducts[T any](typeName string) orcapi.SupportByProvider[T] {
	result := orcapi.SupportByProvider[T]{
		ProductsByProvider: make(map[string][]orcapi.ResolvedSupport[T]),
	}

	providerSupportGlobals.Mu.RLock()
	byType, ok := providerSupportGlobals.ByType[typeName]
	if ok {
		for provider, support := range byType.ByProvider {
			resolved := supportToApi(provider, support)
			var mapped []orcapi.ResolvedSupport[T]
			for _, item := range resolved {
				var mappedSupport T
				_ = json.Unmarshal(item.Support, &mappedSupport)
				mapped = append(mapped, orcapi.ResolvedSupport[T]{
					Product:  item.Product,
					Support:  mappedSupport,
					Features: item.Features,
				})
			}

			result.ProductsByProvider[provider] = mapped
		}
	}
	providerSupportGlobals.Mu.RUnlock()
	return result
}

func SupportByProduct[S any](typeName string, product accapi.ProductReference) (orcapi.ResolvedSupport[S], bool) {
	var result orcapi.ResolvedSupport[S]
	all := SupportRetrieveProducts[S](typeName)
	byProvider, ok := all.ProductsByProvider[product.Provider]
	if !ok {
		return result, false
	}

	for _, item := range byProvider {
		if item.Product.Name == product.Id && item.Product.Category.Name == product.Category {
			return item, true
		}
	}

	return result, false
}

type featureMapper struct {
	Type string
	Key  featureKey
	Path string
}

var featureMapperLegacy = util.Combined(
	driveFeatureMapper,
	jobFeatureMapper,
)

var featureNotSupportedError = &util.HttpError{
	StatusCode: http.StatusBadRequest,
	Why:        "This operation is not supported by this provider",
	ErrorCode:  "FEATURE_NOT_SUPPORTED_BY_PROVIDER",
}
