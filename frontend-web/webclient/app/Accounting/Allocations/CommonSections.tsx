import * as React from "react";
import {
    AllocationDisplayWallet,
    balanceToStringFromUnit,
    explainUnit,
    ProductType,
    UsageAndQuota,
    WalletV2
} from "@/Accounting";
import {Tree, TreeAction, TreeApi, TreeNode} from "@/ui-components/Tree";
import {Box, Button, Checkbox, Flex, Icon, Input, Label, Link, Relative, Text, Truncate} from "@/ui-components";
import AppRoutes from "@/Routes";
import * as Accounting from "@/Accounting";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {chunkedString, timestampUnixMs} from "@/UtilityFunctions"; import {dateToStringNoTime} from "@/Utilities/DateUtilities";
import {TooltipV2} from "@/ui-components/Tooltip";
import {OldProjectRole} from "@/Project";
import {State, UIEvent} from "@/Accounting/Allocations/State";
import {VariableSizeList} from "react-window";
import {AvatarState} from "@/AvataaarLib/hook";
import ReactVirtualizedAutoSizer from "react-virtualized-auto-sizer";
import Avatar from "@/AvataaarLib/avatar";
import {NewAndImprovedProgress} from "@/ui-components/Progress";
import {extractDataTags, injectStyle} from "@/Unstyled";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {useCallback, useRef} from "react";
import {ProgressBar} from "@/Accounting/Allocations/ProgressBar";

export const YourAllocations: React.FunctionComponent<{
    sortedAllocations: [string, {
        usageAndQuota: UsageAndQuota[];
        wallets: AllocationDisplayWallet[]
    }][],
    allocationTree: React.RefObject<TreeApi | null>,
    indent: number
}> = ({sortedAllocations, allocationTree, indent}) => {
    return <>
        <h3>Your allocations</h3>
        {sortedAllocations.length !== 0 ? null : <>
            You do not have any allocations at the moment. You can apply for resources{" "}
            <Link to={AppRoutes.grants.editor()}>here</Link>.
        </>}
        <Tree apiRef={allocationTree}>
            {sortedAllocations.map(([rawType, tree]) => {
                const type = rawType as ProductType;

                return <TreeNode
                    key={rawType}
                    left={<Flex gap={"4px"}>
                        <Icon name={Accounting.productTypeToIcon(type)} size={20}/>
                        {Accounting.productAreaTitle(type)}
                    </Flex>}
                    right={<Flex flexDirection={"row"} gap={"8px"}>
                        {tree.usageAndQuota.map((uq, idx) => <React.Fragment key={idx}>
                                <ProgressBar uq={uq}/>
                            </React.Fragment>
                        )}
                    </Flex>}
                    indent={indent}
                >
                    {tree.wallets.map((wallet, idx) =>
                        <TreeNode
                            key={idx}
                            left={<Flex gap={"4px"}>
                                <ProviderLogo providerId={wallet.category.provider} size={20}/>
                                <code>{wallet.category.name}</code>
                            </Flex>}
                            right={<Flex flexDirection={"row"} gap={"8px"}>
                                <ProgressBar uq={wallet.usageAndQuota}/>
                            </Flex>}
                            indent={indent * 2}
                        >
                            {wallet.allocations
                                .map(alloc =>
                                    <TreeNode
                                        key={alloc.id}
                                        className={alloc.note?.rowShouldBeGreyedOut ? "disabled-alloc" : undefined}
                                        left={<Flex gap={"32px"}>
                                            <Flex width={"200px"}>
                                                <Icon name={"heroBanknotes"} ml={"8px"} mr={4}/>
                                                <div>
                                                    <b>Allocation ID:</b>
                                                    {" "}
                                                    <code>{chunkedString(alloc.id.toString().padStart(6, "0"), 3, false).join(" ")}</code>
                                                </div>
                                            </Flex>

                                            <Flex width={"250px"}>
                                                Period:
                                                {" "}
                                                {dateToStringNoTime(alloc.start)}
                                                {" "}&mdash;{" "}
                                                {dateToStringNoTime(alloc.end)}
                                            </Flex>

                                            {alloc.grantedIn && <>
                                                <Link target={"_blank"}
                                                      to={AppRoutes.grants.editor(alloc.grantedIn)}>
                                                    View grant application{" "}
                                                    <Icon name={"heroArrowTopRightOnSquare"} mt={-6}/>
                                                </Link>
                                            </>}
                                        </Flex>}
                                        right={<Flex flexDirection={"row"} gap={"8px"}>
                                            {alloc.note && <>
                                                <TooltipV2 tooltip={alloc.note.text}>
                                                    <Icon name={alloc.note.icon} color={alloc.note.iconColor}/>
                                                </TooltipV2>
                                            </>}
                                            <div className="low-opaqueness">
                                                {alloc.display.quota}
                                            </div>
                                        </Flex>}
                                    />
                                )
                            }
                        </TreeNode>
                    )}
                </TreeNode>;
            })}
        </Tree>
    </>;
}

