import * as React from "react";
import {
    AllocationDisplayTreeRecipient,
    AllocationDisplayTreeYourAllocation,
    AllocationDisplayWallet,
    balanceToStringFromUnit,
    explainUnit,
    ProductType,
    UsageAndQuota,
    WalletV2
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
    Label,
    Link,
    Relative,
    Text,
    Truncate
} from "@/ui-components";
import AppRoutes from "@/Routes";
import * as Accounting from "@/Accounting";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {chunkedString, doNothing, timestampUnixMs} from "@/UtilityFunctions";
import {dateToStringNoTime} from "@/Utilities/DateUtilities";
import {TooltipV2} from "@/ui-components/Tooltip";
import {OldProjectRole} from "@/Project";
import {State, UIAction, UIEvent} from "@/Accounting/Allocations/State";
import {VariableSizeList} from "react-window";
import {AvatarState} from "@/AvataaarLib/hook";
import AutoSizer from "react-virtualized-auto-sizer";
import Avatar from "@/AvataaarLib/avatar";
import {NewAndImprovedProgress} from "@/ui-components/Progress";
import {classConcat, extractDataTags, injectStyle} from "@/Unstyled";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {useCallback, useEffect, useRef, useState} from "react";
import {ProgressBar} from "@/Accounting/Allocations/ProgressBar";
import {default as ReactModal} from "react-modal";
import {defaultModalStyle, largeModalStyle} from "@/Utilities/ModalUtilities";
import {CardClass} from "@/ui-components/Card";
import * as Heading from "@/ui-components/Heading";
import {ListRow} from "@/ui-components/List";
import {RichSelect, SimpleRichItem, SimpleRichSelect} from "@/ui-components/RichSelect";
import * as Pages from "@/Applications/Pages";
import {NotificationType} from "@/UserSettings/ChangeNotificationSettings";
import {produce} from "immer";
import {heroStar, sortAscending} from "@/ui-components/icons";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {SORT_BY} from "@/ui-components/ResourceBrowserFilters";
import {projectTitle} from "@/Project/ProjectSwitcher";
import {exportUsage, header} from "@/Accounting/Usage";
import {useProject} from "@/Project/cache";
import {useProjectId} from "@/Project/Api";
import {AllocationBar} from "@/Accounting/Allocations/AllocationBar";

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
                    <HexSpin size={64}/>
                </> : <>
                    <div>
                        {allocations.length !== 0 ? null : <>
                            You do not have any allocations at the moment. You can apply for resources{" "}
                            <Link to={AppRoutes.grants.editor()}>here</Link>.
                        </>}
                        <Tree apiRef={allocationTree}>
                            {allocations.map(([rawType, tree]) => {
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
                                                                    <Icon name={alloc.note.icon}
                                                                          color={alloc.note.iconColor}/>
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
        padding: 5px 20px 10px 20px;
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
        padding: 5px 0px 10px 0px;
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
                                <Icon name={"heroMagnifyingGlass"}/>
                            </div>
                        </div>
                    </div>
                    <Button onClick={closeFilters}>Apply</Button>
                </div>
            </Flex>

            {Object.values(settings).map(setting => (
                <KeyMetricSettingsRow key={setting.title} setting={setting} onChange={onSettingsChanged}/>
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
                                <Icon name={"heroMagnifyingGlass"}/>
                            </div>
                        </div>
                    </div>

                    <Button className="filters-button" onClick={openFilters}>
                        <Icon name={"heroAdjustmentsHorizontal"}/>
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
                                        <Icon name={Accounting.productTypeToIcon(type)} size={20}/>
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
                                            <ProviderLogo providerId={"ucloud"} size={20}/>
                                            <h3>u1-cephfs</h3>
                                        </Flex>
                                    }
                                    right={<Flex flexDirection={"row"} gap={"8px"}>
                                        {tree.usageAndQuota.map((uq, idx) => <React.Fragment key={idx}>
                                                <ProgressBar uq={uq}/>
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
                        <br/>
                        <h3>Storage <br/> utilization</h3>
                    </div>
                    <div className="key-metrics-card">
                        <h3>73</h3>
                        <br/>
                        <h3>Idle <br/> projects</h3>
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
            return <ProgressBar key={idx} uq={uq}/>;
        })}
    </>
}

const SubProjectListRow: React.FunctionComponent<{
    style: React.CSSProperties;
    recipient: AllocationDisplayTreeRecipient;
    listRef: React.RefObject<VariableSizeList<number[]> | null>;
    rowIdx: number;
    avatars: AvatarState;
    onEdit: (elem: HTMLElement) => void;
    state: State;
    onEditKey: (ev: React.KeyboardEvent) => Promise<void>;
    onEditBlur: (ev: React.SyntheticEvent) => void;
}> = ({style, recipient, listRef, rowIdx, avatars, onEdit, state, onEditKey, onEditBlur}) => {
    return <div style={style}>
        <TreeNode
            className={"sub-project-list-row"}
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
                    title={recipient.owner.title}
                    width={400}
                >
                    {recipient.owner.title}
                </Truncate>
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
                    </Link>
                }
                <FilteredUsageAndQuota entries={recipient.usageAndQuota}/>
                <SmallIconButton title="View grant application"
                                 icon={"heroBanknotes"}
                                 subIcon={"heroPlusCircle"}
                                 subColor1={"primaryContrast"}
                                 subColor2={"primaryContrast"}/>
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
                    right={<div className={"sub-alloc"}>
                        <ProgressBar uq={g.usageAndQuota}/>
                        <Box width={25} height={25}/>
                    </div>}
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
}

const SubProjectFiltersRow: React.FunctionComponent<{
    setting: SubProjectFilter;
    onChange: (setting: SubProjectFilter) => void;
    state: State;
    dispatchEvent: (action: UIAction) => void;
}> = (props) => {

    const onChecked = useCallback(() => {
        if (props.setting.title === "Single user sub-projects") {
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
                    checked={props.setting.title === "Single user sub-projects" ?
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

const subProjectsDefaultSettings: Record<string, SubProjectFilter> = {
    "Idle sub-projects": {
        title: "Idle sub-projects",
        options: ["1 month", "2 months", "3 months", "6 months"],
        selected: "1 month",
        enabled: false
    },
    "Allocated resource by product type": {
        title: "Allocated resource by product type",
        options: ["Compute", "Storage", "Public IP", "Application licence"],
        selected: "Compute",
        enabled: false
    },
    "Allocated resource by product": {
        title: "Allocated resource by product",
        options: [/*TODO list products here*/],
        selected: "u1-standard-h",
        enabled: false
    },
    "Allocated resource by provider": {
        title: "Allocated resource by provider",
        options: ["SDU/K8s", "AAU/K8s", "AAU/VM"],
        selected: "SDU/K8s",
        enabled: false
    },
    "Expired allocations": {
        title: "Expired allocations",
        options: [],
        selected: "",
        enabled: false
    },
    "Single user sub-projects": {
        title: "Single user sub-projects",
        options: [],
        selected: "",
        enabled: false
    },
    "Overallocations at risk": {
        title: "Overallocations at risk",
        options: [],
        selected: "",
        enabled: false
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
        <Flex>
            <div className="key-metrics-settings-container">
                <h3>Sub-project filters</h3>
                <h4 style={{color: "var(--textSecondary)"}}>Select filters to apply</h4>
            </div>
            <div className="key-metrics-input">
                <div className="key-metrics-search-box">
                    <Input placeholder="Search in your sub-project filters"></Input>
                    <div style={{position: "relative"}}>
                        <div style={{position: "absolute", top: "5px", right: "10px"}}>
                            <Icon name={"heroMagnifyingGlass"}/>
                        </div>
                    </div>
                </div>
                <Button onClick={closeFilters}>Apply</Button>
            </div>
        </Flex>
        {Object.values(settings).map(setting => (
            <SubProjectFiltersRow
                key={setting.title}
                setting={setting}
                onChange={onSettingsChanged}
                dispatchEvent={dispatchEvent}
                state={state}
            />
        ))}
        <Divider/>
        <div className="sub-projects-sorting-container">
            <div className="sub-projects-sorting-headers">
                <h3>Sub-project sorting</h3>
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
                            onClick={onSortingToggle}/>
                    </TooltipV2>
                </div>
            </div>
        </div>
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
    const [filtersShown, setFiltersShown] = useState(false);
    const closeFilters = useCallback(() => {
        setFiltersShown(false);
    }, []);
    const openFilters = useCallback(() => {
        setFiltersShown(true);
    }, []);

    return <>
        <SubProjectFilters filtersShown={filtersShown} closeFilters={closeFilters}
                           dispatchEvent={dispatchEvent} state={state}/>

        <div className={subProjectsStyle}>
            {projectId !== undefined && <>
                <Flex mt={32} mb={10} alignItems={"center"} gap={"8px"}>
                    <h3 style={{margin: 0}}>Sub-projects</h3>
                    <div className="sub-projects-search-bar-container">
                        <Box flexGrow={1}/>
                        <Button className="new-sub-project-button" height={35} onClick={onNewSubProject}
                                disabled={projectRole == OldProjectRole.USER}>
                            <Icon name={"heroPlus"} mr={8}/>
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
                                    <Icon name={"heroMagnifyingGlass"}/>
                                </div>
                            </div>
                        </Box>
                        <Button className="filters-button" onClick={openFilters}>
                            <Icon name={"heroAdjustmentsHorizontal"}/>
                        </Button>
                    </div>
                </Flex>

                <div className="sub-projects-container" style={{height: "500px", width: "100%"}}>
                    {state.remoteData.wallets === undefined ? <>
                        <HexSpin size={64}/>
                    </> : <>
                        {state.filteredSubProjectIndices.length !== 0 ? null : <>
                            You do not have any sub-allocations {state.searchQuery ? "with the active search" : ""} at
                            the
                            moment.
                            {projectRole === OldProjectRole.USER ? null : <>
                                You can create a sub-project by clicking <a href="#" onClick={onNewSubProject}>here</a>.
                            </>}
                        </>}
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
                                                listRef={listRef}
                                                rowIdx={rowIdx}
                                                avatars={avatars}
                                                onEdit={onEdit}
                                                state={state}
                                                onEditKey={onEditKey}
                                                onEditBlur={onEditBlur}
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

    const recipientIdx = state.filteredSubProjectIndices[idx];
    const recipient = state.subAllocations.recipients[recipientIdx];
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
