import * as React from "react";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import {theme} from "../../app/ui-components";
import Error from "../../app/ui-components/Error";

describe("Error component", () => {

    it("Error without error and no dismiss button", () => {
        expect(create(
            <ThemeProvider theme={theme}>
                <Error />
            </ThemeProvider>).toJSON()).toMatchSnapshot();
    });

    it("Error with error and no dismiss button", () => {
        expect(create(
            <ThemeProvider theme={theme}>
                <Error error="This is an error" />
            </ThemeProvider>).toJSON()).toMatchSnapshot();
    });

    it("Error with error and dismiss button", () => {
        expect(create(
            <ThemeProvider theme={theme}>
                <Error error="This is an error" clearError={() => undefined} />
            </ThemeProvider>).toJSON()).toMatchSnapshot();
    });
});
