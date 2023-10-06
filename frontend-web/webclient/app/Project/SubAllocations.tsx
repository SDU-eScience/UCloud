import * as React from "react";
import styled from "styled-components";
import {Box, Button, ButtonGroup, Flex, Grid, Icon, Input, Label, Relative, Text, TextArea, Tooltip, Truncate} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {
    ChargeType,
    deposit,
    DepositToWalletRequestItem,
    explainAllocation, normalizeBalanceForBackend,
    normalizeBalanceForFrontend, ProductPriceUnit, ProductType,
    productTypes,
    productTypeToIcon,
    productTypeToTitle,
    retrieveRecipient,
    RetrieveRecipientResponse,
    updateAllocation,
    UpdateAllocationRequestItem,
    Wallet,
    WalletAllocation
} from "@/Accounting";
import {APICallState, callAPI, useCloudCommand} from "@/Authentication/DataHook";
import {PageV2} from "@/UCloud";
import {MutableRefObject, useCallback, useMemo, useRef, useState} from "react";
import {displayErrorMessageOrDefault, errorMessageOrDefault, stopPropagationAndPreventDefault} from "@/UtilityFunctions";
import {bulkRequestOf} from "@/DefaultObjects";
import {Accordion} from "@/ui-components/Accordion";
import {Spacer} from "@/ui-components/Spacer";
import format from "date-fns/format";
import {ListRow, ListRowStat} from "@/ui-components/List";
import {dialogStore} from "@/Dialog/DialogStore";
import {DatePicker} from "@/ui-components/DatePicker";
import {InputLabel} from "@/ui-components/Input";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {ListV2} from "@/Pagination";
import {AllocationViewer, resultAsPercent, SubAllocation, allocationText, percentageString} from "./Allocations";
import {ResourceProgress} from "@/ui-components/ResourcesProgress";
import {TextSpan} from "@/ui-components/Text";
import startOfDay from "date-fns/esm/startOfDay";
import ProjectAPI, {useProjectIdFromParams} from "@/Project/Api";
import {isAllocationSuitableForSubAllocation} from "@/Grants";
import {getProviderTitle, ProviderTitle} from "@/Providers/ProviderTitle";

function titleForSubAllocation(alloc: SubAllocation): string {
    return rawAllocationTitleInRow(alloc.productCategoryId.name, alloc.productCategoryId.provider) + ` [${getParentAllocationFromSuballocation(alloc)}]`;
}

function getParentAllocationFromSuballocation(alloc: SubAllocation): string {
    return alloc.path.split(".").at(-2) ?? "";
}

function rawAllocationTitleInRow(category: string, provider: string) {
    return `${category} @ ${getProviderTitle(provider)}`;
}

export const SubAllocationViewer: React.FunctionComponent<{
    wallets: APICallState<PageV2<Wallet>>;
    allocations: APICallState<PageV2<SubAllocation>>;
    generation: number;
    loadMore: () => void;
    onQuery: (query: string) => void;
    filterByAllocation: (allocationId: string) => void;
    filterByWorkspace: (workspaceId: string, isProject: boolean) => void;
}> = ({allocations, generation, loadMore, wallets, onQuery}) => {
    const reload = useCallback(() => {
        const query = document.querySelector<HTMLInputElement>("#resource-search");
        if (query != null) {
            onQuery(query.value);
        }
    }, [onQuery]);

    const onSearchInput = useCallback((ev: React.KeyboardEvent) => {
        if (ev.code === "Enter" || ev.code === "NumpadEnter") {
            const target = ev.target as HTMLInputElement;
            ev.preventDefault();
            onQuery(target.value);
        }
    }, [onQuery]);

    return <>
        <Spacer
            mt="32px"
            mr="15px"
            left={<Heading.h3>Sub-allocations</Heading.h3>}
            right={
                <Flex>
                    <Input id={"resource-search"} placeholder={"Search in allocations..."} onKeyDown={onSearchInput} />
                    <Relative right="30px" top="9px">
                        <Label htmlFor={"resource-search"}>
                            <Icon mr="-25px" name={"search"} size={"20px"} />
                        </Label>
                    </Relative>
                </Flex>
            }
        />

        <Text color="darkGray" fontSize={1} mb={"16px"}>
            An overview of workspaces which have received a <i>grant</i> or a <i>deposit</i> from you
        </Text>

        <NewRecipients wallets={wallets.data.items} reload={reload} />

        <ListV2
            infiniteScrollGeneration={generation}
            page={allocations.data}
            pageRenderer={page => <SuballocationRows rows={page} wallets={wallets.data.items} reload={reload} />}
            loading={allocations.loading || wallets.loading}
            onLoadMore={loadMore}
        />
    </>;
};

interface Recipient {
    id: number;
    ref: React.RefObject<HTMLInputElement>;
    isProject: boolean;
    suballocations: SuballocationCreationRow[];
    asNewProject: boolean;
}

