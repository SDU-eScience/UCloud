import {Reducer as ReduxReducer} from "redux";
import {FavoriteActions} from "./FavoritesActions";
import {init, Type as ReduxType} from "./FavoriteObject";

export const SET_FAVORITES_LOADING = "SET_FAVORITES_LOADING";

export interface Reducer {
    favorites: ReduxReducer<ReduxType>
}

const favorites = (state: ReduxType = init().favorites, action: FavoriteActions): ReduxType => {
    switch (action.type) {
        case SET_FAVORITES_LOADING:
            return {...state, ...action.payload};
        default:
            return state;
    }
};

export default favorites;