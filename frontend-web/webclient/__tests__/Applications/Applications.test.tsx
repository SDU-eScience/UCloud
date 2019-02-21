import * as React from "react";
import * as Renderer from "react-test-renderer";
import { configureStore } from "Utilities/ReduxUtilities";
import { init } from "Applications/Redux/BrowseObject";
import applicationsReducer from "Applications/Redux/BrowseReducer";
import { applicationsPage } from "../mock/Applications";
import { MemoryRouter } from "react-router";
import { shallow } from "enzyme";
import "jest-styled-components";

const emptyPageStore = configureStore({ /* applicationsBrowse: init() */ }, { applications: applicationsReducer });
const fullPageStore = {
    ...emptyPageStore
};

