import * as React from "react";
import JobResults from "../../app/Applications/JobResults";
import {create} from "react-test-renderer";
import {configureStore} from "../../app/Utilities/ReduxUtilities";
import {initAnalyses} from "../../app/DefaultObjects";
import analyses from "../../app/Applications/Redux/AnalysesReducer";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import {analyses as analysesPage} from "../mock/Analyses";
import {createMemoryHistory} from "history";
import "jest-styled-components";
import {responsiveStoreEnhancer, createResponsiveStateReducer} from "redux-responsive";
import theme, {responsiveBP} from "../../app/ui-components/theme";
import {ThemeProvider} from "styled-components";


const configureTestStore = () => configureStore({analyses: initAnalyses()}, {
    analyses,
    responsive: createResponsiveStateReducer(
        responsiveBP,
        {infinity: "xxl"}),
}, responsiveStoreEnhancer);

describe("Analyses component", () => {
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