export const SubProjectList: React.FunctionComponent<{
    projectId: string | undefined,
    onNewSubProject: () => Promise<void>,
    projectRole: OldProjectRole,
    state: State,
    onSearchInput: (ev: React.SyntheticEvent) => void,
    onSearchKey: (event: React.KeyboardEvent<Element>) => void,
    searchBox: React.RefObject<HTMLInputElement | null>,
    dispatchEvent: (event: UIEvent) => unknown,
    suballocationTree: React.RefObject<TreeApi | null>,
    listRef: React.RefObject<VariableSizeList<number[]> | null>,
    onSubAllocationShortcut: (target: HTMLElement, ev: KeyboardEvent) => void,
    avatars: AvatarState,
    onEdit: (elem: HTMLElement) => void,
    onEditKey: (ev: React.KeyboardEvent) => Promise<void>,
    onEditBlur: (ev: React.SyntheticEvent) => void
}> = (
    {
        projectId,
        onNewSubProject,
        projectRole,
        state,
        onSearchInput,
        onSearchKey,
        searchBox,
        dispatchEvent,
        suballocationTree,
        listRef,
        onSubAllocationShortcut,
        avatars,
        onEdit,
        onEditKey,
        onEditBlur
    }
) => {
    return <>
        {projectId !== undefined && <>
            <Flex mt={32} mb={10} alignItems={"center"} gap={"8px"}>
                <h3 style={{margin: 0}}>Sub-projects</h3>
                <Box flexGrow={1}/>
                <Button height={35} onClick={onNewSubProject} disabled={projectRole == OldProjectRole.USER}>
                    <Icon name={"heroPlus"} mr={8}/>
                    New sub-project
                </Button>

                <Box width={"300px"}>
                    <Input
                        placeholder={"Search in your sub-projects"}
                        height={35}
                        value={state.searchQuery}
                        onInput={onSearchInput}
                        onKeyDown={onSearchKey}
                        disabled={state.editControlsDisabled}
                        inputRef={searchBox}
                    />
                    <div style={{position: "relative"}}>
                        <div style={{position: "absolute", top: "-30px", right: "11px"}}>
                            <Icon name={"heroMagnifyingGlass"}/>
                        </div>
                    </div>
                </Box>
            </Flex>
            <Flex>
                <Label width="160px" ml="auto">
                    <Checkbox onChange={e => dispatchEvent({
                        type: "ToggleViewOnlyProjects",
                        viewOnlyProjects: e.target.checked
                    })} defaultChecked={state.viewOnlyProjects}/>
                    <span>View only projects</span>
                </Label>
            </Flex>

            {state.filteredSubProjectIndices.length !== 0 ? null : <>
                You do not have any sub-allocations {state.searchQuery ? "with the active search" : ""} at the
                moment.
                {projectRole === OldProjectRole.USER ? null : <>
                    You can create a sub-project by clicking <a href="#" onClick={onNewSubProject}>here</a>.
                </>}
            </>}

            <ReactVirtualizedAutoSizer>
                {({height, width}) =>
                    <Tree apiRef={suballocationTree} onAction={(row, action) => {
                        if (![TreeAction.TOGGLE, TreeAction.OPEN, TreeAction.CLOSE].includes(action)) return;
                        const grantId = row.getAttribute("data-grant-id");
                        if (grantId && TreeAction.TOGGLE === action) {
                            // Note(Jonas): Just `window.open(AppRoutes...)` will omit the `/app` part, so we add it this way.
                            window.open(window.origin + "/app" + AppRoutes.grants.editor(grantId), "_blank");
                        } else {
                            const recipient = row.getAttribute("data-recipient");
                            if (!recipient) return;
                            const group = row.getAttribute("data-group");
                            setNodeState(action, recipient, group);
                            listRef.current?.resetAfterIndex(0);
                        }
                    }} unhandledShortcut={onSubAllocationShortcut}>
                        <VariableSizeList
                            height={600}
                            width={width}
                            ref={listRef}
                            estimatedItemSize={ROW_HEIGHT}
                            itemCount={state.filteredSubProjectIndices.length}
                            itemData={state.filteredSubProjectIndices}
                            itemSize={idx => calculateHeightInPx(idx, state)}
                        >
                            {({index: rowIdx, style, data}) => {
                                const recipientIdx = data[rowIdx];
                                const recipient = state.subAllocations.recipients[recipientIdx];

                                return <div style={style}>
                                    <TreeNode
                                        key={recipient.owner.title}
                                        data-recipient={recipient.owner.title}
                                        data-open={openNodes[recipient.owner.title]}
                                        onActivate={open => {
                                            if (open) setNodeState(TreeAction.OPEN, recipient.owner.title);
                                            else setNodeState(TreeAction.CLOSE, recipient.owner.title);
                                            listRef.current?.resetAfterIndex(rowIdx);
                                        }}
                                        left={<Flex gap={"4px"} alignItems={"center"}>
                                            <TooltipV2 tooltip={`Project PI: ${recipient.owner.primaryUsername}`}>
                                                <Avatar {...avatars.avatarFromCache(recipient.owner.primaryUsername)}
                                                        style={{height: "32px", width: "auto", marginTop: "-4px"}}
                                                        avatarStyle={"Circle"}/>
                                            </TooltipV2>
                                            <Truncate
                                                title={recipient.owner.title}>{recipient.owner.title}</Truncate>
                                        </Flex>}
                                        right={<div className={"sub-alloc"}>
                                            {recipient.owner.reference.type === "project" &&
                                                <Link
                                                    to={AppRoutes.grants.grantGiverInitiatedEditor({
                                                        title: recipient.owner.title,
                                                        piUsernameHint: recipient.owner.primaryUsername,
                                                        projectId: recipient.owner.reference.projectId,
                                                        start: timestampUnixMs(),
                                                        end: timestampUnixMs() + (1000 * 60 * 60 * 24 * 365),
                                                        subAllocator: false,
                                                    })}
                                                >
                                                    <SmallIconButton title="View grant application"
                                                                     icon={"heroBanknotes"}
                                                                     subIcon={"heroPlusCircle"}
                                                                     subColor1={"primaryContrast"}
                                                                     subColor2={"primaryContrast"}/>
                                                </Link>
                                            }


                                            {recipient.usageAndQuota.map((uq, idx) => {
                                                if (idx > 2) return null;
                                                return <ProgressBar key={idx} uq={uq}/>;
                                            })}
                                        </div>}
                                    >
                                        {recipient.groups.map((g, gidx) =>
                                            <TreeNode
                                                key={g.category.name}
                                                data-recipient={recipient.owner.title}
                                                data-group={g.category.name}
                                                data-open={openNodes[makeCategoryKey(recipient.owner.title, g.category.name)]}
                                                left={<Flex gap={"4px"}>
                                                    <Flex gap={"4px"} width={"200px"}>
                                                        <ProviderLogo providerId={g.category.provider} size={20}/>
                                                        <Icon
                                                            name={Accounting.productTypeToIcon(g.category.productType)}
                                                            size={20}/>
                                                        <code>{g.category.name}</code>
                                                    </Flex>
                                                </Flex>}
                                                right={
                                                    <ProgressBar uq={g.usageAndQuota}/>
                                                }
                                                onActivate={open => {
                                                    if (open) setNodeState(TreeAction.OPEN, recipient.owner.title, g.category.name);
                                                    else setNodeState(TreeAction.CLOSE, recipient.owner.title, g.category.name);
                                                    listRef.current?.resetAfterIndex(rowIdx);
                                                }}
                                            >
                                                {g.allocations
                                                    .map((alloc, idx) =>
                                                        <TreeNode
                                                            key={alloc.allocationId}
                                                            className={alloc.note?.rowShouldBeGreyedOut ? "disabled-alloc" : undefined}
                                                            data-ridx={rowIdx} data-idx={idx} data-gidx={gidx}
                                                            data-grant-id={alloc.grantedIn}
                                                            left={<Flex>
                                                                <Flex width={"200px"}>
                                                                    <Icon name={"heroBanknotes"} ml="8px" mr={4}/>
                                                                    <div>
                                                                        <b>Allocation ID:</b>
                                                                        {" "}
                                                                        <code>{chunkedString(alloc.allocationId.toString().padStart(6, "0"), 3, false).join(" ")}</code>
                                                                    </div>
                                                                </Flex>

                                                                <Flex width={"250px"}>
                                                                    Period:
                                                                    {" "}
                                                                    {dateToStringNoTime(alloc.start)}
                                                                    {" "}&mdash;{" "}
                                                                    {dateToStringNoTime(alloc.end)}
                                                                </Flex>

                                                                {alloc.grantedIn && <>
                                                                    <Link target={"_blank"}
                                                                          to={AppRoutes.grants.editor(alloc.grantedIn)}>
                                                                        View grant application{" "}
                                                                        <Icon name={"heroArrowTopRightOnSquare"}
                                                                              mt={-6}/>
                                                                    </Link>
                                                                </>}
                                                            </Flex>}
                                                            right={<Flex flexDirection={"row"} gap={"8px"}>
                                                                {alloc.note?.rowShouldBeGreyedOut !== true && !alloc.isEditing &&
                                                                    <SmallIconButton
                                                                        icon={"heroPencil"} onClick={onEdit}
                                                                        disabled={state.editControlsDisabled}
                                                                        data-ridx={rowIdx} data-idx={idx}
                                                                        data-gidx={gidx}/>
                                                                }
                                                                {alloc.note && <>
                                                                    <TooltipV2 tooltip={alloc.note.text}>
                                                                        <Icon name={alloc.note.icon}
                                                                              color={alloc.note.iconColor}/>
                                                                    </TooltipV2>
                                                                </>}

                                                                {alloc.isEditing ?
                                                                    <Flex gap={"4px"} width={"250px"}>
                                                                        <Input
                                                                            height={"24px"}
                                                                            defaultValue={Math.ceil(Accounting.explainUnit(g.category).priceFactor * alloc.quota)}
                                                                            autoFocus
                                                                            onKeyDown={onEditKey}
                                                                            onBlur={onEditBlur}
                                                                            data-ridx={rowIdx} data-idx={idx}
                                                                            data-gidx={gidx}
                                                                        />
                                                                        <Text
                                                                            width="120px">{Accounting.explainUnit(g.category).name}</Text>
                                                                    </Flex>
                                                                    : <Text>
                                                                        {Accounting.balanceToString(g.category, alloc.quota)}
                                                                    </Text>
                                                                }
                                                            </Flex>}
                                                        />
                                                    )
                                                }
                                            </TreeNode>)}
                                    </TreeNode>
                                </div>
                            }}
                        </VariableSizeList>
                    </Tree>}
            </ReactVirtualizedAutoSizer>
        </>}
    </>;
}

