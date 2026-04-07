import {apiRetrieve} from "@/Authentication/DataHook";

export interface BrandingLink {
    title: string
    href: string
}

export enum BrandingLoginPageType {
    brandingLoginPageGeneric = 0,
    brandingLoginPageDeic = 1
}

export interface BrandingLoginPage {
    type: BrandingLoginPageType
    primaryLogoUrl: string
    secondaryLogoUrls: string[]
}

export interface BrandingResponse {
    deploymentName: string
    dataProtection?: BrandingLink
    statusPage?: BrandingLink
    documentation?: BrandingLink
    supportEmail?: string
    faqLink?: string
    loginPage: BrandingLoginPage

}

export class BrandingApi {
    baseContext = "/api/branding";
    public retrieve(): APICallParameters<unknown, BrandingResponse> {
        return apiRetrieve({}, this.baseContext);
    }
}

const brandingApi = new BrandingApi();
export { brandingApi };