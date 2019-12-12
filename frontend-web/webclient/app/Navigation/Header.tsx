import {
    AdvancedSearchRequest as AppSearchRequest,
    DetailedApplicationSearchReduxState,
    FullAppInfo
} from "Applications";
import DetailedApplicationSearch from "Applications/DetailedApplicationSearch";
import {setAppQuery} from "Applications/Redux/DetailedApplicationSearchActions";
import {Client} from "Authentication/HttpClientInstance";
import {UserAvatar} from "AvataaarLib/UserAvatar";
import BackgroundTask from "BackgroundTasks/BackgroundTask";
import {HeaderSearchType, KeyCode, ReduxObject} from "DefaultObjects";
import {AdvancedSearchRequest, DetailedFileSearchReduxState, File} from "Files";
import DetailedFileSearch from "Files/DetailedFileSearch";
import {setFilename} from "Files/Redux/DetailedFileSearchActions";
import {HeaderStateToProps} from "Navigation";
import {setPrioritizedSearch} from "Navigation/Redux/HeaderActions";
import Notification from "Notifications";
import {usePromiseKeeper} from "PromiseKeeper";
import * as React from "react";
import {connect} from "react-redux";
import {useHistory, useLocation} from "react-router";
import {Dispatch} from "redux";
import {searchApplications, searchFiles, setApplicationsLoading, setFilesLoading} from "Search/Redux/SearchActions";
import {applicationSearchBody, fileSearchBody} from "Search/Search";
import styled from "styled-components";
import {Page} from "Types";
import {
    Absolute,
    Box,
    Divider,
    ExternalLink,
    Flex,
    Hide,
    Icon,
    Input,
    Label,
    Relative,
    SelectableText,
    SelectableTextWrapper,
    Support,
    Text,
    theme,
    Tooltip
} from "ui-components";
import {DevelopmentBadgeBase} from "ui-components/Badge";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {Dropdown} from "ui-components/Dropdown";
import Link from "ui-components/Link";
import {TextSpan} from "ui-components/Text";
import {ThemeToggler} from "ui-components/ThemeToggle";
import {findAvatar} from "UserSettings/Redux/AvataaarActions";
import {searchPage} from "Utilities/SearchUtilities";
import {getQueryParamOrElse} from "Utilities/URIUtilities";
import {
    displayErrorMessageOrDefault,
    inDevEnvironment,
    isLightThemeStored,
    prettierString,
    stopPropagationAndPreventDefault
} from "UtilityFunctions";
import {DEV_SITE, PRODUCT_NAME, STATUS_PAGE, VERSION_TEXT} from "../../site.config.json";

interface HeaderProps extends HeaderStateToProps, HeaderOperations {
    toggleTheme(): void;
}

const DevelopmentBadge = () => window.location.host === DEV_SITE || inDevEnvironment() ?
    <DevelopmentBadgeBase>{window.location.host}</DevelopmentBadgeBase> : null;

function Header(props: HeaderProps) {
    const [upcomingDowntime, setUpcomingDowntime] = React.useState(false);
    const [intervalId, setIntervalId] = React.useState(-1);
    const history = useHistory();
    const promises = usePromiseKeeper();

    React.useEffect(() => {
        if (Client.isLoggedIn) props.fetchAvatar();
        setIntervalId(setInterval(fetchDowntimes, 600_000));
        fetchDowntimes();
        return () => clearInterval(intervalId);
    }, []);

    // TODO If more hacks like this is needed then implement a general process for hiding header/sidebar.
    // The following is only supposed to work for the initial load.
    if (window.location.pathname === "/app/login" && window.location.search === "?dav=true") return null;

    if (!Client.isLoggedIn) return null;

    function toSearch() {
        history.push("/search/files");
    }

    const {refresh, spin} = props;

    return (
        <HeaderContainer color="headerText" bg="headerBg">
            <Logo />
            <Box ml="auto" />
            <Hide xs sm md>
                <Search />
            </Hide>
            <Hide lg xxl xl>
                <Icon
                    name="search"
                    size="32"
                    mr="3px"
                    cursor="pointer"
                    onClick={toSearch}
                />
            </Hide>
            <Box mr="auto" />
            {upcomingDowntime ? (
                <ExternalLink href={STATUS_PAGE}>
                    <Tooltip
                        right="0"
                        bottom="1"
                        tooltipContentWidth="115px"
                        wrapperOffsetLeft="10px"
                        trigger={<Icon color="yellow" name="warning" />}
                    >
                        Upcoming downtime.<br />
                        Click to view
                    </Tooltip>
                </ExternalLink>
            ) : null}
            <DevelopmentBadge />
            <BackgroundTask />
            <Flex width="48px" justifyContent="center">
                <Refresh spin={spin} onClick={refresh} headerLoading={props.statusLoading} />
            </Flex>
            <Support />
            <Notification />
            <ClickableDropdown
                colorOnHover={false}
                width="200px"
                left="-180%"
                trigger={<Flex>{Client.isLoggedIn ? <UserAvatar avatar={props.avatar} mx={"8px"} /> : null}</Flex>}
            >
                {!STATUS_PAGE ? null : (
                    <>
                        <Box ml="-17px" mr="-17px" pl="15px">
                            <ExternalLink color="black" href={STATUS_PAGE}>
                                <Flex color="black">
                                    <Icon name="favIcon" mr="0.5em" my="0.2em" size="1.3em" />
                                    <TextSpan>Site status</TextSpan>
                                </Flex>
                            </ExternalLink>
                        </Box>
                        <Divider />
                    </>
                )}
                <Box ml="-17px" mr="-17px" pl="15px">
                    <Link color="black" to="/users/settings">
                        <Flex color="black">
                            <Icon name="properties" mr="0.5em" my="0.2em" size="1.3em" />
                            <TextSpan>Settings</TextSpan>
                        </Flex>
                    </Link>
                </Box>
                <Flex ml="-17px" mr="-17px" pl="15px">
                    <Link to={"/users/avatar"}>
                        <Flex color="black">
                            <Icon name="edit" mr="0.5em" my="0.2em" size="1.3em" />
                            <TextSpan>Edit Avatar</TextSpan>
                        </Flex>
                    </Link>
                </Flex>
                <Flex ml="-17px" mr="-17px" pl="15px" onClick={() => Client.logout()}>
                    <Icon name="logout" mr="0.5em" my="0.2em" size="1.3em" />
                    Logout
                </Flex>
                <Divider />
                <Flex cursor="auto">
                    <ThemeToggler
                        isLightTheme={isLightThemeStored()}
                        onClick={onToggleTheme}
                    />
                </Flex>
            </ClickableDropdown>
        </HeaderContainer>
    );

    function onToggleTheme(e: React.SyntheticEvent<HTMLDivElement, Event>) {
        stopPropagationAndPreventDefault(e);
        props.toggleTheme();
    }

    async function fetchDowntimes() {
        try {
            const result = await promises.makeCancelable(Client.get("/downtime/listUpcoming")).promise;
            setUpcomingDowntime(result.response.items.length > 0);
        } catch (err) {
            displayErrorMessageOrDefault(err, "Could not fetch upcoming downtimes.");
        }
    }
}

