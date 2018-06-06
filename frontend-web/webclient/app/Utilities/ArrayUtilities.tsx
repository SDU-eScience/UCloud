export function removeEntry<T>(arr: T[], index: number) { 
    return arr.slice(0, index).concat(arr.slice(index + 1)); 
}