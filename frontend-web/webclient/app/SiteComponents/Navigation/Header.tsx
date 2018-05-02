import * as React from "react";
import { Menu, Dropdown, Icon } from "semantic-ui-react";
import { Cloud } from "../../../authentication/SDUCloudObject"
import { connect } from "react-redux";
import "./Header.scss";
import "./HeaderMenuLinks.scss";
import Notifications from "../Notifications/index";
import StatusBar from "./StatusBar";
import { setSidebarOpen } from "../../Actions/Sidebar";

interface HeaderProps { title: string }
class Header extends React.Component<any, any> {
    constructor(props) {
        super(props);
    }

    public render() {
        return (
            <Menu color="blue" secondary className="menu-padding">
                <Menu.Item onClick={() => this.props.dispatch(setSidebarOpen(true))}>
                    <Icon.Group size="large">
                        <Icon name="sidebar"/>
                        <Icon corner size="large" name="triangle right"/>
                    </Icon.Group>
                </Menu.Item>
                <Menu.Menu position="right">
                    <Menu.Item>
                        <StatusBar />
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

const mapStateToProps = (state: any) => state.status;
export default connect(mapStateToProps)(Header);
