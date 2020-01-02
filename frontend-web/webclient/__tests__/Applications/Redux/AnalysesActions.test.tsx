import * as AnalysesActions from "../../../app/Applications/Redux/AnalysesActions";
import {store} from "../../../app/Utilities/ReduxUtilities";

const emptyPageStore = {...store};

describe("Analyses Actions", () => {

    test("Set as loading and not loading", () => {
        emptyPageStore.dispatch(AnalysesActions.setLoading(true));
        expect(emptyPageStore.getState().analyses.loading).toBe(true);
        emptyPageStore.dispatch(AnalysesActions.setLoading(false));
        expect(emptyPageStore.getState().analyses.loading).toBe(false);
    });
});