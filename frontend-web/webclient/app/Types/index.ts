import HttpClient from "Authentication/lib";
import {emptyPage} from "DefaultObjects";
import {Action} from "redux";
import {IconName} from "ui-components/Icon";
import {ThemeColor} from "ui-components/theme";

export interface SidebarOption {
    name: string;
    icon: string;
    href: string;
    children?: SidebarOption;
}


declare global {

    export interface PaginationRequest {
        itemsPerPage: number;
        page: number;
    }

    export interface Page<T> {
        itemsInTotal: number;
        itemsPerPage: number;
        pagesInTotal: number;
        pageNumber: number;
        items: T[];
    }
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
    if (items.length > itemsPerPage) throw Error("Not yet implemented");
    if (page !== 0) throw Error("Not yet implemented");

    return {
        itemsInTotal: items.length,
        itemsPerPage,
        pagesInTotal: 1,
        pageNumber: page,
        items
    };
}

export enum AccessRight {
    READ = "READ",
    WRITE = "WRITE"
}

export class AccessRights {
    public static READ_RIGHTS = [AccessRight.READ];
    public static WRITE_RIGHTS = [AccessRight.READ, AccessRight.WRITE];
}

export interface ClearRefresh {
    clearRefresh: () => void;
}

export type SetLoadingAction<T> = PayloadAction<T, {loading: boolean}>;
export type Error<T> = PayloadAction<T, {error?: string, statusCode?: number}>;
declare global {
    export interface PayloadAction<Act, T> extends Action<Act> {payload: T;}
}

export interface PredicatedOperation<T> {
    predicate: (listItems: T[], client: HttpClient) => boolean;
    onTrue: Operation<T>;
    onFalse: Operation<T>;
}

export interface Operation<T> {
    text: string;
    onClick: (listItems: T[], client: HttpClient) => void;
    disabled: (listItems: T[], client: HttpClient) => boolean;
    icon: IconName;
    color?: ThemeColor;
}
