import * as React from "react";
import { Input, Menu, Dropdown, Icon, Responsive, Header as H1, Form } from "semantic-ui-react";
import { Cloud } from "../../../authentication/SDUCloudObject"
import { connect } from "react-redux";
import { Link, withRouter } from "react-router-dom";
import "./Header.scss";
import { Dispatch } from "redux";
import Notifications from "../Notifications/index";
import { setSidebarOpen } from "../../Actions/Sidebar";
import { History } from "history";

interface HeaderProps {
    open: boolean
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

    updateSearchText = (searchText) => this.setState(() => ({ searchText }));

    public render() {
        const sidebarIcon = this.props.open ? "triangle left" : "triangle right";
        const { searchText } = this.state;
        return (
            <Menu className="menu-padding">
                <Responsive maxWidth={1024}>
                    <Menu.Item onClick={() => this.props.dispatch(setSidebarOpen())} className="sidebar-button-padding">
                        <Icon.Group size="large">
                            <Icon name="sidebar" />
                            <Icon corner color="grey" size="massive" name={sidebarIcon} />
                        </Icon.Group>
                    </Menu.Item>
                </Responsive>
                <Menu.Item>
                    <Link to={"/dashboard"}><H1>SDUCloud</H1></Link>
                </Menu.Item>
                <Menu.Menu position="right">
                    <Menu.Item>
                        <Responsive minWidth={700}>
                            <Form onSubmit={(e) => { e.preventDefault(); !!searchText ? this.props.history.push(`/metadata/search/${searchText}`) : null }} >
                                <Input value={searchText} onChange={(e, { value }) => this.updateSearchText(value)} className="header-search" fluid icon='search' placeholder='Search...' />
                            </Form>
                        </Responsive>
                        <Responsive maxWidth={699}>
                            <Link to={"/metadata/search/"}>
                                <Icon name="search" />
                            </Link>
                        </Responsive>
                    </Menu.Item>
                    <Menu.Item>
                        <Notifications />
                    </Menu.Item>
                    <Dropdown item icon="settings">
                        <Dropdown.Menu>
                            <Dropdown.Item onClick={() => Cloud.logout()}>Logout</Dropdown.Item>
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
export default connect(mapStateToProps)(withRouter(Header));
