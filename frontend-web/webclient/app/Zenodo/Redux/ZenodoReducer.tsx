import { initZenodo, ZenodoReduxObject } from "DefaultObjects";
import { ZenodoActions } from "./ZenodoActions";

export const SET_ZENODO_LOADING = "SET_ZENODO_LOADING";
export const RECEIVE_PUBLICATIONS = "RECEIVE_PUBLICATIONS";
export const RECEIVE_ZENODO_LOGIN_STATUS = "RECEIVE_ZENODO_LOGIN_STATUS";
export const SET_ZENODO_ERROR = "SET_ZENODO_ERROR";

const zenodo = (state: ZenodoReduxObject = initZenodo(), action: ZenodoActions): ZenodoReduxObject => {
    switch (action.type) {
        case RECEIVE_PUBLICATIONS: {
            action.page
            return { ...state, page: action.page, loading: false };
        }
        case SET_ZENODO_LOADING: {
            return { ...state, loading: action.loading };
        }
        case RECEIVE_ZENODO_LOGIN_STATUS: {
            return { ...state, connected: action.connected };
        }
        case SET_ZENODO_ERROR: {
            return { ...state, error: action.error, loading: false };
        }
        default: {
            return state;
        }
    }
}

export default zenodo;