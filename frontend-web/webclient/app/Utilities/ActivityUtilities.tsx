export const activityQuery = (pageNumber: number, pageSize: number) => `/activity/stream?page=${pageNumber}&itemsPerPage=${pageSize}`;

export const getLatestActivity = () => undefined;