export const ResourcesGranted: React.FunctionComponent<{
    state: State;
    allocationTree: React.RefObject<TreeApi | null>;
    sortedAllocations: [
        string,
        {
            usageAndQuota: UsageAndQuota[];
            wallets: AllocationDisplayWallet[]
        }
    ][];
    indent: number;
    avatars: AvatarState;
}> = ({state, allocationTree, sortedAllocations, indent, avatars}) => {
    return <>
        <Flex mt={32} mb={10}>
            <h3>Resources Granted</h3>
        </Flex>
        {state.subAllocations.recipients.length === 0 ? "You have not granted any resources to sub-projects at the moment." : <>
            <Tree apiRef={allocationTree}>
                {sortedAllocations.map(([rawType, tree]) => {
                    const type = rawType as ProductType;

                    return <TreeNode
                        key={rawType}
                        left={<Flex gap={"4px"}>
                            <Icon name={Accounting.productTypeToIcon(type)} size={20}/>
                            {Accounting.productAreaTitle(type)}
                        </Flex>}
                        indent={indent}
                    >
                        {tree.wallets.map((wallet, idx) => {
                                if (wallet.totalAllocated === 0) return null;
                                return <TreeNode
                                    key={idx}
                                    left={<Flex gap={"4px"}>
                                        <ProviderLogo providerId={wallet.category.provider} size={20}/>
                                        <code>{wallet.category.name}</code>
                                    </Flex>}
                                    right={<Flex flexDirection={"row"} gap={"8px"}>
                                        <NewAndImprovedProgress
                                            label={
                                                balanceToStringFromUnit(
                                                    wallet.usageAndQuota.raw.type,
                                                    wallet.usageAndQuota.raw.unit,
                                                    explainUnit(wallet.category).balanceFactor * wallet.totalAllocated,
                                                    {
                                                        precision: 2,
                                                        removeUnitIfPossible: true
                                                    })
                                                + " / " +
                                                balanceToStringFromUnit(
                                                    wallet.usageAndQuota.raw.type,
                                                    wallet.usageAndQuota.raw.unit,
                                                    wallet.usageAndQuota.raw.quota,
                                                    {precision: 2})
                                            }
                                            percentage={
                                                100 * ((explainUnit(wallet.category).balanceFactor * wallet.totalAllocated) / wallet.usageAndQuota.raw.quota)
                                            }
                                            limitPercentage={(explainUnit(wallet.category).balanceFactor * wallet.totalAllocated) > wallet.usageAndQuota.raw.quota ? 0 : 100}
                                            withWarning={false}
                                        />
                                    </Flex>}
                                    indent={indent * 2}
                                >
                                    {/* TODO: Calculate this and store in useMemo instead of on every re-render */}
                                    {/* List All granted resources in descending order for each product category */}
                                    {state.subAllocations.recipients.sort((a, b) => {
                                        const aval = a.groups.filter((g) => g.category === wallet.category).reduce((asum, element) => asum + element.totalGranted, 0)
                                        const bval = b.groups.filter((g) => g.category === wallet.category).reduce((bsum, element) => bsum + element.totalGranted, 0)
                                        return bval - aval;
                                    }).map((recipient, idx) =>
                                        <TreeNode
                                            key={idx}
                                            left={
                                                <Flex gap={"4px"} alignItems={"center"}>
                                                    <TooltipV2 tooltip={`Project PI: ${recipient.owner.primaryUsername}`}>
                                                        <Avatar {...avatars.avatarFromCache(recipient.owner.primaryUsername)}
                                                                style={{height: "32px", width: "auto", marginTop: "-4px"}}
                                                                avatarStyle={"Circle"}/>
                                                    </TooltipV2>
                                                    <Truncate
                                                        title={recipient.owner.title}>{recipient.owner.title}</Truncate>
                                                </Flex>
                                            }
                                            right={
                                                <Flex flexDirection={"row"} gap={"8px"}>
                                                    {
                                                        balanceToStringFromUnit(
                                                            wallet.usageAndQuota.raw.type,
                                                            wallet.usageAndQuota.raw.unit,
                                                            explainUnit(wallet.category).balanceFactor *
                                                            recipient.groups.filter(
                                                                (elm) => elm.category == wallet.category
                                                            ).reduce((sum, element) => sum + element.totalGranted, 0),
                                                            {
                                                                precision: 2
                                                            }
                                                        )
                                                    }
                                                </Flex>
                                            }
                                        >

                                        </TreeNode>
                                    )}
                                </TreeNode>;
                            }
                        )}
                    </TreeNode>;
                })}
            </Tree>

        </>
        }
    </>;
}

