import * as React from "react";
import Flex from "./Flex";
import Icon, {IconName} from "./Icon";
import Link from "./Link";
import Box from "./Box";

export interface LinkInfo {
    to: string;
    text: string;
    icon: IconName;
    disabled?: boolean;
    removed?: boolean;
}

export function SidebarLinkColumn({links}: { links: LinkInfo[] }): React.ReactNode {
    return <Flex flexDirection="column">
        {links.map(it => it.removed ? null : it.disabled ?
            <Box key={it.text} mb="8px" cursor="not-allowed" color={"gray"}>
                <Icon size="18px" name={it.icon} color="gray" color2="gray" mr={"4px"}/>
                {it.text}
            </Box> :
            <Link key={it.text} mb="8px" to={it.to}>
                <Icon size="18px" name={it.icon} color="#fff" mr={"4px"}/>
                {it.text}
            </Link>
        )}
    </Flex>
}

export const SidebarSectionHeader: React.FunctionComponent<{ children: React.ReactNode }> = ({children}) => {
    return null;
}
