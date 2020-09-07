import {Client} from "Authentication/HttpClientInstance";
import {MainContainer} from "MainContainer/MainContainer";
import {useTitle} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {Box, Button, Flex, Icon, Link} from "ui-components";
import * as Heading from "ui-components/Heading";
import {IconName} from "ui-components/Icon";
import {ThemeColor} from "ui-components/theme";
import {useSidebarPage, SidebarPages} from "ui-components/Sidebar";

const linkInfo: LinkInfo[] = [
    {to: "/admin/userCreation", text: "User Creation", icon: "user", color: "white", color2: "midGray"},
    {to: "/applications/studio", text: "Application Studio", icon: "appStore", color: "white", color2: "blue"},
    {to: "/admin/licenseServers", text: "License Servers", icon: "license", color: "white", color2: "white"},
    {to: "/admin/news", text: "News", icon: "warning", color: "white", color2: "black"}
];

interface LinkInfo {
    to: string;
    text: string;
    icon?: IconName;
    color?: ThemeColor;
    color2?: ThemeColor;
}

function AdminOverview(): JSX.Element | null {

    useTitle("Admin Dashboard");
    useSidebarPage(SidebarPages.Admin);

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
                                        {!it.icon ? null :
                                            <Icon
                                                mr=".5em"
                                                name={it.icon}
                                                size="1.5em"
                                                color={it.color}
                                                color2={it.color2}
                                            />
                                        }
                                        {it.text}
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

export default AdminOverview;