function NewRecipients({wallets, ...props}: {wallets: Wallet[]; reload(): void;}): JSX.Element {
    const [newRecipients, setRecipients] = useState<Recipient[]>([]);
    const projectId = useProjectIdFromParams();

    const newRecipientId = useRef(0);

    /* Duplicate, so todo. (Move up?) */
    const allocationsByProductTypes = useMemo((): {
        [key: string]: {
            wallet: Wallet;
            allocations: WalletAllocation[];
        }[]
    } => ({
        ["COMPUTE" as ProductType]: findValidAllocations(wallets, "COMPUTE"),
        ["STORAGE" as ProductType]: findValidAllocations(wallets, "STORAGE"),
        ["INGRESS" as ProductType]: findValidAllocations(wallets, "INGRESS"),
        ["LICENSE" as ProductType]: findValidAllocations(wallets, "LICENSE"),
        ["NETWORK_IP" as ProductType]: findValidAllocations(wallets, "NETWORK_IP")
    }), [wallets]);

    const toggleIsProject = useCallback((id: number, isProject: boolean) => {
        const entry = newRecipients.find(it => it.id === id);
        if (!entry) return;
        entry.isProject = isProject;
        entry.asNewProject = false;
        return setRecipients([...newRecipients]);
    }, [newRecipients]);

    const toggleAsNewProject = useCallback((id: number) => {
        const entry = newRecipients.find(it => it.id === id);
        if (!entry) return;
        entry.isProject = true;
        entry.asNewProject = true;
        return setRecipients([...newRecipients]);
    }, [newRecipients]);

    const addRecipientRow = useCallback(() => {
        const firstProductTypeWithEntries = productTypes.find(it =>
            hasValidAllocations(allocationsByProductTypes[it])
        );
        if (!firstProductTypeWithEntries) {
            snackbarStore.addFailure("No allocations to get resources from.", false);
            return;
        }
        const internalEntryToUse = allocationsByProductTypes[firstProductTypeWithEntries]
            .findIndex(it => it.allocations.length > 0);

        setRecipients(existing => {
            existing.push({
                id: newRecipientId.current++,
                ref: React.createRef<HTMLInputElement>(),
                isProject: true,
                asNewProject: false,
                suballocations: [{
                    id: newRecipientId.current++,
                    productType: firstProductTypeWithEntries,
                    amount: undefined,
                    endDate: undefined,
                    startDate: startOfDay(new Date()).getTime(),
                    wallet: allocationsByProductTypes[firstProductTypeWithEntries][internalEntryToUse].wallet,
                    allocationId: allocationsByProductTypes[firstProductTypeWithEntries][internalEntryToUse].allocations[0].id,
                }],
            });
            return [...existing];
        });
    }, [wallets, allocationsByProductTypes]);

    const removeNewRecipientRow = useCallback((id: number) => {
        setRecipients(existing => {
            return [...existing.filter(it => it.id !== id)];
        });
    }, []);

    const addSubAllocation = useCallback((id: number) => {
        const recipient = newRecipients.find(it => it.id === id);
        if (!recipient) {
            snackbarStore.addFailure("Something went very wrong.", false);
            return newRecipients;
        }
        const firstProductTypeWithEntries = productTypes.find(it =>
            hasValidAllocations(allocationsByProductTypes[it])
        );

        if (!firstProductTypeWithEntries) {
            snackbarStore.addFailure("No available allocations to use", false);
            return newRecipients;
        }

        const internalEntryToUse = allocationsByProductTypes[firstProductTypeWithEntries]
            .findIndex(it => it.allocations.length > 0);

        const recipientIndex = newRecipients.findIndex(it => it.id === id);
        newRecipients[recipientIndex].suballocations.push({
            id: newRecipientId.current++,
            productType: firstProductTypeWithEntries,
            amount: undefined,
            endDate: undefined,
            startDate: startOfDay(new Date()).getTime(),
            wallet: allocationsByProductTypes[firstProductTypeWithEntries][internalEntryToUse].wallet,
            allocationId: allocationsByProductTypes[firstProductTypeWithEntries][0].allocations[0].id,
        });
        return setRecipients([...newRecipients]);
    }, [newRecipients, allocationsByProductTypes]);

    const removeSubAllocation = useCallback((recipientId: number, suballocationId: number) => {
        const index = newRecipients.findIndex(it => it.id === recipientId);
        if (index !== -1) {
            newRecipients[index].suballocations = newRecipients[index].suballocations.filter(it => it.id !== suballocationId);
            return setRecipients([...newRecipients])
        }
    }, [newRecipients]);

    const addRowButtonEnabled = React.useMemo(() => {
        for (const pt of productTypes) {
            if (hasValidAllocations(allocationsByProductTypes[pt])) {
                return true;
            }
        }
        return false;
    }, [allocationsByProductTypes]);

    const [loading, invokeCommand] = useCloudCommand();

    const selectAllocation = React.useCallback((allocationAndWallets: {
        wallet: Wallet;
        allocations: WalletAllocation[];
    }[], recipientId: number, allocationId: number) => {
        dialogStore.addDialog(
            <Box maxWidth={largeModalStyle.content.maxWidth} width="830px">
                <Heading.h3>Available Allocations</Heading.h3>
                <Grid gridTemplateColumns={`repeat(2, 1fr)`} gridGap="15px">
                    {allocationAndWallets.flatMap(it => it.allocations.map(allocation =>
                        <Box key={allocation.allocationPath} cursor="pointer" onClick={() => {
                            setRecipients(recipients => {
                                recipients[recipientId].suballocations[allocationId].wallet = it.wallet;
                                recipients[recipientId].suballocations[allocationId].allocationId = allocation.id;
                                return [...recipients];
                            });
                            dialogStore.success();
                        }}>
                            <AllocationViewer allocation={allocation} wallet={it.wallet} simple={false} />
                        </Box>
                    ))}
                </Grid>
            </Box>, () => undefined, false, largeModalStyle);
    }, []);

    const submitRows = useCallback(async (recipient: Recipient) => {
        const mappedRows: DepositToWalletRequestItem[] = [];

        if (recipient.suballocations.length === 0) {
            snackbarStore.addFailure("No sub-allocations to submit.", false);
            return;
        }

        const recipientId = recipient.ref.current?.value;
        if (!recipientId) {
            snackbarStore.addFailure("Recipient can't be empty.", false);
            return;
        }

        var recipientTitle = recipientId;

        try {
            if (recipient.asNewProject) {
                const [result] = (await invokeCommand(ProjectAPI.create(bulkRequestOf({
                    title: recipientId,
                    parent: projectId
                })), {defaultErrorHandler: false})).responses;
                recipientTitle = result.id;
            } else {
                const result = await invokeCommand<RetrieveRecipientResponse>(retrieveRecipient({query: recipientId}), {defaultErrorHandler: false});
                if (result == null) return;
                if (recipient.isProject != result.isProject) {
                    if (recipient.isProject) {
                        snackbarStore.addFailure("Recipient entered as a project, but is a user.", false);
                    } else { // !recipient.isProject
                        snackbarStore.addFailure("Recipient entered as a user, but is a project.", false);
                    }
                    return;
                }
                recipientTitle = result.id;
            }
        } catch (err) {
            if (err?.request?.status === 404) {
                if (recipient.isProject) {
                    snackbarStore.addFailure("Could not find project. Did you provide the full path?", false);
                } else {
                    snackbarStore.addFailure("Could not find the user. Did you provide the full user id?", false);
                }
            } else {
                displayErrorMessageOrDefault(err, "Failed to find project/user");
            }
            return;
        }

        for (const [index, row] of recipient.suballocations.entries()) {
            if (row.allocationId == "") {
                /* NOTE(Jonas): Should not be possible, but let's make sure it's handled. */
                snackbarStore.addFailure(`Row #${index + 1} is missing an allocation selection`, false);
                return;
            }

            if (row.amount == null || isNaN(row.amount)) {
                snackbarStore.addFailure(`Row #${index + 1} is missing an amount`, false);
                return;
            } else if (row.amount < 1) {
                snackbarStore.addFailure(`Row #${index + 1}'s amount must be more than 0.`, false);
                return;
            }

            mappedRows.push({
                amount: row.amount,
                description: "",
                recipient: recipient.isProject ? {type: "project", projectId: recipientTitle} : {type: "user", username: recipientTitle},
                sourceAllocation: row.allocationId,
                startDate: row.startDate,
                endDate: row.endDate,
            });
        }

        if (mappedRows.length === 0) {
            snackbarStore.addFailure("No sub-allocations to submit. Shouldn't happen.", false);
        }

        let reason = "";

        dialogStore.addDialog(<Box>
            <Heading.h3>Reason for sub-allocations</Heading.h3>
            <Box height="290px" mb="10px">
                <TextArea
                    placeholder="Explain the reasoning for the new suballocation..."
                    style={{height: "100%"}}
                    width="100%"
                    onChange={e => reason = e.target.value}
                />
            </Box>
            <ButtonGroup>
                <Button onClick={async () => {
                    if (!reason) {
                        snackbarStore.addFailure("Reason can't be empty", false);
                        return;
                    }
                    mappedRows.forEach(it => it.description = reason);
                    try {
                        await invokeCommand(deposit(bulkRequestOf(...mappedRows)), {defaultErrorHandler: false});
                        removeNewRecipientRow(recipient.id);
                        props.reload();
                        dialogStore.success();
                        snackbarStore.addSuccess("Sub-allocations added.", false);
                    } catch (e) {
                        displayErrorMessageOrDefault(e, "Failed to submit rows");
                    }
                }} color="green">Confirm</Button>
                <Button color="red" onClick={() => dialogStore.failure()}>Cancel</Button></ButtonGroup>
        </Box>, () => undefined);
    }, [projectId]);

    return <>
        <Spacer left={null} right={
            addRowButtonEnabled ? <Button onClick={addRecipientRow}>New recipient</Button> : <Tooltip trigger={<Button type="button" disabled={!addRowButtonEnabled}>New recipient</Button>}>
                No allocations to allocate from found.
            </Tooltip>}
        />

        {newRecipients.map(recipient =>
            <Accordion
                key={recipient.id}
                iconColor2="white"
                title={<Flex>
                    <Flex width="170px" ml="8px" mt="7px">
                        <ClickableDropdown useMousePositioning width="250px" chevron trigger={<><Icon mr="4px" color2="white" name={recipient.isProject ? "projects" : "user"} />{!recipient.isProject ? "User" : recipient.asNewProject ? "New" : "Existing"}</>}>
                            <Flex onClick={() => toggleIsProject(recipient.id, true)}><Icon mt="2px" mr="12px" size="18px" color2="white" name={"projects"} /> Existing project</Flex>
                            <Flex onClick={() => toggleAsNewProject(recipient.id)}><Icon mt="2px" mr="12px" size="18px" color2="white" name={"projects"} /> New project</Flex>
                            <Flex onClick={() => toggleIsProject(recipient.id, false)}><Icon mt="2px" mr="12px" size="18px" color2="white" name={"user"} /> User</Flex>
                        </ClickableDropdown>
                    </Flex>
                    <Input ml="10px" ref={recipient.ref} placeholder={recipient.isProject ? "Projectname..." : "Username..."} />
                </Flex>}
                titleContent={
                    <Button ml="8px" mt="4px" height="32px" color="red" onClick={() => removeNewRecipientRow(recipient.id)}>Discard</Button>
                }
                forceOpen
                omitChevron
            >
                <Box px="12px">
                    <Spacer my="4px"
                        right={<>
                            <Button ml="8px" mt="2px" height="32px" onClick={() => addSubAllocation(recipient.id)}>Add row</Button>
                            {recipient.suballocations.length === 0 ? null : <Button ml="8px" mt="2px" disabled={loading || !addRowButtonEnabled} height="32px" onClick={() => submitRows(recipient)}>Submit new allocations</Button>}
                        </>}
                        left={null}
                    />
                    {recipient.suballocations.map(row => {
                        const productAndProvider = row.wallet ? <Flex>{row.wallet.paysFor.name} @ <ProviderTitle providerId={row.wallet.paysFor.provider} /><TextSpan pl="0.3em" title="Allocation ID">[{row.allocationId}]</TextSpan></Flex> : null;
                        const remainingProductTypes = productTypes.filter(it => it !== row.productType);
                        const recipientId = newRecipients.findIndex(it => it.id === recipient.id);
                        const suballocationId = newRecipients[recipientId].suballocations.findIndex(it => it.id === row.id);
                        return (
                            <ListRow
                                key={row.id}
                                icon={<Box pl="20px">
                                    <ClickableDropdown width="190px" useMousePositioning chevron trigger={<Icon name={productTypeToIcon(row.productType)} />}>
                                        {remainingProductTypes.map(pt => {
                                            const allowProductSelect = hasValidAllocations(allocationsByProductTypes[pt]);
                                            return allowProductSelect ?
                                                <Flex height="32px" key={pt} color="text" style={{alignItems: "center"}} onClick={() => {
                                                    const internalEntryToUse = allocationsByProductTypes[pt]
                                                        .findIndex(it => it.allocations.length > 0);

                                                    const {wallet, allocations} = allocationsByProductTypes[pt][internalEntryToUse];
                                                    newRecipients[recipientId].suballocations[suballocationId].productType = pt;
                                                    newRecipients[recipientId].suballocations[suballocationId].wallet = wallet;
                                                    newRecipients[recipientId].suballocations[suballocationId].allocationId = allocations[0].id;
                                                    setRecipients([...newRecipients]);
                                                }}>
                                                    <Icon my="4px" mr="8px" name={productTypeToIcon(pt)} />{productTypeToTitle(pt)}
                                                </Flex> :
                                                <Tooltip key={pt} trigger={<Flex height="32px" style={{alignItems: "center"}} color="gray" onClick={stopPropagationAndPreventDefault}>
                                                    <Icon mr="8px" name={productTypeToIcon(pt)} />{productTypeToTitle(pt)}</Flex>}>
                                                    No allocations for product type available
                                                </Tooltip>
                                        })}
                                    </ClickableDropdown>
                                </Box>}
                                left={<Flex>
                                    <Flex cursor="pointer" onClick={() => selectAllocation(allocationsByProductTypes[row.productType], recipientId, suballocationId)}>
                                        {productAndProvider}<Icon name="chevronDownLight" mt="5px" size="1em" ml=".7em" color={"darkGray"} />
                                    </Flex>
                                    <Flex color="var(--gray)" ml="12px">
                                        <DatePicker
                                            selectsRange
                                            fontSize="18px"
                                            py="0"
                                            dateFormat="dd/MM/yy"
                                            width="215px"
                                            startDate={new Date(row.startDate)}
                                            isClearable={row.endDate != null}
                                            endDate={row.endDate != null ? new Date(row.endDate) : undefined}
                                            onChange={(dates: [Date | null, Date | null]) => {
                                                setRecipients(recipients => {
                                                    if (dates[0]) recipients[recipientId].suballocations[suballocationId].startDate = dates[0].getTime();
                                                    recipients[recipientId].suballocations[suballocationId].endDate = dates[1]?.getTime();
                                                    return [...recipients];
                                                });
                                            }}
                                        />
                                    </Flex>
                                </Flex>}
                                right={row.wallet == null ? null : <Flex width={flexRequiresMoreSpace(row.productType) ? "330px" : "280px"}>
                                    <Input
                                        width="100%"
                                        type="number"
                                        rightLabel
                                        min={0}
                                        placeholder="Amount..."
                                        onChange={e => newRecipients[recipientId].suballocations[suballocationId].amount = normalizeBalanceForBackend(parseInt(e.target.value, 10), row.productType, row.wallet!.chargeType, row.wallet!.unit)}
                                    />
                                    <InputLabel textAlign="center" width={labelSpace(row.wallet)} rightLabel>{explainSubAllocation(row.wallet)}</InputLabel>
                                    <Icon mt="9px" ml="12px" name="close" color="red" cursor="pointer" onClick={() => removeSubAllocation(recipient.id, row.id)} />
                                </Flex>}
                            />);
                    })}
                </Box>
            </Accordion>
        )}
    </>;
}

