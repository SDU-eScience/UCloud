import {apiBrowse, apiDelete, apiRetrieve, apiUpdate} from "@/Authentication/DataHook";
import {BulkRequest, FindByStringId, PageV2, PaginationRequestV2} from "@/UCloud";
import {ResourcePermissions, UpdatedAcl} from "@/UCloud/ResourceApi";
import {Job} from "@/UCloud/JobsApi";
import {NetworkIP} from "@/UCloud/NetworkIPApi";
import {License} from "@/UCloud/LicenseApi";
import {PublicLink} from "@/UCloud/PublicLinkApi";
import {PrivateNetwork} from "@/UCloud/PrivateNetworkApi";

const baseContext = "/api/jobs/stacks";

export interface Stack {
    id: string;
    type?: string;
    createdAt: number;
    permissions: ResourcePermissions;
    status?: StackStatus | null;
}

export interface StackStatus {
    ucxUiMode: string;
    ucxConnectJobId?: string | null;
    jobs: Job[];
    licenses: License[];
    publicIps: NetworkIP[];
    publicLinks: PublicLink[];
    networks: PrivateNetwork[];
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
