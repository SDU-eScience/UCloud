import {Action} from "redux";
import {emptyPage} from "DefaultObjects";
import Cloud from "Authentication/lib"

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

export function singletonToPage<T>(item?: T | null, itemsPerPage: number = 50): Page<T> {
    if (item === undefined || item === null) return emptyPage;
    return {
        itemsInTotal: 1,
        itemsPerPage,
        pagesInTotal: 1,
        pageNumber: 0,
        items: [item]
    };
}

export function arrayToPage<T>(items: T[], itemsPerPage: number = 50, page: number = 0): Page<T> {
    if (items.length > itemsPerPage) throw "Not yet implemented";
    if (page !== 0) throw "Not yet implemented";

    return {
        itemsInTotal: items.length,
        itemsPerPage: itemsPerPage,
        pagesInTotal: 1,
        pageNumber: page,
        items: items
    };
}

export enum AccessRight {
    READ = "READ",
    WRITE = "WRITE"
}

export class AccessRights {
    static READ_RIGHTS = [AccessRight.READ];
    static WRITE_RIGHTS = [AccessRight.READ, AccessRight.WRITE]
}

export interface DropdownOption {
    name: string
    value: string
}

export interface ClearRefresh {
    clearRefresh: () => void
}

export type SetLoadingAction<T> = PayloadAction<T, {loading: boolean}>
export type Error<T> = PayloadAction<T, {error?: string, statusCode?: number}>
export type ReceivePage<T1, T2> = PayloadAction<T1, {page: Page<T2>}>
export interface PayloadAction<T1, T2> extends Action<T1> {payload: T2}

export interface Dictionary<V> {
    [key: string]: V
}

export interface PredicatedOperation<T> {
    predicate: (listItems: T[], cloud: Cloud) => boolean
    onTrue: Operation<T>
    onFalse: Operation<T>
}
export type Operation<T> = {
    text: string
    onClick: (listItems: T[], cloud: Cloud) => void
    disabled: (listItems: T[], cloud: Cloud) => boolean
    icon?: string
    color?: string
}
