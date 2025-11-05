import React, {ReactNode, useEffect, useMemo} from "react";
import {useSyncExternalStore} from "react";
import {bulkRequestOf, doNothing, looksLikeUUID} from "@/UtilityFunctions";
import {apiUpdate, callAPI} from "@/Authentication/DataHook";

// NOTE(Dan): This cache uses the information lookup endpoint. This cache should _only_ be used for projects you might
// not be a member of. Prefer the normal cache if you are definitely a member of the project. For example, this cache
// should be used when working with projects from grants or subprojects. This is, in particular, required for the new
// Core as it does _not_ return project information unless the API is directly related to projects. Thus, there will be
// project titles/PI info from any of the accounting or grants APIs.

// Network API
// =====================================================================================================================

export interface ProjectInfo {
    id: string;
    piUsername: string;
    title: string;
}

export async function lookupProjectInformation(projectIds: string[]): Promise<Record<string, ProjectInfo>> {
    const result = await callAPI<{ projects: Record<string, ProjectInfo>}>(
        apiUpdate(bulkRequestOf(...projectIds.map(id => ({id}))), "/api/projects/v2", "retrieveInformation")
    );

    return result.projects;
}

// Cache
// =====================================================================================================================

type CacheStatus = "idle" | "loading" | "ready" | "error";

type CacheRecord = {
    status: CacheStatus;
    data: ProjectInfo | null; // null means definitively missing
    error: unknown | null;
};

const cache = new Map<string, CacheRecord>();
const listeners = new Map<string, Set<() => void>>();

function notify(id: string) {
    const set = listeners.get(id);
    if (!set) return;
    set.forEach((fn) => fn());
}

function subscribe(id: string, cb: () => void) {
    let set = listeners.get(id);
    if (!set) {
        set = new Set();
        listeners.set(id, set);
    }
    set.add(cb);
    return () => {
        set!.delete(cb);
        if (set!.size === 0) listeners.delete(id);
    };
}

const queued = new Set<string>();
const inflight = new Set<string>();
const resolvers = new Map<string, Array<{ resolve: () => void; reject: (e: unknown) => void }>>();
let timer: ReturnType<typeof setTimeout> | null = null;

function stage(id: string) {
    if (inflight.has(id)) return;
    queued.add(id);
    if (!timer) timer = setTimeout(flush, 50);
}

async function flush() {
    const ids = Array.from(queued);
    queued.clear();
    timer = null;
    const toFetch = ids.filter((id) => !inflight.has(id));
    toFetch.forEach((id) => inflight.add(id));

    if (toFetch.length === 0) return;

    try {
        const result = await lookupProjectInformation(toFetch);
        toFetch.forEach((id) => {
            const info = result[id] ?? null;
            const prev = cache.get(id);
            cache.set(id, {
                status: "ready",
                data: info,
                error: null,
            });
            inflight.delete(id);
            if (prev?.status !== "ready" || prev?.data !== info) notify(id);
            const q = resolvers.get(id);
            if (q) {
                q.forEach(({resolve}) => resolve());
                resolvers.delete(id);
            }
        });
    } catch (e) {
        toFetch.forEach((id) => {
            cache.set(id, {status: "error", data: null, error: e});
            inflight.delete(id);
            notify(id);
            const q = resolvers.get(id);
            if (q) {
                q.forEach(({reject}) => reject(e));
                resolvers.delete(id);
            }
        });
    }
}

function ensure(id: string) {
    const existing = cache.get(id);
    if (existing?.status === "ready" || existing?.status === "loading") return;
    cache.set(id, {status: "loading", data: null, error: null});
    stage(id);
}

function waitFor(id: string) {
    return new Promise<void>((resolve, reject) => {
        let arr = resolvers.get(id);
        if (!arr) {
            arr = [];
            resolvers.set(id, arr);
        }
        arr.push({resolve, reject});
    });
}


// React utilities
// =====================================================================================================================

function useProjectRecord(id: string | null | undefined) {
    const idlePlaceholder = {status: "idle", data: null, error: null};
    return useSyncExternalStore(
        (cb) => id ? subscribe(id, cb) : doNothing,
        () => (id ? cache.get(id) ?? idlePlaceholder : idlePlaceholder),
        () => (id ? cache.get(id) ?? idlePlaceholder : idlePlaceholder)
    );
}

export function useProjectInfo(id: string | null | undefined) {
    const record = useProjectRecord(id);
    useEffect(() => {
        if (!id) return;
        if (record.status === "idle") ensure(id);
    }, [id, record.status]);

    return useMemo(
        () => ({
            data: record.data,
            loading: record.status === "idle" || record.status === "loading",
            error: record.status === "error" ? record.error : null,
            refetch: async () => {
                if (!id) return;
                inflight.delete(id);
                queued.add(id);
                cache.set(id, {status: "loading", data: null, error: null});
                notify(id);
                await Promise.all([waitFor(id), flush()]);
            },
        }),
        [id, record]
    );
}

