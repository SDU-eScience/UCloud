import {ActivityFilter} from "Activity";
import {ScrollRequest} from "Scroll";
import {buildQueryString} from "./URIUtilities";

interface ParamsType extends Omit<ActivityFilter, "maxTimestamp" | "minTimestamp">, Partial<ScrollRequest<number>> {
    maxTimestamp?: number;
    minTimestamp?: number;
}

export const activityQuery = (scroll: ScrollRequest<number>, filter?: ActivityFilter) => {
    const params: ParamsType = {};
    if (!!scroll.offset) params.offset = scroll.offset;
    params.scrollSize = scroll.scrollSize;

    if (filter !== undefined) {
        if (filter.type !== undefined) params.type = filter.type;
        if (filter.collapseAt !== undefined) params.collapseAt = filter.collapseAt;
        if (filter.maxTimestamp !== undefined) params.maxTimestamp = filter.maxTimestamp.getTime();
        if (filter.minTimestamp !== undefined) params.minTimestamp = filter.minTimestamp.getTime();
        if (filter.user !== undefined) params.user = filter.user;
    }

    return buildQueryString("/activity/browse", params);
};

export const activityStreamByPath = (path: string) =>
    `/activity/by-path?path=${encodeURIComponent(path)}`;
