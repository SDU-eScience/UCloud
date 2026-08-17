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
    breadcrumbsSeparatedBySlashes: false,
    showColumnTitles: true,
    dragToSelect: true,
};

const COLUMNS: ColumnTitleList = [
    {name: "Title"},
    {name: "Base version", columnWidth: 180},
    {name: "Created by", columnWidth: 220},
    {name: "Published", columnWidth: 90},
];

const RENAME_SHORTCUT: CommonActionShortcut = {code: "F2", key: "F2"};

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
    const browserRef = React.useRef<ResourceBrowser<ApplicationVariant> | null>(null);
    const loadingRef = React.useRef(false);
    const [error, setError] = React.useState<string | null>(null);

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<ApplicationVariant>(mount, "Application flavors", {isModal: true}).init(browserRef, FEATURES, "", browser => {
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
                        const index = page.findIndex(item => item.id === updated.id);
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
                        browser.removeEntryFromCurrentPage(item => item.id === variant.id);
                        browser.renderRows();
                        await onUpdated();
                        onDeleted?.(variant);
                    } catch (cause: any) {
                        setError("Could not delete the flavor. " + extractErrorMessage(cause));
                    } finally {
                        setLoading(false);
                    }
                }

                browser.on("skipOpen", (_oldPath, _newPath, resource) => resource != null);
                const registerFlavors = (flavors: Application[]) => {
                    const items = flavors.flatMap(app => app.metadata.variant ? [app.metadata.variant] : []);
                    browser.registerPage({items, itemsPerPage: items.length}, browser.currentPath, true);
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

                browser.on("open", (_oldPath, newPath, resource) => {
                    if (resource) return;

                    const items = flavors.flatMap(app => app.metadata.variant ? [app.metadata.variant] : []);
                    browser.registerPage({items, itemsPerPage: items.length}, newPath, true);
                    browser.renderRows();
                });

                browser.on("renderRow", (variant, row) => {
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
                browser.on("fetchOperations", () => retrieveOperations(browser, update, remove, loadingRef));
                browser.on("fetchFilters", () => []);
                browser.on("generateBreadcrumbs", () => [{title: "Application flavors", absolutePath: ""}]);
                browser.on("pathToEntry", variant => variant.id.toString());
                browser.on("nameOfEntry", variant => variant.title);
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
    browser: ResourceBrowser<ApplicationVariant>,
    update: (variant: ApplicationVariant, changes: {title?: string; publishedToProject?: boolean}) => Promise<void>,
    remove: (variant: ApplicationVariant) => Promise<void>,
    loadingRef: React.MutableRefObject<boolean>,
): ResourceBrowserActions<ApplicationVariant, {}> {
    const publish: ActionItem<ApplicationVariant, {}> = {
        text: selected => selected[0]?.publishedToProject ? "Unpublish from project" : "Publish to project",
        icon: "heroGlobeAlt",
        enabled: selected => selected.length === 1 && !loadingRef.current,
        onClick: ([variant]) => void update(variant, {publishedToProject: !variant.publishedToProject}),
    };
    const rename: ActionItem<ApplicationVariant, {}> = {
        text: "Rename",
        icon: "heroPencilSquare",
        shortcut: RENAME_SHORTCUT,
        enabled: selected => selected.length === 1 && !loadingRef.current,
        onClick: ([variant]) => {
            browser.showRenameField(
                item => item.id === variant.id,
                () => {
                    const title = browser.renameValue.trim();
                    if (title && title !== variant.title) void update(variant, {title});
                },
                () => {},
                variant.title,
            );
        },
    };
    const deleteAction: ActionItem<ApplicationVariant, {}> = {
        text: "Delete",
        icon: "heroTrash",
        destructive: true,
        confirmationText: selected => `Delete ${selected[0]?.title}?`,
        confirmationButtonText: "Delete",
        enabled: selected => selected.length === 1 && !loadingRef.current,
        onClick: ([variant]) => void remove(variant),
    };

    return {
        topbar: [publish, rename, deleteAction],
        contextMenu: [publish, rename, "divider", deleteAction],
        appearance: action => action === deleteAction ? {color: "errorMain"} : undefined,
    };
}
