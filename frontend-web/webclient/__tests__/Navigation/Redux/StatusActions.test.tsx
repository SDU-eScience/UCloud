import * as StatusActions from "Navigation/Redux/StatusActions";
import {store} from "Utilities/ReduxUtilities";

describe("Status", () => {
    test("Update page title", () => {
        const pageTitle = "New Page Title";
        const storeCopy = {...store};
        storeCopy.dispatch(StatusActions.updatePageTitle(pageTitle));
        expect(storeCopy.getState().status.title).toBe(pageTitle);
    });
});
