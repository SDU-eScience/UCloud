import * as React from "react";
import { MainContainer } from "MainContainer/MainContainer";
import { create } from "react-test-renderer";
import { Provider } from "react-redux";
import { responsive, configureStore } from "Utilities/ReduxUtilities";
import "jest-styled-components";

describe("Main container", () => {
    it("Only main container", () =>
        expect(create(
            <Provider store={configureStore({}, { responsive })}>
                <MainContainer main={<div />} />
            </Provider>
        ).toJSON()).toMatchSnapshot())

    it("Main container with sidebar", () =>
        expect(create(
            <Provider store={configureStore({}, { responsive })}>
                <MainContainer main={<div />} sidebar={<div />} />
            </Provider>
        ).toJSON()).toMatchSnapshot())

    it("Main container with header", () =>
        expect(create(
            <Provider store={configureStore({}, { responsive })}>
                <MainContainer main={<div />} header={<div />} />
            </Provider>
        ).toJSON()).toMatchSnapshot())
});

describe("Loadable Main Container", () => {
    it("", () => { })
})