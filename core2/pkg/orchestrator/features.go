package orchestrator

import (
	"encoding/json"
	"net/http"
	"slices"
	"strings"
	"sync"
	"time"
	accapi "ucloud.dk/shared/pkg/accounting"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type SupportFeatureKey string

type providerSupport struct {
	Type      string
	AppliesTo accapi.ProductReference
	Features  map[SupportFeatureKey]string
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
					featureFetchProviderSupport(
						provider,
						orcapi.DrivesProviderRetrieveProducts,
						driveType,
						supportMap,
						func(item orcapi.FSSupport) accapi.ProductReference {
							return item.Product
						},
					)

					featureFetchProviderSupport(
						provider,
						orcapi.JobsProviderRetrieveProducts,
						jobType,
						supportMap,
						func(item orcapi.JobSupport) accapi.ProductReference {
							return item.Product
						},
					)

					featureFetchProviderSupport(
						provider,
						orcapi.IngressesProviderRetrieveProducts,
						ingressType,
						supportMap,
						func(item orcapi.IngressSupport) accapi.ProductReference {
							return item.Product
						},
					)

					featureFetchProviderSupport(
						provider,
						orcapi.PublicIpsProviderRetrieveProducts,
						publicIpType,
						supportMap,
						func(item orcapi.PublicIpSupport) accapi.ProductReference {
							return item.Product
						},
					)

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

func featureFetchProviderSupport[T any](
	providerId string,
	call rpc.Call[util.Empty, fndapi.BulkResponse[T]],
	resourceType string,
	supportMap map[string][]providerSupport,
	productGetter func(item T) accapi.ProductReference,
) {
	resp, err := InvokeProvider(providerId, call, util.Empty{}, ProviderCallOpts{
		Reason: util.OptValue("Periodic pull for supported features"),
	})

	if err == nil {
		var supportItems []providerSupport
		for _, item := range resp.Responses {
			p := productGetter(item)
			obj, _ := json.Marshal(item)

			support := providerSupport{}
			support.Type = resourceType
			support.Features = readSupportFromLegacy(obj)
			support.AppliesTo = p
			supportItems = append(supportItems, support)
		}
		supportMap[resourceType] = supportItems
	}
}

func featureSupported(typeName string, product accapi.ProductReference, feature SupportFeatureKey) bool {
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

func walkFeatureObject(node json.RawMessage, path string, output map[SupportFeatureKey]string) {
	if path != "product" {
		// Leaf
		var b bool
		if err := json.Unmarshal(node, &b); err == nil {
			if b {
				for _, l := range featureMapperLegacy {
					if l.Path == path {
						output[l.Key] = ""
						return
					}
				}
			}
		}

		var s string
		if err := json.Unmarshal(node, &s); err == nil {
			for _, l := range featureMapperLegacy {
				if l.Path == path {
					output[l.Key] = s
					return
				}
			}
		}

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

func readSupportFromLegacy(obj json.RawMessage) map[SupportFeatureKey]string {
	result := map[SupportFeatureKey]string{}
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
						featureValue, hasFeature := support.Features[feature.Key]
						components := strings.Split(feature.Path, ".")
						currentMap := legacyMap
						for i, comp := range components {
							leaf := i == len(components)-1
							if leaf {
								if featureValue == "" {
									currentMap[comp] = hasFeature
								} else {
									currentMap[comp] = featureValue
								}
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
				case ingressType:
					productRelevant = product.Type == accapi.ProductTypeCIngress
				case publicIpType:
					productRelevant = product.Type == accapi.ProductTypeCNetworkIp
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
	// TODO It seems like this function needs to wait for at least one round of support retrieval before being okay
	//   with returning

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

func SupportByProduct[S any](typeName string, product accapi.ProductReference) (ProductSupport[S], bool) {
	var result orcapi.ResolvedSupport[S]
	all := SupportRetrieveProducts[S](typeName)
	byProvider, ok := all.ProductsByProvider[product.Provider]
	if !ok {
		return ProductSupport[S]{result}, false
	}

	for _, item := range byProvider {
		if item.Product.Name == product.Id && item.Product.Category.Name == product.Category {
			return ProductSupport[S]{item}, true
		}
	}

	return ProductSupport[S]{result}, false
}

type ProductSupport[T any] struct {
	orcapi.ResolvedSupport[T]
}

func (t *ProductSupport[T]) ToApi() orcapi.ResolvedSupport[T] {
	return t.ResolvedSupport
}

func (t *ProductSupport[T]) Has(key SupportFeatureKey) bool {
	for _, feature := range t.Features {
		if feature == string(key) {
			return true
		}
	}
	return false
}

func (t *ProductSupport[T]) Get(key SupportFeatureKey) (string, bool) {
	obj, _ := json.Marshal(t.Support)
	m := readSupportFromLegacy(obj)
	value, ok := m[key]
	return value, ok
}

type featureMapper struct {
	Type string
	Key  SupportFeatureKey
	Path string
}

var featureMapperLegacy = util.Combined(
	driveFeatureMapper,
	jobFeatureMapper,
	ingressFeatureMapper,
	publicIpFeatureMapper,
)

var featureNotSupportedError = &util.HttpError{
	StatusCode: http.StatusBadRequest,
	Why:        "This operation is not supported by this provider",
	ErrorCode:  "FEATURE_NOT_SUPPORTED_BY_PROVIDER",
}
