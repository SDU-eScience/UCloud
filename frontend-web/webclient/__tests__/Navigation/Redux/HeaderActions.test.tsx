import * as HeaderActions from "../../../app/Navigation/Redux/HeaderActions";
import {store} from "../../../app/Utilities/ReduxUtilities";

describe("HeaderActions", () => {
    test("Set prioritized search", () => {
        const search = "applications";
        const storeCopy = {...store};
        storeCopy.dispatch(HeaderActions.setPrioritizedSearch(search));
        expect(storeCopy.getState().header.prioritizedSearch).toMatch(search);
    });
});
