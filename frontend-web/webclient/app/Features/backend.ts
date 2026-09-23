import {useEffect} from "react";
import {Client} from "@/Authentication/HttpClientInstance";
import {callAPI} from "@/Authentication/DataHook";
import {featuresApi} from "@/UCloud/FeaturesApi";
import {useForcedRender} from "@/Utilities/ReactUtilities";

let enabledFeatures: Set<string> = new Set();
let fetchPromise: Promise<void> | null = null;
let hasFetched = false;
const listeners = new Set<() => void>();

function storeEnabled(features: string[]): void {
    enabledFeatures = new Set(features);
    for (const listener of listeners) listener();
}

export function backendFeatureEnabled(feature: string): boolean {
    return enabledFeatures.has(feature);
}

export function backendFeaturesReady(): boolean {
    return hasFetched;
}

export function fetchBackendFeatures(force = false): Promise<void> {
    if (!Client.isLoggedIn) return Promise.resolve();
    if (fetchPromise !== null && !force) return fetchPromise;

    fetchPromise = callAPI<string[]>(featuresApi.retrieveEnabled())
        .then(features => {
            storeEnabled(features ?? []);
            hasFetched = true;
        })
        .catch(() => {
            fetchPromise = null;
        });
    return fetchPromise;
}

export function resetBackendFeatures(): void {
    enabledFeatures = new Set();
    fetchPromise = null;
    hasFetched = false;
    for (const listener of listeners) listener();
}

export function useBackendFeatures(): {ready: boolean; enabled: (feature: string) => boolean} {
    const rerender = useForcedRender();

    useEffect(() => {
        listeners.add(rerender);
        return () => {
            listeners.delete(rerender);
        };
    }, [rerender]);

    useEffect(() => {
        if (!backendFeaturesReady()) fetchBackendFeatures();
    }, []);

    return {
        ready: backendFeaturesReady(),
        enabled: backendFeatureEnabled,
    };
}
