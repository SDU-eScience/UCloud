import * as React from "react";
import {
    ProductSupport,
    Resource,
    ResourceApi, ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate
} from "@/UCloud/ResourceApi";
import {SidebarPages} from "@/ui-components/Sidebar";
import {Icon} from "@/ui-components";
import {EnumFilter} from "@/Resource/Filter";
import {JobBinding} from "@/UCloud/JobsApi";
import {ItemRenderer} from "@/ui-components/Browse";
import {ProductLicense} from "@/Accounting";

export interface LicenseSpecification extends ResourceSpecification {
}

export type LicenseState = "READY" | "PREPARING" | "UNAVAILABLE";

export interface LicenseStatus extends ResourceStatus {
    boundTo: string[];
    state: LicenseState;
}

export interface LicenseSupport extends ProductSupport {
    binding?: JobBinding;
}

export interface LicenseUpdate extends ResourceUpdate {
    state?: LicenseState;
    didBind: boolean;
    newBinding?: string;
}

export interface LicenseFlags extends ResourceIncludeFlags {
    filterState?: string;
}

export type License = Resource<LicenseUpdate, LicenseStatus, LicenseSpecification>;

class LicenseApi extends ResourceApi<License, ProductLicense, LicenseSpecification, LicenseUpdate,
    LicenseFlags, LicenseStatus, LicenseSupport> {
    routingNamespace = "licenses";
    title = "Software License";
    page = SidebarPages.Runs;
    productType = "LICENSE" as const;

    renderer: ItemRenderer<License> = {
        MainTitle({resource}) {
            return resource ? <>{resource.specification.product.id} ({(resource as License).id})</> : <></>
        },
        Icon({resource, size}) {return <Icon name={"fileSignatureSolid"} size={size} />}
    };

    constructor() {
        super("licenses");

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

export default new LicenseApi();
