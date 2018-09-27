import * as React from "react";
import SidebarComponent from "Navigation/Sidebar";
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

        test("Mount sidebar", () => {
            expect(create(
                <Provider store={configureStore({ sidebar: initSidebar() }, { sidebar })}>
                    <MemoryRouter>
                        <SidebarComponent />
                    </MemoryRouter>
                </Provider>)).toMatchSnapshot();
        });

        test("Close open sidebar using every possible option", () => {
            const store = configureStore({ sidebar: initSidebar() }, { sidebar });
            const sidebarWrapper = mount(
                <Provider store={store}>
                    <MemoryRouter>
                        <SidebarComponent />
                    </MemoryRouter>
                </Provider>
            );
            const sidebarOptions = sidebarWrapper.findWhere(it => it.props().className === "sidebar-option").findWhere(it => !!it.props().to);
            sidebarOptions.forEach(opt => {
                store.dispatch(SidebarActions.setSidebarState(true));
                expect(store.getState().sidebar.open).toBe(true);
                opt.simulate("click");
                expect(store.getState().sidebar.open).toBe(false);
            });
        });

        test("Close open sidebar using sidebar option", () => {
            const store = configureStore({ sidebar: initSidebar() }, { sidebar });
            const sidebarWrapper = mount(
                <Provider store={store}>
                    <MemoryRouter>
                        <SidebarComponent />
                    </MemoryRouter>
                </Provider>
            );
            store.dispatch(SidebarActions.setSidebarState(true));
            sidebarWrapper.find(Sidebar.Pusher).simulate("click");
            expect(store.getState().sidebar.open).toBe(false);
        });

        test("Open accordion", () => {
            const store = configureStore({ sidebar: initSidebar() }, { sidebar });
            const sidebarWrapper = mount(
                <Provider store={store}>
                    <MemoryRouter>
                        <SidebarComponent />
                    </MemoryRouter>
                </Provider>
            );
            const accordionTitles = sidebarWrapper.find(Accordion.Title).findWhere(it => it.props().index === 0);
            accordionTitles.first().simulate("click");
            const sidebarComponent = sidebarWrapper.find(SidebarComponent);
            expect((sidebarComponent.children().first().instance().state as any).activeIndices).toEqual([true, false, false]);
        });

        afterAll(() => Object.defineProperty(window, "innerWidth", { value: initialWidth }));
    });
});