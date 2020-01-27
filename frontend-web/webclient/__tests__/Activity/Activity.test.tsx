import * as React from "react";
import {Provider} from "react-redux";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import Activity from "../../app/Activity/Page";
import theme from "../../app/ui-components/theme";
import {store} from "../../app/Utilities/ReduxUtilities";

test("Mount Activity Page", () => expect(create(
    <Provider store={store}>
        <ThemeProvider theme={theme}>
            <Activity />
        </ThemeProvider>
    </Provider>
)).toMatchSnapshot());
