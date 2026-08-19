import {callAPI} from "@/Authentication/DataHook";
import {ProviderBranding, providerBrandingApi, ProviderBrandingResponse} from "@/UCloud/ProviderBrandingApi";
import {ExternalStoreBase} from "@/Utilities/ReduxUtilities";
import ProviderInfo from "@/Assets/provider_info.json";


export const providerBrandingStore = new class extends ExternalStoreBase {
    private branding: ProviderBrandingResponse = {providers: {}};

    async onInit(): Promise<void> {
        this.fetch();
        window.setInterval(() => {
            this.fetch();
        }, 3 * 600_000);
    }

    async fetch() {
        try {
            const response = await callAPI(providerBrandingApi.browse());
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
        if (!property) console.warn(`Property '${providerProperty}' missing for ${providerId}`, this.branding);
        return property ? property : ProviderInfo.providers.find(it => it.id === providerId)?.[providerProperty as string];
    }
}

providerBrandingStore.onInit();
