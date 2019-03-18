import { Action } from "redux";

export interface SidebarOption {
    name: string
    icon: string
    href: string
    children?: SidebarOption
}


export interface Page<T> {
    itemsInTotal: number,
    itemsPerPage: number,
    pagesInTotal: number
    pageNumber: number,
    items: T[]
}

export enum AccessRight {
    READ = "READ",
    WRITE = "WRITE",
    EXECUTE = "EXECUTE"
}

export interface DropdownOption {
    name: string
    value: string
}

export interface ClearRefresh {
    clearRefresh: () => void
}

export type SetLoadingAction<T> = PayloadAction<T, { loading: boolean }>
export type Error<T> = PayloadAction<T, { error?: string, statusCode?: number }>
export type ReceivePage<T1, T2> = PayloadAction<T1, { page: Page<T2> }>
export interface PayloadAction<T1, T2> extends Action<T1> { payload: T2 }

export interface Dictionary<V> {
    [key: string]: V
}
