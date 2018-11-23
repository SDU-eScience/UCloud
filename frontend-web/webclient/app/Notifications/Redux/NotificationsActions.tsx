import { RECEIVE_NOTIFICATIONS, NOTIFICATION_READ, SET_REDIRECT, NOTIFICATIONS_ERROR, READ_ALL, SET_NOTIFICATIONS_ERROR } from "./NotificationsReducer";
import { Cloud } from "Authentication/SDUCloudObject";
import { Page, ReceivePage, PayloadAction } from "Types";
import { Action } from "redux";
import { failureNotification, inSuccessRange } from "UtilityFunctions";
import { Notification } from ".."
import { notificationsQuery, readNotificationQuery, readAllNotificationsQuery } from "Utilities/NotificationUtilities";

export type NotificationActions = ReceivePage<typeof RECEIVE_NOTIFICATIONS, Notification> | SetRedirectToAction |
    ReadAction | { type: typeof NOTIFICATIONS_ERROR } | ReadAllAction
/**
 * Returns the action for receiving the notifications
 * @param {Page<Notification>} page Page of notifications received
 */
export const receiveNotifications = (page: Page<Notification>): ReceivePage<typeof RECEIVE_NOTIFICATIONS, Notification> => ({
    type: RECEIVE_NOTIFICATIONS,
    payload: { page }
})

/**
 * Fetches notifications for the user.
 */
export const fetchNotifications = (): Promise<ReceivePage<typeof RECEIVE_NOTIFICATIONS, Notification> | Action> =>
    Cloud.get(notificationsQuery)
        .then(({ response }) => receiveNotifications(response))
        .catch(() => {
            failureNotification("Failed to retrieve notifications, please try again later");
            return { type: NOTIFICATIONS_ERROR };
        });

interface ReadAction extends PayloadAction<typeof NOTIFICATION_READ, { id: Number }> { }
/**
 * Sets a notification as read, based on the id
 * @param id the id of the notification that has been read
 */
export const notificationRead = async (id: number): Promise<ReadAction> => {
    await Cloud.post(readNotificationQuery(id));
    return {
        type: NOTIFICATION_READ,
        payload: { id }
    };
}


type ReadAllAction = Action<typeof READ_ALL>
/**
 * Sets all notifications as read.
 */
export const readAllNotifications = async (): Promise<ReadAllAction | { type: typeof NOTIFICATIONS_ERROR }> => {
    const { request } = await Cloud.post(readAllNotificationsQuery);
    if (inSuccessRange(request.status))
        return { type: READ_ALL };
    failureNotification("Failed to retrieve notifications, please try again later");
    return { type: NOTIFICATIONS_ERROR }

}

interface SetRedirectToAction extends PayloadAction<typeof SET_REDIRECT, { redirectTo: string }> { }
/**
 * Sets the redirectTo to be used in the Notifications component
 * @param {string} redirectTo the path to be redirected to
 */
export const setRedirectTo = (redirectTo: string): SetRedirectToAction => ({
    type: SET_REDIRECT,
    payload: { redirectTo }
});