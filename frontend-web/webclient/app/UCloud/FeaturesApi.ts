import {apiBrowse, apiRetrieve, apiUpdate} from "@/Authentication/DataHook";

export interface FeatureGrant {
    feature: string;
    projectId: string;
    projectTitle: string;
    grantedAt: number;
    grantedBy: string;
}

export interface FeatureToggle {
    feature: string;
    title: string;
    description: string;
    enabled: boolean;
}

export interface FeatureToggleUpdateRequest {
    feature: string;
    enabled: boolean;
}

export interface FeatureAdminEntry {
    feature: string;
    title: string;
    description: string;
    toggleable: boolean;
    globallyEnabled: boolean;
    configEnabled: boolean;
}

export interface FeatureGlobalUpdateRequest {
    feature: string;
    enabled: boolean;
}

export class FeaturesApi {
    baseContext = "/api/features";

    public retrieveEnabled(): APICallParameters<unknown, string[]> {
        return apiRetrieve({}, this.baseContext, "enabled");
    }

    public retrieveToggles(): APICallParameters<unknown, FeatureToggle[]> {
        return apiRetrieve({}, this.baseContext, "toggles");
    }

    public updateToggle(request: FeatureToggleUpdateRequest): APICallParameters {
        return apiUpdate(request, this.baseContext, "toggleUser");
    }

    public browse(): APICallParameters<unknown, FeatureGrant[]> {
        return apiBrowse({}, this.baseContext);
    }

    public grant(request: {feature: string; projectId: string}): APICallParameters {
        return apiUpdate(request, this.baseContext, "grant");
    }

    public revoke(request: {feature: string; projectId: string}): APICallParameters {
        return apiUpdate(request, this.baseContext, "revoke");
    }

    public browseAdmin(): APICallParameters<unknown, FeatureAdminEntry[]> {
        return apiBrowse({}, this.baseContext, "admin");
    }

    public updateGlobal(request: FeatureGlobalUpdateRequest): APICallParameters {
        return apiUpdate(request, this.baseContext, "global");
    }
}

const featuresApi = new FeaturesApi();
export {featuresApi};
