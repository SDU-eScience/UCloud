import * as React from "react";
import {UFile} from "@/UCloud/FilesApi";
import {apiUpdate} from "@/Authentication/DataHook";
import {BulkResponse, compute, FindByStringId, PaginationRequestV2} from "@/UCloud";
import {useState} from "react";
import {appLogoCache} from "@/Applications/AppToolLogo";
import JobsApi from "@/UCloud/JobsApi";
import {Button} from "@/ui-components";
import {bulkRequestOf, emptyPageV2} from "@/DefaultObjects";
import {getParentPath} from "@/Utilities/FileUtilities";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {useNavigate} from "react-router";
import {ProductV2, ProductV2Compute} from "@/Accounting";
import {dialogStore} from "@/Dialog/DialogStore";
import * as UCloud from "@/UCloud";
import {displayErrorMessageOrDefault, joinToString} from "@/UtilityFunctions";
import {findRelevantMachinesForApplication, Machines} from "@/Applications/Jobs/Widgets/Machines";
import {ResolvedSupport} from "@/UCloud/ResourceApi";
import {callAPI as baseCallAPI} from "@/Authentication/DataHook";
import {Client} from "@/Authentication/HttpClientInstance";
import {ResourceBrowser, ResourceBrowserOpts, addContextSwitcherInPortal, checkCanConsumeResources} from "@/ui-components/ResourceBrowser";
import {logoDataUrls} from "./Jobs/JobsBrowse";
import {AppLogo, hashF} from "./Card";
import {projectTitleFromCache} from "@/Project/ContextSwitcher";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";

function findApplicationsByExtension(
    request: {files: string[]} & PaginationRequestV2
): APICallParameters<{files: string[]} & PaginationRequestV2> {
    return apiUpdate(request, "/api/hpc/apps", "bySupportedFileExtension");
}

