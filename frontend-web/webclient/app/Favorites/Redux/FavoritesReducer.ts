import {Reducer as ReduxReducer} from "redux";
import {FavoriteActions} from "./FavoritesActions";
import {init, Type as ReduxType} from "./FavoriteObject";

export const RECEIVE_FAVORITES = "RECEIVE_FAVORITES";
export const SET_ERROR_MESSAGE = "SET_ERROR_MESSAGE";
export const SET_FAVORITES_LOADING = "SET_FAVORITES_LOADING";
export const SET_FAVORITES_SHOWN = "SET_FAVORITES_SHOWN";
export const CHECK_ALL_FAVORITES = "CHECK_ALL_FAVORITES";
export const CHECK_FAVORITE = "CHECK_FAVORITE";

export interface Reducer {
    favorites: ReduxReducer<ReduxType>
}

const favorites = (state: ReduxType = init().favorites, action: FavoriteActions): ReduxType => {
    switch (action.type) {
        case RECEIVE_FAVORITES:
        case SET_FAVORITES_LOADING:
        case SET_FAVORITES_SHOWN:
            return {...state, ...action.payload};
        case CHECK_ALL_FAVORITES: {
            return {
                ...state, page: {
                    ...state.page, items: state.page.items.map(f => {
                        f.isChecked = action.payload.checked;
                        return f;
                    })
                }
            }
        }
        case CHECK_FAVORITE: {
            return {
                ...state, page: {
                    ...state.page, items: state.page.items.map((f) => {
                        if (action.payload.path === f.path) f.isChecked = action.payload.checked
                        return f;
                    })
                }
            }
        }
        case SET_ERROR_MESSAGE:
        default:
            return state;
    }
}

export default favorites;