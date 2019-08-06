import * as React from "react";
import {Cloud} from "Authentication/SDUCloudObject"
import {connect} from "react-redux";
import Link from "ui-components/Link";
import {Dispatch} from "redux";
import Avatar from "AvataaarLib";
import {History} from "history";
import {HeaderStateToProps} from "Navigation";
import {ReduxObject, KeyCode, HeaderSearchType} from "DefaultObjects";
import {Flex, Box, Text, Icon, Relative, Absolute, Input, Label, Support, Hide, Divider, SelectableText, SelectableTextWrapper} from "ui-components";
import Notification from "Notifications";
import styled from "styled-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {searchPage} from "Utilities/SearchUtilities";
import BackgroundTask from "BackgroundTasks/BackgroundTask";
import {withRouter, RouteComponentProps} from "react-router";
import DetailedFileSearch from "Files/DetailedFileSearch";
import {Dropdown} from "ui-components/Dropdown";
import DetailedApplicationSearch from "Applications/DetailedApplicationSearch";
import {inDevEnvironment, prettierString, isLightThemeStored} from "UtilityFunctions";
import {DevelopmentBadgeBase} from "ui-components/Badge";
import {TextSpan} from "ui-components/Text";
import {AvatarType} from "UserSettings/Avataaar";
import {findAvatar} from "UserSettings/Redux/AvataaarActions";
import {setPrioritizedSearch} from "Navigation/Redux/HeaderActions";
import {SpaceProps} from "styled-system";
import {ThemeToggler} from "ui-components/ThemeToggle";

interface HeaderProps extends HeaderStateToProps, HeaderOperations, RouteComponentProps {
    history: History
    toggleTheme: () => void
}

const DevelopmentBadge = () => window.location.host === "dev.cloud.sdu.dk" || inDevEnvironment() ?
    <DevelopmentBadgeBase>DEVELOPMENT</DevelopmentBadgeBase> : null;

// NOTE: Ideal for hooks, if useRouter ever happens
function Header(props: HeaderProps) {

    const searchRef = React.useRef<HTMLInputElement>(null);

    React.useEffect(() => {
        props.fetchAvatar();
    }, []);

    const {prioritizedSearch, history, refresh, spin} = props;
    if (!Cloud.isLoggedIn) return null;
    return (
        <HeaderContainer color="headerText" bg="headerBg">
            <Logo />
            <Box ml="auto" />
            <Hide xs sm md>
                <Search
                    searchType={props.prioritizedSearch}
                    navigate={() => history.push(searchPage(prioritizedSearch, searchRef.current && searchRef.current.value || ""))}
                    searchRef={searchRef}
                    setSearchType={st => props.setSearchType(st)}
                />
            </Hide>
            <Hide lg xxl xl>
                <Icon name="search" size="32" mr="3px" cursor="pointer"
                    onClick={() => props.history.push("/search/files")} />
            </Hide>
            <Box mr="auto" />
            <DevelopmentBadge />
            <BackgroundTask />
            <Flex width="48px" justifyContent="center">
                <Refresh spin={spin} onClick={refresh} headerLoading={props.statusLoading} />
            </Flex>
            <Support />
            <Notification />
            <ClickableDropdown colorOnHover={false} width="200px" left="-180%" trigger={<Flex>{Cloud.isLoggedIn ?
                <UserAvatar avatar={props.avatar} mx={"8px"} /> : null}</Flex>}>
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
                <Flex ml="-17px" mr="-17px" pl="15px" onClick={() => Cloud.logout()}>
                    <Icon name="logout" mr="0.5em" my="0.2em" size="1.3em" />
                    Logout
                </Flex>
                <Divider />
                <Flex onClick={e => (e.preventDefault(), e.stopPropagation(), props.toggleTheme())}>
                    <ThemeToggler isLightTheme={isLightThemeStored()} />
                </Flex>
            </ClickableDropdown>
        </HeaderContainer>
    )
}

export const Refresh = ({onClick, spin, headerLoading}: {onClick?: () => void, spin: boolean, headerLoading?: boolean}) => !!onClick || headerLoading ?
    <RefreshIcon data-tag="refreshButton" name="refresh" spin={spin || headerLoading}
        onClick={() => !!onClick ? onClick() : undefined} /> : <Box width="24px" />;

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
    box-shadow: ${({theme}) => theme.shadows["sm"]};
`;

const Logo = () => (
    <Link to={"/"}>
        <Flex alignItems={"center"} ml="15px">
            <Icon name={"logoEsc"} size={"38px"} />
            <Text color="headerText" fontSize={4} ml={"8px"}>SDUCloud</Text>
            <Text ml={"4px"} mt={-7} style={{verticalAlign: "top", fontWeight: 700}} color="red" fontSize={17}>BETA</Text>
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


interface SearchProps {
    searchRef: React.RefObject<HTMLInputElement>
    searchType: HeaderSearchType
    navigate: () => void
    setSearchType: (st: HeaderSearchType) => void
}

const Search = ({searchRef, navigate, searchType, setSearchType}: SearchProps) => {
    const allowedSearchTypes: HeaderSearchType[] = ["files", "applications"];
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
                    <Icon name="search" size="20" />
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
                        <Icon name="chevronDown" size="15px" />
                    </Absolute>
                }>
                <SelectableTextWrapper>
                    <Box ml="auto" />
                    {allowedSearchTypes.map(it =>
                        <SelectableText key={it} onClick={() => setSearchType(it)} mr="1em"
                            selected={it === searchType}>
                            {prettierString(it)}
                        </SelectableText>
                    )}
                    <Box mr="auto" />
                </SelectableTextWrapper>
                {searchType === "files" ?
                    <DetailedFileSearch defaultFilename={searchRef.current && searchRef.current.value} cantHide /> :

                    searchType === "applications" ?
                        <DetailedApplicationSearch
                            defaultAppName={searchRef.current && searchRef.current.value || undefined} /> :

                        null}
            </ClickableDropdown>
            {!Cloud.isLoggedIn ? <Login /> : null}
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

interface HeaderOperations {
    fetchAvatar: () => void
    setSearchType: (st: HeaderSearchType) => void
}

const mapDispatchToProps = (dispatch: Dispatch): HeaderOperations => ({
    fetchAvatar: async () => dispatch(await findAvatar()),
    setSearchType: st => dispatch(setPrioritizedSearch(st)),
});

const mapStateToProps = ({header, avatar, ...rest}: ReduxObject): HeaderStateToProps => ({
    ...header,
    avatar,
    spin: isAnyLoading(rest as ReduxObject),
    statusLoading: rest.status.loading
});

const isAnyLoading = (rO: ReduxObject): boolean =>
    rO.loading === true || rO.files.loading || rO.fileInfo.loading || rO.notifications.loading || rO.simpleSearch.filesLoading
    || rO.simpleSearch.applicationsLoading || rO.zenodo.loading || rO.activity.loading
    || rO.analyses.loading || rO.dashboard.recentLoading || rO.dashboard.analysesLoading || rO.dashboard.favoriteLoading
    || rO.applicationsFavorite.applications.loading || rO.applicationsBrowse.applications.loading || rO.favorites.loading
    || rO.accounting.resources["compute/timeUsed"].events.loading
    || rO.accounting.resources["storage/bytesUsed"].events.loading;

export default connect<HeaderStateToProps, HeaderOperations>(mapStateToProps, mapDispatchToProps)(withRouter(Header));
