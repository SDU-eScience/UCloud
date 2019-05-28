import { createStore, combineReducers, Store, AnyAction, Action, Dispatch } from "redux";
import { composeWithDevTools } from 'redux-devtools-extension';
import { ReduxObject, initObject } from "DefaultObjects";
import { responsiveBP } from "ui-components/theme";
import { createResponsiveStateReducer } from "redux-responsive";
import { Cloud } from "Authentication/SDUCloudObject";
import { CONTEXT_SWITCH, USER_LOGOUT, USER_LOGIN } from "Navigation/Redux/HeaderReducer";
import { Snack } from "Snackbar/Snackbars";
import { addSnack } from "Snackbar/Redux/SnackbarsActions";
import { receiveSingleNotification } from "Notifications/Redux/NotificationsActions";

export function configureStore(initialObject: Partial<ReduxObject>, reducers, enhancers?): Store<ReduxObject, AnyAction> {
    const combinedReducers = combineReducers<ReduxObject, AnyAction>(reducers);
    const rootReducer = (state: ReduxObject, action: Action): ReduxObject => {
        if ([USER_LOGIN, USER_LOGOUT, CONTEXT_SWITCH].some(it => it === action.type)) {
            state = initObject(Cloud.homeFolder);
        }
        return combinedReducers(state, action);
    }
    return createStore<ReduxObject, AnyAction, {}, {}>(rootReducer, initialObject, composeWithDevTools(enhancers));
};

export const responsive = createResponsiveStateReducer(
    responsiveBP,
    { infinity: "xxl" }
);

export function addNotificationEntry(dispatch: Dispatch, snack: Snack) {
    dispatch(addSnack(snack));
    dispatch(receiveSingleNotification({
        type: `${snack.type}`,
        id: snack.message,
        message: snack.message,
        ts: new Date().getTime(),
        read: false,
        meta: ""
    }));
}