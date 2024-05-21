/**
 * Used for resolving paths, which contain either "." or "..", and returning the resolved path.
 * @param path The current input path, which can include relative paths
 */
export function resolvePath(path: string): string {
    const components = path.split("/");
    const result: string[] = [];
    components.forEach(it => {
        if (it === "") {
            return;
        } else if (it === ".") {
            return;
        } else if (it === "..") {
            result.pop();
        } else {
            result.push(it);
        }
    });
    return "/" + result.join("/");
}

/**
 * Splits a path into components based on the divider '/', and filters away empty strings.
 * @param path to be split.
 * @returns every filtered component as a string array.
 */
export function pathComponents(path: string): string[] {
    return resolvePath(path).split("/").filter(it => it !== "");
}

export const getParentPath = (path: string): string => {
    if (path.length === 0) return path;
    let splitPath = path.split("/");
    splitPath = splitPath.filter(p => p);
    let parentPath = "/";
    for (let i = 0; i < splitPath.length - 1; i++) {
        parentPath += splitPath[i] + "/";
    }
    return parentPath;
};

const goUpDirectory = (
    count: number,
    path: string
): string => count ? goUpDirectory(count - 1, getParentPath(path)) : path;

export function fileName(path: string): string {
    const lastSlash = path.lastIndexOf("/");
    if (lastSlash !== -1 && path.length > lastSlash + 1) {
        return path.substring(lastSlash + 1);
    } else {
        return path;
    }
}

function isInt(value: number): boolean {
    if (isNaN(value)) {
        return false;
    }
    return (value | 0) === value;
}

export function sizeToString(bytes: number | null): string {
    if (bytes === null) return "";
    if (bytes < 0) return "Invalid size";
    const {size, unit} = sizeToHumanReadableWithUnit(bytes);

    if (isInt(size)) {
        return `${size} ${unit}`;
    } else {
        return `${size.toFixed(2)} ${unit}`;
    }
}

export function sizeToHumanReadableWithUnit(bytes: number): {size: number; unit: string} {
    if (bytes < 1000) {
        return {size: bytes, unit: "B"};
    } else if (bytes < 1000 ** 2) {
        return {size: (bytes / 1000), unit: "KB"};
    } else if (bytes < 1000 ** 3) {
        return {size: (bytes / 1000 ** 2), unit: "MB"};
    } else if (bytes < 1000 ** 4) {
        return {size: (bytes / 1000 ** 3), unit: "GB"};
    } else if (bytes < 1000 ** 5) {
        return {size: (bytes / 1000 ** 4), unit: "TB"};
    } else if (bytes < 1000 ** 6) {
        return {size: (bytes / 1000 ** 5), unit: "PB"};
    } else {
        return {size: (bytes / 1000 ** 6), unit: "EB"};
    }
}

export function readableUnixMode(unixPermissions: number): string {
    let result = "";
    if ((unixPermissions & (1 << 8)) != 0) result += "r";
    else result += "-";
    if ((unixPermissions & (1 << 7)) != 0) result += "w";
    else result += "-";
    if ((unixPermissions & (1 << 6)) != 0) result += "x";
    else result += "-";

    if ((unixPermissions & (1 << 5)) != 0) result += "r";
    else result += "-";
    if ((unixPermissions & (1 << 4)) != 0) result += "w";
    else result += "-";
    if ((unixPermissions & (1 << 3)) != 0) result += "x";
    else result += "-";

    if ((unixPermissions & (1 << 2)) != 0) result += "r";
    else result += "-";
    if ((unixPermissions & (1 << 1)) != 0) result += "w";
    else result += "-";
    if ((unixPermissions & (1 << 0)) != 0) result += "x";
    else result += "-";

    return result;
}