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
import {auth} from "@/UCloud";
import refresh = auth.refresh;
import {sendFailureNotification, sendSuccessNotification} from "@/Notifications";
import {UserCriteriaEditor} from "@/Project/ProjectSettings";

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

    const onAllowAdd = useCallback((criteria: Grants.UserCriteria, projectId: string) => {
        const settingIndex = settings.findIndex(it => it.projectId == projectId)
        var newSetting: Grants.ProjectToSetting
        if (settingIndex == -1) {
            newSetting = {
                projectId: projectId,
                settings: {
                    ...defaultSetting,
                    enabled: true,
                    allowRequestsFrom: [...defaultSetting.allowRequestsFrom, criteria],
                }
            };
            settings.push(newSetting);
        } else {
            const previousSetting = settings[settingIndex].settings;
            newSetting = {
                projectId: projectId,
                settings: {
                    ...previousSetting,
                    allowRequestsFrom: [...previousSetting.allowRequestsFrom, criteria]
                }
            }
            settings[settingIndex] = newSetting;
        }
        setSettings(settings);
        sendSettingUpdate(newSetting);
    }, [settings]);

    const onAllowRemove = useCallback((idx: number, projectId: string) => {
        const settingIndex = settings.findIndex(it => it.projectId == projectId)
        var newSetting: Grants.ProjectToSetting
        if (settingIndex == -1) {
            newSetting = {
                projectId: projectId,
                settings: {
                    ...defaultSetting,
                    enabled: true,
                    allowRequestsFrom: [...defaultSetting.allowRequestsFrom],
                }
            };
            settings.push(newSetting);
        } else {
            const previousSetting = settings[settingIndex].settings;
            const allowRequestsFrom = [...previousSetting.allowRequestsFrom];
            allowRequestsFrom.splice(idx, 1);
            newSetting = {
                projectId: projectId,
                settings: {
                    ...previousSetting,
                    allowRequestsFrom
                }
            }
        }
        settings[settingIndex] = newSetting;
        setSettings(settings);
        sendSettingUpdate(newSetting);
    }, [settings]);


    const onExcludeAdd = useCallback((criteria: Grants.UserCriteria, projectId: string) => {
        const settingIndex = settings.findIndex(it => it.projectId == projectId)
        var newSetting: Grants.ProjectToSetting
        if (settingIndex == -1) {
            newSetting = {
                projectId: projectId,
                settings: {
                    ...defaultSetting,
                    enabled: true,
                    excludeRequestsFrom: [...defaultSetting.excludeRequestsFrom, criteria],
                }
            };
            settings.push(newSetting);
        } else {
            const previousSetting = settings[settingIndex].settings;
            newSetting = {
                projectId: projectId,
                settings: {
                    ...previousSetting,
                    excludeRequestsFrom: [...previousSetting.excludeRequestsFrom, criteria]
                }
            }
            settings[settingIndex] = newSetting;
        }
        setSettings(settings);
        sendSettingUpdate(newSetting);
    }, [settings]);

    const onExcludeRemove = useCallback((idx: number, projectId: string) => {
        const settingIndex = settings.findIndex(it => it.projectId == projectId)
        var newSetting: Grants.ProjectToSetting
        if (settingIndex == -1) {
            newSetting = {
                projectId: projectId,
                settings: {
                    ...defaultSetting,
                    enabled: true,
                    allowRequestsFrom: [...defaultSetting.excludeRequestsFrom],
                }
            };
            settings.push(newSetting);
        } else {
            const previousSetting = settings[settingIndex].settings;
            const excludeRequestsFrom = [...previousSetting.excludeRequestsFrom];
            excludeRequestsFrom.splice(idx, 1);
            newSetting = {
                projectId: projectId,
                settings: {
                    ...previousSetting,
                    excludeRequestsFrom
                }
            }
        }
        settings[settingIndex] = newSetting;
        setSettings(settings);
        sendSettingUpdate(newSetting);
    }, [settings]);

    const projectIdRef = React.useRef<HTMLInputElement>(null);

    async function sendSettingUpdate(projectToSetting: Grants.ProjectToSetting) {
        const success = await callAPIWithErrorHandler({
            ...Grants.updateRequestSettings(projectToSetting.settings), projectOverride: projectToSetting.projectId
        }) !== null;

        if (success) {
            sendSuccessNotification("Project settings saved!");
        }
    }

    const onEnableProject = useCallback(async (e) => {
        e.preventDefault();

        const projectId = projectIdRef.current?.value;

        if (!projectId || !projectId.trim()) {
            sendFailureNotification("Project ID is empty.")
            return;
        }

        const newSetting = {
            projectId: projectId,
            settings: {
                ...defaultSetting,
                enabled: true,
            }
        }

        sendSettingUpdate(newSetting)

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
                                            right={<Flex flexDirection={"row"} gap={"8px"}></Flex>}
                                            indent={1}
                                        >
                                            <div>
                                                <label style={{marginBottom: "16px"}}>Allow applications from</label>
                                                <UserCriteriaEditor
                                                    criteria={projectToSetting.settings.allowRequestsFrom}
                                                    projectId={projectToSetting.projectId}
                                                    onSubmit={onAllowAdd}
                                                    isExclusion={false}
                                                    onRemove={onAllowRemove}
                                                    showSubprojects={projectToSetting.settings.enabled}
                                                />
                                            </div>

                                            <div>
                                                <label>Exclude applications from</label>
                                                <UserCriteriaEditor
                                                    criteria={projectToSetting.settings.excludeRequestsFrom}
                                                    projectId={projectToSetting.projectId}
                                                    onSubmit={onExcludeAdd}
                                                    isExclusion={true}
                                                    onRemove={onExcludeRemove}
                                                    showSubprojects={false}
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
