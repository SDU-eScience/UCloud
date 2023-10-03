import {apiRetrieve, apiUpdate} from "@/Authentication/DataHook";
import * as Accounting from "@/Accounting";

export interface Affiliations {
    allocators: Allocator[];
}

export interface Allocator {
    id: string;
    title: string;
    description: string;
    template: string;
    categories: Accounting.ProductCategoryV2[];
}

const baseContext = "/api/grant";

export type RetrieveAffiliations2Request =
    { type: "PersonalWorkspace" }
    | { type: "NewProject", title: string }
    | { type: "ExistingProject", id: string }
    | { type: "ExistingApplication", id: string }
    ;

export function retrieveAffiliations2(
    request:  RetrieveAffiliations2Request
): APICallParameters<RetrieveAffiliations2Request, Affiliations> {
    return apiUpdate(request, baseContext, "retrieveAffiliations2");
}
