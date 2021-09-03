import * as React from "react";
import {
    ProductSupport,
    Resource,
    ResourceApi, ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate
} from "UCloud/ResourceApi";
import {SidebarPages} from "ui-components/Sidebar";
import {Icon} from "ui-components";
import {EnumFilter} from "Resource/Filter";
import {JobBinding} from "UCloud/JobsApi";
import {ItemRenderer} from "ui-components/Browse";
import {ProductSyncFolder} from "Accounting";

export interface SyncFolderSpecification extends ResourceSpecification {
    path: string;
}

export type SyncFolderState = "READY" | "PREPARING" | "UNAVAILABLE";

export interface SyncFolderStatus extends ResourceStatus {
    boundTo: string[];
    state: SyncFolderState;
}

export interface SyncFolderSupport extends ProductSupport {
    binding?: JobBinding;
}

export interface SyncFolderUpdate extends ResourceUpdate {
    state?: SyncFolderState;
    didBind: boolean;
    newBinding?: string;
}

export interface SyncFolderFlags extends ResourceIncludeFlags {
    filterState?: string;
}

export interface SyncFolder extends Resource<SyncFolderUpdate, SyncFolderStatus, SyncFolderSpecification> {};

class SyncFolderApi extends ResourceApi<SyncFolder, ProductSyncFolder, SyncFolderSpecification, SyncFolderUpdate,
    SyncFolderFlags, SyncFolderStatus, SyncFolderSupport> {
    routingNamespace = "syncfolders";
    title = "Sync Title";
    page = SidebarPages.Runs;

    renderer: ItemRenderer<SyncFolder> = {
        MainTitle: ({resource}) =>
            resource ? <>{resource.specification.product.id} ({(resource as SyncFolder).id})</> : <></>,
        Icon: ({resource, size}) => <Icon name={"fileSignatureSolid"} size={size}/>
    };

    constructor() {
        super("sync.folders");

        this.registerFilter(EnumFilter(
            "radioEmpty",
            "filterState",
            "Status",
            [
                {
                    title: "Preparing",
                    value: "PREPARING",
                    icon: "hashtag"
                },
                {
                    title: "Ready",
                    value: "READY",
                    icon: "hashtag"
                },
                {
                    title: "Unavailable",
                    value: "UNAVAILABLE",
                    icon: "hashtag"
                }
            ]
        ));
    }
}

export default new SyncFolderApi();
