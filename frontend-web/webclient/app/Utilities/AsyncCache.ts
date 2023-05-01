import {timestampUnixMs} from "@/UtilityFunctions";

export class AsyncCache<V> {
    private expiration: Record<string, number> = {};
    private cache: Record<string, V> = {};
    private inflight: Record<string, Promise<V>> = {};
    private globalTtl: number | undefined = undefined;

    constructor(opts?: {
        globalTtl?: number;
    }) {
        this.globalTtl = opts?.globalTtl;
    }

    retrieveFromCacheOnly(name: string): V | undefined {
        return this.cache[name];
    }

    retrieveWithInvalidCache(name: string, fn: () => Promise<V>, ttl?: number): [V | undefined, Promise<V>] {
        const cached = this.cache[name];
        if (cached) {
            const expiresAt = this.expiration[name];
            if (expiresAt !== undefined && timestampUnixMs() > expiresAt) {
                delete this.cache[name];
            } else {
                return [cached, Promise.resolve(cached)];
            }
        }

        const inflight = this.inflight[name];
        if (inflight) return [cached, inflight];

        const promise = fn();
        this.inflight[name] = promise;
        return [
            cached,
            promise
                .then(r => {
                    this.cache[name] = r;
                    const actualTtl = ttl ?? this.globalTtl;
                    if (actualTtl !== undefined) {
                        this.expiration[name] = timestampUnixMs() + actualTtl;
                    }
                    return r;
                })
                .finally(() => delete this.inflight[name])
        ];
    }

    retrieve(name: string, fn: () => Promise<V>, ttl?: number): Promise<V> {
        return this.retrieveWithInvalidCache(name, fn, ttl)[1];
    }
}
