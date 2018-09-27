import { NotificationsReduxObject, initNotifications } from "DefaultObjects";
import { NotificationActions } from "./NotificationsActions"; 

export const RECEIVE_NOTIFICATIONS = "RECEIVE_NOTIFICATIONS";
export const NOTIFICATION_READ = "NOTIFICATION_READ";
export const SET_REDIRECT = "SET_REDIRECT";

const Notifications = (state: NotificationsReduxObject = initNotifications(), action: NotificationActions) => {
    switch (action.type) {
        case RECEIVE_NOTIFICATIONS: {
            return { ...state, page: action.payload.page };
        }
        case NOTIFICATION_READ: {
            return {
                ...state, items: state.page.items.map((n) => {
                    if (n.id === action.payload.id) n.read = true;
                    return n;
                })
            }
        }
        case SET_REDIRECT: {
            return { ...state, ...action };
        }
        // FIXME: Missing error case
        default: {
            return state;
        }
    }
}

export default Notifications;