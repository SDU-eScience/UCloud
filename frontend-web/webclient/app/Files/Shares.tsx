import * as React from "react";
import {useEffect, useRef, useState} from "react";
import SharesApi, {Share, ShareLink, shareLinksApi, ShareState} from "@/UCloud/SharesApi";
import {NavigateFunction, useNavigate} from "react-router";
import {buildQueryString} from "@/Utilities/URIUtilities";
import * as Heading from "@/ui-components/Heading";
import {useAvatars} from "@/AvataaarLib/hook";
import {Client} from "@/Authentication/HttpClientInstance";
import {LinkInfo, SidebarTabId} from "@/ui-components/SidebarComponents";
import {Box, Button, Flex, Icon, Input, RadioTile, RadioTilesContainer, Text, Tooltip, Truncate} from "@/ui-components";
import {accounting, BulkResponse, PageV2} from "@/UCloud";
import {callAPI, callAPIWithErrorHandler, InvokeCommand, noopCall, useCloudAPI} from "@/Authentication/DataHook";
import {FindById, ResourceBrowseCallbacks} from "@/UCloud/ResourceApi";
import {
    bulkRequestOf,
    copyToClipboard,
    createHTMLElements,
    displayErrorMessageOrDefault,
    doNothing,
    stopPropagation,
    timestampUnixMs
} from "@/UtilityFunctions";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {dialogStore} from "@/Dialog/DialogStore";
import AppRoutes from "@/Routes";
import {injectStyleSimple} from "@/Unstyled";
import MainContainer from "@/ui-components/MainContainer";
import {useDispatch} from "react-redux";
import {usePage} from "@/Navigation/Redux";
import {
    clearFilterStorageValue,
    dateRangeFilters,
    EmptyReasonTag,
    ResourceBrowseFeatures,
    ResourceBrowser,
    ResourceBrowserOpts
} from "@/ui-components/ResourceBrowser";
import {dateToString} from "@/Utilities/DateUtilities";
import {fileName} from "@/Utilities/FileUtilities";
import {ReactStaticRenderer} from "@/Utilities/ReactStaticRenderer";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {div} from "@/Utilities/HTMLUtilities";
import {FlexClass} from "@/ui-components/Flex";
import {ButtonGroupClass} from "@/ui-components/ButtonGroup";
import {defaultModalStyle} from "@/Utilities/ModalUtilities";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import Avatar from "@/AvataaarLib/avatar";
import {emptyPageV2} from "@/Utilities/PageUtilities";
import {useProjectId} from "@/Project/Api";
import {HTMLTooltip} from "@/ui-components/Tooltip";
import {TruncateClass} from "@/ui-components/Truncate";

export const sharesLinksInfo: LinkInfo[] = [
    {text: "Shared with me", to: AppRoutes.shares.sharedWithMe(), icon: "share", tab: SidebarTabId.FILES},
    {text: "Shared by me", to: AppRoutes.shares.sharedByMe(), icon: "shareMenu", tab: SidebarTabId.FILES},
]

function daysLeftToTimestamp(timestamp: number): number {
    return Math.floor((timestamp - timestampUnixMs()) / 1000 / 3600 / 24);
}

function inviteLinkFromToken(token: string): string {
    return window.location.origin + "/app/shares/invite/" + token;
}

let avatarCache: Record<string, ReactStaticRenderer> = {}

const ShareModalStyle = {
    ...defaultModalStyle,
    content: {...defaultModalStyle.content, minHeight: undefined, top: "25%"}
}

interface SelectedShare {
    path: string;
    product: accounting.ProductReference
}

interface ShareCallbacks {
    invokeCommand: InvokeCommand;
    navigate: NavigateFunction;
}

export function addShareModal(selected: SelectedShare, cb: ShareCallbacks): void {
    dialogStore.addDialog(
        <ShareModal
            selected={selected}
            cb={cb}
        />,
        doNothing, true, ShareModalStyle
    );
}

export const ICONS: Record<string, ReactStaticRenderer | null> = {
    READ_ICON: null,
    EDIT_ICON: null,
}

