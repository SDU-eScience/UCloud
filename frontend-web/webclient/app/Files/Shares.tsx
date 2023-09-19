import * as React from "react";
import {ResourceBrowse} from "@/Resource/Browse";
import {ResourceRouter} from "@/Resource/Router";
import SharesApi, {OutgoingShareGroup, OutgoingShareGroupPreview, Share, ShareLink, ShareState, shareLinksApi} from "@/UCloud/SharesApi";
import {NavigateFunction, useLocation, useNavigate} from "react-router";
import {buildQueryString, getQueryParam} from "@/Utilities/URIUtilities";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import * as Heading from "@/ui-components/Heading";
import {useAvatars} from "@/AvataaarLib/hook";
import {BrowseType} from "@/Resource/BrowseType";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Client} from "@/Authentication/HttpClientInstance";
import {LinkInfo} from "@/ui-components/SidebarLink";
import {Box, Button, Flex, Icon, Input, RadioTile, RadioTilesContainer, Text, Tooltip} from "@/ui-components";
import {BulkResponse, PageV2} from "@/UCloud";
import {callAPI, callAPIWithErrorHandler, noopCall, useCloudAPI} from "@/Authentication/DataHook";
import {UFile} from "@/UCloud/FilesApi";
import {FindById, ResourceBrowseCallbacks} from "@/UCloud/ResourceApi";
import {copyToClipboard, stopPropagation, timestampUnixMs} from "@/UtilityFunctions";
import {bulkRequestOf, emptyPageV2} from "@/DefaultObjects";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {dialogStore} from "@/Dialog/DialogStore";
import AppRoutes from "@/Routes";
import {injectStyleSimple} from "@/Unstyled";
import MainContainer from "@/MainContainer/MainContainer";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useDispatch} from "react-redux";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, clearFilterStorageValue, dateRangeFilters, providerIcon} from "@/ui-components/ResourceBrowser";
import {dateToString} from "@/Utilities/DateUtilities";
import {fileName} from "@/Utilities/FileUtilities";
import {ReactStaticRenderer} from "@/Utilities/ReactStaticRenderer";
import {Avatar} from "@/AvataaarLib";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";

export const sharesLinksInfo: LinkInfo[] = [
    {text: "Shared with me", to: AppRoutes.shares.sharedWithMe(), icon: "share"},
    {text: "Shared by me", to: AppRoutes.shares.sharedByMe(), icon: "shareMenu"},
]

function daysLeftToTimestamp(timestamp: number): number {
    return Math.floor((timestamp - timestampUnixMs()) / 1000 / 3600 / 24);
}

function inviteLinkFromToken(token: string): string {
    return window.location.origin + "/app/shares/invite/" + token;
}

export const ShareModal: React.FunctionComponent<{
    selected: UFile,
    cb: ResourceBrowseCallbacks<UFile>
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
            shareLinksApi.browse({itemsPerPage: 10, path: selected.id}),
        );
    }, []);

    return !editingLink ? <>
        <Box mb="40px">
            <Heading.h3 mb={"10px"}>Share</Heading.h3>
            <form onSubmit={async (e) => {
                e.preventDefault();

                if (!usernameRef?.current?.value) return;

                await cb.invokeCommand<BulkResponse<FindById>>(
                    SharesApi.create(
                        bulkRequestOf({
                            sharedWith: usernameRef.current?.value ?? "",
                            sourceFilePath: selected.id,
                            permissions: ["READ" as const],
                            product: selected.specification.product
                        })
                    )
                ).then(it => {
                    if (it?.responses) {
                        cb.navigate(`/shares/outgoing`);
                        dialogStore.success();
                    }
                });
            }}>
                <Flex>
                    <Input inputRef={usernameRef} placeholder={"Username"} rightLabel />
                    <Button type={"submit"} color={"green"} attached>Share</Button>
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
                            shareLinksApi.create({path: selected.id})
                        );

                        fetchLinks(
                            shareLinksApi.browse({itemsPerPage: 10, path: selected.id}),
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
                                shareLinksApi.create({path: selected.id})
                            );

                            fetchLinks(
                                shareLinksApi.browse({itemsPerPage: 10, path: selected.id})
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
                                    color="red"
                                    height={40}
                                    onAction={async () => {
                                        await callAPIWithErrorHandler(
                                            shareLinksApi.delete({token: link.token, path: selected.id})
                                        );

                                        fetchLinks(
                                            shareLinksApi.browse({itemsPerPage: 10, path: selected.id})
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
                                shareLinksApi.update({token: editingLink, path: selected.id, permissions: newPermissions})
                            );

                            fetchLinks(
                                shareLinksApi.browse({itemsPerPage: 10, path: selected.id})
                            );
                        }}
                    />
                </div>
            </Flex>
        </Box>
    </>;

};

