import {HttpClient} from "../Authentication/lib";
import {emptyPage} from "@/DefaultObjects";
import {Action} from "redux";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";

declare global {
    const DEVELOPMENT_ENV: boolean;
}

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
        pageNumber: number;
        items: T[];
    }
}

export function singletonToPage<T>(item?: T | null, itemsPerPage = 50): Page<T> {
    if (item === undefined || item === null) return emptyPage;
    return {
        itemsInTotal: 1,
        itemsPerPage,
        pageNumber: 0,
        items: [item]
    };
}

export function arrayToPage<T>(items: T[], itemsPerPage = 50, page = 0): Page<T> {
    if (items.length > itemsPerPage) throw Error("Not yet implemented");
    if (page !== 0) throw Error("Not yet implemented");

    return {
        itemsInTotal: items.length,
        itemsPerPage,
        pageNumber: page,
        items
    };
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