export const Refresh = ({
    onClick,
    spin,
    headerLoading
}: {onClick?: () => void, spin: boolean, headerLoading?: boolean}) => !!onClick || headerLoading ? (
    <RefreshIcon
        data-tag="refreshButton"
        name="refresh"
        spin={spin || headerLoading}
        onClick={onClick}
    />
) : <Box width="24px" />;

const RefreshIcon = styled(Icon)`
    cursor: pointer;
`;

const HeaderContainer = styled(Flex)`
    height: 48px;
    align-items: center;
    position: fixed;
    top: 0;
    width: 100%;
    z-index: 100;
    box-shadow: ${theme.shadows.sm};
`;

const LogoText = styled(Text)`
    vertical-align: top;
    font-weight: 700;
`;

const Logo = () => (
    <Link to="/">
        <Flex alignItems="center" ml="15px">
            <Icon name="logoEsc" size="38px" />
            <Text color="headerText" fontSize={4} ml="8px">{PRODUCT_NAME}</Text>
            {!VERSION_TEXT ? null : (
                <LogoText
                    ml="4px"
                    mt={-7}
                    color="red"
                    fontSize={17}
                >
                    {VERSION_TEXT}
                </LogoText>
            )}
        </Flex>
    </Link>
);

const Login = () => (
    <Icon name="user" />
);

const SearchInput = styled(Flex)`
    min-width: 250px;
    width: 425px;
    max-width: 425px;
    height: 36px;
    align-items: center;
    color: white;
    background-color: rgba(236, 239, 244, 0.25);
    border-radius: 5px;
    input::-webkit-input-placeholder, input::-moz-placeholder, input::-ms-input-placeholder, input:-moz-placeholder {
        color: white;
    }
    input:focus::-webkit-input-placeholder, input:focus::-moz-placeholder, input:focus::-ms-input-placeholder, input:focus::-moz-placeholder {
        color: black;
    }
    input:focus ~ div > span > div > svg, input:focus + div > label > svg {
        color: black;
    }
    input ~ div > span > div > svg, input + div > label > svg {
        color: white;
    }
    & > input:focus {
        color: black;
        background-color: white;
    }
    & > ${Dropdown} > ${Text} > input {
        width: 350px;
        height: 36px;
        padding-right: 10px;
        padding-left: 30px;
    }
`;


interface SearchStateProps {
    prioritizedSearch: HeaderSearchType;
    fileSearch: DetailedFileSearchReduxState;
    appSearch: DetailedApplicationSearchReduxState;
    files: Page<File>;
    applications: Page<FullAppInfo>;
}

interface SearchOperations {
    setSearchType: (st: HeaderSearchType) => void;
    searchFiles: (body: AdvancedSearchRequest) => void;
    searchApplications: (body: AppSearchRequest) => void;
}

type SearchProps = SearchOperations & SearchStateProps;

