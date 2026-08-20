import {Dispatch, SetStateAction, useCallback, useState} from "react";
import {setWidgetValues} from "@/Applications/Jobs/Widgets";
import {flushSync} from "react-dom";
import {ApplicationParameter} from "@/Applications/AppStoreApi";
import {compute} from "@/UCloud";
import AppParameterValue = compute.AppParameterValue;

export interface ResourceHook {
    onAdd: () => void;
    onRemove: (id: string) => void;
    setSize: (size: number) => ApplicationParameter[];
    params: ApplicationParameter[];
    errors: Record<string, string>;
    provider?: string;
    setErrors: Dispatch<SetStateAction<Record<string, string>>>;
    warning: string;
    setWarning: (warning: string) => void;
}

type ResourcePrefix = "resource";
type PeerResourceNS = `${ResourcePrefix}Peer`;
export type FolderResourceNS = `${ResourcePrefix}Folder`;
type PrivateNetworkResourceNS = `${ResourcePrefix}PrivateNetwork`;
type ResourceTypes = FolderResourceNS | PeerResourceNS | PrivateNetworkResourceNS | "ingress" | "network";

function nextResourceName(ns: ResourceTypes, params: ApplicationParameter[]): string {
    const names = new Set(params.map(it => it.name));
    let index = 0;
    while (names.has(`${ns}${index}`)) index++;
    return `${ns}${index}`;
}

export function useResource(ns: ResourceTypes, provider: string | undefined,
    paramMapper: (name: string) => ApplicationParameter): ResourceHook {
    const [params, setParams] = useState<ApplicationParameter[]>([]);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [warning, setWarning] = useState<string>("");

    const onAdd = useCallback(() => {
        setParams(current => [...current, paramMapper(nextResourceName(ns, current))]);
    }, [ns, paramMapper]);

    const onRemove = useCallback((id: string) => {
        let placeholderName: string | undefined;
        flushSync(() => setParams(oldParams => {
            const remaining = oldParams.filter(it => it.name !== id);
            if (remaining.length === 1) placeholderName = remaining[0].name;
            return remaining;
        }));
        if (placeholderName !== undefined && placeholderName !== `${ns}0`) {
            setParams([paramMapper(`${ns}0`)]);
        }
        if (errors[id]) {
            delete errors[id];
            setErrors(({...errors}));
        }
    }, [setParams, setErrors, errors, ns, paramMapper]);

    const setSize = useCallback((size: number): ApplicationParameter[] => {
        const params: ApplicationParameter[] = [];
        let i = size;
        while (i--) {
            params.push(paramMapper(nextResourceName(ns, params)));
        }
        flushSync(() => {
            setParams(params);
        });
        return params;
    }, [setParams, ns, paramMapper]);

    return {onAdd, onRemove, params, errors, setErrors, warning, setWarning, setSize, provider};
}

export function createSpaceForLoadedResources(
    resources: ResourceHook,
    values: AppParameterValue[],
    type: string,
    keepEmptyRow = false,
): ApplicationParameter[] {
    const resourceFolders = values.filter(it => it.type === type);
    return resources.setSize(resourceFolders.length + (keepEmptyRow ? 1 : 0));
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
