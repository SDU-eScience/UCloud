import * as Grants from "@/Grants";
import {apiBrowse, apiCreate, apiDelete, apiUpdate} from "@/Authentication/DataHook";
import {FindByLongId, PageV2, PaginationRequestV2} from "@/UCloud";
import {removeTrailingSlash} from "@/UtilityFunctions";

const baseContext = "/api/gifts";

export interface Gift {
    title: string;
    description: string;
    resources: Grants.AllocationRequest[];
    resourcesOwnedBy: string;
}

export interface GiftWithCriteria extends Gift {
    id: number;
    criteria: Grants.UserCriteria[];
}

export function claim(request: { giftId: number }): APICallParameters {
    return apiUpdate(request, baseContext, "claim");
}

export function available(): APICallParameters<unknown, { gifts: FindByLongId[] }> {
    return {
        context: "",
        method: "GET",
        path: removeTrailingSlash(baseContext) + "/" + "available",
    };
}

export function create(request: GiftWithCriteria): APICallParameters<unknown, FindByLongId> {
    return apiCreate(request, baseContext);
}

export function remove(request: { giftId: number }): APICallParameters {
    return apiDelete(request, baseContext);
}

export function browse(request: PaginationRequestV2): APICallParameters<unknown, PageV2<GiftWithCriteria>> {
    return apiBrowse(request, baseContext);
}