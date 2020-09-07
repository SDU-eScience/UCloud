import {createBrowserHistory} from "history";
import * as React from "react";
import {Provider} from "react-redux";
import {Router} from "react-router";
import {create, act} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import Files from "../../app/Files/Files";
import theme from "../../app/ui-components/theme";
import {store} from "../../app/Utilities/ReduxUtilities";

jest.mock("Utilities/ProjectUtilities", () => ({
    isAdminOrPI: () => true,
    getProjectNames: () => []
}));

jest.mock("Project/index", () => ({
    ProjectRole: {USER: "USER"},
    useProjectManagementStatus: () => ({projectRole: "USER"})
}));

test("Mount Files component", async () => {
    const history = createBrowserHistory();
    history.push("app/files?path=%2Fhome%2Fjonas%40hinchely.dk");
    let comp;
    act(() => {
        comp = create(
            <Provider store={store}>
                <ThemeProvider theme={theme}>
                    <Router history={history}>
                        <Files />
                    </Router>
                </ThemeProvider>
            </Provider>
        );
    });
    expect(comp.toJSON()).toMatchSnapshot();
});
