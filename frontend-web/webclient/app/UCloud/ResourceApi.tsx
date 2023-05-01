import * as UCloud from ".";
import * as React from "react";
import {accounting, BulkRequest, BulkResponse, PageV2, PaginationRequestV2} from ".";
import ProductReference = accounting.ProductReference;
import {SidebarPages} from "@/ui-components/SidebarPagesEnum";
import {apiBrowse, apiCreate, apiDelete, apiRetrieve, apiSearch, apiUpdate, InvokeCommand} from "@/Authentication/DataHook";
import {Operation} from "@/ui-components/Operation";
import {dialogStore} from "@/Dialog/DialogStore";
import {ResourcePermissionEditor} from "@/Resource/PermissionEditor";
import {doNothing} from "@/UtilityFunctions";
import {bulkRequestOf} from "@/DefaultObjects";
import {DateRangeFilter, FilterWidgetProps, PillProps, TextFilter} from "@/Resource/Filter";
import {IconName} from "@/ui-components/Icon";
import {Dispatch} from "redux";
import {ResourceProperties} from "@/Resource/Properties";
import {ItemRenderer} from "@/ui-components/Browse";
import {Product, ProductType} from "@/Accounting";
import {NavigateFunction} from "react-router";

export interface ProductSupport {
    product: ProductReference;
    maintenance?: Maintenance | null;
}

export interface Maintenance {
    description: string;
    startsAt: number;
    estimatedEndsAt?: number | null;
    availability: MaintenanceAvailability;

    // NOTE(Dan): This property is only set by the frontend
    frontendAlwaysShow?: boolean;
}

export enum MaintenanceAvailability {
    MINOR_DISRUPTION = "MINOR_DISRUPTION",
    MAJOR_DISRUPTION = "MAJOR_DISRUPTION",
    NO_SERVICE = "NO_SERVICE",
}

export interface ResolvedSupport<P extends Product = Product, S extends ProductSupport = ProductSupport> {
    product: P;
    support: S;
}

export type ResourceStatus = UCloud.provider.ResourceStatus;
export type ResourceUpdate = UCloud.provider.ResourceUpdate;
export type ResourceOwner = UCloud.provider.ResourceOwner;
export type ResourceSpecification = UCloud.provider.ResourceSpecification;

export type Permission = "READ" | "EDIT" | "ADMIN";

export type AclEntity =
    {type: "project_group", projectId: string, group: string} |
    {type: "user", username: string};

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
    filterProvider?: string;
    filterProductId?: string;
    filterProductCategory?: string;
    hideProvider?: string;
    hideProductId?: string;
    hideProductCategory?: string;
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
    status: Status;
    updates: Update[];
    owner: ResourceOwner;
    permissions: ResourcePermissions;
}

export interface FindById {
    id: string;
}

export interface SupportByProvider<P extends Product = Product, S extends ProductSupport = ProductSupport> {
    productsByProvider: Record<string, ResolvedSupport<P, S>[]>;
}

export function findSupport<S extends ProductSupport = ProductSupport>(
    support: SupportByProvider,
    resource: Resource
): ResolvedSupport<Product, S> | null {
    const ref = resource.specification.product;
    return support.productsByProvider[ref.provider]
        ?.find(it => it.product.name === ref.id && it.product.category.name === ref.category) ?? null as any;
}

export interface ResourceBrowseCallbacks<Res extends Resource> {
    commandLoading: boolean;
    invokeCommand: InvokeCommand;
    reload: () => void;
    api: ResourceApi<Res, never>;
    isCreating: boolean;
    startCreation?: () => void;
    cancelCreation?: () => void;
    viewProperties?: (res: Res) => void;
    closeProperties?: () => void;
    onSelect?: (resource: Res) => void;
    onSelectRestriction?: (resource: Res) => boolean | string;
    embedded: boolean;
    dispatch: Dispatch;
    startRenaming?: (resource: Res, defaultValue: string) => void;
    navigate: NavigateFunction;
    supportByProvider: SupportByProvider;
    isWorkspaceAdmin: boolean;
    inPopIn?: boolean;
}

export interface SortFlags {
    sortBy?: string;
    sortDirection?: "ascending" | "descending";
}

export interface SortEntry {
    icon: IconName;
    title: string;
    column: string;
    helpText?: string;
}

