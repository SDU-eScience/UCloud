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

export function removePrefixFrom(prefix: string, text: string): string {
    if (text.indexOf(prefix) === 0) {
        return text.substring(prefix.length);
    }
    return text;
}

export function visualizeWhitespaces(value: string): string {
    if (value === " ") return "␣";
    let result = value;
    result = result.replace(/\n/g, "↵").replace(/\r/g, "↵").replace(/\t/g, "→→→→");
    if (result.indexOf("  ") !== -1) result = result.replace(/ /g, "␣");
    return result;
}
