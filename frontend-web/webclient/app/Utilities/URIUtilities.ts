import { History } from "history";

export interface RouterLocationProps {
    history: History
    location: {
        // TODO There is more here
        search: string
    }
}

export const getQueryParam = (
    props: RouterLocationProps,
    key: string
): string | null => {
    const parsed = new URLSearchParams(props.location.search);
    return parsed.get(key);
};

export const getQueryParamOrCompute = (
    props: RouterLocationProps,
    key: string,
    orElse: () => string
): string => {
    const result = getQueryParam(props, key);
    return result ? result : orElse();
};

export const getQueryParamOrElse = (
    props: RouterLocationProps,
    key: string,
    defaultValue: string
): string => {
    const result = getQueryParam(props, key);
    return result ? result : defaultValue;
};

export const buildQueryString = (path: string, params: any): string => {
    const builtParams = Object.entries(params).map(
        pair => {
            let [key, val] = pair;
            // normalize val to always an array
            const arr = (val instanceof Array) ? val : [val];
            // encode key only once
            const encodedKey = encodeURIComponent(key);
            // then make a different query string for each val member
            return arr.map(
                member => `${encodedKey}=${encodeURIComponent(member)}`
            ).join('&');
        }
    ).join('&');

    return path + '?' + builtParams;
};