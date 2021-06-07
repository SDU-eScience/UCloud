import * as UCloud from ".";
import {accounting, BulkRequest, BulkResponse, PageV2, PaginationRequestV2} from ".";
import ProductReference = accounting.ProductReference;
import Product = accounting.Product;
import {buildQueryString} from "Utilities/URIUtilities";

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

    public abstract title: string;

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
