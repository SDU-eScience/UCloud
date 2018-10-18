export const SET_ANALYSES_LOADING = "SET_ANALYSES_LOADING";
export const RECEIVE_ANALYSES = "RECEIVE_ANALYSES";
export const SET_ANALYSES_ERROR = "SET_ANALYSES_ERROR";
import { AnalysisReduxObject, initAnalyses } from "DefaultObjects";
import { AnalysesActions } from "./AnalysesActions";

const analyses = (state: AnalysisReduxObject = initAnalyses(), action: AnalysesActions): AnalysisReduxObject => {
    switch (action.type) {
        case SET_ANALYSES_ERROR: 
        case RECEIVE_ANALYSES: {
            return { ...state, ...action.payload, loading: false };
        }
        case SET_ANALYSES_LOADING: {
            return { ...state, ...action.payload };
        }
        default: {
            return state;
        }
    }
}

export default analyses;