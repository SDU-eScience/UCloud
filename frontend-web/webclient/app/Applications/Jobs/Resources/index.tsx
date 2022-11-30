import {useCallback, useRef, useState} from "react";
import {compute} from "@/UCloud";
import ApplicationParameter = compute.ApplicationParameter;
import AppParameterValue = compute.AppParameterValue;
import {setWidgetValues} from "@/Applications/Jobs/Widgets";
import {flushSync} from "react-dom";

export interface ResourceHook {
    onAdd: () => void;
    onRemove: (id: string) => void;
    setSize: (size: number) => ApplicationParameter[];
    params: ApplicationParameter[];
    errors: Record<string, string>;
    provider?: string;
    setErrors: (newErrors: Record<string, string>) => void;
    warning: string;
    setWarning: (warning: string) => void;
}

type ResourcePrefix = "resource";
type PeerResourceNS = `${ResourcePrefix}Peer`;
export type FolderResourceNS = `${ResourcePrefix}Folder`;
type ResourceTypes = FolderResourceNS | PeerResourceNS | "ingress" | "network";

export function useResource(ns: ResourceTypes, provider: string | undefined,
    paramMapper: (name: string) => ApplicationParameter): ResourceHook {
    const counter = useRef<number>(0);
    const [params, setParams] = useState<ApplicationParameter[]>([]);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [warning, setWarning] = useState<string>("");

    const onAdd = useCallback(() => {
        setParams([...params, paramMapper(`${ns}${counter.current++}`)]);
    }, [params]);

    const onRemove = useCallback((id: string) => {
        setParams(oldParams => oldParams.filter(it => it.name !== id));
    }, [setParams]);

    const setSize = useCallback((size: number): ApplicationParameter[] => {
        const params: ApplicationParameter[] = [];
        let i = size;
        while (i--) {
            params.push(paramMapper(`${ns}${counter.current++}`));
        }
        flushSync(() => {
            setParams(params);
        });
        return params;
    }, [setParams, setErrors]);

    return {onAdd, onRemove, params, errors, setErrors, warning, setWarning, setSize, provider};
}

export function createSpaceForLoadedResources(
    resources: ResourceHook,
    values: AppParameterValue[],
    type: string
): ApplicationParameter[] {
    const resourceFolders = values.filter(it => it.type === type);
    return resources.setSize(resourceFolders.length);
}

export function injectResources(
    params: ApplicationParameter[],
    values: AppParameterValue[],
    type: string
): void {
    for (const [i, value] of values.filter(it => it.type === type).entries()) {
        setWidgetValues([{param: params[i], value}]);
    }
}