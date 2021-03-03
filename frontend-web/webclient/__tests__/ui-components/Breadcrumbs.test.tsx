import * as React from "react";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import {BreadCrumbs} from "../../app/ui-components/Breadcrumbs";
import theme from "../../app/ui-components/theme";
import {Client} from "../../app/Authentication/HttpClientInstance";
import {Provider} from "react-redux";
import {store} from "../../app/Utilities/ReduxUtilities";

describe("Breadcrumbs", () => {
    it("Build breadcrumbs, embedded", () => {
        expect(create(
            <ThemeProvider theme={theme}>
                <Provider store={store}>
                    <BreadCrumbs
                        embedded
                        currentPath="/home/mail@mailhost.dk/folder1"
                        navigate={() => undefined}
                        client={Client}
                    />
                </Provider>
            </ThemeProvider>)).toMatchSnapshot();
    });

    it("Build breadcrumbs with empty path, embedded", () => {
        expect(create(
            <ThemeProvider theme={theme}>
                <Provider store={store}>
                    <BreadCrumbs
                        embedded
                        currentPath=""
                        navigate={() => undefined}
                        client={Client}
                    />
                </Provider>
            </ThemeProvider>
        )).toMatchSnapshot();
    });

    it("Build breadcrumbs, unembedded", () => {
        expect(create(
            <ThemeProvider theme={theme}>
                <Provider store={store}>
                    <BreadCrumbs
                        embedded={false}
                        currentPath="/home/mail@mailhost.dk/folder1"
                        navigate={() => undefined}
                        client={Client}
                    />
                </Provider>
            </ThemeProvider>)).toMatchSnapshot();
    });

    it("Build breadcrumbs with empty path, unembedded", () => {
        expect(create(
            <ThemeProvider theme={theme}>
                <Provider store={store}>
                    <BreadCrumbs
                        embedded={false}
                        currentPath=""
                        navigate={() => undefined}
                        client={Client}
                    />
                </Provider>
            </ThemeProvider>
        )).toMatchSnapshot();
    });
});
