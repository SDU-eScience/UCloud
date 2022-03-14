export function shorten(maxLength: number, text: string): string {
    if (text.length > maxLength) {
        return text.slice(0, maxLength).trim() + "...";
    } else {
        return text;
    }
}

export function stupidPluralize(count: number, text: string): string {
    if (count > 1) return text + "s";
    return text;
}