export abstract class ResourceApi<Res extends Resource,
    Prod extends Product,
    Spec extends ResourceSpecification = ResourceSpecification,
    Update extends ResourceUpdate = ResourceUpdate,
    Flags extends ResourceIncludeFlags = ResourceIncludeFlags,
    Status extends ResourceStatus = ResourceStatus,
    Support extends ProductSupport = ProductSupport> {
    protected namespace: string;
    protected baseContext: string;

    public abstract productType?: ProductType;
    public abstract routingNamespace;
    public abstract title: string;
    public abstract page: SidebarPages;
    public isCoreResource: boolean = false;
    public defaultSortDirection: "ascending" | "descending" = "ascending";

    public filterWidgets: React.FunctionComponent<FilterWidgetProps>[] = [];
    public filterPills: React.FunctionComponent<PillProps>[] = [];
    public sortEntries: SortEntry[] = [
        {
            icon: "calendar",
            title: "Date created",
            column: "createdAt",
            helpText: "Date and time of initial creation"
        },
        {
            icon: "user",
            title: "Created by",
            column: "createdBy",
            helpText: "The user who initially created the resource"
        }
    ];

    public registerFilter([w, p]: [React.FunctionComponent<FilterWidgetProps>, React.FunctionComponent<PillProps>]): void {
        this.filterWidgets.push(w);
        this.filterPills.push(p);
    }

    public idIsUriEncoded = false;

    public getNamespace(): string {
        return this.namespace;
    }

    public abstract renderer: ItemRenderer<Res>;
    /*
    public InlineTitleRenderer?: React.FunctionComponent<{ resource: Res }>;
    public IconRenderer?: React.FunctionComponent<{ resource: Res | null; size: string; }>
    public StatsRenderer?: React.FunctionComponent<{ resource: Res }>;
    public TitleRenderer?: React.FunctionComponent<{ resource: Res }>;
    public ImportantStatsRenderer?: React.FunctionComponent<{ resource: Res }>;
     */
    public Properties: React.FunctionComponent<{
        resource?: Res;
        reload?: () => void;
        closeProperties?: () => void;
        api: ResourceApi<Res, Prod, Spec, Update, Flags, Status, Support>;
        embedded?: boolean;
        inPopIn?: boolean;
    }> = props => <ResourceProperties {...props} api={this} />

    protected constructor(namespace: string) {
        this.namespace = namespace;
        this.baseContext = "/api/" + namespace.replace(".", "/") + "/";

        this.registerFilter(TextFilter("user", "filterCreatedBy", "Created by"));
        this.registerFilter(DateRangeFilter("calendar", "Date created", "filterCreatedBefore", "filterCreatedAfter"));
        // TODO We need to add a pill for provider and product
    }

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
                enabled: (selected, cb) => selected.length === 1 && cb.onSelect !== undefined && (cb.onSelectRestriction?.(selected[0]) ?? true),
                canAppearInLocation: loc => loc === "IN_ROW",
                onClick: (selected, cb) => {
                    cb.onSelect!(selected[0]);
                }
            },
            {
                text: "Create " + this.title.toLowerCase(),
                icon: "upload",
                primary: true,
                canAppearInLocation: loc => loc !== "IN_ROW",
                enabled: (selected, cb) => {
                    return !(selected.length !== 0 || cb.startCreation == null || cb.isCreating);

                },
                onClick: (selected, cb) => cb.startCreation!(),
                tag: CREATE_TAG
            },
            {
                text: "Cancel",
                icon: "close",
                color: "red",
                canAppearInLocation: loc => loc === "SIDEBAR" || loc === "TOPBAR",
                primary: true,
                enabled: (selected, cb) => {
                    return cb.isCreating
                },
                onClick: (selected, cb) => cb.cancelCreation!(),
            },
            {
                text: "Permissions",
                icon: "share",
                enabled: (selected, cb) => {
                    return selected.length === 1 &&
                        selected[0].owner.project != null &&
                        cb.viewProperties != null &&
                        selected[0].permissions.myself.some(it => it === "ADMIN");
                },
                onClick: (selected, cb) => {
                    if (!cb.embedded) {
                        dialogStore.addDialog(
                            <ResourcePermissionEditor reload={cb.reload} entity={selected[0]} api={cb.api} />,
                            doNothing,
                            true
                        );
                    } else {
                        cb.viewProperties!(selected[0]);
                    }
                },
                tag: PERMISSIONS_TAG
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

                    if (!cb.viewProperties && !cb.embedded) {
                        cb.navigate(`/${cb.api.routingNamespace}`)
                    }
                },
                tag: DELETE_TAG
            },
            {
                text: "Properties",
                icon: "properties",
                enabled: (selected, cb) => selected.length === 1 && cb.viewProperties != null,
                onClick: (selected, cb) => {
                    cb.viewProperties!(selected[0]);
                },
                tag: PROPERTIES_TAG
            }
        ];
    }

    public get titlePlural(): string {
        if (this.title.endsWith("s")) return this.title + "es";
        return this.title + "s";
    }

    init(): APICallParameters {
        return apiUpdate({}, this.baseContext, "init");
    }

    browse(req: PaginationRequestV2 & Flags & SortFlags): APICallParameters<PaginationRequestV2 & Flags, PageV2<Res>> {
        return apiBrowse(req, this.baseContext);
    }

    retrieve(req: FindById & Flags): APICallParameters<FindById & Flags, Res> {
        return apiRetrieve(req, this.baseContext);
    }

    create(req: BulkRequest<Spec>): APICallParameters<BulkRequest<Spec>, BulkResponse<Record<string, never> | null>> {
        return apiCreate(req, this.baseContext);
    }

    remove(req: BulkRequest<FindById>): APICallParameters<BulkRequest<FindById>, BulkResponse<Record<string, never> | null>> {
        return apiDelete(req, this.baseContext);
    }

    retrieveProducts(): APICallParameters<Record<string, never>, SupportByProvider<Prod, Support>> {
        return apiRetrieve({}, this.baseContext, "products");
    }

    updateAcl(req: BulkRequest<UpdatedAcl>): APICallParameters<BulkRequest<UpdatedAcl>, BulkResponse<{} | null>> {
        return apiUpdate(req, this.baseContext, "updateAcl");
    }

    search(
        req: {query: string; flags: Flags;} & PaginationRequestV2 & SortFlags
    ): APICallParameters<{query: string; flags: Flags;} & PaginationRequestV2, PageV2<Res>> {
        return apiSearch(req, this.baseContext);
    }
}

export const PERMISSIONS_TAG = "permissions";
export const DELETE_TAG = "delete";
export const PROPERTIES_TAG = "properties";
export const CREATE_TAG = "create";
export const UCLOUD_CORE = "ucloud_core";
