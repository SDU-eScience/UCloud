import ZenodoInfo from "Zenodo/Info";
import { create } from "react-test-renderer";
import * as React from "react";
import { MemoryRouter } from "react-router";
import { Provider } from "react-redux";
import { configureStore } from "Utilities/ReduxUtilities";
import { initZenodo } from "DefaultObjects";
import zenodo from "Zenodo/Redux/ZenodoReducer";

describe("Zenodo Info", () => {
    test("Mount component", () =>
        expect(create(
            <Provider store={configureStore({ zenodo: initZenodo() }, { zenodo })}>
                <MemoryRouter>
                    <ZenodoInfo match={{
                        params: { jobID: "" },
                        isExact: true,
                        path: "",
                        url: ""
                    }} />
                </MemoryRouter>
            </Provider>).toJSON()).toMatchSnapshot()
    );
});