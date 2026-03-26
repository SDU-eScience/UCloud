import {NavigateFunction} from "react-router-dom";

export interface RouterLocationProps {
    navigate: NavigateFunction;
    location: {
        search: string;
    };
}

export function getQueryParam(
    props: RouterLocationProps | string,
    key: string
): string | null {
    const search = typeof props === "object" ? props.location.search : props;
    const parsed = new URLSearchParams(search);
    return parsed.get(key);
}

export function getQueryParamOrElse(
    props: RouterLocationProps | string,
    key: string,
    defaultValue: string
): string {
    const result = getQueryParam(props, key);
    return result ? result : defaultValue;
}

function flattenQueryEntries(key: string, value: any): Array<[string, string]> {
    if (value === undefined || value === null) {
        return [];
    }

    if (Array.isArray(value)) {
        const result: Array<[string, string]> = [];
        for (const member of value) {
            result.push(...flattenQueryEntries(key, member));
        }
        return result;
    }

    if (typeof value === "object") {
        const result: Array<[string, string]> = [];
        for (const [subKey, subValue] of Object.entries(value)) {
            result.push(...flattenQueryEntries(`${key}.${subKey}`, subValue));
        }
        return result;
    }

    return [[key, String(value)]];
}

export const buildQueryString = <T extends Record<string, any>>(path: string, params: T): string => {
    const entries: Array<[string, string]> = [];
    for (const [key, value] of Object.entries(params)) {
        entries.push(...flattenQueryEntries(key, value));
    }

    if (entries.length === 0) {
        return path;
    }

    const builtParams = entries
        .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
        .join("&");

    return path + "?" + builtParams;
};
