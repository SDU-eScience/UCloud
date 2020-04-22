import {useCloudAPI} from "Authentication/DataHook";
import {emptyPage, ReduxObject, KeyCode} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import * as Pagination from "Pagination";
import {listProjects, ListProjectsRequest, UserInProject, ProjectRole, Project} from "Project/index";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Page, Operation} from "Types";
import Button from "ui-components/Button";
import {Flex, Icon, List, Text, Input, Box} from "ui-components";
import Link from "ui-components/Link";
import VerticalButtonGroup from "ui-components/VerticalButtonGroup";
import {updatePageTitle} from "Navigation/Redux/StatusActions";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {ListRow} from "ui-components/List";
import {useHistory} from "react-router";
import {loadingAction} from "Loading";
import {dispatchSetProjectAction} from "Project/Redux";
import {projectRoleToString} from "Project/api";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {isAdminOrPI} from "Utilities/ProjectUtilities";
import {Client} from "Authentication/HttpClientInstance";
import {errorMessageOrDefault, preventDefault} from "UtilityFunctions";
import {SnackType} from "Snackbar/Snackbars";
import ClickableDropdown from "ui-components/ClickableDropdown";

// eslint-disable-next-line no-underscore-dangle
const _List: React.FunctionComponent<DispatchProps & {project?: string}> = props => {
    const [response, setFetchParams] = useCloudAPI<Page<UserInProject>, ListProjectsRequest>(
        listProjects({page: 0, itemsPerPage: 50}),
        emptyPage
    );

    const [creatingProject, setCreatingProject] = React.useState(false);
    const title = React.useRef<HTMLInputElement>(null);

    React.useEffect(() => {
        props.setLoading(response.loading);
    }, [response.loading]);

    const history = useHistory();

    const reload = (): void =>
        setFetchParams(listProjects({page: response.data.pageNumber, itemsPerPage: response.data.itemsPerPage}));

    React.useEffect(() => {
        props.setPageTitle();
    }, []);

    React.useEffect(() => {
        props.setRefresh(reload);
        return () => props.setRefresh();
    }, [reload]);

    const projectOperations: ProjectOperation[] = [{
        text: "Groups",
        disabled: projects => projects.length !== 1 || !projects.every(it => isAdminOrPI(it.whoami.role)),
        icon: "user",
        onClick: ([project]) => {
            if (props.project !== project.projectId) props.setProject(project.projectId);
            snackbarStore.addInformation(`${project.projectId} set as active project.`);
            history.push("/projects/groups/");
        }
    }];

    return (
        <MainContainer
            headerSize={58}
            header={<div />}
            main={(
                <Pagination.List
                    page={response.data}
                    pageRenderer={page => (
                        <List>
                            <ListRow
                                icon={<Box width="24px" />}
                                left={<Text>Personal project</Text>}
                                leftSub={<Text color="gray" fontSize={0}><Icon size="10" name="id" /> {Client.username}</Text>}
                                right={<Icon
                                    mr="20px"
                                    mt="5px"
                                    name="check"
                                    color={!props.project ? "green" : "gray"}
                                    hoverColor="green"
                                    onClick={() => {
                                        if (!props.project) return;
                                        snackbarStore.addInformation(`Personal project is now the active.`);
                                        props.setProject();
                                    }}
                                />}
                            />
                            {creatingProject ?
                                <ListRow
                                    icon={<Icon
                                        cursor="pointer"
                                        size="24"
                                        name={"starEmpty"}
                                        color={"midGray"}
                                        onClick={(): void => {}}
                                        hoverColor="blue"
                                    />}
                                    left={<form onSubmit={createProject}><Input
                                        pt="0px"
                                        pb="0px"
                                        pr="0px"
                                        pl="0px"
                                        noBorder
                                        fontSize={20}
                                        maxLength={1024}
                                        onKeyDown={e => {
                                            if (e.keyCode === KeyCode.ESC) {
                                                setCreatingProject(false);
                                            }
                                        }}
                                        borderRadius="0px"
                                        type="text"
                                        width="100%"
                                        autoFocus
                                        ref={title}
                                    /></form>}
                                    right={<div />}
                                    leftSub={<Text ml="4px" color="gray" fontSize={0}>
                                        <Icon color="white" color2="gray" mt="-2px" size="10" name="user" />
                                        {" "}{projectRoleToString(ProjectRole.PI)}
                                    </Text>}
                                /> : null}
                            {page.items.map(e => {
                                const isSelected = e.projectId === props.project;
                                return (
                                    <ListRow
                                        key={e.projectId}
                                        icon={<Icon
                                            cursor="pointer"
                                            size="24"
                                            name={e.needsVerification ? "starFilled" : "starEmpty"}
                                            color={e.needsVerification ? "blue" : "midGray"}
                                            onClick={(event): void => {}}
                                            hoverColor="blue"
                                        />}
                                        navigate={() => history.push(`/projects/view/${encodeURIComponent(e.projectId)}`)}
                                        left={<Text cursor="pointer">{e.title}</Text>}
                                        leftSub={<>
                                            <Text color="gray" fontSize={0}><Icon size="10" name="id" /> {e.projectId}</Text>
                                            <Text ml="4px" color="gray" fontSize={0}><Icon color="white" color2="gray" mt="-2px" size="10" name="user" />
                                                {" "}{projectRoleToString(e.whoami.role)}
                                            </Text>
                                        </>}
                                        right={<>
                                            <Flex alignItems={"center"}>
                                                {!e.needsVerification ? null : (
                                                    <Text fontSize={0} mr={8}><Icon name={"warning"} /> Attention required</Text>
                                                )}
                                                <Link
                                                    to={`/projects/view/${encodeURIComponent(e.projectId)}`}
                                                >
                                                    <Icon
                                                        cursor="pointer"
                                                        color="black"
                                                        name="projects"
                                                        mr="15px"
                                                    />
                                                </Link>
                                                <Icon
                                                    mr="20px"
                                                    mt="5px"
                                                    name="check"
                                                    color={isSelected ? "green" : "gray"}
                                                    hoverColor="green"
                                                    onClick={() => {
                                                        if (isSelected) return;
                                                        snackbarStore.addInformation(`${e.projectId} is now the active project`);
                                                        props.setProject(e.projectId);
                                                    }}
                                                />
                                                <ClickableDropdown
                                                    width="125px"
                                                    left="-105px"
                                                    trigger={(
                                                        <Icon
                                                            onClick={preventDefault}
                                                            ml="10px"
                                                            mr="10px"
                                                            name="ellipsis"
                                                            size="1em"
                                                            rotation={90}
                                                        />
                                                    )}
                                                >
                                                    <ProjectOperations
                                                        selectedProjects={[e]}
                                                        projectOperations={projectOperations}
                                                    />
                                                </ClickableDropdown>
                                            </Flex>
                                        </>}
                                    />
                                );
                            })}
                        </List>
                    )}
                    loading={response.loading}
                    onPageChanged={newPage => setFetchParams(listProjects({page: newPage, itemsPerPage: 50}))}
                />
            )}
            sidebar={(<>
                <VerticalButtonGroup>
                    <Button onClick={startCreateProject}>Create project</Button>
                    <ProjectOperations selectedProjects={[]} projectOperations={projectOperations} />
                </VerticalButtonGroup>
            </>)}
        />
    );

    function startCreateProject(): void {
        setCreatingProject(true);
    }


    async function createProject(e: React.FormEvent): Promise<void> {
        e.preventDefault();
        if (response.loading) return;
        if (title.current == null) return;
        if (title.current.value === "") {
            snackbarStore.addInformation("Project name can't be empty.");
            return;
        }

        try {
            const res = await Client.post<{id: string}>("/projects", {
                title: title.current.value,
                principalInvestigator: Client.username!
            });

            snackbarStore.addSnack({
                message: "Group created.",
                type: SnackType.Success
            });
            setCreatingProject(false);
            reload();
            props.setProject(res.response.id);
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to create project."));
        }
    }
};

