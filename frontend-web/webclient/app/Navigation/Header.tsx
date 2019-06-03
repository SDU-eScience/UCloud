import * as React from "react";
import {Cloud} from "Authentication/SDUCloudObject"
import {connect} from "react-redux";
import Link from "ui-components/Link";
import {Dispatch} from "redux";
import Avatar from "avataaars";
import {History} from "history";
import {HeaderStateToProps} from "Navigation";
import {fetchLoginStatus} from "Zenodo/Redux/ZenodoActions";
import {ReduxObject, KeyCode, HeaderSearchType} from "DefaultObjects";
import {Flex, Box, Text, Icon, Relative, Absolute, Input, Label, Support, Hide} from "ui-components";
import Notification from "Notifications";
import styled from "styled-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {searchPage} from "Utilities/SearchUtilities";
import BackgroundTask from "BackgroundTasks/BackgroundTask";
import {withRouter} from "react-router";
import DetailedFileSearch from "Files/DetailedFileSearch";
import {Dropdown} from "ui-components/Dropdown";
import DetailedApplicationSearch from "Applications/DetailedApplicationSearch";
import DetailedProjectSearch from "Project/DetailedProjectSearch"
import {inDevEnvironment, prettierString} from "UtilityFunctions";
import {DevelopmentBadgeBase} from "ui-components/Badge";
import {EllipsedText, TextSpan} from "ui-components/Text";
import {SearchOptions, SelectableText} from "Search/Search";
import {AvatarType} from "UserSettings/Avataaar";
import {SnackType} from "Snackbar/Snackbars";
import {findAvatar} from "UserSettings/Redux/AvataaarActions";
import {setPrioritizedSearch} from "Navigation/Redux/HeaderActions";
import {SpaceProps} from "styled-system";
import {snackbarStore} from "Snackbar/SnackbarStore";

interface HeaderProps extends HeaderStateToProps, HeaderOperations {
    history: History
}

const DevelopmentBadge = () => window.location.host === "dev.cloud.sdu.dk" || inDevEnvironment() ?
    <DevelopmentBadgeBase>DEVELOPMENT</DevelopmentBadgeBase> : null;

// NOTE: Ideal for hooks, if useRouter ever happens
class Header extends React.Component<HeaderProps> {

    private searchRef = React.createRef<HTMLInputElement>();

    constructor(props) {
        super(props);
        props.fetchLoginStatus();
        props.fetchAvatar();
    }

    public render() {
        const {prioritizedSearch, history, refresh, spin} = this.props;
        if (!Cloud.isLoggedIn) return null;
        return (
            <HeaderContainer color="headerText" bg="headerBg">
                <Logo/>
                {/* <ContextSwitcher /> */}
                <Box ml="auto"/>
                <Hide xs sm md>
                    <Search
                        searchType={this.props.prioritizedSearch}
                        navigate={() => history.push(searchPage(prioritizedSearch, this.searchRef.current && this.searchRef.current.value || ""))}
                        searchRef={this.searchRef}
                        setSearchType={st => this.props.setSearchType(st)}
                    />
                </Hide>
                <Hide lg xxl xl>
                    <Icon name="search" size="32" mr="3px" cursor="pointer"
                          onClick={() => this.props.history.push("/search/files")}/>
                </Hide>
                <Box mr="auto"/>
                <DevelopmentBadge/>
                <BackgroundTask/>
                <Flex width="48px" justifyContent="center">
                    <Refresh spin={spin} onClick={refresh} headerLoading={this.props.statusLoading}/>
                </Flex>
                <Support/>
                <Notification/>
                <ClickableDropdown width="200px" left="-180%" trigger={<Flex>{Cloud.isLoggedIn ?
                    <UserAvatar avatar={this.props.avatar} mx={"8px"}/> : null}</Flex>}>
                    <Box ml="-17px" mr="-17px" pl="15px">
                        <Link color="black" to="/users/settings">
                            <Flex color="black">
                                <Icon name="properties" mr="0.5em" my="0.2em" size="1.3em"/>
                                <TextSpan>Settings</TextSpan>
                            </Flex>
                        </Link>
                    </Box>
                    <Flex ml="-17px" mr="-17px" pl="15px">
                        <Link to={"/users/avatar"}>
                            <Flex color="black">
                                <Icon name="edit" mr="0.5em" my="0.2em" size="1.3em"/>
                                <TextSpan>Edit Avatar</TextSpan>
                            </Flex>
                        </Link>
                    </Flex>
                    <Flex ml="-17px" mr="-17px" pl="15px" onClick={() => Cloud.logout()}>
                        <Icon name="logout" mr="0.5em" my="0.2em" size="1.3em"/>
                        Logout
                    </Flex>
                </ClickableDropdown>
            </HeaderContainer>
        )
    }
}

