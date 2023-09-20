import * as React from "react";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import SharesApi, {OutgoingShareGroup, OutgoingShareGroupPreview, Share, ShareState} from "@/UCloud/SharesApi";
import {useCallback, useMemo, useRef, useState} from "react";
import {ItemRow, StandardBrowse} from "@/ui-components/Browse";
import MainContainer from "@/MainContainer/MainContainer";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {PrettyFilePath, prettyFilePath} from "@/Files/FilePath";
import {
    Button,
    Flex,
    Grid,
    Input, Link,
    List,
    RadioTile,
    RadioTilesContainer,
    Tooltip
} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {capitalized, displayErrorMessageOrDefault, doNothing, extractErrorMessage, stopPropagation, timestampUnixMs} from "@/UtilityFunctions";
import {bulkRequestOf, placeholderProduct} from "@/DefaultObjects";
import {Client} from "@/Authentication/HttpClientInstance";
import {useToggleSet} from "@/Utilities/ToggleSet";
import {callAPI, noopCall, useCloudCommand} from "@/Authentication/DataHook";
import {ResourceBrowseCallbacks} from "@/UCloud/ResourceApi";
import {useLocation, useNavigate} from "react-router";
import {useDispatch} from "react-redux";
import Icon from "../ui-components/Icon";
import {buildQueryString, getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {useAvatars} from "@/AvataaarLib/hook";
import {Spacer} from "@/ui-components/Spacer";
import {BrowseType} from "@/Resource/BrowseType";
import {api as FilesApi} from "@/UCloud/FilesApi";
import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, SelectionMode, clearFilterStorageValue, dateRangeFilters} from "@/ui-components/ResourceBrowser";
import {ReactStaticRenderer} from "@/Utilities/ReactStaticRenderer";
import {Avatar} from "@/AvataaarLib";
import {ShareModal, StateIconAndColor} from "./Shares";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import AppRoutes from "@/Routes";
import {Operation} from "@/ui-components/Operation";
import {ButtonClass} from "@/ui-components/Button";
import {arrayToPage} from "@/Types";
import {dialogStore} from "@/Dialog/DialogStore";
import {snackbarStore} from "@/Snackbar/SnackbarStore";

function fakeShare(path: string, preview: OutgoingShareGroupPreview): Share {
    return {
        id: preview.shareId,
        owner: {
            createdBy: Client.username ?? "_ucloud",
        },
        status: {
            state: preview.state,
        },
        permissions: {
            myself: ["ADMIN"],
            others: []
        },
        createdAt: timestampUnixMs(),
        updates: [],
        specification: {
            permissions: preview.permissions,
            sharedWith: preview.sharedWith,
            sourceFilePath: path,
            product: placeholderProduct()
        }
    };
}

// Unused(Jonas): Should probably remove, but wait until next component is finished.
const SharesOutgoing: React.FunctionComponent = () => {
    useTitle("Shares (Outgoing)");
    // HACK(Jonas): DISABLE UNTIL ALL SHARES CAN BE SEARCHED
    // useResourceSearch(SharesApi);

    const navigate = useNavigate();
    const dispatch = useDispatch();
    const [commandLoading, invokeCommand] = useCloudCommand();
    const reloadRef = useRef<() => void>(doNothing);
    const avatars = useAvatars();

    const callbacks: ResourceBrowseCallbacks<Share> = useMemo(() => ({
        commandLoading,
        invokeCommand,
        reload: () => reloadRef.current(),
        api: SharesApi,
        navigate,
        isCreating: false,
        embedded: false,
        dispatch,
        supportByProvider: {productsByProvider: {}},
        isWorkspaceAdmin: true
    }), [dispatch, commandLoading, invokeCommand]);

    const generateFetch = useCallback((next?: string) => {
        callAPI(SharesApi.browseOutgoing({next, itemsPerPage: 50})).then(it => console.log("items", it.items));
        return SharesApi.browseOutgoing({next, itemsPerPage: 50});
    }, []);

    const onGroupsLoaded = useCallback((items: OutgoingShareGroup[]) => {
        if (items.length === 0) return;
        avatars.updateCache(items.reduce<string[]>((acc, it) => {
            it.sharePreview.forEach(preview => {
                acc.push(preview.sharedWith);
            });
            return acc;
        }, []));
    }, []);

    const pageRenderer = useCallback((page: OutgoingShareGroup[]) => {
        return <Grid gridTemplateColumns={"1fr"} gridGap={"16px"}>
            {page.length === 0 ?
                <Heading.h3 textAlign={"center"}>
                    No shares
                    <br />
                    <small>You can create a new share by clicking 'Share' on one of your directories.</small>
                </Heading.h3> :
                null
            }

            {page.map(it => <ShareGroup key={it.sourceFilePath} group={it} cb={callbacks} />)}
        </Grid>;
    }, []);

    return <MainContainer
        main={
            <StandardBrowse
                generateCall={generateFetch} pageRenderer={pageRenderer} reloadRef={reloadRef}
                onLoad={onGroupsLoaded}
            />
        }
    />;
};

