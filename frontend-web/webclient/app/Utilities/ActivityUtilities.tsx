import { buildQueryString } from "./URIUtilities";

export const activityQuery = (offset: number | null, scrollSize: number) => {
    const params = { scrollSize };
    if (offset !== null) params["offset"] = offset;

    return buildQueryString("/activity/browse/user", params);
}

export const activityStreamByPath = (path: string) =>
    `/activity/by-path?path=${encodeURIComponent(path)}` 

export const getLatestActivity = () => undefined;