export const ShareBrowse: React.FunctionComponent<{
    onSelect?: (selection: Share) => void;
    isSearch?: boolean;
    browseType?: BrowseType;
}> = props => {
    const browseType = props.browseType ?? BrowseType.MainContent;
    const location = useLocation();
    const filterIngoing = getQueryParam(location.search, "filterIngoing") !== "false";
    const filterRejected = getQueryParam(location.search, "filterRejected") !== "false";
    const filterOriginalPath = getQueryParam(location.search, "filterOriginalPath");
    const avatars = useAvatars();

    const additionalFilters: Record<string, string> = useMemo(() => {
        const result: Record<string, string> = {};
        result["filterIngoing"] = filterIngoing.toString()
        if (filterOriginalPath) {
            result["filterOriginalPath"] = filterOriginalPath;
        }
        if (filterRejected) {
            result["filterRejected"] = filterRejected.toString();
        }
        return result;
    }, [filterIngoing]);

    const onSharesLoaded = useCallback((items: Share[]) => {
        if (items.length === 0) return;
        avatars.updateCache(items.map(it => it.specification.sharedWith));
    }, []);

    const navigateToEntry = React.useCallback((navigate: NavigateFunction, share: Share): void => {
        if (browseType === BrowseType.MainContent) {
            if (share.status.state === "APPROVED" || share.specification.sharedWith !== Client.username) {
                navigate(buildQueryString("/files", {path: share.status.shareAvailableAt}));
            } else {
                snackbarStore.addFailure("Share must be accepted to access.", false);
            }
        } else {
            // Should we handle this differently for other browseTypes?
            navigate(buildQueryString("/files", {path: share.status.shareAvailableAt}));
        }
    }, []);

    return <Box ml="12px" mt="12px">
        <ResourceBrowse
            api={SharesApi}
            disableSearch // HACK(Jonas): THIS IS TEMPORARY, UNTIL SEARCH WORKS FOR ALL SHARES
            onSelect={props.onSelect}
            browseType={BrowseType.Embedded}
            isSearch={props.isSearch}
            onResourcesLoaded={onSharesLoaded}
            additionalFilters={additionalFilters}
            navigateToChildren={navigateToEntry}
            headerSize={55}
            emptyPage={
                <Heading.h3 textAlign={"center"}>
                    No shares match your search/filter criteria.
                    <br />
                    <small>You can create a new share by clicking 'Share' on one of your directories.</small>
                </Heading.h3>
            }
        />
    </Box>;
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

export function IngoingSharesBrowse({opts}: {opts?: {additionalFilters?: Record<string, string>}}): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<Share> | null>(null);
    const navigate = useNavigate();
    const dispatch = useDispatch();

    const avatars = useAvatars();

    useTitle("Shared with me");

    const features: ResourceBrowseFeatures = FEATURES;

    const dateRanges = dateRangeFilters("Created after");

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<Share>(mount, "Shared with me").init(browserRef, features, "", browser => {
                // Removed stored filters that shouldn't persist.
                dateRanges.keys.forEach(it => clearFilterStorageValue(browser.resourceName, it));

                const flags = defaultRetrieveFlags;

                browser.on("open", (oldPath, newPath, resource) => {
                    const share = resource as Share | undefined;
                    if (share) {
                        navigate(AppRoutes.resource.properties("shares", share.id));
                        return;
                    }

                    callAPI(
                        SharesApi.browse({
                            itemsPerPage: 250,
                            ...browser.browseFilters,
                        })
                    ).then(result => {
                        browser.registerPage(result, newPath, true);

                        browser.renderRows();
                    });

                });

                browser.on("wantToFetchNextPage", async (path) => {
                    const result = await callAPI(
                        SharesApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters
                        })
                    );
                    browser.registerPage(result, path, false);
                });


                // GET https://dev.cloud.sdu.dk/api/shares/browse?itemsPerPage=100&includeUpdates=true&includeOthers=true&filterCreatedAfter=1693951200000&filterCreatedBy=a&sortDirection=ascending&filterIngoing=true&filterRejected=true
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
                    console.log(share.specification.sharedWith);
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

                    const sharedByMe = share.specification.sharedWith !== Client.username;

                    // Row title
                    row.title.append(
                        ResourceBrowser.defaultTitleRenderer(
                            share.owner.createdBy !== Client.username ?
                                fileName(share.specification.sourceFilePath) :
                                share.specification.sharedWith ?? share.id,
                            dims
                        )
                    );

                    // Row stat1


                    const {state} = share.status;
                    const [stateIcon, setStateIcon] = ResourceBrowser.defaultIconRenderer();
                    stateIcon.style.marginTop = stateIcon.style.marginBottom = "auto";
                    row.stat1.appendChild(stateIcon);
                    browser.icons.renderIcon({
                        ...StateIconAndColor[state],
                        color2: "black",
                        height: 32,
                        width: 32,
                    }).then(setStateIcon);
                    const pIcon = providerIcon(share.specification.product.provider, {
                        fontSize: "28px", width: "40px", height: "40px", marginRight: "8px",
                    });
                    pIcon.style.marginTop = pIcon.style.marginBottom = "auto"
                    row.stat1.appendChild(pIcon);
                    const isEdit = share.specification.permissions.some(it => it === "EDIT");

                    // Note(Jonas): To any future reader (as opposed to past reader?) the radioTilesContainerWrapper is to ensure that
                    // the re-render doesn't happen multiple times, when re-rendering. The radioTilesContainerWrapper can be dead,
                    // so attaching doesn't do anything, instead of risking the promise resolving after a second re-render,
                    // causing multiple avatars to be shown.
                    const radioTilesContainerWrapper = document.createElement("div");
                    row.stat1.append(radioTilesContainerWrapper);
                    new ReactStaticRenderer(() =>
                        <RadioTilesContainer height={48} onClick={stopPropagation}>
                            {!isEdit ? <RadioTile
                                disabled={share.owner.createdBy !== Client.username}
                                label={"Read"}
                                onChange={noopCall}
                                icon={"search"}
                                name={"READ"}
                                checked={!isEdit && sharedByMe}
                                height={40}
                                fontSize={"0.5em"}
                            /> : null}
                            {isEdit ? <RadioTile
                                disabled
                                label={"Edit"}
                                onChange={noopCall}
                                icon={"edit"}
                                name={"EDIT"}
                                checked={isEdit && sharedByMe}
                                height={40}
                                fontSize={"0.5em"}
                            /> : null}
                        </RadioTilesContainer>
                    ).promise.then(it => radioTilesContainerWrapper.append(it.clone()));

                    // Row stat2
                    row.stat2.innerText = dateToString(share.createdAt ?? timestampUnixMs());

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

                    new ReactStaticRenderer(() =>
                        <Tooltip
                            trigger={<Avatar style={{height: "40px", width: "40px"}} avatarStyle="Circle" {...avatar} />}
                        >
                            Shared by {share.owner.createdBy}
                        </Tooltip>
                    ).promise.then(it => {
                        avatarWrapper.appendChild(it.clone());
                    });
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

                browser.on("nameOfEntry", s => s.id);
                browser.on("pathToEntry", s => s.id);
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

                async function updatePermissions(share: Share, isEditing: boolean) {
                    await callAPI(SharesApi.updatePermissions(bulkRequestOf(
                        {
                            id: share.id,
                            permissions: isEditing ? ["READ", "EDIT"] : ["READ"]
                        }
                    )));
                    // TODO(Jonas): Reload
                };
            });
        }
    }, []);

    useRefreshFunction(() => {
        browserRef.current?.refresh();
    });

    const main = <div ref={mountRef} />;
    return <MainContainer main={main} />;
}


const Router: React.FunctionComponent = () => {
    return <ResourceRouter api={SharesApi} Browser={ShareBrowse} />;
};

const SelectBoxClass = injectStyleSimple("select-box", `
    border: 2px solid var(--midGray);
    border-radius: 5px;
    padding: 10px;
`);

export const StateIconAndColor: Record<ShareState, {name: IconName, color: ThemeColor}> = {
    "APPROVED": {color: "green", name: "check"},
    "PENDING": {color: "blue", name: "questionSolid"},
    "REJECTED": {color: "red", name: "close"},
}

export default Router;
