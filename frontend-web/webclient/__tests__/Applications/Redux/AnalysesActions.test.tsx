import * as AnalysesActions from "../../../app/Applications/Redux/AnalysesActions";
import analysesReducer from "../../../app/Applications/Redux/AnalysesReducer";
import { configureStore } from "../../../app/Utilities/ReduxUtilities";
import { initAnalyses } from "../../../app/DefaultObjects";

const emptyPageStore = configureStore({ analyses: initAnalyses() }, { analyses: analysesReducer });

describe("Analyses Actions", () => {

    test("Set error message", () => {
        const errorMessage = "This is an error message";
        emptyPageStore.dispatch(AnalysesActions.setErrorMessage(errorMessage));
        expect(emptyPageStore.getState().analyses.error).toBe(errorMessage);
    });

    test("Clear error message", () => {
        emptyPageStore.dispatch(AnalysesActions.setErrorMessage());
        expect(emptyPageStore.getState().analyses.error).toBeUndefined();
    });

    test("Set as loading and not loading", () => {
        emptyPageStore.dispatch(AnalysesActions.setLoading(true));
        expect(emptyPageStore.getState().analyses.loading).toBe(true);
        emptyPageStore.dispatch(AnalysesActions.setLoading(false));
        expect(emptyPageStore.getState().analyses.loading).toBe(false);
    });
});