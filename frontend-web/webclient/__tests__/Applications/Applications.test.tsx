import * as React from "react";
import * as Renderer from "react-test-renderer";
import Applications, { SingleApplication } from "Applications/Applications";
import { configureStore } from "Utilities/ReduxUtilities";
import { initApplications } from "DefaultObjects";
import applicationsReducer from "Applications/Redux/ApplicationsReducer";
import { Provider } from "react-redux";
import { applicationsPage } from "../mock/Applications";
import { MemoryRouter } from "react-router";
import { shallow } from "enzyme";

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

    test.skip("Favorite application", () => {
        const func = jest.fn();
        const application = applicationsPage.items[0];
        const singleApp = shallow(<SingleApplication
            app={application}
            favoriteApp={func}
        />);
        singleApp.simulate("click");
        expect(func).toHaveBeenCalled();
    });
});