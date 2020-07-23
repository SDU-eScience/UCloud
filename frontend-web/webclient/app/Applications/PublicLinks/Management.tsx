import * as React from "react";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {createPublicLink, deletePublicLink, listPublicLinks, PublicLink} from "Applications/PublicLinks/index";
import {emptyPage, KeyCode} from "DefaultObjects";
import * as Pagination from "Pagination";
import {Box, Button, Flex, Icon, Input, List} from "ui-components";
import {ListRow, ListRowStat} from "ui-components/List";
import ClickableDropdown from "ui-components/ClickableDropdown";
import * as UF from "UtilityFunctions";
import {useRef, useState} from "react";
import {addStandardDialog} from "UtilityComponents";
import {TextSpan} from "ui-components/Text";
import {shortUUID} from "UtilityFunctions";
import styled from "styled-components";

export interface PublicLinkManagementProps {
    onSelect?: (link: PublicLink) => void;
}

export const Wrapper = styled.div`
    width: 800px;
`;

export const PublicLinkManagement: React.FunctionComponent<PublicLinkManagementProps> = ({onSelect}) => {
    const [links, fetchLinks, linksParams] = useCloudAPI<Page<PublicLink>>(
        listPublicLinks({itemsPerPage: 10, page: 0}),
        emptyPage
    );

    const title = useRef<HTMLInputElement>(null);
    const [creatingItem, setCreatingItem] = useState<boolean>(false);
    const [, runCommand] = useAsyncCommand();

    const reload = (): void => {
        fetchLinks(listPublicLinks({...linksParams.parameters}));
    };

    const create = async (e): Promise<void> => {
        e.preventDefault();
        await runCommand(createPublicLink({url: title.current!.value}));
        reload();
        setCreatingItem(false);
    };

    return (
        <Wrapper>
            {creatingItem ?
                <ListRow
                    left={(
                        <form onSubmit={create}>
                            <Flex alignItems={"center"}>
                                <TextSpan color={"gray"}>https://app-</TextSpan>
                                <Input
                                    onBlur={create}
                                    pt="0px"
                                    pb="0px"
                                    pr="0px"
                                    pl="0px"
                                    noBorder
                                    fontSize={20}
                                    maxLength={1024}
                                    onKeyDown={e => {
                                        e.stopPropagation();
                                        if (e.keyCode === KeyCode.ESC) {
                                            setCreatingItem(false);
                                        }
                                    }}
                                    borderRadius="0px"
                                    type="text"
                                    width="calc(100 - 220px)"
                                    autoFocus
                                    ref={title}
                                />
                                <TextSpan color={"gray"}>.cloud.sdu.dk</TextSpan>
                            </Flex>
                        </form>
                    )}
                    right={<div/>}
                    leftSub={<ListRowStat icon={"appStore"}>Not in use</ListRowStat>}
                /> : null}

            <List>
                <Pagination.List
                    loading={links.loading}
                    page={links.data}
                    onPageChanged={(page) => fetchLinks(listPublicLinks({itemsPerPage: 10, page}))}
                    customEmptyPage={<>No public links created. Click below to create one.</>}
                    pageRenderer={() => {
                        return (
                            <>
                                {links.data.items.map(link => (
                                    <ListRow
                                        key={link.url}
                                        left={
                                            <>
                                                <TextSpan color={"gray"}>https://app-</TextSpan>
                                                {link.url}
                                                <TextSpan color={"gray"}>.cloud.sdu.dk</TextSpan>
                                            </>
                                        }
                                        leftSub={(
                                            <>
                                                <ListRowStat icon={"appStore"}>
                                                    {!link.inUseByUIFriendly ?
                                                        "Not in use" :
                                                        <>Used by {shortUUID(link.inUseByUIFriendly)}</>
                                                    }
                                                </ListRowStat>
                                            </>
                                        )}
                                        right={(
                                            <Flex alignItems={"center"}>
                                                {!onSelect || link.inUseByUIFriendly ?
                                                    null :
                                                    <Button onClick={e => {
                                                        e.preventDefault();
                                                        onSelect(link);
                                                    }}>Use</Button>
                                                }
                                                <ClickableDropdown
                                                    trigger={(
                                                        <Icon
                                                            onClick={UF.preventDefault}
                                                            ml="10px"
                                                            mr="10px"
                                                            name="ellipsis"
                                                            size="1em"
                                                            rotation={90}
                                                        />
                                                    )}
                                                >
                                                    <Box width={"100%"} onClick={async () => {
                                                        addStandardDialog({
                                                            title: "Are you sure?",
                                                            message: "Delete this public link? " + link.url,
                                                            confirmText: "Delete link",
                                                            addToFront: true,
                                                            onConfirm: async () => {
                                                                await runCommand(deletePublicLink({url: link.url}));
                                                                reload();
                                                            }
                                                        });
                                                    }}>
                                                        <Icon
                                                            size={16}
                                                            mr="0.5em"
                                                            color={"red"}
                                                            color2={"red"}
                                                            name={"trash"}
                                                        />

                                                        Delete
                                                    </Box>
                                                </ClickableDropdown>
                                            </Flex>
                                        )}
                                    />
                                ))}
                            </>
                        );
                    }}
                />
            </List>

            <Button
                mt={"16px"}
                type={"button"}
                width={"100%"}
                onClick={() => setCreatingItem(true)}
            >
                New link
            </Button>
        </Wrapper>
    );
};
