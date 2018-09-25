import * as StatusActions from "Navigation/Redux/StatusActions";
import { configureStore } from "Utilities/ReduxUtilities";
import {  initStatus } from "DefaultObjects";
import status from "Navigation/Redux/StatusReducer";
import { StatusLevel } from "Navigation";

describe("Status", () => {
    test("Update page title", () => {
        const pageTitle = "New Page Title";
        const store = configureStore({ status: initStatus() }, { status });
        store.dispatch(StatusActions.updatePageTitle(pageTitle));
        expect(store.getState().status.title).toBe(pageTitle);
    });

    test("Update status", () => {
        const mockStatus = {
            title: "status title",
            level: "NO ISSUES" as StatusLevel,
            body: "status body"
        };
        const store = configureStore({ status: initStatus() }, { status });
        store.dispatch(StatusActions.updateStatus(mockStatus));
        expect(store.getState().status.status).toEqual(mockStatus);
    });
});