import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {Client} from "@/Authentication/HttpClientInstance";
import {callAPI} from "@/Authentication/DataHook";
import {fetchBackendFeatures} from "@/Features/backend";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {Box, Button, Card, Flex, Icon, Input, Label, MainContainer, Markdown, Select} from "@/ui-components";
import {Toggle} from "@/ui-components/Toggle";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {sendFailureNotification, sendSuccessNotification} from "@/Notifications";
import {displayErrorMessageOrDefault} from "@/UtilityFunctions";
import {FeatureAdminEntry, FeatureGrant, featuresApi} from "@/UCloud/FeaturesApi";

function FeatureManagement(): React.ReactNode {
    usePage("Feature management", SidebarTabId.ADMIN);

    const isAdmin = Client.userIsAdmin;
    const [entries, setEntries] = useState<FeatureAdminEntry[]>([]);
    const [grants, setGrants] = useState<FeatureGrant[]>([]);
    const [loading, setLoading] = useState(true);
    const [selectedFeature, setSelectedFeature] = useState("");
    const [projectId, setProjectId] = useState("");

    const load = useCallback(async () => {
        if (!isAdmin) return;
        setLoading(true);
        try {
            const [adminEntries, grantEntries] = await Promise.all([
                callAPI(featuresApi.browseAdmin()),
                callAPI(featuresApi.browse()),
            ]);
            setEntries(adminEntries ?? []);
            setGrants(grantEntries ?? []);
            setSelectedFeature(previous => previous || adminEntries?.[0]?.feature || "");
        } catch {
            sendFailureNotification("Could not load features");
        } finally {
            setLoading(false);
        }
    }, [isAdmin]);

    useEffect(() => {
        void load();
    }, [load]);

    const updateGlobal = useCallback(async (entry: FeatureAdminEntry, enabled: boolean) => {
        try {
            await callAPI(featuresApi.updateGlobal({
                feature: entry.feature,
                enabled,
            }));
            setEntries(previous => previous.map(item =>
                item.feature === entry.feature ? {...item, globallyEnabled: enabled} : item
            ));
            await fetchBackendFeatures(true);
            sendSuccessNotification(enabled ? "Feature enabled globally" : "Feature disabled globally");
        } catch (error) {
            displayErrorMessageOrDefault(error, "Could not update feature");
        }
    }, []);

    const grantFeature = useCallback(async (event: React.FormEvent) => {
        event.preventDefault();
        const trimmedProjectId = projectId.trim();
        if (!selectedFeature || !trimmedProjectId) {
            sendFailureNotification("Select a feature and enter a project ID");
            return;
        }

        try {
            await callAPI(featuresApi.grant({
                feature: selectedFeature,
                projectId: trimmedProjectId,
            }));
            setProjectId("");
            await load();
            sendSuccessNotification("Feature granted to project");
        } catch (error) {
            displayErrorMessageOrDefault(error, "Could not grant feature");
        }
    }, [load, projectId, selectedFeature]);

    const revokeFeature = useCallback(async (grant: FeatureGrant) => {
        try {
            await callAPI(featuresApi.revoke({
                feature: grant.feature,
                projectId: grant.projectId,
            }));
            await load();
            sendSuccessNotification("Feature grant revoked");
        } catch (error) {
            displayErrorMessageOrDefault(error, "Could not revoke feature");
        }
    }, [load]);

    if (!isAdmin) return null;

    return <MainContainer
        header={<h3 className="title">Feature management</h3>}
        main={<>
            <p>Manage runtime feature enablement and project grants.</p>

            <h4>Global features</h4>
            <p>Globally enabled features are available to all users.</p>
            {loading ? <HexSpin /> : entries.map(entry => {
                const globallyEnabled = entry.configEnabled || entry.globallyEnabled;
                return <Card key={entry.feature} mb="12px">
                    <Flex alignItems="center" justifyContent="space-between" gap="16px">
                        <Box>
                            <Flex alignItems="center" gap="8px">
                                <strong>{entry.title}</strong>
                                <span style={{color: "var(--textSecondary)", fontSize: "0.9em"}}>{entry.feature}</span>
                            </Flex>
                            <Box mt="4px">
                                <Markdown>{entry.description}</Markdown>
                            </Box>
                            {entry.toggleable ? <Box mt="8px" style={{color: "var(--textSecondary)", fontSize: "0.9em"}}>
                                This feature supports a per-user toggle.
                            </Box> : null}
                            {entry.configEnabled ? <Box mt="8px" style={{color: "var(--textSecondary)", fontSize: "0.9em"}}>
                                Enabled by server configuration
                            </Box> : null}
                        </Box>
                        <Toggle
                            checked={globallyEnabled}
                            disabled={entry.configEnabled}
                            onChange={() => void updateGlobal(entry, !globallyEnabled)}
                            ariaLabel={`Enable ${entry.title} globally`}
                        />
                    </Flex>
                </Card>;
            })}

            <h4>Project grants</h4>
            <p>Grant a feature to a project. Members of the project can then access the feature. If the feature is user-toggleable, they can turn it on or off for themselves.</p>
            <Card mb="16px">
                <form onSubmit={grantFeature}>
                    <Flex gap="16px" alignItems="flex-end" flexWrap="wrap">
                        <Label width="240px">
                            Feature
                            <Select
                                value={selectedFeature}
                                onChange={event => setSelectedFeature(event.target.value)}
                            >
                                {entries.map(entry => <option key={entry.feature} value={entry.feature}>
                                    {entry.title}
                                </option>)}
                            </Select>
                        </Label>
                        <Label flexGrow={1} minWidth="240px">
                            Project ID
                            <Input
                                value={projectId}
                                onChange={event => setProjectId(event.target.value)}
                                placeholder="Project ID"
                            />
                        </Label>
                        <Button type="submit" color="successMain">Grant</Button>
                    </Flex>
                </form>
            </Card>

            {loading ? <HexSpin /> : grants.length === 0 ? (
                <Box>No projects have been granted features.</Box>
            ) : grants.map(grant => {
                const feature = entries.find(entry => entry.feature === grant.feature);
                return <Flex
                    key={`${grant.feature}:${grant.projectId}`}
                    alignItems="center"
                    justifyContent="space-between"
                    borderBottom="1px solid var(--borderColor)"
                    py="12px"
                >
                    <Box>
                        <div>{feature?.title ?? grant.feature}</div>
                        <div style={{color: "var(--textSecondary)", fontSize: "0.9em"}}>
                            {grant.projectTitle || grant.projectId} ({grant.projectId})
                        </div>
                        <div style={{color: "var(--textSecondary)", fontSize: "0.9em"}}>
                            Granted by {grant.grantedBy} at {new Date(grant.grantedAt).toLocaleString()}
                        </div>
                    </Box>
                    <Icon
                        name="trash"
                        color="errorMain"
                        cursor="pointer"
                        onClick={() => void revokeFeature(grant)}
                    />
                </Flex>;
            })}
        </>}
    />;
}

export default FeatureManagement;
