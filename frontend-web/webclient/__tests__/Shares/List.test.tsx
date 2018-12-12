import * as React from "react";
import List from "Shares/List";
import { create } from "react-test-renderer";
import { Provider } from "react-redux";
import { configureStore } from "Utilities/ReduxUtilities";
import { initFiles } from "DefaultObjects";
import files from "Files/Redux/FilesReducer";
import { MemoryRouter } from "react-router";
import { configure, shallow, mount } from "enzyme"
import * as Adapter from "enzyme-adapter-react-16";
import { shares } from "../mock/Shares";

configure({ adapter: new Adapter() });

describe("Shares List", () => {
    test.skip("Shares component", () => {
        expect(create(
            <Provider store={configureStore({ files: initFiles("/home/user@test.abc/") }, { files })}>
                <MemoryRouter>
                    <List />
                </MemoryRouter>
            </Provider >)).toMatchSnapshot();
    });

    test.skip("Shares component with shares", () => {
        let sharesListWrapper = shallow(
            <Provider store={configureStore({ files: initFiles("/home/user@test.abc/") }, { files })}>
                <MemoryRouter>
                    <List keepTitle={true} />
                </MemoryRouter>
            </Provider >);
        console.warn(shares.items);
        sharesListWrapper = sharesListWrapper.update();
        console.error(sharesListWrapper.find(List).dive().state());
        sharesListWrapper.find(List).dive().setState(() => ({ shares: shares.items }));
        console.error(sharesListWrapper.find(List).dive().state());
        expect(sharesListWrapper.html()).toMatchSnapshot();
    });
});