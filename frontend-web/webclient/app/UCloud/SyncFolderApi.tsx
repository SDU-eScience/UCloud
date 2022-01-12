import * as React from "react";
import {
    ProductSupport,
    Resource,
    ResourceApi,
    ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate,
} from "@/UCloud/ResourceApi";
import { SidebarPages } from "@/ui-components/Sidebar";
import { Icon } from "@/ui-components";
import { ItemRenderer } from "@/ui-components/Browse";
import { ProductSyncFolder } from "@/Accounting";

enum SynchronizationType {
    SEND_RECEIVE = "SEND_RECEIVE",
    SEND_ONLY = "SEND_ONLY",
}

export interface SyncFolderSpecification extends ResourceSpecification {
    path: string;
}

export interface SyncFolderStatus extends ResourceStatus {
    deviceId: string;
    syncType: SynchronizationType;
}

export interface SyncFolderSupport extends ProductSupport {}

export interface SyncFolderUpdate extends ResourceUpdate {
    timestamp: number;
    status: string;
}

export interface SyncFolderFlags extends ResourceIncludeFlags {
    filterByPath?: string;
}

export interface SyncFolder
    extends Resource<
        SyncFolderUpdate,
        SyncFolderStatus,
        SyncFolderSpecification
    > {}

class SyncFolderApi extends ResourceApi<
    SyncFolder,
    ProductSyncFolder,
    SyncFolderSpecification,
    SyncFolderUpdate,
    SyncFolderFlags,
    SyncFolderStatus,
    SyncFolderSupport
> {
    routingNamespace = "sync-folders";
    title = "Synchronization Folder";
    page = SidebarPages.Files;
    productType = "SYNCHRONIZATION" as const;

    renderer: ItemRenderer<SyncFolder> = {
        MainTitle({ resource }) {
            return resource ? (
                <>
                    {resource.specification.product.id} (
                    {(resource as SyncFolder).id})
                </>
            ) : (
                <></>
            );
        },
        Icon({ resource, size }) {
            return <Icon name={"ftFolder"} size={size} />;
        },
    };

    constructor() {
        super("sync.folders");
    }
}

export default new SyncFolderApi();
