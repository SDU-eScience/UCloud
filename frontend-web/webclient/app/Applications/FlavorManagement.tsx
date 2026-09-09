import * as React from "react";
import {Application, ApplicationVariant, deleteApplicationVariant, updateApplicationVariant} from "@/Applications/AppStoreApi";
import {callAPI} from "@/Authentication/DataHook";
import {dialogStore} from "@/Dialog/DialogStore";
import {slimModalStyle} from "@/Utilities/ModalUtilities";
import {ActionItem, CommonActionShortcut, ResourceBrowserActions} from "@/ui-components/Actions";
import {ColumnTitleList, ResourceBrowseFeatures, ResourceBrowser} from "@/ui-components/ResourceBrowser";
import {extractErrorMessage} from "@/UtilityFunctions";
import {SimpleAvatarComponentCache} from "@/Files/Shares";
import {avatarState} from "@/AvataaarLib/hook";
import {TruncateClass} from "@/ui-components/Truncate";
import Warning from "@/ui-components/Warning";

export type FlavorRefresh = () => void | Application[] | Promise<void | Application[]>;

interface Props {
    flavors: Application[];
    onUpdated: FlavorRefresh;
    onDeleted?(variant: ApplicationVariant): void;
}

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    sorting: false,
    filters: false,
    breadcrumbTitles: true,
    showColumnTitles: true,
    dragToSelect: true,
};

const COLUMNS: ColumnTitleList = [
    {name: "Title"},
    {name: "Base version", columnWidth: 180},
    {name: "Created by", columnWidth: 220},
    {name: "Published", columnWidth: 90},
];

const VERSION_COLUMNS: ColumnTitleList = [
    {name: "Version"},
    {name: "Base flavor", columnWidth: 260},
    {name: "Base version", columnWidth: 180},
    {name: "Created by", columnWidth: 180},
];

type VariantVersion = {
    entryType: "version";
    variantId: number;
    version: string;
    createdBy: string;
    baseName: string;
    baseVersion: string;
};

type BrowserEntry = ApplicationVariant | VariantVersion;

function isVariantVersion(entry: BrowserEntry): entry is VariantVersion {
    return "entryType" in entry && entry.entryType === "version";
}

const RENAME_SHORTCUT: CommonActionShortcut = {code: "F2", key: "F2"};
const ROOT_PATH = "/";

export function openFlavorManagement(flavors: Application[], onUpdated: Props["onUpdated"], onDeleted?: Props["onDeleted"]): void {
    dialogStore.addDialog(<FlavorManagement flavors={flavors} onUpdated={onUpdated} onDeleted={onDeleted} />, () => undefined, true, {
        ...slimModalStyle,
        content: {
            ...slimModalStyle.content,
            width: "min(900px, calc(100vw - 32px))",
            left: "50%",
            transform: "translateX(-50%)",
            overflow: "hidden",
        },
    });
}

