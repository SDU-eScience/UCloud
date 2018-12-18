import * as AccountingObject from "./AccountingObject";
import * as AccountingReducer from "./AccountingReducer";

export type Reducers = AccountingReducer.Reducer;
export type Objects = AccountingObject.Wrapper;

export function init(): Objects {
    return {
        ...AccountingObject.init()
    };
}

export const reducers: Reducers = {
    accounting: AccountingReducer.default
};