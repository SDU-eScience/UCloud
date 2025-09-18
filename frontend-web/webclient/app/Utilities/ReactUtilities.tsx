import * as React from "react";
import {useCallback, useState, useEffect, useRef, useMemo, DependencyList} from "react";

export function useForcedRender(): () => void {
    const [generation, setGeneration] = useState(0);
    return useCallback(() => setGeneration(prev => prev + 1), []);
}

export function useDidUnmount(): React.RefObject<boolean> {
    const didUnmount = useRef(false);
    useEffect(() => {
        return () => {
            didUnmount.current = true;
        };
    }, []);
    return didUnmount;
}

export function defaultMemoCompareWithLogging(prev, next): boolean {
    const prevEntries = Object.entries(prev);
    const nextEntries = Object.entries(next);

    if (prevEntries.length != nextEntries.length) {
        console.log("Number of properties has changed", prev, next);
        return false;
    }

    for (const [key, value] of nextEntries) {
        const previousValue = prev[key];
        if (previousValue !== value) {
            console.log(key, "has changed ", previousValue, value);
            if (typeof value === "object" && typeof previousValue === "object") {
                console.log("Now trying to determine why ", key, "has changed...")
                defaultMemoCompareWithLogging(value, previousValue);
                console.log("---")
            }
            return false
        }
    }
    return true;
}

function convertDependencyListToObject(dependencies) {
    const result = {};
    let i = 0;
    for (const dep of dependencies) {
        result[i++] = dep;
    }
    return result;
}

export function useMemoWithLogging<T>(fn: () => T, dependencies: any): T {
    const previousDependencies = useRef(undefined);
    useEffect(() => {
        if (previousDependencies.current != null) {
            const didChange = defaultMemoCompareWithLogging(
                convertDependencyListToObject(previousDependencies.current),
                convertDependencyListToObject(dependencies)
            );
        }
        previousDependencies.current = dependencies;
    }, [dependencies]);

    return useMemo(fn, dependencies);
}

export function useCallbackWithLogging<T extends (...args: any[]) => any>(callback: T, dependencies: DependencyList) {
    const previousDependencies = useRef<DependencyList | undefined>(undefined);
    useEffect(() => {
        if (previousDependencies.current != null) {
            defaultMemoCompareWithLogging(convertDependencyListToObject(previousDependencies.current), convertDependencyListToObject(dependencies));
        }
        previousDependencies.current = dependencies;
    }, [dependencies]);

    return useCallback(callback, dependencies);
}

