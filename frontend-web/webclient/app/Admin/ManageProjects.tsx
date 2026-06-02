import * as React from "react";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {Client} from "@/Authentication/HttpClientInstance";
import {Button, Divider, Flex, Icon, Input, Link, MainContainer} from "@/ui-components";
import {useCallback, useEffect, useState} from "react";
import {callAPI, callAPIWithErrorHandler} from "@/Authentication/DataHook";
import * as Grants from "@/Grants";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {Tree, TreeNode} from "@/ui-components/Tree";
import {yourAllocationsStyle} from "@/Accounting/Allocations/CommonSections";
import {ProjectTitleForNewCore} from "@/Project/InfoCache";
import {sendFailureNotification, sendSuccessNotification} from "@/Notifications";
import {UserCriteriaEditorReadOnly} from "@/Project/ProjectSettings";

function ManageProjects(): React.ReactNode {
    usePage("Manage projects", SidebarTabId.ADMIN);
    if (!Client.userIsAdmin) return null;

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

    const [settings, setSettings] = useState<Grants.ProjectToSetting[]>([{
        projectId: "",
        settings: defaultSetting
    }]);

    const projectIdRef = React.useRef<HTMLInputElement>(null);

    async function sendSettingUpdate(projectToSetting: Grants.ProjectToSetting) {
        const success = await callAPIWithErrorHandler({
            ...Grants.updateRequestSettingsAdmin(projectToSetting)
        }) !== null;

        if (success) {
            sendSuccessNotification("Project settings saved!");
            setSettings([...settings]);
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

        const existingSetting = await callAPI<Grants.RequestSettings>(
            {
                ...Grants.retrieveRequestSettingsAdmin({id: projectId})
            }
        );

        const projectSettings = {
            projectId: projectId,
            settings: {
                ...existingSetting,
                enabled: true,
            }
        };

        settings.push(projectSettings);
        sendSettingUpdate(projectSettings);

        if (projectIdRef.current) {
            projectIdRef.current.value = "";
        }

    }, [settings]);

    useEffect(() => {
        (async () => {
            try {
                const res = await callAPI<Grants.ProjectToSetting[]>(
                    {
                        ...Grants.browseEnabledProjects(),
                    }
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
            <form action="#" onSubmit={onEnableProject} style={{display: "flex", gap: "8px", width: "100%"}}>
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
                                {settings.length !== 0 ? null : <div style={{marginLeft: "20px", marginTop: "10px"}}>
                                    No projects are enabled at the moment.
                                </div>}
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
                                                <label style={{marginBottom: "16px"}}>Allow applications from</label>
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
