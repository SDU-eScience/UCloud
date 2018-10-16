import * as React from "react";
import * as ReactDOM from "react-dom";
import { Provider } from "react-redux";
import { BrowserRouter } from "react-router-dom";
import Core from "Core";
import { Cloud } from "Authentication/SDUCloudObject";
import { initObject } from "DefaultObjects";
import header from "Navigation/Redux/HeaderReducer";
import files from "Files/Redux/FilesReducer";
import status from "Navigation/Redux/StatusReducer";
import applications from "Applications/Redux/ApplicationsReducer";
import dashboard from "Dashboard/Redux/DashboardReducer";
import zenodo from "Zenodo/Redux/ZenodoReducer";
import sidebar from "Navigation/Redux/SidebarReducer";
import analyses from "Applications/Redux/AnalysesReducer";
import notifications from "Notifications/Redux/NotificationsReducer";
import uploader from "Uploader/Redux/UploaderReducer";
import activity from "Activity/Redux/ActivityReducer";
import detailedResult from "Applications/Redux/DetailedResultReducer";
import simpleSearch from "SimpleSearch/Redux/SimpleSearchReducer"
import { configureStore } from "Utilities/ReduxUtilities";

window.onload = () => Cloud.receiveAccessTokenOrRefreshIt();

const store = configureStore(initObject(Cloud.homeFolder), {
    activity,
    files,
    dashboard,
    analyses,
    applications,
    header,
    status,
    zenodo,
    sidebar,
    uploader,
    notifications,
    detailedResult,
    simpleSearch
});

ReactDOM.render(
    <Provider store={store}>
        <BrowserRouter basename="app">
            <Core />
        </BrowserRouter>
    </Provider>,
    document.getElementById("app")
);

import {
    Flex,
    Box,
    Text,
    Icon,
    ThemeProvider,
    Relative,
    Absolute,
    Badge,
    Input,
    Tooltip,
    FormField,
    Label,
    Heading
} from "ui-components";
import styled from "styled-components";

const HeaderContainer = styled(Flex)`
    background-color: ${props => props.theme.colors["lightBlue"]}
    height: 48px;
    align-items: center;
    color: #fff;
    background-color: rgba(0, 85, 213, 1);

    * {
        font-family: "Roboto", Arial, Helvetica, sans-serif;
    }
`;

const Logo = () => (
    <Text fontSize="3">
        SDUCloud
        </Text>
);

const Notification = () => (
    <>
        <Relative top="0" left="0">
            <Flex justify="center" width="60px">
                <Icon name="notification" legacy={false} />
            </Flex>

            <Absolute top="-12px" left="28px">
                <Badge bg="red">42</Badge>
            </Absolute>
        </Relative>
    </>
);

const Dropdown = (props) => (
    <>
        {props.children}
    </>
);

const SearchInput = styled(Flex)`
    width: 300px;
    height: 36px;
    align-items: center;
    color: white;
    background-color: rgba(236, 239, 244, 0.247);
    border-color: rgba(201, 201, 233, 1);

    input::-webkit-input-placeholder, input::-moz-placeholder, input::-ms-input-placeholder, input::-moz-placeholder { 
        color: black;
    }

    input:focus ~ div > label > svg {
        color: black;
    }

    input ~ div > label > svg {
        color: white;
    }

    input:focus {
        color: black;
        background-color: white; 
    }

    input {
        border-radius: 5px;
        background-color: rgba(1, 1, 1, 0.1);
        padding: 10px;
        padding-left: 30px;
    }
`;

const Search = () => (
    <Relative>
        <SearchInput>
            <Input pl={"30px"}
                id="search_input"
                placeholder="Do search..."
            />
            <Absolute left="6px" top="7px">
                <Label htmlFor="search_input">
                    <Icon name="search" size="20" />
                </Label>
            </Absolute>
        </SearchInput>
    </Relative>
);

const Header = () => (
    <HeaderContainer>
        <Logo />
        <Box ml="auto" />
        <Search />
        <Tooltip bottom left>
            Hello
        </Tooltip>
        <Notification />
    </HeaderContainer>
);

const App = () => (
    <Header />
);

ReactDOM.render(
    (
        <ThemeProvider customBreakpoints={[]}>
            <App />
        </ThemeProvider>
    ),
    document.getElementById("app")
);
