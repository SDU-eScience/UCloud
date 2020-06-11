import {useCloudAPI, useAsyncCommand} from "Authentication/DataHook";
import {MainContainer} from "MainContainer/MainContainer";
import {
    UserInProject,
    createProject,
    listSubprojects,
    useProjectManagementStatus,
    deleteProject,
} from "Project/index";
import * as React from "react";
import {useEffect} from "react";
import {Box, Button, Flex, Icon, Input, Relative, Absolute, Label} from "ui-components";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {loadingAction} from "Loading";
import styled from "styled-components";
import {emptyPage} from "DefaultObjects";
import {dispatchSetProjectAction} from "Project/Redux";
import {preventDefault, errorMessageOrDefault} from "UtilityFunctions";
import {SubprojectsList} from "./SubprojectsList";
import {addStandardDialog, addStandardInputDialog} from "UtilityComponents";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Page} from "Types";
import {ProjectBreadcrumbs} from "Project/Breadcrumbs";

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


const Subprojects: React.FunctionComponent<SubprojectsOperations> = () => {
    const newSubprojectRef = React.useRef<HTMLInputElement>(null);
    const [isLoading, runCommand] = useAsyncCommand();

    const {
        projectId,
        allowManagement,
        subprojectSearchQuery,
        setSubprojectSearchQuery
    } = useProjectManagementStatus();

    const [subprojects, setSubprojectParams, subprojectParams] = useCloudAPI<Page<UserInProject>>(
        listSubprojects({itemsPerPage: 50, page: 0}),
        emptyPage
    );

    const reloadSubprojects = (): void => {
        setSubprojectParams({...subprojectParams});
    };

    useEffect(() => {
        reloadSubprojects();
    }, [projectId]);

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
            header={<ProjectBreadcrumbs crumbs={[{title: "Subprojects"}]} />}
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
                                            placeholder="Name of new child project"
                                            disabled={isLoading}
                                            ref={newSubprojectRef}
                                            onChange={e => {
                                                newSubprojectRef.current!.value = e.target.value;
                                            }}
                                            rightLabel
                                        />
                                        <Button attached type={"submit"}>Create</Button>
                                    </form>
                                )}
                                <form onSubmit={preventDefault}>
                                    <Input
                                        id="subproject-search"
                                        placeholder="Filter this page..."
                                        pr="30px"
                                        autoComplete="off"
                                        disabled={isLoading}
                                        onChange={e => {
                                            setSubprojectSearchQuery(e.target.value);
                                        }}
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
                                subprojects={subprojects.data}
                                searchQuery={subprojectSearchQuery}
                                loading={subprojects.loading}
                                fetchParams={setSubprojectParams}
                                onAssignCredits={async (subprojectId, subprojectTitle)  => addStandardInputDialog({
                                        title: `Assign credits to ${subprojectTitle}?`
                                    })
                                }
                                onRemoveSubproject={async (subprojectId, subprojectTitle) => addStandardDialog({
                                    title: "Delete subproject",
                                    message: `Delete ${subprojectTitle}?`,
                                    onConfirm: async () => {
                                        await runCommand(deleteProject({
                                            projectId: subprojectId,
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
