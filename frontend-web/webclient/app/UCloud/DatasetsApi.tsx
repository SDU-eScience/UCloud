import {ProductStorage} from "@/Accounting";
import {apiUpdate} from "@/Authentication/DataHook";
import {BulkRequest, FindByStringId} from "@/UCloud/index";
import {ItemRenderer} from "@/ui-components/Browse";
import {
    Resource,
    ResourceApi,
    ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate,
} from "./ResourceApi";

/**
 * Prefix of the provider generated id of drives which hold the datasets (data archival) of a project,
 * e.g. "da-4f9ab1e2".
 */
export const DATASET_ARCHIVE_DRIVE_PREFIX = "da-";

// DatasetEntry is a file or folder which has been selected for inclusion in a dataset.
export interface DatasetEntry {
    id: string; // UCloud id of the selected file or folder
    path: string; // UCloud path of the selected file or folder, e.g. /123/folder/data.csv
}

// DatasetMetadata is the user-editable metadata of a dataset. It is sent to the publication target on publish.
export interface DatasetMetadata {
    title: string;
    description?: string;
    keywords?: string[];
}

export interface DatasetSpecification extends ResourceSpecification {
    // Entries is the initial selection of files and folders which are copied (snapshotted) into this dataset.
    entries: DatasetEntry[];
    title: string;
    description?: string;
    keywords?: string[];
}

export type DatasetState = "DRAFT" | "READY" | "PUBLISHING" | "PUBLISHED" | "FAILURE";

export interface DatasetStatus extends ResourceStatus {
    state: DatasetState;

    // Path is the UCloud path of the folder which backs this dataset, e.g. /<datasetsDriveId>/<datasetId>.
    // The folder can be browsed with the regular files API.
    path?: string;

    targetId?: string; // ID at the publication target, e.g. a Pure dataset UUID
    targetUrl?: string; // Link to the published record at the target
}

export type Dataset = Resource<ResourceUpdate, DatasetStatus, DatasetSpecification>;
export type DatasetUpdate = ResourceUpdate;
export type DatasetFlags = ResourceIncludeFlags;

export interface DatasetsUpdateEntriesRequest {
    id: string;
    addedEntries?: DatasetEntry[]; // files/folders which are copied into the dataset
    deletedEntries?: DatasetEntry[]; // files/folders which are removed from the dataset
}

export interface DatasetsUpdateMetadataRequest {
    id: string;
    newMetadata: DatasetMetadata;
}

class DatasetsApi extends ResourceApi<Dataset, ProductStorage, DatasetSpecification,
    ResourceUpdate, DatasetFlags, DatasetStatus> {
    routingNamespace = "datasets";
    title = "Dataset";
    productType = "STORAGE" as const;

    constructor() {
        super("datasets");
    }

    renderer: ItemRenderer<Dataset> = {};

    // DatasetsUpdateEntries changes the selection of files and folders of an existing dataset.
    // This is only allowed while the dataset has not been published. Adding new entries moves the dataset
    // back to DRAFT until they have been copied.
    updateEntries(
        request: BulkRequest<DatasetsUpdateEntriesRequest>
    ): APICallParameters<BulkRequest<DatasetsUpdateEntriesRequest>> {
        return apiUpdate(request, this.baseContext, "updateEntries");
    }

    updateMetadata(
        request: BulkRequest<DatasetsUpdateMetadataRequest>
    ): APICallParameters<BulkRequest<DatasetsUpdateMetadataRequest>> {
        return apiUpdate(request, this.baseContext, "updateMetadata");
    }

    // DatasetsPublish publishes a dataset to its publication target. Publishing is only allowed once
    // every selected entry has been copied into the dataset, that is while the dataset is READY.
    publish(
        request: BulkRequest<FindByStringId>
    ): APICallParameters<BulkRequest<FindByStringId>> {
        return apiUpdate(request, this.baseContext, "publish");
    }
}

const api = new DatasetsApi();
export {api};
export default api;