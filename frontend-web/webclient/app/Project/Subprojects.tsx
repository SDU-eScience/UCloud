import {callAPIWithErrorHandler, useCloudAPI, useGlobalCloudAPI, useAsyncCommand, APICallParameters} from "Authentication/DataHook";
import {MainContainer} from "MainContainer/MainContainer";
import {
    listOutgoingInvites,
    OutgoingInvite,
    ProjectMember,
    ProjectRole,
    UserInProject,
    viewProject,
} from "Project/index";
import * as Heading from "ui-components/Heading";
import * as React from "react";
import {useCallback, useEffect} from "react";
import {useHistory, useParams} from "react-router";
import {Box, Button, Link, Flex, Icon, Input, Relative, Absolute, Label} from "ui-components";
import {connect, useSelector} from "react-redux";
import {Dispatch} from "redux";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {loadingAction} from "Loading";
import {
    shouldVerifyMembership,
    ShouldVerifyMembershipResponse,
    verifyMembership
} from "Project";
import styled from "styled-components";
import GroupView from "./GroupList";
import ProjectMembers, {MembersBreadcrumbs} from "./MembersPanel";
import {ReduxObject} from "DefaultObjects";
import {dispatchSetProjectAction} from "Project/Redux";
import {useProjectStatus} from "Project/cache";
import {isAdminOrPI} from "Utilities/ProjectUtilities";
import {ProjectSettings} from "Project/ProjectSettings";
import {Client} from "Authentication/HttpClientInstance";
import {preventDefault} from "UtilityFunctions";
import {SubprojectsList} from "./SubprojectsList";
import {addStandardDialog} from "UtilityComponents";

const SearchContainer = styled(Flex)`
    flex-wrap: wrap;
    
    form {
        flex-grow: 1;
        flex-basis: 350px;
        display: flex;
        margin-right: 10px;
        margin-bottom: 10px;
    }
`;

export const removeSubprojectFromProject = (payload: {projectId: string; subproject: string}): APICallParameters => ({
    method: "DELETE",
    path: "/projects/subprojects",
    payload,
    reloadId: Math.random()
});




// A lot easier to let typescript take care of the details for this one
// eslint-disable-next-line
export function useProjectManagementStatus() {
    const history = useHistory();
    const projectId = useSelector<ReduxObject, string | undefined>(it => it.project.project);
    const locationParams = useParams<{group: string; member?: string}>();
    let group = locationParams.group ? decodeURIComponent(locationParams.group) : undefined;
    let membersPage = locationParams.member ? decodeURIComponent(locationParams.member) : undefined;
    if (group === '-') group = undefined;
    if (membersPage === '-') membersPage = undefined;
   
    const [projectDetails, fetchProjectDetails, projectDetailsParams] = useGlobalCloudAPI<UserInProject>(
        "projectManagementDetails",
        {noop: true},
        {
            projectId: projectId ?? "",
            favorite: false,
            needsVerification: false,
            title: projectId ?? "",
            whoami: {username: Client.username ?? "", role: ProjectRole.USER},
            archived: false
        }
    );

    if (projectId === undefined) {
        history.push("/");
    }

    console.log(projectId);


    const projects = useProjectStatus();
    console.log(projects.fetch().membership);
    const projectRole = projects.fetch().membership
        .find(it => it.projectId === projectId)?.whoami?.role ?? ProjectRole.USER;

    console.log(projectRole);
    const allowManagement = isAdminOrPI(projectRole);
    const reloadProjectStatus = projects.reload;

    const setSubprojectParams = {};
    const subprojectParams = {};
    const fetchOutgoingInvites = {};
    const outgoingSubprojectInvitesParams = {};

    return {
        locationParams, projectId: projectId ?? "", group,
        allowManagement, reloadProjectStatus,
        membersPage, projectRole, setSubprojectParams, subprojectParams, fetchOutgoingInvites,
        outgoingSubprojectInvitesParams,
        projectDetails, projectDetailsParams, fetchProjectDetails,
    };
}

