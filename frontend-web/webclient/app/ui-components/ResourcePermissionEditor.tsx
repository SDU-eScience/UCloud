import * as React from "react";
import {ShakingBox} from "@/UtilityComponents";
import {Button, Flex, RadioTile, RadioTilesContainer, Text, Truncate} from "@/ui-components/index";
import {useCloudCommand} from "@/Authentication/DataHook";
import {bulkRequestOf} from "@/DefaultObjects";
import {useCallback, useEffect, useState} from "react";
import {TextSpan} from "@/ui-components/Text";
import {Link} from "react-router-dom";
import {BulkRequest, provider} from "@/UCloud";
import ResourceAclEntry = provider.ResourceAclEntry;
import ResourceDoc = provider.ResourceDoc;
import {IconName} from "@/ui-components/Icon";
import {useProjectId} from "@/Project/Api";
import {useProject} from "@/Project/cache";
import Spinner from "@/LoadingIcon/LoadingIcon";


interface ResourcePermissionEditorProps<T extends ResourceDoc> {
    entityName: string;
    options: {icon: IconName; name: string, title?: string}[];
    reload: () => void;
    entity: T;
    updateAclEndpoint: (request: BulkRequest<{id: string; acl: unknown[]}>) => APICallParameters;
    showMissingPermissionHelp?: boolean;
}

function ResourcePermissionEditor<T extends ResourceDoc>(
    props: ResourcePermissionEditorProps<T>
): React.ReactElement | null {
    const {entityName, entity, options, reload, updateAclEndpoint} = props;
    const projectId = useProjectId();

    const [commandLoading, invokeCommand] = useCloudCommand();

    const project = useProject();

    const [acl, setAcl] = useState<ResourceAclEntry[]>(entity.acl ?? []);

    useEffect(() => {
        setAcl(entity.acl ?? []);
    }, [entity]);

    const updateAcl = useCallback(async (group: string, permissions: string[]) => {
        if (!projectId) return;
        if (commandLoading) return;

        const newAcl = acl
            .filter(it => !(
                "projectId" in it.entity &&
                it.entity.projectId === projectId && it.entity.group === group
            ));
        newAcl.push({entity: {projectId, group, type: "project_group"}, permissions});

        setAcl(newAcl);

        await invokeCommand(updateAclEndpoint(bulkRequestOf({acl: newAcl, id: entity.id})))
        reload();
    }, [acl, projectId, commandLoading]);

    const anyGroupHasPermission = acl.some(it => it.permissions.length !== 0);

    if (project.loading) {
        return <Spinner />
    }

    const groups = project.fetch().status.groups ?? [];

    return (<>
        {groups.length !== 0 ? null :

            <Flex width={"100%"} height={"100%"} alignItems={"center"} justifyContent={"center"}
                flexDirection={"column"}>
                <ShakingBox shaking mb={"10px"}>
                    No groups exist for this project.{" "}
                    <TextSpan bold>As a result, this {entityName.toLowerCase()} can only be used by project admins!</TextSpan>
                </ShakingBox>

                <Link to={"/project/members"} target={"_blank"}><Button fullWidth>Create group</Button></Link>
            </Flex>}
        <>
            {anyGroupHasPermission || !(props.showMissingPermissionHelp ?? true) ? null :
                <ShakingBox shaking mb={16}>
                    <Text bold>This {entityName.toLowerCase()} can only be used by project admins</Text>
                    <Text>
                        You must assign permissions to one or more group, if your collaborators need to use this
                        {" "}{entityName.toLowerCase()}.
                    </Text>
                </ShakingBox>
            }
            {groups.map(group => {
                const g = group.id;
                const permissions = acl.find(it =>
                    "projectId" in it.entity &&
                    it.entity.group === g &&
                    it.entity.projectId === projectId
                )?.permissions ?? [];

                return (
                    <Flex key={g} alignItems={"center"} mb={16}>
                        <Truncate width={"300px"} mr={16} title={group.specification.title}>
                            {group.specification.title}
                        </Truncate>

                        <RadioTilesContainer>
                            <RadioTile
                                label={"None"}
                                onChange={() => updateAcl(g, [])}
                                icon={"close"}
                                name={group.id}
                                checked={permissions.length === 0}
                                height={40}
                                fontSize={"0.5em"}
                            />
                            {options.map(opt =>
                                <RadioTile
                                    key={opt.name}
                                    label={opt.title ?? opt.name}
                                    onChange={() => updateAcl(g, [opt.name])}
                                    icon={opt.icon}
                                    name={group.id}
                                    checked={permissions.indexOf(opt.name) !== -1 && permissions.length === 1}
                                    height={40}
                                    fontSize={"0.5em"}
                                />
                            )}

                        </RadioTilesContainer>
                    </Flex>
                );
            })}
        </>
    </>)
}
