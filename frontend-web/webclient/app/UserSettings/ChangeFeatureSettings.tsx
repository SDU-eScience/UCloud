import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {callAPI} from "@/Authentication/DataHook";
import {fetchBackendFeatures} from "@/Features/backend";
import {sendFailureNotification} from "@/Notifications";
import {Markdown} from "@/ui-components";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {SettingsCheckboxRow, SettingsSection} from "@/ui-components/SettingsComponents";
import {featuresApi, FeatureToggle} from "@/UCloud/FeaturesApi";

interface ChangeFeatureSettingsProps {
    setLoading: (loading: boolean) => void;
}

export const ChangeFeatureSettings: React.FunctionComponent<ChangeFeatureSettingsProps> = ({setLoading: setPageLoading}) => {
    const [features, setFeatures] = useState<FeatureToggle[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        setPageLoading(loading);
    }, [loading, setPageLoading]);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const result = await callAPI(featuresApi.retrieveToggles());
            setFeatures(result ?? []);
        } catch {
            sendFailureNotification("Could not load features");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        void load();
    }, [load]);

    const toggleFeature = useCallback(async (feature: FeatureToggle) => {
        const enabled = !feature.enabled;
        setFeatures(previous => previous.map(item =>
            item.feature === feature.feature ? {...item, enabled} : item
        ));

        try {
            await callAPI(featuresApi.updateToggle({
                feature: feature.feature,
                enabled,
            }));
            await fetchBackendFeatures(true);
        } catch {
            setFeatures(previous => previous.map(item =>
                item.feature === feature.feature ? {...item, enabled: feature.enabled} : item
            ));
            sendFailureNotification("Could not update feature");
        }
    }, []);

    return <SettingsSection
        id="features"
        title="Features"
        description="Optional features available to you. Turning a feature off affects only your user, and you can turn it back on at any time."
    >
        {loading ? <HexSpin /> : features.length === 0 ? (
            <div>No optional features are available to you.</div>
        ) : features.map(feature =>
            <SettingsCheckboxRow
                key={feature.feature}
                title={feature.title}
                description={<Markdown>{feature.description}</Markdown>}
                checked={feature.enabled}
                onClick={() => void toggleFeature(feature)}
                disabled={loading}
            />
        )}
    </SettingsSection>;
};
