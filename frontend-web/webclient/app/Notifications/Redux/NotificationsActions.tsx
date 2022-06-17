import {Client} from "@/Authentication/HttpClientInstance";
import {Action} from "redux";
import {notificationsQuery, readAllNotificationsQuery, readNotificationQuery} from "@/Utilities/NotificationUtilities";
import {errorMessageOrDefault} from "@/UtilityFunctions";
import {Notification} from "..";
import {
    NOTIFICATION_READ,
    NOTIFICATIONS_ERROR,
    READ_ALL,
    RECEIVE_NOTIFICATIONS,
    RECEIVE_SINGLE_NOTIFICATION
} from "./NotificationsReducer";
import {NotificationProps as NotificationCardProps} from "../NotificationCard";

export type NotificationActions = ReceiveNotificationAction | ReceiveSingleNotificationAction | ReadAction |
    SetNotificationError | ReadAllAction;

type SetNotificationError = PayloadAction<typeof NOTIFICATIONS_ERROR, {error: string}>;
type ReceiveSingleNotificationAction = 
    PayloadAction<typeof RECEIVE_SINGLE_NOTIFICATION, {item: Notification | NotificationCardProps}>;
export const receiveSingleNotification = (notification: Notification | NotificationCardProps): ReceiveSingleNotificationAction => ({
    type: RECEIVE_SINGLE_NOTIFICATION,
    payload: {item: notification}
});

type ReceiveNotificationAction = PayloadAction<typeof RECEIVE_NOTIFICATIONS, {items: Notification[]}>;

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
        return notificationError(errorMessageOrDefault(e, "An error occurred. Please reload the page."));
    }
}

type ReadAction = PayloadAction<typeof NOTIFICATION_READ, {id: number | string}>;
/**
 * Sets a notification as read, based on the id
 * @param id the id of the notification that has been read
 */
export const notificationRead = async (id: number): Promise<ReadAction | SetNotificationError> => {
    try {
        // Note(Jonas): ids less than 1 are front-end generated.
        if (id >= 0) await Client.post(readNotificationQuery(), {ids: id});
        return {
            type: NOTIFICATION_READ,
            payload: {id}
        };
    } catch (e) {
        return notificationError(errorMessageOrDefault(e, "Could not mark notification as read."));
    }
};

export const notificationError = (error: string): SetNotificationError => ({
    type: NOTIFICATIONS_ERROR,
    payload: {
        error
    }
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
        return notificationError(errorMessageOrDefault(e, "Failed to mark notifications as read, please try again later."));
    }
};
