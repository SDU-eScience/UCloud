import * as React from "react";
import { Menu, Dropdown, Icon, Responsive } from "semantic-ui-react";
import { Cloud } from "../../../authentication/SDUCloudObject"
import { connect } from "react-redux";
import "./Header.scss";
import Notifications from "../Notifications/index";
import { setSidebarOpen } from "../../Actions/Sidebar";

interface HeaderProps { title: string }
class Header extends React.Component<any, any> {
    constructor(props) {
        super(props);
    }

    public render() {
        const sidebarIcon = this.props.open ? "triangle left" : "triangle right";
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
                <Menu.Menu position="right">
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

const mapStateToProps = ({ sidebar }: any) => ({ open: sidebar.open });
export default connect(mapStateToProps)(Header);
