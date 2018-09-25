import Zenodo from "Zenodo/Zenodo";
import { create } from "react-test-renderer";
import { MemoryRouter } from "react-router";
import { configureStore } from "Utilities/ReduxUtilities";
import { Provider } from "react-redux";
import * as React from "react";
import zenodo from "Zenodo/Redux/ZenodoReducer";
import { initZenodo } from "DefaultObjects";


describe("Zenodo", () => {
    test("Mount Zenodo component", () => {
        expect(create(
            <Provider store={configureStore({ zenodo: initZenodo() }, { zenodo })}>
                <MemoryRouter>
                    <Zenodo />
                </MemoryRouter>
            </Provider>
        ).toJSON()).toMatchSnapshot()
    });
});