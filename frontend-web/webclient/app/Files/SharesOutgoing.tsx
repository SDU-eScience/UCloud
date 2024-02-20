import * as React from "react";
import {useTitle} from "@/Navigation/Redux";
import SharesApi, {OutgoingShareGroup, OutgoingShareGroupPreview, Share, ShareState, isViewingShareGroupPreview} from "@/UCloud/SharesApi";
import MainContainer from "@/ui-components/MainContainer";
import {prettyFilePath} from "@/Files/FilePath";
import {RadioTile, RadioTilesContainer} from "@/ui-components";
import {capitalized, createHTMLElements, displayErrorMessageOrDefault, extractErrorMessage, stopPropagation} from "@/UtilityFunctions";
import {callAPI, noopCall} from "@/Authentication/DataHook";
import {ResourceBrowseCallbacks} from "@/UCloud/ResourceApi";
import {useLocation, useNavigate} from "react-router";
import {useDispatch} from "react-redux";
import {buildQueryString, getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {avatarState} from "@/AvataaarLib/hook";
import {api as FilesApi} from "@/UCloud/FilesApi";
import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts, SelectionMode, clearFilterStorageValue, dateRangeFilters} from "@/ui-components/ResourceBrowser";
import {ReactStaticRenderer} from "@/Utilities/ReactStaticRenderer";
import {addShareModal, StateIconAndColor} from "./Shares";
import AppRoutes from "@/Routes";
import {Operation, ShortcutKey} from "@/ui-components/Operation";
import {ButtonClass} from "@/ui-components/Button";
import {arrayToPage} from "@/Types";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {fileName} from "@/Utilities/FileUtilities";
import {bulkRequestOf} from "@/UtilityFunctions";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import Avatar from "@/AvataaarLib/avatar";
import {useProjectId} from "@/Project/Api";
import {FlexClass} from "@/ui-components/Flex";

enum ShareValidateState {
    NOT_VALIDATED,
    VALIDATED,
    DELETED
}

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    filters: true,
    sorting: true,
    breadcrumbsSeparatedBySlashes: false,
    showColumnTitles: true,
    dragToSelect: true,
};

const defaultRetrieveFlags: {itemsPerPage: number} = {
    itemsPerPage: 250,
};

let avatarCache: Record<string, ReactStaticRenderer> = {}
let RIGHTS_TOGGLE_ICON_CACHE: {
    ENABLED_READ: null | ReactStaticRenderer,
    ENABLED_EDIT: null | ReactStaticRenderer,
    REJECTED: null | ReactStaticRenderer,
} = {
    ENABLED_READ: null,
    ENABLED_EDIT: null,
    REJECTED: null,
}

new ReactStaticRenderer(() =>
    <RadioTilesContainer height={48} onClick={stopPropagation}>
        <RadioTile
            label={"Read"}
            onChange={noopCall}
            icon={"search"}
            name={"READ"}
            checked
            height={40}
            fontSize={"0.5em"}
        />
        <RadioTile
            label={"Edit"}
            onChange={noopCall}
            icon={"edit"}
            name={"EDIT"}
            checked={false}
            height={40}
            fontSize={"0.5em"}
        />
    </RadioTilesContainer>
).promise.then(it => RIGHTS_TOGGLE_ICON_CACHE.ENABLED_READ = it);

new ReactStaticRenderer(() =>
    <RadioTilesContainer height={48} onClick={stopPropagation}>
        <RadioTile
            label={"Read"}
            onChange={noopCall}
            icon={"search"}
            name={"READ"}
            checked={false}
            height={40}
            fontSize={"0.5em"}
        />
        <RadioTile
            label={"Edit"}
            onChange={noopCall}
            icon={"edit"}
            name={"EDIT"}
            checked
            height={40}
            fontSize={"0.5em"}
        />
    </RadioTilesContainer>
).promise.then(it => RIGHTS_TOGGLE_ICON_CACHE.ENABLED_EDIT = it);

