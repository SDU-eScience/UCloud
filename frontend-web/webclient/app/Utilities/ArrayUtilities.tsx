export function removeEntry<T>(arr: T[], index: number):T[] {
    return arr.slice(0, index).concat(arr.slice(index + 1)); 
}