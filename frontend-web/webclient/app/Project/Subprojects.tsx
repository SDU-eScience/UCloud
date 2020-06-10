import {callAPIWithErrorHandler, useCloudAPI, useGlobalCloudAPI, useAsyncCommand, APICallParameters} from "Authentication/DataHook";
import {MainContainer} from "MainContainer/MainContainer";
import {
    UserInProject,
    createProject,
    listSubprojects,
    useProjectManagementStatus,
    deleteProject,
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
import styled from "styled-components";
import GroupView from "./GroupList";
import ProjectMembers, {MembersBreadcrumbs} from "./MembersPanel";
import {ReduxObject, emptyPage} from "DefaultObjects";
import {dispatchSetProjectAction} from "Project/Redux";
import {preventDefault, shortUUID, errorMessageOrDefault} from "UtilityFunctions";
import {SubprojectsList} from "./SubprojectsList";
import {addStandardDialog} from "UtilityComponents";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Page} from "Types";

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


const Subprojects: React.FunctionComponent<SubprojectsOperations> = props => {
    const newSubprojectRef = React.useRef<HTMLInputElement>(null);
    const [isLoading, runCommand] = useAsyncCommand();

    const {
        projectId,
        projectDetails,
        allowManagement
    } = useProjectManagementStatus();

    const [subprojects, setSubprojectParams, subprojectParams] = useCloudAPI<Page<UserInProject>>(
        listSubprojects({itemsPerPage: 100, page: 0}),
        emptyPage
    );

    useEffect(() => {
        setSubprojectParams({...subprojectParams});
    }, [projectId]);

    const reloadSubprojects = (): void => {
        //setSubprojectParams(subprojectParams);
        //fetchOutgoingInvites(outgoingSubprojectInvitesParams);
    };

    const onSubmit = async (e: React.FormEvent): Promise<void> => {
        e.preventDefault();
        const inputField = newSubprojectRef.current!;
        const newProjectName = inputField.value;
        try {
            await runCommand(createProject({
                title: newProjectName,
                parent: projectId
            }));
            inputField.value = "";
            reloadSubprojects();
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed creating new project"), false);
        }
    };

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
                            {projectDetails.data.title}
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
                    <Box className="subprojects" maxWidth={850} ml="auto" mr="auto">
                        <Box ml={8} mr={8}>
                            <SearchContainer>
                                {!allowManagement ? null : (
                                    <form onSubmit={onSubmit}>
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
                                subprojects={subprojects.data.items}
                                onRemoveSubproject={async (projectId, subprojectTitle) => addStandardDialog({
                                    title: "Remove subproject",
                                    message: `Remove ${subprojectTitle}?`,
                                    onConfirm: async () => {
                                        await runCommand(deleteProject({
                                            projectId,
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
