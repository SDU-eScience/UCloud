import {useAsyncCommand} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";
import {useCallback} from "react";
import {useGlobal} from "Utilities/ReduxHooks";
import {usePromiseKeeper} from "PromiseKeeper";

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
    const [loading, sendCommand] = useAsyncCommand();
    const [cache, setCache] = useGlobal("fileFavoriteCache", {});
    const promises = usePromiseKeeper();

    const updateCache = useCallback(async (files: string[], markAsFavorite?: boolean) => {
        if (markAsFavorite === true) {
            const newCache: Record<string, boolean> = {...cache};
            files.forEach(it => newCache[it] = true);
            if (promises.canceledKeeper) return;
            setCache(newCache);
        } else {
            const favStatus = await sendCommand<FavoriteStatusResponse>(favoriteStatus({files}));
            if (favStatus != null && !promises.canceledKeeper) {
                const newCache: Record<string, boolean> = ({...cache, ...favStatus.favorited});
                setCache(newCache);
            }
        }
    }, [cache, setCache, sendCommand, promises]);

    const toggle: ((path: string) => Promise<void>) = useCallback(async (path: string) => {
        await sendCommand(toggleFavorite({path}));
        const existing = cache ? (cache[path] ?? false) : false;
        const newCache: Record<string, boolean> = {...cache};
        newCache[path] = !existing;
        setCache(newCache);
    }, [cache, setCache, sendCommand]);

    return {cache: (cache ?? {}), updateCache, toggle, loading};
}
