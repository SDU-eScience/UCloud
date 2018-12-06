import * as React from "react";
import * as Renderer from "react-test-renderer";
import { ApplicationCard } from "Applications/Card";
import { configureStore } from "Utilities/ReduxUtilities";
import { init } from "Applications/Redux/BrowseObject";
import applicationsReducer from "Applications/Redux/BrowseReducer";
import { applicationsPage } from "../mock/Applications";
import { MemoryRouter } from "react-router";
import { shallow } from "enzyme";

const emptyPageStore = configureStore({ applications: init() }, { applications: applicationsReducer });
const fullPageStore = {
    ...emptyPageStore
};



describe("Single Application Component", () => {
    test.skip("Render Single Application", () => {
        expect(Renderer.create(
            <MemoryRouter>
                <ApplicationCard
                    app={applicationsPage.items[0]}
                    onFavorite={() => null}
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
            onFavorite={func}
        />);
        singleApp.simulate("click");
        expect(func).toHaveBeenCalled();
    });
});