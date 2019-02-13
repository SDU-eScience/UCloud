import { Reducer as ReduxReducer } from "redux";
import { FavoriteActions } from "./FavoritesActions";
import { init, Type as ReduxType } from "./FavoriteObject";

export const RECEIVE_FAVORITES = "RECEIVE_FAVORITES";
export const SET_ERROR_MESSAGE = "SET_ERROR_MESSAGE";
export const SET_FAVORITES_LOADING = "SET_FAVORITES_LOADING";
export const SET_FAVORITES_SHOWN = "SET_FAVORITES_SHOWN";

export interface Reducer {
    favorites: ReduxReducer<ReduxType>
}

const favorites = (state: ReduxType = init().favorites, { type, payload }: FavoriteActions): ReduxType => {
    switch (type) {
        case RECEIVE_FAVORITES:
        case SET_ERROR_MESSAGE:
        case SET_FAVORITES_LOADING:
        case SET_FAVORITES_SHOWN:
            return { ...state, ...payload };
        default:
            return state;
    }
}

export default favorites;