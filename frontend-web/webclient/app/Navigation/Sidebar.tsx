import * as React from "react";
import { Link } from "react-router-dom";
import { Menu, Sidebar, Icon, Accordion, List, Responsive, AccordionTitleProps } from "semantic-ui-react";
import { Cloud } from "Authentication/SDUCloudObject";
import { connect } from "react-redux";
import { setSidebarState } from "./Redux/SidebarActions";
import { PP } from "UtilityComponents";

interface SidebarProps {
    open: boolean
    pp: boolean
    setSidebarState: (open: boolean) => void
}

interface SidebarState {
    activeIndices: [boolean, boolean, boolean]
}

class SidebarComponent extends React.Component<SidebarProps, SidebarState> {
    constructor(props) {
        super(props);
        this.state = {
            activeIndices: [false, false, false]
        };
    }

    handleClick = (e: React.MouseEvent<HTMLDivElement>, { index }: { index: number }) => {
        const { activeIndices } = this.state;
        activeIndices[index] = !activeIndices[index]
        this.setState({ activeIndices });
    }

    render() {
        const { open, setSidebarState } = this.props;
        const { activeIndices } = this.state;
        const sidebarIsOpen = open && window.innerWidth < 1000;

        const content = (
            <div className={"container-wrapper"}>
                <div className="container-content">
                    <div className="container-padding">
                        {this.props.children}
                    </div>
                </div>
            </div>
        );

        return (
            <>
                <Responsive minWidth={1000}>
                    <Accordion as={Menu} vertical borderless fixed="left" className="my-sidebar">
                        <SidebarMenuItems handleClick={this.handleClick} activeIndices={activeIndices} closeSidebar={() => setSidebarState(false)} />
                        <PP visible={this.props.pp}/>
                    </Accordion>

                    {content}
                </Responsive>

                <Responsive maxWidth={999} as={Sidebar.Pushable}>
                    <MobileSidebar
                        closeSidebar={() => setSidebarState(false)}
                        visible={open}
                        handleClick={this.handleClick}
                        activeIndices={activeIndices}
                    />

                    <Sidebar.Pusher
                        onClick={() => setSidebarState(false)}
                        dimmed={sidebarIsOpen}
                    >
                        {content}
                    </Sidebar.Pusher>
                </Responsive>
            </ >
        );
    }
}

type HandleClick = (e: React.MouseEvent<HTMLDivElement>, d: AccordionTitleProps) => void;
interface AdminOptionsProps { menuActive: boolean, handleClick: HandleClick, closeSidebar: Function }
const AdminOptions = ({ menuActive, handleClick, closeSidebar }: AdminOptionsProps) => Cloud.userIsAdmin ? (
    <Menu.Item>
        <Accordion.Title content="Admin" onClick={handleClick} index={2} active={menuActive} />
        <Accordion.Content active={menuActive}>
            <List>
                <Link to="/admin/usercreation" onClick={() => closeSidebar()} className="sidebar-option">
                    <List.Item content="User Creation" icon="user plus" className="item-padding-right" />
                </Link>
            </List>
        </Accordion.Content>
    </Menu.Item>) : null;

interface MobileSidebarProps { handleClick: HandleClick, activeIndices: boolean[], visible: boolean, closeSidebar: Function }
const MobileSidebar = ({ handleClick, activeIndices, visible, closeSidebar }: MobileSidebarProps) => (
    <Sidebar animation="overlay" visible={visible}>
        <Accordion as={Menu} borderless vertical fixed="left" className="my-sidebar">
            <SidebarMenuItems closeSidebar={closeSidebar} handleClick={handleClick} activeIndices={activeIndices} />
        </Accordion>
    </Sidebar>
);

const SidebarMenuItems = ({ handleClick, closeSidebar, activeIndices }) => (
    <>
        <Accordion>
            <Menu.Item>
                <MenuLink icon="home" to="/dashboard" name="Dashboard" onClick={() => closeSidebar()} />
            </Menu.Item>
            <Menu.Item>
                <MenuLink icon="file" to={`/files/${Cloud.homeFolder}`} name="Files" onClick={() => closeSidebar()} />
            </Menu.Item>
            <Menu.Item>
                <MenuLink icon="question" to={`/activity/`} name="Activity" onClick={() => closeSidebar()} />
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
            <AdminOptions menuActive={activeIndices[2]} handleClick={handleClick} closeSidebar={closeSidebar} />
        </Accordion>
    </>
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


const mapDispatchToProps = (dispatch) => ({
    setSidebarState: (open: boolean) => dispatch(setSidebarState(open))
});

const mapStateToProps = ({ sidebar }) => sidebar;
export default connect(mapStateToProps, mapDispatchToProps)(SidebarComponent);