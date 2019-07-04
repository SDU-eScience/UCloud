import ZenodoInfo from "../../app/Zenodo/Info";
import { create } from "react-test-renderer";
import * as React from "react";
import { MemoryRouter } from "react-router";
import { Provider } from "react-redux";
import { configureStore } from "../../app/Utilities/ReduxUtilities";
import { initObject, initZenodo } from "../../app/DefaultObjects";
import zenodo from "../../app/Zenodo/Redux/ZenodoReducer";
import theme, { responsiveBP } from "../../app/ui-components/theme";
import "jest-styled-components";
import { createResponsiveStateReducer, responsiveStoreEnhancer } from "redux-responsive";
import { ThemeProvider } from "styled-components";

/*  */
const store = configureStore({ zenodo: initZenodo() }, {
    zenodo,
    responsive: createResponsiveStateReducer(
        responsiveBP,
        { infinity: "xxl" }),
}, responsiveStoreEnhancer);

describe("Zenodo Info", () => {
    test("Mount component", () =>
        expect(create(
            <Provider store={store}>
                <ThemeProvider theme={theme}>
                    <MemoryRouter>
                        <ZenodoInfo match={{
                            params: { jobID: "" },
                            isExact: true,
                            path: "",
                            url: ""
                        }} />
                    </MemoryRouter>
                </ThemeProvider>
            </Provider>).toJSON()).toMatchSnapshot()
    );
});