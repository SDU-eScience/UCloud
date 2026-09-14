import {apiBrowse, apiRetrieve, apiUpdate} from "@/Authentication/DataHook";

export interface FeatureGrant {
    feature: string;
    projectId: string;
    projectTitle: string;
    grantedAt: number;
    grantedBy: string;
}

export class FeaturesApi {
    baseContext = "/api/features";

    public retrieveEnabled(): APICallParameters<unknown, string[]> {
        return apiRetrieve({}, this.baseContext, "enabled");
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
}

const featuresApi = new FeaturesApi();
export {featuresApi};
