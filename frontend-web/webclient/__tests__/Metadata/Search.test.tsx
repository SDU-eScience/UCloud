import * as React from "react";
import { Search } from "Project/Search";
import { MemoryRouter } from "react-router";
import { configure, mount } from "enzyme";
import * as PropTypes from "prop-types";
import * as Adapter from "enzyme-adapter-react-16";
import { configureStore } from "Utilities/ReduxUtilities";
import { initStatus } from "DefaultObjects";
import status from "Navigation/Redux/StatusReducer";
import { Provider } from "react-redux";

configure({ adapter: new Adapter() });

describe("Search component", () => {
    test("Mount search component", () => {
        const store = configureStore({ status: initStatus() }, { status });
        expect(mount(<MemoryRouter><Search /></MemoryRouter>, { context: { store: store }, childContextTypes: { store: PropTypes.object} }).html()).toMatchSnapshot()
    })
});