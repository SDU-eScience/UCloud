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
	AppliesTo struct {
		Provider string
		Category util.Option[string]
		Id       util.Option[string]
	}
	Features map[featureKey]util.Empty
}

var providerSupportGlobals struct {
	Mu     sync.RWMutex
	ByType map[string]providerSupportByType
}

type providerSupportByType struct {
	Type       string
	ByProvider map[string]providerSupport
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
			newSupport := map[string]map[string]providerSupport{}

			for _, provider := range providers {
				supportMap := map[string]providerSupport{}
				newSupport[provider] = supportMap
				go func() {
					resp, err := InvokeProvider(provider, orcapi.DrivesProviderRetrieveProducts, util.Empty{}, ProviderCallOpts{
						Reason: util.OptValue("Periodic pull for supported features"),
					})

					if err == nil {
						for _, item := range resp.Responses {
							p := item.Product
							obj, _ := json.Marshal(item)

							support := providerSupport{}
							support.Type = drive
							support.Features = readSupportFromLegacy(obj)
							support.AppliesTo.Provider = provider
							support.AppliesTo.Id.Set(p.Id)
							support.AppliesTo.Category.Set(p.Category)
							supportMap[support.Type] = support
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
							ByProvider: map[string]providerSupport{},
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
	s := providerSupport{}

	providerSupportGlobals.Mu.RLock()
	typeMap, ok := providerSupportGlobals.ByType[typeName]
	if ok {
		s = typeMap.ByProvider[product.Provider]
	}
	providerSupportGlobals.Mu.RUnlock()

	if s.Features != nil {
		_, hasFeature := s.Features[feature]
		return hasFeature
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

func supportToApi(support providerSupport) []orcapi.ResolvedSupport[json.RawMessage] {
	allProducts := productsByProvider(support.AppliesTo.Provider)

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

	var result []orcapi.ResolvedSupport[json.RawMessage]
	for _, product := range allProducts {
		productRelevant := false
		switch support.Type {
		case drive:
			productRelevant = product.Type == accapi.ProductTypeCStorage
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
			resolved := supportToApi(support)
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

type featureMapper struct {
	Type string
	Key  featureKey
	Path string
}

var featureMapperLegacy = []featureMapper{
	{
		Type: drive,
		Key:  driveAcl,
		Path: "collection.aclModifiable",
	},
	{
		Type: drive,
		Key:  driveManagement,
		Path: "collection.usersCanCreate",
	},
	{
		Type: drive,
		Key:  driveManagement,
		Path: "collection.usersCanDelete",
	},
	{
		Type: drive,
		Key:  driveManagement,
		Path: "collection.usersCanRename",
	},

	{
		Type: drive,
		Key:  "", // no longer supported but keep in legacy (always false)
		Path: "files.aclModifiable",
	},
	{
		Type: drive,
		Key:  driveOpsTrash,
		Path: "files.trashSupported",
	},
	{
		Type: drive,
		Key:  driveOpsReadOnly,
		Path: "files.isReadOnly",
	},
	{
		Type: drive,
		Key:  driveOpsSearch,
		Path: "files.searchSupported",
	},
	{
		Type: drive,
		Key:  driveOpsStreamingSearch,
		Path: "files.streamingSearchSupported",
	},
	{
		Type: drive,
		Key:  driveOpsShares,
		Path: "files.sharesSupported",
	},
	{
		Type: drive,
		Key:  driveOpsTerminal,
		Path: "files.openInTerminal",
	},

	{
		Type: drive,
		Key:  driveStatsSize,
		Path: "stats.sizeInBytes",
	},
	{
		Type: drive,
		Key:  driveStatsRecursiveSize,
		Path: "stats.sizeIncludingChildrenInBytes",
	},
	{
		Type: drive,
		Key:  driveStatsTimestamps,
		Path: "stats.modifiedAt",
	},
	{
		Type: drive,
		Key:  driveStatsTimestamps,
		Path: "stats.createdAt",
	},
	{
		Type: drive,
		Key:  driveStatsTimestamps,
		Path: "stats.accessedAt",
	},
	{
		Type: drive,
		Key:  driveStatsUnix,
		Path: "stats.unixPermissions",
	},
	{
		Type: drive,
		Key:  driveStatsUnix,
		Path: "stats.unixOwner",
	},
	{
		Type: drive,
		Key:  driveStatsUnix,
		Path: "stats.unixGroup",
	},
}

var featureNotSupportedError = &util.HttpError{
	StatusCode: http.StatusBadRequest,
	Why:        "This operation is not supported by this provider",
	ErrorCode:  "FEATURE_NOT_SUPPORTED_BY_PROVIDER",
}
