export { default as List } from "./List";
import { AccessRightValues } from "Types";

export interface ListProps {
    innerComponent?: boolean
    byPath?: string
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
    ACCEPTED = "ACCEPTED"
}

export type ShareId = string
export interface SharesByPath {
    path: string
    sharedBy: string
    sharedByMe: boolean
    shares: Share[]
}