import deepcopy from "deepcopy";
import Fuse from "fuse.js";

export function associateBy<T>(items: T[], keySelector: (t: T) => string): Record<string, T> {
    const result: Record<string, T> = {};
    items.forEach(item => {
        const key = keySelector(item);
        result[key] = item;
    });
    return result;
}

export function takeLast<T>(items: T[], numberOfItems: number): T[] {
    return items.slice(Math.max(0, items.length - numberOfItems));
}

export function deepCopy<T>(item: T): T {
    return deepcopy(item);
}

export function createRecordFromArray<T, V>(array: T[], keyValueMapper: (value: T) => [string, V]): Record<string, V> {
    const result: Record<string, V> = {};
    for (const elem of array) {
        const [k, v] = keyValueMapper(elem);
        result[k] = v;
    }
    return result;
}

export function fuzzySearch<T, K extends keyof T>(array: T[], keys: K[], query: string, opts?: { sort?: boolean }): T[] {
    const fuse = new Fuse(
        array,
        {
            threshold: 0.6,
            location: 0,
            distance: 100,
            minMatchCharLength: 1,
            shouldSort: opts?.sort,
            keys: keys as string[]
        }
    );

    return fuse.search(query).map(it => it.item);
}

export function fuzzyMatch<T, K extends keyof T>(item: T, keys: K[], query: string): boolean {
    const fuse = new Fuse(
        [item],
        {
            threshold: 0.6,
            location: 0,
            distance: 100,
            minMatchCharLength: 1,
            shouldSort: false,
            keys: keys as string[]
        }
    );

    return fuse.search(query).length > 0;
}

export function newFuzzyMatchFuse<T, K extends keyof T>(keys: K[]): Fuse<T> {
    return new Fuse(
        [],
        {
            threshold: 0.1,
            minMatchCharLength: 1,
            shouldSort: false,
            keys: keys as string[]
        }
    );
}