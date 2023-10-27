import deepcopy from "deepcopy";

export function groupBy<K extends keyof any, T>(items: T[], keySelector: (t: T) => K): Record<K, T[]> {
    let result = {} as Record<K, T[]>;
    items.forEach(item => {
        const key = keySelector(item);
        const newList = result[key] ?? [];
        newList.push(item);
        result[key] = newList;
    });
    return result;
}

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
