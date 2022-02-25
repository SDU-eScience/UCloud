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
import { ProductSyncDevice } from "@/Accounting";

export interface SyncDeviceSpecification extends ResourceSpecification {
    deviceId: string;
}

export type SyncDeviceUpdate = ResourceUpdate;
export type SyncDeviceStatus = ResourceStatus;
export type SyncDeviceSupport = ProductSupport;
export type SyncDeviceFlags = ResourceIncludeFlags;
export interface SyncDevice
    extends Resource<
        SyncDeviceUpdate,
        SyncDeviceStatus,
        SyncDeviceSpecification
    > {}

class SyncDeviceApi extends ResourceApi<
    SyncDevice,
    ProductSyncDevice,
    SyncDeviceSpecification,
    SyncDeviceUpdate,
    SyncDeviceFlags,
    SyncDeviceStatus,
    SyncDeviceSupport
> {
    routingNamespace = "sync-devices";
    title = "Synchronization Device";
    page = SidebarPages.Runs;
    productType = "SYNCHRONIZATION" as const;

    renderer: ItemRenderer<SyncDevice> = {
        MainTitle({ resource }) {
            return resource ? <>{resource.specification.deviceId}</> : <></>;
        },
        Icon({ resource, size }) {
            return <Icon name={"hdd"} size={size} />;
        },
    };

    constructor() {
        super("sync.devices");
    }
}

export default new SyncDeviceApi();
