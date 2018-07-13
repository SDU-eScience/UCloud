export const RECEIVE_NOTIFICATIONS = "RECEIVE_NOTIFICATIONS";
export const NOTIFICATION_READ = "NOTIFICATION_READ";
export const SET_REDIRECT = "SET_REDIRECT";

const Notifications = (state: any = {}, action: any) => {
    switch (action.type) {
        case RECEIVE_NOTIFICATIONS: {
            return { ...state, page: action.page };
        }
        case NOTIFICATION_READ: {
            return {
                ...state, items: state.page.items.map((n) => {
                    if (n.id === action.id) n.read = true;
                    return n;
                })
            }
        }
        case SET_REDIRECT: {
            return { ...state, redirectTo: action.redirectTo }
        }
        default: {
            return state;
        }
    }
}

export default Notifications;