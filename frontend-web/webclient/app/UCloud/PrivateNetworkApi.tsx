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
    placeholderProduct,
} from "@/UCloud/ResourceApi";
import {Icon} from "@/ui-components";
import {ItemRenderer} from "@/ui-components/Browse";
import {Product, productTypeToIcon} from "@/Accounting";
import {ResourceProperties} from "@/Resource/Properties";
import {Operation} from "@/ui-components/Operation";

export interface PrivateNetworkSpecification extends ResourceSpecification {
    name: string;
    subdomain: string;
}

export interface PrivateNetworkStatus extends ResourceStatus {
    members: string[];
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

    Properties = props => {
        const resource = props.resource;
        const normalizedResource = !resource ? undefined : {
            ...resource,
            specification: {
                ...resource.specification,
                product: resource.specification.product ?? placeholderProduct(),
            },
        };

        return <ResourceProperties
            api={this}
            showPermissions={false}
            showPermissionsTable
            {...props}
            resource={normalizedResource}
        />;
    };

    constructor() {
        super("private-networks");
    }

    public retrieveOperations(): Operation<PrivateNetwork, ResourceBrowseCallbacks<PrivateNetwork>>[] {
        const ops = super.retrieveOperations();
        const create = ops.find(it => it.tag === CREATE_TAG);
        if (create) {
            create.text = "Create private network";
        }
        return ops;
    }
}

export default new PrivateNetworkApi();
