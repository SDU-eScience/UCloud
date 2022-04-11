import { PageV2 } from "@/UCloud";
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

function fetchConfigFake(): Promise<SyncthingConfig> {
    const devices: SyncthingDevice[] = [];

    for (let i = 0; i < 10; i++) {
        const device = {
            label: "Android " + i, 
            deviceId: "L5WRYM7-V6IDHTV-2V4MN67-2STUNXL-FLEX7QR-LD7UXEC-2QRUCQE-FUHWEQ" + i
        };
        devices.push(device);
    }

    const folders: SyncthingFolder[] = [];

    for (let i = 0; i < 5; i++) {
        const folder = {
            id: "Cuda" + i,
            ucloudPath: "/4/Jobs/Coder CUDA" + i
        };
        folders.push(folder);
    }

    
    return new Promise((resolve, reject) => {
        resolve({devices, folders});
    });
}

export async function fetchConfig(): Promise<SyncthingConfig> {
    const resp = await callAPI<SyncthingConfigResponse>(api.retrieveConfiguration("ucloud"));
    return resp.config;
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
                provider: "ucloud"
            }
        },
        permissions: { myself: ["ADMIN"], others: [] },
        updates: [],
        status: {
            state: "RUNNING",
            jobParametersJson: null,
        }
    }];

    return new Promise((resolve, reject) => resolve(servers));
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

    retrieveConfiguration(providerId: string): APICallParameters {
        return apiRetrieve({ providerId }, this.baseContext);
    }

    updateConfiguration(request: UpdateConfigRequest): APICallParameters {
        return apiUpdate(request, this.baseContext, "update");
    }

    resetConfiguration(request: ResetConfigRequest): APICallParameters {
        return apiUpdate(request, this.baseContext, "reset");
    }
}


export interface SyncthingConfigResponse {
    etag: string;
    config: SyncthingConfig;
}

export interface UpdateConfigRequest {
    providerId: string;
    config: SyncthingConfig;
    expectedETag?: string | null;
}

export interface ResetConfigRequest {
    providerId: string;
    expectedETag?: string | null;
}

export const api = new Api();

