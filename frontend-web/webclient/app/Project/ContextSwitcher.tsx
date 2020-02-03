import {ReduxObject} from "DefaultObjects";
import * as React from "react";
import {connect} from "react-redux";
import Box from "ui-components/Box";
import Link from "ui-components/Link";
import {SidebarTextLabel} from "ui-components/Sidebar";
import {EllipsedText} from "ui-components/Text";
import {inDevEnvironment} from "UtilityFunctions";

// eslint-disable-next-line no-underscore-dangle
const _ContextSwitcher: React.FunctionComponent<{maxSize: number} & ContextSwitcherReduxProps> = props => {
    if (!inDevEnvironment()) return null;

    const userContext = props.activeProject || "Personal Project";

    return (
        <SidebarTextLabel icon="projects" height="25px" textSize={1} iconSize="1em" space=".5em">
            <Link to="/projects">
                <Box cursor="pointer">
                    <EllipsedText
                        width={props.maxSize - 20}
                        fontSize="14px"
                        as="span"
                        title={userContext}
                    >
                        {userContext}
                    </EllipsedText>
                </Box>
            </Link>
        </SidebarTextLabel>
    );
};

interface ContextSwitcherReduxProps {
    activeProject?: string;
}

const mapStateToProps = (state: ReduxObject): {activeProject?: string} => ({activeProject: state.project.project});

export const ContextSwitcher = connect(mapStateToProps)(_ContextSwitcher);
