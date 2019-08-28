import {createMemoryHistory} from "history";
import "jest-styled-components";
import * as React from "react";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import {create} from "react-test-renderer";
import {createResponsiveStateReducer, responsiveStoreEnhancer} from "redux-responsive";
import {ThemeProvider} from "styled-components";
import JobResults from "../../app/Applications/JobResults";
import analyses from "../../app/Applications/Redux/AnalysesReducer";
import {initAnalyses} from "../../app/DefaultObjects";
import theme, {responsiveBP} from "../../app/ui-components/theme";
import {configureStore} from "../../app/Utilities/ReduxUtilities";
import {analyses as analysesPage} from "../mock/Analyses";

const configureTestStore = () => configureStore({analyses: initAnalyses()}, {
    analyses,
    responsive: createResponsiveStateReducer(
        responsiveBP,
        {infinity: "xxl"}),
}, responsiveStoreEnhancer);

describe("Analyses component", () => {
    // TODO This test is causing Jest to crash
    /*
    test("Mount component", () => {
        const store = configureTestStore();
        expect(create(
            <Provider store={store}>
                <ThemeProvider theme={theme}>
                    <MemoryRouter>
                        <JobResults history={createMemoryHistory()} />
                    </MemoryRouter>
                </ThemeProvider>
            </Provider>).toJSON()).toMatchSnapshot();
    });
     */

    test("Mount component with non-empty page", () => {
        const store = configureTestStore();
        store.getState().analyses.page = analysesPage;
        expect(create(
            <Provider store={store}>
                <ThemeProvider theme={theme}>
                    <MemoryRouter>
                        <JobResults history={createMemoryHistory()} />
                    </MemoryRouter>
                </ThemeProvider>
            </Provider>).toJSON()).toMatchSnapshot();
    });
});
