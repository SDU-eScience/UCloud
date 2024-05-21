import {emptyPage} from "@/Utilities/PageUtilities";
import {Action} from "redux";

declare global {
    const DEVELOPMENT_ENV: boolean;
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

    export interface PaginationRequestV2 {
        itemsPerPage: number;
        next?: string;
        itemsToSkip?: number;
    }

    export interface PageV2<T = unknown> {
        itemsPerPage: number;
        items: T[];
        next?: string;
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

export type SetLoadingAction<T extends string> = PayloadAction<T, {loading: boolean}>;
export type Error<T extends string> = PayloadAction<T, {error?: string, statusCode?: number}>;
declare global {
    export interface PayloadAction<Act extends string, T> extends Action<Act> {payload: T;}
}
