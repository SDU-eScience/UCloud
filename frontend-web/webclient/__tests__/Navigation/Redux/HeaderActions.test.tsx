import * as HeaderActions from "Navigation/Redux/HeaderActions";
import { configureStore } from "Utilities/ReduxUtilities";
import { initHeader, initSidebar, initUploads } from "DefaultObjects";
import header from "Navigation/Redux/HeaderReducer";
import uploader from "Uploader/Redux/UploaderReducer";
import sidebar from "Navigation/Redux/SidebarReducer";

describe("HeaderActions", () => {
    test("Set prioritized search", () => {
        const search = "applications";
        const store = configureStore({ header: initHeader(), sidebar: initSidebar(), uploader: initUploads() }, { header, uploader, sidebar })
        store.dispatch(HeaderActions.setPrioritizedSearch(search));
        expect(store.getState().header.prioritizedSearch).toMatch(search);
    });
});