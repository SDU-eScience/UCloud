import { RECEIVE_SINGLE_NOTIFICATION, RECEIVE_NOTIFICATIONS, NOTIFICATION_READ, SET_REDIRECT, NOTIFICATIONS_ERROR, READ_ALL, SET_NOTIFICATIONS_ERROR } from "./NotificationsReducer";
import { Cloud } from "Authentication/SDUCloudObject";
import { Page, PayloadAction } from "Types";
import { Action } from "redux";
import { errorMessageOrDefault } from "UtilityFunctions";
import { Notification } from ".."
import { notificationsQuery, readNotificationQuery, readAllNotificationsQuery } from "Utilities/NotificationUtilities";

export type NotificationActions =
    ReceiveNotificationAction |
    ReceiveSingleNotificationAction |
    SetRedirectToAction |
    ReadAction |
    SetNotificationError |
    ReadAllAction;

type SetNotificationError = PayloadAction<typeof NOTIFICATIONS_ERROR, { error?: string }>
interface ReceiveSingleNotificationAction {
    type: typeof RECEIVE_SINGLE_NOTIFICATION,
    payload: { item: Notification }
}

export const receiveSingleNotification = (notification: Notification): ReceiveSingleNotificationAction => ({
    type: RECEIVE_SINGLE_NOTIFICATION,
    payload: { item: notification }
});

interface ReceiveNotificationAction {
    type: typeof RECEIVE_NOTIFICATIONS,
    payload: { items: Notification[] }
}
/**
 * Returns the action for receiving the notifications
 * @param {Page<Notification>} page Page of notifications received
 */
export const receiveNotifications = (page: Page<Notification>): ReceiveNotificationAction => ({
    type: RECEIVE_NOTIFICATIONS,
    payload: { items: page.items }
});

/**
 * Fetches notifications for the user.
 */
export async function fetchNotifications(): Promise<ReceiveNotificationAction | SetNotificationError> {
    try {
        const res = await Cloud.get<Page<Notification>>(notificationsQuery);
        return receiveNotifications(res.response);
    } catch (e) {
        return setNotificationError(errorMessageOrDefault(e, "Failed to retrieve notifications, please try again later"));
    }
}

type ReadAction = PayloadAction<typeof NOTIFICATION_READ, { id: number | string }> 
/**
 * Sets a notification as read, based on the id
 * @param id the id of the notification that has been read
 */
export const notificationRead = async (id: number | string): Promise<ReadAction | SetNotificationError> => {
    try {
        /* FIXME: Likely not the most ideal way of handling local/non-local difference for notifications */
        if (typeof id === "number") await Cloud.post(readNotificationQuery(id));
        return {
            type: NOTIFICATION_READ,
            payload: { id }
        };
    } catch (e) {
        return setNotificationError(errorMessageOrDefault(e, "Could not mark notification as read"));
    }
};

export const setNotificationError = (error?: string): SetNotificationError => ({
    type: NOTIFICATIONS_ERROR,
    payload: { error }
});


type ReadAllAction = Action<typeof READ_ALL>
/**
 * Sets all notifications as read.
 */
export const readAllNotifications = async (): Promise<ReadAllAction | SetNotificationError> => {
    try {
        await Cloud.post(readAllNotificationsQuery);
        return { type: READ_ALL };
    } catch (e) {
        return setNotificationError(errorMessageOrDefault(e, "Failed to mark notifications as read, please try again later"));
    }

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