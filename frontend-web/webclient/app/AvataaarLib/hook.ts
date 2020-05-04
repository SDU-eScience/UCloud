import {useGlobal, useGlobalWithMerge} from "Utilities/ReduxHooks";
import {AvatarType, defaultAvatar} from "UserSettings/Avataaar";
import {useCallback} from "react";
import {useAsyncCommand} from "Authentication/DataHook";
import {fetchBulkAvatars, FetchBulkAvatarsResponse} from "AvataaarLib/index";
import {Dictionary} from "Types";

export function useAvatars(): AvatarHook {
    const [cache, setCache] = useGlobalWithMerge("avatarCache", {});
    const [_, invokeCommand] = useAsyncCommand();
    const updateCache = useCallback(async (usernames: string[]) => {
        const usernamesToUse = usernames.filter(it => !cache.hasOwnProperty(it));
        if (usernamesToUse.length === 0) return;
        const response = await invokeCommand<FetchBulkAvatarsResponse>(fetchBulkAvatars({usernames: usernamesToUse}));
        const newCache = response !== null ? {...cache, ...response.avatars} : cache;
        if (response !== null) {
            setCache(newCache);
        }
    }, [cache, setCache]);
    return {cache, updateCache};
}

export interface AvatarHook {
    cache: Dictionary<AvatarType>;
    updateCache: (usernames: string[]) => Promise<void>;
}

