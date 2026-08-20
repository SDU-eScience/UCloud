import * as React from "react";
import {MainContainer} from "@/ui-components";
import {addProjectSwitcherInPortal, EmptyReasonTag, providerIcon, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts} from "@/ui-components/ResourceBrowser";
import {useNavigate} from "react-router-dom";
import {useDispatch} from "react-redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {usePage} from "@/Navigation/Redux";
import * as Api from "./api";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {callAPI, useCloudAPI} from "@/Authentication/DataHook";
import {Operation, ShortcutKey} from "@/ui-components/Operation";
import {StandardCallbacks} from "@/ui-components/Browse";
import AppRoutes from "@/Routes";
import {formatTs} from "./Add";
import {SimpleAvatarComponentCache} from "@/Files/Shares";
import {divText} from "@/Utilities/HTMLUtilities";
import {TruncateClass} from "@/ui-components/Truncate";
import {copyToClipboard} from "@/UtilityFunctions";
import {sendInformationNotification} from "@/Notifications";
import {HTMLTooltip} from "@/ui-components/Tooltip";
import {IconName} from "@/ui-components/Icon";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
}

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    sorting: true,
    filters: false,
    breadcrumbsSeparatedBySlashes: false,
    projectSwitcher: true,
    showColumnTitles: true,
    dragToSelect: true,
};

export function ApiTokenBrowse(props: {opts?: ResourceBrowserOpts<Api.ApiToken>}): React.ReactNode {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<Api.ApiToken> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    usePage("API tokens", SidebarTabId.RESOURCES);
    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);
    const [options] = useCloudAPI(Api.retrieveOptions(), {byProvider: {}});
    const optionsRef = React.useRef(options.data);
    optionsRef.current = options.data;

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<Api.ApiToken>(mount, "API tokens", props.opts).init(browserRef, FEATURES, "", browser => {
                browser.setColumns([{name: "Title"}, {name: "Created by", columnWidth: 200}, {name: "Expires at", columnWidth: 200}, {name: "Server URL", columnWidth: 320}]);

                browser.on("skipOpen", (oldPath, path, resource) => resource != null);

                browser.on("open", (oldPath, newPath, resource) => {
                    // For initial fetch.
                    callAPI(Api.browse({
                        ...defaultRetrieveFlags,
                        ...browser.browseFilters,
                        ...props.opts?.additionalFilters,
                        filterHidden: true,
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                    })
                });

                browser.on("unhandledShortcut", () => {});

                browser.on("wantToFetchNextPage", async path => {
                    const result = await callAPI(
                        Api.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...props.opts?.additionalFilters,
                            filterHidden: true,
                        })
                    )

                    if (path !== browser.currentPath) return; // Shouldn't be possible.

                    browser.registerPage(result, path, false);
                });

                browser.on("renderRow", (token, row, dims) => {
                    const isUCloudCore = !token.specification.provider;
                    const pIcon = providerIcon(token.specification.provider ?? "", undefined, isUCloudCore ? "ucloud.png" : undefined);
                    pIcon.style.marginRight = "8px";
                    row.title.append(pIcon);

                    const context = tokenContextFromOptions(token, optionsRef.current);
                    if (context) {
                        const tooltip = context === "personal"
                            ? "This token can be used for all your projects"
                            : "This token is bound to the current project";
                        const contextIcon = document.createElement("img");
                        contextIcon.alt = tooltip;
                        contextIcon.style.width = "20px";
                        contextIcon.style.height = "20px";
                        contextIcon.style.marginRight = "8px";
                        HTMLTooltip(contextIcon, divText(tooltip));
                        ResourceBrowser.icons.renderIcon({
                            name: (context === "personal" ? "heroGlobeEuropeAfrica" : "heroLockClosed") as IconName,
                            color: "iconColor",
                            color2: "iconColor2",
                            width: 20,
                            height: 20,
                        }).then(source => {
                            contextIcon.src = source;
                        });
                        row.title.append(contextIcon);
                    }

                    row.title.append(ResourceBrowser.defaultTitleRenderer(token.specification.title, row));

                    row.stat1.style.justifyContent = "left";
                    SimpleAvatarComponentCache.appendTo(row.stat1, token.owner.createdBy, `Created by ${token.owner.createdBy}`).then(wrapper => {
                        const div = divText(token.owner.createdBy);
                        div.style.marginTop = div.style.marginBottom = "auto";
                        div.classList.add(TruncateClass);
                        div.style.maxWidth = "150px";
                        div.style.marginLeft = "12px";
                        wrapper.append(div);
                        wrapper.style.display = "flex";
                    });

                    row.stat2.append(formatTs(token.specification.expiresAt));

                    const serverUrl = token.status.server?.trim() || "Not available";
                    const serverElement = divText(serverUrl);
                    serverElement.classList.add(TruncateClass);
                    serverElement.title = serverUrl;
                    row.stat3.append(serverElement);
                    row.stat3.style.marginTop = row.stat3.style.marginBottom = "auto"
                });

                browser.on("endRenderPage", () => {
                    SimpleAvatarComponentCache.fetchMissingAvatars();
                });

                browser.on("generateBreadcrumbs", () => [{title: browser.resourceName, absolutePath: ""}]);

                browser.on("renderEmptyPage", reason => {
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append("We are fetching your API tokens...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            if (Object.values(browser.browseFilters).length !== 0)
                                e.reason.append("No API token found with active filters.")
                            else e.reason.append("You have no API tokens.");
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                            e.reason.append("We could not find any data related to your API tokens.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append("We are currently unable to show your API tokens. Try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }
                });

                browser.setEmptyIcon("heroCircleStack");

                browser.on("fetchOperationsCallback", () => ({
                    dispatch,
                    navigate,
                    isCreating: false,
                    api: {isCoreResource: true},
                    invokeCommand: callAPI,
                    reload: () => browser.refresh()
                }));

                browser.on("pathToEntry", apiTok => apiTok.id);

                browser.on("fetchOperations", () => {
                    const entries = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn());
                    return retrieveOperations().filter(it => it.enabled(entries, callbacks as any, entries));
                });
            });
        }
        addProjectSwitcherInPortal(browserRef, setSwitcherWorkaround);
    }, []);

    React.useEffect(() => {
        browserRef.current?.renderRows();
    }, [options.data]);

    useSetRefreshFunction(() => {
        browserRef.current?.refresh();
    });

    return <MainContainer
        main={<>
            <div ref={mountRef} />
            {switcher}
        </>}
    />
}

