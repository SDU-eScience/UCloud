import {
    Resource,
    ResourceOwner,
    ResourcePermissions,
    ResourceSpecification,
    ResourceUpdate
} from "@/UCloud/ResourceApi";
import {FindByStringId, PageV2, PaginationRequestV2, provider} from "@/UCloud";
import ResourceStatus = provider.ResourceStatus;
import {apiBrowse, apiCreate, apiRetrieve, apiUpdate} from "@/Authentication/DataHook";

export interface ApiToken extends Resource<ResourceUpdate, ApiTokenStatus, ApiTokenSpecification> {
    specification: ApiTokenSpecification;
    status: ApiTokenStatus;

    id: string;
    createdAt: number;
    updates: ResourceUpdate[];
    owner: ResourceOwner;
    permissions: ResourcePermissions;
}

export interface ApiTokenStatus extends ResourceStatus {
    // visible only during creation
    token: string | null;

    // URL. May include path if needed.
    server: string;
}

export interface ApiTokenSpecification extends ResourceSpecification {
    title: string;
    description: string;
    requestedPermissions: ApiTokenPermission[];
    expiresAt: number;

    // null implies UCloud itself
    provider: string | null;

    // NOTE(Dan): This doesn't actually have a product. API will ignore it.
}

export interface ApiTokenPermission {
    name: string;
    action: string;
}

export interface ApiTokenOptions {
    availablePermissions: ApiTokenPermissionSpecification[];
}

export interface ApiTokenPermissionSpecification {
    name: string;
    title: string;
    description: string;

    // name of action to human-readable format
    actions: Record<string, string>;
}

export interface ApiTokenRetrieveOptionsResponse {
    byProvider: Record<string, ApiTokenOptions>;
}

const baseContext = "/api/tokens";

export function create(request: ApiTokenSpecification): APICallParameters<ApiTokenSpecification, ApiToken> {
    return apiCreate(request, baseContext);
}

export function browse(request: PaginationRequestV2): APICallParameters<PaginationRequestV2, PageV2<ApiToken>> {
    return apiBrowse(request, baseContext);
}

export function revoke(request: FindByStringId): APICallParameters<FindByStringId> {
    return apiUpdate(request, baseContext, "revoke");
}

export function retrieveOptions(): APICallParameters<{}, ApiTokenRetrieveOptionsResponse> {
    return apiRetrieve({}, baseContext, "options");
}
