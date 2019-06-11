import {EllipsedText} from "ui-components/Text";
import Box from "ui-components/Box";
import {inDevEnvironment} from "UtilityFunctions";
import * as React from "react";
import {SidebarTextLabel} from "ui-components/Sidebar";
import {connect} from "react-redux";
import {ReduxObject} from "DefaultObjects";

const _ContextSwitcher: React.FunctionComponent<{ maxSize: number } & ContextSwitcherReduxProps> = props => {
    if (!inDevEnvironment()) return null;

    const userContext = props.activeProject || "Personal Project";

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

interface ContextSwitcherReduxProps {
    activeProject?: string
}

export const ContextSwitcher = connect(
    (state: ReduxObject) => ({
        activeProject: state.project.project
    })
)(_ContextSwitcher);