new ReactStaticRenderer(() =>
    <RadioTilesContainer height={48} onClick={stopPropagation}>
        <RadioTile
            label={"Read"}
            onChange={noopCall}
            icon={"search"}
            checked={false}
            name={"READ"}
            height={40}
            fontSize={"0.5em"}
        />
    </RadioTilesContainer>
).promise.then(it => ICONS.READ_ICON = it);

new ReactStaticRenderer(() =>
    <RadioTilesContainer height={48} onClick={stopPropagation}>
        <RadioTile
            disabled
            label={"Edit"}
            onChange={noopCall}
            icon={"edit"}
            checked={false}
            name={"EDIT"}
            height={40}
            fontSize={"0.5em"}
        />
    </RadioTilesContainer>
).promise.then(it => ICONS.EDIT_ICON = it);

const ShareModal: React.FunctionComponent<{
    selected: SelectedShare,
    cb: ShareCallbacks
}> = ({selected, cb}) => {
    const [inviteLinks, fetchLinks] = useCloudAPI<PageV2<ShareLink>>({noop: true}, emptyPageV2);
    const [editingLink, setEditingLink] = useState<string | undefined>(undefined);
    const [selectedPermission, setSelectedPermission] = useState<string>("READ");
    const usernameRef = useRef<HTMLInputElement>(null);

    const permissions = [
        {text: "Read", value: "READ"},
        {text: "Edit", value: "EDIT"}
    ];

    useEffect(() => {
        if (editingLink) {
            const link = inviteLinks.data.items.find(it => it.token === editingLink);
            const permissionSelected = link?.permissions.includes("EDIT") ? "EDIT" : "READ"

            setSelectedPermission(permissionSelected);
        }
    }, [editingLink, inviteLinks]);

    useEffect(() => {
        fetchLinks(
            shareLinksApi.browse({itemsPerPage: 10, path: selected.path}),
        );
    }, []);

    return !editingLink ? <>
        <Box mb="40px" onKeyDown={e => {
            if (e.key !== "Escape") {
                e.stopPropagation()
            }
        }}>
            <Heading.h3 mb={"10px"}>Share</Heading.h3>
            <form onSubmit={e => {
                e.preventDefault();

                if (!usernameRef?.current?.value) return;

                cb.invokeCommand<BulkResponse<FindById>>(
                    SharesApi.create(
                        bulkRequestOf({
                            sharedWith: usernameRef.current?.value ?? "",
                            sourceFilePath: selected.path,
                            permissions: ["READ"],
                            product: selected.product
                        })
                    )
                ).then(it => {
                    if (it?.responses) {
                        cb.navigate(`/shares/outgoing`);
                        dialogStore.success();
                    }
                }).catch(e => displayErrorMessageOrDefault(e, "Failed to share file."));
            }}>
                <Flex>
                    <Input inputRef={usernameRef} placeholder={"Username"} rightLabel />
                    <Button type={"submit"} color={"successMain"} attached>Share</Button>
                </Flex>
            </form>
        </Box>

        {inviteLinks.data.items.length < 1 ? <>
            <Heading.h3>Share with link</Heading.h3>
            <Box textAlign="center">
                <Text mb="20px" mt="20px">Share files with other users with a link</Text>
                <Button
                    onClick={async () => {
                        await callAPIWithErrorHandler(
                            shareLinksApi.create({path: selected.path})
                        );

                        fetchLinks(
                            shareLinksApi.browse({itemsPerPage: 10, path: selected.path}),
                        );
                    }}
                >Create link</Button>
            </Box>
        </> : <>
            <Flex justifyContent="space-between">
                <Heading.h3>Share with link</Heading.h3>
                <Box textAlign="right">
                    <Button
                        onClick={async () => {
                            await callAPIWithErrorHandler(
                                shareLinksApi.create({path: selected.path})
                            );

                            fetchLinks(
                                shareLinksApi.browse({itemsPerPage: 10, path: selected.path})
                            );
                        }}
                    >Create link</Button>
                </Box>
            </Flex>
            <Box mt={20}>
                {inviteLinks.data.items.map(link => (
                    <Box key={link.token} mb="10px">
                        <Flex justifyContent="space-between">

                            <Flex flexDirection={"column"}>
                                <Tooltip
                                    trigger={(
                                        <Input
                                            readOnly
                                            style={{"cursor": "pointer"}}
                                            onClick={() => {
                                                copyToClipboard({value: inviteLinkFromToken(link.token), message: "Link copied to clipboard"})
                                            }}
                                            mr={10}
                                            value={inviteLinkFromToken(link.token)}
                                            width="500px"
                                        />
                                    )}
                                >
                                    Click to copy link to clipboard
                                </Tooltip>
                                <Text fontSize={12}>This link will automatically expire in {daysLeftToTimestamp(link.expires)} days</Text>
                            </Flex>
                            <Flex>
                                <Button mr="5px" height={40} onClick={() => setEditingLink(link.token)}>
                                    <Icon name="edit" size={20} />
                                </Button>

                                <ConfirmationButton
                                    icon="trash"
                                    color="errorMain"
                                    height={40}
                                    onAction={async () => {
                                        await callAPIWithErrorHandler(
                                            shareLinksApi.delete({token: link.token, path: selected.path})
                                        );

                                        fetchLinks(
                                            shareLinksApi.browse({itemsPerPage: 10, path: selected.path})
                                        );
                                    }}
                                />
                            </Flex>
                        </Flex>
                    </Box>
                ))}
            </Box>
        </>}
    </> : <>
        <Box minHeight="200px">
            <Flex>
                <Button mr={20} onClick={() => setEditingLink(undefined)}>
                    <Icon name="backward" size={20} />
                </Button>
                <Heading.h3>Edit link settings</Heading.h3>
            </Flex>

            <Flex justifyContent="space-between" mt={20} mb={10}>
                <Text pt="10px">Anyone with the link can</Text>
                <div className={SelectBoxClass}>
                    <ClickableDropdown
                        useMousePositioning
                        width="100px"
                        chevron
                        trigger={<>{permissions.find(it => it.value === selectedPermission)?.text}</>}
                        options={permissions}
                        onChange={async chosen => {
                            const newPermissions = chosen == "EDIT" ? ["EDIT", "READ"] : ["READ"];

                            await callAPIWithErrorHandler(
                                shareLinksApi.update({token: editingLink, path: selected.path, permissions: newPermissions})
                            );

                            fetchLinks(
                                shareLinksApi.browse({itemsPerPage: 10, path: selected.path})
                            );
                        }}
                    />
                </div>
            </Flex>
        </Box>
    </>;

};

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    filters: true,
    sorting: true,
    breadcrumbsSeparatedBySlashes: false,
    showColumnTitles: true,
    dragToSelect: true,
};

