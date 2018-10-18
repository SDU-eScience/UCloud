import { initZenodo, ZenodoReduxObject } from "DefaultObjects";
import { ZenodoActions } from "./ZenodoActions";

export const SET_ZENODO_LOADING = "SET_ZENODO_LOADING";
export const RECEIVE_PUBLICATIONS = "RECEIVE_PUBLICATIONS";
export const RECEIVE_ZENODO_LOGIN_STATUS = "RECEIVE_ZENODO_LOGIN_STATUS";
export const SET_ZENODO_ERROR = "SET_ZENODO_ERROR";

const zenodo = (state: ZenodoReduxObject = initZenodo(), action: ZenodoActions): ZenodoReduxObject => {
    switch (action.type) {
        case SET_ZENODO_ERROR:
        case RECEIVE_PUBLICATIONS: {
            return { ...state, ...action.payload, loading: false };
        }
        case SET_ZENODO_LOADING: 
        case RECEIVE_ZENODO_LOGIN_STATUS: {
            return { ...state, ...action.payload };
        }
        default: {
            return state;
        }
    }
}

export default zenodo;