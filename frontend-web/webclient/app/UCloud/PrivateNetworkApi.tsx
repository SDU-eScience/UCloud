import * as React from "react";
import {
    CREATE_TAG,
    ProductSupport,
    Resource,
    ResourceApi,
    ResourceBrowseCallbacks,
    ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate,
} from "@/UCloud/ResourceApi";
import {Icon} from "@/ui-components";
import {ItemRenderer} from "@/ui-components/Browse";
import {Product, productTypeToIcon} from "@/Accounting";
import {Operation} from "@/ui-components/Operation";
import {PrivateNetworkProperties} from "@/Applications/PrivateNetwork/PrivateNetworkProperties";
import {PrivateNetworkType} from "@/Applications/PrivateNetwork/types";

export interface PrivateNetworkSpecification extends ResourceSpecification {
    name: string;
    type?: PrivateNetworkType;
    cidr?: string;
}

export interface PrivateNetworkStatus extends ResourceStatus {
    subdomain?: string;
    members: string[];
    cidrBlock?: string;
}

export interface PrivateNetworkSupport extends ProductSupport {
    networkType?: PrivateNetworkType;
    addressPools?: string[];
    excludedRanges?: string[];
    minPrefixLength?: number;
    maxPrefixLength?: number;
    rdmaResourceName?: string;
}

export interface PrivateNetworkFlags extends ResourceIncludeFlags {
}

export type PrivateNetwork = Resource<ResourceUpdate, PrivateNetworkStatus, PrivateNetworkSpecification>;

class PrivateNetworkApi extends ResourceApi<
    PrivateNetwork,
    Product,
    PrivateNetworkSpecification,
    ResourceUpdate,
    PrivateNetworkFlags,
    PrivateNetworkStatus,
    PrivateNetworkSupport
> {
    routingNamespace = "networks";
    title = "Network";
    productType = "PRIVATE_NETWORK" as const;

    renderer: ItemRenderer<PrivateNetwork> = {
        MainTitle({resource}) {
            if (!resource) return <>Network</>;
            return <>{resource.specification.name || resource.id}</>;
        },
        Icon({size}) {
            return <Icon name={productTypeToIcon("PRIVATE_NETWORK")} size={size} />;
        },
    };

    Properties = props => <PrivateNetworkProperties {...props} />;

    constructor() {
        super("networks");
    }

    public retrieveOperations(): Operation<PrivateNetwork, ResourceBrowseCallbacks<PrivateNetwork, Product>>[] {
        const ops = super.retrieveOperations();
        const create = ops.find(it => it.tag === CREATE_TAG);
        if (create) {
            create.text = "Create network";
        }
        return ops;
    }
}

export default new PrivateNetworkApi();