function setNodeState(action: TreeAction, recipient: string, group?: string | null): void {
    const key = group ? makeCategoryKey(recipient, group) : recipient;
    switch (action) {
        case TreeAction.CLOSE:
            delete openNodes[key];
            break;
        case TreeAction.OPEN: {
            openNodes[key] = true;
            break;
        }
        case TreeAction.TOGGLE: {
            openNodes[key] = !openNodes[key];
            if (!openNodes[key]) delete openNodes[key];
            break;
        }
    }
}

const ROW_HEIGHT = 48;

function calculateHeightInPx(idx: number, state: State): number {
    if (state.subAllocations.recipients.length <= idx) return 0; // Already handled
    const recipient = state.subAllocations.recipients[idx];
    let height = ROW_HEIGHT;
    const isOpen = openNodes[recipient.owner.title]
    if (isOpen) {
        height += recipient.groups.length * ROW_HEIGHT;
        recipient.groups.forEach(g => {
            const isGroupOpen = openNodes[makeCategoryKey(recipient.owner.title, g.category.name)];
            if (isGroupOpen) {
                height += g.allocations.length * ROW_HEIGHT;
            }
        });
    }

    return height;
}

function makeCategoryKey(title: Accounting.AllocationDisplayTreeRecipientOwner["title"], name: Accounting.ProductCategoryV2["name"]): string {
    return title + "$$" + name;
}