export function OpenWithBrowser({opts, file}: {file: UFile, opts?: ResourceBrowserOpts<UCloud.compute.Application>}): React.ReactNode {
    const [selectedProduct, setSelectedProduct] = useState<ProductV2 | null>(null);
    const browserRef = React.useRef<ResourceBrowser<UCloud.compute.Application> | null>(null);
    const [switcher, setSwitcherWorkaround] = React.useState<JSX.Element>(<></>);
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const navigate = useNavigate();
    const supportRef = React.useRef<ResolvedSupport[]>([]);
    const productsRef = React.useRef<ProductV2Compute[]>([]);
    const walletsRef = React.useRef<UCloud.PageV2<ProductV2Compute>>(emptyPageV2)
    const machineSupportRef = React.useRef<compute.JobsRetrieveProductsResponse>();

    const activeProject = React.useRef(Client.projectId);

    function callAPI<T>(parameters: APICallParameters<unknown, T>): Promise<T> {
        return baseCallAPI({
            ...parameters,
            projectOverride: activeProject.current ?? ""
        });
    }

    const normalizedFileId = file.status.type === "DIRECTORY" ? `${file.id}/` : file.id;

    const [selectedApp, setSelectedApp] = React.useState<UCloud.compute.Application | undefined>(undefined);

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser(mount, "Launch with", opts).init(browserRef, {
                breadcrumbsSeparatedBySlashes: false,
                contextSwitcher: true,
            }, "", browser => {
                fetchInfo();

                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) return;

                    callAPI(findApplicationsByExtension({
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

                    row.title.append(ResourceBrowser.defaultTitleRenderer(entry.metadata.title, dimensions, row));

                    logoDataUrls.retrieve(entry.metadata.name, async () => {
                        const result = await appLogoCache.fetchLogo(entry.metadata.name);
                        if (result !== null) {
                            return result;
                        }

                        return await browser.icons.renderSvg(
                            entry.metadata.name,
                            () => <AppLogo size="32px" hash={hashF(entry.metadata.name)} />,
                            32,
                            32
                        ).then(it => it).catch(e => {
                            console.log("render SVG error", e);
                            return "";
                        });
                    }).then(result => {
                        if (result) {
                            setIcon(result);
                        }
                    });

                    const button = browser.defaultButtonRenderer({
                        onClick: async () => {
                            try {
                                const resolvedApplication = await callAPI(
                                    UCloud.compute.apps.findByNameAndVersion({
                                        appName: entry.metadata.name,
                                        appVersion: entry.metadata.version
                                    })
                                );

                                productsRef.current = !resolvedApplication ? [] :
                                    findRelevantMachinesForApplication(resolvedApplication, machineSupportRef.current!, walletsRef.current);

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

                browser.on("renderEmptyPage", reason => browser.defaultEmptyPage("applications", reason, {}));
                browser.on("unhandledShortcut", () => void 0);
                browser.on("generateBreadcrumbs", path => browser.defaultBreadcrumbs());
                browser.on("fetchOperationsCallback", () => ({}));
                browser.on("fetchOperations", () => []);

                browser.on("wantToFetchNextPage", async path => {
                    const result = await callAPI(findApplicationsByExtension({
                        files: [normalizedFileId],
                        itemsPerPage: 50
                    }));

                    if (path !== browser.currentPath) return;

                    browser.registerPage(result, path, false);
                });
            });
        }
        addContextSwitcherInPortal(browserRef, setSwitcherWorkaround, {setLocalProject});
    }, []);

    const setLocalProject = (projectId?: string) => {
        const b = browserRef.current;
        if (b) {
            b.canConsumeResources = checkCanConsumeResources(projectId ?? null, {api: JobsApi});
        }
        activeProject.current = projectId;
        fetchInfo();
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
                loading={false}
            />
            <Button mt={"8px"} fullWidth onClick={async () => {
                if (!selectedApp || !selectedProduct) return;
                try {
                    const response = await callAPI<BulkResponse<FindByStringId | null>>(
                        JobsApi.create(bulkRequestOf({
                            application: {
                                name: selectedApp.metadata.name,
                                version: selectedApp.metadata.version,
                            },
                            product: {
                                id: selectedProduct.name,
                                provider: selectedProduct.category.provider,
                                category: selectedProduct.category.name
                            },
                            parameters: {},
                            replicas: 1,
                            allowDuplicateJob: true,
                            timeAllocation: {
                                hours: 3,
                                minutes: 0,
                                seconds: 0
                            },
                            resources: [{
                                type: "file",
                                path: file.status.type === "DIRECTORY" ? file.id : getParentPath(file.id),
                                readOnly: false
                            }],
                            openedFile: file.id
                        }))
                    );

                    dialogStore.success();

                    const ids = response?.responses;
                    if (!ids || ids.length === 0) {
                        snackbarStore.addFailure("UCloud failed to submit the job", false);
                        return;
                    }

                    navigate(`/jobs/properties/${ids[0]?.id}?app=${selectedApp.metadata.name}`);
                } catch (e) {
                    snackbarStore.addFailure("UCloud failed to submit the job", false);
                }
            }} disabled={!selectedProduct}>Launch {isActiveProject(activeProject.current)}</Button>
        </> : null}
    </div>;

    function fetchInfo() {
        walletsRef.current = emptyPageV2;
        machineSupportRef.current = {productsByProvider: {}};
        supportRef.current = [];

        callAPI(UCloud.accounting.products.browse({
            filterUsable: true,
            filterProductType: "COMPUTE",
            itemsPerPage: 250,
            includeBalance: true,
            includeMaxBalance: true
        })).then((products) => {
            walletsRef.current = products as unknown as UCloud.PageV2<ProductV2Compute>;

            const providers = new Set(products.items.map(it => it.category.provider));

            if (providers.size > 0) {
                callAPI(UCloud.compute.jobs.retrieveProducts({
                    providers: joinToString(Array.from(providers), ",")
                })).then(support => {
                    machineSupportRef.current = support;
                    const items: ResolvedSupport[] = [];
                    let productsByProvider = support.productsByProvider;
                    for (const provider of Object.keys(productsByProvider)) {
                        const providerProducts = productsByProvider[provider];
                        // TODO(Dan): We need to fix some of these types soon. We are still using a lot of the old generated stuff.
                        for (const item of providerProducts) items.push((item as unknown) as ResolvedSupport);
                    }
                    supportRef.current = items;
                }).catch(err => displayErrorMessageOrDefault(err, "Failed to fetch support."));
            }
        }).catch(err => displayErrorMessageOrDefault(err, "Failed to fetch products."));
    }
}

function isActiveProject(projectId: string | undefined) {
    if (projectId === undefined || projectId === Client.projectId) return "";
    return "with " + projectTitleFromCache(projectId);
}