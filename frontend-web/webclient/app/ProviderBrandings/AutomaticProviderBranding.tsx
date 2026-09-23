import * as React from "react";
import {callAPI} from "@/Authentication/DataHook";
import {ProviderBranding, providerBrandingApi, ProviderBrandingResponse} from "@/UCloud/ProviderBrandingApi";
import {ExternalStoreBase} from "@/Utilities/ReduxUtilities";
import ProviderInfo from "@/Assets/provider_info.json";

class ProviderBrandingStore extends ExternalStoreBase {
    private branding: ProviderBrandingResponse = {providers: {}};

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
        } catch (e: any) {
            console.warn(e);
        }
    }

    public getSnapshot(): Readonly<ProviderBrandingResponse> {
        return this.branding;
    }

    public getProviderProperty<Property extends keyof ProviderBranding>(providerId: string, providerProperty: Property): ProviderBranding[Property] | undefined {
        const property = this.branding.providers[providerId]?.[providerProperty];
        return property ? property : ProviderInfo.providers.find(it => it.id === providerId)?.[providerProperty as string];
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
    const branding = useProviderBranding(providerId);
    const property = branding?.[providerProperty];
    return property ? property : ProviderInfo.providers.find(it => it.id === providerId)?.[providerProperty as string];
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
