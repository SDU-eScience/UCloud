import { Reducer as ReduxReducer } from "redux";
import { Type as ActionType, Tag } from "./BrowseActions";
import { Type as ReduxType, init } from "./BrowseObject";
import { loadableEventToContent } from "LoadableContent";

export interface Reducer {
    applicationsBrowse: ReduxReducer<ReduxType>
}

const reducer = (state: ReduxType = init().applicationsBrowse, action: ActionType): ReduxType => {
    switch (action.type) {
        case Tag.RECEIVE_APP: {
            return { ...state, applications: loadableEventToContent(action.payload) };
        }
        
        default: {
            return state;
        }
    }
};

export default reducer;