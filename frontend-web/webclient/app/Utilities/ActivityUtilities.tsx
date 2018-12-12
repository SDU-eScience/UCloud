export const activityQuery = (pageNumber: number, pageSize: number) =>
    `/activity?page=${pageNumber}&itemsPerPage=${pageSize}`;

export const activityStreamByPath = (path: string) =>
    `/activity/by-path?path=${encodeURIComponent(path)}` 

export const getLatestActivity = () => undefined;