function flexRequiresMoreSpace(type: ProductType): boolean {
    if (["NETWORK_IP", "INGRESS", "LICENSE"].includes(type)) return true;
    return false;
}

function labelSpace(wallet?: {productType: ProductType, unit: ProductPriceUnit}): string {
    if (!wallet) return "0px";
    const PADDING = "20px";
    return `calc(${explainAllocation(wallet.productType, wallet.unit).length * 3}em + ${PADDING})`;
}

function SuballocationRows(props: {
    rows: SubAllocation[];
    reload(): void;
    wallets: Wallet[];
}): JSX.Element {
    const [rows, setRows] = useState(props.rows);

    React.useEffect(() => {
        if (props.rows.length > 0) {
            setRows(props.rows);
        }
    }, [props.rows]);

    return React.useMemo(() => {
        const groupedByName: {[key: string]: SubAllocation[]} = {};
        rows.forEach(row => {
            if (groupedByName[row.workspaceTitle]) {
                groupedByName[row.workspaceTitle].push(row);
            } else {
                groupedByName[row.workspaceTitle] = [row];
            }
        });

        const keys = Object.keys(groupedByName);

        return <Box>
            {keys.map((key, index) => <SuballocationGroup key={key} isLast={keys.length - 1 === index} entryKey={key} rows={groupedByName[key]} wallets={props.wallets} reload={props.reload} />)}
        </Box>
    }, [rows, props.wallets, props.reload]);
}

