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
import {JobBinding} from "UCloud/JobsApi";

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

export interface License extends Resource<LicenseUpdate, LicenseStatus, LicenseSpecification> {}

class LicenseApi extends ResourceApi<License, ProductNS.License, LicenseSpecification, LicenseUpdate,
    LicenseFlags, LicenseStatus, LicenseSupport> {
    routingNamespace = "licenses";
    title = "Software License";
    page = SidebarPages.Runs;

    InlineTitleRenderer = ({resource}) =>
        <>{(resource as License).specification.product.id} ({(resource as License).id})</>
    IconRenderer = ({resource, size}) => <Icon name={"fileSignatureSolid"} size={size}/>
    TitleRenderer = this.InlineTitleRenderer;

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
