import * as React from "react";
import {
    ProductSupport,
    Resource,
    ResourceApi, ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate
} from "UCloud/ResourceApi";
import {SidebarPages} from "ui-components/Sidebar";
import {Icon} from "ui-components";
import {ItemRenderer} from "ui-components/Browse";
import {Product} from "Accounting";
import {PrettyFilePath} from "Files/FilePath";

export interface ShareSpecification extends ResourceSpecification {
    sharedWith: string;
    sourceFilePath: string;
    permissions: ("READ" | "WRITE")[];
}

export type ShareState = "APPROVED" | "PENDING" | "REJECTED";

export interface ShareStatus extends ResourceStatus {
    shareAvailableAt?: string | null;
    state: ShareState;
}

export type ShareSupport = ProductSupport;

export interface ShareUpdate extends ResourceUpdate {
    newState: ShareState;
    shareAvailableAt?: string | null;
}

export interface ShareFlags extends ResourceIncludeFlags {
}

export type Share = Resource<ShareUpdate, ShareStatus, ShareSpecification>;

class ShareApi extends ResourceApi<Share, Product, ShareSpecification, ShareUpdate,
    ShareFlags, ShareStatus, ShareSupport> {
    routingNamespace = "shares";
    title = "File Share";
    page = SidebarPages.Shares;
    productType = "STORAGE" as const;

    renderer: ItemRenderer<Share> = {
        MainTitle({resource}) {
            return resource ? <><PrettyFilePath path={resource.specification.sourceFilePath} /></> : <></>
        },
        Icon({resource, size}) {return <Icon name={"ftSharesFolder"} size={size} />}
    };

    constructor() {
        super("shares");
    }
}

export default new ShareApi();
