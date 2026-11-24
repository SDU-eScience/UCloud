import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import {useLocation} from "react-router-dom";
import AppRoutes from "@/Routes";
import {Box, MainContainer} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {useProjectId} from "@/Project/Api";
import {useProject} from "@/Project/cache";
import {isAdminOrPI, OldProjectRole} from "@/Project";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";
import {useProjectInfos} from "@/Project/InfoCache";
import {ShortcutHint, useShortcutReveal} from "@/Applications/KeyboardNavigation";
import {injectStyle} from "@/Unstyled";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {callAPI} from "@/Authentication/DataHook";
import {fetchAll} from "@/Utilities/PageUtilities";
import {browseWalletsV2} from "@/Accounting";
import type {WalletV2} from "@/Accounting";
import {currentCalendarYear, normalize} from "./Model";
import type {DateRange} from "./Model";
import {PeriodFilter, focusTable, useShortcuts} from "./Controls";
import {Granted} from "./Granted";
import {Received} from "./Received";
import {ProviderTools, useHasProviderTools, useProviderToolsState} from "./ProviderTools";

const PageStyle = injectStyle("allocation-page", k => `
    ${k}[data-mode="allocations"] {
        display: flex;
        flex-direction: column;
        height: calc(100vh - var(--termsize, 0px) - 32px);
        min-height: 0;
        min-width: 0;
    }
    ${k} > header {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 12px;
        margin-bottom: 16px;
        flex: 0 0 auto;
    }
    ${k} > header h3 {
        margin: 0;
    }
    ${k} [data-allocation-shortcut-hint] {
        display: none;
    }
    ${k}[data-shortcuts-visible="true"] [data-allocation-shortcut-hint] {
        display: inline-flex;
    }
`);

type PageMode = "received" | "sub-allocated" | "provider-tools";

function usePageMode(): PageMode {
    const {pathname} = useLocation();
    if (pathname.endsWith(AppRoutes.accounting.providerTools())) return "provider-tools";
    if (pathname.endsWith(AppRoutes.accounting.subAllocations())) return "sub-allocated";
    return "received";
}

async function loadWallets(): Promise<WalletV2[]> {
    return await fetchAll(next => callAPI(browseWalletsV2({
        itemsPerPage: 250,
        next,
        includeChildren: true,
    })));
}

function useWallets(projectId?: string): {
    wallets?: WalletV2[];
    reload: () => Promise<void>;
} {
    const didUnmount = useDidUnmount();
    const [wallets, setWallets] = useState<WalletV2[]>();

    const reload = useCallback(async () => {
        const next = await loadWallets();
        if (!didUnmount.current) setWallets(next);
    }, [didUnmount]);

    useEffect(() => {
        setWallets(undefined);
        void reload();
    }, [projectId, reload]);

    return {wallets, reload};
}

