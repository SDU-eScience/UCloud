import {Reducer as ReduxReducer} from "redux";
import {Tag, Type as ActionType} from "./BrowseActions";
import {init, Type as ReduxType} from "./BrowseObject";

export interface Reducer {
    applicationsBrowse: ReduxReducer<ReduxType>;
}

const reducer = (state: ReduxType = init().applicationsBrowse, action: ActionType): ReduxType => {
    switch (action.type) {
        case Tag.RECEIVE_APPS_BY_KEY: {
            const {applications} = state;
            applications.set(action.payload.key, action.payload.page);
            return {...state, applications};
        }
        case Tag.RECEIVE_APP: {
            return {...state, applicationsPage: {...action.payload, loading: false}};
        }
        case Tag.RECEIVE_APPS_BY_KEY_ERROR:
        default:
            return state;
    }
};

export default reducer;
