import {buildQueryString} from "Utilities/URIUtilities";

export interface CreatePublicLinkRequest {
    url: string;
}

export type CreatePublicLinkResponse = {};

export function createPublicLink(request: CreatePublicLinkRequest): APICallParameters<CreatePublicLinkRequest> {
    return {
        path: "/hpc/urls",
        method: "POST",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export interface PublicLink {
    url: string;
    inUseBy?: string;
    inUseByUIFriendly?: string;
}

export type ListPublicLinksRequest = PaginationRequest;
export type ListPublicLinksResponse = Page<PublicLink>;

export function listPublicLinks(request: ListPublicLinksRequest): APICallParameters<ListPublicLinksRequest> {
    return {
        path: buildQueryString("/hpc/urls", request),
        method: "GET",
        parameters: request,
        reloadId: Math.random()
    };
}

export interface DeletePublicLinkRequest {
    url: string;
}

export type DeletePublicLinkResponse = {};

export function deletePublicLink(request: DeletePublicLinkRequest): APICallParameters<DeletePublicLinkRequest> {
    return {
        path: "/hpc/urls",
        method: "DELETE",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}
