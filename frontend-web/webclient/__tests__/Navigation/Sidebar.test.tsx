import * as React from "react";
import { create } from "react-test-renderer";
import { Provider } from "react-redux";
import { configureStore } from "Utilities/ReduxUtilities";
import { initSidebar } from "DefaultObjects";
import sidebar from "Navigation/Redux/SidebarReducer";
import { MemoryRouter } from "react-router";
import * as Adapter from "enzyme-adapter-react-16";
import { mount, configure } from "enzyme";
import * as SidebarActions from "Navigation/Redux/SidebarActions";
import { Sidebar, Accordion } from "semantic-ui-react";

configure({ adapter: new Adapter() });
const initialWidth = window.innerWidth;

describe("Sidebar", () => {
    describe("Mobile", () => {
        beforeAll(() =>
            // Make the window small enough to trigger responsive mode
            Object.defineProperty(window, "innerWidth", { value: 500 })
        );

        test("Sidebar", () => false);

        afterAll(() => Object.defineProperty(window, "innerWidth", { value: initialWidth }));
    });
});