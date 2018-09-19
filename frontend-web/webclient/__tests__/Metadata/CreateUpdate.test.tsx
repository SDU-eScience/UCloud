import * as React from "react";
import { create } from "react-test-renderer";
import { CreateUpdate } from "Metadata/CreateUpdate";
import { createMemoryHistory } from "history";
import { Provider } from "react-redux";
import { configureStore } from "Utilities/ReduxUtilities";
import { initStatus } from "DefaultObjects";
import status from "Navigation/Redux/StatusReducer";

describe("CreateUpdate Component", () => {
    test("Mount", () => {
        expect(create(
            <Provider store={configureStore({ status: initStatus() }, { status })}>
                <CreateUpdate
                    match={{ params: { 0: "" }, isExact: true, path: "", url: "" }}
                    history={createMemoryHistory()}
                />
            </Provider>
        ).toJSON()).toMatchSnapshot()
    });
});