import {Client} from "Authentication/HttpClientInstance";
import {MainContainer} from "MainContainer/MainContainer";
import {setActivePage} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Box, Button, Icon, Link} from "ui-components";
import * as Heading from "ui-components/Heading";
import {IconName} from "ui-components/Icon";
import {SidebarPages} from "ui-components/Sidebar";

const linkInfo: LinkInfo[] = [
    {to: "/admin/userCreation", text: "User Creation", icon: "user"},
    {to: "/applications/studio", text: "Application Studio", icon: "eye"},
    {to: "/admin/licenseServer", text: "License Servers", icon: "eye"},
    {to: "/admin/downtime", text: "Downtime Status", icon: "eye"}
];

interface LinkInfo {
    to: string;
    text: string;
    icon: IconName;
}

function AdminOverview(props: {setActivePage: () => void}) {
    if (!Client.userIsAdmin) return null;
    React.useEffect(() => {
        props.setActivePage();
    }, []);
    return (
        <MainContainer
            main={(
                <>
                    <Heading.h2>Admin</Heading.h2>
                    <Box mt="30px">
                        {linkInfo.map(it => (
                            <Link key={it.to} to={it.to}>
                                <Button mb="10px" mx="10px" width="200px">
                                    {it.icon ? <Icon mr="3px" name={it.icon} /> : null}{it.text}
                                </Button>
                            </Link>
                        ))}
                    </Box>
                </>
            )}
        />
    );
}

const mapDispatchToProps = (dispatch: Dispatch) => ({
    setActivePage: () => dispatch(setActivePage(SidebarPages.Admin)),
});

export default connect(null, mapDispatchToProps)(AdminOverview);
