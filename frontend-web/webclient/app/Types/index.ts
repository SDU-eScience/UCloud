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

export type AccessRightValues = keyof typeof AccessRight
export enum AccessRight {
    READ = "READ",
    WRITE = "WRITE",
    EXECUTE = "EXECUTE"
}

export interface DropdownOption {
    name: string
    value: string
}

export interface SetLoadingAction<T> extends Action<T> { loading: boolean }
export interface ReceivePage<T1, T2> extends Action<T1> { page: Page<T2> }
export interface Error<T> extends Action<T> { error?: string }