function findChangedAndMapToRequest(oldAllocs: SubAllocation[], newAllocs: SubAllocation[]): APICallParameters | null {
    const updated: UpdateAllocationRequestItem[] = [];
    for (const [index, oldEntry] of oldAllocs.entries()) {
        const newEntry = newAllocs[index];
        if (isNaN(newEntry.remaining)) newEntry.remaining = oldEntry.remaining;
        if (!isEqual(oldEntry, newEntry)) {
            updated.push({
                id: newEntry.id,
                balance: oldEntry.chargeType === "ABSOLUTE" ? newEntry.remaining : newEntry.initialBalance,
                startDate: newEntry.startDate,
                endDate: newEntry.endDate ?? undefined,
                reason: "Allocation updated by grant giver"
            });
        }
    }
    if (updated.length === 0) return null;
    return updateAllocation(bulkRequestOf(...updated));
}

function isEqual(oldAlloc: SubAllocation, newAlloc: SubAllocation): boolean {
    if (oldAlloc.startDate != newAlloc.startDate) return false;
    if (oldAlloc.endDate != newAlloc.endDate) return false;
    if (oldAlloc.chargeType === "ABSOLUTE") {
        if (oldAlloc.remaining != newAlloc.remaining) return false;
    } else if (oldAlloc.chargeType === "DIFFERENTIAL_QUOTA") {
        if (oldAlloc.initialBalance != newAlloc.initialBalance) return false;
    }
    return true;
}