const Subprojects: React.FunctionComponent<SubprojectsOperations> = props => {
    const newSubprojectRef = React.useRef<HTMLInputElement>(null);
    const [isLoading, runCommand] = useAsyncCommand();

    const {
        projectId,
        allowManagement,
        setSubprojectParams,
        subprojectParams,
        fetchOutgoingInvites,
        outgoingSubprojectInvitesParams
    } = useProjectManagementStatus();

    console.log(allowManagement);

    const reloadSubprojects = (): void => {
        //setSubprojectParams(subprojectParams);
        //fetchOutgoingInvites(outgoingSubprojectInvitesParams);
    };

    const [shouldVerify, setShouldVerifyParams] = useCloudAPI<ShouldVerifyMembershipResponse>(
        shouldVerifyMembership(projectId),
        {shouldVerify: false}
    );

    useEffect(() => {
        if (projectId !== "") {
            props.setActiveProject(projectId);
        }
    }, [projectId]);

    const onApprove = async (): Promise<void> => {
        await callAPIWithErrorHandler(verifyMembership(projectId));
        setShouldVerifyParams(shouldVerifyMembership(projectId));
    };

    const projectText = `${projectId.slice(0, 20).trim()}${projectId.length > 20 ? "..." : ""}`;

    return (
        <MainContainer
            header={<Flex>
                <MembersBreadcrumbs>
                    <li>
                        <Link to="/projects">
                            My Projects
                        </Link>
                    </li>
                    <li>
                        <Link to={`/project/dashboard`}>
                            {projectText}
                        </Link>
                    </li>
                    <li>
                        Subprojects
                    </li>
                </MembersBreadcrumbs>
            </Flex>}
            sidebar={null}
            main={(
                <>
                    {!shouldVerify.data.shouldVerify ? null : (
                        <Box backgroundColor="orange" color="white" p={32} m={16}>
                            <Heading.h4>Time for a review!</Heading.h4>

                            <ul>
                                <li>PIs and admins are asked to occasionally review members of their project</li>
                                <li>We ask you to ensure that only the people who need access have access</li>
                                <li>If you find someone who should not have access then remove them by clicking
                                &apos;X&apos; next to their name
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
                    <Box className="subprojects" maxWidth={850} ml="auto" mr="auto">
                        <Box ml={8} mr={8}>
                            <SearchContainer>
                                {!allowManagement ? null : (
                                    <form>
                                        <Input
                                            id="new-project-subproject"
                                            placeholder="Name of project"
                                            disabled={isLoading}
                                            ref={newSubprojectRef}
                                            onChange={e => {
                                                newSubprojectRef.current!.value = e.target.value;
                                            }}
                                            rightLabel
                                        />
                                        <Button attached type={"submit"}>Add</Button>
                                    </form>
                                )}
                                <form onSubmit={preventDefault}>
                                    <Input
                                        id="subproject-search"
                                        placeholder="Enter name of project to search..."
                                        pr="30px"
                                        autoComplete="off"
                                        disabled={isLoading}
                                    />
                                    <Relative>
                                        <Absolute right="6px" top="10px">
                                            <Label htmlFor="subproject-search">
                                                <Icon name="search" size="24" />
                                            </Label>
                                        </Absolute>
                                    </Relative>
                                </form>
                            </SearchContainer>
                            <SubprojectsList
                                subprojects={[{id: "testProject", name: "Test Project"}]}
                                onRemoveSubproject={async subproject => addStandardDialog({
                                    title: "Remove subproject",
                                    message: `Remove ${subproject.name}?`,
                                    onConfirm: async () => {
                                        await runCommand(removeSubprojectFromProject({
                                            projectId,
                                            subproject: subproject.id
                                        }));

                                        reloadSubprojects();
                                    }
                                })}
                                reload={reloadSubprojects}
                                projectId={projectId}
                                allowRoleManagement={allowManagement}
                            />

                        </Box>
                    </Box>
                </>
            )}
        />
    );
};

interface SubprojectsOperations {
    setRefresh: (refresh?: () => void) => void;
    setLoading: (loading: boolean) => void;
    setActiveProject: (project: string) => void;
}

const mapDispatchToProps = (dispatch: Dispatch): SubprojectsOperations => ({
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    setLoading: loading => dispatch(loadingAction(loading)),
    setActiveProject: project => dispatchSetProjectAction(dispatch, project),
});

export default connect(null, mapDispatchToProps)(Subprojects);
