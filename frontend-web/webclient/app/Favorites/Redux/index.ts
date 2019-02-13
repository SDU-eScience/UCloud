import * as FavoritesObject from "./FavoriteObject";
import * as FavoritesReducer from "./FavoritesReducer";

export type Reducers = FavoritesReducer.Reducer;
export type Objects = FavoritesObject.Wrapper;

export function init(): Objects {
    return {
        ...FavoritesObject.init()
    }
}

export const reducers: Reducers = {
    favorites: FavoritesReducer.default
}