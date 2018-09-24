import { RECEIVE_NOTIFICATIONS, NOTIFICATION_READ, SET_REDIRECT } from "./NotificationsReducer";
import { Cloud } from "Authentication/SDUCloudObject";
import { Page, ReceivePage, Action } from "Types";
import { failureNotification } from "UtilityFunctions";
import { Notification } from ".."

const ERROR = "ERROR";

/**
 * Returns the action for receiving the notifications
 * @param {Page<Notification>} page Page of notifications received
 */
export const receiveNotifications = (page: Page<Notification>): ReceivePage<Notification> => ({
    type: RECEIVE_NOTIFICATIONS,
    page
})

/**
 * Fetches notifications for the user.
 */
export const fetchNotifications = (): Promise<ReceivePage<Notification> | Action> =>
    Cloud.get("/notifications")
        .then(({ response }) => receiveNotifications(response))
        .catch(() => {
            failureNotification("Failed to retrieve notifications, please try again later");
            return ({ type: ERROR });
        });

interface ReadAction extends Action { id: Number }
/**
 * Sets a notification as read, based on the id
 * @param id the id of the notification that has been read
 */
export const notificationRead = (id: Number): ReadAction => ({
    type: NOTIFICATION_READ,
    id
});

interface SetRedirectToAction extends Action { redirectTo: string }
/**
 * Sets the redirectTo to be used in the Notifications component
 * @param {string} redirectTo the path to be redirected to
 */
export const setRedirectTo = (redirectTo: string): SetRedirectToAction => ({
    type: SET_REDIRECT,
    redirectTo
});