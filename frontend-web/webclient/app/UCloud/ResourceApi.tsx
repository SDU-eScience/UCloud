import * as UCloud from ".";
import * as React from "react";
import {accounting, BulkRequest, BulkResponse, PageV2, PaginationRequestV2} from ".";
import ProductReference = accounting.ProductReference;
import Product = accounting.Product;
import {buildQueryString} from "Utilities/URIUtilities";
import {SidebarPages} from "ui-components/Sidebar";
import {InvokeCommand} from "Authentication/DataHook";
import {Operation} from "ui-components/Operation";
import {dialogStore} from "Dialog/DialogStore";
import {ResourcePermissionEditor} from "Resource/PermissionEditor";
import {doNothing} from "UtilityFunctions";
import {bulkRequestOf} from "DefaultObjects";

export interface ProductSupport {
    product: ProductReference;
}

export interface ResolvedSupport {
    product: UCloud.accounting.Product;
    support: ProductSupport;
}

export type ResourceStatus = UCloud.provider.ResourceStatus;
export type ResourceUpdate = UCloud.provider.ResourceUpdate;
export type ResourceOwner = UCloud.provider.ResourceOwner;
export type ResourceSpecification = UCloud.provider.ResourceSpecification;

export type Permission = "READ" | "EDIT" | "ADMIN";

export type AclEntity =
    { type: "project_group", projectId: string, group: string } |
    { type: "user", username: string };

export interface ResourceAclEntry {
    entity: AclEntity;
    permissions: Permission[];
}

export interface ResourcePermissions {
    myself: Permission[];
    others?: ResourceAclEntry[];
}

export interface ResourceIncludeFlags {
    includeOthers?: boolean;
    includeSupport?: boolean;
    includeUpdates?: boolean;
}

export interface UpdatedAcl {
    id: string;
    added: ResourceAclEntry[];
    deleted: AclEntity[];
}

export interface Resource<Update extends ResourceUpdate = ResourceUpdate,
    Status extends ResourceStatus = ResourceStatus,
    Spec extends ResourceSpecification = ResourceSpecification> {
    id: string;
    createdAt: number;
    specification: Spec;
    status: ResourceStatus;
    updates: ResourceUpdate[];
    owner: ResourceOwner;
    permissions: ResourcePermissions;
}

export interface FindById {
    id: string;
}

export interface SupportByProvider {
    productsByProvider: Record<string, ResolvedSupport[]>;
}

export interface ResourceBrowseCallbacks<Res extends Resource> {
    commandLoading: boolean;
    invokeCommand: InvokeCommand;
    reload: () => void;
    api: ResourceApi<Res, never>;
    isCreating: boolean;
    startCreation?: () => void;
    viewProperties?: (res: Res) => void;
    closeProperties?: () => void;
    onSelect?: (resource: Res) => void;
    embedded: boolean;
}

export abstract class ResourceApi<Res extends Resource,
    Prod extends Product,
    Spec extends ResourceSpecification = ResourceSpecification,
    Update extends ResourceUpdate = ResourceUpdate,
    Flags extends ResourceIncludeFlags = ResourceIncludeFlags,
    Status extends ResourceStatus = ResourceStatus,
    Support extends ProductSupport = ProductSupport> {
    private namespace: string;
    private baseContext: string;

    protected constructor(namespace: string) {
        this.namespace = namespace;
        this.baseContext = "/api/" + namespace.replace(".", "/") + "/";
    }

    public abstract routingNamespace;
    public abstract title: string;
    public abstract page: SidebarPages;

    public TitleRenderer?: React.FunctionComponent<{ resource: Res }>;
    public IconRenderer?: React.FunctionComponent<{ resource: Res | null; size: string; }>
    public StatsRenderer?: React.FunctionComponent<{ resource: Res }>;
    public NameRenderer?: React.FunctionComponent<{ resource: Res }>;

    public retrieveOperations(): Operation<Res, ResourceBrowseCallbacks<Res>>[] {
        return [
            {
                text: "Back to " + this.titlePlural.toLowerCase(),
                primary: true,
                icon: "backward",
                enabled: (selected, cb) => cb.closeProperties != null,
                onClick: (selected, cb) => {
                    cb.closeProperties!();
                }
            },
            {
                text: "Use",
                primary: true,
                enabled: (selected, cb) => selected.length === 1 && cb.onSelect !== undefined,
                canAppearInLocation: loc => loc === "IN_ROW",
                onClick: (selected, cb) => {
                    cb.onSelect!(selected[0]);
                }
            },
            {
                text: "Create " + this.title.toLowerCase(),
                icon: "upload",
                color: "blue",
                primary: true,
                canAppearInLocation: loc => loc !== "IN_ROW",
                enabled: (selected, cb) => {
                    if (selected.length !== 0 || cb.startCreation == null) return false;
                    if (cb.isCreating) return "You are already creating a " + this.title.toLowerCase();
                    return true;
                },
                onClick: (selected, cb) => cb.startCreation!()
            },
            {
                text: "Permissions",
                icon: "share",
                enabled: (selected, cb) => selected.length === 1 && selected[0].owner.project != null
                    && cb.viewProperties != null,
                onClick: (selected, cb) => {
                    if (!cb.embedded) {
                        dialogStore.addDialog(
                            <ResourcePermissionEditor reload={cb.reload} entity={selected[0]} api={cb.api}/>,
                            doNothing,
                            true
                        );
                    } else {
                        cb.viewProperties!(selected[0]);
                    }
                }
            },
            {
                text: "Delete",
                icon: "trash",
                color: "red",
                confirm: true,
                enabled: (selected) => selected.length >= 1,
                onClick: async (selected, cb) => {
                    await cb.invokeCommand(cb.api.remove(bulkRequestOf(...selected.map(it => ({id: it.id})))));
                    cb.reload();
                    cb.closeProperties?.();
                }
            },
            {
                text: "Properties",
                icon: "properties",
                enabled: (selected, cb) => selected.length === 1 && cb.viewProperties != null,
                onClick: (selected, cb) => {
                    cb.viewProperties!(selected[0]);
                }
            }
        ];
    }

    public get titlePlural(): string {
        return this.title + "s";
    }

    browse(req: PaginationRequestV2 & Flags): APICallParameters<PaginationRequestV2 & Flags, PageV2<Res>> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString(this.baseContext + "browse", req),
            parameters: req
        };
    }

    retrieve(req: FindById & Flags): APICallParameters<FindById & Flags, Res> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString(this.baseContext + "retrieve", req),
            parameters: req
        };
    }

    create(req: BulkRequest<Spec>): APICallParameters<BulkRequest<Spec>, BulkResponse<{} | null>> {
        return {
            context: "",
            method: "POST",
            path: this.baseContext,
            payload: req,
            parameters: req
        };
    }

    remove(req: BulkRequest<FindById>): APICallParameters<BulkRequest<FindById>, BulkResponse<{} | null>> {
        return {
            context: "",
            method: "DELETE",
            path: this.baseContext,
            payload: req,
            parameters: req
        };
    }

    retrieveProducts(): APICallParameters<{}, SupportByProvider> {
        return {
            context: "",
            method: "GET",
            path: this.baseContext + "retrieveProducts",
        };
    }

    updateAcl(req: BulkRequest<UpdatedAcl>): APICallParameters<BulkRequest<UpdatedAcl>, BulkResponse<{} | null>> {
        return {
            context: "",
            method: "POST",
            path: this.baseContext + "updateAcl",
            payload: req,
            parameters: req
        };
    }
}
