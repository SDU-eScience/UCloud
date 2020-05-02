import {callAPIWithErrorHandler, useCloudAPI, useGlobalCloudAPI} from "Authentication/DataHook";
import {LoadingMainContainer} from "MainContainer/MainContainer";
import {
    emptyProject,
    Project,
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
    groupSummaryRequest,
    listGroupMembersRequest,
    shouldVerifyMembership,
    ShouldVerifyMembershipResponse,
    verifyMembership
} from "Project/api";
import styled from "styled-components";
import GroupView, {GroupWithSummary} from "Project/GroupView";
import ProjectMembers from "./Members";
import {Page} from "Types";
import {emptyPage} from "DefaultObjects";

export function useProjectManagementStatus() {
    const locationParams = useParams<{ id: string, group: string }>();
    const projectId = decodeURIComponent(locationParams.id);
    const group = locationParams.group ? decodeURIComponent(locationParams.group) : undefined;
    const [project, setProjectParams] = useGlobalCloudAPI<Project>(
        "projectManagement",
        viewProject({id: projectId}),
        emptyProject(projectId)
    );

    const [groupMembers, fetchGroupMembers, groupMembersParams] = useGlobalCloudAPI<Page<string>>(
        "projectManagementGroupMembers",
        {noop: true},
        emptyPage
    );

    const [groupSummaries, fetchSummaries, groupSummaryParams] = useGlobalCloudAPI<Page<GroupWithSummary>>(
        "projectManagementGroupSummary",
        groupSummaryRequest({itemsPerPage: 10, page: 0}),
        emptyPage
    );

    return {
        locationParams, projectId, group, project, setProjectParams, groupMembers,
        fetchGroupMembers, groupMembersParams, groupSummaries, fetchSummaries, groupSummaryParams
    };
}

const View: React.FunctionComponent<ViewOperations> = props => {
    const {
        projectId,
        group,
        project,
        setProjectParams,
        groupMembers,
        fetchGroupMembers,
        fetchSummaries
    } = useProjectManagementStatus();

    const [shouldVerify, setShouldVerifyParams] = useCloudAPI<ShouldVerifyMembershipResponse>(
        shouldVerifyMembership(projectId),
        {shouldVerify: false}
    );

    useEffect(() => {
        if (group !== undefined) {
            fetchGroupMembers(listGroupMembersRequest({group, itemsPerPage: 25, page: 0}));
        } else {
            fetchSummaries(groupSummaryRequest({itemsPerPage: 10, page: 0}));
        }
    }, [projectId, group]);
    useEffect(() => setProjectParams(viewProject({id: projectId})), [projectId]);

    useEffect(() => {
        props.setLoading(project.loading || groupMembers.loading);
    }, [project.loading, groupMembers.loading]);

    const reload = (): void => {
        setProjectParams(viewProject({id: projectId}));
        if (group !== undefined) {
            fetchGroupMembers(listGroupMembersRequest({group, itemsPerPage: 25, page: 0}));
        }
    };

    useEffect(() => {
        props.setRefresh(reload);
        return () => props.setRefresh();
    }, [projectId, group]);

    const onApprove = async (): Promise<void> => {
        await callAPIWithErrorHandler(verifyMembership(projectId));
        setShouldVerifyParams(shouldVerifyMembership(projectId));
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
                            <Box ml={8} mr={8}>
                                <ProjectMembers/>
                            </Box>
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
            height: calc(100vh - 100px);
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
