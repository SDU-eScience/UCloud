import {createMemoryHistory} from "history";
import * as React from "react";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import Runs from "../../app/Applications/Runs";
import theme from "../../app/ui-components/theme";
import {store} from "../../app/Utilities/ReduxUtilities";
import {runs} from "../mock/Runs";

describe("Runs component", () => {
    test("Mount component with non-empty page", () => {
        const storeCopy = {...store};
        storeCopy.getState().analyses.page = runs;
        expect(create(
            <Provider store={storeCopy}>
                <ThemeProvider theme={theme}>
                    <MemoryRouter>
                        <Runs history={createMemoryHistory()} />
                    </MemoryRouter>
                </ThemeProvider>
            </Provider>).toJSON()).toMatchSnapshot();
    });
});
