package orchestrator

import (
	"sync"
	"sync/atomic"
	"time"

	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var providerBrandingsGlobals struct {
	Mu                sync.RWMutex
	ProviderBrandings map[string]orcapi.ProviderBranding // by provider name
	ProviderById      map[int]string
	ProviderByName    map[string]int
	Ready             atomic.Bool
}

func retrieveProviders() ([]string, []int) {
	providers, providerIds := db.NewTx2(func(tx *db.Transaction) ([]string, []int) {
		rows := db.Select[struct {
			Resource     int
			ProviderName string
		}](
			tx,
			`
						select resource, unique_name as provider_name
						from provider.providers
				    `,
			db.Params{},
		)

		ids := make([]int, len(rows))
		names := make([]string, len(rows))
		for i, row := range rows {
			names[i] = row.ProviderName
			ids[i] = row.Resource
		}
		return names, ids
	})
	return providers, providerIds
}

func initProviderBrandings() {
	providerBrandingsGlobals.ProviderBrandings = make(map[string]orcapi.ProviderBranding)
	providerBrandingsGlobals.ProviderById = make(map[int]string)
	providerBrandingsGlobals.ProviderByName = make(map[string]int)
	providerBrandingsGlobals.Ready.Store(false)

	go func() {
		providersBeingMonitored := map[string]util.Empty{}
		log.Info("Starting monitoring provider brandings")
		for {
			providers, providerIds := retrieveProviders()
			for idx, provider := range providers {
				// Testing -----------
				InvokeProvider(provider, orcapi.ProviderBrandingRetrieveImage, orcapi.ProviderBrandingImageRequest{Name: "abc.png"}, ProviderCallOpts{})
				// Testing -----------
				_, isBeingMonitored := providersBeingMonitored[provider]
				if !isBeingMonitored {
					providersBeingMonitored[provider] = util.Empty{}
					providerBrandingsGlobals.Mu.Lock()
					providerBrandingsGlobals.ProviderById[providerIds[idx]] = provider
					providerBrandingsGlobals.ProviderByName[provider] = providerIds[idx]
					providerBrandingsGlobals.Mu.Unlock()
					go brandingMonitorProvider(provider)
				}

			}
			time.Sleep(10 * time.Second)
		}
	}()

	orcapi.ProviderBrandingBrowse.Handler(func(info rpc.RequestInfo, request util.Empty) (orcapi.ProviderBrandingBrowseResponse, *util.HttpError) {
		return orcapi.ProviderBrandingBrowseResponse{Providers: providerBrandingsGlobals.ProviderBrandings}, nil
	})
}

func brandingMonitorProvider(provider string) {
	failedAttemptCount := 0
	for {
		branding, err := InvokeProvider(provider, orcapi.ProviderBrandingRetrieve, util.Empty{}, ProviderCallOpts{})
		if err != nil {
			time.Sleep(util.ExponentialBackoffForNetwork(failedAttemptCount))
			log.Error("Failed to retrieve branding info of provider %s: %v", provider, err)
			failedAttemptCount++
			continue
		}
		failedAttemptCount = 0
		providerBrandingsGlobals.Mu.Lock()
		providerBrandingsGlobals.ProviderBrandings[provider] = branding
		providerBrandingsGlobals.Ready.Store(true)
		providerBrandingsGlobals.Mu.Unlock()
		time.Sleep(10 * time.Second)
	}
}
