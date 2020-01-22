import * as React from "react";
import {Provider} from "react-redux";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import {MainContainer} from "../../app/MainContainer/MainContainer";
import theme from "../../app/ui-components/theme";
import {store} from "../../app/Utilities/ReduxUtilities";

describe("Main container", () => {
    it("Only main container", () =>
        expect(create(
            <Provider store={store}>
                <ThemeProvider theme={theme}>
                    <MainContainer main={<div />} />
                </ThemeProvider>
            </Provider>
        ).toJSON()).toMatchSnapshot());

    it("Main container with sidebar", () =>
        expect(create(
            <Provider store={store}>
                <ThemeProvider theme={theme}>
                    <MainContainer main={<div />} sidebar={<div />} />
                </ThemeProvider>
            </Provider>
        ).toJSON()).toMatchSnapshot());

    it("Main container with header", () =>
        expect(create(
            <Provider store={store}>
                <ThemeProvider theme={theme}>
                    <MainContainer main={<div />} header={<div />} />
                </ThemeProvider>
            </Provider>
        ).toJSON()).toMatchSnapshot());
});
