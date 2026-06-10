import * as React from "react";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {Client} from "@/Authentication/HttpClientInstance";
import {Box, Button, Divider, Flex, Icon, Input, Label, Link, MainContainer} from "@/ui-components";
import {useCallback, useEffect, useState} from "react";
import {callAPI, callAPIWithErrorHandler} from "@/Authentication/DataHook";
import * as Grants from "@/Grants";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {Tree, TreeNode} from "@/ui-components/Tree";
import {yourAllocationsStyle} from "@/Accounting/Allocations/CommonSections";
import {ProjectTitleForNewCore} from "@/Project/InfoCache";
import {sendFailureNotification, sendSuccessNotification} from "@/Notifications";
import {UserCriteriaEditorReadOnly} from "@/Project/ProjectSettings";

const defaultSetting: Grants.RequestSettings = {
    enabled: false,
    description: "No description",
    allowRequestsFrom: [],
    excludeRequestsFrom: [],
    templates: {
        type: "plain_text",
        personalProject: "No template",
        newProject: "No template",
        existingProject: "No template",
    }
}

function ManageProjects(): React.ReactNode {
    usePage("Manage projects", SidebarTabId.ADMIN);
    if (!Client.userIsAdmin) return null;

    const [settings, setSettings] = useState<Grants.ProjectToSetting[]>([{
        projectId: "",
        settings: defaultSetting
    }]);

    const projectIdRef = React.useRef<HTMLInputElement>(null);

    async function sendSettingUpdate(projectToSetting: Grants.ProjectToSetting) {
        const success = await callAPIWithErrorHandler(
            Grants.updateRequestSettingsAdmin(projectToSetting)
        ) !== null;

        if (success) {
            sendSuccessNotification("Project settings saved!");
            setSettings([...settings]);
            if (projectIdRef.current) {
                projectIdRef.current.value = "";
            }
        } else {
            sendFailureNotification("Could not update project settings");
        }
    }

    function onDisableProject(projectId: string) {
        const idx = settings.findIndex(value => value.projectId == projectId)
        if (idx > -1) {
            const oldSetting = settings[idx].settings;
            settings.splice(idx, 1);

            const deleteUpdate: Grants.ProjectToSetting = {
                projectId: projectId,
                settings: {
                    ...oldSetting,
                    enabled: false,
                    allowRequestsFrom: [],
                    excludeRequestsFrom: []
                }
            }
            sendSettingUpdate(deleteUpdate);
        }
    };

    const onEnableProject = useCallback(async (e) => {
        e.preventDefault();

        const projectId = projectIdRef.current?.value;

        if (!projectId || !projectId.trim()) {
            sendFailureNotification("Project ID is empty.")
            return;
        }

        if (settings.find(it => it.projectId == projectId)) {
            sendFailureNotification("Project is already enabled.")
            return
        }

        let existingSetting = defaultSetting
        try {
            existingSetting = await callAPI<Grants.RequestSettings>(
                Grants.retrieveRequestSettingsAdmin({id: projectId})
            );
        } catch (e: any) {
            sendFailureNotification("Cannot enabled project.")
            return
        }

        const projectSettings = {
            projectId: projectId,
            settings: {
                ...existingSetting,
                enabled: true,
            }
        };

        settings.push(projectSettings);
        sendSettingUpdate(projectSettings);

    }, [settings]);

    useEffect(() => {
        (async () => {
            try {
                const res = await callAPI<Grants.ProjectToSetting[]>(
                    Grants.browseEnabledProjects(),
                );
                setSettings(res)
            } catch (e) {
                // Ignoring failure
            }
        })();
    }, []);

    return <MainContainer
        main={<>
            <h3 className="title">Project Management</h3>
            <p>Admins can manage projects on this page.</p>
            <Divider />
            <h3>Grant enabled projects</h3>
            <p>Enable grant reception for existing project</p>
            <form onSubmit={onEnableProject} style={{display: "flex", gap: "8px", width: "100%"}}>
                <Input
                    inputRef={projectIdRef}
                    type="text"
                    placeholder="Project ID"
                    style={{flexGrow: 1}}
                />
                <Button type={"submit"}><Icon name={"heroPlus"} /></Button>
            </form>
            <br />
            <p>Projects enabled to receive grants</p>
            <>
                <div className={yourAllocationsStyle}>
                    <div className="your-allocations-container">
                        {settings === undefined ? <>
                            <HexSpin size={64} />
                        </> : <>
                            <div>
                                {settings.length !== 0 ? null : <Box ml="20px" mt="10px">
                                    No projects are enabled at the moment.
                                </Box>}
                                <Tree>
                                    {settings.map((projectToSetting) => {
                                        return <TreeNode
                                            key={projectToSetting.projectId}
                                            left={<ProjectTitleForNewCore id={projectToSetting.projectId} />}
                                            right={<Flex flexDirection={"row"} gap={"8px"}>
                                                <Icon color={"errorMain"} name={"trash"} cursor={"pointer"} onClick={() => onDisableProject(projectToSetting.projectId)} />
                                            </Flex>}
                                            indent={1}
                                        >
                                            <div>
                                                <Label mb="16px">Allow applications from</Label>
                                                <UserCriteriaEditorReadOnly
                                                    criteria={projectToSetting.settings.allowRequestsFrom}
                                                    projectId={projectToSetting.projectId}
                                                    isExclusion={false}
                                                />
                                            </div>

                                            <div>
                                                <label>Exclude applications from</label>
                                                <UserCriteriaEditorReadOnly
                                                    criteria={projectToSetting.settings.excludeRequestsFrom}
                                                    projectId={projectToSetting.projectId}
                                                    isExclusion={true}
                                                />
                                            </div>
                                        </TreeNode>
                                    })}
                                </Tree>
                            </div>
                        </>}
                    </div>
                </div>
            </>
        </>
        }>

    </MainContainer>
}

export default ManageProjects;
