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

export async function fetchProducts(provider: string): Promise<compute.ComputeProductSupportResolved[]> {
    const resp = await callAPI<compute.JobsRetrieveProductsResponse>(compute.jobs.retrieveProducts({
        providers: provider
    }));

    return resp.productsByProvider[provider];
}

export function fetchServersFake(): Promise<Job[]> {
    const servers: Job[] = [{
        id: "1",
        createdAt: 0,
        owner: {
            createdBy: "user",
        },
        specification: {
            name: "Syncthing Default Server",
            application: { name: "syncthing", version: "1.2.3" },
            replicas: 1,
            parameters: {},
            resources: [],
            product: {
                category: "syncthing",
                id: "syncthing",
                provider: "development"
            }
        },
        permissions: { myself: ["ADMIN"], others: [] },
        updates: [],
        status: {
            state: "RUNNING",
            jobParametersJson: null,
        }
    }];

    return new Promise((resolve) => resolve(servers));
}

export async function fetchServers(): Promise<Job[]> {
    const resp = await callAPI<PageV2<Job>>(
        {
            ...JobsApi.browse({
                filterApplication: "syncthing",
                filterProductId: "syncthing",
                filterProductCategory: "syncthing",

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

    retrieveConfiguration(providerId: string, category: string): APICallParameters {
        return apiRetrieve({ providerId, category }, this.baseContext);
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
    providerId: string;
    category: string;
    config: SyncthingConfig;
    expectedETag?: string | null;
}

export interface ResetConfigRequest {
    providerId: string;
    category: string;
    expectedETag?: string | null;
}

export interface RestartRequest {
    providerId: string;
    category: string;
}

export const api = new Api();

