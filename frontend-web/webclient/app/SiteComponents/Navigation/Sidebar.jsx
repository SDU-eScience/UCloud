import React from "react";
import { Link } from "react-router-dom";
import { Menu, Sidebar, Icon, Accordion, Transition, List, Responsive } from "semantic-ui-react";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { connect } from "react-redux";
import { fetchSidebarOptions, setSidebarLoading, setSidebarClosed } from "../../Actions/Sidebar";
import Avatar from "avataaars";

class SidebarComponent extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            activeIndices: [false, false]
        };
        const { dispatch } = props;
        dispatch(setSidebarLoading(true));
        dispatch(fetchSidebarOptions());
        this.handleClick = this.handleClick.bind(this);
    }

    handleClick(e, titleProps) {
        const { index } = titleProps;
        const { activeIndices } = this.state;
        activeIndices[index] = !activeIndices[index]
        this.setState({ activeIndices });
    }

    render() {
        const { open, dispatch } = this.props;
        const { activeIndices } = this.state;
        const closeSidebar = () => dispatch(setSidebarClosed())
        return (
            <React.Fragment>
                <Sidebar.Pushable className="sidebar-height">
                    <Responsive minWidth={1025}>
                        <Accordion as={Menu} vertical borderless={true} fixed="left" className="my-sidebar">
                            <SidebarMenuItems handleClick={this.handleClick} activeIndices={activeIndices} closeSidebar={closeSidebar} />
                        </Accordion>
                    </Responsive>
                    <MobileSidebar
                        closeSidebar={closeSidebar}
                        visible={open}
                        handleClick={this.handleClick}
                        activeIndices={activeIndices}
                    />
                    <Sidebar.Pusher
                        onClick={() => open ? closeSidebar() : null}
                        dimmed={open && window.innerWidth <= 1024}
                    >
                        <div className="container-margin content-height container-padding">
                            {this.props.children}
                        </div>
                    </Sidebar.Pusher>
                </Sidebar.Pushable>
            </React.Fragment >
        );
    }
}

const MobileSidebar = ({ handleClick, activeIndices, visible, closeSidebar }) => (
    <Sidebar animation="overlay" visible={visible}>
        <Accordion as={Menu} vertical fixed="left" className="my-sidebar">
            <SidebarMenuItems closeSidebar={closeSidebar} handleClick={handleClick} activeIndices={activeIndices} />
        </Accordion>

    </Sidebar>
);
const SidebarMenuItems = ({ handleClick, closeSidebar, activeIndices }, ...props) => (
    <React.Fragment>
        <Accordion>
            <Menu.Item>
                <Avatar
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
                <div className="user-name">{`Welcome, ${Cloud.userInfo.firstNames}`}</div>
            </Menu.Item>
            <Menu.Item>
                <MenuLink icon="home" to="/dashboard" name="Dashboard" onClick={() => closeSidebar()} />
            </Menu.Item>
            <Menu.Item>
                <MenuLink icon="file" to={`/files/${Cloud.homeFolder}`} name="Files" onClick={() => closeSidebar()} />
            </Menu.Item>
            <Menu.Item>
                <Accordion.Title onClick={handleClick} index={0} active={activeIndices[0]}>
                    Applications
                    <Icon name="dropdown" />
                </Accordion.Title>
                <Accordion.Content active={activeIndices[0]} >
                    <List>
                        <Link to="/applications" onClick={() => closeSidebar()} className="sidebar-option">
                            <List.Item className="item-padding-right">
                                <List.Icon name="code" />
                                Run
                            </List.Item>
                        </Link>
                        <Link to="/analyses" onClick={() => closeSidebar()} className="sidebar-option">
                            <List.Item className="item-padding-right">
                                <Icon name="tasks" />
                                Results
                            </List.Item>
                        </Link>
                    </List>
                </Accordion.Content>
            </Menu.Item>
            <Menu.Item>
                <Accordion.Title content="Publishing" onClick={handleClick} index={1} active={activeIndices[1]} />
                <Accordion.Content active={activeIndices[1]}>
                    <List>
                        <Link to="/zenodo" onClick={() => closeSidebar()} className="sidebar-option">
                            <List.Item className="item-padding-right">
                                <List.Icon name="newspaper" />
                                Publications
                                </List.Item>
                        </Link>
                        <Link to="/zenodo/publish" onClick={() => closeSidebar()} className="sidebar-option">
                            <List.Item className="item-padding-right">
                                <List.Icon name="edit" />
                                Publish
                            </List.Item>
                        </Link>
                    </List>
                </Accordion.Content>
            </Menu.Item>
            <Menu.Item>
                <MenuLink icon="share" to="/shares" name="Shares" onClick={() => closeSidebar()} />
            </Menu.Item>
        </Accordion>
    </React.Fragment>
);

const MenuLink = ({ icon, name, to, onClick }) =>
    <Link to={to} onClick={onClick} className="sidebar-option">
        <List>
            <List.Item>
                <List.Content floated="right">
                    <List.Icon name={icon} />
                </List.Content>
                {name}
                </List.Item>
        </List>
    </Link>

const mapStateToProps = ({ sidebar }) => ({ options, loading, open } = sidebar);
export default connect(mapStateToProps)(SidebarComponent);