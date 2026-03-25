import {apiBrowse, apiDelete, apiRetrieve, apiUpdate} from "@/Authentication/DataHook";
import {BulkRequest, FindByStringId, PageV2, PaginationRequestV2} from "@/UCloud";
import {ResourcePermissions, UpdatedAcl} from "@/UCloud/ResourceApi";

const baseContext = "/api/jobs/stacks";

export interface Stack {
    id: string;
    name?: string;
    type?: string;
    createdAt: number;
    permissions?: ResourcePermissions;
}

export function browse(request: PaginationRequestV2): APICallParameters<PaginationRequestV2, PageV2<Stack>> {
    return apiBrowse(request, baseContext);
}

export function retrieve(request: FindByStringId): APICallParameters<FindByStringId, Stack> {
    return apiRetrieve(request, baseContext);
}

export function updateAcl(
    request: BulkRequest<UpdatedAcl>
): APICallParameters<BulkRequest<UpdatedAcl>> {
    return apiUpdate(request, baseContext, "updateAcl");
}

export function remove(
    request: BulkRequest<FindByStringId>
): APICallParameters<BulkRequest<FindByStringId>> {
    return apiDelete(request, baseContext);
}
