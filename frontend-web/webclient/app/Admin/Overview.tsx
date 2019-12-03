import {Client} from "Authentication/HttpClientInstance";
import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {Box, Button, Icon, Link} from "ui-components";
import * as Heading from "ui-components/Heading";
import {IconName} from "ui-components/Icon";

const linkInfo: LinkInfo[] = [
    {to: "/admin/userCreation", text: "User Creation", icon: "user"},
    {to: "/applications/studio", text: "Application Studio", icon: "eye"},
    /* {to: "/playground", text: "License Servers", icon: "eye"}, */
    {to: "/playground", text: "Downtime Status", icon: "eye"}
];

interface LinkInfo {
    to: string;
    text: string;
    icon: IconName;
}

export function AdminOverview() {
    if (!Client.userIsAdmin) return null;
    return (
        <MainContainer
            main={(
                <>
                    <Heading.h2>Admin</Heading.h2>
                    <Box>
                        {linkInfo.map(it => (
                            <Link key={it.to} to={it.to}>
                                <Button mx="30px" width="200px"><Icon mr="3px" name={it.icon} />{it.text}</Button>
                            </Link>
                        ))}
                    </Box>
                </>
            )}
        />
    );
}
