import * as React from "react";
import {ShakingBox} from "@/UtilityComponents";
import {Box, Button, Flex, RadioTile, RadioTilesContainer, Text, Truncate} from "@/ui-components/index";
import {useCloudCommand} from "@/Authentication/DataHook";
import {bulkRequestOf} from "@/UtilityFunctions";
import {useCallback, useEffect, useState} from "react";
import {TextSpan} from "@/ui-components/Text";
import {Link} from "react-router-dom";
import {
    AclEntity,
    Permission,
    Resource,
    ResourceAclEntry,
    ResourceApi,
} from "@/UCloud/ResourceApi";
import {useProjectId} from "@/Project/Api";
import {useProject} from "@/Project/cache";
import Spinner from "@/LoadingIcon/LoadingIcon";
import {classConcat} from "@/Unstyled";

interface ResourcePermissionEditorProps<T extends Resource> {
    reload: () => void;
    entity: T;
    api: ResourceApi<T, never>;
    showMissingPermissionHelp?: boolean;
    noPermissionsWarning?: string;
}

export function ResourcePermissionEditor<T extends Resource>(
    props: ResourcePermissionEditorProps<T>
): React.ReactNode {
    const {entity, reload, api} = props;
    const projectId = useProjectId();
    const project = useProject();

    const [commandLoading, invokeCommand] = useCloudCommand();

    const [acl, setAcl] = useState<ResourceAclEntry[]>(entity.permissions?.others ?? []);

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

    if (project.loading) {
        return <Spinner />;
    }

    const groups = project.fetch().status.groups ?? [];

    return <>
        {groups.length !== 0 ? null : (
            <Flex width={"100%"} alignItems={"center"} justifyContent={"center"}
                flexDirection={"column"}>
                <Box className={classConcat(ShakingBox,"shaking")} mb={"10px"}>
                    No groups exist for this project.{" "}
                    <TextSpan bold>{warning}</TextSpan>
                </Box>

                <Link to={"/project/members"} target={"_blank"}><Button fullWidth>Create group</Button></Link>
            </Flex>
        )}
        <>
            {anyGroupHasPermission || !(props.showMissingPermissionHelp ?? true) ? null :
                <Box className={classConcat(ShakingBox, "shaking")} mb={16}>
                    <Text bold>{warning}</Text>
                    <Text>
                        You must assign permissions to one or more group, if your collaborators need to use this
                        {" "}{api.title.toLowerCase()}.
                    </Text>
                </Box>
            }
            {groups.map(summary => {
                const g = summary.id;
                const permissions = acl.find(it =>
                    "projectId" in it.entity &&
                    it.entity.group === g &&
                    it.entity.projectId === projectId
                )?.permissions ?? [];

                const title = summary.specification.title;

                return (
                    <Flex key={g} alignItems={"center"} mb={16} data-component={"permission-row"}
                        data-group={title} data-group-id={summary.id}>
                        <Truncate width={"300px"} mr={16} title={title}>
                            {title}
                        </Truncate>

                        <RadioTilesContainer data-component={"permission-container"}>
                            <RadioTile
                                label={"None"}
                                onChange={() => updateAcl(g, null)}
                                icon={"close"}
                                name={summary.id}
                                checked={permissions.length === 0}
                                height={40}
                                fontSize={"0.5em"}
                            />
                            <RadioTile
                                label={"Read"}
                                onChange={() => updateAcl(g, "READ")}
                                icon={"search"}
                                name={summary.id}
                                checked={permissions.indexOf("READ") !== -1 && permissions.length === 1}
                                height={40}
                                fontSize={"0.5em"}
                            />
                            <RadioTile
                                label={"Write"}
                                onChange={() => updateAcl(g, "EDIT")}
                                icon={"edit"}
                                name={summary.id}
                                checked={permissions.indexOf("EDIT") !== -1}
                                height={40}
                                fontSize={"0.5em"}
                            />

                        </RadioTilesContainer>
                    </Flex>
                );
            })}
        </>
    </>;
}
