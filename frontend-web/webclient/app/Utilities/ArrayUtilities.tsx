export function removeEntry<T>(arr: T[], index: number):T[] {
    return arr.slice(0, index).concat(arr.slice(index + 1)); 
}


type PrimitiveDataTypes = string | number | boolean
/**
 * @param set 
 * @param entry 
 */
export function addEntryIfNotPresent(set: Set<PrimitiveDataTypes>, entry: PrimitiveDataTypes): boolean {
    const size = set.size;
    set.add(entry);
    return size === set.size;
}