function tokenContextFromOptions(
    token: Api.ApiToken,
    options: Api.ApiTokenRetrieveOptionsResponse,
): Api.ApiTokenContext | undefined {
    const provider = token.specification.provider;
    const service = token.specification.requestedPermissions[0]?.name;
    if (provider == null || service == null) return undefined;

    return options.byProvider[provider]?.availablePermissions.find(it => it.name === service)?.context;
}

function retrieveOperations(): Operation<Api.ApiToken, StandardCallbacks<Api.ApiToken>>[] {
    return [{
        icon: "heroCircleStack",
        text: "Create API token",
        primary: true,
        enabled: (selected) => selected.length === 0,
        onClick: (selected, cb) => {
            cb.navigate(AppRoutes.resources.apiTokensCreate());
        },
        shortcut: ShortcutKey.N,
    },
    {
        icon: "copy",
        text: "Copy server URL",
        enabled: (selected) => selected.length === 1 && !!selected[0].status.server?.trim(),
        onClick: ([selected]) => {
            copyToClipboard(selected.status.server);
            sendInformationNotification("Server URL copied to clipboard");
        },
        shortcut: ShortcutKey.C,
    },
    {
        icon: "trash",
        text: "Revoke",
        color: "errorMain",
        enabled: (selected) => selected.length > 0,
        confirm: true,
        confirmationText: selected => selected.length === 1 ?
            "Are you sure you want to revoke this API token?" :
            `Are you sure you want to revoke these ${selected.length} API tokens?`,
        confirmationButtonText: "Revoke",
        onClick: async (selected, cb) => {
            const promises: Promise<unknown>[] = [];
            for (const element of selected) {
                promises.push(cb.invokeCommand(
                    Api.revoke({id: element.id})
                ));
            }

            await Promise.all(promises);

            cb.reload();
        },
        shortcut: ShortcutKey.R
    }];
}