enum ShareValidateState {
    NOT_VALIDATED,
    VALIDATED,
    DELETED
}

const ShareGroup: React.FunctionComponent<{
    group: OutgoingShareGroup;
    cb: ResourceBrowseCallbacks<Share>;
}> = ({group, cb}) => {
    const shares = useMemo(() => group.sharePreview.map(it => fakeShare(group.sourceFilePath, it)), [group]);
    const toggleSet = useToggleSet(shares);
    const [isCreatingShare, setCreatingShare] = React.useState(false);

    const usernameInputRef = useRef<HTMLInputElement>(null);
    const [isEdit, setIsEdit] = useState(false);
    const setToEdit = useCallback(() => {
        setIsEdit(true);
    }, [setIsEdit]);
    const setToRead = useCallback(() => {
        setIsEdit(false);
    }, [setIsEdit]);

    const onShare = useCallback(async (e) => {
        e?.preventDefault();
        const usernameInput = usernameInputRef.current;
        if (!usernameInput) return;

        await cb.invokeCommand(SharesApi.create(bulkRequestOf({
            sharedWith: usernameInput.value,
            sourceFilePath: group.sourceFilePath,
            product: group.storageProduct,
            permissions: isEdit ? ["READ", "EDIT"] : ["READ"]
        })));

        cb.reload();
        setCreatingShare(false);
        usernameInput.value = "";
    }, [cb, isEdit, group.sourceFilePath]);

    const [, invokeCommand] = useCloudCommand();

    const [shareValidated, setValidated] = React.useState(ShareValidateState.NOT_VALIDATED);

    React.useEffect(() => {
        validateShare(group.sourceFilePath);
    }, [shares]);

    const isDeleted = shareValidated === ShareValidateState.DELETED;

    return <HighlightedCard
        color={"blue"}
        title={
            <Spacer
                left={<>
                    <Icon
                        name="ftSharesFolder"
                        m={8}
                        ml={0}
                        size="20"
                        color="FtFolderColor"
                        color2="FtFolderColor2"
                    />

                    {shareValidated === ShareValidateState.VALIDATED ? (
                        <Link to={buildQueryString("/files", {path: group.sourceFilePath})}>
                            <Heading.h3><PrettyFilePath path={group.sourceFilePath} /></Heading.h3>
                        </Link>) : (<>
                            <Heading.h3><PrettyFilePath path={group.sourceFilePath} /></Heading.h3>
                            {shareValidated !== ShareValidateState.DELETED ? null : (
                                <Tooltip trigger={<Icon cursor="pointer" mt="6px" ml="18px" color="red" name="close" />}>
                                    The folder associated with the share no longer exists.
                                </Tooltip>
                            )}
                        </>
                    )}
                </>}
                right={null}
            />
        }
    >
        <List childPadding={"8px"} bordered={false}>
            {isDeleted ? <Spacer left={null} right={<Button mr="9px" onClick={() => removeInvalidShare(group)} width="136px" color="red">Remove</Button>} /> :
                shares.map((share, idx) => idx == 10 ? null :
                    <ItemRow
                        key={share.specification.sharedWith}
                        browseType={BrowseType.MainContent}
                        item={share}
                        renderer={SharesApi.renderer}
                        toggleSet={toggleSet}
                        operations={SharesApi.retrieveOperations()}
                        callbacks={cb}
                        itemTitle={SharesApi.title}
                        disableSelection
                    />)}
            {isCreatingShare ? <form onSubmit={onShare}>
                <Flex mb={"16px"} mt={"8px"}>
                    <Input placeholder={"Username"} inputRef={usernameInputRef} />
                    <Button type="submit" width="128px" ml="8px"><Icon name={"share"} color={"white"} size={"14px"} mr={".7em"} /> Share</Button>
                    <RadioTilesContainer height={42} mx={"8px"} onClick={stopPropagation}>
                        <RadioTile
                            label={"Read"}
                            onChange={setToRead}
                            icon={"search"}
                            name={"READ"}
                            checked={!isEdit}
                            height={40}
                            fontSize={"0.5em"}
                        />
                        <RadioTile
                            label={"Edit"}
                            onChange={setToEdit}
                            icon={"edit"}
                            name={"EDIT"}
                            checked={isEdit}
                            height={40}
                            fontSize={"0.5em"}
                        />
                    </RadioTilesContainer>
                    <Icon onClick={() => setCreatingShare(false)} cursor="pointer" ml="6px" mt="8px" mr="14px" name="close" color="red" />
                </Flex>
            </form> : <Spacer
                left={null}
                right={isDeleted ? null : <Button onClick={() => setCreatingShare(true)} mr="10px" width="136px" ml="8px"><Icon name="share" color="white" size="14px" mr=".7em" /> Share</Button>}
            />}
        </List>
        {shares.length > 10 ?
            <Link to={buildQueryString("/shares", {filterIngoing: false, filterOriginalPath: group.sourceFilePath})}>
                <Button type={"button"} fullWidth mt={"16px"} mb={"8px"}>View more</Button>
            </Link> : null
        }
    </HighlightedCard>;

    async function validateShare(path: string): Promise<void> {
        try {
            await invokeCommand(FilesApi.retrieve({id: path}), {defaultErrorHandler: false});
            setValidated(ShareValidateState.VALIDATED);
        } catch (e) {
            if ([400, 404].includes(e.request.status)) {
                setValidated(ShareValidateState.DELETED);
            }
        }
    }

    async function removeInvalidShare(group: OutgoingShareGroup): Promise<void> {
        await invokeCommand(SharesApi.remove(bulkRequestOf(...group.sharePreview.map(it => ({id: it.shareId})))));
        cb.reload();
    }
};

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    filters: true,
    sortDirection: true,
    breadcrumbsSeparatedBySlashes: false,
};

