import {callAPI} from "@/Authentication/DataHook";
import {AvatarType, defaultAvatar, fetchBulkAvatars, FetchBulkAvatarsResponse} from "@/AvataaarLib/index";
import {useUState, UState} from "@/Utilities/UState";

class AvatarState extends UState<AvatarState> {
    private inflight: Record<string, true> = {};
    private cache: Record<string, AvatarType> = {};

    updateCache(usernames: string[]): Promise<void> {
        const usernamesToUse = usernames.filter(it =>
            !this.cache.hasOwnProperty(it) &&
            !this.inflight.hasOwnProperty(it));

        if (usernamesToUse.length === 0) return Promise.resolve();
        for (const username of usernamesToUse) this.inflight[username] = true;

        return this.run(async () => {
            const response = await callAPI<FetchBulkAvatarsResponse>(fetchBulkAvatars({usernames: usernamesToUse}));

            const newCache = response !== null ? {...this.cache, ...response.avatars} : this.cache;
            if (response !== null) {
                this.cache = newCache;
                for (const username of usernamesToUse) {
                    if (this.cache[username] == null) {
                        this.cache[username] = defaultAvatar;
                    }
                }
            }
        });
    }

    invalidateAndUpdate(usernames: string[]): Promise<void> {
        for (const name of usernames) {
            delete this.cache[name];
        }
        return this.updateCache(usernames);
    }

    avatar(username: string): AvatarType {
        this.updateCache([username]);
        return this.cache[username] ?? defaultAvatar;
    }
}

export const avatarState = new AvatarState(2); // Two, otherwise we can't fetch by invites and projectmembers in the Members.tsx file.

export function useAvatars(): AvatarState {
    return useUState(avatarState);
}