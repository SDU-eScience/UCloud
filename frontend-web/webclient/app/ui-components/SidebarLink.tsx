import * as React from "react";
import Flex from "./Flex";
import Icon, {IconName} from "./Icon";
import Link from "./Link";
import {TextSpan} from "./Text";

export interface LinkInfo {
    to: string;
    text: string;
    icon: IconName;
}

export function SidebarLinkColumn({links}: {links: LinkInfo[]}): JSX.Element {
    return <Flex flexDirection="column">
        {links.map(it =>
            <Link key={it.text} ml="8px" mb="8px" to={it.to}>
                <Icon size="18px" name={it.icon} color="white" color2="white" />
                <TextSpan fontSize="var(--breadText)" ml="8px" color="white">{it.text}</TextSpan>
            </Link>
        )}
    </Flex>
}