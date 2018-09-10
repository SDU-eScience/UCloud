import * as React from "react";
import * as Renderer from "react-test-renderer";
import Applications, { SingleApplication } from "Applications/Applications";
import { configureStore } from "Utilities/ReduxUtilities";
import { initApplications } from "DefaultObjects";
import applicationsReducer from "Applications/Redux/ApplicationsReducer";
import { Provider } from "react-redux";
import { applicationsPage } from "../mock/Applications";
import { MemoryRouter } from "react-router";
import { favoriteApplication } from "UtilityFunctions";

const emptyPageStore = configureStore({ applications: initApplications() }, { applications: applicationsReducer });
const fullPageStore = {
    ...emptyPageStore
};

fullPageStore.getState().applications.page = applicationsPage;

describe("Applications component", () => {
    test("Render empty component", () => {
        expect(Renderer.create(
            <Provider store={emptyPageStore} >
                <MemoryRouter>
                    <Applications />
                </MemoryRouter>
            </Provider>
        ).toJSON()).toMatchSnapshot();
    });

    test("Render full component", () => {
        expect(Renderer.create(
            <Provider store={fullPageStore} >
                <MemoryRouter>
                    <Applications />
                </MemoryRouter>
            </Provider>
        ).toJSON()).toMatchSnapshot();
    });
});

describe("Single Application Component", () => {
    test("Render Single Application", () => {
        expect(Renderer.create(
            <MemoryRouter>
                <SingleApplication
                    app={applicationsPage.items[0]}
                    favoriteApp={() => null}
                />
            </MemoryRouter>).toJSON()).toMatchSnapshot();
    });
});

describe("Single Applications", () => {
    // FIXME Cloud relies on this
    test.skip("Favorite application", () => {
        const application = applicationsPage.items[0];
        const foo = <SingleApplication
            app={application}
            favoriteApp={() => null}
        />;
        // console.log(foo.props.favoriteApp());
    });
});