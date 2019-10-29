import * as React from "react";
import * as Renderer from "react-test-renderer";
import {configureStore} from "../../app/Utilities/ReduxUtilities";
import {init} from "../../app/Applications/Redux/BrowseObject";
import applicationsReducer from "../../app/Applications/Redux/BrowseReducer";
import {applicationsPage} from "../mock/Applications";
import {MemoryRouter} from "react-router";
import {shallow} from "enzyme";
import "jest-styled-components";

const emptyPageStore = configureStore({ /* applicationsBrowse: init() */}, {applications: applicationsReducer});
const fullPageStore = {
    ...emptyPageStore
};

