import * as React from "react";
import {MainContainer} from "MainContainer/MainContainer";
import {create} from "react-test-renderer";
import {Provider} from "react-redux";
import {responsive, configureStore} from "Utilities/ReduxUtilities";
import "jest-styled-components";
import theme from "ui-components/theme";
import {ThemeProvider} from "styled-components";

describe("Main container", () => {
    it("Only main container", () =>
        expect(create(
            <Provider store={configureStore({}, {responsive})}>
                <ThemeProvider theme={theme}>
                    <MainContainer main={<div />} />
                </ThemeProvider>
            </Provider>
        ).toJSON()).toMatchSnapshot())

    it("Main container with sidebar", () =>
        expect(create(
            <Provider store={configureStore({}, {responsive})}>
                <ThemeProvider theme={theme}>
                    <MainContainer main={<div />} sidebar={<div />} />
                </ThemeProvider>
            </Provider>
        ).toJSON()).toMatchSnapshot())

    it("Main container with header", () =>
        expect(create(
            <Provider store={configureStore({}, {responsive})}>
                <ThemeProvider theme={theme}>
                    <MainContainer main={<div />} header={<div />} />
                </ThemeProvider>
            </Provider>
        ).toJSON()).toMatchSnapshot());
});