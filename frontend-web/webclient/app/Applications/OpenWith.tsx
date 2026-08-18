import * as React from "react";
import {BulkResponse, compute, FindByStringId} from "@/UCloud";
import {useState} from "react";
import JobsApi from "@/UCloud/JobsApi";
import {Button} from "@/ui-components";
import {bulkRequestOf, isLightThemeStored} from "@/UtilityFunctions";
import {getParentPath} from "@/Utilities/FileUtilities";
import {useNavigate} from "react-router-dom";
import {browseWalletsV2, ProductV2Compute, WalletV2} from "@/Accounting";
import {dialogStore} from "@/Dialog/DialogStore";
import * as UCloud from "@/UCloud";
import {displayErrorMessageOrDefault, joinToString} from "@/UtilityFunctions";
import {findRelevantMachinesForApplication, Machines} from "@/Applications/Jobs/Widgets/Machines";
import {ResolvedSupport} from "@/UCloud/ResourceApi";
import {callAPI as baseCallAPI} from "@/Authentication/DataHook";
import {Client} from "@/Authentication/HttpClientInstance";
import {
    ResourceBrowser,
    ResourceBrowserOpts,
    addProjectSwitcherInPortal,
    checkCanConsumeResources,
    EmptyReasonTag
} from "@/ui-components/ResourceBrowser";
import {projectTitleFromCache} from "@/Project/ProjectSwitcher";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {UFile} from "@/UCloud/UFile";
import {emptyPageV2} from "@/Utilities/PageUtilities";
import * as AppStore from "@/Applications/AppStoreApi";
import {ApplicationWithExtension} from "@/Applications/AppStoreApi";
import {sendFailureNotification} from "@/Notifications";

export interface OpenWithFastPath {
    application: {
        name: string;
        version?: string;
    };
    parameters?: Record<string, UCloud.compute.AppParameterValue>;
    machine?: {
        preferredVcpuCount?: number;
    };
}

type OpenWithLaunchResult = {
    jobId: string;
    projectId: string | null;
    applicationName: string;
    applicationVersion: string;
};

type ApiCaller = <T>(parameters: APICallParameters<unknown, T>) => Promise<T>;

export async function launchOpenWithFastPath(file: UFile, fastPath: OpenWithFastPath): Promise<OpenWithLaunchResult> {
    const projectId = Client.projectId;
    const provider = file.specification.product.provider;
    const callInProject: ApiCaller = parameters => baseCallAPI({
        ...parameters,
        projectOverride: projectId ?? ""
    });

    const [application, products, machineSupport, wallets] = await Promise.all([
        callInProject(AppStore.findByNameAndVersion({
            appName: fastPath.application.name,
            appVersion: fastPath.application.version
        })),
        callInProject(UCloud.accounting.products.browse({
            filterUsable: true,
            filterProductType: "COMPUTE",
            itemsPerPage: 250,
            includeBalance: true,
            includeMaxBalance: true
        })),
        callInProject(UCloud.compute.jobs.retrieveProducts({providers: provider})),
        fetchWallets(callInProject)
    ]);

    const relevantMachines = findRelevantMachinesForApplication(
        application,
        machineSupport,
        (products as unknown as UCloud.PageV2<ProductV2Compute>).items,
        wallets
    );
    const preferredVcpuCount = fastPath.machine?.preferredVcpuCount ?? 4;
    const machine = relevantMachines
        .filter(product => product.category.provider === provider)
        .filter(product => (product.gpu ?? 0) === 0 && product.cpu != null)
        .sort((a, b) => {
            const distance = Math.abs(a.cpu! - preferredVcpuCount) - Math.abs(b.cpu! - preferredVcpuCount);
            if (distance !== 0) return distance;
            if (a.cpu !== b.cpu) return a.cpu! - b.cpu!;
            return `${a.category.name}/${a.name}`.localeCompare(`${b.category.name}/${b.name}`);
        })[0];

    if (!machine) {
        throw new Error(`No suitable CPU-only machine is available from ${provider}.`);
    }

    const jobId = await submitOpenWithJob(file, application.metadata.name, application.metadata.version, machine, callInProject, fastPath.parameters);
    return {
        jobId,
        projectId: projectId ?? null,
        applicationName: application.metadata.name,
        applicationVersion: application.metadata.version
    };
}