// tslint:disable-next-line: variable-name
const _Search = (props: SearchProps) => {
    const history = useHistory();
    const location = useLocation();
    const [search, setSearch] = React.useState(getQueryParamOrElse({history, location}, "query", ""));
    const {prioritizedSearch, setSearchType} = props;
    const allowedSearchTypes: HeaderSearchType[] = ["files", "applications"];
    return (
        <Relative>
            <SearchInput>
                <Input
                    pl="30px"
                    pt="6px"
                    pb="6px"
                    id="search_input"
                    type="text"
                    value={search}
                    noBorder
                    onKeyDown={e => {
                        if (e.keyCode === KeyCode.ENTER && search) fetchAll();
                    }}
                    onChange={e => setSearch(e.target.value)}
                />
                <Absolute left="6px" top="7px">
                    <Label htmlFor="search_input">
                        <Icon name="search" size="20" />
                    </Label>
                </Absolute>
                <ClickableDropdown
                    keepOpenOnOutsideClick
                    overflow="visible"
                    left={-425}
                    top={15}
                    width="425px"
                    colorOnHover={false}
                    keepOpenOnClick
                    squareTop
                    trigger={(
                        <Absolute top={-12.5} right={12} bottom={0} left={-28}>
                            <Icon cursor="pointer" name="chevronDown" size="15px" />
                        </Absolute>
                    )}
                >
                    <SelectableTextWrapper>
                        <Box ml="auto" />
                        {allowedSearchTypes.map(it => (
                            <SelectableText
                                key={it}
                                onClick={() => setSearchType(it)}
                                mr="1em"
                                selected={it === prioritizedSearch}
                            >
                                {prettierString(it)}
                            </SelectableText>
                        ))}
                        <Box mr="auto" />
                    </SelectableTextWrapper>
                    {prioritizedSearch === "files" ? (
                        <DetailedFileSearch
                            onSearch={() => fetchAll()}
                            cantHide
                        />
                    ) : prioritizedSearch === "applications" ? (
                        <DetailedApplicationSearch
                            onSearch={() => fetchAll()}
                            defaultAppQuery={search}
                        />
                    ) : null}
                </ClickableDropdown>
                {!Client.isLoggedIn ? <Login /> : null}
            </SearchInput>
        </Relative>
    );

    function fetchAll(itemsPerPage?: number) {
        props.searchFiles({
            ...fileSearchBody(
                props.fileSearch,
                itemsPerPage ?? props.files.itemsPerPage,
                props.files.pageNumber
            ), fileName: search
        });
        props.searchApplications({
            ...applicationSearchBody(
                props.appSearch,
                itemsPerPage ?? props.applications.itemsPerPage,
                props.applications.pageNumber
            ), query: search
        });
        history.push(searchPage(prioritizedSearch, search));
    }
};

const mapSearchStateToProps = ({
    header,
    detailedFileSearch,
    detailedApplicationSearch,
    simpleSearch
}: ReduxObject): SearchStateProps => ({
    prioritizedSearch: header.prioritizedSearch,
    fileSearch: detailedFileSearch,
    appSearch: detailedApplicationSearch,
    files: simpleSearch.files,
    applications: simpleSearch.applications
});

const mapSearchDispatchToProps = (dispatch: Dispatch): SearchOperations => ({
    setSearchType: (st: HeaderSearchType) => dispatch(setPrioritizedSearch(st)),
    searchFiles: async (body: AdvancedSearchRequest) => {
        dispatch(setFilesLoading(true));
        dispatch(await searchFiles(body));
        dispatch(setFilename(body.fileName || ""));
    },
    searchApplications: async (body: AppSearchRequest) => {
        dispatch(setApplicationsLoading(true));
        dispatch(await searchApplications(body));
        dispatch(setAppQuery(body.query || ""));
    },
});

const Search = connect(mapSearchStateToProps, mapSearchDispatchToProps)(_Search);

interface HeaderOperations {
    fetchAvatar: () => void;
}

const mapDispatchToProps = (dispatch: Dispatch): HeaderOperations => ({
    fetchAvatar: async () => {
        const action = await findAvatar();
        if (action !== null) dispatch(action);
    }
});

const mapStateToProps = ({header, avatar, ...rest}: ReduxObject): HeaderStateToProps => ({
    ...header,
    avatar,
    spin: isAnyLoading(rest as ReduxObject),
    statusLoading: rest.status.loading
});

const isAnyLoading = (rO: ReduxObject): boolean =>
    rO.loading === true || rO.fileInfo.loading || rO.notifications.loading || rO.simpleSearch.filesLoading
    || rO.simpleSearch.applicationsLoading || rO.activity.loading
    || rO.analyses.loading || rO.dashboard.recentLoading || rO.dashboard.analysesLoading || rO.dashboard.favoriteLoading
    || rO.applicationsFavorite.applications.loading || rO.applicationsBrowse.loading
    || rO.applicationsFavorite.applications.loading || /* rO.applicationsBrowse.applicationsPage.loading */ false
    || rO.accounting.resources["compute/timeUsed"].events.loading
    || rO.accounting.resources["storage/bytesUsed"].events.loading;

export default connect<HeaderStateToProps, HeaderOperations>(mapStateToProps, mapDispatchToProps)(Header);
