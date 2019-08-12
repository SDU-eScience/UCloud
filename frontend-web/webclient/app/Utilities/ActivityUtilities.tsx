import { buildQueryString } from "./URIUtilities";
import { ScrollRequest } from "Scroll";
import { ActivityFilter } from "Activity";

export const activityQuery = (scroll: ScrollRequest<number>, filter?: ActivityFilter) => {
    let params: any = {};
    if (!!scroll.offset) params.offset = scroll.offset;
    params.scrollSize = scroll.scrollSize;

    if (filter !== undefined) {
        if (filter.type !== undefined) params.type = filter.type;
        if (filter.collapseAt !== undefined) params.collapseAt = filter.collapseAt;
        if (filter.maxTimestamp !== undefined) params.maxTimestamp = filter.maxTimestamp.getTime();
        if (filter.minTimestamp !== undefined) params.minTimestamp = filter.minTimestamp.getTime();
    }

    return buildQueryString("/activity/browse/user", params);
};

export const activityStreamByPath = (path: string) =>
    `/activity/by-path?path=${encodeURIComponent(path)}`;

export const getLatestActivity = () => undefined;