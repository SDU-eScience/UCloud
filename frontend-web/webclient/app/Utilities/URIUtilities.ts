export interface RouterLocationProps {
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