function FlavorManagement({flavors, onUpdated, onDeleted}: Props): React.ReactNode {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<BrowserEntry> | null>(null);
    const loadingRef = React.useRef(false);
    const [error, setError] = React.useState<string | null>(null);

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            const versionsByVariantId = new Map<number, VariantVersion[]>();
            const variantsByPath = new Map<string, ApplicationVariant>();
            const versionsByPath = new Map<string, VariantVersion>();
            let currentFlavors = flavors;
            new ResourceBrowser<BrowserEntry>(mount, "Application flavors", {isModal: true}).init(browserRef, FEATURES, ROOT_PATH, browser => {
                browser.setColumns(COLUMNS);

                const setLoading = (loading: boolean) => {
                    loadingRef.current = loading;
                    browser.renderOperations();
                };

                async function update(variant: ApplicationVariant, changes: {title?: string; publishedToProject?: boolean}) {
                    if (loadingRef.current) return;
                    setLoading(true);
                    setError(null);
                    try {
                        const updated = await callAPI<ApplicationVariant>(updateApplicationVariant({id: variant.id, ...changes}));
                        const page = browser.cachedData[browser.currentPath] ?? [];
                        const index = page.findIndex(item => !isVariantVersion(item) && item.id === updated.id);
                        if (index !== -1) page[index] = updated;
                        browser.renderRows();
                        await onUpdated();
                    } catch (cause: any) {
                        setError("Could not update the flavor. " + extractErrorMessage(cause));
                    } finally {
                        setLoading(false);
                    }
                }

                async function remove(variant: ApplicationVariant) {
                    if (loadingRef.current) return;
                    setLoading(true);
                    setError(null);
                    try {
                        await callAPI(deleteApplicationVariant({id: variant.id}));
                        browser.removeEntryFromCurrentPage(item => !isVariantVersion(item) && item.id === variant.id);
                        browser.renderRows();
                        await onUpdated();
                        onDeleted?.(variant);
                    } catch (cause: any) {
                        setError("Could not delete the flavor. " + extractErrorMessage(cause));
                    } finally {
                        setLoading(false);
                    }
                }

                async function removeVersion(version: VariantVersion) {
                    if (loadingRef.current) return;
                    setLoading(true);
                    setError(null);
                    try {
                        await callAPI(deleteApplicationVariant({id: version.variantId, version: version.version}));
                        browser.removeEntryFromCurrentPage(item => isVariantVersion(item) &&
                            item.variantId === version.variantId && item.version === version.version);
                        browser.renderRows();
                        const updated = await onUpdated();
                        if (updated) {
                            const path = browser.currentPath;
                            registerFlavors(updated);
                            if (path === ROOT_PATH) browser.renderRows();
                            else if ((browser.cachedData[ROOT_PATH] ?? []).some(item => !isVariantVersion(item) &&
                                item.id.toString() === path.split("/").filter(Boolean)[0])) browser.open(`/${path.split("/").filter(Boolean)[0]}`, true);
                            else browser.open(ROOT_PATH, true);
                        }
                    } catch (cause: any) {
                        setError("Could not delete the flavor version. " + extractErrorMessage(cause));
                    } finally {
                        setLoading(false);
                    }
                }

                const versionEntries = (app: Application, variant: ApplicationVariant): VariantVersion[] =>
                    (app.versions?.length ? app.versions : [app.metadata.version]).map(version => ({
                        entryType: "version",
                        variantId: variant.id,
                        version,
                        createdBy: variant.createdBy,
                        baseName: variant.baseApplication.name,
                        baseVersion: variant.baseApplication.version,
                    }));

                const registerFlavors = (flavors: Application[]) => {
                    currentFlavors = flavors;
                    versionsByVariantId.clear();
                    variantsByPath.clear();
                    versionsByPath.clear();
                    const items = flavors.flatMap(app => {
                        if (!app.metadata.variant) return [];
                        const variant = app.metadata.variant;
                        variantsByPath.set(`/${variant.id}`, variant);
                        versionsByVariantId.set(variant.id, versionEntries(app, variant));
                        return [variant];
                    });
                    browser.setColumns(COLUMNS);
                    browser.registerPage({items, itemsPerPage: items.length}, ROOT_PATH, true);
                };

                const renderVariantVersions = (path: string, variant: ApplicationVariant) => {
                    const items = versionsByVariantId.get(variant.id) ?? [];
                    variantsByPath.set(path, variant);
                    for (const item of items) {
                        versionsByPath.set(`${path}/${encodeURIComponent(item.version)}`, item);
                    }
                    browser.setColumns(VERSION_COLUMNS);
                    browser.registerPage({items, itemsPerPage: items.length}, path, true);
                    browser.renderRows();
                };

                const renderVersion = (path: string, version: VariantVersion) => {
                    versionsByPath.set(path, version);
                    browser.setColumns(VERSION_COLUMNS);
                    browser.registerPage({items: [version], itemsPerPage: 1}, path, true);
                    browser.renderRows();
                };

                browser.on("refresh", () => {
                    void Promise.resolve()
                        .then(onUpdated)
                        .then(updatedFlavors => {
                            if (updatedFlavors) registerFlavors(updatedFlavors);
                        })
                        .catch(cause => setError("Could not refresh the flavors. " + extractErrorMessage(cause)));
                });

                browser.on("skipOpen", (_oldPath, _newPath, resource) => resource != null && isVariantVersion(resource));

                browser.on("open", (_oldPath, newPath, resource) => {
                    if (resource && isVariantVersion(resource)) return;
                    if (resource) {
                        renderVariantVersions(newPath, resource);
                        return;
                    }

                    if (newPath === ROOT_PATH) {
                        registerFlavors(currentFlavors);
                        browser.renderRows();
                        return;
                    }

                    const components = newPath.split("/").filter(Boolean);
                    const variantPath = `/${components[0]}`;
                    const variant = variantsByPath.get(variantPath) ??
                        (browser.cachedData[ROOT_PATH] ?? []).find(item => !isVariantVersion(item) && item.id.toString() === components[0]) as ApplicationVariant | undefined;
                    if (!variant) return;
                    if (components.length === 1) {
                        renderVariantVersions(variantPath, variant);
                    } else {
                        const version = versionsByPath.get(newPath);
                        if (version) renderVersion(newPath, version);
                    }
                });

                browser.on("renderRow", (entry, row) => {
                    if (isVariantVersion(entry)) {
                        row.title.append(ResourceBrowser.defaultTitleRenderer(entry.version, row));
                        row.stat1.append(ResourceBrowser.defaultTitleRenderer(entry.baseName, row));
                        row.stat2.innerText = entry.baseVersion;
                        row.stat1.style.justifyContent = "";
                        row.stat2.style.justifyContent = "";
                        row.stat3.style.justifyContent = "";
                        row.stat3.innerText = entry.createdBy;
                        return;
                    }
                    const variant = entry;
                    row.title.append(ResourceBrowser.defaultTitleRenderer(variant.title, row));
                    row.stat1.append(ResourceBrowser.defaultTitleRenderer(variant.baseApplication.version, row));
                    row.stat2.style.justifyContent = "flex-start";
                    SimpleAvatarComponentCache.appendTo(row.stat2, variant.createdBy, `Created by ${variant.createdBy}`).then(wrapper => {
                        wrapper.style.display = "flex";
                        wrapper.style.alignItems = "center";
                        wrapper.style.gap = "8px";
                        wrapper.style.width = "100%";
                        wrapper.style.minWidth = "0";

                        const name = document.createElement("div");
                        name.className = TruncateClass;
                        name.style.minWidth = "0";
                        name.style.maxWidth = "calc(100% - 48px)";
                        name.innerText = variant.createdBy;
                        name.title = variant.createdBy;
                        wrapper.append(name);
                    });
                    row.stat3.innerText = variant.publishedToProject ? "Yes" : "No";
                });

                browser.on("endRenderPage", () => {
                    SimpleAvatarComponentCache.fetchMissingAvatars();
                });

                const avatarListener = () => browser.rerender();
                avatarState.subscribe(avatarListener);
                browser.on("unmount", () => avatarState.unsubscribe(avatarListener));

                browser.on("fetchOperationsCallback", () => ({}));
                browser.on("fetchOperations", () => retrieveOperations(browser, update, remove, removeVersion, loadingRef));
                browser.on("fetchFilters", () => []);
                browser.on("generateBreadcrumbs", path => {
                    const result = [{title: "Application flavors", absolutePath: ROOT_PATH}];
                    let absolutePath = "";
                    for (const component of path.split("/").filter(Boolean)) {
                        absolutePath += `/${component}`;
                        const variant = variantsByPath.get(absolutePath);
                        const version = versionsByPath.get(absolutePath);
                        result.push({
                            title: variant?.title ?? version?.version ?? component,
                            absolutePath,
                        });
                    }
                    return result;
                });
                browser.on("pathToEntry", entry => {
                    if (isVariantVersion(entry)) {
                        const parent = browser.currentPath;
                        return `${parent}/${encodeURIComponent(entry.version)}`;
                    }
                    return `/${entry.id}`;
                });
                browser.on("nameOfEntry", entry => isVariantVersion(entry) ? entry.version : entry.title);
                browser.on("unhandledShortcut", () => {});
            });
        }
    }, []);

    return <div>
        {error ? <Warning warning={error} clearWarning={() => setError(null)} mb="12px" /> : null}
        <div ref={mountRef} />
    </div>;
}

