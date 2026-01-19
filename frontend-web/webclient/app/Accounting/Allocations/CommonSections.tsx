import * as React from "react";
import {
    AllocationDisplayTreeRecipient,
    AllocationDisplayTreeYourAllocation, normalizedBalanceToRaw, ProductCategoryV2,
    ProductType,
    UsageAndQuota,
} from "@/Accounting";
import {Tree, TreeAction, TreeApi, TreeNode} from "@/ui-components/Tree";
import {
    Box,
    Button,
    Checkbox,
    Divider,
    Flex,
    Icon,
    Input,
    Link,
    Relative,
    Text,
    Truncate
} from "@/ui-components";
import AppRoutes from "@/Routes";
import * as Accounting from "@/Accounting";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {bulkRequestOf, chunkedString, doNothing, timestampUnixMs} from "@/UtilityFunctions";
import {dateToStringNoTime} from "@/Utilities/DateUtilities";
import {TooltipV2} from "@/ui-components/Tooltip";
import {OldProjectRole} from "@/Project";
import {State, UIAction, UIEvent} from "@/Accounting/Allocations/State";
import {VariableSizeList} from "react-window";
import {AvatarState} from "@/AvataaarLib/hook";
import AutoSizer from "react-virtualized-auto-sizer";
import Avatar from "@/AvataaarLib/avatar";
import {classConcat, extractDataTags, injectStyle} from "@/Unstyled";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {useCallback, useEffect, useRef, useState} from "react";
import {ProgressBar} from "@/Accounting/Allocations/ProgressBar";
import {default as ReactModal} from "react-modal";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {CardClass} from "@/ui-components/Card";
import {ListRow} from "@/ui-components/List";
import {SimpleRichItem, SimpleRichSelect} from "@/ui-components/RichSelect";
import {produce} from "immer";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {exportUsage, header} from "@/Accounting/Usage";
import {useProject} from "@/Project/cache";
import {useProjectId} from "@/Project/Api";
import {AllocationBar} from "@/Accounting/Allocations/AllocationBar";
import {projectInfoPi, projectInfoTitle, useProjectInfo} from "@/Project/InfoCache";
import {useForcedRender} from "@/Utilities/ReactUtilities";
import {Feature, hasFeature} from "@/Features";
import {dialogStore} from "@/Dialog/DialogStore";
import * as Heading from "@/ui-components/Heading";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import DatePicker from "react-datepicker";
import {callAPIWithErrorHandler} from "@/Authentication/DataHook";

interface Datapoint {
    product: string;
    provider: string;
    usage: number;
    quota: number;
    unit: string;
}

