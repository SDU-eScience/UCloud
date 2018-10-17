import * as React from "react";
import { Dropdown as SDropdown, Icon as SIcon, Popup, Feed, Divider as SDivider } from "semantic-ui-react";
import { Cloud } from "Authentication/SDUCloudObject"
import { connect } from "react-redux";
import { Link } from "react-router-dom";
import * as PropTypes from "prop-types";
import { Dispatch } from "redux";
import { setSidebarState } from "./Redux/SidebarActions";
import Avatar from "avataaars";
import { History } from "history";
import { HeaderStateToProps } from "Navigation";
import { fetchLoginStatus } from "Zenodo/Redux/ZenodoActions";
import { ReduxObject, KeyCode } from "DefaultObjects";
import {
    Flex,
    Box,
    Text,
    Icon,
    Relative,
    Absolute,
    Input,
    Label,
    Divider
} from "ui-components";
import { Dropdown, DropdownContent } from "ui-components/Dropdown";
import Notification from "Notifications";
import styled from "styled-components";
import { ArrowUp, HiddenArrowUp } from "ui-components/Arrow";

interface HeaderProps {
    sidebarOpen?: boolean
    history: History
    prioritizedSearch: string
}

interface HeaderState {
    searchText: string
}

class Header extends React.Component<HeaderProps & HeaderOperations, HeaderState> {
    constructor(props) {
        super(props);
        this.state = {
            searchText: ""
        };
        props.fetchLoginStatus()
    }

    static contextTypes = {
        router: PropTypes.object
    }

    public render() {
        const { history } = this.context.router;
        const { searchText } = this.state;
        const { prioritizedSearch } = this.props;
        return (
            <HeaderContainer color='lightGray' bg='blue'>
                <Logo onClick={() => history.push("/dashboard/")} />
                <Box ml="auto" />
                <Search
                    onChange={searchText => this.setState(() => ({ searchText }))}
                    navigate={() => history.push(`/simplesearch/${prioritizedSearch}/${searchText}`)}
                    searchText={searchText} />
                <Notification />
                <Popup style={{ marginRight: "10px" }}
                    trigger={
                        <Flex>
                            <UserAvatar />
                        </Flex>
                    }
                    content={
                        <Feed>
                            <p>Welcome, {Cloud.userInfo.firstNames}</p>
                            <Divider />
                            <p>
                                <Link style={{ color: "black" }} to={"/usersettings/settings"}>
                                    <SIcon name="settings" />
                                    Settings
                                </Link>
                            </p>
                            <p onClick={() => Cloud.logout()}>
                                <SIcon name="sign out" />
                                Logout
                            </p>
                        </Feed>
                    }
                    on="click"
                    position="bottom right"
                />
                {/* <Dropdown>
                    <span><UserAvatar /></span>
                    <HiddenArrowUp />
                    <DropdownContent left={-60}>
                        <p>Welcome, {Cloud.userInfo.firstNames}</p>
                        <Divider width={138} ml={"-16px"} mt={"-2px"} mb={"12px"} />
                        <p><Link style={{ color: "black" }} to={"/usersettings/settings"}>
                            <SIcon name="settings" />
                            Settings
                        </Link></p>
                        <p style={{ cursor: "pointer" }} onClick={() => Cloud.logout()}>
                            <SIcon name="sign out" />
                            Logout
                        </p>
                    </DropdownContent>
                </Dropdown> */}
            </HeaderContainer>
        )
    }
}

const HeaderContainer = styled(Flex)`
    height: 48px;
    align-items: center;
    position: fixed;
    top: 0;
    width: 100%;
`;

const Logo = ({ onClick }) => (
    <Text onClick={onClick} fontSize={4} ml="24px">
        SDUCloud
    </Text>
);

const SearchInput = styled(Flex)`
    width: 300px;
    height: 36px;
    align-items: center;
    color: white;
    background-color: rgba(236, 239, 244, 0.247);
    border-color: rgba(201, 201, 233, 1);

    input::-webkit-input-placeholder, input::-moz-placeholder, input::-ms-input-placeholder, input:-moz-placeholder {
        color: white;
    }
    
    input:focus::-webkit-input-placeholder, input:focus::-moz-placeholder, input:focus::-ms-input-placeholder, input:focus::-moz-placeholder {
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

const Search = ({ searchText, onChange, navigate }) => (
    <Relative>
        <SearchInput>
            <Input pl="30px"
                id="search_input"
                value={searchText}
                type="text"
                onChange={e => onChange(e.target.value)}
                onKeyDown={e => { if (e.keyCode === KeyCode.ENTER && !!searchText) navigate(); }}
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

const ClippedBox = styled(Flex)`
    align-items: center;
    overflow: hidden;
    height: 48px;
`;

const UserAvatar = () => (
    <ClippedBox mr="8px" width="60px">
        <Avatar
            style={{ width: "64px", height: "60px" }}
            avatarStyle="Circle"
            topType="LongHairCurly"
            accessoriesType="Sunglasses"
            hairColor="Brown"
            facialHairType="Blank"
            clotheType="CollarSweater"
            clotheColor="PastelRed"
            eyeType="Default"
            eyebrowType="Default"
            mouthType="Smile"
            skinColor="Light"
        />
    </ClippedBox>
);

interface HeaderOperations {
    setSidebarOpen: (open: boolean) => void
    fetchLoginStatus: () => void
}

const mapDispatchToProps = (dispatch: Dispatch): HeaderOperations => ({
    setSidebarOpen: (open) => dispatch(setSidebarState(open)),
    fetchLoginStatus: async () => dispatch(await fetchLoginStatus())
});

const mapStateToProps = ({ sidebar, header }: ReduxObject): HeaderStateToProps => ({
    sidebarOpen: sidebar.open,
    prioritizedSearch: header.prioritizedSearch
});

export default connect(mapStateToProps, mapDispatchToProps)(Header);
