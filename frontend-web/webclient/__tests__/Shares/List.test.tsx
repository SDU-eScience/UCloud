import * as React from "react";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import {create, act} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import List from "../../app/Shares/List";
import theme from "../../app/ui-components/theme";
import {store} from "../../app/Utilities/ReduxUtilities";
import {emptyPage} from "../../app/DefaultObjects";

jest.mock("Authentication/HttpClientInstance", () => ({
    Client: {
        get: (path: string) => Promise.resolve({request: {status: 200} as XMLHttpRequest, response: emptyPage}),
        call: (p) => Promise.resolve({request: {status: 200} as XMLHttpRequest, response: emptyPage}),
        homeFolder: "/home/test@test/"
    }
}));

jest.mock("Utilities/ProjectUtilities", () => ({
    getProjectNames: () => []
}));

describe("Shares List", () => {
    test("Shares component", async () => {
        await act(async () => {
            expect(await create(
                <Provider store={store}>
                    <ThemeProvider theme={theme}>
                        <MemoryRouter>
                            <List />
                        </MemoryRouter>
                    </ThemeProvider>
                </Provider>
            )).toMatchSnapshot();
        });
    });
});