const defaultRetrieveFlags: {itemsPerPage: number, filterIngoing: true} = {
    itemsPerPage: 250,
    filterIngoing: true,
};

interface SetShowBrowserHack {
    setShowBrowser?: (show: boolean) => void;
}

const TITLE = "Shared with me";

export function IngoingSharesBrowse({opts}: {opts?: ResourceBrowserOpts<Share> & {filterState?: ShareState} & SetShowBrowserHack}): JSX.Element {

    //Projects should now show this page
    const activeProjectId = useProjectId();
    React.useEffect(() => {
        if (activeProjectId && !opts?.embedded) {
            navigate(AppRoutes.dashboard.dashboardA());
        }
    }, [activeProjectId])

    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<Share> | null>(null);
    const navigate = useNavigate();
    const dispatch = useDispatch();

    const avatars = useAvatars();

    if (!opts?.embedded) {
        usePage(TITLE, SidebarTabId.FILES);
    }

    const features: ResourceBrowseFeatures = {
        ...FEATURES,
        dragToSelect: !opts?.embedded
    };

    const dateRanges = dateRangeFilters("Created after");

    avatars.subscribe(() => {
        avatarCache = {};
        browserRef.current?.renderRows()
    });

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<Share>(mount, TITLE, opts).init(browserRef, features, "", browser => {
                // Removed stored filters that shouldn't persist.
                dateRanges.keys.forEach(it => clearFilterStorageValue(browser.resourceName, it));

                browser.setColumns([{name: "Filename"}, {name: "Share state", columnWidth: 200}, {name: "Last updated", columnWidth: 160}, {name: "Shared by", columnWidth: 90}]);

                browser.on("skipOpen", (oldPath, path, share) => Client.username !== share?.owner.createdBy && share?.status.state === "PENDING");
                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) {
                        navigate(buildQueryString("/files", {path: resource.status.shareAvailableAt}));
                        return;
                    }

                    callAPI(
                        SharesApi.browse({
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...opts?.additionalFilters
                        })
                    ).then(result => {
                        if (opts?.filterState) {
                            // HACK(Jonas): You can't fetch pending shares from backend, so we do this hacky thing instead.
                            const filteredResult = {...result, items: result.items.filter(it => it.status.state === opts.filterState)};
                            browser.registerPage(filteredResult, newPath, true);
                            browser.renderRows();
                            opts?.setShowBrowser?.(filteredResult.items.length > 0);
                            return result;
                        }

                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                        return result;
                    });
                });

                browser.on("wantToFetchNextPage", async (path) => {
                    const result = await callAPI(
                        SharesApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...opts?.additionalFilters
                        })
                    );
                    browser.registerPage(result, path, false);
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
                        avatars.updateCache([...currentAvatars]);
                        currentAvatars.forEach(it => fetchedAvatars.add(it));
                        currentAvatars.clear();
                    }
                });

                browser.on("renderRow", (share, row, dims) => {
                    const [icon, setIcon] = ResourceBrowser.defaultIconRenderer();
                    row.title.append(icon);
                    ResourceBrowser.icons.renderIcon({
                        name: "ftSharesFolder",
                        color: "FtFolderColor",
                        color2: "FtFolderColor2",
                        height: 64,
                        width: 64
                    }).then(setIcon);

                    // Row title
                    row.title.append(
                        ResourceBrowser.defaultTitleRenderer(
                            share.owner.createdBy !== Client.username ?
                                fileName(share.specification.sourceFilePath) :
                                share.specification.sharedWith ?? share.id,
                            dims,
                            row
                        )
                    );

                    const pendingSharedWithMe = share.owner.createdBy !== Client.username && share.status.state === "PENDING";

                    // Row stat1
                    const wrapper = div("");
                    row.stat1.append(wrapper);
                    wrapper.className = FlexClass;
                    wrapper.style.marginTop = wrapper.style.marginBottom = "auto"

                    const isEdit = share.specification.permissions.some(it => it === "EDIT");
                    const radioTilesContainerWrapper = document.createElement("div");
                    radioTilesContainerWrapper.style.marginRight = "8px";
                    wrapper.appendChild(radioTilesContainerWrapper);
                    if (isEdit) {
                        if (ICONS.EDIT_ICON) radioTilesContainerWrapper.append(ICONS.EDIT_ICON.clone());
                    } else {
                        if (ICONS.READ_ICON) radioTilesContainerWrapper.append(ICONS.READ_ICON.clone());
                    }


                    if (pendingSharedWithMe) {
                        const group = createHTMLElements<HTMLDivElement>({
                            tagType: "div",
                            className: ButtonGroupClass,
                            style: {marginTop: "auto", marginBottom: "auto", marginLeft: "12px"},
                        });
                        wrapper.append(group);
                        group.appendChild(browser.defaultButtonRenderer({
                            onClick: async () => {
                                await callAPI(SharesApi.approve(bulkRequestOf({id: share.id})));
                                browser.refresh();
                            },
                            show(res) {
                                return true;
                            },
                            text: "Accept"
                        }, share, {color: "successMain", width: "72px"})!);
                        group.appendChild(browser.defaultButtonRenderer({
                            onClick: async () => {
                                await callAPI(SharesApi.reject(bulkRequestOf({id: share.id})))
                                browser.refresh();
                            },
                            show(res) {
                                return true;
                            },
                            text: "Decline"
                        }, share, {color: "errorMain", width: "72px"})!);
                    } else {
                        const {state} = share.status;
                        const [stateIcon, setStateIcon] = ResourceBrowser.defaultIconRenderer();
                        stateIcon.style.marginTop = stateIcon.style.marginBottom = "auto";
                        wrapper.appendChild(stateIcon);
                        ResourceBrowser.icons.renderIcon({
                            ...StateIconAndColor[state],
                            color2: "iconColor2",
                            height: 64,
                            width: 64,
                        }).then(setStateIcon);
                    }

                    // Row stat2
                    row.stat2.appendChild(createHTMLElements({
                        tagType: "div",
                        style: {marginTop: "auto", marginBottom: "auto"},
                        innerText: dateToString(share.createdAt ?? timestampUnixMs())
                    }));

                    // Row stat3
                    const avatar = avatars.avatar(share.owner.createdBy);
                    if (!fetchedAvatars.has(share.owner.createdBy)) {
                        currentAvatars.add(share.owner.createdBy);
                    }

                    // Note(Jonas): To any future reader (as opposed to past reader?) the avatarWrapper is to ensure that
                    // the re-render doesn't happen multiple times, when re-rendering. The avatarWrapper can be dead,
                    // so attaching doesn't do anything, instead of risking the promise resolving after a second re-render,
                    // causing multiple avatars to be shown.
                    const avatarWrapper = document.createElement("div");
                    row.stat3.append(avatarWrapper);
                    HTMLTooltip(avatarWrapper, createHTMLElements({tagType: "div", className: TruncateClass, innerText: `Shared by ${share.owner.createdBy}`}), {tooltipContentWidth: 250});
                    if (avatarCache[share.owner.createdBy]) {
                        const avatar = avatarCache[share.owner.createdBy].clone()
                        avatarWrapper.appendChild(avatar);
                    } else {
                        new ReactStaticRenderer(() =>
                            <Avatar style={{height: "40px", width: "40px"}} avatarStyle="Circle" {...avatar} />
                        ).promise.then(it => {
                            avatarCache[share.owner.createdBy] = it;
                            const avatar = it.clone();
                            avatarWrapper.appendChild(avatar);
                        });
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

                browser.on("nameOfEntry", s => s.id);
                browser.on("pathToEntry", s => s.id);
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
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as any;
                    return SharesApi.retrieveOperations().filter(op => op.enabled(entries, callbacks, entries));
                });
                browser.on("generateBreadcrumbs", () => [{title: "Shared with me", absolutePath: ""}]);
                browser.on("search", query => {
                    browser.searchQuery = query;
                    browser.currentPath = "/search";
                    browser.cachedData["/search"] = [];
                    browser.renderRows();
                    browser.renderOperations();

                    callAPI(SharesApi.search({
                        query,
                        itemsPerPage: 250,
                        flags: {},
                    })).then(res => {
                        if (browser.currentPath !== "/search") return;
                        browser.registerPage(res, "/search", true);
                        browser.renderRows();
                        browser.renderBreadcrumbs();
                    })
                });

                browser.on("searchHidden", () => {
                    browser.searchQuery = "";
                    browser.currentPath = "/";
                    browser.renderRows();
                    browser.renderOperations();
                    browser.renderBreadcrumbs();
                });
            });
        }
        if (opts?.reloadRef) {
            opts.reloadRef.current = () => {
                browserRef.current?.refresh();
            }
        }
    }, []);

    if (!opts?.isModal && !opts?.embedded) {
        useSetRefreshFunction(() => {
            browserRef.current?.refresh();
        });
    }

    return <MainContainer main={<div ref={mountRef} />} />;
}


const SelectBoxClass = injectStyleSimple("select-box", `
    border: 2px solid var(--borderColor);
    border-radius: 5px;
    padding: 10px;
`);

export const StateIconAndColor: Record<ShareState, {name: IconName, color: ThemeColor}> = {
    "APPROVED": {color: "successMain", name: "check"},
    "PENDING": {color: "primaryMain", name: "questionSolid"},
    "REJECTED": {color: "errorMain", name: "close"},
}