interface SuballocationCreationRow {
    id: number;
    productType: ProductType;
    startDate: number;
    endDate?: number;
    amount?: number;
    wallet?: Wallet;
    allocationId: string;
}

interface InitialAndRemainingBalanceWithExplanation {
    initialBalance: number;
    remaining: number;
    asPercent: number;
    resourceText: string;
}

interface EntriesByUnitAndChargeType {
    ABSOLUTE: InitialAndRemainingBalanceWithExplanation[];
    DIFFERENTIAL_QUOTA: InitialAndRemainingBalanceWithExplanation[];
}

export function mapToBalancesWithExplanation(
    entry: {remaining: number; initialBalance: number;},
    productType: ProductType,
    unit: ProductPriceUnit
): InitialAndRemainingBalanceWithExplanation {
    const doTruncate = shouldTruncate(entry.initialBalance);
    const initialBalance = Math.floor(doTruncate ? entry.initialBalance / 1_000 : entry.initialBalance);
    const remaining = Math.floor(doTruncate ? entry.remaining / 1_000 : entry.remaining);
    const used = initialBalance - Math.min(initialBalance, remaining);
    return {
        initialBalance,
        remaining,
        asPercent: resultAsPercent({
            balance: remaining,
            initialBalance
        }),
        resourceText: normalizeBalanceForFrontend(
            used,
            productType,
            unit,
            2
        ) + " / " + normalizeBalanceForFrontend(
            initialBalance,
            productType,
            unit,
            2
        ) + " " + allocationText(unit, productType, doTruncate) + percentageString({
            balance: remaining,
            initialBalance
        })
    };
}

function entriesByUnitAndChargeType(suballocations: SubAllocation[], productType: ProductType): EntriesByUnitAndChargeType {
    const byUnitAndChargeType = {ABSOLUTE: {}, DIFFERENTIAL_QUOTA: {}};
    for (const entry of suballocations) {
        const remaining = entry.remaining;
        const initialBalance = entry.initialBalance;
        if (byUnitAndChargeType[entry.chargeType][entry.unit] == null) {
            byUnitAndChargeType[entry.chargeType][entry.unit] = {
                remaining,
                initialBalance
            };
        } else {
            byUnitAndChargeType[entry.chargeType][entry.unit].initialBalance += initialBalance;
            byUnitAndChargeType[entry.chargeType][entry.unit].remaining += remaining;
        }
    }

    const absolute = Object.keys(byUnitAndChargeType.ABSOLUTE).map((it: ProductPriceUnit) => {
        return mapToBalancesWithExplanation(byUnitAndChargeType.ABSOLUTE[it], productType, it);
    });

    const differential = Object.keys(byUnitAndChargeType.DIFFERENTIAL_QUOTA).map((it: ProductPriceUnit) => {
        return mapToBalancesWithExplanation(byUnitAndChargeType.DIFFERENTIAL_QUOTA[it], productType, it);
    });

    return {
        ABSOLUTE: absolute,
        DIFFERENTIAL_QUOTA: differential
    }
}

function ResourceBarByProductType(props: {rows: SubAllocation[]; productType: ProductType}) {
    const storageEntries = props.rows.filter(it => it.productType === props.productType);
    const storages = entriesByUnitAndChargeType(storageEntries, props.productType);
    if (storageEntries.length === 0) return null;
    return <Flex>
        {Object.keys(storages).flatMap((s: ChargeType) =>
            storages[s].map(e =>
                <React.Fragment key={`${e.initialBalance}-${e.remaining}`}>
                    <ResourceProgress
                        width={e.resourceText.length * 7.2 + "px"}
                        value={e.asPercent}
                        text={e.resourceText}
                    />
                    <Box mx="1px" />
                </React.Fragment>
            ))}
    </Flex>;
}