export const YourAllocations: React.FunctionComponent<{
    allocations: [string, AllocationDisplayTreeYourAllocation][],
    allocationTree: React.RefObject<TreeApi | null>,
    indent: number;
    state: State;
}> = ({allocations, allocationTree, indent, state}) => {
    const project = useProject();
    const projectId = useProjectId();

    const onExportData = useCallback(() => {
        const toExport: Datapoint[] = [];
        for (const [, allocationTree] of allocations) {
            for (let wallet of allocationTree.wallets) {
                let usage = wallet.usageAndQuota.raw.usage;
                if (!wallet.usageAndQuota.raw.retiredAmountStillCounts) {
                    usage -= wallet.usageAndQuota.raw.retiredAmount;
                }
                toExport.push({
                    product: wallet.category.name,
                    provider: wallet.category.provider,
                    usage,
                    quota: wallet.usageAndQuota.raw.quota,
                    unit: wallet.usageAndQuota.raw.unit
                });
            }
        }
        if (!project.error || projectId === undefined) {
            const title = projectId === undefined ? undefined : project.fetch().specification.title;
            exportUsage<Datapoint>(
                toExport,
                [
                    header("product", "Product", true),
                    header("provider", "Provider", true),
                    header("usage", "Usage", true),
                    header("quota", "Quota", true),
                    header("unit", "Unit", true)
                ],
                title
            );
        }
    }, [allocations, project, projectId]);

    return <>
        <div className={yourAllocationsStyle}>
            <div className="your-allocations-header">
                <h3>Your allocations</h3>
                <Button onClick={onExportData}>
                    Export
                </Button>
            </div>
            <div className="your-allocations-container">
                {state.remoteData.wallets === undefined ? <>
                    <HexSpin size={64} />
                </> : <>
                    <div>
                        {allocations.length !== 0 ? null : <div style={{marginLeft: "20px", marginTop: "10px"}}>
                            You do not have any allocations at the moment. You can apply for resources{" "}
                            <Link to={AppRoutes.grants.editor()}>here</Link>.
                        </div>}
                        <Tree apiRef={allocationTree}>
                            {allocations.map(([rawType, tree]) => {
                                const type = rawType as ProductType;

                                return <TreeNode
                                    key={rawType}
                                    left={<Flex gap={"4px"}>
                                        <Icon name={Accounting.productTypeToIcon(type)} size={20} />
                                        {Accounting.productAreaTitle(type)}
                                    </Flex>}
                                    right={<Flex flexDirection={"row"} gap={"8px"}>
                                        {tree.usageAndQuota.map((uq, idx) => <React.Fragment key={idx}>
                                            <ProgressBar uq={uq} />
                                        </React.Fragment>
                                        )}
                                    </Flex>}
                                    indent={indent}
                                >
                                    {tree.wallets.map((wallet, idx) =>
                                        <TreeNode
                                            key={idx}
                                            left={<Flex gap={"4px"}>
                                                <ProviderLogo providerId={wallet.category.provider} size={20} />
                                                <code>{wallet.category.name}</code>
                                            </Flex>}
                                            right={<Flex flexDirection={"row"} gap={"8px"}>
                                                <ProgressBar uq={wallet.usageAndQuota} />
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
                                                                <Icon name={"heroBanknotes"} ml={"8px"} mr={4} />
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
                                                                    <Icon name={"heroArrowTopRightOnSquare"} mt={-6} />
                                                                </Link>
                                                            </>}
                                                        </Flex>}
                                                        right={<Flex flexDirection={"row"} gap={"8px"}>
                                                            {alloc.note && <>
                                                                <TooltipV2 tooltip={alloc.note.text}>
                                                                    <Icon name={alloc.note.icon}
                                                                        color={alloc.note.iconColor} />
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
                                </TreeNode>
                            })}
                        </Tree>
                    </div>
                </>}
            </div>
        </div>
    </>;
}

const yourAllocationsStyle = injectStyle("your-allocations", k => `
    ${k} .your-allocations-header {
        display: flex;
        align-items: center;
        margin-bottom: 14px;
    }
    
    ${k} .your-allocations-header > h3 {
        flex-grow: 1;
    }
    
    ${k} .your-allocations-container {
        border: 1px solid var(--borderColor);
        border-radius: 5px;
        padding: 10px 20px;
    }
`);

const keyMetricsStyle = injectStyle("key-metrics", k => `
    ${k} .sub-project-allocations-container {
        width: 80%;
        height: 350px;
        border: 1px solid var(--borderColor);
        padding: 20px;
        border-radius: 5px;
        overflow: auto;
    }
    
    ${k} .key-metrics-header-container {
        display: flex;
        align
        flex-direction: row;
        gap: 14px;
        margin-top: 14px;
        align-items: center;
    }
   
    ${k} .key-metrics-header-container > h3 {
        flex-grow: 1;
    }
    
    ${k} .key-metrics-container {
        display: flex;
        align
        flex-direction: row;
        gap: 14px;
        margin-top: 14px;
    }
    ${k} .filters-button {
        width: 35px;
    }
    
    ${k} .key-metrics-input {
        width: 400px;
        display: flex;
        gap: 10px;
    }
    
    ${k} .key-metrics-input > .key-metrics-search-box {
        flex-grow: 1;
        display: flex;
    }
    
    ${k} .key-metrics-card-container {
        display: flex;
        flex-direction: row;
        flex-wrap: wrap;
        flex-basis: 400px;
        flex-shrink: 0;
        flex-grow: 0;
        gap: 14px;
    }
    
    ${k} .key-metrics-card {
        width: 25%;
        background: var(--primaryMain);
        border-radius: 5px;
        padding: 20px 20px 20px 16px;
        color: white;
        font-size: 20px;
        flex-grow: 1;
    }
    
    ${k} .key-metrics-card h3 {
        font-weight: 500;
    }
    
    ${k} .key-metrics-list {
        width: 100%;
        border: 1px solid var(--borderColor);
        border-radius: 5px;
        padding-left: 16px;
        padding-right: 16px;
    }
    
    ${k} .key-metrics-line {
        display: flex;
    }
    
    ${k} .key-metric-table-left {
        flex-grow: 1;
    }
    
    ${k} .key-metric-table-right {
        flex-shrink: 0; 
    }
    
    ${k} .key-metrics-settings-container {
        flex-grow: 1;
    }
    
    ${k} .key-metrics-setting-title {
        font-size: 11pt;
        padding-left: 10px;
    }
    
    ${k} .key-metrics-checkbox {
        padding: 0 5px 0 0;
    }
    
    ${k} .key-metrics-selector {
        padding-right: 6px;
    }
`);

const subProjectsStyle = injectStyle("sub-projects", k => ` 
    ${k} .sub-projects-search-bar-container {
        display: flex;
        flex-grow: 1;
        gap: 10px;
    }
    
    ${k} .sub-projects-container {
        border: 1px solid var(--borderColor);
        padding: 10px 0px;
        border-radius: 5px;
    }
    
    ${k} .filters-button {
        width: 35px;
    }
    
    ${k} .new-sub-project-button {
        margin-right: 4px;
    }
    
    ${k} .sub-project-list-row {
        padding: 0 20px;
    }
    
    ${k} .sub-projects-sorting-container {
        display: flex;
        padding-top: 6px;            
    }
    
    ${k} .sub-projects-sorting-headers {
        display: block;
        flex-grow: 1;
    }
    
    ${k} .sub-projects-sorting-selector {
        display: flex;
        align-items: center;
        gap: 9px;  
    }
    
    ${k} .sort-button {
        padding-right: 22px;
    }
`);

interface KeyMetricSetting {
    title: string;
    options: string[];
    selected?: string;
    starred: boolean;
    enabled: boolean;
}

const KeyMetricSettingsRow: React.FunctionComponent<{
    setting: KeyMetricSetting;
    onChange: (setting: KeyMetricSetting) => void;
}> = (props) => {

    const onChecked = useCallback(() => {
        props.onChange(produce(props.setting, draft => {
            draft.enabled = !draft.enabled;
        }))
    }, [props.setting, props.onChange])

    const onSelectOption = useCallback((item: SimpleRichItem) => {
        props.onChange(produce(props.setting, draft => {
            draft.selected = item.key
        }))
    }, [props.setting, props.onChange])

    const onStarring = useCallback(() => {
        props.onChange(produce(props.setting, draft => {
            draft.starred = !draft.starred;
        }))
    }, [props.setting, props.onChange])

    let selectedOpt = props.setting.selected;
    return <ListRow
        left={<Flex>
            <Icon
                name={props.setting.starred ? "starFilled" : "starEmpty"}
                color={props.setting.starred ? "favoriteColor" : "favoriteColorEmpty"}
                onClick={onStarring}
            />
            <h3 className="key-metrics-setting-title">{props.setting.title}</h3>
        </Flex>
        }
        right={<>
            {props.setting.options.length === 0 ? null : <div className="key-metrics-selector">
                <SimpleRichSelect
                    items={props.setting.options.map((it) => ({key: it, value: it}))}
                    onSelect={onSelectOption}
                    selected={selectedOpt ? {key: selectedOpt, value: selectedOpt} : undefined}
                    dropdownWidth={"300px"}
                />
            </div>}

            <div className="key-metrics-checkbox">
                <Checkbox
                    size={30}
                    checked={props.setting.enabled}
                    handleWrapperClick={onChecked}
                    onChange={onChecked}
                />
            </div>
        </>
        }
    >
    </ListRow>
}

const keyMetricDefaultSettings: Record<string, KeyMetricSetting> = {
    "Idle sub-projects": {
        title: "Idle sub-projects",
        options: ["1 month", "2 months", "3 months"],
        selected: "1 month",
        starred: true,
        enabled: true
    },
    "Sub-project resource utilization": {
        title: "Sub-project resource utilization",
        options: ["Core-hours", "GPU-hours", "Storage"],
        selected: "Core-hours",
        starred: true,
        enabled: true
    },
    "Your resource utilization": {
        title: "Your resource utilization",
        options: ["Core-hours", "GPU-hours", "Storage"],
        selected: "Core-hours",
        starred: false,
        enabled: false
    },
    "Allocation expiration": {
        title: "Allocation expiration",
        options: ["1 month", "2 months", "3 months", "6 months", "1 year"],
        selected: "1 month",
        starred: false,
        enabled: false
    },
    "Overallocation indicators": {
        title: "Overallocation indicators",
        options: [],
        selected: "",
        starred: false,
        enabled: false
    },
};

export const SubProjectAllocations: React.FunctionComponent<{
    allocations: [string, AllocationDisplayTreeYourAllocation][];
    indent: number;
}> = ({allocations, indent}) => {
    const treeApi = useRef<TreeApi>(null);
    const [filtersShown, setFiltersShown] = useState(false);
    const closeFilters = useCallback(() => {
        setFiltersShown(false);
    }, []);
    const openFilters = useCallback(() => {
        setFiltersShown(true);
    }, []);

    const [settings, setSettings] = useState<Record<string, KeyMetricSetting>>(keyMetricDefaultSettings);

    const onSettingsChanged = useCallback((setting: KeyMetricSetting) => {
        setSettings(prev => {
            return produce(prev, draft => {
                draft[setting.title] = setting;
            });
        });
    }, []);

    if (!hasFeature(Feature.ALLOCATIONS_PAGE_IMPROVEMENTS)) return null;

    return <>
        <ReactModal
            isOpen={filtersShown}
            shouldCloseOnEsc
            onRequestClose={closeFilters}
            style={largeModalStyle}
            ariaHideApp={false}
            className={classConcat(CardClass, keyMetricsStyle)}
        >

            <Flex>
                <div className="key-metrics-settings-container">
                    <h3>Key metrics settings</h3>
                    <h4 style={{color: "var(--textSecondary)"}}>Select key metrics to display</h4>
                </div>
                <div className="key-metrics-input">
                    <div className="key-metrics-search-box">
                        <Input placeholder="Search in your key metrics"></Input>
                        <div style={{position: "relative"}}>
                            <div style={{position: "absolute", top: "5px", right: "10px"}}>
                                <Icon name={"heroMagnifyingGlass"} />
                            </div>
                        </div>
                    </div>
                    <Button onClick={closeFilters}>Apply</Button>
                </div>
            </Flex>

            {Object.values(settings).map(setting => (
                <KeyMetricSettingsRow key={setting.title} setting={setting} onChange={onSettingsChanged} />
            ))}
        </ReactModal>

        <Box mt={32} mb={10} className={keyMetricsStyle}>
            <div className="key-metrics-header-container">
                <h3>Key metrics</h3>
                <div className="key-metrics-input">
                    <div className="key-metrics-search-box">
                        <Input placeholder="Search in your key metrics"></Input>
                        <div style={{position: "relative"}}>
                            <div style={{position: "absolute", top: "5px", right: "10px"}}>
                                <Icon name={"heroMagnifyingGlass"} />
                            </div>
                        </div>
                    </div>

                    <Button className="filters-button" onClick={openFilters}>
                        <Icon name={"heroAdjustmentsHorizontal"} />
                    </Button>
                </div>
            </div>
            <div className="key-metrics-container">
                <div className="sub-project-allocations-container">
                    <h3>Sub-project allocations</h3>
                    <Tree apiRef={treeApi}>
                        {allocations.map(([rawType, tree]) => {
                            const type = rawType as ProductType;

                            return <TreeNode
                                key={rawType}
                                left={
                                    <Flex gap={"4px"}>
                                        <Icon name={Accounting.productTypeToIcon(type)} size={20} />
                                        {Accounting.productAreaTitle(type)}
                                    </Flex>
                                }
                                right={<Flex flexDirection={"row"} gap={"8px"}>
                                    {tree.usageAndQuota.map((uq, idx) =>
                                        <React.Fragment key={idx}>
                                            <AllocationBar label={"65% Ok | 5% At risk | 30% Underused"} okPercentage={65} atRiskPercentage={5} underusedPercentage={30} />
                                        </React.Fragment>
                                    )}
                                </Flex>}
                                indent={indent}
                            >
                                <TreeNode
                                    left={
                                        <Flex gap={"4px"}>
                                            <ProviderLogo providerId={"ucloud"} size={20} />
                                            <h3>u1-cephfs</h3>
                                        </Flex>
                                    }
                                    right={<Flex flexDirection={"row"} gap={"8px"}>
                                        {tree.usageAndQuota.map((uq, idx) => <React.Fragment key={idx}>
                                            <ProgressBar uq={uq} />
                                        </React.Fragment>
                                        )}
                                    </Flex>}
                                >
                                </TreeNode>
                            </TreeNode>;
                        })}
                    </Tree>
                </div>
                <div className="key-metrics-card-container">
                    <div className="key-metrics-card">
                        <h3>41%</h3>
                        <br />
                        <h3>Storage <br /> utilization</h3>
                    </div>
                    <div className="key-metrics-card">
                        <h3>73</h3>
                        <br />
                        <h3>Idle <br /> projects</h3>
                    </div>
                    <div className="key-metrics-list">
                        <div className="key-metrics-line">
                            <p className="key-metric-table-left">Key metrics content goes here:</p>
                            <p className="key-metric-table-right">12%</p>
                        </div>
                        <div className="key-metrics-line">
                            <p className="key-metric-table-left">More content on this line:</p>
                            <p className="key-metric-table-right">73%</p>
                        </div>
                        <div className="key-metrics-line">
                            <p className="key-metric-table-left">This one is also a key metric:</p>
                            <p className="key-metric-table-right">100%</p>
                        </div>
                    </div>
                </div>
            </div>
        </Box>
    </>;
};

const FilteredUsageAndQuota: React.FunctionComponent<{
    entries: UsageAndQuota[];
}> = ({entries}) => {
    const filteredEntries = entries
        .filter(it => {
            return it.type === "STORAGE" || it.type === "COMPUTE";
        })
        .sort((a, b) => {
            const order: Record<string, number> = {
                "GPU-hours": 1,
                "Core-hours": 2,
                "GB": 3,
                "TB": 3,
                "PB": 3,
                "EB": 3,
            };
            return (order[a.raw.unit] ?? 99) - (order[b.raw.unit] ?? 99);
        });

    return <>
        {filteredEntries.map((uq, idx) => {
            if (idx > 2) return null;
            return <ProgressBar key={idx} uq={uq} />;
        })}
    </>
}

function DurationSelector(props: {periodRef: {start: Date | null; end: Date | null} }) {
    const originalStart = props.periodRef.start;
    const originalEnd = props.periodRef.end;
    const [startDate, setStartDate] = useState<Date | null>(props.periodRef.start);
    const [endDate, setEndDate] = useState<Date | null>(props.periodRef.end);
    props.periodRef.start = startDate;
    props.periodRef.end = endDate;

    const onChange = (dates: [Date | null, Date | null]) => {
        const [start, end] = dates
        setStartDate(start)
        setEndDate(end)
    }

    return <>
        <Box mb={"8px"} mt={"16px"}>
            Allocation period (Original period: {dateToStringNoTime(originalStart?.getTime() ?? new Date().getTime())} - {dateToStringNoTime(originalEnd?.getTime() ?? new Date().getTime())})
            <br/>
            <DatePicker
                selected={startDate}
                onChange={onChange}
                startDate={startDate}
                endDate={endDate}
                selectsRange
                dateFormat="MM/yyyy"
                minDate={new Date()}
                showMonthYearPicker
                required
            />
        </Box>
    </>
}

function openUpdater(
    category: ProductCategoryV2,
    allocationId: number,
    originalStart: Date | null,
    originalEnd: Date | null,
    originalQuota: number,
    dispatchEvent:(ev: UIEvent) => void,
    idx: number,
    gidx: number,
    ridx: number
): void {
    const periodRef = {
        start: originalStart,
        end: originalEnd,
    }
    let quota = originalQuota;
    dialogStore.addDialog((
    <div onKeyDown={e => e.stopPropagation()}>
        <div>
            <Heading.h3>Update Allocation {allocationId}</Heading.h3>
            <Divider />
            <DurationSelector periodRef={periodRef} />
            <Box mb={"16px"}>
                Allocation quota (Original quota: {Accounting.balanceToString(category, quota)})
                <Input width={1} my="3px" type="number" min={0} onChange={e => quota = normalizedBalanceToRaw(category, e.target.valueAsNumber)} />
            </Box>
            <Box mb={"16px"}>
                <form onSubmit={async ev => {
                    ev.preventDefault();
                    ev.stopPropagation();
                    const success = (await callAPIWithErrorHandler(
                        Accounting.updateAllocationV2(bulkRequestOf({
                            allocationId: allocationId,
                            newQuota: quota,
                            newStart: periodRef.start?.getTime() ?? new Date().getTime(),
                            newEnd: periodRef.end?.getTime() ?? new Date().getTime(),
                            reason: "Allocation updated",
                        }))
                    )) !== null;

                    if (success) {
                        dispatchEvent({
                            type: "UpdateAllocation",
                            allocationIdx: idx,
                            recipientIdx: ridx,
                            groupIdx: gidx,
                            newQuota: quota,
                            newStart: periodRef.start ?? new Date(),
                            newEnd: periodRef.end ?? new Date(),
                        });
                        snackbarStore.addSuccess("Update Success", false)
                        dialogStore.success()
                    }
                }}>
                    <Button type={"submit"} fullWidth>
                        Update Allocation
                    </Button>
                </form>
            </Box>
        </div>
    </div>
    ), doNothing, true);
}

const SubProjectListRow: React.FunctionComponent<{
    style: React.CSSProperties;
    recipient: AllocationDisplayTreeRecipient;
    listRef: React.RefObject<VariableSizeList<number[]> | null>;
    rowIdx: number;
    recipientIdx: number;
    avatars: AvatarState;
    state: State;
    setNodeState: (action: TreeAction, reference: string, group?: string | null) => void;
    dispatchEvent: (ev: UIEvent) => void;
}> = ({style, recipient, listRef, rowIdx, recipientIdx, avatars, state, setNodeState, dispatchEvent}) => {
    const projectInfo = useProjectInfo(recipient.owner.reference.type === "user" ? "" : recipient.owner.reference.projectId);
    const workspaceId = recipient.owner.reference["username"] ?? recipient.owner.reference["projectId"] ?? "";
    const pi = recipient.owner.reference.type === "user" ?
        recipient.owner.reference.username :
        projectInfoPi(projectInfo.data, recipient.owner.primaryUsername) ?? "-";
    const title = recipient.owner.reference.type === "user" ?
        recipient.owner.reference.username :
        projectInfoTitle(projectInfo.data, recipient.owner.title) ?? "-";

    return <div style={style}>
        <TreeNode
            className={"sub-project-list-row"}
            key={title}
            data-recipient={workspaceId}
            data-open={openNodes[workspaceId]}
            onActivate={open => {
                if (open) setNodeState(TreeAction.OPEN, workspaceId);
                else setNodeState(TreeAction.CLOSE, workspaceId);
                listRef.current?.resetAfterIndex(rowIdx);
            }}
            left={<Flex gap={"4px"} alignItems={"center"}>
                <TooltipV2 tooltip={`Project PI: ${pi}`}>
                    <Avatar {...avatars.avatarFromCache(pi)}
                        style={{height: "32px", width: "auto", marginTop: "-4px"}}
                        avatarStyle={"Circle"} />
                </TooltipV2>
                <Truncate
                    title={title}
                    width={400}
                >
                    {title}
                </Truncate>
            </Flex>}
            right={<div className={"sub-alloc"}>
                <FilteredUsageAndQuota entries={recipient.usageAndQuota} />
                {recipient.owner.reference.type === "project" &&
                    <Link
                        to={AppRoutes.grants.grantGiverInitiatedEditor({
                            title: title,
                            piUsernameHint: pi,
                            projectId: recipient.owner.reference.projectId,
                            start: timestampUnixMs(),
                            end: timestampUnixMs() + (1000 * 60 * 60 * 24 * 365),
                            subAllocator: false,
                        })}
                    >
                        <SmallIconButton tooltip="Allocate more resources"
                            icon={"heroBanknotes"}
                            subIcon={"heroPlusCircle"}
                            subColor1={"primaryContrast"}
                            subColor2={"primaryContrast"} />
                    </Link>
                }
            </div>}
        >

            {recipient.groups.map((g, gidx) =>
                <TreeNode
                    key={g.category.name}
                    data-recipient={workspaceId}
                    data-group={g.category.name}
                    data-open={openNodes[makeCategoryKeyFromWorkspaceId(workspaceId, g.category.name)]}
                    left={<Flex gap={"4px"}>
                        <Flex gap={"4px"} width={"200px"}>
                            <ProviderLogo providerId={g.category.provider} size={20} />
                            <Icon
                                name={Accounting.productTypeToIcon(g.category.productType)}
                                size={20} />
                            <code>{g.category.name}</code>
                        </Flex>
                    </Flex>}
                    right={<div className={"sub-alloc"}>
                        <ProgressBar uq={g.usageAndQuota} />
                        <Box width={25} height={25} />
                    </div>}
                    onActivate={open => {
                        if (open) setNodeState(TreeAction.OPEN, workspaceId, g.category.name);
                        else setNodeState(TreeAction.CLOSE, workspaceId, g.category.name);
                        listRef.current?.resetAfterIndex(rowIdx);
                    }}
                >
                    {g.allocations
                        .map((alloc, idx) =>
                            <TreeNode
                                key={alloc.allocationId}
                                className={alloc.note?.rowShouldBeGreyedOut ? "disabled-alloc" : undefined}
                                data-ridx={recipientIdx} data-idx={idx} data-gidx={gidx}
                                data-grant-id={alloc.grantedIn}
                                left={<Flex>
                                    <Flex width={"200px"}>
                                        <Icon name={"heroBanknotes"} ml="8px" mr={4} />
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
                                                mt={-6} />
                                        </Link>
                                    </>}
                                </Flex>}
                                right={<Flex flexDirection={"row"} gap={"8px"}>
                                    {alloc.note && <>
                                        <TooltipV2 tooltip={alloc.note.text}>
                                            <Icon name={alloc.note.icon}
                                                  color={alloc.note.iconColor} />
                                        </TooltipV2>
                                    </>}
                                    <Text>
                                        {Accounting.balanceToString(g.category, alloc.quota)}
                                    </Text>
                                    <SmallIconButton
                                        icon={"heroPencil"}
                                        onClick={(e) => openUpdater(
                                            g.category,
                                            alloc.allocationId,
                                            new Date(alloc.start),
                                            new Date(alloc.end),
                                            alloc.quota,
                                            dispatchEvent,
                                            idx,
                                            gidx,
                                            recipientIdx,
                                        )}
                                        disabled={alloc.end < new Date().getTime()}
                                        data-ridx={recipientIdx} data-idx={idx}
                                        data-gidx={gidx} />
                                </Flex>}
                            />
                        )
                    }
                </TreeNode>)}
        </TreeNode>
    </div>;
}

interface SubProjectFilter {
    title: string;
    options: string[];
    selected?: string;
    enabled: boolean;
    feature?: Feature;
}

const SubProjectFiltersRow: React.FunctionComponent<{
    setting: SubProjectFilter;
    onChange: (setting: SubProjectFilter) => void;
    state: State;
    dispatchEvent: (action: UIAction) => void;
}> = (props) => {

    const onChecked = useCallback(() => {
        if (props.setting.title === SingleUserProjects) {
            props.dispatchEvent({
                type: "ToggleViewOnlyProjects",
            });
        } else {
            props.onChange(produce(props.setting, draft => {
                draft.enabled = !draft.enabled;
            }));
        }
    }, [props.setting, props.onChange, props.dispatchEvent])

    const onSelectOption = useCallback((item: SimpleRichItem) => {
        props.onChange(produce(props.setting, draft => {
            draft.selected = item.key
        }))
    }, [props.setting, props.onChange])

    let selectedOpt = props.setting.selected;
    return <ListRow
        left={<Flex>
            <h3 className="sub-project-filter-title">{props.setting.title}</h3>
        </Flex>
        }
        right={<>
            {props.setting.options.length === 0 ? null : <div className="key-metrics-selector">
                <SimpleRichSelect
                    items={props.setting.options.map((it) => ({key: it, value: it}))}
                    onSelect={onSelectOption}
                    selected={selectedOpt ? {key: selectedOpt, value: selectedOpt} : undefined}
                    dropdownWidth={"300px"}
                />
            </div>}

            <div className="key-metrics-checkbox">
                <Checkbox
                    size={30}
                    checked={props.setting.title === SingleUserProjects ?
                        !props.state.viewOnlyProjects : props.setting.enabled}
                    handleWrapperClick={onChecked}
                    onChange={onChecked}
                />
            </div>
        </>
        }
    >
    </ListRow>
}

const SingleUserProjects = "Personal workspaces"

const subProjectsDefaultSettings: Record<string, SubProjectFilter> = {
    "Idle sub-projects": {
        title: "Idle sub-projects",
        options: ["1 month", "2 months", "3 months", "6 months"],
        selected: "1 month",
        enabled: false,
        feature: Feature.ALLOCATIONS_PAGE_IMPROVEMENTS,
    },
    "Allocated resource by product type": {
        title: "Allocated resource by product type",
        options: ["Compute", "Storage", "Public IP", "Application licence"],
        selected: "Compute",
        enabled: false,
        feature: Feature.ALLOCATIONS_PAGE_IMPROVEMENTS,
    },
    "Allocated resource by product": {
        title: "Allocated resource by product",
        options: [/*TODO list products here*/],
        selected: "u1-standard-h",
        enabled: false,
        feature: Feature.ALLOCATIONS_PAGE_IMPROVEMENTS,
    },
    "Allocated resource by provider": {
        title: "Allocated resource by provider",
        options: ["SDU/K8s", "AAU/K8s", "AAU/VM"],
        selected: "SDU/K8s",
        enabled: false,
        feature: Feature.ALLOCATIONS_PAGE_IMPROVEMENTS,
    },
    "Expired allocations": {
        title: "Expired allocations",
        options: [],
        selected: "",
        enabled: false,
        feature: Feature.ALLOCATIONS_PAGE_IMPROVEMENTS,
    },
    [SingleUserProjects]: {
        title: "Personal workspaces",
        options: [],
        selected: "",
        enabled: false,
    },
    "Overallocations at risk": {
        title: "Overallocations at risk",
        options: [],
        selected: "",
        enabled: false,
        feature: Feature.ALLOCATIONS_PAGE_IMPROVEMENTS,
    },
};

export const SubProjectFilters: React.FunctionComponent<{
    filtersShown: boolean;
    closeFilters: () => void;
    state: State;
    dispatchEvent: (event: UIEvent) => unknown;
}> = ({filtersShown, closeFilters, dispatchEvent, state}) => {
    const [settings, setSettings] = useState<Record<string, SubProjectFilter>>(subProjectsDefaultSettings);

    const onSettingsChanged = useCallback((setting: SubProjectFilter) => {
        setSettings(prev => {
            return produce(prev, draft => {
                draft[setting.title] = setting;
            });
        });
    }, [setSettings]);

    const [ascending, setAscending] = useState<boolean>(true);
    const [sortBy, setSortBy] = useState<SimpleRichItem>({key: "title", value: "Title"});

    useEffect(() => {
        dispatchEvent({
            type: "SortSubprojects",
            sortBy: sortBy.key,
            ascending: ascending
        })
    }, [ascending, sortBy]);

    const onSortingToggle = useCallback(() => {
        setAscending(current => !current);
    }, [setAscending]);

    return <ReactModal
        isOpen={filtersShown}
        shouldCloseOnEsc
        onRequestClose={closeFilters}
        style={largeModalStyle}
        ariaHideApp={false}
        className={classConcat(CardClass, keyMetricsStyle, subProjectsStyle)}
    >
        <Flex flexDirection={"column"} height={"100%"} width={"100%"}>
            <Flex>
                <div className="key-metrics-settings-container">
                    <h3>Sub-project filters</h3>
                </div>
                {/*<div className="key-metrics-input">*/}
                {/*    <div className="key-metrics-search-box">*/}
                {/*        <Input placeholder="Search in your sub-project filters"></Input>*/}
                {/*        <div style={{position: "relative"}}>*/}
                {/*            <div style={{position: "absolute", top: "5px", right: "10px"}}>*/}
                {/*                <Icon name={"heroMagnifyingGlass"}/>*/}
                {/*            </div>*/}
                {/*        </div>*/}
                {/*    </div>*/}
                {/*</div>*/}
            </Flex>
            {Object.values(settings).map(setting => (
                setting.feature === undefined || hasFeature(setting.feature) ?
                    <SubProjectFiltersRow
                        key={setting.title}
                        setting={setting}
                        onChange={onSettingsChanged}
                        dispatchEvent={dispatchEvent}
                        state={state}
                    /> : null
            ))}
            <Divider />
            <div className="sub-projects-sorting-container">
                <div className="sub-projects-sorting-headers">
                    <h3>Sort by</h3>
                    <h4 style={{color: "var(--textSecondary)"}}>Select sorting criteria to apply</h4>
                </div>
                <div className="sub-projects-sorting-selector">
                    <SimpleRichSelect
                        items={
                            [
                                {key: "title", value: "Title"},
                                {key: "PI", value: "PI"},
                                {key: "age", value: "Age"},
                                {key: "usagePercentageCompute", value: "Usage percentage (Compute)"},
                                {key: "usagePercentageStorage", value: "Usage percentage (Storage)"},
                                {key: "usagePercentagePublicIP", value: "Usage percentage (Public IP)"},
                                {key: "usagePercentageLicence", value: "Usage percentage (Application license)"},
                            ]
                        }
                        onSelect={setSortBy}
                        selected={sortBy}
                        dropdownWidth={"300px"}>
                    </SimpleRichSelect>
                    <div className="sort-button">
                        <TooltipV2 tooltip={ascending ? "Set to ascending" : "Set to descending"}>
                            <SmallIconButton
                                icon={ascending ? "heroBarsArrowUp" : "heroBarsArrowDown"}
                                onClick={onSortingToggle} />
                        </TooltipV2>
                    </div>
                </div>
            </div>

            <Box flexGrow={1} />

            <Flex justifyContent="end" px={"20px"} py={"12px"} margin={"-20px"} background={"var(--dialogToolbar)"} gap={"8px"}>
                <Button color={"successMain"} type="button" onClick={closeFilters}>Apply</Button>
            </Flex>
        </Flex>
    </ReactModal>;
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
    avatars: AvatarState
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
        avatars
    }
) => {
        const [filtersShown, setFiltersShown] = useState(false);
        const closeFilters = useCallback(() => {
            setFiltersShown(false);
        }, []);
        const openFilters = useCallback(() => {
            setFiltersShown(true);
        }, []);

        const rerender = useForcedRender();
        const setNodeStateHack = useCallback((action: TreeAction, reference: string, group?: string | null) => {
            setNodeState(action, reference, group);
            rerender();
        }, []);

        return <>
            <SubProjectFilters filtersShown={filtersShown} closeFilters={closeFilters}
                dispatchEvent={dispatchEvent} state={state} />

            <div className={subProjectsStyle}>
                {projectId !== undefined && <>
                    <Flex mt={32} mb={10} alignItems={"center"} gap={"8px"}>
                        <h3 style={{margin: 0}}>Sub-projects</h3>
                        <div className="sub-projects-search-bar-container">
                            <Box flexGrow={1} />
                            <Button className="new-sub-project-button" height={35} onClick={onNewSubProject}
                                disabled={projectRole == OldProjectRole.USER}>
                                <Icon name={"heroPlus"} mr={8} />
                                New sub-project
                            </Button>
                            <Box width={"355px"}>
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
                                        <Icon name={"heroMagnifyingGlass"} />
                                    </div>
                                </div>
                            </Box>
                            <Button className="filters-button" onClick={openFilters}>
                                <Icon name={"heroAdjustmentsHorizontal"} />
                            </Button>
                        </div>
                    </Flex>

                <div className="sub-projects-container" style={{height: "500px", width: "100%"}}>
                    {state.remoteData.wallets === undefined ? <>
                        <HexSpin size={64}/>
                    </> : <>
                        {state.filteredSubProjectIndices.length !== 0 ? null : <div style={{marginLeft: "20px", marginTop: "10px"}}>
                            You do not have any sub-allocations {state.searchQuery ? "with the active search" : ""} at
                            the moment. {" "}
                            {projectRole === OldProjectRole.USER ? null : <>
                                You can create a sub-project by clicking <a href="#" onClick={onNewSubProject}>here</a>.
                            </>}
                        </div>}
                        <AutoSizer>
                            {({height, width}) => (
                                <Tree
                                    apiRef={suballocationTree}
                                    onAction={(row, action) => {
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
                                    }}
                                    unhandledShortcut={onSubAllocationShortcut}
                                >
                                    <VariableSizeList
                                        itemSize={(idx) => calculateHeightInPx(idx, state)}
                                        height={height}
                                        width={width}
                                        ref={listRef}
                                        itemCount={state.filteredSubProjectIndices.length}
                                        itemData={state.filteredSubProjectIndices}
                                    >
                                        {({index: rowIdx, style, data}) => {
                                            const recipientIdx = data[rowIdx];
                                            const recipient = state.subAllocations.recipients[recipientIdx];

                                                return <SubProjectListRow
                                                    style={style}
                                                    recipient={recipient}
                                                    dispatchEvent={dispatchEvent}
                                                    listRef={listRef}
                                                    rowIdx={rowIdx}
                                                    recipientIdx={recipientIdx}
                                                    avatars={avatars}
                                                    state={state}
                                                    setNodeState={setNodeStateHack}
                                                />
                                            }}
                                        </VariableSizeList>
                                    </Tree>
                                )}
                            </AutoSizer>
                        </>}
                    </div>
                </>}
            </div>
        </>;
    }

function setNodeState(action: TreeAction, recipient: string, group?: string | null): void {
    const key = group ? makeCategoryKeyFromWorkspaceId(recipient, group) : recipient;
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

    const recipientIdx = state.filteredSubProjectIndices[idx];
    const recipient = state.subAllocations.recipients[recipientIdx];


    const workspaceId = recipient.owner.reference["username"] ?? recipient.owner.reference["projectId"] ?? "";
    let height = ROW_HEIGHT;
    const isOpen = openNodes[workspaceId];
    if (isOpen) {
        height += recipient.groups.length * ROW_HEIGHT;
        recipient.groups.forEach(g => {
            const isGroupOpen = openNodes[makeCategoryKeyFromWorkspaceId(workspaceId, g.category.name)];
            if (isGroupOpen) {
                height += g.allocations.length * ROW_HEIGHT;
            }
        });
    }

    return height;
}

function makeCategoryKeyFromWorkspaceId(workspace: string, name: Accounting.ProductCategoryV2["name"]): string {
    return workspace + "$$" + name;
}

let openNodes: Record<string, boolean> = {};

export function resetOpenNodes() {
    openNodes = {};
}

// Utility components
// =====================================================================================================================
// Various helper components used by the main user-interface.

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
    tooltip?: string;
    onClick?: (ev: HTMLButtonElement) => void;
    disabled?: boolean;
}> = props => {
    const ref = useRef<HTMLButtonElement>(null);
    const onClick = useCallback(() => {
        props?.onClick?.(ref.current!);
    }, [props.onClick]);

    const body = <Button
        className={SmallIconButtonStyle}
        onClick={onClick}
        color={props.color}
        disabled={props.disabled}
        btnRef={ref}
        data-has-sub={props.subIcon !== undefined}
        {...extractDataTags(props)}
    >
        <Icon name={props.icon} hoverColor={"primaryContrast"} />
        {props.subIcon &&
            <Relative>
                <div className={"sub"}>
                    <Icon name={props.subIcon} hoverColor={props.subColor1} color={props.subColor1}
                        color2={props.subColor2} />
                </div>
            </Relative>
        }
    </Button>;

    if (props.tooltip === undefined) {
        return body;
    } else {
        return <TooltipV2 tooltip={props.tooltip} contentWidth={200}>{body}</TooltipV2>;
    }
};
