import * as UCloud from "@/UCloud";
import * as React from "react";
import {ShakingBox} from "@/UtilityComponents";
import {Button, Flex, RadioTile, RadioTilesContainer, Text, Truncate} from "@/ui-components/index";
import {groupSummaryRequest, useProjectId} from "@/Project";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {GroupWithSummary} from "@/Project/GroupList";
import {bulkRequestOf, emptyPage} from "@/DefaultObjects";
import {useCallback, useEffect, useState} from "react";
import * as Pagination from "@/Pagination";
import {TextSpan} from "@/ui-components/Text";
import {Link} from "react-router-dom";
import {
    AclEntity,
    Permission,
    Resource,
    ResourceAclEntry,
    ResourceApi,
} from "@/UCloud/ResourceApi";

interface ResourcePermissionEditorProps<T extends Resource> {
    reload: () => void;
    entity: T;
    api: ResourceApi<T, never>;
    showMissingPermissionHelp?: boolean;
    noPermissionsWarning?: string;
}

export function ResourcePermissionEditor<T extends Resource>(
    props: ResourcePermissionEditorProps<T>
): React.ReactElement | null {
    const {entity, reload, api} = props;
    const projectId = useProjectId();
    const [projectGroups, fetchProjectGroups, groupParams] =
        useCloudAPI<Page<GroupWithSummary>>({noop: true}, emptyPage);

    const [commandLoading, invokeCommand] = useCloudCommand();

    const [acl, setAcl] = useState<ResourceAclEntry[]>(entity.permissions?.others ?? []);

    useEffect(() => {
        fetchProjectGroups(UCloud.project.group.listGroupsWithSummary({itemsPerPage: 50, page: 0}));
    }, [projectId]);

    useEffect(() => {
        setAcl(entity.permissions?.others ?? []);
    }, [entity]);

    const updateAcl = useCallback(async (group: string, permission: Permission | null) => {
        if (!projectId) return;
        if (commandLoading) return;

        const thisEntity: AclEntity = {type: "project_group", projectId: projectId, group};

        if (permission === null) {
            setAcl(prev => prev.filter(it => !(it.entity.type === "project_group" && it.entity.group === group)));

            await invokeCommand(
                api.updateAcl(bulkRequestOf(
                    {
                        id: entity.id,
                        added: [],
                        deleted: [thisEntity]
                    }
                ))
            );

            reload();
        } else {
            const fixedPermissions: Permission[] = permission === "EDIT" ? ["READ", "EDIT"] : ["READ"];
            const newEntry: ResourceAclEntry = {
                entity: {type: "project_group", projectId, group},
                permissions: fixedPermissions
            };

            setAcl(prev => {
                const copy = prev.filter(it => !(it.entity.type === "project_group" && it.entity.group === group));
                copy.push(newEntry);
                return copy;
            });

            await invokeCommand(
                api.updateAcl(bulkRequestOf(
                    {
                        id: entity.id,
                        added: [newEntry],
                        deleted: [thisEntity]
                    }
                ))
            );

            reload();
        }
    }, [acl, projectId, commandLoading, api, entity]);

    const anyGroupHasPermission = acl.some(it => it.permissions.length !== 0);
    const warning = props.noPermissionsWarning ?? `This ${api.title.toLowerCase()} can only be used by project admins!`;

    return <Pagination.List
        loading={projectGroups.loading}
        page={projectGroups.data}
        onPageChanged={(page) => fetchProjectGroups(groupSummaryRequest({
            ...groupParams.parameters,
            page
        }))}
        customEmptyPage={(
            <Flex width={"100%"} alignItems={"center"} justifyContent={"center"}
                  flexDirection={"column"}>
                <ShakingBox shaking mb={"10px"}>
                    No groups exist for this project.{" "}
                    <TextSpan bold>{warning}</TextSpan>
                </ShakingBox>

                <Link to={"/project/members"} target={"_blank"}><Button fullWidth>Create group</Button></Link>
            </Flex>
        )}
        pageRenderer={() => (
            <>
                {anyGroupHasPermission || !(props.showMissingPermissionHelp ?? true) ? null :
                    <ShakingBox shaking mb={16}>
                        <Text bold>{warning}</Text>
                        <Text>
                            You must assign permissions to one or more group, if your collaborators need to use this
                            {" "}{api.title.toLowerCase()}.
                        </Text>
                    </ShakingBox>
                }
                {projectGroups.data.items.map(summary => {
                    const g = summary.groupId;
                    const permissions = acl.find(it =>
                        "projectId" in it.entity &&
                        it.entity.group === g &&
                        it.entity.projectId === projectId
                    )?.permissions ?? [];

                    return (
                        <Flex key={g} alignItems={"center"} mb={16}>
                            <Truncate width={"300px"} mr={16} title={summary.groupTitle}>
                                {summary.groupTitle}
                            </Truncate>

                            <RadioTilesContainer>
                                <RadioTile
                                    label={"None"}
                                    onChange={() => updateAcl(g, null)}
                                    icon={"close"}
                                    name={summary.groupId}
                                    checked={permissions.length === 0}
                                    height={40}
                                    fontSize={"0.5em"}
                                />
                                <RadioTile
                                    label={"Read"}
                                    onChange={() => updateAcl(g, "READ")}
                                    icon={"search"}
                                    name={summary.groupId}
                                    checked={permissions.indexOf("READ") !== -1 && permissions.length === 1}
                                    height={40}
                                    fontSize={"0.5em"}
                                />
                                <RadioTile
                                    label={"Write"}
                                    onChange={() => updateAcl(g, "EDIT")}
                                    icon={"edit"}
                                    name={summary.groupId}
                                    checked={permissions.indexOf("EDIT") !== -1}
                                    height={40}
                                    fontSize={"0.5em"}
                                />

                            </RadioTilesContainer>
                        </Flex>
                    );
                })}
            </>
        )}
    />;
}
