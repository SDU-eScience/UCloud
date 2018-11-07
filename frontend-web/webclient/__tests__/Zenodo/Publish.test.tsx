import ZenodoPublish from "Zenodo/Publish";
import { create } from "react-test-renderer";
import * as React from "react";
import { configureStore } from "Utilities/ReduxUtilities";
import { initZenodo } from "DefaultObjects";
import zenodo from "Zenodo/Redux/ZenodoReducer";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router";
import { configure } from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import { mount } from "enzyme";
import * as ZenodoActions from "Zenodo/Redux/ZenodoActions";
import { Button } from "semantic-ui-react";

configure({ adapter: new Adapter });

describe("Zenodo Publish", () => {
    test("Mount Zenodo component", () => {
        expect(create(
            <Provider store={configureStore({ zenodo: initZenodo() }, { zenodo })}>
                <MemoryRouter>
                    <ZenodoPublish />
                </MemoryRouter>
            </Provider>
        ).toJSON()).toMatchSnapshot()
    });

    test("Add and remove file row", () => {
        const store = configureStore({ zenodo: initZenodo() }, { zenodo })
        store.dispatch(ZenodoActions.receiveLoginStatus(true));
        store.dispatch(ZenodoActions.setZenodoLoading(false));
        const publishWrapper = mount(
            <Provider store={store}>
                <MemoryRouter>
                    <ZenodoPublish />
                </MemoryRouter>
            </Provider>
        );
        expect((publishWrapper.find(ZenodoPublish).children().instance().state as any).files.length).toBe(1);
        publishWrapper.find(Button).findWhere(it => it.props().content === "Add file").simulate("click");
        expect((publishWrapper.find(ZenodoPublish).children().instance().state as any).files.length).toBe(2);
        publishWrapper.find(Button).findWhere(it => it.props().content === "✗").first().simulate("click");
        expect((publishWrapper.find(ZenodoPublish).children().instance().state as any).files.length).toBe(1);
    });

    test("Update name", () => {
        const pubName = "Publication name";
        const store = configureStore({ zenodo: initZenodo() }, { zenodo })
        store.dispatch(ZenodoActions.receiveLoginStatus(true));
        const publishWrapper = mount(
            <Provider store={store}>
                <MemoryRouter>
                    <ZenodoPublish />
                </MemoryRouter>
            </Provider>
        );
        store.dispatch(ZenodoActions.setZenodoLoading(false));
        publishWrapper.find("input").last().simulate("change", { target: { value: pubName } });
        expect((publishWrapper.find(ZenodoPublish).children().instance().state as any).name).toBe(pubName);
    });
});