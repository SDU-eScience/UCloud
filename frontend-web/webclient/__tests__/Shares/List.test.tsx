import * as React from "react";
import List from "../../app/Shares/List";
import { create } from "react-test-renderer";
import { Provider } from "react-redux";
import { configureStore } from "../../app/Utilities/ReduxUtilities";
import { initFiles, initShares, initObject } from "../../app/DefaultObjects";
import shares from "../../app/Shares/Redux/SharesReducer"
import { MemoryRouter } from "react-router";
import { configure, shallow, mount } from "enzyme"
import * as Adapter from "enzyme-adapter-react-16";
import { shares as mock_shares } from "../mock/Shares";
import { createResponsiveStateReducer, responsiveStoreEnhancer } from "redux-responsive";
import theme, { responsiveBP } from "../../app/ui-components/theme";
import { ThemeProvider } from "styled-components";
import "jest-styled-components";

configure({ adapter: new Adapter() });


const store = configureStore({ shares: initShares() }, {
    shares,
    responsive: createResponsiveStateReducer(
        responsiveBP,
        { infinity: "xxl" }),
}, responsiveStoreEnhancer);

describe("Shares List", () => {
    test("Shares component", () => {
        expect(create(
            <Provider store={store}>
                <ThemeProvider theme={theme}>
                    <MemoryRouter>
                        <List />
                    </MemoryRouter>
                </ThemeProvider>
            </Provider >)).toMatchSnapshot();
    });

    test.skip("Shares component with shares", () => {
        let sharesListWrapper = shallow(
            <Provider store={store}>
                <MemoryRouter>
                    <List />
                </MemoryRouter>
            </Provider >);
        console.warn(mock_shares.items);
        sharesListWrapper = sharesListWrapper.update();
        console.error(sharesListWrapper.find(List).dive().state());
        sharesListWrapper.find(List).dive().setState(() => ({ shares: mock_shares.items }));
        console.error(sharesListWrapper.find(List).dive().state());
        expect(sharesListWrapper.html()).toMatchSnapshot();
    });
});