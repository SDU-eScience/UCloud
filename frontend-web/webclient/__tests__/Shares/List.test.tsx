import * as React from "react";
import { List } from "Shares/List";
import { create } from "react-test-renderer";
import { Provider } from "react-redux";
import { configureStore } from "Utilities/ReduxUtilities";
import { initFiles } from "DefaultObjects";
import files from "Files/Redux/FilesReducer";
import { MemoryRouter } from "react-router";

describe("Shares List", () => {
    test("Shares component", () => {
        expect(create(
            <Provider store={configureStore({ files: initFiles({ homeFolder: "/home/user@test.abc/" }) }, { files })}>
                <MemoryRouter>
                    <List />
                </MemoryRouter>
            </Provider >)).toMatchSnapshot();
    });

    test("Shares component", () => {
        expect(create(
            <Provider store={configureStore({ files: initFiles({ homeFolder: "/home/user@test.abc/" }) }, { files })}>
                <MemoryRouter>
                    <List keepTitle={true} />
                </MemoryRouter>
            </Provider >)).toMatchSnapshot();
    });
});