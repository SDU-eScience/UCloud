import * as ViewObject from "./ViewObject";
import * as ViewReducer from "./ViewReducer";
import * as ApplicationObject from "./BrowseObject";
import * as ApplicationReducer from "./BrowseReducer";

export type Reducers = ViewReducer.Reducer & ApplicationReducer.Reducer;
export type Objects = ViewObject.Wrapper & ApplicationObject.Wrapper;

export function init(): Objects {
    return { 
        ...ViewObject.init(),
        ...ApplicationObject.init()
    }
}

export const reducers: Reducers = {
    applicationView: ViewReducer.default,
    applicationsBrowse: ApplicationReducer.default
};