new ReactStaticRenderer(() => {
    const foo = Math.random();
    return <RadioTilesContainer height={48} onClick={stopPropagation}>
        <RadioTile
            disabled
            label={"Read"}
            onChange={noopCall}
            icon={"search"}
            name={foo.toString()}
            checked={false}
            height={40}
            fontSize={"0.5em"}
        />
        <RadioTile
            disabled
            label={"Edit"}
            onChange={noopCall}
            icon={"edit"}
            name={foo.toString()}
            checked={false}
            height={40}
            fontSize={"0.5em"}
        />
    </RadioTilesContainer>
}).promise.then(it => RIGHTS_TOGGLE_ICON_CACHE.REJECTED = it);

const shareValidationCache: Record<string, ShareValidateState> = {};

const TITLE = "Shared by me";

export function OutgoingSharesBrowse({opts}: {opts?: ResourceBrowserOpts<OutgoingShareGroup | OutgoingShareGroupPreview>}): JSX.Element {
    // Projects should now show this page
    const activeProjectId = useProjectId();
    React.useEffect(() => {
        if (activeProjectId) {
            navigate(AppRoutes.dashboard.dashboardA());
        }
    }, [activeProjectId])

    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<OutgoingShareGroup | OutgoingShareGroupPreview> | null>(null);
    const navigate = useNavigate();
    const dispatch = useDispatch();
    const location = useLocation();

    useTitle(TITLE);

    const features: ResourceBrowseFeatures = FEATURES;

    const dateRanges = dateRangeFilters("Created after");
    var isInitial = true;

    avatarState.subscribe(() => {
        avatarCache = {};
        browserRef.current?.renderRows();
    });

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<OutgoingShareGroup | OutgoingShareGroupPreview>(mount, TITLE, opts).init(browserRef, features, "", browser => {
                // Removed stored filters that shouldn't persist.
                dateRanges.keys.forEach(it => clearFilterStorageValue(browser.resourceName, it));
                let shouldRemoveFakeDirectory = true;
                const dummyId = "temporary-share-id-that-will-be-unique";
                function showShareInput() {
                    browser.removeEntryFromCurrentPage(it => (it as any).id === dummyId);
                    shouldRemoveFakeDirectory = false;
                    insertFakeEntry(dummyId);
                    const idx = browser.findVirtualRowIndex((it: OutgoingShareGroupPreview) => it.shareId === dummyId);
                    if (idx !== null) browser.ensureRowIsVisible(idx, true);

                    browser.showRenameField(
                        (it: OutgoingShareGroupPreview) => it.shareId === dummyId,
                        () => {
                            const idx = browser.findVirtualRowIndex((it: OutgoingShareGroupPreview) => it.shareId === dummyId);
                            if (idx !== null) {
                                browser.ensureRowIsVisible(idx, true, true);
                                browser.select(idx, SelectionMode.SINGLE);
                            }

                            const share = browser.findSelectedEntries().at(0) as OutgoingShareGroupPreview;
                            if (!share) return;

                            browser.removeEntryFromCurrentPage((it: OutgoingShareGroupPreview) => it.shareId === dummyId);

                            const sharedWith = browser.renameValue;
                            if (!sharedWith) return;
                            const filePath = new URLSearchParams(location.search).get("path") as string;
                            if (!filePath) return;
                            const page = browser.cachedData["/"] as OutgoingShareGroup[];
                            const shareGroup = page.find(it => it.sourceFilePath === filePath);
                            if (!shareGroup) return;

                            callAPI(SharesApi.create(bulkRequestOf({sharedWith, sourceFilePath: filePath, permissions: share.permissions, product: shareGroup.storageProduct, conflictPolicy: "RENAME"})))
                                .then(it => {
                                    const id = it.responses[0]!.id;
                                    const page = browser.cachedData[filePath];
                                    page.push({permissions: share.permissions, shareId: id, sharedWith, state: "PENDING"})
                                    browser.rerender();
                                }).catch(err => {
                                    snackbarStore.addFailure(extractErrorMessage(err), false);
                                });
                        },
                        () => {
                            if (shouldRemoveFakeDirectory) browser.removeEntryFromCurrentPage((it: OutgoingShareGroupPreview) => it.shareId === dummyId);
                        },
                        ""
                    );

                    shouldRemoveFakeDirectory = true;
                }

                function insertFakeEntry(dummyId: string): void {
                    browser.insertEntryIntoCurrentPage({permissions: ["READ"], shareId: dummyId, sharedWith: "", state: "PENDING"} as OutgoingShareGroupPreview);
                }

                function isDeleted(share: OutgoingShareGroup | OutgoingShareGroupPreview) {
                    return !isViewingShareGroupPreview(share) && shareValidationCache[share.sourceFilePath] === ShareValidateState.DELETED;
                }

                browser.on("open", (oldPath, newPath, resource) => {
                    if (newPath !== "/") {
                        browser.setColumnTitles([{name: "Shared with"}, {name: "Share rights"}, {name: "State"}, {name: ""}]);
                    } else {
                        browser.setColumnTitles([{name: "Filename"}, {name: ""}, {name: "Permissions"}, {name: "Shared with"}])
                    }
                    if (resource && isViewingShareGroupPreview(resource)) {
                        // navigate to share
                        const p = getQueryParamOrElse(window.location.search, "path", "");
                        if (p) {
                            navigate(buildQueryString(`/files`, {path: p}));
                        }
                        return;
                    }

                    if (resource) { // NOT share group preview
                        navigate(`/shares/outgoing?path=${resource.sourceFilePath}`);
                    }

                    if (oldPath !== newPath) {
                        if (newPath === "/shares/outgoing") {
                            navigate(`/shares/outgoing`);
                        }
                        browser.rerender();
                    }

                    callAPI(
                        SharesApi.browseOutgoing({
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...opts?.additionalFilters
                        })
                    ).then(result => {
                        browser.registerPage(result, "/", true);
                        const page = result as Page<OutgoingShareGroup>;

                        const rerenderCheck = {doRerender: false};
                        const promises: Promise<unknown>[] = [];
                        for (const it of page.items) {
                            browser.registerPage({items: it.sharePreview, itemsPerPage: it.sharePreview.length}, it.sourceFilePath, true)
                            if (shareValidationCache[it.sourceFilePath] == null) {
                                shareValidationCache[it.sourceFilePath] === ShareValidateState.NOT_VALIDATED;
                                promises.push(callAPI(FilesApi.retrieve({id: it.sourceFilePath})).then(() => {
                                    shareValidationCache[it.sourceFilePath] = ShareValidateState.VALIDATED
                                }).catch(error => {
                                    if ([400, 404].includes(error.request.status)) {
                                        shareValidationCache[it.sourceFilePath] = ShareValidateState.DELETED;
                                        rerenderCheck.doRerender = true;
                                    }
                                }));
                            }
                        }

                        const searchPath = new URLSearchParams(location.search).get("path") as string;
                        if (isInitial && searchPath) {
                            browser.open(searchPath, true);
                        }

                        isInitial = false;
                        browser.renderRows();

                        Promise.allSettled(promises).finally(() => {
                            if (rerenderCheck.doRerender) {
                                browser.renderRows();
                            }
                        });
                    });
                });

                browser.on("wantToFetchNextPage", async (path) => {
                    const result = await callAPI(
                        SharesApi.browseOutgoing({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...opts?.additionalFilters
                        })
                    );

                    browser.registerPage(result, "/", false);
                    const page = result as Page<OutgoingShareGroup>;

                    const rerenderCheck = {doRerender: false};
                    const promises: Promise<unknown>[] = [];
                    for (const it of page.items) {
                        browser.registerPage({items: it.sharePreview, itemsPerPage: it.sharePreview.length}, it.sourceFilePath, false);
                        if (shareValidationCache[it.sourceFilePath] == null) {
                            shareValidationCache[it.sourceFilePath] === ShareValidateState.NOT_VALIDATED;
                            promises.push(callAPI(FilesApi.retrieve({id: it.sourceFilePath})).then(() => {
                                shareValidationCache[it.sourceFilePath] = ShareValidateState.VALIDATED
                            }).catch(error => {
                                if ([400, 404].includes(error.request.status)) {
                                    shareValidationCache[it.sourceFilePath] = ShareValidateState.DELETED;
                                    rerenderCheck.doRerender = true;
                                }
                            }));
                        }
                    }

                    browser.renderRows();

                    Promise.allSettled(promises).finally(() => {
                        if (rerenderCheck.doRerender) {
                            browser.renderRows();
                        }
                    });
                });

                browser.on("fetchFilters", () => [{
                    key: "filterCreatedBy",
                    type: "input",
                    icon: "user",
                    text: "Created by"
                }, dateRangeFilters("Date created")]);

                const currentAvatars = new Set<string>();
                const fetchedAvatars = new Set<string>();
                browser.on("endRenderPage", () => {
                    if (currentAvatars.size > 0) {
                        avatarState.updateCache([...currentAvatars]);
                        currentAvatars.forEach(it => fetchedAvatars.add(it));
                        currentAvatars.clear();
                    }
                });

                avatarState.subscribe(() => browser.rerender());

                browser.on("renderRow", (share, row, dims) => {
                    const avatarWrapper = document.createElement("div");

                    if (isViewingShareGroupPreview(share)) {
                        row.title.append(avatarWrapper);
                        const wrapper = createHTMLElements({
                            tagType: "div",
                            style: {marginRight: "8px"}
                        });
                        avatarWrapper.appendChild(wrapper);

                        if (avatarCache[share.sharedWith]) {
                            wrapper.appendChild(avatarCache[share.sharedWith].clone());
                        } else {
                            const sharedWithAvatar = avatarState.avatar(share.sharedWith);
                            new ReactStaticRenderer(() =>
                                <Avatar style={{height: "40px", width: "40px"}} avatarStyle="Circle" {...sharedWithAvatar} />
                            ).promise.then(it => {
                                avatarCache[share.sharedWith] = it;
                                wrapper.appendChild(it.clone());
                            });
                        }
                    } else {
                        const [icon, setIcon] = ResourceBrowser.defaultIconRenderer(opts?.embedded === true);

                        row.title.append(icon);
                        // TODO(Jonas): For some reason, the arrow is not rendered.
                        browser.icons.renderIcon({
                            name: "ftSharesFolder",
                            color: "iconColor",
                            color2: "iconColor2",
                            height: 32,
                            width: 32
                        }).then(setIcon);
                    }

                    const isShareDeleted = isDeleted(share);

                    // Row title
                    if (isViewingShareGroupPreview(share)) {
                        row.title.append(ResourceBrowser.defaultTitleRenderer(share.sharedWith, dims, row));
                    } else {
                        const node = ResourceBrowser.defaultTitleRenderer(share.sourceFilePath, dims, row);
                        row.title.append(node);
                        prettyFilePath(share.sourceFilePath).then(title => {
                            node.innerText = title;
                            node.title = title;
                        });
                    }

                    // Row stat1
                    if (isShareDeleted) {
                        const deletedTextNode = document.createElement("span");
                        deletedTextNode.innerText = "File deleted";
                        deletedTextNode.style.marginLeft = "8px";
                        row.stat1.append(deletedTextNode);
                    } else if (isViewingShareGroupPreview(share)) {
                        const isEdit = share.permissions.some(it => it === "EDIT");
                        const radioTilesContainerWrapper = document.createElement("div");
                        row.stat1.append(radioTilesContainerWrapper);
                        const isRejected = share.state === "REJECTED";
                        if (isRejected) {
                            const rejectedIcon = RIGHTS_TOGGLE_ICON_CACHE.REJECTED;
                            if (rejectedIcon) radioTilesContainerWrapper.append(rejectedIcon.clone());
                        } else {
                            let icons = isEdit ? RIGHTS_TOGGLE_ICON_CACHE.ENABLED_EDIT : RIGHTS_TOGGLE_ICON_CACHE.ENABLED_READ;
                            if (icons) {
                                radioTilesContainerWrapper.append(icons.clone());
                                const tiles = radioTilesContainerWrapper.querySelectorAll(":scope input[type='radio']");
                                const readTile = tiles.item(0) as HTMLInputElement;
                                const editTile = tiles.item(1) as HTMLInputElement;
                                if (readTile && editTile) {
                                    readTile.id = editTile.id = readTile.name = editTile.name = share.shareId;
                                    readTile["onclick"] = editTile["onclick"] = e => e.stopPropagation();
                                    readTile["onchange"] = () => {
                                        if (share.permissions.includes("EDIT")) {
                                            updatePermissions(share, false);
                                            share.permissions = ["READ"];
                                            browser.renderRows();
                                        }
                                    }
                                    editTile["onchange"] = () => {
                                        if (!share.permissions.includes("EDIT")) {
                                            updatePermissions(share, true);
                                            share.permissions = ["READ", "EDIT"];
                                            browser.renderRows();
                                        }
                                    }
                                } else {
                                    console.log("NOT defined")
                                }
                            }
                        }
                    }

                    // Row stat2
                    if (!isViewingShareGroupPreview(share) && isShareDeleted) {
                        const button = document.createElement("button");
                        button.innerText = "Remove share";
                        button.className = ButtonClass;
                        button.style.color = "var(--errorContrast)";
                        button.style.backgroundColor = "var(--errorMain)";
                        button.style.height = "32px";
                        button.style.width = "128px";
                        button.onclick = e => {
                            e.stopImmediatePropagation();
                            const oldPage = browser.cachedData["/"];
                            browser.registerPage(
                                arrayToPage(oldPage.filter((it: OutgoingShareGroup) => it.sourceFilePath !== share.sourceFilePath)),
                                "/",
                                true
                            );
                            browser.renderRows();
                            try {
                                callAPI(SharesApi.remove(bulkRequestOf(...share.sharePreview.map(sg => ({id: sg.shareId})))));
                            } catch (e) {
                                displayErrorMessageOrDefault(e, "Failed to remove invalid share");
                                browser.registerPage(arrayToPage(oldPage), "/", true);
                                browser.renderRows();
                            }
                        }
                        row.stat2.replaceChildren(button);
                    } else {
                        const [stateIcon, setStateIcon] = ResourceBrowser.defaultIconRenderer(opts?.embedded === true);
                        stateIcon.style.marginTop = stateIcon.style.marginBottom = "auto";
                        row.stat2.appendChild(stateIcon);
                        const text = createHTMLElements({tagType: "div", style: {marginTop: "auto", marginBottom: "auto"}});
                        let state: ShareState;

                        if (isViewingShareGroupPreview(share)) {
                            state = share.state;
                            text.innerText = capitalized(share.state.toString());
                        } else {
                            const pending = share.sharePreview.filter(it => it.state === "PENDING");
                            const rejected = share.sharePreview.filter(it => it.state === "REJECTED");
                            const anyPending = pending.length > 0;
                            const anyRejected = pending.length > 0;

                            if (anyPending) {
                                text.innerText = `${pending.length} pending`;
                                state = "PENDING";
                            } else if (anyRejected) {
                                text.innerText = `${rejected.length} rejected`;
                                state = "REJECTED";
                            } else { // All must be approved if none are rejected or pending.
                                text.innerText = `All accepted`;
                                state = "APPROVED";
                            }
                        }

                        row.stat2.append(text);
                        browser.icons.renderIcon({
                            ...StateIconAndColor[state],
                            color2: "iconColor2",
                            height: 32,
                            width: 32,
                        }).then(setStateIcon);
                    }

                    // Row stat3
                    if (isViewingShareGroupPreview(share)) {
                        if (!fetchedAvatars.has(share.sharedWith)) {
                            currentAvatars.add(share.sharedWith);
                        }
                    } else {
                        for (const preview of share.sharePreview) {
                            if (!fetchedAvatars.has(preview.sharedWith)) {
                                currentAvatars.add(preview.sharedWith);
                            }
                        }
                    }

                    // Note(Jonas): To any future reader (as opposed to past reader?) the avatarWrapper is to ensure that
                    // the re-render doesn't happen multiple times, when re-rendering. The avatarWrapper can be dead,
                    // so attaching doesn't do anything, instead of risking the promise resolving after a second re-render,
                    // causing multiple avatars to be shown.
                    if (!isViewingShareGroupPreview(share)) {
                        const sharedWithAvatars = share.sharePreview.slice(0, 5).map(it => avatarState.avatar(it.sharedWith));
                        const flexWrapper = createHTMLElements({
                            tagType: "div",
                            className: FlexClass,
                            style: {marginRight: "26px"}
                        });
                        row.stat3.append(flexWrapper);
                        if (share.sharePreview.every(it => avatarCache[it.sharedWith] != null)) {
                            share.sharePreview.forEach(s => {
                                const wrapper = createHTMLElements({
                                    tagType: "div",
                                    style: {marginRight: "-26px"},
                                });
                                wrapper.appendChild(avatarCache[s.sharedWith].clone());
                                flexWrapper.appendChild(wrapper);
                            });
                        } else {
                            sharedWithAvatars.forEach((avatar, index) => {
                                const sharedWith = share.sharePreview[index].sharedWith;
                                new ReactStaticRenderer(() =>
                                    <Avatar key={sharedWith} style={{height: "40px", width: "40px"}} avatarStyle="Circle" {...avatar} />
                                ).promise.then(it => {
                                    avatarCache[sharedWith] = it;
                                    const wrapper = createHTMLElements({
                                        tagType: "div",
                                        style: {marginRight: "-26px"},
                                    });
                                    wrapper.appendChild(it.clone());
                                    flexWrapper.appendChild(wrapper);
                                });
                            })

                        }
                    }
                });

                browser.setEmptyIcon("heroShare");

                browser.on("unhandledShortcut", () => void 0);

                browser.on("renderEmptyPage", reason => {
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append("We are fetching your shares...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            if (Object.values({...browser.browseFilters, ...(opts?.additionalFilters ?? {})}).length !== 0)
                                e.reason.append("No shares found with active filters.")
                            else e.reason.append("This workspace has no shares.");
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                            e.reason.append("We could not find any data related to your shares.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append("We are currently unable to show your shares. Try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }
                });

                browser.on("nameOfEntry", s => {
                    if (isViewingShareGroupPreview(s)) {
                        return s.shareId;
                    } else {
                        return s.sourceFilePath;
                    }
                });
                browser.on("pathToEntry", s => {
                    if (isViewingShareGroupPreview(s)) {
                        return s.shareId;
                    } else {
                        return s.sourceFilePath;
                    }
                });

                browser.on("fetchOperationsCallback", () => {
                    const support = {productsByProvider: {}};
                    const callbacks: ResourceBrowseCallbacks<Share> = {
                        api: SharesApi,
                        navigate: to => navigate(to),
                        commandLoading: false,
                        invokeCommand: call => callAPI(call),
                        embedded: false,
                        isCreating: false,
                        dispatch: dispatch,
                        supportByProvider: support,
                        reload: () => browser.refresh(),
                        isWorkspaceAdmin: true, // This is shares, after all.
                        viewProperties: s => {
                            navigate(AppRoutes.resource.properties("shares", s.id));
                        }
                    };
                    return callbacks;
                });
                browser.on("fetchOperations", () => {
                    const entries = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as ResourceBrowseCallbacks<Share>;

                    const operations: Operation<OutgoingShareGroup | OutgoingShareGroupPreview, ResourceBrowseCallbacks<Share>>[] = [{
                        text: "Delete",
                        confirm: true,
                        icon: "trash",
                        enabled(selected) {
                            return (selected.length !== 0) && isViewingShareGroupPreview(selected[0]);
                        },
                        async onClick(selected, extra) {
                            if (isViewingShareGroupPreview(selected[0])) {
                                const previews = selected as OutgoingShareGroupPreview[];
                                await extra.invokeCommand(extra.api.remove(bulkRequestOf(...previews.map(it => ({id: it.shareId})))));
                                extra.reload();
                            }
                        },
                        shortcut: ShortcutKey.R
                    },
                    {
                        text: "Delete share",
                        confirm: true,
                        icon: "trash",
                        enabled(selected) {
                            return (selected.length !== 0) && !isViewingShareGroupPreview(selected[0]);
                        },
                        async onClick(selected, extra) {
                            if (!isViewingShareGroupPreview(selected[0])) {
                                const previews = selected as OutgoingShareGroup[];
                                const ids = previews.flatMap(s => s.sharePreview.map(sg => ({id: sg.shareId})));
                                await extra.invokeCommand(extra.api.remove(bulkRequestOf(...ids)));
                                extra.reload();
                            }
                        },
                        shortcut: ShortcutKey.R
                    }, {
                        icon: "share",
                        text: "Invite",
                        enabled(selected) {return selected.length === 1 && !isViewingShareGroupPreview(selected[0])},
                        onClick(selected, cb) {
                            const [share] = selected;
                            if (!isViewingShareGroupPreview(share)) {
                                addShareModal({path: share.sourceFilePath, product: share.storageProduct}, cb);
                            }
                        },
                        shortcut: ShortcutKey.I // Note(Jonas): Or S?
                    }, {
                        icon: "share",
                        text: "Invite",
                        enabled(selected) {
                            const hasPath = window.location.search !== "";
                            return selected.length === 0 && hasPath;
                        },
                        onClick() {
                            showShareInput();
                        },
                        shortcut: ShortcutKey.I
                    }, {
                        icon: "properties",
                        text: "Properties",
                        enabled(selected) {
                            return selected.length === 1 && isViewingShareGroupPreview(selected[0])
                        },
                        onClick([selection]: [OutgoingShareGroupPreview]) {
                            navigate(AppRoutes.resource.properties("shares", selection.shareId));
                        },
                        shortcut: ShortcutKey.P
                    }, {
                        icon: "properties",
                        text: "Manage share",
                        enabled(selected) {
                            return selected.length === 1 && !isViewingShareGroupPreview(selected[0])
                        },
                        onClick([selection]: [OutgoingShareGroup]) {
                            navigate(`/shares/outgoing?path=${selection.sourceFilePath}`);
                        },
                        shortcut: ShortcutKey.P
                    }];
                    return operations.filter(it => it.enabled(entries, callbacks, entries));
                });

                browser.on("generateBreadcrumbs", () => {
                    const breadcrumbs = [{title: "Shared by me", absolutePath: "/shares/outgoing"}];
                    const search = getQueryParamOrElse(window.location.search, "path", "");
                    if (search !== "") breadcrumbs.push({title: "/ " + fileName(search), absolutePath: ""});
                    return breadcrumbs;
                });

                async function updatePermissions(share: OutgoingShareGroupPreview, isEditing: boolean) {
                    if (share.shareId === dummyId) return;
                    try {
                        await callAPI(SharesApi.updatePermissions(bulkRequestOf({
                            id: share.shareId,
                            permissions: isEditing ? ["READ", "EDIT"] : ["READ"]
                        })));
                    } catch (e) {
                        displayErrorMessageOrDefault(e, "Failed to update permissions.");
                        share.permissions = isEditing ? ["READ"] : ["READ", "EDIT"];
                        browser.renderRows();
                    }
                }
            });

            browserRef.current!.renameField.style.marginLeft = "38px";
        }
    }, []);

    if (!opts?.embedded && !opts?.isModal) {
        useSetRefreshFunction(() => {
            browserRef.current?.refresh();
        });
    }

    React.useLayoutEffect(() => {
        const b = browserRef.current;
        if (!b) return;
        const path = getQueryParamOrElse(location.search, "path", "");
        b.open(path);
    }, [location.search]);

    const main = <div ref={mountRef} />;
    return <MainContainer main={main} />;
}