function retrieveOperations(
    browser: ResourceBrowser<BrowserEntry>,
    update: (variant: ApplicationVariant, changes: {title?: string; publishedToProject?: boolean}) => Promise<void>,
    remove: (variant: ApplicationVariant) => Promise<void>,
    removeVersion: (version: VariantVersion) => Promise<void>,
    loadingRef: React.MutableRefObject<boolean>,
): ResourceBrowserActions<BrowserEntry, {}> {
    const publish: ActionItem<BrowserEntry, {}> = {
        text: selected => {
            const variant = selected[0];
            return !variant || isVariantVersion(variant) || variant.publishedToProject ? "Unpublish from project" : "Publish to project";
        },
        icon: "heroGlobeAlt",
        enabled: selected => selected.length === 1 && !isVariantVersion(selected[0]) && !loadingRef.current,
        onClick: ([variant]) => {
            if (!variant || isVariantVersion(variant)) return;
            void update(variant, {publishedToProject: !variant.publishedToProject});
        },
    };
    const rename: ActionItem<BrowserEntry, {}> = {
        text: "Rename",
        icon: "heroPencilSquare",
        shortcut: RENAME_SHORTCUT,
        enabled: selected => selected.length === 1 && !isVariantVersion(selected[0]) && !loadingRef.current,
        onClick: ([variant]) => {
            if (!variant || isVariantVersion(variant)) return;
            browser.showRenameField(
                item => !isVariantVersion(item) && item.id === variant.id,
                () => {
                    const title = browser.renameValue.trim();
                    if (title && title !== variant.title) void update(variant, {title});
                },
                () => {},
                variant.title,
            );
        },
    };
    const deleteAction: ActionItem<BrowserEntry, {}> = {
        text: "Delete",
        icon: "heroTrash",
        destructive: true,
        confirmationText: selected => {
            const variant = selected[0];
            return `Delete ${variant && !isVariantVersion(variant) ? variant.title : ""}?`;
        },
        confirmationButtonText: "Delete",
        enabled: selected => selected.length === 1 && !isVariantVersion(selected[0]) && !loadingRef.current,
        onClick: ([variant]) => {
            if (!variant || isVariantVersion(variant)) return;
            void remove(variant);
        },
    };
    const deleteVersionAction: ActionItem<BrowserEntry, {}> = {
        text: "Delete version",
        icon: "heroTrash",
        destructive: true,
        confirmationText: selected => {
            const version = selected[0];
            return `Delete version ${version && isVariantVersion(version) ? version.version : ""}?`;
        },
        confirmationButtonText: "Delete",
        enabled: selected => selected.length === 1 && isVariantVersion(selected[0]) && !loadingRef.current,
        onClick: ([version]) => {
            if (!version || !isVariantVersion(version)) return;
            void removeVersion(version);
        },
    };

    return {
        topbar: [publish, rename, deleteAction, deleteVersionAction],
        contextMenu: [publish, rename, "divider", deleteAction, deleteVersionAction],
        appearance: action => action === deleteAction || action === deleteVersionAction ? {color: "errorMain"} : undefined,
    };
}
