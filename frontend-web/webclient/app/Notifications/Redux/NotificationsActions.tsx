import {
    RECEIVE_SINGLE_NOTIFICATION,
    RECEIVE_NOTIFICATIONS,
    NOTIFICATION_READ,
    SET_REDIRECT,
    NOTIFICATIONS_ERROR, READ_ALL
} from "./NotificationsReducer";
import {Cloud} from "Authentication/SDUCloudObject";
import {Page, PayloadAction} from "Types";
import {Action} from "redux";
import {errorMessageOrDefault} from "UtilityFunctions";
import {Notification} from ".."
import {notificationsQuery, readNotificationQuery, readAllNotificationsQuery} from "Utilities/NotificationUtilities";
import {snackbarStore} from "Snackbar/SnackbarStore";

export type NotificationActions =
    ReceiveNotificationAction |
    ReceiveSingleNotificationAction |
    SetRedirectToAction |
    ReadAction |
    SetNotificationError |
    ReadAllAction;

type SetNotificationError = Action<typeof NOTIFICATIONS_ERROR>
interface ReceiveSingleNotificationAction {
    type: typeof RECEIVE_SINGLE_NOTIFICATION,
    payload: {item: Notification}
}

export const receiveSingleNotification = (notification: Notification): ReceiveSingleNotificationAction => ({
    type: RECEIVE_SINGLE_NOTIFICATION,
    payload: {item: notification}
});

interface ReceiveNotificationAction {
    type: typeof RECEIVE_NOTIFICATIONS,
    payload: {items: Notification[]}
}
/**
 * Returns the action for receiving the notifications
 * @param {Page<Notification>} page Page of notifications received
 */
export const receiveNotifications = (page: Page<Notification>): ReceiveNotificationAction => ({
    type: RECEIVE_NOTIFICATIONS,
    payload: {items: page.items}
});

/**
 * Fetches notifications for the user.
 */
export async function fetchNotifications(): Promise<ReceiveNotificationAction | SetNotificationError> {
    try {
        const res = await Cloud.get<Page<Notification>>(notificationsQuery, undefined, true);
        return receiveNotifications(res.response);
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, "Failed to retrieve notifications, please try again later"));
        return notificationError();
    }
}

type ReadAction = PayloadAction<typeof NOTIFICATION_READ, {id: number | string}>
/**
 * Sets a notification as read, based on the id
 * @param id the id of the notification that has been read
 */
export const notificationRead = async (id: number): Promise<ReadAction | SetNotificationError> => {
    try {
        if (id >= 0) await Cloud.post(readNotificationQuery(id));
        return {
            type: NOTIFICATION_READ,
            payload: {id}
        };
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, "Could not mark notification as read"))
        return notificationError();
    }
};

export const notificationError = (): SetNotificationError => ({
    type: NOTIFICATIONS_ERROR
});


type ReadAllAction = Action<typeof READ_ALL>
/**
 * Sets all notifications as read.
 */
export const readAllNotifications = async (): Promise<ReadAllAction | SetNotificationError> => {
    try {
        await Cloud.post(readAllNotificationsQuery);
        return {type: READ_ALL};
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, "Failed to mark notifications as read, please try again later"))
        return notificationError();
    }

}

interface SetRedirectToAction extends PayloadAction<typeof SET_REDIRECT, {redirectTo: string}> {}
/**
 * Sets the redirectTo to be used in the Notifications component
 * @param {string} redirectTo the path to be redirected to
 */
export const setRedirectTo = (redirectTo: string): SetRedirectToAction => ({
    type: SET_REDIRECT,
    payload: {redirectTo}
});