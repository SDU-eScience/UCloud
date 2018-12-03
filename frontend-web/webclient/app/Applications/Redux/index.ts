import * as ViewObject from "./ViewObject";
import * as ViewReducer from "./ViewReducer";

export type Reducers = ViewReducer.Reducer;
export type Objects = ViewObject.Wrapper;

export function init(): Objects {
    return { 
        ...ViewObject.init() 
    }
}

export const reducers: Reducers = {
    applicationView: ViewReducer.default
};