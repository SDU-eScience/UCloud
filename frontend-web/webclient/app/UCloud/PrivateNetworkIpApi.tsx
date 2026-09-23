import * as React from "react";
import {Product, productTypeToIcon} from "@/Accounting";
import {Icon} from "@/ui-components";
import {Resource, ResourceApi, ResourceIncludeFlags, ResourceSpecification, ResourceStatus, ResourceUpdate} from "@/UCloud/ResourceApi";
import {ItemRenderer} from "@/ui-components/Browse";

export interface PrivateNetworkIpSpecification extends ResourceSpecification {
    network: string;
    ipAddress?: string;
}

export interface PrivateNetworkIpStatus extends ResourceStatus {
    ipAddress?: string;
}

export type PrivateNetworkIp = Resource<ResourceUpdate, PrivateNetworkIpStatus, PrivateNetworkIpSpecification>;

class PrivateNetworkIpApi extends ResourceApi<PrivateNetworkIp, Product, PrivateNetworkIpSpecification, ResourceUpdate,
    ResourceIncludeFlags, PrivateNetworkIpStatus> {
    routingNamespace = "private-network-ips";
    title = "Private network IP";
    productType = "PRIVATE_NETWORK_IP" as const;

    renderer: ItemRenderer<PrivateNetworkIp> = {
        MainTitle({resource}) {
            return <>{resource?.status.ipAddress ?? resource?.specification.ipAddress ?? "Pending address"}</>;
        },
        Icon({size}) {
            return <Icon name={productTypeToIcon("PRIVATE_NETWORK_IP")} size={size} />;
        },
    };

    constructor() {
        super("private-network-ips");
    }
}

export default new PrivateNetworkIpApi();
