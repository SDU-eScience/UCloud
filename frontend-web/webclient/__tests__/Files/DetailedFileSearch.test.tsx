import * as React from "react";
import * as Renderer from "react-test-renderer";
import DetailedFileSearch from "Files/DetailedFileSearch";
import { Provider } from "react-redux";
import { configureStore } from "Utilities/ReduxUtilities";
import { initFiles } from "DefaultObjects";
import files from "Files/Redux/FilesReducer";

const store = configureStore({ files: initFiles({ homeFolder: "/home/person@place.tv" }) }, { files });

describe("DetailedFileSearch", () => {
    test("Detailed File Search component", () => {
        expect(Renderer.create(
            <Provider store={store}>
                <DetailedFileSearch />
            </Provider>
        ).toJSON()).toMatchSnapshot();
    });
});