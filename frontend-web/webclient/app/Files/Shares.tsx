import * as React from "react";
import {ResourceBrowse} from "@/Resource/Browse";
import {ResourceRouter} from "@/Resource/Router";
import SharesApi, {Share, ShareLink, shareLinksApi} from "@/UCloud/SharesApi";
import { NavigateFunction, useLocation} from "react-router";
import {buildQueryString, getQueryParam} from "@/Utilities/URIUtilities";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import * as Heading from "@/ui-components/Heading";
import {useAvatars} from "@/AvataaarLib/hook";
import {BrowseType} from "@/Resource/BrowseType";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Client} from "@/Authentication/HttpClientInstance";
import {LinkInfo, SidebarLinkColumn} from "@/ui-components/SidebarLink";
import {Box, Button, Flex, Icon, Input, Text, Tooltip} from "@/ui-components";
import {BulkResponse, PageV2} from "@/UCloud";
import {callAPIWithErrorHandler, useCloudAPI} from "@/Authentication/DataHook";
import {UFile} from "@/UCloud/FilesApi";
import {FindById, ResourceBrowseCallbacks} from "@/UCloud/ResourceApi";
import {copyToClipboard, preventDefault, timestampUnixMs} from "@/UtilityFunctions";
import {bulkRequestOf, emptyPageV2} from "@/DefaultObjects";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import styled from "styled-components";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {dialogStore} from "@/Dialog/DialogStore";

import AppRoutes from "@/Routes";

export const sharesLinksInfo: LinkInfo[] = [
    {text: "Shared with me", to: AppRoutes.shares.sharedWithMe(), icon: "share"},
    {text: "Shared by me", to: AppRoutes.shares.sharedByMe(), icon: "shareMenu"},
]

export function SharesLinks(): JSX.Element {
    return <SidebarLinkColumn links={sharesLinksInfo} />
}

function daysLeftToTimestamp(timestamp: number): number {
    return Math.floor((timestamp - timestampUnixMs())/1000 / 3600 / 24);
}

function inviteLinkFromToken(token: string): string {
    return window.location.origin + "/app/shares/invite/" + token;
}

export const ShareModal: React.FunctionComponent<{
    selected: UFile,
    cb: ResourceBrowseCallbacks<UFile>
}> = ({selected, cb}) => {

    const [inviteLinks, fetchLinks] = useCloudAPI<PageV2<ShareLink>>({noop: true}, emptyPageV2);
    const [editingLink, setEditingLink] = useState<string|undefined>(undefined);
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
                <Text mb="20px" mt="20px">Share files with other users by sharing a link</Text>
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
                                    left="-50%"
                                    top="1"
                                    mb="35px"
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
                            <Box>
                                <Button
                                    mr="5px"
                                    height={40}
                                    onClick={() =>
                                        setEditingLink(link.token)
                                    }
                                >
                                    <Icon name="edit" size={20} />
                                </Button>

                                <ConfirmationButton
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
                                    icon="trash"
                                />
                            </Box>
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
                <SelectBox>
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
                </SelectBox>
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

    return <ResourceBrowse
        api={SharesApi}
        disableSearch // HACK(Jonas): THIS IS TEMPORARY, UNTIL SEARCH WORKS FOR ALL SHARES
        onSelect={props.onSelect}
        browseType={browseType}
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
    />;
};

const Router: React.FunctionComponent = () => {
    return <ResourceRouter api={SharesApi} Browser={ShareBrowse} />;
};

const SelectBox = styled.div`
    border: 2px solid var(--midGray);
    border-radius: 5px;
    padding: 10px;
`;

export default Router;
