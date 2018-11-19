import * as React from "react";
import * as Renderer from "react-test-renderer";
import Applications, { ApplicationCard } from "Applications/Applications";
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


describe("Single Application Component", () => {
    test("Render Single Application", () => {
        expect(Renderer.create(
            <MemoryRouter>
                <ApplicationCard
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
        const singleApp = shallow(<ApplicationCard
            app={application}
            favoriteApp={func}
        />);
        singleApp.simulate("click");
        expect(func).toHaveBeenCalled();
    });
});