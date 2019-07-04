import {NotificationsReduxObject, initNotifications} from "DefaultObjects";
import {NotificationActions} from "./NotificationsActions";

export const RECEIVE_SINGLE_NOTIFICATION = "RECEIVE_SINGLE_NOTIFICATION";
export const RECEIVE_NOTIFICATIONS = "RECEIVE_NOTIFICATIONS";
export const NOTIFICATION_READ = "NOTIFICATION_READ";
export const SET_REDIRECT = "SET_REDIRECT";
export const SET_NOTIFICATIONS_ERROR = "SET_NOTIFICATIONS_ERROR";
export const NOTIFICATIONS_ERROR = "NOTIFICATIONS_ERROR";
export const READ_ALL = "READ_ALL";

const Notifications = (
    state: NotificationsReduxObject = initNotifications(),
    action: NotificationActions
): NotificationsReduxObject => {
    switch (action.type) {
        case SET_REDIRECT:
        case RECEIVE_NOTIFICATIONS: {
            return {...state, ...action.payload};
        }
        case RECEIVE_SINGLE_NOTIFICATION: {
            return {...state, items: [action.payload.item].concat(state.items)};
        }
        case NOTIFICATION_READ: {
            return {
                ...state,
                items: state.items.map(n => {
                    if (n.id === action.payload.id) n.read = true;
                    return n;
                })
            }
        }
        case READ_ALL: {
            return {
                ...state,
                items: state.items.map(n => {
                    n.read = true;
                    return n;
                })
            }
        }
        case NOTIFICATIONS_ERROR:
        default: {
            return state;
        }
    }
}

export default Notifications;