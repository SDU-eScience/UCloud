import {Client} from "Authentication/HttpClientInstance";
import {MainContainer} from "MainContainer/MainContainer";
import {setActivePage} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Box, Button, Flex, Icon, Link} from "ui-components";
import * as Heading from "ui-components/Heading";
import {IconName} from "ui-components/Icon";
import {SidebarPages} from "ui-components/Sidebar";
import {ThemeColor} from "ui-components/theme";

const linkInfo: LinkInfo[] = [
    {to: "/admin/userCreation", text: "User Creation", icon: "user", color: "blue", color2: "white"},
    {to: "/applications/studio", text: "Application Studio", icon: "appStore", color: "white", color2: "blue"},
    {to: "/admin/licenseServers", text: "License Servers", icon: "license", color: "white", color2: "white"},
    {to: "/admin/downtime", text: "Downtime Status", icon: "warning", color: "white", color2: "black"}
];

interface LinkInfo {
    to: string;
    text: string;
    icon?: IconName;
    color?: ThemeColor;
    color2?: ThemeColor;
}

interface AdminOperations {
    setActivePage: () => void;
}

function AdminOverview(props: AdminOperations): JSX.Element | null {

    React.useEffect(() => {
        props.setActivePage();
    }, []);

    if (!Client.userIsAdmin) return null;
    return (
        <MainContainer
            main={(
                <>
                    <Heading.h2>Admin dashboard</Heading.h2>
                    <Flex justifyContent="center">
                        <Box mt="30px">
                            {linkInfo.map(it => (
                                <Link key={it.to} to={it.to}>
                                    <Button mb="10px" mx="10px" width="200px">
                                        {it.text}
                                        {!it.icon ? null : <Icon ml=".5em" name={it.icon} size={"1.5em"} color={it.color} color2={it.color2} />}
                                    </Button>
                                </Link>
                            ))}
                        </Box>
                    </Flex>
                </>
            )}
        />
    );
}

const mapDispatchToProps = (dispatch: Dispatch): AdminOperations => ({
    setActivePage: () => dispatch(setActivePage(SidebarPages.Admin)),
});

export default connect(null, mapDispatchToProps)(AdminOverview);
