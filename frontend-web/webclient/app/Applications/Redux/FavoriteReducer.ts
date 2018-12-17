import { Reducer as ReduxReducer } from "redux";
import { Type as ActionType, Tag } from "./FavoriteActions";
import { Type as ReduxType, init } from "./FavoriteObject";
import { loadableEventToContent } from "LoadableContent";

export interface Reducer {
    applicationsFavorite: ReduxReducer<ReduxType>
}

const reducer = (state: ReduxType = init().applicationsFavorite, action: ActionType): ReduxType => {
    switch (action.type) {
        case Tag.RECEIVE_APP: {
            return { ...state, applications: { ...state.applications, ...loadableEventToContent(action.payload) }};
        }
        
        default: {
            return state
        }
    }
};

export default reducer;