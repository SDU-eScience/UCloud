import {Reducer as ReduxReducer} from "redux";
import {Type as ActionType, Tag} from "./BrowseActions";
import {Type as ReduxType, init} from "./BrowseObject";

export interface Reducer {
    applicationsBrowse: ReduxReducer<ReduxType>
}

const reducer = (state: ReduxType = init().applicationsBrowse, action: ActionType): ReduxType => {
    switch (action.type) {
        case Tag.RECEIVE_APPS_BY_KEY: {
            const {applications} = state;
            applications.set(action.payload.key, action.payload.page);
            return {...state, applications};
        }
        default: {
            return state;
        }
    }
};

export default reducer;