import * as React from "react";
import Analyses from "Applications/Analyses.tsx";
import { create } from "react-test-renderer";
import { configureStore } from "Utilities/ReduxUtilities";
import { initAnalyses } from "DefaultObjects";
import analyses from "Applications/Redux/AnalysesReducer";
import * as AnalysesActions from "Applications/Redux/AnalysesActions";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router";
import { analyses as analysesPage } from "../mock/Analyses";

describe("Analyses component", () => {
    test("Mount component", () => {
        const store = configureStore({ analyses: initAnalyses() }, { analyses })
        expect(create(
            <Provider store={store}>
                <MemoryRouter>
                    <Analyses />
                </MemoryRouter>
            </Provider>).toJSON()).toMatchSnapshot();
    });

    test.skip("Mount component with non-empty page", () => {
        const store = configureStore({ analyses: initAnalyses() }, { analyses })
        store.getState().analyses.page = analysesPage;
        expect(create(
            <Provider store={store}>
                <MemoryRouter>
                    <Analyses />
                </MemoryRouter>
            </Provider>).toJSON()).toMatchSnapshot();
    });
});