import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {MainContainer} from "MainContainer/MainContainer";
import {createProject, listSubprojects, Project, useProjectManagementStatus} from "Project";
import * as React from "react";
import {useCallback, useEffect, useRef, useState} from "react";
import {Absolute, Box, Button, Card, Flex, Icon, Input, Label, Link, Relative, Text} from "ui-components";
import styled from "styled-components";
import {emptyPage} from "DefaultObjects";
import {errorMessageOrDefault, preventDefault} from "UtilityFunctions";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {ProjectBreadcrumbs} from "Project/Breadcrumbs";
import Table, {TableCell, TableRow} from "ui-components/Table";
import * as Pagination from "Pagination";
import * as Heading from "ui-components/Heading";
import {
    isQuotaSupported,
    ProductArea, productCategoryEquals,
    retrieveBalance,
    RetrieveBalanceResponse, retrieveQuota, RetrieveQuotaResponse, setCredits, updateQuota,
    WalletBalance, walletEquals
} from "Accounting";
import {creditFormatter} from "Project/ProjectUsage";
import {DashboardCard} from "Dashboard/Dashboard";
import HexSpin, {HexSpinWrapper} from "LoadingIcon/LoadingIcon";
import {Client} from "Authentication/HttpClientInstance";
import {sizeToString} from "Utilities/FileUtilities";
import {isAdminOrPI} from "Utilities/ProjectUtilities";
import {useTitle} from "Navigation/Redux/StatusActions";
import {useSidebarPage, SidebarPages} from "ui-components/Sidebar";
import {Balance} from "Accounting/Balance";
import {shakeAnimation, shakingClassName} from "UtilityComponents";

const WalletContainer = styled.div`
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(350px, auto));
    grid-gap: 32px;
    
    margin: 32px 0;
    
    ${shakeAnimation}
    
    .request-resources {
        grid-column-start: span 2;
    }
    
    .request-resources ${Card} {
        height: 100%;
    }
`;

const SelectableWalletWrapper = styled.div`
    ${Card} {
        cursor: pointer;
        min-width: 350px;
        transition: all 0.25s ease-out;
        width: 100%;
        height: 100%;
    }
    
    &.selected ${Card} {
        background-color: var(--lightBlue, #f00);
        transform: translate3d(0, -10px, 0);
    }
    
    &:hover ${Card} {
        background-color: var(--lightGray, #f00);
    }
    
    th {
        margin-right: 8px;
        text-align: left;
    }
    
    td {
        text-align: right;
        width: 100%;
    }
    
    table {
        margin: 8px;
    }
`;

const SelectableWallet: React.FunctionComponent<{
    wallet: WalletBalance,
    allocated?: number,
    selected?: boolean,
    onClick?: () => void,
    quotaInBytes?: number
}> = props => {
    return (
        <SelectableWalletWrapper className={props.selected === true ? "selected" : ""} onClick={props.onClick}>
            <DashboardCard color={props.selected ? "green" : "blue"} isLoading={false}>
                <table>
                    <tbody>
                        <tr>
                            <th>Product</th>
                            <td>{props.wallet.wallet.paysFor.provider} / {props.wallet.wallet.paysFor.id}</td>
                        </tr>
                        <tr>
                            <th>Balance</th>
                            <td>
                                <Balance amount={props.wallet.balance} productCategory={props.wallet.wallet.paysFor}/>
                            </td>
                        </tr>
                        {!props.allocated ? null : (
                            <tr>
                                <th>Allocated</th>
                                <td>{creditFormatter(props.allocated)}</td>
                            </tr>
                        )}
                        {props.quotaInBytes === undefined ? null : (
                            <tr>
                                <th>Quota</th>
                                <td>{props.quotaInBytes === -1 ? "No quota" : sizeToString(props.quotaInBytes)}</td>
                            </tr>
                        )}
                        <tr>
                            <th />
                            <td>
                                <Icon
                                    name={props.wallet.area === ProductArea.COMPUTE ? "cpu" : "ftFileSystem"}
                                    size={32}
                                />
                            </td>
                        </tr>
                    </tbody>
                </table>
            </DashboardCard>
        </SelectableWalletWrapper>
    );
};


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

