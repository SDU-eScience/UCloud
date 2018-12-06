import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject"
import { connect } from "react-redux";
import Link from "ui-components/Link";
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
import Notification from "Notifications";
import styled from "styled-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import { searchFiles } from "Search/Redux/SearchActions";
import { searchPage } from "Utilities/SearchUtilities";

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

    context: { router: { history: History } }

    static contextTypes = {
        router: PropTypes.object
    }

    public render() {
        const { history } = this.context.router;
        const { searchText } = this.state;
        const { prioritizedSearch, searchFiles } = this.props;
        return (
            <HeaderContainer color="headerText" bg="headerBg">
                <Logo />
                <Box ml="auto" />
                <Search
                    onChange={searchText => this.setState(() => ({ searchText }))}
                    navigate={() => history.push(searchPage(prioritizedSearch, searchText))}
                    searchText={searchText}
                    searchFiles={searchFiles}
                />
                <Notification />
                <ClickableDropdown left={"-100%"} trigger={<Flex><UserAvatar /></Flex>}>
                    <Box style={{ backgroundColor: "unset" }}>Welcome, {Cloud.userInfo.firstNames}</Box>
                    <Divider />
                    <Box ml="-17px" mr="-17px" pl="15px">
                        <Link color="black" to={"/users/settings"}>
                            <Flex>
                                <Box pr="0.5em"><i className="fas fa-cogs" /></Box>
                                Settings
                            </Flex>
                        </Link>
                    </Box>
                    <Flex ml="-17px" mr="-17px" pl="15px" onClick={() => Cloud.logout()}>
                        <Box pr="0.7em">
                            <i className="fas fa-sign-out-alt" />
                        </Box>
                        Logout
                    </Flex>
                </ClickableDropdown>
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
    width: 300px;
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
        height: 36px;
        padding-right: 10px;
        padding-left: 30px;
    }
`;

const Search = ({ searchText, onChange, navigate, searchFiles }) => (
    <Relative>
        <SearchInput>
            <Input
                pl="30px"
                id="search_input"
                value={searchText}
                type="text"
                noBorder
                onChange={e => onChange(e.target.value)}
                onKeyDown={e => { if (e.keyCode === KeyCode.ENTER && !!searchText) { searchFiles(searchText); navigate(); } }}
                placeholder=""
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

const OldUserAvatar = () => (
    <ClippedBox mr="8px" width="60px">
        <Avatar
            style={{ width: "64px", height: "60px", cursor: "pointer" }}
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

const UserAvatar = () => (
    <ClippedBox mr="8px" width="60px">
        <Avatar
            avatarStyle='Circle'
            topType='ShortHairShortFlat'
            accessoriesType='Blank'
            hairColor='Black'
            facialHairType='Blank'
            clotheType='BlazerShirt'
            eyeType='Default'
            eyebrowType='Default'
            mouthType='Default'
            skinColor='Light'
        />
    </ClippedBox>
);

interface HeaderOperations {
    setSidebarOpen: (open: boolean) => void
    fetchLoginStatus: () => void
    searchFiles: (fileName: string) => void
}

const mapDispatchToProps = (dispatch: Dispatch): HeaderOperations => ({
    setSidebarOpen: (open) => dispatch(setSidebarState(open)),
    fetchLoginStatus: async () => dispatch(await fetchLoginStatus()),
    searchFiles: async (fileName) => dispatch(await searchFiles({ fileName, fileTypes: ["FILE", "DIRECTORY"] }))
});

const mapStateToProps = ({ sidebar, header }: ReduxObject): HeaderStateToProps => ({
    sidebarOpen: sidebar.open,
    prioritizedSearch: header.prioritizedSearch
});

export default connect(mapStateToProps, mapDispatchToProps)(Header);
