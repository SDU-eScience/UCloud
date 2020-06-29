/**
 * Cache for project and group membership.
 */
import {userProjectStatus, UserStatusResponse} from "Project";
import {useGlobal} from "Utilities/ReduxHooks";
import {useCallback} from "react";
import {useAsyncCommand} from "Authentication/DataHook";

// This needs to be global
let cacheIsLoading = false;

export function useProjectStatus(): ProjectStatus {
    const [cache, setCache] = useGlobal(
        "projectCache",
        {expiresAt: 0, status: {groups: [], membership: []}}
    );

    const [, invokeCommand] = useAsyncCommand();

    const reload = useCallback(async () => {
        if (cacheIsLoading) return;
        cacheIsLoading = true;

        const status = await invokeCommand<UserStatusResponse>(userProjectStatus({}));
        if (status !== null) {
            setCache({expiresAt: +(new Date()) + cacheMaxAge, status});
        } else {
            setCache({...cache, expiresAt: +(new Date()) + (1000 * 30)});
        }

        cacheIsLoading = false;
    }, [invokeCommand, cache, setCache]);

    const fetch = useCallback(() => {
        const now = (+new Date());
        if (now > cache.expiresAt) {
            reload();
        }

        return cache.status;
    }, [cache]);

    return {fetch, reload};
}

export interface ProjectStatus {
    fetch: () => UserStatusResponse;
    reload: () => Promise<void>;
}

export interface ProjectCache {
    expiresAt: number;
    status: UserStatusResponse;
}

const cacheMaxAge = 1000 * 60 * 3;
