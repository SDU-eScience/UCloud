package orchestrator

import (
	"maps"
	"net/url"
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
		for {
			providers, providerIds := retrieveProviders()
			for idx, provider := range providers {
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
		providerBrandingsGlobals.Mu.RLock()
		result := orcapi.ProviderBrandingBrowseResponse{Providers: maps.Clone(providerBrandingsGlobals.ProviderBrandings)}
		providerBrandingsGlobals.Mu.RUnlock()
		return result, nil
	})
}

func brandingMonitorProvider(provider string) {
	failedAttemptCount := 0
	didComplain := false
	for {
		branding, err := InvokeProvider(provider, orcapi.ProviderBrandingRetrieve, util.Empty{}, ProviderCallOpts{})
		if err != nil {
			if !didComplain {
				log.Info("Failed to retrieve branding info of provider %s: %v", provider, err)
				didComplain = true
			}
			time.Sleep(util.ExponentialBackoffForNetwork(failedAttemptCount))
			failedAttemptCount++
			continue
		}
		failedAttemptCount = 0
		didComplain = false
		providerBrandingNormalizeUrls(provider, &branding)
		providerBrandingsGlobals.Mu.Lock()
		providerBrandingsGlobals.ProviderBrandings[provider] = branding
		providerBrandingsGlobals.Ready.Store(true)
		providerBrandingsGlobals.Mu.Unlock()
		time.Sleep(10 * time.Second)
	}
}

func providerBrandingNormalizeUrls(provider string, branding *orcapi.ProviderBranding) {
	providerDomain, ok := ProviderDomain(provider)
	if !ok {
		return
	}

	branding.Url = providerBrandingNormalizeSingleUrl(providerDomain, branding.Url)
	if branding.Logo.Present {
		branding.Logo.Value = providerBrandingNormalizeSingleUrl(providerDomain, branding.Logo.Value)
	}

	for idx := range branding.Sections {
		if branding.Sections[idx].Image.Present {
			branding.Sections[idx].Image.Value = providerBrandingNormalizeSingleUrl(providerDomain, branding.Sections[idx].Image.Value)
		}
	}

	for idx := range branding.ProductDescription {
		image := &branding.ProductDescription[idx].Section.Image
		if image.Present {
			image.Value = providerBrandingNormalizeSingleUrl(providerDomain, image.Value)
		}
	}
}

func providerBrandingNormalizeSingleUrl(providerDomain string, value string) string {
	if value == "" {
		return value
	}

	base, err := url.Parse(providerDomain)
	if err != nil {
		return value
	}
	relative, err := url.Parse(value)
	if err != nil || relative.IsAbs() || relative.Host != "" {
		return value
	}
	return base.ResolveReference(relative).String()
}
