import React from "react";
import { Link } from "react-router-dom";
import { Menu, Sidebar, Icon, Accordion, Transition, List, Segment } from "semantic-ui-react";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { connect } from "react-redux";
import { fetchSidebarOptions, setSidebarLoading, setSidebarOpen } from "../../Actions/Sidebar";
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
        const { open } = this.props;
        const { activeIndex } = this.state;
        return (
            <Sidebar.Pushable as={Segment}>
                <Sidebar as={Menu} fixed="left" animation="overlay" visible={open} vertical>
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
                        <Link to={"/dashboard"} className="sidebar-option">
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
                        <Link to={`/files/${Cloud.homeFolder}`} className="sidebar-option">
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
                            <Accordion.Title onClick={this.handleClick} index={0} active={activeIndex === 0}>
                                <Icon name="dropdown" />
                                Applications
                            </Accordion.Title>
                            {/*<Transition duration={200} visible={activeIndex === 0} animation="fade right">*/}
                            <Accordion.Content active={activeIndex === 0}>
                                <List>
                                    <Link to="/applications" className="sidebar-option">
                                        <List.Item>
                                            <List.Icon name="code" />
                                            Run
                                    </List.Item>
                                    </Link>
                                    <Link to="/analyses" className="sidebar-option">
                                        <List.Item>
                                            <Icon name="tasks" />
                                            Results
                                    </List.Item>
                                    </Link>
                                </List>
                            </Accordion.Content>
                            {/*</Transition>*/}
                        </Accordion>
                    </Menu.Item>
                    <Menu.Item>
                        <Accordion>
                            <Accordion.Title onClick={this.handleClick} index={1} active={activeIndex === 1}>
                                <Icon name="dropdown" />
                                Publishing
                            </Accordion.Title>
                            {/*<Transition duration={200} visible={activeIndex === 0} animation="fade right">*/}
                            <Accordion.Content active={activeIndex === 1}>
                                <List>
                                    <Link to="/zenodo" className="sidebar-option">
                                        <List.Item>
                                            <List.Icon name="newspaper" />
                                            Publications
                                    </List.Item>
                                    </Link>
                                    <Link to="/zenodo/publish" className="sidebar-option">
                                        <List.Item>
                                            <List.Icon name="edit" />
                                            Publish
                                    </List.Item>
                                    </Link>
                                </List>
                            </Accordion.Content>
                            {/* </Transition> */}
                        </Accordion>
                    </Menu.Item>
                </Sidebar >
                <Sidebar.Pusher>
                    {this.props.children}
                </Sidebar.Pusher>
            </Sidebar.Pushable>
        );
    }
}

const mapStateToProps = ({ sidebar }) => ({ options, loading, open } = sidebar);
export default connect(mapStateToProps)(SidebarComponent);