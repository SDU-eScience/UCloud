import { PageV2, compute } from "@/UCloud";
import { api as JobsApi, Job, isJobStateFinal } from "@/UCloud/JobsApi";
import { callAPI, apiRetrieve, apiUpdate } from "@/Authentication/DataHook";
import FileCollectionsApi, {FileCollectionSupport} from "@/UCloud/FileCollectionsApi";
import {retrieveSupportV2} from "@/UCloud/ResourceApi";
import {ProductV2Storage} from "@/Accounting";

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

export async function fetchProviders(): Promise<string[]> {
    const support = await retrieveSupportV2<ProductV2Storage, FileCollectionSupport>(FileCollectionsApi);
    const driveProviders = Object.entries(support.productsByProvider)
        .filter(([, products]) => products.some(it => it.support.collection.usersCanCreate))
        .map(([provider]) => provider)
        .sort((a, b) => a.localeCompare(b));

    const syncthingProducts = await Promise.all(driveProviders.map(fetchProducts));
    return driveProviders.filter((_, index) => syncthingProducts[index].length > 0);
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
        return {...apiRetrieve({ provider, productId }, this.baseContext), projectOverride: ""};
    }

    updateConfiguration(request: UpdateConfigRequest): APICallParameters {
        return {...apiUpdate(request, this.baseContext, "update"), projectOverride: ""};
    }

    resetConfiguration(request: ResetConfigRequest): APICallParameters {
        return {...apiUpdate(request, this.baseContext, "reset"), projectOverride: ""};
    }

    restart(request: RestartRequest): APICallParameters {
        return {...apiUpdate(request, this.baseContext, "restart"), projectOverride: ""};
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
