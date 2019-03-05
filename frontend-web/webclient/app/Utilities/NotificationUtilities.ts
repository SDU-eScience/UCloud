export const notificationsQuery = "/notifications?itemsPerPage=100";
export const readAllNotificationsQuery = "notifications/read/all";
export const readNotificationQuery = (id: number) => `notifications/read/${id}`;