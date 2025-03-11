import { PageV2, compute } from "@/UCloud";
import { api as JobsApi, Job, isJobStateFinal } from "@/UCloud/JobsApi";
import { callAPI, apiRetrieve, apiUpdate } from "@/Authentication/DataHook";

export interface SyncthingConfig {
    devices: SyncthingDevice[];
    folders: SyncthingFolder[];
}

export interface SyncthingDevice {
    deviceId: string;
    label: string;
}

export interface SyncthingFolder {
    id: string;
    path?: string;
    ucloudPath: string;
}

export async function fetchConfig(provider: string): Promise<SyncthingConfig> {
    const resp = await callAPI<SyncthingConfigResponse>(api.retrieveConfiguration(provider, "syncthing"));
    return resp.config;
}

export async function fetchConfigAndEtag(provider: string): Promise<[SyncthingConfig, string]> {
    const resp = await callAPI<SyncthingConfigResponse>(api.retrieveConfiguration(provider, "syncthing"));
    return [resp.config, resp.etag];
}

export async function fetchProducts(provider: string): Promise<compute.ComputeProductSupportResolved[]> {
    const products = await callAPI<compute.JobsRetrieveProductsResponse>(compute.jobs.retrieveProducts({
        providers: provider
    }));
    return products.productsByProvider[provider]?.filter(it => it.product.name === "syncthing") ?? [];
}

export async function fetchServers(): Promise<Job[]> {
    const resp = await callAPI<PageV2<Job>>(
        {
            ...JobsApi.browse({
                filterApplication: "syncthing",
                filterProductId: "syncthing",
                includeParameters: true,
                sortBy: "createdAt",
                sortDirection: "descending",
                itemsPerPage: 250
            }),
            projectOverride: ""
        }
    );


    return resp.items.filter(it => !isJobStateFinal(it.status.state));
}

class Api {
    baseContext = "/api/iapps/syncthing";

    retrieveConfiguration(provider: string, productId: string): APICallParameters {
        return apiRetrieve({ provider, productId }, this.baseContext);
    }

    updateConfiguration(request: UpdateConfigRequest): APICallParameters {
        return apiUpdate(request, this.baseContext, "update");
    }

    resetConfiguration(request: ResetConfigRequest): APICallParameters {
        return apiUpdate(request, this.baseContext, "reset");
    }

    restart(request: RestartRequest): APICallParameters {
        return apiUpdate(request, this.baseContext, "restart");
    }
}


export interface SyncthingConfigResponse {
    etag: string;
    config: SyncthingConfig;
}

export interface UpdateConfigRequest {
    provider: string;
    productId: string;
    config: SyncthingConfig;
    expectedETag?: string | null;
}

export interface ResetConfigRequest {
    provider: string;
    productId: string;
    expectedETag?: string | null;
}

export interface RestartRequest {
    provider: string;
    productId: string;
}

export const api = new Api();

