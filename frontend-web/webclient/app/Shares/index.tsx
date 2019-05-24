import {APICallParameters} from "Authentication/DataHook";

export {default as List} from "./List";
import {AccessRight} from "Types";
import {buildQueryString} from "Utilities/URIUtilities";

export interface ListProps {
    innerComponent?: boolean
    byPath?: string
}

export interface Share {
    id: ShareId,
    sharedWith: string,
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

export interface ListSharesParams {
    sharedByMe: boolean
    itemsPerPage: number
    page: number
}

export const listShares = ({sharedByMe, itemsPerPage, page}: ListSharesParams): APICallParameters<ListSharesParams> => ({
    method: "GET",
    path: buildQueryString("/shares", {itemsPerPage, page}),
    parameters: {sharedByMe, itemsPerPage, page},
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

export interface LoadAvatarsParams {
    usernames: Set<string>
}

export const loadAvatars = ({usernames}: LoadAvatarsParams): APICallParameters<LoadAvatarsParams> => ({
    method: "POST",
    path: "/avatar/bulk",
    payload: {usernames: Array.from(usernames)},
    parameters: {usernames}
});