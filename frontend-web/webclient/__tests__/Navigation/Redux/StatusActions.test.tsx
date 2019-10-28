import * as StatusActions from "Navigation/Redux/StatusActions";
import {configureStore} from "Utilities/ReduxUtilities";
import {initStatus} from "DefaultObjects";
import status from "Navigation/Redux/StatusReducer";

describe("Status", () => {
    test("Update page title", () => {
        const pageTitle = "New Page Title";
        const store = configureStore({status: initStatus()}, {status});
        store.dispatch(StatusActions.updatePageTitle(pageTitle));
        expect(store.getState().status.title).toBe(pageTitle);
    });
});
