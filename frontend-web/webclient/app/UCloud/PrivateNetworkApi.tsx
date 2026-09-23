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

export interface PrivateNetworkSpecification extends ResourceSpecification {
    name: string;
    subdomain: string;
    cidr?: string;
}

export interface PrivateNetworkStatus extends ResourceStatus {
    members: string[];
    cidrBlock?: string;
}

export interface PrivateNetworkSupport extends ProductSupport {
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
    routingNamespace = "private-networks";
    title = "Private network";
    productType = "PRIVATE_NETWORK" as const;

    renderer: ItemRenderer<PrivateNetwork> = {
        MainTitle({resource}) {
            if (!resource) return <>Private network</>;
            return <>{resource.specification.name || resource.id}</>;
        },
        Icon({size}) {
            return <Icon name={productTypeToIcon("PRIVATE_NETWORK")} size={size} />;
        },
    };

    Properties = props => <PrivateNetworkProperties {...props} />;

    constructor() {
        super("private-networks");
    }

    public retrieveOperations(): Operation<PrivateNetwork, ResourceBrowseCallbacks<PrivateNetwork, Product>>[] {
        const ops = super.retrieveOperations();
        const create = ops.find(it => it.tag === CREATE_TAG);
        if (create) {
            create.text = "Create private network";
        }
        return ops;
    }
}

export default new PrivateNetworkApi();