function SuballocationGroup(props: {entryKey: string; rows: SubAllocation[]; reload(): void; wallets: Wallet[]; isLast: boolean;}): JSX.Element {
    const isProject = props.rows[0].workspaceIsProject;

    const [editing, setEditing] = useState(false);
    const [loading, invokeCommand] = useCloudCommand();

    /* TODO(Jonas): Use context? */
    const editEntries = useRef<Record<string, SubAllocation>>({});
    /* TODO(Jonas): End */

    React.useEffect(() => {
        editEntries.current = {};
        if (editing) {
            props.rows.forEach(it => {
                editEntries.current[it.id] = {...it};
            });
        }
    }, [props.rows, editing]);

    const updateRows = useCallback(async () => {
        try {
            const edited = props.rows.map(it => editEntries.current[it.id]);
            const request = findChangedAndMapToRequest(props.rows, edited);
            if (request != null) {
                await callAPI(request);
                setEditing(false);
                props.reload();
            } else {
                snackbarStore.addInformation("No rows have been changed.", false);
            }
        } catch (e) {
            displayErrorMessageOrDefault(e, "Update failed");
        }
    }, [props.rows]);

    const [creationRows, setCreationRows] = useState<SuballocationCreationRow[]>([]);

    const submitNewRows = React.useCallback(async (creationRows: SuballocationCreationRow[]): Promise<void> => {
        const mappedRows: DepositToWalletRequestItem[] = [];
        for (const [index, row] of creationRows.entries()) {
            if (row.allocationId == "") {
                /* NOTE(Jonas): Should not be possible, but let's make sure it's handled. */
                snackbarStore.addFailure(`Row #${index + 1} is missing an allocation selection`, false);
                return;
            }

            if (row.amount == null || isNaN(row.amount)) {
                snackbarStore.addFailure(`Row #${index + 1} is missing an amount`, false);
                return;
            } else if (row.amount < 1) {
                snackbarStore.addFailure(`Row #${index + 1}'s amount must be more than 0.`, false);
                return;
            }

            mappedRows.push({
                amount: row.amount,
                description: "",
                recipient: isProject ? {type: "project", projectId: props.rows[0].workspaceId} : {type: "user", username: props.entryKey},
                sourceAllocation: row.allocationId,
                startDate: row.startDate,
                endDate: row.endDate,
            });
        }

        if (mappedRows.length > 0) {

            let reason = "";

            dialogStore.addDialog(<Box>
                <Heading.h3>Reason for sub-allocations</Heading.h3>
                <Box height="290px" mb="10px">
                    <TextArea
                        placeholder="Explain the reasoning for the new suballocation..."
                        style={{height: "100%"}}
                        width="100%"
                        onChange={e => reason = e.target.value}
                    />
                </Box>
                <ButtonGroup><Button onClick={async () => {
                    if (!reason) {
                        snackbarStore.addFailure("Reason can't be empty", false);
                        return;
                    }
                    mappedRows.forEach(it => it.description = reason);
                    try {
                        await invokeCommand(deposit(bulkRequestOf(...mappedRows)));
                        setCreationRows([]);
                        props.reload();
                        dialogStore.success();
                    } catch (e) {
                        errorMessageOrDefault(e, "Failed to submit rows");
                    }
                }} color="green">Confirm</Button><Button color="red" onClick={() => dialogStore.failure()}>Cancel</Button></ButtonGroup>
            </Box>, () => undefined);
        } else {
            // Note(Jonas): Technically not reachable, as submit is only available when >0 rows are shown.
            // Should be caught in earlier checks, but let's keep it here, to make sure this issue doesn't pop up
            // during a re-write of the code.
            snackbarStore.addFailure("No rows to submit", false);
        }
    }, []);

    const allocationsByProductTypes = useMemo((): {[key: string]: {wallet: Wallet; allocations: WalletAllocation[];}[]} => ({
        ["COMPUTE" as ProductType]: findValidAllocations(props.wallets, "COMPUTE"),
        ["STORAGE" as ProductType]: findValidAllocations(props.wallets, "STORAGE"),
        ["INGRESS" as ProductType]: findValidAllocations(props.wallets, "INGRESS"),
        ["LICENSE" as ProductType]: findValidAllocations(props.wallets, "LICENSE"),
        ["NETWORK_IP" as ProductType]: findValidAllocations(props.wallets, "NETWORK_IP")
    }), [props.wallets]);

    const idRef = useRef(0);
    const addNewRow = React.useCallback(() => setCreationRows(rows => {
        const firstProductTypeWithEntries = productTypes.find(it =>
            hasValidAllocations(allocationsByProductTypes[it])
        );

        if (!firstProductTypeWithEntries) {
            snackbarStore.addFailure("No available allocations to use", false);
            return rows;
        }

        const internalEntryToUse = allocationsByProductTypes[firstProductTypeWithEntries]
            .findIndex(it => it.allocations.length > 0);

        rows.push({
            id: idRef.current++,
            productType: firstProductTypeWithEntries,
            amount: undefined,
            endDate: undefined,
            startDate: startOfDay(new Date()).getTime(),
            wallet: allocationsByProductTypes[firstProductTypeWithEntries][internalEntryToUse].wallet,
            allocationId: allocationsByProductTypes[firstProductTypeWithEntries][internalEntryToUse].allocations[0].id,
        });
        return [...rows];
    }), [props.wallets]);

    const removeRow = React.useCallback((id: number) => {
        setCreationRows(rows => [...rows.filter(it => it.id !== id)]);
    }, []);

    const selectAllocation = React.useCallback((allocationAndWallets: {
        wallet: Wallet;
        allocations: WalletAllocation[];
    }[], id: number) => {
        dialogStore.addDialog(
            <Box width="830px">
                <Heading.h3>Available Allocations</Heading.h3>
                <Grid gridTemplateColumns={`repeat(2, 1fr)`} gridGap="15px">
                    {allocationAndWallets.flatMap(it => it.allocations.map(allocation =>
                        <Box key={allocation.allocationPath} cursor="pointer" onClick={() => {
                            setCreationRows(rows => {
                                rows[id].wallet = it.wallet;
                                rows[id].allocationId = allocation.id;
                                return [...rows];
                            });
                            dialogStore.success();
                        }}>
                            <AllocationViewer allocation={allocation} wallet={it.wallet} simple={false} />
                        </Box>
                    ))}
                </Grid>
            </Box>, () => undefined, false, largeModalStyle);
    }, []);

    const addRowButtonEnabled = React.useMemo(() => {
        for (const pt of productTypes) {
            if (hasValidAllocations(allocationsByProductTypes[pt])) {
                return true;
            }
        }
        return false;
    }, [allocationsByProductTypes]);

    const pi = props.rows[0]?.projectPI ?? "";

    return React.useMemo(() =>
        <Accordion
            key={props.entryKey}
            icon={isProject ? "projects" : "user"}
            iconColor2="white"
            title={props.entryKey}
            titleSub={<ListRowStat>
                <Icon size="10px" name={isProject ? "userPi" : "user"} /> {isProject ? "Project PI: " + pi : "Personal Workspace"}
            </ListRowStat>}
            forceOpen={editing || creationRows.length > 0}
            noBorder={props.isLast}
            titleContent={<>
                <UsageRowsWithMargin>
                    <ResourceBarByProductType rows={props.rows} productType="STORAGE" />
                    <ResourceBarByProductType rows={props.rows} productType="COMPUTE" />
                </UsageRowsWithMargin>
            </>}
            titleContentOnOpened={<>
                {addRowButtonEnabled ? <Button ml="8px" mt="-5px" mb="-8px" height="32px" width="100px" onClick={addNewRow}>New row</Button> :
                    <Tooltip trigger={<Button ml="8px" mt="-5px" mb="-8px" height="32px" width="100px" disabled>New row</Button>}>
                        No allocations available for use.
                    </Tooltip>
                }
                {editing ?
                    <ButtonGroup ml="8px" mb="-8px" mt="-5px" height="32px">
                        <Button color="green" onClick={updateRows}>Update</Button>
                        <Button color="red" onClick={() => setEditing(false)}>Cancel</Button>
                    </ButtonGroup> : <Button ml="8px" mt="-5px" mb="-8px" height="32px" onClick={() => setEditing(true)}>Edit</Button>}
            </>}
        >
            <Box px="12px">
                {creationRows.length === 0 ? null : <Spacer my="4px" right={<Button ml="8px" mt="2px" disabled={loading} height="32px" onClick={e => submitNewRows(creationRows)}>Submit new rows</Button>} left={null} />}
                {creationRows.map((row, index) => {
                    const productAndProvider = row.wallet ? <Text>{row.wallet.paysFor.name} @ <ProviderTitle providerId={row.wallet.paysFor.provider} /><TextSpan pl="0.3em" title="Allocation ID">[{row.allocationId}]</TextSpan></Text> : null;
                    const remainingProductTypes = productTypes.filter(it => it !== row.productType);

                    return (
                        <ListRow
                            key={row.id}
                            icon={<Box pl="20px">
                                <ClickableDropdown useMousePositioning width="190px" chevron trigger={<Icon name={productTypeToIcon(row.productType)} />}>
                                    {remainingProductTypes.map(pt => {
                                        const allowProductSelect = hasValidAllocations(allocationsByProductTypes[pt]);
                                        return allowProductSelect ?
                                            <Flex height="32px" key={pt} color="text" style={{alignItems: "center"}} onClick={() => {

                                                const {wallet, allocations} = allocationsByProductTypes[pt][0];
                                                creationRows[index].productType = pt;
                                                creationRows[index].wallet = wallet;
                                                creationRows[index].allocationId = allocations[0].id;
                                                setCreationRows([...creationRows]);
                                            }}>
                                                <Icon my="4px" mr="8px" name={productTypeToIcon(pt)} />{productTypeToTitle(pt)}
                                            </Flex> :
                                            <Tooltip key={pt} trigger={<Flex height="32px" style={{alignItems: "center"}} color="gray" onClick={stopPropagationAndPreventDefault}>
                                                <Icon mr="8px" name={productTypeToIcon(pt)} />{productTypeToTitle(pt)}</Flex>}>
                                                No allocations for product type available
                                            </Tooltip>
                                    })}
                                </ClickableDropdown></Box>}
                            left={<Flex>
                                <Flex cursor="pointer" onClick={() => selectAllocation(allocationsByProductTypes[row.productType], index)}>
                                    {productAndProvider}<Icon name="chevronDownLight" mt="5px" size="1em" ml=".7em" color={"darkGray"} />
                                </Flex>
                                <Flex color="var(--gray)" ml="12px">
                                    <DatePicker
                                        selectsRange
                                        fontSize="18px"
                                        py="0"
                                        dateFormat="dd/MM/yy"
                                        width="215px"
                                        startDate={new Date(row.startDate)}
                                        isClearable={creationRows[index].endDate != null}
                                        endDate={row.endDate != null ? new Date(row.endDate) : undefined}
                                        onChange={(dates: [Date | null, Date | null]) => {
                                            setCreationRows(rows => {
                                                if (dates[0]) rows[index].startDate = dates[0].getTime();
                                                rows[index].endDate = dates[1]?.getTime();
                                                return [...rows];
                                            });
                                        }}
                                    />
                                </Flex>
                            </Flex>}
                            right={row.wallet == null ? null : <Flex width={["NETWORK_IP", "INGRESS", "LICENSE"].includes(row.productType) ? "330px" : "280px"}>
                                <Input
                                    width="100%"
                                    type="number"
                                    rightLabel
                                    min={0}
                                    placeholder="Amount..."
                                    onChange={e => creationRows[index].amount = normalizeBalanceForBackend(parseInt(e.target.value, 10), row.productType, row.wallet!.chargeType, row.wallet!.unit)}
                                />
                                <InputLabel textAlign="center" width={labelSpace(row.wallet)} rightLabel>{explainSubAllocation(row.wallet)}</InputLabel>
                                <Icon mt="9px" ml="12px" name="close" color="red" cursor="pointer" onClick={() => removeRow(row.id)} />
                            </Flex>}
                        />);
                })}
                {props.rows.map(row => <SubAllocationRow key={row.id} editEntries={editEntries} editing={editing} suballocation={row} />)}
            </Box>
        </Accordion>, [creationRows, props.rows, props.wallets, loading, allocationsByProductTypes, editing, editEntries.current]);
}