let openNodes: Record<string, boolean> = {};

export function resetOpenNodes() {
    openNodes = {};
}

// Utility components
// =====================================================================================================================
// Various helper components used by the main user-interface.

export function currentTotalUsage(wallet: WalletV2): number {
    let totalusage: number
    let retired = 0
    if (wallet.paysFor.accountingFrequency === "ONCE") {
        totalusage = wallet.totalUsage
    } else {
        wallet.allocationGroups.forEach(group =>
            group.group.allocations.forEach(alloc =>
                retired += alloc.retiredUsage ?? 0
            )
        )
        totalusage = wallet.totalUsage - retired
    }
    return totalusage;
}

const SmallIconButtonStyle = injectStyle("small-icon-button", k => `
    ${k},
    ${k}:hover {
        color: var(--primaryContrast) !important;
    }
        
    ${k} {
        height: 25px !important;
        width: 25px !important;
        padding: 12px !important;
    }
    
    ${k} svg {
        margin: 0;
    }
    
    ${k}[data-has-sub=true] svg {
        width: 14px;
        height: 14px;
        position: relative;
        left: -2px;
        top: -1px;
    }
    
    ${k} .sub {
        position: absolute;
        left: -15px;
        top: -10px;
    }
    
    ${k} .sub > svg {
        width: 12px;
        height: 12px;
        position: relative;
        left: 5px;
        top: 3px;
    }
`);

const SmallIconButton: React.FunctionComponent<{
    icon: IconName;
    subIcon?: IconName;
    subColor1?: ThemeColor;
    subColor2?: ThemeColor;
    color?: ThemeColor;
    title?: string;
    onClick?: (ev: HTMLButtonElement) => void;
    disabled?: boolean;
}> = props => {
    const ref = useRef<HTMLButtonElement>(null);
    const onClick = useCallback(() => {
        props?.onClick?.(ref.current!);
    }, [props.onClick]);

    return <Button
        className={SmallIconButtonStyle}
        onClick={onClick}
        color={props.color}
        disabled={props.disabled}
        btnRef={ref}
        title={props.title}
        data-has-sub={props.subIcon !== undefined}
        {...extractDataTags(props)}
    >
        <Icon name={props.icon} hoverColor={"primaryContrast"}/>
        {props.subIcon &&
            <Relative>
                <div className={"sub"}>
                    <Icon name={props.subIcon} hoverColor={props.subColor1} color={props.subColor1}
                          color2={props.subColor2}/>
                </div>
            </Relative>
        }
    </Button>;
};