export const Refresh = ({onClick, spin, headerLoading}: { onClick?: () => void, spin: boolean, headerLoading?: boolean }) => !!onClick || headerLoading ?
    <RefreshIcon data-tag="refreshButton" name="refresh" spin={spin || headerLoading}
                 onClick={() => !!onClick ? onClick() : undefined}/> : <Box width="24px"/>

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
    // background-color: #1a73e8
    // background: linear-gradient(to right, hsla(215, 100%, 50%, 1), hsla(220, 80%, 50%, 1));
    // background: linear-gradient(to right, hsla(215, 100%, 50%, 1), hsla(215, 90%, 50%, 1));
    box-shadow: ${({ theme }) => theme.shadows["sm"]};
`;

const Logo = () => (
    <Link to={"/"}>
        <Flex alignItems={"center"} ml="15px">
            <Icon name={"logoEsc"} size={"38px"} />
            <Text color="headerText" fontSize={4} ml={"8px"}>SDUCloud</Text>
        </Flex>
    </Link>
);

const Login = () => (
    <Icon name="user"/>
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

    input:focus {
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


interface SearchProps {
    searchRef: React.RefObject<HTMLInputElement>
    searchType: HeaderSearchType
    navigate: () => void
    setSearchType: (st: HeaderSearchType) => void
}

const Search = ({searchRef, navigate, searchType, setSearchType}: SearchProps) => {
    const allowedSearchTypes: HeaderSearchType[] = ["files", "applications"];
    if (inDevEnvironment()) allowedSearchTypes.push("projects");
    return (<Relative>
            <SearchInput>
                <Input
                    pl="30px"
                    id="search_input"
                    type="text"
                    ref={searchRef}
                    noBorder
                    onKeyDown={e => {
                        if (e.keyCode === KeyCode.ENTER && !!(searchRef.current && searchRef.current.value)) navigate();
                    }}
                />
                <Absolute left="6px" top="7px">
                    <Label htmlFor="search_input">
                        <Icon name="search" size="20"/>
                    </Label>
                </Absolute>
                <ClickableDropdown
                    overflow={"visible"}
                    left={-425}
                    top={15}
                    width="425px"
                    colorOnHover={false}
                    keepOpenOnClick
                    squareTop
                    trigger={
                        <Absolute top={-12.5} right={12} bottom={0} left={-28}>
                            <Icon name="chevronDown" size="15px"/>
                        </Absolute>
                    }>
                    <SearchOptions>
                        <Box ml="auto"/>
                        {allowedSearchTypes.map(it =>
                            <SelectableText key={it} onClick={() => setSearchType(it)} mr="1em"
                                            selected={it === searchType}>
                                {prettierString(it)}
                            </SelectableText>
                        )}
                        <Box mr="auto"/>
                    </SearchOptions>
                    {searchType === "files" ?
                        <DetailedFileSearch defaultFilename={searchRef.current && searchRef.current.value} cantHide/> :
                        searchType === "applications" ?
                            <DetailedApplicationSearch defaultAppName={searchRef.current && searchRef.current.value}/> :
                            searchType === "projects" ? <DetailedProjectSearch
                                defaultProjectName={searchRef.current && searchRef.current.value}/> : null}
                </ClickableDropdown>
                {!Cloud.isLoggedIn ? <Login/> : null}
            </SearchInput>
        </Relative>
    )
};

const ClippedBox = styled(Flex)`
    align-items: center;
    overflow: hidden;
    height: 48px;
