import { RECEIVE_NOTIFICATIONS, NOTIFICATION_READ, SET_REDIRECT } from "../Reducers/Notifications";
import { Cloud } from "../../authentication/SDUCloudObject";
import { Page } from "../types/types";
import { failureNotification } from "../UtilityFunctions";

const ERROR = "ERROR";

type Action = { type: string }
interface ReceiveAction extends Action { page: Page<Notification> }
interface ReadAction extends Action { id: Number }

const receiveNotifications = (page: Page<Notification>): ReceiveAction => ({
    type: RECEIVE_NOTIFICATIONS,
    page
})

export const fetchNotifications = () =>
    Cloud.get("/notifications")
        .then(({ response }: { response: Page<Notification> }) => receiveNotifications(response))
        .catch(() => {
            failureNotification("Failed to retrieve notifications, please try again later");
            return NoAction();
        });;

export const notificationRead = (id: Number): ReadAction => ({
    type: NOTIFICATION_READ,
    id
});

export const setRedirectTo = (redirectTo: string) => ({
    type: SET_REDIRECT,
    redirectTo
});

const NoAction = () => ({ type: ERROR })