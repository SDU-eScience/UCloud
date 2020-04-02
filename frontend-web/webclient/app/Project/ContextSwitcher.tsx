import {ReduxObject} from "DefaultObjects";
import * as React from "react";
import {connect} from "react-redux";
import Box from "ui-components/Box";
import Link from "ui-components/Link";
import {SidebarTextLabel} from "ui-components/Sidebar";
import {EllipsedText} from "ui-components/Text";
import {inDevEnvironment} from "UtilityFunctions";
import {useEffect} from "react";
import {Dispatch} from "redux";
import {dispatchSetProjectAction, getStoredProject} from "Project/Redux";
import {DEV_SITE, STAGING_SITE} from "../../site.config.json";

// eslint-disable-next-line no-underscore-dangle
const _ContextSwitcher: React.FunctionComponent<{maxSize: number} & ContextSwitcherReduxProps & DispatchProps> = props => {
    if (![DEV_SITE, STAGING_SITE].includes(window.location.host) && !inDevEnvironment()) return null;

    const userContext = props.activeProject ?? "Personal Project";

    useEffect(() => {
        const storedProject = getStoredProject();
        props.setProject(storedProject ?? undefined);
    }, []);

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

interface DispatchProps {
    setProject: (id?: string) => void;
}

const mapStateToProps = (state: ReduxObject): {activeProject?: string} => ({activeProject: state.project.project});

const mapDispatchToProps = (dispatch: Dispatch): DispatchProps => ({
    setProject: id => dispatchSetProjectAction(dispatch, id)
});

export const ContextSwitcher = connect(mapStateToProps, mapDispatchToProps)(_ContextSwitcher);
