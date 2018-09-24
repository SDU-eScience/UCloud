import * as SidebarAction from "Navigation/Redux/SidebarActions";
import { configureStore } from "Utilities/ReduxUtilities";
import { initSidebar } from "DefaultObjects";
import sidebar from "Navigation/Redux/SidebarReducer";

describe("Sidebar", () => {
    test("Set open", () => {
        const open = true;
        const store = configureStore({ sidebar: initSidebar() }, { sidebar })
        store.dispatch(SidebarAction.setSidebarState(open));
        expect(store.getState().sidebar.open).toBe(open);
    });

    test("Set closed", () => {
        const open = false;
        const store = configureStore({ sidebar: initSidebar() }, { sidebar })
        store.dispatch(SidebarAction.setSidebarState(open));
        expect(store.getState().sidebar.open).toBe(open);
    });
});