function Page(props: {
    mode: PageMode;
    wallets?: WalletV2[];
    loading: boolean;
    onReloadWallets: () => Promise<void>;
    projectId?: string;
    projectRole: OldProjectRole;
    canCreateSubAllocator: boolean;
    personalWorkspaces: boolean;
    onPersonalWorkspacesChange: () => void;
    dateRange: DateRange;
    onDateRangeChange: (range: DateRange) => void;
    providerTools?: React.ReactNode;
}): React.ReactNode {
    const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
    const shortcutsVisible = useShortcutReveal();
    useShortcuts();
    const model = useMemo(() => normalize(props.wallets ?? [], Date.now()), [props.wallets]);
    const projectIds = useMemo(() => {
        const ids = new Set<string>();
        for (const wallet of props.wallets ?? []) {
            for (const child of wallet.children ?? []) {
                if (child.child.projectId) ids.add(child.child.projectId);
            }
            for (const group of wallet.allocationGroups) {
                if (group.parent?.projectId) ids.add(group.parent.projectId);
            }
        }
        return [...ids];
    }, [props.wallets]);
    const projectInfo = useProjectInfos(projectIds).data;
    const periodControl = <PeriodFilter
        now={model.now}
        value={props.dateRange}
        onChange={props.onDateRangeChange}
        shortcut="K"
        onReturnFocus={focusTable}
    />;
    const canApply = !props.projectId || isAdminOrPI(props.projectRole);

    useEffect(() => {
        setSelectedCategories([]);
    }, [props.mode, props.projectId]);

    const received = props.mode === "received";
    const providerTools = props.mode === "provider-tools";

    return <MainContainer headerSize={0} main={<div className={PageStyle}
        data-mode={providerTools ? "provider" : "allocations"}
        data-shortcuts-visible={shortcutsVisible ? "true" : undefined}>
        <header>
            {providerTools ?
                <h3>Gifts and root allocations</h3> :
                <h3>{received ? "Received allocations" : "Resource allocations"}</h3>}
            {received ? <ShortcutHint shortcut="S" hideUntilReveal /> : null}
            <Box flexGrow={1} />
            <ProjectSwitcher />
        </header>
        {providerTools ?
            props.providerTools :
            received ?
                <Received
                    model={model}
                    loading={props.loading}
                    dateRange={props.dateRange}
                    periodControl={periodControl}
                    projectId={props.projectId}
                    canApply={canApply}
                    projectInfo={projectInfo}
                /> :
                <Granted
                    projectId={props.projectId}
                    projectRole={props.projectRole}
                    canCreateSubAllocator={props.canCreateSubAllocator}
                    model={model}
                    loading={props.loading}
                    personalWorkspaces={props.personalWorkspaces}
                    onPersonalWorkspacesChange={props.onPersonalWorkspacesChange}
                    projectInfo={projectInfo}
                    dateRange={props.dateRange}
                    selectedCategories={selectedCategories}
                    onCategoriesChange={setSelectedCategories}
                    periodControl={periodControl}
                    onReloadWallets={props.onReloadWallets}
                />
        }
    </div>} />;
}

const Allocations: React.FunctionComponent = () => {
    const projectId = useProjectId();
    const projectState = useProject();
    const project = projectState.fetch();
    const projectRole = project.status.myRole ?? OldProjectRole.USER;
    const mode = usePageMode();
    const {wallets, reload} = useWallets(projectId);
    const [providerToolsState, dispatchProviderToolsEvent] = useProviderToolsState();
    const hasProviderTools = useHasProviderTools();
    const [dateRange, setDateRange] = useState(currentCalendarYear);
    const [personalWorkspaces, setPersonalWorkspaces] = useState(false);

    useEffect(() => {
        setPersonalWorkspaces(false);
    }, [projectId]);

    usePage(
        mode === "provider-tools"
            ? "Gifts and root allocations"
            : mode === "sub-allocated"
                ? "Allocations"
                : "Received allocations",
        SidebarTabId.PROJECT,
    );

    if (project.status.personalProviderProjectFor != null) {
        return <MainContainer
            main={
                <>
                    <Heading.h2>Unavailable for this project</Heading.h2>
                    <p>
                        This project belongs to a provider which does not support the accounting and project management
                        features of UCloud. Try again with a different project.
                    </p>
                </>
            }
        />
    }

    const hasManagedProviders = (providerToolsState.remoteData.managedProviders ?? []).length > 0;
    const canCreateSubAllocator = hasManagedProviders || project.specification.canConsumeResources === false;

    return <Page
        mode={mode}
        wallets={wallets}
        loading={wallets === undefined}
        onReloadWallets={reload}
        dateRange={dateRange}
        onDateRangeChange={setDateRange}
        projectId={projectId}
        projectRole={projectRole}
        canCreateSubAllocator={canCreateSubAllocator}
        personalWorkspaces={personalWorkspaces}
        onPersonalWorkspacesChange={() => setPersonalWorkspaces(previous => !previous)}
        providerTools={hasProviderTools ? <ProviderTools
            state={providerToolsState}
            dispatchEvent={dispatchProviderToolsEvent}
            hasManagedProviders={hasManagedProviders}
        /> : undefined}
    />;
};

export default Allocations;
