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
import {Wallet, RetrieveBalanceResponse, retrieveBalance, WalletBalance, setCredits, SetCreditsRequest} from "Accounting/Compute";

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

const WalletDropdown = styled(Box)`
    border: 2px solid var(--borderGray);
    border-radius: 5px;
    padding: 8px;

`;

function AllocateCreditsModal(
    {project}: {project: Project}
): JSX.Element {

    const amountField = React.useRef<HTMLInputElement>(null);
    const [parentProjectBalance, fetchParentProjectBalance] = useCloudAPI<RetrieveBalanceResponse>(
        {noop: true},
        {wallets: []}
    );

    const [subprojectWallets, fetchSubprojectWallets] = useCloudAPI<RetrieveBalanceResponse>(
        {noop: true},
        {wallets: []}
    );

    const [isOpen, setOpen] = React.useState<boolean>(true);
    const [wallets, setWallets] = React.useState<WalletBalance[]>([]);
    const [walletOptions, setWalletOptions] = React.useState<{text: string; value: string}[]>([{text: "No wallets found", value:"undefined"}]);
    const [selectedWallet, setSelectedWallet] = React.useState<WalletBalance|undefined>(undefined);
    const [subprojectWallet, setSubprojectWallet] = React.useState<WalletBalance|undefined>(undefined);
    const [allocatedCredits, setAllocatedCredits] = useCloudAPI<{}, SetCreditsRequest>({ noop: true }, {});

    useEffect(() => {
        if (project.parent === undefined) return;
        fetchParentProjectBalance(retrieveBalance({id: project.parent, type: "PROJECT"}));
    }, [project]);

    useEffect(() => {
        if (parentProjectBalance.data.wallets.length < 1) return;
        setWallets(parentProjectBalance.data.wallets);
        setWalletOptions(parentProjectBalance.data.wallets.map(wallet => (
            {text: wallet.wallet.paysFor.provider + " " + wallet.wallet.paysFor.id + " (Balance: " + wallet.balance + " DKK)", value: wallet.wallet.id}
        )));
    }, [parentProjectBalance.data]);

    useEffect(() => {
        if (isOpen === false) return;
    }, [isOpen]);

    useEffect(() => {
        fetchSubprojectWallets(retrieveBalance({id: project.id, type: "PROJECT"}));


    }, [selectedWallet]);

    useEffect(() => {
        if (selectedWallet !== undefined) {
            setSubprojectWallet(subprojectWallets.data.wallets.find(wallet =>
                wallet.wallet.id === project.id &&
                wallet.wallet.type === "PROJECT" &&
                wallet.wallet.paysFor.id === selectedWallet.wallet.paysFor.id &&
                wallet.wallet.paysFor.provider === selectedWallet.wallet.paysFor.provider
            ));
        }
    }, [subprojectWallets]);

    return (
        <Box>
            <div>
                <Flex alignItems="center">
                    <Heading.h3>
                        <TextSpan color="gray">Set allocated funds for subproject </TextSpan> {project.title}
                    </Heading.h3>
                </Flex>
                <Box mt={16}>
                    <form onSubmit={e => {
                        e.preventDefault();
                        if (selectedWallet === undefined) {
                            snackbarStore.addFailure("No wallet selected", true);
                            return;
                        }

                        const chosenAmount = amountField.current;
                        if (chosenAmount === null) return;
                        if (chosenAmount.value === "") return;

                        if (parseInt(chosenAmount.value, 10) > selectedWallet.balance) {
                            snackbarStore.addFailure("Not enough funds", true);
                            return;
                        }

                        const lastKnownBalance = subprojectWallet?.balance ?? 0;
                        const newBalanceInCredits = parseInt(chosenAmount.value, 10) * 1000000;

                        console.log(newBalanceInCredits);

                        setAllocatedCredits(setCredits({
                            wallet: {
                                id: project.id,
                                type: selectedWallet.wallet.type,
                                paysFor: selectedWallet.wallet.paysFor
                            },
                            lastKnownBalance,
                            newBalance: newBalanceInCredits
                        }));

                        snackbarStore.addSuccess(`Allocated funds for ${project.title} set to ${chosenAmount.value}`, true);
                    }}>
                        <Box>From wallet</Box>
                        <WalletDropdown width="100%" mb={20}>
                            <ClickableDropdown
                                chevron
                                width="450px"
                                onChange={(val) => setSelectedWallet(wallets.find(wallet => wallet.wallet.id === val))}
                                trigger={
                                    <Box as="span" minWidth="450px">
                                        {
                                            selectedWallet !== undefined ?
                                                selectedWallet.wallet.paysFor.provider + " " + selectedWallet.wallet.paysFor.id + " (Balance: " + selectedWallet.balance + " DKK)"
                                            : "Please select a wallet"
                                        }
                                    </Box>
                                }
                                options={walletOptions}
                            />
                        </WalletDropdown>

                        {selectedWallet !== undefined ? (
                            <>
                                <Box>Allocated funds for wallet {selectedWallet.wallet.paysFor.provider + " " + selectedWallet.wallet.paysFor.id}</Box>
                                <Flex alignItems="center" mb={20}>
                                    <Input
                                        mr={10}
                                        required
                                        type="number"
                                        ref={amountField}
                                        placeholder="Amount"
                                        defaultValue={subprojectWallet !== undefined ? subprojectWallet.balance/1000000 : undefined}
                                    />
                                    <TextSpan>DKK</TextSpan>
                                </Flex>
                            </>
                        ) : null}

                        <Flex alignItems="center">
                            <Button color="red" mr="5px" type="button" onClick={() => dialogStore.failure()}>
                                Cancel
                            </Button>

                            <Button
                                color="green"
                                type={"submit"}
                            >
                                Allocate funds
                            </Button>
                        </Flex>
                    </form>
                </Box>
            </div>
        </Box>
    );
}

function openAllocateCreditsModal(subproject: Project): void {
    dialogStore.addDialog(<AllocateCreditsModal project={subproject} />, () => undefined);
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
                                onAllocateCredits={async (subproject) =>
                                    openAllocateCreditsModal(subproject)
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
