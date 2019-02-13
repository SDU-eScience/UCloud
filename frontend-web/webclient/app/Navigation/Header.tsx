import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject"
import { connect } from "react-redux";
import Link from "ui-components/Link";
import { Dispatch } from "redux";
import Avatar from "avataaars";
import { History } from "history";
import { HeaderStateToProps } from "Navigation";
import { fetchLoginStatus } from "Zenodo/Redux/ZenodoActions";
import { ReduxObject, KeyCode, HeaderSearchType } from "DefaultObjects";
import { Flex, Box, Text, Icon, Relative, Absolute, Input, Label, Divider, Support } from "ui-components";
import Notification from "Notifications";
import styled from "styled-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import { searchFiles } from "Search/Redux/SearchActions";
import { searchPage } from "Utilities/SearchUtilities";
import BackgroundTask from "BackgroundTasks/BackgroundTask";
import { withRouter } from "react-router";
import DetailedFileSearch from "Files/DetailedFileSearch";
import { Dropdown } from "ui-components/Dropdown";
import DetailedApplicationSearch from "Applications/DetailedApplicationSearch";
import DetailedProjectSearch from "Project/DetailedProjectSearch"
import { prettierString } from "UtilityFunctions";
import { AvatarType } from "UserSettings/Avataaar";
import { findAvatar } from "UserSettings/Redux/AvataaarActions";
import { setPrioritizedSearch } from "./Redux/HeaderActions";
import { EllipsedText } from "ui-components/Text";
import { SearchOptions, SelectableText } from "Search/Search";

interface HeaderState {
    searchText: string
}

class Header extends React.Component<HeaderStateToProps & HeaderOperations & { history: History }, HeaderState> {
    constructor(props: any) {
        super(props);
        this.state = {
            searchText: ""
        };
        props.fetchLoginStatus();
        props.fetchAvatar();
    }

    public render() {
        const { searchText } = this.state;
        const { prioritizedSearch, history, refresh, spin } = this.props;
        return (
            <HeaderContainer color="headerText" bg={"headerBg"}>
                <Logo />
                <Box ml="auto" />
                <Search
                    searchType={this.props.prioritizedSearch}
                    onChange={searchText => this.setState(() => ({ searchText }))}
                    navigate={() => history.push(searchPage(prioritizedSearch, searchText))}
                    searchText={searchText}
                    setSearchType={st => this.props.setSearchType(st)}
                />
                <Box mr="auto" />
                <BackgroundTask />
                <Refresh spin={spin} onClick={refresh} />
                <Support />
                <Notification />
                <ClickableDropdown width="200px" left={"-180%"} trigger={<Flex><UserAvatar avatar={this.props.avatar} /></Flex>}>
                    <WhiteBackgroundEllipsedText width={"170px"}>Welcome, {Cloud.userInfo.firstNames}</WhiteBackgroundEllipsedText>
                    <Divider />
                    <Box ml="-17px" mr="-17px" pl="15px">
                        <Link color="black" to={"/users/settings"}>
                            <Flex>
                                <Icon name="properties" mr="0.5em" my="0.2em" size="1.3em" />
                                Settings
                            </Flex>
                        </Link>
                    </Box>
                    <Flex ml="-17px" mr="-17px" pl="15px">
                        <Link to={"/users/avatar"}>
                            <Icon name="edit" mr="0.5em" my="0.2em" size="1.3em" />
                            Edit Avatar
                        </Link>
                    </Flex>
                    <Flex ml="-17px" mr="-17px" pl="15px" onClick={() => Cloud.logout()}>
                        <Icon name="logout" mr="0.5em" my="0.2em" size="1.3em" />
                        Logout
                    </Flex>
                </ClickableDropdown>
            </HeaderContainer>
        )
    }
}

export const Refresh = ({ onClick, spin }: { onClick?: () => void, spin: boolean }) => !!onClick ?
    <RefreshIcon name="refresh" spin={spin} onClick={() => onClick()} /> : null

const RefreshIcon = styled(Icon)`
    cursor: pointer;
`;

