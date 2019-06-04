import {EllipsedText} from "ui-components/Text";
import Box from "ui-components/Box";
import {inDevEnvironment} from "UtilityFunctions";
import * as React from "react";
import {SidebarTextLabel} from "ui-components/Sidebar";

export const ContextSwitcher: React.FunctionComponent<{ maxSize: number }> = props => {
    if (!inDevEnvironment()) return null;

    const userContext = "Personal Project";

    return <SidebarTextLabel icon={"projects"} height={"25px"} textSize={1} iconSize="1em" space={".5em"}>
        <Box cursor={"pointer"}>
            <EllipsedText
                width={props.maxSize - 20}
                fontSize={"14px"}
                as={"span"}
                title={userContext}
            >
                {userContext}
            </EllipsedText>
        </Box>
    </SidebarTextLabel>
};
