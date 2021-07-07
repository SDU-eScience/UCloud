import * as React from "react";
import {
    ProductSupport,
    Resource,
    ResourceApi, ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate
} from "UCloud/ResourceApi";
import {accounting, compute} from "UCloud/index";
import ProductNS = accounting.ProductNS;
import {SidebarPages} from "ui-components/Sidebar";
import {Icon} from "ui-components";
import {EnumFilter} from "Resource/Filter";
import {JobBinding} from "UCloud/JobsApi";
import PortRangeAndProto = compute.PortRangeAndProto;

export interface NetworkIPSpecification extends ResourceSpecification {
    firewall?: Firewall;
}

export interface Firewall {
    openPorts: PortRangeAndProto[];
}

export type NetworkIPState = "READY" | "PREPARING" | "UNAVAILABLE";

export interface NetworkIPStatus extends ResourceStatus {
    boundTo: string[];
    state: NetworkIPState;
    ipAddress?: string;
}

export interface NetworkIPSupport extends ProductSupport {
    binding?: JobBinding;
}

export interface NetworkIPUpdate extends ResourceUpdate {
    state?: NetworkIPState;
    didBind: boolean;
    newBinding?: string;
}

export interface NetworkIPFlags extends ResourceIncludeFlags {
    filterState?: string;
}

export interface NetworkIP extends Resource<NetworkIPUpdate, NetworkIPStatus, NetworkIPSpecification> {}

class NetworkIPApi extends ResourceApi<NetworkIP, ProductNS.NetworkIP, NetworkIPSpecification, NetworkIPUpdate,
    NetworkIPFlags, NetworkIPStatus, NetworkIPSupport> {
    routingNamespace = "public-ips";
    title = "Public IP";
    page = SidebarPages.Runs;

    InlineTitleRenderer = ({resource}) =>
        <>{(resource as NetworkIP).status.ipAddress ?? (resource as NetworkIP).id.toString()}</>
    IconRenderer = ({resource, size}) => <Icon name={"networkWiredSolid"} size={size}/>
    TitleRenderer = this.InlineTitleRenderer;

    constructor() {
        super("networkips");

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

export default new NetworkIPApi();
