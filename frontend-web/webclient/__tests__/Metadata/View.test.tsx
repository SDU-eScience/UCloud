import * as React from "react";
import { View } from "Project/View";
import { create } from "react-test-renderer";
import { metadata } from "../mock/Metadata";
import { MemoryRouter } from "react-router";
import { Provider } from "react-redux";
import { configureStore, responsive } from "Utilities/ReduxUtilities";
import "jest-styled-components";

describe("View component", () => {
    test("Mount view", () =>
        expect(create(
            <Provider store={configureStore({}, { responsive })}>
                <MemoryRouter>
                    <View metadata={metadata.metadata} canEdit={true} />
                </MemoryRouter>
            </Provider>
        )).toMatchSnapshot()
    );
}) 