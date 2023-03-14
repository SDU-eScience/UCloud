import {Client} from "@/Authentication/HttpClientInstance";
import * as React from "react";
import { Flex, Icon, Link} from "@/ui-components";
import {IconName} from "@/ui-components/Icon";
import {TextSpan} from "@/ui-components/Text";
import AppRoutes from "@/Routes";

const linkInfo: LinkInfo[] = [
    {to: AppRoutes.admin.userCreation(), text: "User creation", icon: "user"},
    {to: AppRoutes.admin.applicationStudio(), text: "Application studio", icon: "appStore"},
    {to: AppRoutes.admin.news(), text: "News", icon: "warning"},
    {to: AppRoutes.admin.providers(), text: "Providers", icon: "cloudTryingItsBest"},
    {to: AppRoutes.admin.scripts(), text: "Scripts", icon: "play"},
];

interface LinkInfo {
    to: string;
    text: string;
    icon: IconName;
}

function AdminLinks(): JSX.Element | null {
    if (!Client.userIsAdmin) return null;
    return <Flex flexDirection="column">
        {linkInfo.map(it =>
            <Link ml="8px" mb="8px" to={it.to}>
                <Icon size="18px" name={it.icon} color="white" color2="white" />
                <TextSpan fontSize="var(--breadText)" ml="8px" color="white">{it.text}</TextSpan>
            </Link>
        )}
    </Flex>;
}

export default AdminLinks;
