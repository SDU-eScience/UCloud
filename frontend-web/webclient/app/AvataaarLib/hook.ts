import {AvatarType} from "UserSettings/Avataaar";
import {useCallback, useState} from "react";
import {useAsyncCommand} from "Authentication/DataHook";
import {fetchBulkAvatars, FetchBulkAvatarsResponse} from "AvataaarLib/index";

// Hack: It is really hard for these kind of global caches to avoid double loading if we go through the
// React/Redux way of thinking. Instead we keep an actual global reference, which is more or less what we do with redux,
// but this way we can at least check if someone loaded the avatar while we were waiting for the last request to finish.

let avatarCache: Record<string, AvatarType> = {};
let frontOfQueue: Promise<any> | null = null;

export function useAvatars(): AvatarHook {
    // Hack: This dummy value is used to force a refresh in clients using this hook.
    const [, forceRefresh] = useState<number>(42);

    const [, invokeCommand] = useAsyncCommand();
    const updateCache = useCallback(async (usernames: string[]) => {
        {
            // eslint-disable-next-line no-prototype-builtins
            const usernamesToUse = usernames.filter(it => !avatarCache.hasOwnProperty(it));
            if (usernamesToUse.length === 0) return;
        }

        if (frontOfQueue !== null) await frontOfQueue;
        // eslint-disable-next-line no-prototype-builtins
        const usernamesToUse = usernames.filter(it => !avatarCache.hasOwnProperty(it));
        if (usernamesToUse.length === 0) return;
        frontOfQueue = invokeCommand<FetchBulkAvatarsResponse>(fetchBulkAvatars({usernames: usernamesToUse}));

        const response = await frontOfQueue;
        const newCache = response !== null ? {...avatarCache, ...response.avatars} : avatarCache;
        if (response !== null) {
            avatarCache = newCache;
        }
        forceRefresh(Math.random());
    }, []);
    return {cache: avatarCache, updateCache};
}

export interface AvatarHook {
    cache: Record<string, AvatarType>;
    updateCache: (usernames: string[]) => Promise<void>;
}

