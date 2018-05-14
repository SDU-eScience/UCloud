import React from "react";
import { Link } from "react-router-dom";
import { Menu, Sidebar, Icon, Accordion, Transition, List, Segment, Container, Responsive } from "semantic-ui-react";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { connect } from "react-redux";
import { fetchSidebarOptions, setSidebarLoading, setSidebarOpen, setSidebarClosed } from "../../Actions/Sidebar";
import Avatar from "avataaars";

class SidebarComponent extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            activeIndex: -1
        };
        const { dispatch } = props;
        dispatch(setSidebarLoading(true));
        dispatch(fetchSidebarOptions());
        this.handleClick = this.handleClick.bind(this);
    }

    handleClick(e, titleProps) {
        const { index } = titleProps;
        const { activeIndex } = this.state;
        const newIndex = activeIndex === index ? -1 : index;
        this.setState({ activeIndex: newIndex });
    }

    render() {
        const { open, dispatch } = this.props;
        const { activeIndex } = this.state;
        const closeSidebar = () => dispatch(setSidebarClosed())
        return (
            <React.Fragment>
                <Sidebar.Pushable className="sidebar-height">
                    <Responsive minWidth={1025}>
                        <Menu vertical borderless={true} fixed="left" className="my-sidebar">
                            <SidebarMenuItems handleClick={this.handleClick} activeIndex={activeIndex} closeSidebar={closeSidebar} />
                        </Menu>
                    </Responsive>
                    <MobileSidebar
                        closeSidebar={closeSidebar}
                        visible={open}
                        handleClick={this.handleClick}
                        activeIndex={activeIndex}
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

const MobileSidebar = ({ handleClick, activeIndex, visible, closeSidebar }) => (
    <Sidebar as={Menu} animation="overlay" vertical fixed="left" visible={visible}>
        <SidebarMenuItems closeSidebar={closeSidebar} handleClick={handleClick} activeIndex={activeIndex} />
    </Sidebar>
);
const SidebarMenuItems = ({ handleClick, closeSidebar, activeIndex }, ...props) => (
    <React.Fragment>
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
            <Link to={"/dashboard"} onClick={() => closeSidebar()} className="sidebar-option">
                <List>
                    <List.Item>
                        <List.Content floated="right">
                            <List.Icon name="home" />
                        </List.Content>
                        Dashboard
                    </List.Item>
                </List>
            </Link>
        </Menu.Item>
        <Menu.Item>
            <Link to={`/files/${Cloud.homeFolder}`} onClick={() => closeSidebar()} className="sidebar-option">
                <List>
                    <List.Item>
                        <List.Content floated="right" >
                            <List.Icon name="file" floated="right" />
                        </List.Content>
                        Files
                    </List.Item>
                </List>
            </Link>
        </Menu.Item>
        <Menu.Item>
            <Accordion>
                <Accordion.Title onClick={handleClick} index={0} active={activeIndex === 0}>
                    <Icon name="dropdown" />
                    Applications
                            </Accordion.Title>
                <Transition duration={200} visible={activeIndex === 0} animation="fade right">
                    <Accordion.Content active={activeIndex === 0}>
                        <List>
                            <Link to="/applications" onClick={() => closeSidebar()} className="sidebar-option">
                                <List.Item>
                                    <List.Icon name="code" />
                                    Run
                                </List.Item>
                            </Link>
                            <Link to="/analyses" onClick={() => closeSidebar()} className="sidebar-option">
                                <List.Item>
                                    <Icon name="tasks" />
                                    Results
                                </List.Item>
                            </Link>
                        </List>
                    </Accordion.Content>
                </Transition>
            </Accordion>
        </Menu.Item>
        <Menu.Item>
            <Accordion>
                <Accordion.Title onClick={handleClick} index={1} active={activeIndex === 1}>
                    <Icon name="dropdown" />
                    Publishing
                </Accordion.Title>
                <Transition duration={200} visible={activeIndex === 1} animation="fade right">
                    <Accordion.Content active={activeIndex === 1}>
                        <List>
                            <Link to="/zenodo" onClick={() => closeSidebar()} className="sidebar-option">
                                <List.Item>
                                    <List.Icon name="newspaper" />
                                    Publications
                                </List.Item>
                            </Link>
                            <Link to="/zenodo/publish" onClick={() => closeSidebar()} className="sidebar-option">
                                <List.Item>
                                    <List.Icon name="edit" />
                                    Publish
                                </List.Item>
                            </Link>
                        </List>
                    </Accordion.Content>
                </Transition>
            </Accordion>
        </Menu.Item>
    </React.Fragment>
);

const mapStateToProps = ({ sidebar }) => ({ options, loading, open } = sidebar);
export default connect(mapStateToProps)(SidebarComponent);