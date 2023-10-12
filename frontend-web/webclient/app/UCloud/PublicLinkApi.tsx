import * as React from "react";
import {
    ProductSupport,
    Resource,
    ResourceApi,
    ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate
} from "@/UCloud/ResourceApi";
import {Icon} from "@/ui-components";
import {EnumFilter} from "@/Resource/Filter";
import {ItemRenderer} from "@/ui-components/Browse";
import {ProductIngress as ProductPublicLink} from "@/Accounting";

export interface PublicLinkSpecification extends ResourceSpecification {
    domain: string;
}

export type PublicLinkState = "READY" | "PREPARING" | "UNAVAILABLE";

export interface PublicLinkStatus extends ResourceStatus {
    boundTo: string[];
    state: PublicLinkState;
}

export interface PublicLinkSupport extends ProductSupport {
    domainPrefix: string;
    domainSuffix: string;
}

export interface PublicLinkUpdate extends ResourceUpdate {
    state?: PublicLinkState;
    didBind: boolean;
    newBinding?: string;
}

export interface PublicLinkFlags extends ResourceIncludeFlags {
    filterState?: string;
}

export type PublicLink = Resource<PublicLinkUpdate, PublicLinkStatus, PublicLinkSpecification>;

class PublicLinkApi extends ResourceApi<PublicLink, ProductPublicLink, PublicLinkSpecification, PublicLinkUpdate,
    PublicLinkFlags, PublicLinkStatus, PublicLinkSupport> {
    routingNamespace = "public-links";
    title = "Public Link";
    productType = "INGRESS" as const;

    renderer: ItemRenderer<PublicLink> = {
        Icon({resource, size}) {return <Icon name={"globeEuropeSolid"} size={size} />},
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

export default new PublicLinkApi();
