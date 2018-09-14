export function removeEntry<T>(arr: T[], index: number):T[] {
    return arr.slice(0, index).concat(arr.slice(index + 1)); 
}


type PrimitiveDataTypes = string | number | boolean
/**
 * @param set 
 * @param entry 
 * FIXME Why is this in array utilities if it accepts sets?
 */
export function addEntryIfNotPresent(set: Set<PrimitiveDataTypes>, entry: PrimitiveDataTypes): boolean {
    const size = set.size;
    set.add(entry);
    return size !== set.size;
}