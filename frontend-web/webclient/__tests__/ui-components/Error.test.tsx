import {configure, mount} from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import * as React from "react";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import {Icon, theme} from "../../app/ui-components";
import Error from "../../app/ui-components/Error";
configure({adapter: new Adapter()});

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

    /* SKIP WHILE ENZYME DOESN'T SUPPORT REACT 17 */
    it.skip("Dismissing error", () => {
        const fn = jest.fn();
        const error = mount(<ThemeProvider theme={theme}>
            <Error error="This is an error" clearError={() => fn()} />
        </ThemeProvider>);
        error.find(Icon).first().simulate("click", {stopPropagation: () => undefined});
        expect(fn).toBeCalledTimes(1);
    });
});
