import { SharesActions } from "./SharesActions";
import { SharesReduxObject, initShares } from "DefaultObjects";

export const RECEIVE_SHARES = "RECEIVE_SHARES";
export const SET_SHARES_ERROR = "SET_SHARES_ERROR";
export const SET_SHARE_STATE = "SET_SHARE_STATE";
export const SET_SHARES_LOADING = "SET_SHARES_LOADING";

const shares = (state: SharesReduxObject = initShares(), action: SharesActions) => {
    switch (action.type) {
        case RECEIVE_SHARES:
        case SET_SHARE_STATE:
        case SET_SHARES_ERROR:
        case SET_SHARES_LOADING:
            return { ...state, ...action.payload };
        default:
            return state;
    }
}

export default shares