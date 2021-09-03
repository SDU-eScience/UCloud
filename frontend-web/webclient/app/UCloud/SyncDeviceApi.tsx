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
import {ProductSyncDevice} from "Accounting";

export interface SyncDeviceSpecification extends ResourceSpecification {
    deviceId: string;
}

export type SyncDeviceState = "READY" | "PREPARING" | "UNAVAILABLE";

export interface SyncDeviceStatus extends ResourceStatus {
    boundTo: string[];
    state: SyncDeviceState;
}

export interface SyncDeviceSupport extends ProductSupport {
    binding?: JobBinding;
}

export interface SyncDeviceUpdate extends ResourceUpdate {
    state?: SyncDeviceState;
    didBind: boolean;
    newBinding?: string;
}

export interface SyncDeviceFlags extends ResourceIncludeFlags {
    filterState?: string;
}

export interface SyncDevice extends Resource<SyncDeviceUpdate, SyncDeviceStatus, SyncDeviceSpecification> {};

class SyncDeviceApi extends ResourceApi<SyncDevice, ProductSyncDevice, SyncDeviceSpecification, SyncDeviceUpdate,
    SyncDeviceFlags, SyncDeviceStatus, SyncDeviceSupport> {
    routingNamespace = "sync_devices";
    title = "Sync Title";
    page = SidebarPages.Runs;

    renderer: ItemRenderer<SyncDevice> = {
        MainTitle: ({resource}) =>
            resource ? <>{resource.specification.product.id} ({(resource as SyncDevice).id})</> : <></>,
        Icon: ({resource, size}) => <Icon name={"fileSignatureSolid"} size={size}/>
    };

    constructor() {
        super("sync.devices");

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

export default new SyncDeviceApi();