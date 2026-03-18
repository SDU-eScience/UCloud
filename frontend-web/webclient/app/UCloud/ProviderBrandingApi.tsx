import {apiBrowse} from "@/Authentication/DataHook";

export interface ProviderBrandingSection {
    description: string
    image?: string
}

export interface ProviderBrandingCategory {
    name: string
    provider: string
}

export interface ProviderBrandingProductDescription {
    category: ProviderBrandingCategory
    shortDescription: string
    section: ProviderBrandingSection
}


export interface ProviderBranding {
    id: string
    title: string
    shortTitle: string
    shortDescription: string
    description: string
    url: string
    logo?: string
    sections: ProviderBrandingSection[]
    productDescription: ProviderBrandingProductDescription[]
}

export interface ProviderBrandingResponse {
    providers: Record<string, ProviderBranding>
}

export class ProviderBrandingApi {
    baseContext = "/api/providers/branding";
    public browse(): APICallParameters<unknown, ProviderBrandingResponse> {
        return apiBrowse({}, this.baseContext);
    }
}

const providerBrandingApi = new ProviderBrandingApi();
export { providerBrandingApi };