import * as React from "react";
import {
    CREATE_TAG,
    DELETE_TAG,
    ProductSupport,
    Resource,
    ResourceApi, ResourceBrowseCallbacks, ResourceIncludeFlags,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate
} from "@/UCloud/ResourceApi";
import {ItemRenderer} from "@/ui-components/Browse";
import {Product} from "@/Accounting";
import {PrettyFilePath} from "@/Files/FilePath";
import {Operation} from "@/ui-components/Operation";
import {Client} from "@/Authentication/HttpClientInstance";
import {accounting, BulkRequest, FindByStringId, PaginationRequestV2} from "@/UCloud";
import {apiBrowse, apiCreate, apiRetrieve, apiUpdate} from "@/Authentication/DataHook";
import {bulkRequestOf} from "@/DefaultObjects";
import ProductReference = accounting.ProductReference;
import {ValuePill} from "@/Resource/Filter";

export interface ShareSpecification extends ResourceSpecification {
    sharedWith: string;
    sourceFilePath: string;
    permissions: ("READ" | "EDIT")[];
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
    filterIngoing?: boolean | null;
    filterRejected?: boolean | null;
    filterOriginalPath?: string | null;
}

export type Share = Resource<ShareUpdate, ShareStatus, ShareSpecification>;

export interface OutgoingShareGroup {
    sourceFilePath: string;
    storageProduct: ProductReference;
    sharePreview: OutgoingShareGroupPreview[];
}

export interface OutgoingShareGroupPreview {
    sharedWith: string;
    permissions: ("READ" | "EDIT")[];
    state: ShareState;
    shareId: string;
}

export interface ShareLink {
    token: string;
    expires: number;
    permissions: ("READ" | "EDIT")[];
}

export interface CreateLinkRequest {
    path: string;
}

export interface BrowseLinksRequest {
    path: string;
}

export interface RetrieveLinkRequest {
    token: string;
}

export interface RetrieveLinkResponse {
    token: string;
    path: string;
    sharedBy: string;
    sharePath?: string;
}

export interface DeleteLinkRequest {
    token: string;
    path: string;
}

export interface UpdateLinkRequest {
    token: string;
    path: string;
    permissions: string[];
}

export interface AcceptLinkRequest {
    token: string;
}

class ShareApi extends ResourceApi<Share, Product, ShareSpecification, ShareUpdate,
    ShareFlags, ShareStatus, ShareSupport> {
    routingNamespace = "shares";
    title = "File Share";
    productType = "STORAGE" as const;

    renderer: ItemRenderer<Share, ResourceBrowseCallbacks<Share>> = {
        MainTitle() {
            return <div />
        },

        Icon() {
            return <div />;
        },

        ImportantStats() {
            return <div />
        }
    };

    constructor() {
        super("shares");

        this.filterPills.push(props => {
            return <ValuePill {...props} propertyName={"filterIngoing"} showValue={true} icon={"share"} title={""}
                valueToString={value => value === "true" ? "Shared with me" : "Shared by me"}
                canRemove={false} />
        });

        this.filterPills.push(props => {
            return <ValuePill {...props} propertyName={"filterOriginalPath"} showValue={false} icon={"ftFolder"}
                title={"Path"} canRemove={false}>
                <PrettyFilePath path={props.properties["filterOriginalPath"]} />
            </ValuePill>
        });
    }

    retrieveOperations(): Operation<Share, ResourceBrowseCallbacks<Share>>[] {
        const baseOperations = super.retrieveOperations().filter(op => {
            return op.tag !== CREATE_TAG;
        });
        const deleteOp = baseOperations.find(it => it.tag === DELETE_TAG);
        if (deleteOp) {
            const enabled = deleteOp.enabled;
            deleteOp.enabled = (selected, cb, all) => {
                const isEnabled = enabled(selected, cb, all);
                if (isEnabled !== true) return isEnabled;
                return selected.every(share => share.owner.createdBy === Client.username);
            };
            /* Note(Jonas):
                As Shares currently don't have a properties page, why remove the context redirection on delete.
             */
            deleteOp.onClick = async (selected, cb) => {
                await cb.invokeCommand(cb.api.remove(bulkRequestOf(...selected.map(it => ({id: it.id})))));
                cb.reload();
            }
        }

        return [
            {
                text: "Accept",
                icon: "check",
                color: "green",
                confirm: true,
                primary: true,
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
                primary: true,
                enabled: (selected, cb) => {
                    return selected.length > 0 && selected.every(share =>
                        share.owner.createdBy !== Client.username && share.status.state === "PENDING"
                    )
                },
                onClick: async (selected, cb) => {
                    await cb.invokeCommand(
                        this.reject(bulkRequestOf(...selected.map(share => ({id: share.id}))))
                    );

                    cb.reload();
                }
            },
            {
                text: "Remove",
                icon: "close",
                color: "red",
                confirm: true,
                primary: true,
                enabled: (selected, cb) => {
                    return selected.length > 0 && selected.every(share =>
                        share.owner.createdBy !== Client.username && share.status.state === "APPROVED"
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

    updatePermissions(request: BulkRequest<{id: string, permissions: ("READ" | "EDIT")[]}>): APICallParameters {
        return apiUpdate(request, this.baseContext, "permissions");
    }

    browseOutgoing(request: PaginationRequestV2): APICallParameters {
        return apiBrowse(request, this.baseContext, "outgoing");
    }
}

export class ShareLinksApi {
    baseContext = "/api/shares/links"

    public create(request: CreateLinkRequest): APICallParameters {
        return apiCreate(request, this.baseContext);
    }

    public browse(request: PaginationRequestV2 & BrowseLinksRequest): APICallParameters {
        return apiBrowse(request, this.baseContext);
    }

    public retrieve(request: RetrieveLinkRequest): APICallParameters {
        return apiRetrieve(request, this.baseContext)
    }

    public delete(request: DeleteLinkRequest): APICallParameters {
        return apiUpdate(request, this.baseContext, "delete");
    }

    public update(request: UpdateLinkRequest): APICallParameters {
        return apiUpdate(request, this.baseContext, "update");
    }

    public accept(request: AcceptLinkRequest): APICallParameters {
        return apiUpdate(request, this.baseContext, "accept")
    }
}

const shareLinksApi = new ShareLinksApi();
export {shareLinksApi};
export default new ShareApi();
