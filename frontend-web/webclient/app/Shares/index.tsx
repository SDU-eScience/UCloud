export { List } from "./List";
import { Store } from "redux";
import { AccessRightValues } from "Types";
import PromiseKeeper from "PromiseKeeper";

export interface ListState {
    promises: PromiseKeeper
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
    rights: AccessRightValues[],
    state: ShareStateValues
}

// FIXME Singular instead of plural?

export type ShareStateValues = keyof typeof ShareState
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