const defaultRetrieveFlags: {itemsPerPage: number} = {
    itemsPerPage: 250,
};

const shareValidationCache: Record<string, ShareValidateState> = {};


export function OutgoingSharesBrowse({opts}: {opts?: {additionalFilters?: Record<string, string>}}): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<OutgoingShareGroup | OutgoingShareGroupPreview> | null>(null);
    const navigate = useNavigate();
    const dispatch = useDispatch();
    const location = useLocation();

    const avatars = useAvatars();

    useTitle("Shared by me");

    const features: ResourceBrowseFeatures = FEATURES;

    const dateRanges = dateRangeFilters("Created after");
    var isInitial = true;

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<OutgoingShareGroup | OutgoingShareGroupPreview>(mount, "Shared by me").init(browserRef, features, "", browser => {
                // Removed stored filters that shouldn't persist.
                dateRanges.keys.forEach(it => clearFilterStorageValue(browser.resourceName, it));
                let shouldRemoveFakeDirectory = true;
                const dummyId = "temporary-share-id-that-will-be-unique";
                function showShareInput() {
                    browser.removeEntryFromCurrentPage(it => it.id === dummyId);
                    shouldRemoveFakeDirectory = false;
                    insertFakeEntry(dummyId);
                    const idx = browser.findVirtualRowIndex((it: OutgoingShareGroupPreview) => it.shareId === dummyId);
                    if (idx !== null) browser.ensureRowIsVisible(idx, true);

                    browser.showRenameField(
                        (it: OutgoingShareGroupPreview) => it.shareId === dummyId,
                        () => {
                            browser.removeEntryFromCurrentPage((it: OutgoingShareGroupPreview) => it.shareId === dummyId);

                            const idx = browser.findVirtualRowIndex((it: OutgoingShareGroupPreview) => it.shareId === dummyId);
                            if (idx !== null) {
                                browser.ensureRowIsVisible(idx, true, true);
                                browser.select(idx, SelectionMode.SINGLE);
                            }

                            if (!browser.renameValue) return;
                            const filePath = new URLSearchParams(location.search).get("path") as string;
                            if (!filePath) return;

                            const page = browser.cachedData["/"] as OutgoingShareGroup[];
                            const shareGroup = page.find(it => it.sourceFilePath === filePath);
                            if (!shareGroup) return;

                            callAPI(SharesApi.create(bulkRequestOf({sharedWith: browser.renameValue, sourceFilePath: filePath, permissions: ["READ"], product: shareGroup.storageProduct, conflictPolicy: "RENAME"})))
                                .catch(err => {
                                    snackbarStore.addFailure(extractErrorMessage(err), false);
                                    browser.refresh();
                                });
                        },
                        () => {
                            if (shouldRemoveFakeDirectory) browser.removeEntryFromCurrentPage((it: OutgoingShareGroupPreview) => it.shareId === dummyId);
                        },
                        ""
                    );

                    shouldRemoveFakeDirectory = true;
                };

                function insertFakeEntry(dummyId: string): void {
                    browser.insertEntryIntoCurrentPage({permissions: ["READ"], shareId: dummyId, sharedWith: "", state: "PENDING"} as OutgoingShareGroupPreview);
                }

                function isViewingShareGroupPreview(s: OutgoingShareGroup | OutgoingShareGroupPreview): s is OutgoingShareGroupPreview {
                    return !("sourceFilePath" in s);
                }

                function isDeleted(share: OutgoingShareGroup | OutgoingShareGroupPreview) {
                    return !isViewingShareGroupPreview(share) && shareValidationCache[share.sourceFilePath] === ShareValidateState.DELETED;
                }

                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource && isViewingShareGroupPreview(resource)) {
                        // navigate to share
                        navigate(AppRoutes.resource.properties("shares", resource.shareId));
                        return;
                    }

                    if (resource) { // NOT share group preview
                        navigate(`/shares/outgoing?path=${resource.sourceFilePath}`);
                    }

                    if (oldPath !== newPath) browser.rerender();

                    callAPI(
                        SharesApi.browseOutgoing({
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters
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
                            ...browser.browseFilters
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

                    const searchPath = new URLSearchParams(location.search).get("path") as string;
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

                let currentAvatars = new Set<string>();
                let fetchedAvatars = new Set<string>();
                browser.on("endRenderPage", () => {
                    if (currentAvatars.size > 0) {
                        avatars.updateCache([...currentAvatars]).then(() => browser.rerender());
                        currentAvatars.forEach(it => fetchedAvatars.add(it));
                        currentAvatars.clear();
                    }
                });

                browser.on("renderRow", (share, row, dims) => {
                    const avatarWrapper = document.createElement("div");

                    if (isViewingShareGroupPreview(share)) {
                        const sharedWithAvatar = avatars.avatar(share.sharedWith);
                        row.title.append(avatarWrapper);
                        new ReactStaticRenderer(() =>
                            <Tooltip
                                trigger={<Avatar style={{height: "32px", width: "32px", marginRight: "12px"}} avatarStyle="Circle" {...sharedWithAvatar} />}
                            >
                                Shared with {share.sharedWith}
                            </Tooltip>
                        ).promise.then(it => {
                            avatarWrapper.appendChild(it.clone());
                        });
                    } else {
                        const [icon, setIcon] = ResourceBrowser.defaultIconRenderer();

                        row.title.append(icon);
                        // TODO(Jonas): For some reason, the arrow is not rendered.
                        browser.icons.renderIcon({
                            name: "ftSharesFolder",
                            color: "FtFolderColor",
                            color2: "FtFolderColor2",
                            height: 32,
                            width: 32
                        }).then(setIcon);
                    }

                    const isShareDeleted = isDeleted(share);

                    // Row title
                    if (isViewingShareGroupPreview(share)) {
                        row.title.append(ResourceBrowser.defaultTitleRenderer(share.sharedWith, dims));
                    } else {
                        const node = document.createTextNode(share.sourceFilePath);
                        row.title.append(node);
                        prettyFilePath(share.sourceFilePath).then(title => {
                            node.textContent = ResourceBrowser.defaultTitleRenderer(title, dims);
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
                        // Note(Jonas): To any future reader (as opposed to past reader?) the radioTilesContainerWrapper is to ensure that
                        // the re-render doesn't happen multiple times, when re-rendering. The radioTilesContainerWrapper can be dead,
                        // so attaching doesn't do anything, instead of risking the promise resolving after a second re-render,
                        // causing multiple avatars to be shown.
                        const radioTilesContainerWrapper = document.createElement("div");
                        row.stat1.append(radioTilesContainerWrapper);
                        new ReactStaticRenderer(() =>
                            <RadioTilesContainer height={48} onClick={stopPropagation}>
                                <RadioTile
                                    disabled={share.state === "REJECTED"}
                                    label={"Read"}
                                    onChange={noopCall}
                                    icon={"search"}
                                    name={share.shareId}
                                    checked={!isEdit}
                                    height={40}
                                    fontSize={"0.5em"}
                                />
                                <RadioTile
                                    disabled={share.state === "REJECTED"}
                                    label={"Edit"}
                                    onChange={noopCall}
                                    icon={"edit"}
                                    name={share.shareId}
                                    checked={isEdit}
                                    height={40}
                                    fontSize={"0.5em"}
                                />
                            </RadioTilesContainer>
                        ).promise.then(it => {
                            radioTilesContainerWrapper.append(it.clone());
                            if (share.state !== "REJECTED") {
                                const tiles = radioTilesContainerWrapper.querySelectorAll(":scope input[type='radio']");
                                const readTile = tiles.item(0);
                                const writeTile = tiles.item(1);
                                if (readTile && writeTile) {
                                    readTile["onclick"] = writeTile["onclick"] = e => e.stopPropagation();
                                    readTile["onchange"] = () => {
                                        if (share.permissions.includes("EDIT")) {
                                            updatePermissions(share, false);
                                            share.permissions = ["READ"];
                                            browser.renderRows();
                                        }
                                    }
                                    writeTile["onchange"] = () => {
                                        if (!share.permissions.includes("EDIT")) {
                                            updatePermissions(share, true);
                                            share.permissions = ["READ", "EDIT"];
                                            browser.renderRows();
                                        }
                                    }
                                }
                            }
                        });
                    }

                    // Row stat2
                    if (!isViewingShareGroupPreview(share) && isShareDeleted) {
                        const button = document.createElement("button");
                        button.innerText = "Remove share";
                        button.className = ButtonClass;
                        button.style.color = "var(--white)";
                        button.style.backgroundColor = "var(--red)";
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
                        const [stateIcon, setStateIcon] = ResourceBrowser.defaultIconRenderer();
                        stateIcon.style.marginTop = stateIcon.style.marginBottom = "auto";
                        row.stat2.appendChild(stateIcon);
                        const text = document.createTextNode("");
                        let state: ShareState;

                        if (isViewingShareGroupPreview(share)) {
                            state = share.state;
                            text.textContent = capitalized(share.state.toString());
                        } else {
                            const pending = share.sharePreview.filter(it => it.state === "PENDING");
                            const rejected = share.sharePreview.filter(it => it.state === "REJECTED");
                            const anyPending = pending.length > 0;
                            const anyRejected = pending.length > 0;

                            if (anyPending) {
                                text.textContent = `${pending.length} pending`;
                                state = "PENDING";
                            } else if (anyRejected) {
                                text.textContent = `${rejected.length} pending`;
                                state = "REJECTED";
                            } else { // All must be approved in none are rejected or pending. 
                                text.textContent = `All accepted`;
                                state = "APPROVED";
                            }
                        }

                        row.stat2.append(text);
                        browser.icons.renderIcon({
                            ...StateIconAndColor[state],
                            color2: "black",
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
                        row.stat3.append(avatarWrapper);
                        const sharedWithAvatars = share.sharePreview.map(it => avatars.avatar(it.sharedWith));
                        new ReactStaticRenderer(() =>
                            <Tooltip
                                trigger={<Flex marginRight="26px">
                                    {sharedWithAvatars.slice(0, 5).map(
                                        (avatar, index) => <div key={share.sharePreview[index].sharedWith} style={{marginRight: "-26px"}}>
                                            <Avatar style={{height: "40px", width: "40px"}} avatarStyle="Circle" {...avatar} />
                                        </div>
                                    )}
                                </Flex>}
                            >
                                Shared with {share.sharePreview.map(it => it.sharedWith)}
                            </Tooltip>
                        ).promise.then(it => {
                            avatarWrapper.appendChild(it.clone());
                        });
                    }
                });

                browser.setEmptyIcon("share");

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
                    const support = {productsByProvider: {}}; // TODO(Jonas), FIXME(Jonas): I assume that we need to do something different here.
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
                        async onClick(selected, extra, all) {
                            if (isViewingShareGroupPreview(selected[0])) {
                                const previews = selected as OutgoingShareGroupPreview[];
                                await extra.invokeCommand(extra.api.remove(bulkRequestOf(...previews.map(it => ({id: it.shareId})))));
                                extra.reload();
                            }
                        }
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
                    }, {
                        icon: "share",
                        text: "Share",
                        enabled(selected) {return selected.length === 1 && !isViewingShareGroupPreview(selected[0])},
                        onClick(selected, cb) {
                            const [share] = selected;
                            if (!isViewingShareGroupPreview(share))
                                dialogStore.addDialog(
                                    <ShareModal
                                        selected={{path: share.sourceFilePath, product: share.storageProduct}}
                                        cb={cb}
                                    />,
                                    doNothing, true
                                );
                        }
                    }, {
                        icon: "share",
                        text: "Share",
                        enabled(selected) {
                            const hasPath = window.location.search !== "";
                            return selected.length === 0 && hasPath;
                        },
                        onClick() {
                            showShareInput();
                        }
                    }];
                    return operations.filter(it => it.enabled(entries, callbacks, entries));
                });

                browser.on("generateBreadcrumbs", () => [{title: "Shared by me", absolutePath: ""}]);

                async function updatePermissions(share: OutgoingShareGroupPreview, isEditing: boolean) {
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
                };
            });

            browserRef.current!.renameField.style.marginLeft = "18px";
        }
    }, []);

    useRefreshFunction(() => {
        browserRef.current?.refresh();
    });

    React.useLayoutEffect(() => {
        const b = browserRef.current;
        if (!b) return;
        const path = getQueryParamOrElse(location.search, "path", "");
        b.open(path);
    }, [location.search]);

    const main = <div ref={mountRef} />;
    return <MainContainer main={main} />;
}