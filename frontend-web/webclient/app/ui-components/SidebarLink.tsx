import * as React from "react";
import Flex from "./Flex";
import Icon, {IconName} from "./Icon";
import Link from "./Link";
import {TextSpan} from "./Text";
import Box from "./Box";

export interface LinkInfo {
    to: string;
    text: string;
    icon: IconName;
    disabled?: boolean;
}

export function SidebarLinkColumn({links}: {links: LinkInfo[]}): JSX.Element {
    return <Flex flexDirection="column">
        {links.map(it => it.disabled ?
            <Box key={it.text} mb="8px" cursor="not-allowed">
                <Icon size="18px" name={it.icon} color="red" color2="red" />
                <TextSpan fontSize="var(--breadText)" ml="8px" color="red">{it.text}</TextSpan>
            </Box> :
            <Link key={it.text} mb="8px" to={it.to}>
                <Icon size="18px" name={it.icon} color="white" color2="white" />
                <TextSpan fontSize="var(--breadText)" ml="8px" color="white">{it.text}</TextSpan>
            </Link>
        )}
    </Flex>
}