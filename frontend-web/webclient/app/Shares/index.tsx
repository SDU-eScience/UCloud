export { List } from "./List";
import { Store } from "redux";
import { AccessRight } from "Types";

export interface ListState {
    shares: SharesByPath[]
    errorMessage?: string,
    page: number,
    itemsPerPage: number
    loading: boolean
}

export interface ListProps {
    keepTitle?: boolean
    byPath?: string
}

export interface ListContext {
    store: Store
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
    REJECTED = "REJECT",
    REVOKE = "REVOKE"
}

export type ShareId = string
export interface SharesByPath {
    path: string,
    sharedBy: string,
    sharedByMe: boolean,
    shares: Share[]
}