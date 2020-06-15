import {useCloudAPI, useAsyncCommand} from "Authentication/DataHook";
import {MainContainer} from "MainContainer/MainContainer";
import {
    UserInProject,
    createProject,
    listSubprojects,
    useProjectManagementStatus,
    deleteProject,
    Project,
} from "Project/index";
import * as React from "react";
import {useEffect} from "react";
import {Box, Button, Flex, Icon, Input, Relative, Absolute, Label, theme} from "ui-components";
import * as Heading from "ui-components/Heading";
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
import {TextSpan} from "ui-components/Text";
import {InputLabel} from "ui-components/Input";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {dialogStore} from "Dialog/DialogStore";
import {Wallet, RetrieveBalanceResponse, retrieveBalance, WalletBalance} from "Accounting/Compute";

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

function AssignCreditsModal(
    {project}: {project: Project}
): JSX.Element {

    const amountField = React.useRef<HTMLInputElement>(null);
    const [parentProjectBalance, fetchParentProjectBalance] = useCloudAPI<RetrieveBalanceResponse>(
        {noop: true},
        {wallets: []}
    );

    const [isOpen, setOpen] = React.useState<boolean>(true);
    const [wallets, setWallets] = React.useState<WalletBalance[]>([]);
    const [walletOptions, setWalletOptions] = React.useState<{text: string; value: string}[]>([{text: "Undefined wallet", value:"undefined"}]);
    const [selectedWallet, setSelectedWallet] = React.useState<WalletBalance|undefined>(undefined);

    useEffect(() => {
        if (project.parent === undefined) return;
        fetchParentProjectBalance(retrieveBalance({id: project.parent, type: "PROJECT"}));

    }, [project]);

    useEffect(() => {
        if (parentProjectBalance.data.wallets.length < 1) return;
        setWallets(parentProjectBalance.data.wallets);
        setWalletOptions(parentProjectBalance.data.wallets.map(wallet => (
            {text: wallet.category.id + " - " + wallet.category.provider + " (Balance: " + wallet.balance + " DKK)", value: wallet.category.provider+wallet.category.id}
        )));
    }, [parentProjectBalance.data]);

    useEffect(() => {
        if (isOpen === false) return;
    }, [isOpen]);

        return (
        <Box>
            <div>
                <Flex alignItems="center">
                    <Heading.h3>
                        <TextSpan color="gray">Assign credits to </TextSpan> {project.title}
                    </Heading.h3>
                </Flex>
                <Box mt={16}>
                    <form>
                            <Box>From wallet</Box>
                            <InputLabel width={450} mb={20}>
                                <ClickableDropdown
                                    chevron
                                    width="450px"
                                    onChange={(val) => setSelectedWallet(wallets.find(wallet => wallet.category.provider+wallet.category.id === val))}
                                    trigger={
                                        <Box as="span" minWidth="450px" color="gray">
                                            {
                                                selectedWallet !== undefined ?
                                                    selectedWallet.category.provider + " " + selectedWallet.category.id + " (Balance: " + selectedWallet.balance + ")"
                                                : "No wallet selected"
                                            }
                                        </Box>
                                    }
                                    options={walletOptions}
                                />
                            </InputLabel>

                            <Box>Amount</Box>
                            <Flex alignItems="center" mb={20}>
                                <Input
                                    mr={10}
                                    required
                                    type="text"
                                    ref={amountField}
                                    placeholder="Amount"
                                />
                                <TextSpan>DKK</TextSpan>
                            </Flex>

                            <Flex alignItems="center">
                                <Button color="red" mr="5px" onClick={() => {setOpen(false)}}>
                                    Cancel
                                </Button>

                                <Button
                                    color="green"
                                    type={"submit"}
                                >
                                    Assign credits
                                </Button>
                            </Flex>
                    </form>
                </Box>
                {/*accessList.length > 0 ? (
                    <Box maxHeight="80vh">
                        <Table width="700px">
                            <LeftAlignedTableHeader>
                                <TableRow>
                                    <TableHeaderCell width={150}>Type</TableHeaderCell>
                                    <TableHeaderCell width={500}>Name</TableHeaderCell>
                                    <TableHeaderCell width={200}>Permission</TableHeaderCell>
                                    <TableHeaderCell width={50}>Delete</TableHeaderCell>
                                </TableRow>
                            </LeftAlignedTableHeader>
                            <tbody>
                                {accessList.map((accessEntry, index) => (
                                    <TableRow key={index}>
                                        <TableCell>
                                            {accessEntry.entity.user ? (
                                                prettifyEntityType(UserEntityType.USER)
                                            ) : (
                                                    prettifyEntityType(UserEntityType.PROJECT_GROUP)
                                                )}
                                        </TableCell>
                                        <TableCell>
                                            {accessEntry.entity.user ? (
                                                accessEntry.entity.user
                                            ) : (
                                                    accessEntry.entity.project + " " + accessEntry.entity.group
                                                )}
                                        </TableCell>
                                        <TableCell>{prettifyAccessRight(accessEntry.permission)}</TableCell>
                                        <TableCell textAlign="right">
                                            <Button
                                                color="red"
                                                type="button"
                                                paddingLeft={10}
                                                paddingRight={10}
                                                onClick={() => setAccessEntryToDelete(accessEntry)}
                                            >
                                                <Icon size={16} name="trash" />
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </tbody>
                        </Table>
                    </Box>
                                            ) : <Text textAlign="center">No access entries found</Text>*/}
            </div>
        </Box>
    );
}

function openAssignCreditsModal(subproject: Project): void {
    dialogStore.addDialog(<AssignCreditsModal project={subproject} />, () => undefined);
}



const Subprojects: React.FunctionComponent<SubprojectsOperations> = () => {
    const newSubprojectRef = React.useRef<HTMLInputElement>(null);
    const [isLoading, runCommand] = useAsyncCommand();

    const {
        projectId,
        allowManagement,
        subprojectSearchQuery,
        setSubprojectSearchQuery
    } = useProjectManagementStatus();

    const [subprojects, setSubprojectParams, subprojectParams] = useCloudAPI<Page<Project>>(
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
                                onAssignCredits={async (subproject) => 
                                    openAssignCreditsModal(subproject)
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