const UsageRowsWithMargin = styled(Flex)`
    & > ${Flex}:not(:last-child) {
        margin-right: 12px;
    }
`;

function findValidAllocations(wallets: Wallet[], productType: ProductType): {wallet: Wallet, allocations: WalletAllocation[]}[] {
    const now = new Date().getTime();

    return wallets.filter(it => it.productType === productType).flatMap(w => ({
        wallet: w,
        allocations: w.allocations.filter(it => isAllocationSuitableForSubAllocation(it))
    }));
}

function hasValidAllocations(walletAllocations?: {wallet: Wallet, allocations: WalletAllocation[]}[]): boolean {
    if (!walletAllocations) return false;
    return walletAllocations.some(({allocations}) => allocations.some(it => isAllocationSuitableForSubAllocation(it)));
}

function SubAllocationRow(props: {suballocation: SubAllocation; editing: boolean; editEntries: MutableRefObject<Record<string, SubAllocation>>}): JSX.Element {
    const entry = props.suballocation;
    const [dates, setDates] = useState<[number, number | undefined | null]>([entry.startDate, entry.endDate ?? undefined]);
    React.useEffect(() => {
        setDates([entry.startDate, entry.endDate]);
    }, [props.editing]);

    if (props.editing) {
        return (
            <ListRow
                key={entry.id}
                icon={<Box pl="20px"><Icon name={productTypeToIcon(entry.productType)} /></Box>}
                left={<Flex>
                    <Text>{titleForSubAllocation(entry)}</Text>
                    <Flex color="var(--gray)" ml="12px">
                        <Text color="var(--gray)" mt="3px" fontSize="18px">
                            <DatePicker
                                selectsRange
                                fontSize="18px"
                                py="0"
                                width="215px"
                                pl={0}
                                dateFormat="dd/MM/yy"
                                isClearable={dates[1] != null}
                                startDate={new Date(dates[0])}
                                endDate={dates[1] != null ? new Date(dates[1]) : undefined}
                                onChange={(dates: [Date, Date | null]) =>
                                    setDates(oldDates => {
                                        const firstDate = dates[0] ? dates[0].getTime() : oldDates[0];
                                        const secondDate = dates[1]?.getTime();
                                        props.editEntries.current[entry.id].startDate = firstDate;
                                        props.editEntries.current[entry.id].endDate = secondDate;
                                        return [firstDate, secondDate];
                                    })
                                }
                            />
                        </Text>
                    </Flex>
                </Flex>}
                right={<Flex width="280px">
                    <Input
                        width="100%"
                        type="number"
                        rightLabel
                        min={0}
                        placeholder={normalizeSuballocationBalanceForFrontend(entry, entry.chargeType === "ABSOLUTE" ? entry.remaining : entry.initialBalance)}
                        onChange={e => {
                            const {chargeType} = props.editEntries.current[entry.id];
                            const balance = normalizeBalanceForBackend(parseInt(e.target.value, 10), entry.productType, entry.chargeType, entry.unit);
                            if (chargeType === "ABSOLUTE") {
                                props.editEntries.current[entry.id].remaining = balance;
                            } else if (chargeType === "DIFFERENTIAL_QUOTA") {
                                props.editEntries.current[entry.id].initialBalance = balance;
                            }
                        }}
                    />
                    <InputLabel textAlign={"center"} width={["INGRESS", "LICENSE"].includes(entry.productType) ? "250px" : "78px"} rightLabel>{explainSubAllocation(entry)}</InputLabel>
                </Flex>}
            />
        );
    } else return (
        <ListRow
            key={entry.id}
            icon={<Box pl="20px"><Icon name={productTypeToIcon(entry.productType)} /></Box>}
            left={<Flex>
                <Text>{titleForSubAllocation(entry)}</Text>
                <Text mt="3px" color="var(--gray)" ml="12px" fontSize={"18px"}>{format(entry.startDate, "dd/MM/yy")} - {entry.endDate ? format(entry.endDate, "dd/MM/yy") : "N/A"}</Text>
            </Flex>}
            right={
                <Box mr="0" width="auto" pl="0"><UsageBar suballocation={entry} /></Box>
                /* TODO: Grant redirect <Icon ml="12px" name="grant" /> */
            }
        />
    );
}

function UsageBar(props: {suballocation: SubAllocation}) {
    const {unit, productType} = props.suballocation;
    const mapped = mapToBalancesWithExplanation(props.suballocation, productType, unit);
    return <ResourceProgress value={Math.round(mapped.asPercent)} text={mapped.resourceText} />;
}

function explainSubAllocation(suballocation: {productType: ProductType; chargeType: ChargeType; unit: ProductPriceUnit}): string {
    return explainAllocation(suballocation.productType, suballocation.unit);
}

function normalizeSuballocationBalanceForFrontend(suballocation: SubAllocation, balance: number): string {
    return normalizeBalanceForFrontend(
        balance, suballocation.productType, suballocation.unit, 2
    );
}

const TRUNCATATION_LIMIT = 1_000_000;
export function shouldTruncate(value: number): boolean {
    return value >= TRUNCATATION_LIMIT;
}