const Subprojects: React.FunctionComponent = () => {
    const newSubprojectRef = React.useRef<HTMLInputElement>(null);
    const [isLoading, runCommand] = useAsyncCommand();

    useTitle("Resources");
    useSidebarPage(SidebarPages.Projects);

    const {
        projectId,
        allowManagement,
        subprojectSearchQuery,
        setSubprojectSearchQuery,
        projectRole,
        reloadProjectStatus
    } = useProjectManagementStatus({isRootComponent: true, allowPersonalProject: true});

    const [quota, fetchQuota] = useCloudAPI<RetrieveQuotaResponse>(
        {noop: true},
        {quotaInBytes: 0}
    );

    const [wallets, setWalletParams] = useCloudAPI<RetrieveBalanceResponse>(
        {noop: true},
        {wallets: []}
    );

    const reloadWallets = useCallback(() => {
        setWalletParams(retrieveBalance({includeChildren: true}));
        fetchQuota(retrieveQuota({path: Client.hasActiveProject ? Client.currentProjectFolder : Client.homeFolder}));
    }, [setWalletParams, projectId]);

    const [selectedWallet, setSelectedWallet] = useState<WalletBalance | null>(null);
    const walletContainer = useRef<HTMLDivElement>(null);
    const shakeWallets = useCallback(() => {
        const container = walletContainer.current;
        if (container) {
            container.classList.add(shakingClassName);
            window.setTimeout(() => {
                container.classList.remove(shakingClassName);
            }, 820);
        }
    }, [walletContainer.current]);

    const projectWallets = wallets.data.wallets.filter(it => it.wallet.id === projectId);
    const personalWallets = wallets.data.wallets.filter(it => !Client.hasActiveProject && it.wallet.id === Client.username);
    const mainWallets = [...projectWallets, ...personalWallets];
    const subprojectWallets = wallets.data.wallets.filter(it =>
        it.wallet.id !== projectId &&
        selectedWallet !== null && productCategoryEquals(it.wallet.paysFor, selectedWallet.wallet.paysFor)
    );

    const [subprojects, setSubprojectParams, subprojectParams] = useCloudAPI<Page<Project>>(
        listSubprojects({itemsPerPage: 50, page: 0}),
        emptyPage
    );

    const reloadSubprojects = (): void => {
        setSubprojectParams({...subprojectParams});
    };

    useEffect(() => {
        reloadSubprojects();
        reloadWallets();
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
            reloadProjectStatus();
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed creating new project"), false);
        }
    };

    return (
        <MainContainer
            header={<ProjectBreadcrumbs allowPersonalProject crumbs={[{title: "Resource Allocation"}]} />}
            sidebar={null}
            main={(
                <>
                    <Heading.h3>Resources</Heading.h3>
                    <LoadingBox height="233px" isLoading={wallets.loading}>
                        <WalletContainer ref={walletContainer}>

                            {mainWallets.map((w, i) =>
                                <SelectableWallet
                                    key={i}
                                    wallet={w}
                                    selected={selectedWallet !== null && walletEquals(selectedWallet.wallet, w.wallet)}
                                    allocated={
                                        wallets.data.wallets.reduce((prev, it) => (
                                            it.wallet.id !== projectId && productCategoryEquals(w.wallet.paysFor, it.wallet.paysFor) ?
                                                prev + it.balance : prev
                                        ), 0)
                                    }
                                    quotaInBytes={isQuotaSupported(w.wallet.paysFor) ?
                                        quota.data.quotaInBytes : undefined
                                    }
                                    onClick={() => setSelectedWallet(w)} />
                            )}

                            {!Client.hasActiveProject || isAdminOrPI(projectRole) ?
                                <div className="request-resources">
                                    <DashboardCard color="blue" isLoading={false}>
                                        <Box m={8} mt={0}>
                                            <Heading.h3>Need more resources?</Heading.h3>
                                            <p>
                                                You can request more resources from your parent project.
                                                Click the button below to get started.
                                        </p>
                                            <Flex justifyContent={"flex-end"}>
                                                <Link to={
                                                    !Client.hasActiveProject ?
                                                        "/projects/browser/personal" :
                                                        "/project/grants/existing"
                                                }><Button>Request resources</Button></Link>
                                            </Flex>
                                        </Box>
                                    </DashboardCard>
                                </div> : null}
                        </WalletContainer>
                    </LoadingBox>

                    {!Client.hasActiveProject ? null : <>
                        <Heading.h3>Subprojects</Heading.h3>
                        <Box className="subprojects" maxWidth={850} ml="auto" mr="auto">
                            <Box ml={8} mr={8}>
                                <SearchContainer>
                                    {!allowManagement ? null : (
                                        <form onSubmit={onSubmit}>
                                            <Input
                                                id="new-project-subproject"
                                                placeholder="Title of new project"
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
                                <Box mt={20}>
                                    <Pagination.List
                                        page={subprojects.data}
                                        pageRenderer={pageRenderer}
                                        loading={subprojects.loading}
                                        onPageChanged={newPage => {
                                            setSubprojectParams(
                                                listSubprojects({
                                                    page: newPage,
                                                    itemsPerPage: 50,
                                                })
                                            );
                                        }}
                                        customEmptyPage={
                                            <Flex justifyContent="center" alignItems="center" height="200px">
                                                This project doesn&apos;t have any subprojects.
                                                {isAdminOrPI(projectRole) ? <>You can create one by using the &apos;Create&apos; button above.</> : ""}
                                            </Flex>
                                        }
                                    />
                                </Box>
                            </Box>
                        </Box>
                    </>}
                </>
            )}
        />
    );

    function pageRenderer(page: Page<Project>): JSX.Element {
        const filteredItems = (subprojectSearchQuery === "" ? page.items :
            page.items.filter(it =>
                it.title.toLowerCase()
                    .search(subprojectSearchQuery.toLowerCase().replace(/\W|_|\*/g, "")) !== -1
            )
        );

        return (
            <Table>
                <tbody>
                    {filteredItems.map(subproject =>
                        <SubprojectRow
                            key={subproject.id}
                            subproject={subproject}
                            shakeWallets={shakeWallets}
                            requestReload={reloadWallets}
                            walletBalance={selectedWallet === null ?
                                undefined :
                                subprojectWallets.find(it => it.wallet.id === subproject.id) ??
                                {...selectedWallet, balance: 0, wallet: {...selectedWallet.wallet, id: subproject.id}}
                            }
                            allowManagement={allowManagement}
                        />
                    )}
                </tbody>
            </Table>
        );
    }
};

const AllocationEditor = styled.div`
    display: flex;
    align-items: center;
    justify-content: flex-end;
    width: 250px;
    margin: 0 16px;

    ${Input} {
        width: 1px;
        flex-grow: 1;
        padding-top: 0;
        padding-bottom: 0;
        text-align: right;
    }

    ${HexSpinWrapper} {
        margin: -12px 0 0 10px;
    }
`;

const AllocationForm = styled.form`
    display: flex;
    align-items: center;
`;

const SubprojectRowWrapper = styled(TableRow)`
    td {
        vertical-align: top;
        white-space: nowrap;
    }

    ${TableCell}.allocation {
        width: 100%;
        vertical-align: middle;
    }
`;

const LoadingBox = (props: React.PropsWithChildren<{height: string; isLoading: boolean}>): JSX.Element => {
    if (props.isLoading) return <Flex alignItems="center" style={{minHeight: props.height}}><HexSpin /></Flex>;
    else return <>{props.children}</>;
};

const SubprojectRow: React.FunctionComponent<{
    subproject: Project,
    walletBalance?: WalletBalance,
    shakeWallets?: () => void,
    requestReload?: () => void,
    allowManagement: boolean
}> = ({subproject, walletBalance, shakeWallets, requestReload, allowManagement}) => {
    const balance = walletBalance?.balance;
    const [isEditing, setIsEditing] = useState<boolean>(false);
    const [isEditingQuota, setIsEditingQuota] = useState<boolean>(false);
    const inputRef = useRef<HTMLInputElement>(null);
    const quotaRef = useRef<HTMLInputElement>(null);
    const [loading, runCommand] = useAsyncCommand();
    const [quota, fetchQuota, quotaParams] = useCloudAPI<RetrieveQuotaResponse>(
        {noop: true},
        {quotaInBytes: 0}
    );
    const quotaInBytes = walletBalance && isQuotaSupported(walletBalance.wallet.paysFor) ?
        quota.data.quotaInBytes : undefined;

    useEffect(() => {
        if (walletBalance && isQuotaSupported(walletBalance.wallet.paysFor)) {
            fetchQuota(retrieveQuota({path: `/projects/${subproject.id}`}));
        }
    }, [subproject.id, walletBalance?.wallet?.paysFor?.id, walletBalance?.wallet?.paysFor?.provider]);

    const onSubmit = useCallback(async (e) => {
        e.preventDefault();
        if (loading) return; // Silently fail
        if (balance === undefined || !walletBalance) {
            snackbarStore.addFailure("UCloud is not ready to set balance (Internal error)", false);
            return;
        }

        const rawValue = inputRef.current!.value;
        const parsedValue = parseInt(rawValue, 10);
        if (isNaN(parsedValue) || parsedValue < 0) {
            snackbarStore.addFailure("Please enter a valid number", false);
            return;
        }
        const valueInCredits = parsedValue * 1000000;
        await runCommand(setCredits({
            lastKnownBalance: balance,
            newBalance: valueInCredits,
            wallet: walletBalance.wallet
        }));

        setIsEditing(false);
        if (requestReload) requestReload();
    }, [setIsEditing, inputRef.current, loading, walletBalance, balance, runCommand]);

    const onSubmitQuota = useCallback(async (e) => {
        e.preventDefault();
        if (loading) return;
        const rawValue = quotaRef.current!.value;
        const parsedValue = parseInt(rawValue, 10);
        if (isNaN(parsedValue) || parsedValue < 0) {
            snackbarStore.addFailure("Please enter a valid number", false);
            return;
        }

        const valueInBytes = parsedValue * 1000000000;
        await runCommand(updateQuota({path: `/projects/${subproject.id}`, quotaInBytes: valueInBytes}));

        setIsEditingQuota(false);
        fetchQuota({...quotaParams, reloadId: Math.random()});
        if (requestReload) requestReload();
    }, [setIsEditingQuota, quotaParams, fetchQuota, quotaRef, runCommand, loading]);

    useEffect(() => {
        if (inputRef.current && isEditing) {
            inputRef.current!.value = ((balance ?? 0) / 1000000).toString();
        }
    }, [inputRef.current, isEditing]);

    useEffect(() => {
        if (quotaRef.current && isEditingQuota) {
            if (quotaInBytes === -1) {
                quotaRef.current!.value = "0";
            } else {
                quotaRef.current!.value = ((quotaInBytes ?? 0) / 1000000000).toString();
            }
        }
    }, [quotaRef.current, isEditingQuota]);

    return <>
        <SubprojectRowWrapper>
            <TableCell>
                <Text>{subproject.title}</Text>
            </TableCell>
            <TableCell className={"allocation"}>
                {!allowManagement ? null : (
                    <>
                        <Flex alignItems={"center"} justifyContent={"flex-end"}>
                            {balance === undefined ? (
                                <>
                                    <Button height="35px" width={"135px"} onClick={() => {
                                        snackbarStore.addInformation("You must select a resource above", false);
                                        if (shakeWallets) shakeWallets();
                                    }}>
                                        Edit allocation
                                    </Button>
                                </>
                            ) : (
                                    <>
                                        {!isEditing ? (
                                            <>
                                                <AllocationEditor>
                                                    {creditFormatter(balance, 0)}
                                                </AllocationEditor>
                                                <Button height="35px" width={"135px"} onClick={() => setIsEditing(true)}>
                                                    Edit allocation
                                                </Button>
                                            </>
                                        ) : (
                                                <AllocationForm onSubmit={onSubmit}>
                                                    <AllocationEditor>
                                                        <Input ref={inputRef} noBorder autoFocus />
                                                        <span>DKK</span>
                                                        {loading ?
                                                            <HexSpin size={16} />
                                                            :
                                                            <Icon
                                                                size={16}
                                                                ml="10px"
                                                                cursor="pointer"
                                                                name="close"
                                                                color="red"
                                                                onClick={() => setIsEditing(false)}
                                                            />
                                                        }
                                                    </AllocationEditor>

                                                    <Button type="submit" height="35px" width="135px" color="green"
                                                        disabled={loading}>
                                                        Allocate
                                                    </Button>
                                                </AllocationForm>
                                            )
                                        }
                                    </>
                                )}
                        </Flex>

                        {quotaInBytes === undefined ? null : (
                            <Flex alignItems={"center"} justifyContent={"flex-end"} mt={16}>
                                {!isEditingQuota ? (
                                    <>
                                        <AllocationEditor>
                                            {quotaInBytes === -1 ? "No quota" : sizeToString(quotaInBytes)}
                                        </AllocationEditor>
                                        <Button height="35px" width="135px" onClick={() => setIsEditingQuota(true)}>
                                            Edit quota
                                        </Button>
                                    </>
                                ) : (
                                        <AllocationForm onSubmit={onSubmitQuota}>
                                            <AllocationEditor>
                                                <Input ref={quotaRef} noBorder autoFocus />
                                                <span>GB</span>
                                                {loading ?
                                                    <HexSpin size={16} />
                                                    :
                                                    <Icon
                                                        size={16}
                                                        ml="10px"
                                                        cursor="pointer"
                                                        name="close"
                                                        color="red"
                                                        onClick={() => setIsEditingQuota(false)}
                                                    />
                                                }
                                            </AllocationEditor>

                                            <Button type="submit" height="35px" width="135px" color="green"
                                                disabled={loading}>
                                                Allocate
                                            </Button>
                                        </AllocationForm>
                                    )}
                            </Flex>
                        )}
                    </>
                )}
            </TableCell>
        </SubprojectRowWrapper>
    </>;
};

export default Subprojects;
