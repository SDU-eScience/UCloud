import {createMemoryHistory} from "history";
import "jest-styled-components";
import * as React from "react";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import JobResults from "../../app/Applications/JobResults";
import theme from "../../app/ui-components/theme";
import {store} from "../../app/Utilities/ReduxUtilities";
import {analyses as analysesPage} from "../mock/Analyses";

describe("Analyses component", () => {
    test("Mount component with non-empty page", () => {
        const storeCopy = {...store}
        storeCopy.getState().analyses.page = analysesPage;
        expect(create(
            <Provider store={storeCopy}>
                <ThemeProvider theme={theme}>
                    <MemoryRouter>
                        <JobResults history={createMemoryHistory()} />
                    </MemoryRouter>
                </ThemeProvider>
            </Provider>).toJSON()).toMatchSnapshot();
    });
});
