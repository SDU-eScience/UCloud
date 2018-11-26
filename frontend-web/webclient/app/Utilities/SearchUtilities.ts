export const searchPage = (priority: string, query: string): string => {
    return `/search/${encodeURIComponent(priority)}?query=${encodeURIComponent(query)}`;
};