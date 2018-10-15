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
    Tooltip
} from 'ui-components';

import * as PropTypes from 'prop-types'

import styled from 'styled-components';

const HeaderContainer = styled(Flex)`
    background-color: ${props => props.theme.colors["lightBlue"]}
    height: 48px;
    align-items: center;
`;


const Logo = () => (
    <>
        <Icon name="hotel" mr={2} />

        <Text
            bold
            mx={2}
        >
            SDUCloud
        </Text>
    </>
);

const Notification = () => (
    <>
        <Relative top="0" left="0">
            <Flex justify="center" width="60px">
                <Icon name="notification" />
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

    input:focus ~ div {
        color: black;    
    }

    input:focus {
        color: black;
        background-color: white; 
    }

    input {
        border-radius: 5px;
        background-color: rgba(1, 1, 1, 0.1);
        padding: 10px;
        padding-left: 26px;
    }
`;

const Search = () => (
    <SearchInput>
        <Input placeholder="Do search" />
        <Absolute>
            <Icon name="flame" />
        </Absolute>
    </SearchInput>
);

const Header = () => (
    <HeaderContainer>
        <Logo />
        <Box ml="auto" />
        <Search />

        <Tooltip top left>
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
        <ThemeProvider legacy={false} customBreakpoints={false} theme>
            <App />
        </ThemeProvider>
    ),
    document.getElementById("app")
);