export function OpenWithBrowser({opts, file}: {file: UFile, opts?: ResourceBrowserOpts<ApplicationWithExtension>}): React.ReactNode {
    const [selectedProduct, setSelectedProduct] = useState<ProductV2Compute | null>(null);
    const browserRef = React.useRef<ResourceBrowser<ApplicationWithExtension> | null>(null);
    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const navigate = useNavigate();
    const supportRef = React.useRef<ResolvedSupport[]>([]);
    const productsRef = React.useRef<ProductV2Compute[]>([]);
    const productComputeRef = React.useRef<UCloud.PageV2<ProductV2Compute>>(emptyPageV2);
    const machineSupportRef = React.useRef<compute.JobsRetrieveProductsResponse>(null);
    const walletsRef = React.useRef<WalletV2[]>([]);
    const machineInfoPromiseRef = React.useRef<Promise<void> | null>(null);
    const machineInfoGenerationRef = React.useRef(0);
    const [machineInfoLoading, setMachineInfoLoading] = React.useState(false);

    const activeProject = React.useRef(Client.projectId);

    function callAPI<T>(parameters: APICallParameters<unknown, T>): Promise<T> {
        return baseCallAPI({
            ...parameters,
            projectOverride: activeProject.current ?? ""
        });
    }

    const normalizedFileId = file.status.type === "DIRECTORY" ? `${file.id}/` : file.id;

    const [selectedApp, setSelectedApp] = React.useState<ApplicationWithExtension | undefined>(undefined);

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser(mount, "Launch with", opts).init(browserRef, {
                breadcrumbsSeparatedBySlashes: false,
                projectSwitcher: true,
            }, "", browser => {
                fetchInfo();

                browser.setEmptyIcon("heroServer");

                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) return;
                    callAPI(AppStore.browseOpenWithRecommendations({
                        files: [normalizedFileId],
                        itemsPerPage: 50
                    })).then(apps => {
                        browser.registerPage(apps, newPath, true);
                        browser.renderRows();
                    });
                });

                browser.setEmptyIcon("play");

                browser.on("renderRow", (entry, row, dimensions) => {
                    const [icon, setIcon] = ResourceBrowser.defaultIconRenderer();
                    icon.style.minWidth = "20px"
                    icon.style.minHeight = "20px"
                    row.title.append(icon);

                    row.title.append(ResourceBrowser.defaultTitleRenderer(entry.metadata.title, row));

                    setIcon(AppStore.retrieveAppLogo({
                        name: entry.metadata.name,
                        darkMode: !isLightThemeStored(),
                        includeText: false,
                        placeTextUnderLogo: false,
                    }));

                    const button = browser.defaultButtonRenderer({
                        onClick: async () => {
                            try {
                                await waitForLatestMachineInfo();
                                const resolvedApplication = await callAPI(
                                    AppStore.findByNameAndVersion({
                                        appName: entry.metadata.name,
                                        appVersion: entry.metadata.version
                                    })
                                );

                                productsRef.current = !resolvedApplication ? [] :
                                    findRelevantMachinesForApplication(resolvedApplication, machineSupportRef.current!, productComputeRef.current.items, walletsRef.current);

                                setSelectedApp(entry)
                            } catch (error) {
                                displayErrorMessageOrDefault(error, "Failed to fetch application info.")
                            }
                        },
                        show: () => true,
                        text: "Launch"
                    }, entry);
                    if (button) {
                        row.stat3.replaceChildren(button);
                    }
                });

                browser.on("renderEmptyPage", reason => {
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append(`We are fetching applications...`);
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS:
                        case EmptyReasonTag.EMPTY: {
                            e.reason.append("Couldn't find any suitable applications for this file.")
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append(`We are currently unable to show any applications. Try again later.`);
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }
                });
                browser.on("unhandledShortcut", () => void 0);
                browser.on("generateBreadcrumbs", path => browser.defaultBreadcrumbs());
                browser.on("fetchOperationsCallback", () => ({}));
                browser.on("fetchOperations", () => []);

                browser.on("wantToFetchNextPage", async path => {
                    const result = await callAPI(AppStore.browseOpenWithRecommendations({
                        files: [normalizedFileId],
                        itemsPerPage: 50
                    }));

                    if (path !== browser.currentPath) return;

                    browser.registerPage(result, path, false);
                });
            });
        }
        addProjectSwitcherInPortal(browserRef, setSwitcherWorkaround, {setLocalProject});
    }, []);

    const setLocalProject = async (projectId?: string) => {
        activeProject.current = projectId;
        fetchInfo();
        const b = browserRef.current;
        if (b) {
            const canConsumeResources = await checkCanConsumeResources(projectId ?? null, {api: JobsApi});
            if (activeProject.current === projectId) b.canConsumeResources = canConsumeResources;
        }
    };

    if (!opts?.embedded && !opts?.isModal) {
        useSetRefreshFunction(() => {
            browserRef.current?.refresh();
        });
    }

    return <div>
        <div ref={mountRef} style={selectedApp ? {display: "none"} : undefined} />
        {switcher}
        {selectedApp ? <>
            <Machines
                machines={productsRef.current}
                support={supportRef.current}
                onMachineChange={setSelectedProduct}
                loading={machineInfoLoading}
            />
            <Button mt={"8px"} fullWidth onClick={async () => {
                if (!selectedApp || !selectedProduct) return;
                try {
                    const jobId = await submitOpenWithJob(
                        file,
                        selectedApp.metadata.name,
                        selectedApp.metadata.version,
                        selectedProduct,
                        callAPI
                    );

                    dialogStore.success();
                    navigate(`/jobs/properties/${jobId}?app=${selectedApp.metadata.name}`);
                } catch (e) {
                    sendFailureNotification("UCloud failed to submit the job");
                }
            }} disabled={!selectedProduct}>Launch {isActiveProject(activeProject.current)}</Button>
        </> : null}
    </div>;

    function fetchInfo(): Promise<void> {
        const generation = ++machineInfoGenerationRef.current;
        const projectId = activeProject.current;
        const callInProject: ApiCaller = parameters => baseCallAPI({
            ...parameters,
            projectOverride: projectId ?? ""
        });
        productComputeRef.current = emptyPageV2;
        machineSupportRef.current = {productsByProvider: {}};
        walletsRef.current = [];
        supportRef.current = [];
        setMachineInfoLoading(true);

        const promise = Promise.all([
            callInProject(UCloud.accounting.products.browse({
                filterUsable: true,
                filterProductType: "COMPUTE",
                itemsPerPage: 250,
                includeBalance: true,
                includeMaxBalance: true
            })),
            fetchWallets(callInProject)
        ]).then(async ([products, wallets]) => {
            const providers = new Set(products.items.map(it => it.category.provider));
            let support: compute.JobsRetrieveProductsResponse = {productsByProvider: {}};
            if (providers.size > 0) {
                support = await callInProject(UCloud.compute.jobs.retrieveProducts({
                    providers: joinToString(Array.from(providers), ",")
                }));
            }

            if (generation !== machineInfoGenerationRef.current) return;
            productComputeRef.current = products as unknown as UCloud.PageV2<ProductV2Compute>;
            machineSupportRef.current = support;
            walletsRef.current = wallets;
            supportRef.current = Object.values(support.productsByProvider)
                .flatMap(providerProducts => providerProducts as unknown as ResolvedSupport[]);
        }).finally(() => {
            if (generation === machineInfoGenerationRef.current) setMachineInfoLoading(false);
        });
        machineInfoPromiseRef.current = promise;
        promise.catch(err => {
            if (generation === machineInfoGenerationRef.current) {
                displayErrorMessageOrDefault(err, "Failed to fetch machines.");
            }
        });
        return promise;
    }

    async function waitForLatestMachineInfo(): Promise<void> {
        while (true) {
            const promise = machineInfoPromiseRef.current ?? fetchInfo();
            try {
                await promise;
            } catch (error) {
                if (promise !== machineInfoPromiseRef.current) continue;
                throw error;
            }
            if (promise === machineInfoPromiseRef.current) return;
        }
    }
}

