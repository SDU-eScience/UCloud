import {useCallback, useRef, useState} from "react";
import * as React from "react";
import {compute} from "@/UCloud";
import ApplicationParameter = compute.ApplicationParameter;
import AppParameterValue = compute.AppParameterValue;
import {setWidgetValues} from "@/Applications/Jobs/Widgets";

export interface ResourceHook {
    onAdd: () => void;
    onRemove: (id: string) => void;
    setSize: (size: number) => void;
    params: ApplicationParameter[];
    errors: Record<string, string>;
    provider?: string;
    setErrors: (newErrors: Record<string, string>) => void;
}

type ResourcePrefix = "resource";
type PeerResourceNS = `${ResourcePrefix}Peer`;
type FolderResourceNS = `${ResourcePrefix}Folder`;
type ResourceTypes = FolderResourceNS | PeerResourceNS | "ingress" | "network";

export function useResource(ns: ResourceTypes, provider: string | undefined,
    paramMapper: (name: string) => ApplicationParameter): ResourceHook {
    const counter = useRef<number>(0);
    const [params, setParams] = useState<ApplicationParameter[]>([]);
    const [errors, setErrors] = useState<Record<string, string>>({});

    const onAdd = useCallback(() => {
        setParams([...params, paramMapper(`${ns}${counter.current++}`)]);
    }, [params]);

    const onRemove = useCallback((id: string) => {
        setParams(oldParams => oldParams.filter(it => it.name !== id));
    }, [setParams]);

    const setSize = useCallback((size: number) => {
        const params: ApplicationParameter[] = [];
        let i = size;
        while (i--) {
            params.push(paramMapper(`${ns}${counter.current++}`));
        }
        setParams(params);
    }, [setParams, setErrors]);

    return {onAdd, onRemove, params, errors, setErrors, setSize, provider};
}

export function createSpaceForLoadedResources(
    resources: ResourceHook,
    values: AppParameterValue[],
    type: string,
    jobBeingLoaded: React.MutableRefObject<Partial<compute.JobSpecification> | null>,
    importedJob: Partial<compute.JobSpecification>
): boolean {
    const resourceFolders = values.filter(it => it.type === type);
    if (resources.params.length !== resourceFolders.length) {
        jobBeingLoaded.current = importedJob;
        resources.setSize(resourceFolders.length);
        return true;
    }
    return false;
}

export function injectResources(
    resources: ResourceHook,
    values: AppParameterValue[],
    type: string
): void {
    let i = 0;
    for (const value of values) {
        if (value.type !== type) continue;
        setWidgetValues([{param: resources.params[i], value}]);
        i++;
    }
}