`;

interface UserAvatar extends SpaceProps {
    avatar: AvatarType
}

export const UserAvatar = ({avatar}: UserAvatar) => (
    <ClippedBox mx="8px" width="60px">
        <Avatar
            avatarStyle="Circle"
            topType={avatar.top}
            accessoriesType={avatar.topAccessory}
            hairColor={avatar.hairColor}
            facialHairType={avatar.facialHair}
            facialHairColor={avatar.facialHairColor}
            clotheType={avatar.clothes}
            clotheColor={avatar.colorFabric}
            graphicType={avatar.clothesGraphic}
            eyeType={avatar.eyes}
            eyebrowType={avatar.eyebrows}
            mouthType={avatar.mouthTypes}
            skinColor={avatar.skinColors}
        />
    </ClippedBox>);

const ContextSwitcherFlex = styled(Flex)`
    border-radius: 4px;
    border: 1px solid white;
`;

const ContextSwitcher = (props) => {
    if (!inDevEnvironment()) return null;
    const [userContext, setUserContext] = React.useState(Cloud.username);
    return (<Box ml="6px">
        <ClickableDropdown trigger={
            <ContextSwitcherFlex>
                <EllipsedText pl="8px" pr="6px" width="150px" title={userContext}>{userContext}</EllipsedText>
                <Box cursor="pointer" pr="8px"><Icon size={"10"} name={"chevronDown"}/></Box>
            </ContextSwitcherFlex>
        } width="174px">
            {[Cloud.username, "Project 1", "Project 2"].filter(it => it !== userContext).map(it => (
                <EllipsedText
                    key={it}
                    onClick={() => (snackbarStore.addSnack({message: "Not yet.", type: SnackType.Information}), setUserContext(it))}
                    width="150px"
                >{it}</EllipsedText>
            ))}
        </ClickableDropdown>
    </Box>);
};

interface HeaderOperations {
    fetchLoginStatus: () => void
    fetchAvatar: () => void
    setSearchType: (st: HeaderSearchType) => void
}

const mapDispatchToProps = (dispatch: Dispatch): HeaderOperations => ({
    fetchLoginStatus: async () => dispatch(await fetchLoginStatus()),
    fetchAvatar: async () => dispatch(await findAvatar()),
    setSearchType: st => dispatch(setPrioritizedSearch(st)),
});

const mapStateToProps = ({header, avatar, ...rest}: ReduxObject): HeaderStateToProps => ({
    ...header,
    avatar,
    spin: anyLoading(rest as ReduxObject),
    statusLoading: rest.status.loading
});

const anyLoading = (rO: ReduxObject): boolean =>
    rO.loading === true || rO.files.loading || rO.fileInfo.loading || rO.notifications.loading || rO.simpleSearch.filesLoading
    || rO.simpleSearch.applicationsLoading || rO.simpleSearch.projectsLoading || rO.zenodo.loading || rO.activity.loading
    || rO.analyses.loading || rO.dashboard.recentLoading || rO.dashboard.analysesLoading || rO.dashboard.favoriteLoading
    || rO.applicationsFavorite.applications.loading || rO.applicationsBrowse.applications.loading || rO.favorites.loading
    || rO.accounting.resources["compute/timeUsed"].events.loading
    || rO.accounting.resources["storage/bytesUsed"].events.loading;

export default connect<HeaderStateToProps, HeaderOperations>(mapStateToProps, mapDispatchToProps)(withRouter(Header));
