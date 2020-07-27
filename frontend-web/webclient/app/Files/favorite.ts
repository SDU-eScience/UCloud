import {useAsyncCommand} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";
import {useCallback} from "react";
import {useGlobal} from "Utilities/ReduxHooks";

const fileFavoriteContext = "/files/favorite";

export interface FavoriteToggleRequest {
    path: string;
}

export function toggleFavorite(parameters: FavoriteToggleRequest): APICallParameters<FavoriteToggleRequest> {
    return {
        reloadId: Math.random(),
        method: "POST",
        path: buildQueryString(`${fileFavoriteContext}/toggle`, parameters),
        parameters
    };
}

export interface FavoriteStatusRequest {
    files: string[];
}

export interface FavoriteStatusResponse {
    favorited: Record<string, boolean>;
}

export function favoriteStatus(
    request: FavoriteStatusRequest
): APICallParameters<FavoriteStatusRequest, FavoriteStatusRequest> {
    return {
        reloadId: Math.random(),
        method: "POST",
        path: `${fileFavoriteContext}/status`,
        payload: request,
        parameters: request
    };
}

export type ListFavoritesRequest = PaginationRequest;

export function listFavorites(
    request: ListFavoritesRequest
): APICallParameters<ListFavoritesRequest> {
    return {
        reloadId: Math.random(),
        method: "GET",
        path: buildQueryString(`${fileFavoriteContext}/list`, request),
        parameters: request
    };
}

export interface FavoriteStatusHook {
    cache: Record<string, boolean>;
    updateCache: (files: string[], markAsFavorite?: boolean) => Promise<void>;
    toggle: (path: string) => Promise<void>;
    loading: boolean;
}

export function useFavoriteStatus(): FavoriteStatusHook {
    const [loading, invokeCommand] = useAsyncCommand();
    const [cache, setCache] = useGlobal("fileFavoriteCache", {});

    const updateCache = useCallback(async (files: string[], markAsFavorite?: boolean) => {
        if (markAsFavorite === true) {
            const newCache: Record<string, boolean> = {...cache};
            files.forEach(it => newCache[it] = true);
            setCache(newCache);
        } else {
            const favStatus = await invokeCommand<FavoriteStatusResponse>(favoriteStatus({files}));
            if (favStatus != null) {
                const newCache: Record<string, boolean> = ({...cache, ...favStatus.favorited})
                setCache(newCache);
            }
        }
    }, [cache, setCache, invokeCommand]);

    const toggle: ((path: string) => Promise<void>) = useCallback(async (path: string) => {
        await invokeCommand(toggleFavorite({path}));
        const existing = cache ? (cache[path] ?? false) : false;
        const newCache: Record<string, boolean> = {...cache};
        newCache[path] = !existing;
        setCache(newCache);
    }, [cache, setCache, invokeCommand]);

    return {cache: (cache ?? {}), updateCache, toggle, loading};
}
