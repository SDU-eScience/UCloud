import {APICallParameters} from "Authentication/DataHook";
export {default as List} from "./List";
import {AccessRight} from "Types";
import {buildQueryString} from "Utilities/URIUtilities";

export interface ListProps {
    innerComponent?: boolean;
    byPath?: string;
    simple?: boolean;
}

export interface MinimalShare {
    state: ShareState;
    sharedWith: string;
    rights: AccessRight[];
}

export enum ShareState {
    REQUEST_SENT = "REQUEST_SENT",
    ACCEPTED = "ACCEPTED",
    UPDATING = "UPDATING"
}

export interface SharesByPath {
    path: string;
    sharedBy: string;
    sharedByMe: boolean;
    shares: MinimalShare[];
}

export const findShare = (path: string): APICallParameters => ({
    method: "GET",
    path: buildQueryString("/shares/byPath", {path}),
    reloadId: Math.random()
});

export interface ListSharesParams {
    sharedByMe: boolean;
    itemsPerPage: number;
    page: number;
}

export const listShares = ({
    sharedByMe,
    itemsPerPage,
    page
}: ListSharesParams): APICallParameters<ListSharesParams> => ({
    method: "GET",
    path: buildQueryString("/shares", {itemsPerPage, page, sharedByMe}),
    parameters: {sharedByMe, itemsPerPage, page},
    reloadId: Math.random()
});

export const createShare = (path: string, sharedWith: string, rights: AccessRight[]): APICallParameters => ({
    method: "PUT",
    path: "/shares",
    payload: {path, sharedWith, rights},
    reloadId: Math.random()
});

export interface RevokeShareParams {
    path: string;
    sharedWith: string;
}

export const revokeShare = ({path, sharedWith}: RevokeShareParams): APICallParameters => ({
    method: "POST",
    path: `/shares/revoke`,
    parameters: {path, sharedWith},
    payload: {path, sharedWith},
    reloadId: Math.random()
});

export const acceptShare = (path: string): APICallParameters => ({
    method: "POST",
    path: `/shares/accept`,
    parameters: {path},
    payload: {path},
    reloadId: Math.random()
});

export interface UpdateShareParams {
    path: string;
    sharedWith: string;
    rights: AccessRight[];
}

export const updateShare = ({path, sharedWith, rights}: UpdateShareParams): APICallParameters => ({
    method: "POST",
    path: "/shares/update",
    payload: {path, sharedWith, rights}
});

export interface LoadAvatarsParams {
    usernames: Set<string>;
}

export const loadAvatars = ({usernames}: LoadAvatarsParams): APICallParameters<LoadAvatarsParams> => ({
    method: "POST",
    path: "/avatar/bulk",
    payload: {usernames: Array.from(usernames)},
    parameters: {usernames}
});

export enum ServiceOrigin {
    SHARE_SERVICE = "SHARE_SERVICE",
    PROJECT_SERVICE = "PROJECT_SERVICE"
}

export const searchPreviousSharedUsers = (
    query: string,
    serviceOrigin: ServiceOrigin
): APICallParameters => ({
    method: "POST",
    path: "/contactbook",
    payload: {query, serviceOrigin}
});
