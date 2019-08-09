export const SET_ANALYSES_LOADING = "SET_ANALYSES_LOADING";
export const RECEIVE_ANALYSES = "RECEIVE_ANALYSES";
export const SET_ANALYSES_ERROR = "SET_ANALYSES_ERROR";
export const CHECK_ANALYSIS = "CHECK_ANALYSIS";
export const CHECK_ALL_ANALYSES = "CHECK_ALL_ANALYSES";
import {AnalysisReduxObject, initAnalyses} from "DefaultObjects";
import {AnalysesActions} from "./AnalysesActions";

const analyses = (state: AnalysisReduxObject = initAnalyses(), action: AnalysesActions): AnalysisReduxObject => {
    switch (action.type) {
        case RECEIVE_ANALYSES:
            return {...state, ...action.payload, loading: false};
        case SET_ANALYSES_LOADING:
            return {...state, ...action.payload};
        case CHECK_ANALYSIS: {
            return {
                ...state, page: {
                    ...state.page, items: state.page.items.map(a => {
                        if (a.jobId === action.payload.jobId) a.checked = action.payload.checked;
                        return a;
                    })
                }
            };
        }
        case CHECK_ALL_ANALYSES:
            return {
                ...state, page: {
                    ...state.page, items: state.page.items.map(a => {
                        a.checked = action.payload.checked;
                        return a;
                    })
                }
            };
        case SET_ANALYSES_ERROR:
        default:
            return state;
    }
};

export default analyses;