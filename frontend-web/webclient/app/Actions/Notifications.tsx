import { RECEIVE_NOTIFICATIONS, NOTIFICATION_READ, SET_REDIRECT } from "../Reducers/Notifications";
import { Cloud } from "../../authentication/SDUCloudObject";
import { Page, ReceivePage, Action } from "../types/types";
import { failureNotification } from "../UtilityFunctions";

const ERROR = "ERROR";

const receiveNotifications = (page: Page<Notification>): ReceivePage<Notification> => ({
    type: RECEIVE_NOTIFICATIONS,
    page
})

export const fetchNotifications = ():Promise<ReceivePage<File> | Action> =>
    Cloud.get("/notifications")
        .then(({ response }) => receiveNotifications(response))
        .catch(() => {
            failureNotification("Failed to retrieve notifications, please try again later");
            return NoAction();
        });;

interface ReadAction extends Action { id: Number }
export const notificationRead = (id: Number): ReadAction => ({
    type: NOTIFICATION_READ,
    id
});

interface SetRedirectToAction extends Action { redirectTo: string }
export const setRedirectTo = (redirectTo: string):SetRedirectToAction => ({
    type: SET_REDIRECT,
    redirectTo
});

const NoAction = () => ({ type: ERROR })