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
import {SidebarPages} from "@/ui-components/Sidebar";
import {Flex, Icon, RadioTile, RadioTilesContainer} from "@/ui-components";
import {ItemRenderer} from "@/ui-components/Browse";
import {Product} from "@/Accounting";
import {PrettyFilePath} from "@/Files/FilePath";
import {Operation} from "@/ui-components/Operation";
import {Client} from "@/Authentication/HttpClientInstance";
import {accounting, BulkRequest, FindByStringId, PaginationRequestV2} from "@/UCloud";
import {apiBrowse, apiUpdate} from "@/Authentication/DataHook";
import {bulkRequestOf} from "@/DefaultObjects";
import {fileName} from "@/Utilities/FileUtilities";
import {defaultAvatar} from "@/UserSettings/Avataaar";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";
import {preventDefault, stopPropagation, useEffectSkipMount} from "@/UtilityFunctions";
import {useCallback, useState} from "react";
import ProductReference = accounting.ProductReference;
import {ValuePill} from "@/Resource/Filter";
import {useAvatars} from "@/AvataaarLib/hook";

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

class ShareApi extends ResourceApi<Share, Product, ShareSpecification, ShareUpdate,
    ShareFlags, ShareStatus, ShareSupport> {
    routingNamespace = "shares";
    title = "File Share";
    page = SidebarPages.Shares;
    productType = "STORAGE" as const;

    renderer: ItemRenderer<Share, ResourceBrowseCallbacks<Share>> = {
        MainTitle({resource}) {
            return resource ? <>
                {resource.owner.createdBy !== Client.username ?
                    fileName(resource.specification.sourceFilePath) :
                    resource.specification.sharedWith
                }
            </> : <></>
        },

        Icon({resource, size}) {
            const avatars = useAvatars();
            if (resource?.owner?.createdBy === Client.username) {
                return <UserAvatar
                    avatar={avatars.cache[resource!.specification.sharedWith] ?? defaultAvatar}
                    width={size}
                    height={size}
                    mx={"0"}
                />;
            }
            return <Icon name={"ftSharesFolder"} size={size} color={"FtFolderColor"} color2={"FtFolderColor2"} />;
        },

        ImportantStats({resource, callbacks}) {
            const [isEdit, setIsEdit] = useState(
                resource?.specification?.permissions?.some(it => it === "EDIT") === true
            );

            useEffectSkipMount(() => {
                setIsEdit(resource?.specification?.permissions?.some(it => it === "EDIT") === true)
            }, [resource?.specification.permissions]);

            const updatePermissions = useCallback(async (isEditing: boolean) => {
                if (!resource) return;

                setIsEdit(isEditing);
                const api = callbacks.api as ShareApi;
                await callbacks.invokeCommand(api.updatePermissions(bulkRequestOf(
                    {
                        id: resource.id,
                        permissions: isEditing ? ["READ", "EDIT"] : ["READ"]
                    }
                )));
                callbacks.reload();
            }, [resource, callbacks.invokeCommand, callbacks.reload]);

            const updatePermissionsRead = useCallback((e) => {
                updatePermissions(false);
            }, [updatePermissions]);

            const updatePermissionsEdit = useCallback((e) => {
                updatePermissions(true);
            }, [updatePermissions]);

            if (resource === undefined) return null;

            const sharedByMe = resource!.specification.sharedWith !== Client.username;

            return <Flex alignItems={"center"}>
                {resource.status.state !== "APPROVED" ? null :
                    <><Icon color={"green"} name={"check"} mr={8} /> Approved</>
                }
                {resource.status.state !== "PENDING" ? null :
                    <><Icon color={"blue"} name={"questionSolid"} mr={8} /> Pending</>
                }
                {resource.status.state !== "REJECTED" ? null :
                    <><Icon color={"red"} name={"close"} mr={8} /> Rejected</>
                }
                <form onSubmit={preventDefault} style={{marginLeft: "16px"}}>
                    <RadioTilesContainer height={48} onClick={stopPropagation} title="Share Permission">
                        {sharedByMe || !isEdit ? <RadioTile
                            disabled={resource.owner.createdBy !== Client.username}
                            label={"Read"}
                            onChange={updatePermissionsRead}
                            icon={"search"}
                            name={"READ"}
                            checked={!isEdit && sharedByMe}
                            height={40}
                            fontSize={"0.5em"}
                        /> : null}
                        {sharedByMe || isEdit ? <RadioTile
                            disabled={resource.owner.createdBy !== Client.username}
                            label={"Edit"}
                            onChange={updatePermissionsEdit}
                            icon={"edit"}
                            name={"EDIT"}
                            checked={isEdit && sharedByMe}
                            height={40}
                            fontSize={"0.5em"}
                        /> : null}
                    </RadioTilesContainer>
                </form>
            </Flex>;
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
                        share.owner.createdBy !== Client.username && share.status.state !== "REJECTED"
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

export default new ShareApi();
