import * as React from "react";
import {
    ProductSupport,
    Resource,
    ResourceApi, ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate
} from "@/UCloud/ResourceApi";
import {Icon} from "@/ui-components";
import {EnumFilter} from "@/Resource/Filter";
import {ItemRenderer} from "@/ui-components/Browse";
import {ProductIngress} from "@/Accounting";

export interface IngressSpecification extends ResourceSpecification {
    domain: string;
}

export type IngressState = "READY" | "PREPARING" | "UNAVAILABLE";

export interface IngressStatus extends ResourceStatus {
    boundTo: string[];
    state: IngressState;
}

export interface IngressSupport extends ProductSupport {
    domainPrefix: string;
    domainSuffix: string;
}

export interface IngressUpdate extends ResourceUpdate {
    state?: IngressState;
    didBind: boolean;
    newBinding?: string;
}

export interface IngressFlags extends ResourceIncludeFlags {
    filterState?: string;
}

export type Ingress = Resource<IngressUpdate, IngressStatus, IngressSpecification>;

class IngressApi extends ResourceApi<Ingress, ProductIngress, IngressSpecification, IngressUpdate,
    IngressFlags, IngressStatus, IngressSupport> {
    routingNamespace = "public-links";
    title = "Public Link";
    productType = "INGRESS" as const;

    renderer: ItemRenderer<Ingress> = {
        Icon({resource, size}) {return <Icon name={"globeEuropeSolid"} size={size}/>},
        MainTitle({resource}) {return <>{resource?.specification?.domain ?? ""}</>}
    };

    constructor() {
        super("ingresses");

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

        this.sortEntries.push({
            icon: "globeEuropeSolid",
            title: "Domain",
            column: "domain"
        });
    }
}

export default new IngressApi();