type ProjectOperation = Operation<UserInProject>;

interface ProjectOperations {
    projectOperations: ProjectOperation[];
    selectedProjects: UserInProject[];
}

function ProjectOperations(props: ProjectOperations): JSX.Element | null {
    if (props.projectOperations.length === 0) return null;

    function ProjectOp(op: ProjectOperation): JSX.Element | null {
        if (op.disabled(props.selectedProjects, Client)) return null;
        return <span onClick={() => op.onClick(props.selectedProjects, Client)}>
            <Icon size={16} mr="1em" color={op.color} name={op.icon} />{op.text}</span>;
    }

    return (
        <Flex
            ml="-17px"
            mr="-17px"
            pl="15px">
            {props.projectOperations.map(ProjectOp)}
        </Flex>
    );
}

interface DispatchProps {
    setProject: (id?: string) => void;
    setPageTitle: () => void;
    setRefresh: (refresh?: () => void) => void;
    setLoading: (loading: boolean) => void;
}

const mapStateToProps = (state: ReduxObject): {project?: string} => state.project;

const mapDispatchToProps = (dispatch: Dispatch): DispatchProps => ({
    setPageTitle: () => dispatch(updatePageTitle("Projects")),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    setLoading: loading => dispatch(loadingAction(loading)),
    setProject: id => dispatchSetProjectAction(dispatch, id)
});

export default connect(mapStateToProps, mapDispatchToProps)(_List);
