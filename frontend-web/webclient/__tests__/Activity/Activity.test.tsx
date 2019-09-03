import * as React from "react";
import {Provider} from "react-redux";
import {create} from "react-test-renderer";
import {createResponsiveStateReducer, responsiveStoreEnhancer} from "redux-responsive";
import {ThemeProvider} from "styled-components";
import Activity from "../../app/Activity/Page";
import activity from "../../app/Activity/Redux/ActivityReducer";
import {initActivity} from "../../app/DefaultObjects";
import theme, {responsiveBP} from "../../app/ui-components/theme";
import {configureStore} from "../../app/Utilities/ReduxUtilities";

const store = configureStore({
    activity: initActivity(),
    responsive: undefined,
}, {
    activity,
    responsive: createResponsiveStateReducer(
        responsiveBP,
        {infinity: "xxl"}),
}, responsiveStoreEnhancer);


test("Mount Activity Page", () => expect(create(
    <Provider store={store}>
        <ThemeProvider theme={theme}>
            <Activity />
        </ThemeProvider>
    </Provider>
)).toMatchSnapshot());