async function fetchWallets(apiCall: ApiCaller, next?: string): Promise<WalletV2[]> {
    const result = await apiCall(browseWalletsV2({
        itemsPerPage: 250,
        next
    }));

    if (result.next) {
        return result.items.concat(await fetchWallets(apiCall, result.next));
    }

    return result.items;
}

async function submitOpenWithJob(
    file: UFile,
    applicationName: string,
    applicationVersion: string,
    product: ProductV2Compute,
    apiCall: ApiCaller,
    parameters: Record<string, UCloud.compute.AppParameterValue> = {}
): Promise<string> {
    let parent = getParentPath(file.id);
    if (parent === "/") parent = file.id;

    const response = await apiCall<BulkResponse<FindByStringId | null>>(
        JobsApi.create(bulkRequestOf({
            application: {name: applicationName, version: applicationVersion},
            product: {
                id: product.name,
                provider: product.category.provider,
                category: product.category.name
            },
            parameters,
            replicas: 1,
            allowDuplicateJob: true,
            timeAllocation: {hours: 3, minutes: 0, seconds: 0},
            resources: [{
                type: "file",
                path: parent,
                readOnly: false
            }],
            openedFile: file.id
        }))
    );
    const jobId = response.responses?.[0]?.id;
    if (!jobId) throw new Error("UCloud failed to submit the job");
    return jobId;
}

function isActiveProject(projectId: string | undefined) {
    if (projectId === undefined || projectId === Client.projectId) return "";
    return "with " + projectTitleFromCache(projectId);
}
