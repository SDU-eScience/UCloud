import * as ViewObject from "./ViewObject";
import * as ViewReducer from "./ViewReducer";
import * as ApplicationObject from "./BrowseObject";
import * as ApplicationReducer from "./BrowseReducer";
import * as FavoriteObject from "./FavoriteObject";
import * as FavoriteReducer from "./FavoriteReducer";

export type Reducers = ViewReducer.Reducer & ApplicationReducer.Reducer & FavoriteReducer.Reducer;
export type Objects = ViewObject.Wrapper & ApplicationObject.Wrapper & FavoriteObject.Wrapper;

export function init(): Objects {
    return { 
        ...ViewObject.init(),
        ...ApplicationObject.init(),
        ...FavoriteObject.init()
    }
}

export const reducers: Reducers = {
    applicationView: ViewReducer.default,
    applicationsBrowse: ApplicationReducer.default,
    applicationsFavorite: FavoriteReducer.default
};