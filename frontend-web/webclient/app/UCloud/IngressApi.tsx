import * as React from "react";
import {
    ProductSupport,
    Resource,
    ResourceApi, ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate
} from "UCloud/ResourceApi";
import {accounting} from "UCloud/index";
import ProductNS = accounting.ProductNS;
import {SidebarPages} from "ui-components/Sidebar";
import {Icon} from "ui-components";
import {CheckboxFilter, DateRangeFilter, EnumFilter, TextFilter} from "Resource/Filter";

export interface IngressSpecification extends ResourceSpecification {
    domain: string;
}

export type IngressState = "READY" | "PREPARING" | "UNAVAILABLE";

export interface IngressStatus extends ResourceStatus {
    boundTo?: string;
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

export interface Ingress extends Resource<IngressUpdate, IngressStatus, IngressSpecification> {}

class IngressApi extends ResourceApi<Ingress, ProductNS.Ingress, IngressSpecification, IngressUpdate,
    IngressFlags, IngressStatus, IngressSupport> {
    routingNamespace = "public-links";
    title = "Public Link";
    page = SidebarPages.Runs;

    TitleRenderer = ({resource}) => resource.specification.domain
    IconRenderer = ({resource, size}) => <Icon name={"globeEuropeSolid"} size={size}/>
    NameRenderer = this.TitleRenderer;

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
    }
}

export default new IngressApi();
