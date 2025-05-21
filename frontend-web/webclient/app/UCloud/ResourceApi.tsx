import * as UCloud from ".";
import * as React from "react";
import {accounting, BulkRequest, BulkResponse, PageV2, PaginationRequestV2} from ".";
import ProductReference = accounting.ProductReference;
import {
    apiBrowse,
    apiCreate,
    apiDelete,
    apiRetrieve,
    apiSearch,
    apiUpdate,
    callAPI,
    InvokeCommand
} from "@/Authentication/DataHook";
import {Operation, ShortcutKey} from "@/ui-components/Operation";
import {dialogStore} from "@/Dialog/DialogStore";
import {ResourcePermissionEditor} from "@/Resource/PermissionEditor";
import {doNothing, errorMessageOrDefault} from "@/UtilityFunctions";
import {bulkRequestOf} from "@/UtilityFunctions";
import {FilterWidgetProps, PillProps, SortEntry, SortFlags} from "@/Resource/Filter";
import {Dispatch} from "redux";
import {ResourceProperties} from "@/Resource/Properties";
import {ItemRenderer} from "@/ui-components/Browse";
import {Product, ProductType, ProductV2} from "@/Accounting";
import {NavigateFunction} from "react-router";
import {fetchAll} from "@/Utilities/PageUtilities";
import * as Accounting from "@/Accounting";
import {EmbeddedSettings} from "@/ui-components/ResourceBrowser";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {MainContainer} from "@/ui-components";

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

// NOTE(Dan): Due to time limitations we are sort of hacking this in via the frontend
export interface SupportByProviderV2<P extends ProductV2 = ProductV2, S extends ProductSupport = ProductSupport> extends SupportByProvider<Product, S> {
    newProducts: P[];
}

export function supportV2ProductMatch<P extends ProductV2 = ProductV2>(product: Product, support: SupportByProviderV2<P>): P {
    // NOTE(Dan): This should never fail. In this case I think it is worth simplifying calling code by assuming that
    // this either fails badly or not at all.
    return support.newProducts.find(it =>
        it.name === product.name &&
        it.category.name === product.category.name &&
        it.category.provider === product.category.provider
    )!;
}

export async function retrieveSupportV2<P extends ProductV2 = ProductV2, S extends ProductSupport = ProductSupport>(
    api: ResourceApi<any, any, any, any, any, any, S>
): Promise<SupportByProviderV2<P, S>> {
    const allProductsPromise = fetchAll(next =>
        callAPI(Accounting.browseProductsV2({itemsPerPage: 250, filterProductType: api.productType, next}))
    )
    const supportPromise = callAPI(api.retrieveProducts());

    const support = await supportPromise;
    const allProducts = await allProductsPromise;

    return {
        ...support,
        newProducts: allProducts as P[]
    };
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
    embedded?: EmbeddedSettings;
    dispatch: Dispatch;
    startRenaming?: (resource: Res, defaultValue: string) => void;
    navigate: NavigateFunction;
    supportByProvider: SupportByProvider; // As of today (Nov 8th, 2023) only FileCollectionsApi uses this in callbacks.
    isWorkspaceAdmin: boolean;
    creationDisabled?: boolean;
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
    }

    public idIsUriEncoded = false;

    public getNamespace(): string {
        return this.namespace;
    }

    public abstract renderer: ItemRenderer<Res>;
    public Properties: React.FunctionComponent<{
        resource?: Res;
        reload?: () => void;
        closeProperties?: () => void;
        api: ResourceApi<Res, Prod, Spec, Update, Flags, Status, Support>;
        embedded?: EmbeddedSettings;
    }> = props => <MainContainer main={<ResourceProperties {...props} api={this} />} />

    protected constructor(namespace: string) {
        this.namespace = namespace;
        this.baseContext = "/api/" + namespace.replace(".", "/") + "/";
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
                },
                shortcut: ShortcutKey.Backspace
            },
            {
                text: "Use",
                primary: true,
                icon: "check",
                enabled: (selected, cb) => selected.length === 1 && cb.onSelect !== undefined && (cb.onSelectRestriction?.(selected[0]) === true),
                onClick: (selected, cb) => {
                    cb.onSelect!(selected[0]);
                },
                shortcut: ShortcutKey.Enter
            },
            {
                text: "Create " + this.title.toLowerCase(),
                icon: "upload",
                primary: true,
                enabled: (selected, cb) => {
                    return !(selected.length !== 0 || cb.startCreation == null || cb.isCreating);

                },
                onClick: (selected, cb) => cb.startCreation!(),
                tag: CREATE_TAG,
                shortcut: ShortcutKey.Q
            },
            {
                text: "Cancel",
                icon: "close",
                color: "errorMain",
                primary: true,
                enabled: (selected, cb) => {
                    return cb.isCreating
                },
                onClick: (selected, cb) => cb.cancelCreation!(),
                shortcut: ShortcutKey.Y
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
                tag: PERMISSIONS_TAG,
                shortcut: ShortcutKey.W
            },
            {
                text: "Delete",
                icon: "trash",
                color: "errorMain",
                confirm: true,
                enabled: (selected) => selected.length >= 1,
                onClick: async (selected, cb) => {
                    try {
                        await cb.invokeCommand(cb.api.remove(bulkRequestOf(...selected.map(it => ({id: it.id})))));
                        cb.reload();
                        cb.closeProperties?.();

                        if (!cb.viewProperties && !cb.embedded) {
                            cb.navigate(`/${cb.api.routingNamespace}`)
                        }
                    } catch (e) {
                        snackbarStore.addFailure(errorMessageOrDefault(e, "Failed to delete item"), false);
                    }
                },
                tag: DELETE_TAG,
                shortcut: ShortcutKey.R
            },
            {
                text: "Properties",
                icon: "properties",
                enabled: (selected, cb) => selected.length === 1 && cb.viewProperties != null,
                onClick: (selected, cb) => {
                    cb.viewProperties!(selected[0]);
                },
                tag: PROPERTIES_TAG,
                shortcut: ShortcutKey.E,
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

export function placeholderProduct(): {id: "", category: "", provider: string} {
    return {"id": "", "category": "", "provider": UCLOUD_CORE};
}

