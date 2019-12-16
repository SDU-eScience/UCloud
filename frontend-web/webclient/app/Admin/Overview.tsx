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
    {to: "/admin/userCreation", text: "User Creation", icon: "user", color2: "white"},
    {to: "/applications/studio", text: "Application Studio", icon: "eye", color: "white"},
    {to: "/admin/licenseServer", text: "License Servers", icon: "eye", color: "white"},
    {to: "/admin/downtime", text: "Downtime Status", icon: "eye", color: "white"}
];

interface LinkInfo {
    to: string;
    text: string;
    icon: IconName;
    color?: ThemeColor;
    color2?: ThemeColor;
}

function AdminOverview(props: {setActivePage: () => void}) {

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
                                    <Button color="black" mb="10px" mx="10px" width="200px">
                                        {it.text}
                                        {!it.icon ? null : <Icon ml="6px" name={it.icon} color={it.color} color2={it.color2} />}
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

const mapDispatchToProps = (dispatch: Dispatch) => ({
    setActivePage: () => dispatch(setActivePage(SidebarPages.Admin)),
});

export default connect(null, mapDispatchToProps)(AdminOverview);