interface ProjectInfos {
    data: Record<string, ProjectInfo | null>;
    loading: boolean;
    error: unknown | null;
}

export function projectInfosPi(info: ProjectInfos, projectId: string, fallback?: string | null): string | null {
    return fallback ?? info.data[projectId]?.piUsername ?? null;
}

export function projectInfosTitle(info: ProjectInfos, projectId: string, fallback?: string | null): string | null {
    return fallback ?? info.data[projectId]?.title ?? null;
}

export function projectInfoPi(info: ProjectInfo | null | undefined, fallback?: string | null): string | null {
    const loaded = info?.piUsername ?? "";
    return (fallback?.length ?? 0) > 0 ? fallback! : loaded.length > 0 ? loaded : null;
}

export function projectInfoTitle(info: ProjectInfo | null | undefined, fallback?: string | null): string | null {
    const loaded = info?.title ?? "";
    return (fallback?.length ?? 0) > 0 ? fallback! : loaded.length > 0 ? loaded : null;
}

export function useProjectInfos(ids: Array<string | null | undefined>): ProjectInfos {
    const key = ids.join("|");
    const idlePlaceholder: CacheRecord = { status: "idle", data: null, error: null };
    const lastSnapshotRef = React.useRef<CacheRecord[] | null>(null);

    const getSnapshot = () => {
        const next = ids.map((id) => (id ? cache.get(id) ?? idlePlaceholder : idlePlaceholder));
        const prev = lastSnapshotRef.current;

        if (prev && prev.length === next.length) {
            let same = true;
            for (let i = 0; i < next.length; i++) {
                const a = prev[i];
                const b = next[i];
                if (
                    a === b ||
                    (a.status === b.status && a.data === b.data && a.error === b.error)
                ) {
                    continue;
                }
                same = false;
                break;
            }
            if (same) return prev;
        }
        lastSnapshotRef.current = next;
        return next;
    };

    const records = useSyncExternalStore<CacheRecord[]>(
        (cb) => {
            const unsubs: Array<() => void> = [];
            ids.forEach((id) => {
                if (id) unsubs.push(subscribe(id, cb));
            });
            return () => {
                unsubs.forEach((u) => u());
            };
        },
        getSnapshot,
        getSnapshot
    );

    useEffect(() => {
        ids.forEach((id, i) => {
            if (!id) return;
            if (records[i].status === "idle") ensure(id);
        });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [key, records]);

    const loading = records.some((r) => r.status === "idle" || r.status === "loading");
    const error = records.find((r) => r.status === "error")?.error ?? null;

    const data = React.useMemo(() => {
        const obj: Record<string, ProjectInfo | null> = {};
        ids.forEach((id, i) => {
            if (!id) return;
            obj[id] = records[i].data ?? null;
        });
        return obj;
    }, [key, records]);

    return React.useMemo(() => ({ data, loading, error }), [data, loading, error]);
}

export function ProjectTitle(
    {
        id,
        loadingFallback = null,
        missingFallback = null,
        render,
    }: {
        id: string;
        loadingFallback?: ReactNode;
        missingFallback?: ReactNode;
        render?: (title: string, info: ProjectInfo) => ReactNode;
    }
) {
    const {data, loading, error} = useProjectInfo(id);

    if (loading) return <>{loadingFallback}</>;
    if (error) return null;
    if (!data) return <>{missingFallback}</>;

    return <>{render ? render(data.title, data) : data.title}</>;
}

export function PrefetchProjectInfos({ids}: { ids: string[] }) {
    useEffect(() => {
        const missing = ids.filter((id) => !(cache.get(id)?.status === "ready"));
        if (missing.length) {
            missing.forEach((id) => ensure(id));
        }
    }, [ids.join("|")]);
    return null;
}

export const ProjectTitleForNewCore: React.FunctionComponent<{
    id: string;
    title?: string | null; // title from API
}> = props => {
    if (props.title != null && props.title !== "" && props.title !== props.id) {
        return <>{props.title}</>;
    } else {
        // NOTE(Dan): This will _only_ trigger if running under the new Core since the old will always return a title.
        if (!looksLikeUUID(props.id)) {
            return <>{props.id}</>;
        } else {
            return <ProjectTitle id={props.id} loadingFallback={<>{props.title}</>}/>;
        }
    }
}

export const ProjectPiForNewCore: React.FunctionComponent<{
    id: string;
    piUsername?: string | null;
}> = props => {
    if (props.piUsername != null && props.piUsername !== "" && props.piUsername !== props.id) {
        return <>{props.piUsername}</>;
    } else {
        // NOTE(Dan): This will _only_ trigger if running under the new Core since the old will always return a username.
        if (!looksLikeUUID(props.id)) {
            return <>{props.id}</>;
        } else {
            const {data, loading, error} = useProjectInfo(props.id);
            if (loading || error || !data) return <></>;
            return <>{data.piUsername}</>;
        }
    }
}
