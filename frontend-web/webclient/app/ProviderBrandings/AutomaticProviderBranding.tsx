import * as React from "react";
import {callAPI} from "@/Authentication/DataHook";
import {ProviderBranding, providerBrandingApi, ProviderBrandingResponse} from "@/UCloud/ProviderBrandingApi";
import {ExternalStoreBase} from "@/Utilities/ReduxUtilities";
import ProviderInfo from "@/Assets/provider_info.json";

class ProviderBrandingStore extends ExternalStoreBase {
    private branding: ProviderBrandingResponse = {providers: {}};
    private probedLogoUrls = new Set<string>();
    private failedLogoUrls = new Set<string>();

    constructor() {
        super();
        this.fetch();
        window.setInterval(() => {
            this.fetch();
        }, 1000 * 60 * 60);
    }

    async fetch() {
        try {
            const request: APICallParameters<unknown, ProviderBrandingResponse> = {
                ...providerBrandingApi.browse(),
                unauthenticated: true,
            };
            const response = await callAPI<ProviderBrandingResponse>(request);
            this.branding = response;
            this.emitChange();
            this.probeLogos();
        } catch (e: any) {
            console.warn(e);
        }
    }

    private probeLogos() {
        for (const provider of Object.values(this.branding.providers)) {
            const logo = provider.logo;
            if (!logo) continue;

            const url = providerLogoUrl(logo);
            if (this.probedLogoUrls.has(url)) continue;
            this.probedLogoUrls.add(url);

            const probe = new Image();
            probe.onerror = () => {
                this.failedLogoUrls.add(url);
                this.emitChange();
            };
            probe.src = url;
        }
    }

    public getSnapshot(): Readonly<ProviderBrandingResponse> {
        return this.branding;
    }

    public getProviderProperty<Property extends keyof ProviderBranding>(providerId: string, providerProperty: Property): ProviderBranding[Property] | undefined {
        const property = this.branding.providers[providerId]?.[providerProperty];
        const logoFailed = property != null && providerProperty === "logo" &&
            this.failedLogoUrls.has(providerLogoUrl(String(property)));
        if (property && !logoFailed) {
            return property;
        }
        return ProviderInfo.providers.find(it => it.id === providerId)?.[providerProperty as string];
    }
}

export const providerBrandingStore = new ProviderBrandingStore();

export function useProviderBrandings(): Record<string, ProviderBranding> {
    const snapshot = React.useSyncExternalStore(
        sub => providerBrandingStore.subscribe(sub),
        () => providerBrandingStore.getSnapshot()
    );
    return snapshot.providers;
}

export function useProviderBranding(providerId?: string): ProviderBranding | undefined {
    const providers = useProviderBrandings();
    if (!providerId) return undefined;
    return providers[providerId];
}

export function useProviderProperty<Property extends keyof ProviderBranding>(providerId: string, providerProperty: Property): ProviderBranding[Property] | undefined {
    React.useSyncExternalStore(
        sub => providerBrandingStore.subscribe(sub),
        () => providerBrandingStore.getSnapshot()
    );
    return providerBrandingStore.getProviderProperty(providerId, providerProperty);
}

export function useProviderLogoUrl(providerId: string): string | undefined {
    const logo = useProviderProperty(providerId, "logo");
    if (!logo) return undefined;
    return providerLogoUrl(logo);
}

export function providerLogoUrl(logo: string): string {
    if (logo.includes("/")) return logo;
    return `/Images/${logo}`;
}
