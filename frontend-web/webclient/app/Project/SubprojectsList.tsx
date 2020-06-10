import {changeRoleInProject, ProjectMember, ProjectRole, transferPiRole, projectStringToRole, Subproject} from "Project/index";
import {useAsyncCommand} from "Authentication/DataHook";
import {useAvatars} from "AvataaarLib/hook";
import * as React from "react";
import {useEffect} from "react";
import {defaultAvatar} from "UserSettings/Avataaar";
import {Flex, Icon, Text, Box, Button, RadioTile, RadioTilesContainer} from "ui-components";
import {IconName} from "ui-components/Icon";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "UtilityFunctions";
import {isAdminOrPI} from "Utilities/ProjectUtilities";
import {addStandardDialog} from "UtilityComponents";
import {UserAvatar} from "AvataaarLib/UserAvatar";
import {RemoveButton} from "Files/FileInputSelector";

export function SubprojectsList(props: Readonly<{
    subprojects: Subproject[];
    onRemoveSubproject(subproject: Subproject): void;
    allowRoleManagement: boolean;
    reload?: () => void;
    isOutgoingInvites?: boolean;
    projectId: string;
}>): JSX.Element {
    const [, runCommand] = useAsyncCommand();
    const avatars = useAvatars();

    useEffect(() => {
        const subprojectNames = props.subprojects.map(it => it.name);
        avatars.updateCache(subprojectNames);
    }, [props.subprojects]);

    const options: {text: string; icon: IconName; value: ProjectRole}[] = [
        {text: "User", icon: "user", value: ProjectRole.USER},
        {text: "Admin", icon: "userAdmin", value: ProjectRole.ADMIN}
    ];

    return (<>
        <Box mt={20}>
            {props.subprojects.map(subproject =>
                <>
                    <Flex alignItems="center" mb="16px">
                        {!props.isOutgoingInvites ? <Text bold>{subproject.name}</Text> :
                            <div>
                                <Text bold>{subproject.name}</Text>
                                Invited to join
                            </div>
                        }

                        <Box flexGrow={1} />

                        <Flex alignItems={"center"}>
                            <RemoveButton width="35px" height="35px" onClick={() => props.onRemoveSubproject(subproject)} />
                        </Flex>
                    </Flex>
                </>
            )}
        </Box>
    </>);
}
