export function removeEntry<T>(arr: T[], index: number):T[] {
    return arr.slice(0, index).concat(arr.slice(index + 1)); 
}


type PrimitiveDataTypes = string | number | boolean
/**
 * @param arr 
 * @param entry 
 */
export function addEntryIfNotPresent(arr: PrimitiveDataTypes[], entry: PrimitiveDataTypes) {
    if (arr.includes(entry)) return false;
    arr.push(entry);
    return true;
}