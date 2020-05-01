import {callAPIWithErrorHandler, useCloudAPI} from "Authentication/DataHook";
import {LoadingMainContainer} from "MainContainer/MainContainer";
import {
    emptyProject,
    Project,
    ProjectRole,
    roleInProject,
    viewProject
} from "Project/index";
import * as Heading from "ui-components/Heading";
import * as React from "react";
import {useEffect} from "react";
import {useParams} from "react-router";
import {Box, Button, Flex, Input, Label} from "ui-components";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {loadingAction} from "Loading";
import {
    shouldVerifyMembership,
    ShouldVerifyMembershipResponse,
    verifyMembership
} from "Project/api";
import styled from "styled-components";
import GroupView from "Project/GroupView";
import ProjectMembers from "./Members";

const View: React.FunctionComponent<ViewOperations> = props => {
    const id = decodeURIComponent(useParams<{ id: string }>().id);
    const [project, setProjectParams] = useCloudAPI<Project>(viewProject({id}), emptyProject(id));
    const [shouldVerify, setShouldVerifyParams] = useCloudAPI<ShouldVerifyMembershipResponse>(
        shouldVerifyMembership(id),
        {shouldVerify: false}
    );

    const reload = (): void => setProjectParams(viewProject({id}));

    useEffect(() => {
        props.setRefresh(reload);
        return () => props.setRefresh();
    }, []);

    useEffect(() => {
        props.setLoading(project.loading);
    }, [project.loading]);

    useEffect(() => reload(), [id]);

    const onApprove = async (): Promise<void> => {
        await callAPIWithErrorHandler(verifyMembership(id));
        setShouldVerifyParams(shouldVerifyMembership(id));
    };

    return (
        <LoadingMainContainer
            headerSize={0}
            header={null}
            sidebar={null}
            loading={project.loading && project.data.members.length === 0}
            error={project.error ? project.error.why : undefined}
            main={(
                <>
                    {!shouldVerify.data.shouldVerify ? null : (
                        <Box backgroundColor={"orange"} color={"white"} p={32} m={16}>
                            <Heading.h4>Time for a review!</Heading.h4>

                            <ul>
                                <li>PIs and admins are asked to occasionally review members of their project</li>
                                <li>We ask you to ensure that only the people who need access have access</li>
                                <li>If you find someone who should not have access then remove them by clicking
                                    'Remove'
                                    next to their name
                                </li>
                                <li>
                                    When you are done, click below:

                                    <Box mt={8}>
                                        <Button color={"green"} textColor={"white"} onClick={onApprove}>
                                            Everything looks good now
                                        </Button>
                                    </Box>
                                </li>
                            </ul>

                        </Box>
                    )}
                    <TwoColumnLayout>
                        <Box className={"members"}>
                            <ProjectMembers
                                id={id}
                                project={project}
                                reload={reload} />
                        </Box>
                        <Box className={"groups"}>
                            <GroupView/>
                        </Box>
                    </TwoColumnLayout>
                </>
            )}
        />
    );
};

const TwoColumnLayout = styled.div`
    display: flex;
    flex-direction: row;
    flex-wrap: wrap;
    width: 100%;
    
    & > * {
        flex-basis: 100%;
    }
    
    @media screen and (min-width: 1200px) {
        & {
            height: calc(100vh - 140px);
            overflow: hidden;
        }
        
        & > .members {
            height: 100%;
            flex: 1;
            overflow-y: scroll;
            margin-right: 32px;
        }
        
        & > .groups {
            flex: 2;
            height: 100%;
            overflow: auto;
        }
    }
`;

interface ViewOperations {
    setRefresh: (refresh?: () => void) => void;
    setLoading: (loading: boolean) => void;
}

const mapDispatchToProps = (dispatch: Dispatch): ViewOperations => ({
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    setLoading: loading => dispatch(loadingAction(loading))
});

export default connect(null, mapDispatchToProps)(View);
