export const searchPage = (priority: string, query: string): string => {
    return `/search/${encodeURI(priority)}?query=${encodeURI(query)}`;
};