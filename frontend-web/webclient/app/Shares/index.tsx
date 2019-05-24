import {APICallParameters} from "Shares/DataHook";

export {default as List} from "./List";
import {AccessRight} from "Types";
import {buildQueryString} from "Utilities/URIUtilities";

export interface ListProps {
    innerComponent?: boolean
    byPath?: string
}

export interface Share {
    id: ShareId,
    sharedWith: String,
    rights: AccessRight[],
    state: ShareState
}

export enum ShareState {
    REQUEST_SENT = "REQUEST_SENT",
    ACCEPTED = "ACCEPTED",
    FAILURE = "FAILURE",
    UPDATING = "UPDATING"
}

export type ShareId = string

export interface SharesByPath {
    path: string
    sharedBy: string
    sharedByMe: boolean
    shares: Share[]
}

export const findShare = (sharedByMe: boolean, path: string): APICallParameters => ({
    method: "GET",
    path: buildQueryString("/shares/byPath", {path: path}),
    reloadId: Math.random()
});

export const listShares = (
    sharedByMe: boolean,
    itemsPerPage: number,
    page: number
): APICallParameters => ({
    method: "GET",
    path: buildQueryString("/shares", {itemsPerPage, page}),
    reloadId: Math.random()
});

export const createShare = (path: string, sharedWith: string, rights: AccessRight[]): APICallParameters => ({
    method: "PUT",
    path: "/shares",
    payload: {path, sharedWith, rights},
    reloadId: Math.random()
});

export const revokeShare = (id: ShareId): APICallParameters => ({
    method: "POST",
    path: `/shares/revoke/${id}`,
    reloadId: Math.random()
});

export const acceptShare = (id: ShareId): APICallParameters => ({
    method: "POST",
    path: `/shares/accept/${id}`,
    reloadId: Math.random()
});

export const updateShare = (id: ShareId, rights: AccessRight[]): APICallParameters => ({
    method: "POST",
    path: "/shares",
    payload: {id, rights}
});

