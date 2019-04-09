export function removeEntry<T>(arr: T[], index: number): T[] {
    return arr.slice(0, index).concat(arr.slice(index + 1));
}

export function setsDiffer<T>(s1: Set<T>, s2: Set<T>): boolean {
    if (s1.size !== s2.size) return true;
    let differ = false;
    s1.forEach(it => { 
        if (!s2.has(it)) differ = true 
    });
    return differ;
}

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