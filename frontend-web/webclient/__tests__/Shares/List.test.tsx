import * as React from "react";
import List from "../../app/Shares/List";
import {create} from "react-test-renderer";
import {MemoryRouter} from "react-router";
import {configure, shallow} from "enzyme"
import * as Adapter from "enzyme-adapter-react-16";
import {shares as mock_shares} from "../mock/Shares";
import theme, {responsiveBP} from "../../app/ui-components/theme";
import {ThemeProvider} from "styled-components";
import "jest-styled-components";
import {configureStore} from "../../app/Utilities/ReduxUtilities";
import {initResponsive} from "../../app/DefaultObjects";
import {createResponsiveStateReducer} from "redux-responsive";
import {Provider} from "react-redux";

configure({adapter: new Adapter()});

const store = configureStore({responsive: initResponsive()}, {
    responsive: createResponsiveStateReducer(
        responsiveBP,
        {infinity: "xxl"}
    )
});

describe("Shares List", () => {
    test("Shares component", () => {
        expect(create(
            <Provider store={store}>
                <ThemeProvider theme={theme}>
                    <MemoryRouter>
                        <List/>
                    </MemoryRouter>
                </ThemeProvider>
            </Provider>)).toMatchSnapshot();
    });
});