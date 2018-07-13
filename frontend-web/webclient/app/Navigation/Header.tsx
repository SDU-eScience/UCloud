
import * as React from "react";
import { Input, Menu, Dropdown, Icon, Responsive, Header as HeaderTag, Form } from "semantic-ui-react";
import { Cloud } from "Authentication/SDUCloudObject"
import { connect } from "react-redux";
import { Link } from "react-router-dom";
import "./Header.scss";
import PropTypes from "prop-types";
import { Dispatch } from "redux";
import Notifications from "../Notifications";
import { setSidebarOpen } from "./Redux/SidebarActions";
import Avatar from "avataaars";
import { History } from "history";
import { infoNotification } from "../UtilityFunctions";

interface HeaderProps {
    open?: boolean
    dispatch: Dispatch
    history: History
}

interface HeaderState {
    searchText: string
}

class Header extends React.Component<HeaderProps, HeaderState> {
    constructor(props) {
        super(props);
        this.state = {
            searchText: ""
        };
    }

    static contextTypes = {
        router: PropTypes.object
    }

    updateSearchText = (searchText: string) => this.setState(() => ({ searchText }));

    public render() {
        const { open, dispatch } = this.props;
        const { history } = this.context.router;
        const sidebarIcon = open ? "triangle left" : "triangle right";
        const { searchText } = this.state;

        // TODO Just for testing
        const options = [
            { key: "projects", text: "Projects", value: "projects" },
            /* { key: 'files', text: 'Files', value: 'files' },
            { key: 'apps', text: 'Applications', value: 'apps' }, */
        ];

        return ( // fixed="top" (remove attached, borderless)
            <Menu className="menu-padding" inverted attached borderless size="tiny" >
                <Responsive maxWidth={999} as={Menu.Item} onClick={() => dispatch(setSidebarOpen())}>
                    <Icon.Group size="large">
                        <Icon name="sidebar" />
                        <Icon corner color="grey" size="huge" name={sidebarIcon} />
                    </Icon.Group>
                </Responsive>

                <Menu.Item>
                    <Link to={"/dashboard"}>
                        <HeaderTag><h3 className="logo">SDUCloud</h3></HeaderTag>
                    </Link>
                </Menu.Item>

                <Responsive as={Menu.Item} minWidth={1000}>
                    <Dropdown
                        icon='users'
                        floating
                        labeled
                        button
                        className='icon'
                        options={[
                            { text: "Data Stream Processing", value: "p1" },
                            { text: "Event generator for Beyond Standard Model (BSM) physics", value: "p2" },
                            { text: "Motif Discovery with Homer", value: "p3" },
                        ]}
                        onChange={() => infoNotification("Note: this feature has not been implemented yet")}
                        defaultValue="p1"
                    />
                </Responsive>

                <Menu.Menu position="right">
                    <Menu.Item>
                        <Responsive minWidth={1000}>
                            <Form
                                size="tiny"
                                onSubmit={(e) => {
                                    e.preventDefault();
                                    if (!!searchText) history.push(`/metadata/search/${searchText}`)
                                }}
                            >
                                <Input
                                    label={<Dropdown defaultValue="projects" options={options} basic />}
                                    value={searchText}
                                    onChange={(_, { value }) => this.updateSearchText(value)}
                                    className="header-search"
                                    fluid
                                    icon='search'
                                    placeholder='Search...'
                                />
                            </Form>
                        </Responsive>
                        <Responsive maxWidth={999} as={Link} to={"/metadata/search/"}>
                            <Icon name="search" />
                        </Responsive>
                    </Menu.Item>
                    <Menu.Item>
                        <Notifications />
                    </Menu.Item>
                    <Dropdown
                        item
                        icon={null}
                        trigger={
                            <Avatar
                                style={{ width: "32px", height: "32px" }}
                                avatarStyle="Circle"
                                topType="NoHair"
                                accessoriesType="Blank"
                                facialHairType="Blank"
                                clotheType="GraphicShirt"
                                clotheColor="Blue02"
                                graphicType="Bear"
                                eyeType="Default"
                                eyebrowType="Default"
                                mouthType="Smile"
                                skinColor="Light"
                            />
                        }
                    >
                        <Dropdown.Menu>
                            <Dropdown.Item disabled>
                                Welcome, {Cloud.userInfo.firstNames}
                            </Dropdown.Item>
                            <Dropdown.Item as={Link} to={"/usersettings/settings"}>
                                <Icon name="settings" />
                                Settings
                            </Dropdown.Item>
                            <Dropdown.Item text="Logout" icon="sign out" onClick={() => Cloud.logout()} />
                        </Dropdown.Menu>
                    </Dropdown>
                </Menu.Menu>
            </Menu>
        );
    }
}

interface StateToProps {
    sidebar: {
        open: boolean
    }
}
const mapStateToProps = ({ sidebar }: StateToProps) => ({ open: sidebar.open });
export default connect(mapStateToProps)(Header);
