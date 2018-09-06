import * as ApplicationsActions from "Applications/Redux/ApplicationsActions";
import { configureStore } from "Utilities/ReduxUtilities";
import { initApplications, emptyPage } from "DefaultObjects";
import { applicationsPage } from "../../mock/Applications"
import { createMemoryHistory } from "history";
import applications from "Applications/Redux/ApplicationsReducer";

const emptyPageStore = configureStore({ applications: initApplications() }, { applications });

const mockHistory = createMemoryHistory();

const fullPageStore = {
    ...emptyPageStore
};

fullPageStore.getState().applications.page = applicationsPage

describe("Applications actions", () => {
    test("Set Applications loading, true", () => {
        emptyPageStore.dispatch(ApplicationsActions.setLoading(true));
        expect(emptyPageStore.getState().applications.loading).toBe(true);
    });

    test("Set Applications loading, false", () => {
        emptyPageStore.dispatch(ApplicationsActions.setLoading(false));
        expect(emptyPageStore.getState().applications.loading).toBe(false);
    });

    test("Set error message", () => {
        const errorMessage = "Error message example in Applications";
        emptyPageStore.dispatch(ApplicationsActions.setErrorMessage(errorMessage));
        expect(emptyPageStore.getState().applications.error).toBe(errorMessage);
    });

    test("No error message", () => {
        emptyPageStore.dispatch(ApplicationsActions.setErrorMessage());
        expect(emptyPageStore.getState().applications.error).toBe(undefined);
    });

    test("Update applications, from empty", () => {
        emptyPageStore.dispatch(ApplicationsActions.updateApplications(applicationsPage));
        expect(emptyPageStore.getState().applications.page).toBe(applicationsPage);
    });

    test("Update applications, to empty", () => {
        fullPageStore.dispatch(ApplicationsActions.updateApplications(emptyPage));
        expect(fullPageStore.getState().applications.page).toBe(emptyPage);
    });
});
