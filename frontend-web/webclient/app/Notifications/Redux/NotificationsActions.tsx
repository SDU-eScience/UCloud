import {Client} from "Authentication/HttpClientInstance";
import {Action} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Page, PayloadAction} from "Types";
import {notificationsQuery, readAllNotificationsQuery, readNotificationQuery} from "Utilities/NotificationUtilities";
import {errorMessageOrDefault} from "UtilityFunctions";
import {Notification} from "..";
import {
    NOTIFICATION_READ,
    NOTIFICATIONS_ERROR,
    READ_ALL,
    RECEIVE_NOTIFICATIONS,
    RECEIVE_SINGLE_NOTIFICATION
} from "./NotificationsReducer";

export type NotificationActions = ReceiveNotificationAction | ReceiveSingleNotificationAction | ReadAction |
    SetNotificationError | ReadAllAction;

type SetNotificationError = Action<typeof NOTIFICATIONS_ERROR>;
interface ReceiveSingleNotificationAction {
    type: typeof RECEIVE_SINGLE_NOTIFICATION;
    payload: {item: Notification};
}

export const receiveSingleNotification = (notification: Notification): ReceiveSingleNotificationAction => ({
    type: RECEIVE_SINGLE_NOTIFICATION,
    payload: {item: notification}
});

interface ReceiveNotificationAction {
    type: typeof RECEIVE_NOTIFICATIONS;
    payload: {items: Notification[]};
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
        const res = await Client.get<Page<Notification>>(notificationsQuery, undefined);
        return receiveNotifications(res.response);
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, "Failed to retrieve notifications, please try again later"));
        return notificationError();
    }
}

type ReadAction = PayloadAction<typeof NOTIFICATION_READ, {id: number | string}>;
/**
 * Sets a notification as read, based on the id
 * @param id the id of the notification that has been read
 */
export const notificationRead = async (id: number): Promise<ReadAction | SetNotificationError> => {
    try {
        if (id >= 0) await Client.post(readNotificationQuery(id));
        return {
            type: NOTIFICATION_READ,
            payload: {id}
        };
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, "Could not mark notification as read"));
        return notificationError();
    }
};

export const notificationError = (): SetNotificationError => ({
    type: NOTIFICATIONS_ERROR
});


type ReadAllAction = Action<typeof READ_ALL>;
/**
 * Sets all notifications as read.
 */
export const readAllNotifications = async (): Promise<ReadAllAction | SetNotificationError> => {
    try {
        await Client.post(readAllNotificationsQuery);
        return {type: READ_ALL};
    } catch (e) {
        snackbarStore.addFailure(
            errorMessageOrDefault(e, "Failed to mark notifications as read, please try again later")
        );
        return notificationError();
    }
};
