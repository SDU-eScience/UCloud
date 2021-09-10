import * as React from "react";
import {
    DELETE_TAG,
    ProductSupport,
    Resource,
    ResourceApi, ResourceBrowseCallbacks, ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate
} from "UCloud/ResourceApi";
import {SidebarPages} from "ui-components/Sidebar";
import {Icon} from "ui-components";
import {ItemRenderer} from "ui-components/Browse";
import {Product} from "Accounting";
import {PrettyFilePath} from "Files/FilePath";
import {Operation} from "ui-components/Operation";
import {Client} from "Authentication/HttpClientInstance";
import {BulkRequest, FindByStringId} from "UCloud/index";
import {apiUpdate} from "Authentication/DataHook";
import {bulkRequestOf} from "DefaultObjects";
import {fileName} from "Utilities/FileUtilities";
import {Avatar} from "AvataaarLib";
import {defaultAvatar} from "UserSettings/Avataaar";
import {UserAvatar} from "AvataaarLib/UserAvatar";

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
            return resource ? <>
                {resource.owner.createdBy !== Client.username ?
                    fileName(resource.specification.sourceFilePath) :
                    <PrettyFilePath path={resource.specification.sourceFilePath}/>
                }
            </> : <></>
        },
        Icon({resource, size}) {
            if (resource?.owner?.createdBy === Client.username) {
                return <UserAvatar avatar={defaultAvatar} width={size} />;
            }
            return <Icon name={"ftSharesFolder"} size={size} color={"FtFolderColor"} color2={"FtFolderColor2"} />
        }
    };

    constructor() {
        super("shares");
    }

    retrieveOperations(): Operation<Share, ResourceBrowseCallbacks<Share>>[] {
        const baseOperations = super.retrieveOperations();
        const deleteOp = baseOperations.find(it => it.tag === DELETE_TAG);
        if (deleteOp) {
            const enabled = deleteOp.enabled;
            deleteOp.enabled = (selected, cb, all) => {
                const isEnabled = enabled(selected, cb, all);
                if (isEnabled !== true) return isEnabled;
                return selected.every(share => share.owner.createdBy === Client.username);
            };
        }

        return [
            {
                text: "Accept",
                icon: "check",
                color: "green",
                confirm: true,
                enabled: (selected, cb) => {
                    return selected.length > 0 && selected.every(share =>
                        share.status.state === "PENDING" &&
                        share.owner.createdBy !== Client.username
                    )
                },
                onClick: async (selected, cb) => {
                    await cb.invokeCommand(
                        this.approve(bulkRequestOf(...selected.map(share => ({id: share.id}))))
                    );

                    cb.reload();
                }
            },
            {
                text: "Decline",
                icon: "close",
                color: "red",
                confirm: true,
                enabled: (selected, cb) => {
                    return selected.length > 0 && selected.every(share =>
                        share.owner.createdBy !== Client.username
                    )
                },
                onClick: async (selected, cb) => {
                    await cb.invokeCommand(
                        this.reject(bulkRequestOf(...selected.map(share => ({id: share.id}))))
                    );

                    cb.reload();
                }
            },
            ...baseOperations
        ];
    }

    approve(request: BulkRequest<FindByStringId>): APICallParameters {
        return apiUpdate(request, this.baseContext, "approve");
    }

    reject(request: BulkRequest<FindByStringId>): APICallParameters {
        return apiUpdate(request, this.baseContext, "reject");
    }
}

export default new ShareApi();
