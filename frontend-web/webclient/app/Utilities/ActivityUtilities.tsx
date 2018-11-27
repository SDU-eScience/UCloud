export const activityQuery = (pageNumber: number, pageSize: number) =>
    `/activity/stream?page=${pageNumber}&itemsPerPage=${pageSize}`;

export const activityStreamByPath = (path: string) =>
    `/activity/stream/by-path?path=${encodeURIComponent(path)}` 

export const getLatestActivity = () => undefined;