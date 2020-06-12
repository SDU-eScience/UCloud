import {ProjectRole, UserInProject, deleteProject, listProjects, listSubprojects, ListSubprojectsRequest} from "Project/index";
import {useAsyncCommand, APICallParameters} from "Authentication/DataHook";
import {useAvatars} from "AvataaarLib/hook";
import * as Pagination from "Pagination";
import * as React from "react";
import {useEffect} from "react";
import {Flex, Text, Box, Button, theme} from "ui-components";
import {IconName} from "ui-components/Icon";
import {RemoveButton} from "Files/FileInputSelector";
import {Page} from "Types";


export function SubprojectsList(props: Readonly<{
    subprojects: Page<UserInProject>;
    searchQuery: string;
    loading: boolean;
    onRemoveSubproject(subprojectId: string, subprojectTitle): void;
    onAssignCredits(subproject: UserInProject): void;
    allowRoleManagement: boolean;
    reload?: () => void;
    fetchParams(params: APICallParameters<ListSubprojectsRequest>);
    isOutgoingInvites?: boolean;
    projectId: string;
}>): JSX.Element {

    const avatars = useAvatars();

    useEffect(() => {
        const subprojectNames = props.subprojects.items.map(it => it.title);
        avatars.updateCache(subprojectNames);
    }, [props.subprojects]);

    return (<>
        <Box mt={20}>
            <>
                <Pagination.List
                    page={props.subprojects}
                    pageRenderer={pageRenderer}
                    loading={props.loading}
                    onPageChanged={newPage => {
                        props.fetchParams(
                            listSubprojects({
                                page: newPage,
                                itemsPerPage: 50,
                            })
                        );
                    }}
                    customEmptyPage={<div />}
                />
            </>
        </Box>
    </>);

    function pageRenderer(page: Page<UserInProject>): JSX.Element[] {
        const filteredItems = (props.searchQuery !== "" ?
            page.items.filter(it =>
                it.title.toLowerCase().search(props.searchQuery.toLowerCase().replace(/\W|_|\*/g, "")) !== -1)
            :
                page.items
        );

        return filteredItems.map(subproject => {
            return (
                <Flex key={subproject.projectId} alignItems="center" mb="16px">
                    <Text bold>{subproject.title}</Text>
                    <Text color={theme.colors.gray}>{subproject.projectId}</Text>

                    <Box flexGrow={1} />

                    <Flex alignItems={"center"}>
                        <Button width="150px" height="35px" onClick={() => props.onAssignCredits(subproject)}>Assign credits</Button>
                        <RemoveButton width="35px" height="35px" onClick={() => props.onRemoveSubproject(subproject.projectId, subproject.title)} />
                    </Flex>
                </Flex>
            );
        });
    }
}


