type PrimitiveDataTypes = string | number | boolean;

/**
 * @param set
 * @param entry
 */
export function addEntryIfNotPresent(set: Set<PrimitiveDataTypes>, entry: PrimitiveDataTypes): boolean {
    const size = set.size;
    set.add(entry);
    return size !== set.size;
}

export function groupBy<T>(items: T[], keySelector: (t: T) => string): Record<string, T[]> {
    const result: Record<string, T[]> = {};
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