const WhiteBackgroundEllipsedText = styled(EllipsedText)`
    &&&&& {
        background-color: white
        cursor: default;
    }
`;

const HeaderContainer = styled(Flex)`
    height: 48px;
    align-items: center;
    position: fixed;
    top: 0;
    width: 100%;
    z-index: 100;
`;

const Logo = () => (
    <Link to={"/"}>
        <Text color="headerText" fontSize={4} ml="24px">
            SDUCloud
        </Text>
    </Link>
);


const SearchInput = styled(Flex)`
    width: 350px;
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


interface Search {
    searchText: string
    searchType: HeaderSearchType
    onChange: (input: string) => void
    navigate: () => void
    setSearchType: (st: HeaderSearchType) => void
}
const Search = ({ searchText, onChange, navigate, searchType, setSearchType }: Search) => (
    <Relative>
        <SearchInput>
            <Input
                pl="30px"
                id="search_input"
                value={searchText}
                type="text"
                noBorder
                onChange={e => onChange(e.target.value)}
                onKeyDown={e => { if (e.keyCode === KeyCode.ENTER && !!searchText) navigate(); }}
            />
            <Absolute left="6px" top="7px">
                <Label htmlFor="search_input">
                    <Icon name="search" size="20" />
                </Label>
            </Absolute>
            <ClickableDropdown
                overflow={"visible"}
                left={-350}
                top={15}
                width="350px"
                colorOnHover={false}
                keepOpenOnClick
                squareTop
                trigger={
                    <Absolute top={-12.5} right={12} bottom={0} left={-28}>
                        <Icon name="chevronDown" size="15px" />
                    </Absolute>
                }>
                <SearchOptions>
                    <Box ml="auto" />
                    {searchTypes.map(it =>
                        <SelectableText key={it} onClick={() => setSearchType(it)} mr="1em" selected={it === searchType}>
                            {prettierString(it)}
                        </SelectableText>
                    )}
                    <Box mr="auto" />
                </SearchOptions>
                {searchType === "files" ? <DetailedFileSearch defaultFilename={searchText} cantHide /> :
                    searchType === "applications" ? <DetailedApplicationSearch defaultAppName={searchText} /> :
                        searchType === "projects" ? <DetailedProjectSearch defaultProjectName={searchText} /> : null}
            </ClickableDropdown>
        </SearchInput>
    </Relative >
);

const searchTypes: HeaderSearchType[] = ["files", "projects", "applications"]

const ClippedBox = styled(Flex)`
    align-items: center;
    overflow: hidden;
    height: 48px;
`;

interface UserAvatar { avatar: AvatarType }
export const UserAvatar = ({ avatar }: UserAvatar) => (
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
    </ClippedBox>
);

interface HeaderOperations {
    fetchLoginStatus: () => void
    fetchAvatar: () => void
    setSearchType: (st: HeaderSearchType) => void
}

const mapDispatchToProps = (dispatch: Dispatch): HeaderOperations => ({
    fetchLoginStatus: async () => dispatch(await fetchLoginStatus()),
    fetchAvatar: async () => dispatch(await findAvatar()),
    setSearchType: st => dispatch(setPrioritizedSearch(st))
});

const mapStateToProps = ({ header, avatar, ...rest }: ReduxObject): HeaderStateToProps => ({
    ...header,
    avatar,
    spin: loading(rest)
});

const loading = (rO): boolean =>
    rO.files.loading || rO.fileInfo.loading || rO.notifications.loading || rO.sidebar.loading || rO.simpleSearch.filesLoading
    || rO.simpleSearch.applicationsLoading || rO.simpleSearch.projectsLoading || rO.zenodo.loading || rO.activity.loading
    || rO.analyses.loading || rO.dashboard.recentLoading || rO.dashboard.analysesLoading || rO.dashboard.favoriteLoading
    || rO.applicationsFavorite.applications.loading || rO.applicationsBrowse.applications.loading

export default connect<HeaderStateToProps, HeaderOperations>(mapStateToProps, mapDispatchToProps)(withRouter(Header));
