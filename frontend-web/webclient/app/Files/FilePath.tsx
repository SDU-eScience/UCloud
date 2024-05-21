import * as React from "react";
import {pathComponents} from "@/Utilities/FileUtilities";
import {joinToString} from "@/UtilityFunctions";
import {callAPI} from "@/Authentication/DataHook";
import {api as fileCollectionsApi, FileCollection} from "@/UCloud/FileCollectionsApi";
import {useEffect, useMemo, useState} from "react";

let collectionCache: Record<string, string> = {};

function getCachedPrettyFilePath(pathComponents: string[]): string | null {
    if (pathComponents.length === 0) return "/";
    const cachedCollection = collectionCache[pathComponents[0]];
    if (cachedCollection) {
        return "/" + joinToString([cachedCollection, ...pathComponents.slice(1)], "/");
    } else {
        return null;
    }
}

async function prettyFilePathFromComponents(components: string[]): Promise<string> {
    try {
        const resp = await callAPI<FileCollection>(fileCollectionsApi.retrieve({id: components[0]}));
        collectionCache[components[0]] = resp.specification.title;
        return "/" + joinToString([resp.specification.title, ...components.slice(1)], "/");
    } catch (e) {
        return "/" + joinToString(components, "/");
    }
}

export async function prettyFilePath(path: string): Promise<string> {
    const components = pathComponents(path);
    const cached = getCachedPrettyFilePath(components);
    if (cached !== null) return cached;
    return await prettyFilePathFromComponents(components)
}

export function usePrettyFilePath(rawPath: string): string {
    const components = useMemo(() => pathComponents(rawPath), [rawPath]);
    const [path, setPath] = useState("");
    useEffect(() => {
        let didCancel = false;
        const cached = getCachedPrettyFilePath(components);
        if (cached !== null) {
            setPath(cached);
        } else {
            setPath("");
        }

        prettyFilePathFromComponents(components).then(res => {
            if (!didCancel) setPath(res);
        });

        return () => {
            didCancel = true;
        };
    }, [components]);

    return path;
}

export const PrettyFilePath: React.FunctionComponent<{path: string}> = ({path}) => {
    const pretty = usePrettyFilePath(path);
    return <>{pretty}</>;
};
