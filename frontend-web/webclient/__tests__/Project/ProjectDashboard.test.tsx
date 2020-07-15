import * as React from "react";
import ProjectDashboard from "Project/ProjectDashboard";
import {create} from "react-test-renderer";
import {Provider} from "react-redux";
import {store} from "Utilities/ReduxUtilities";
import {ThemeProvider} from "styled-components";
import theme from "../../app/ui-components/theme";
import {MemoryRouter} from "react-router";

jest.mock("Utilities/ProjectUtilities", () => ({
    isAdminOrPI: () => false
}));


jest.mock("Project/index", () => ({
    useProjectManagementStatus: () => ({
        projectId: "this-is-an-id",
        projectDetails: {data: {title: "this-is-a-title"}},
        projectRole: "ADMIN"
    })
}));

test("Mount ProjectDashboard", () => {
    expect(create(
        <MemoryRouter>
            <Provider store={store}>
                <ThemeProvider theme={theme}>
                    <ProjectDashboard />
                </ThemeProvider>
            </Provider>
        </MemoryRouter>
    ).toJSON()).toMatchSnapshot();
});
