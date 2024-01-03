import * as React from "react";
import {Box, theme} from "@/ui-components";
import {addStandardDialog} from "@/UtilityComponents";
import {injectStyle} from "@/Unstyled";

const TIMEOUT_DURATION = 180_000;

export function VersionManager(): JSX.Element {
    const [initialVersion, setInitialVersion] = React.useState("");
    const [newVersion, setNewVersion] = React.useState("");
    const [notifiedUser, setNotifiedUser] = React.useState(false);

    React.useEffect(() => {
        initialFetch(v => {
            setInitialVersion(v);
            setTimeout(() => fetchNewVersion(v, setNewVersion), TIMEOUT_DURATION);
        });
    }, []);

    React.useEffect(() => {
        if (newVersion && !notifiedUser) {
            notifyModal();
            setNotifiedUser(true);
        }
    }, [newVersion]);

    if (newVersion === "" || initialVersion === newVersion) {
        return <Box />;
    } else {
        return <div className={NotifyBox} onClick={notifyModal}>!</div>;
    }
}

const NotifyBox = injectStyle("notify-box", k => `
    ${k} {
        background-color: ${theme.colors.red};
        width: 32px;
        height: 32px;
        border-radius: 999px;
        animation: pulsate 0.7s infinite alternate;
    }

    @keyframes pulsate {
        from { 
            padding-left: 13px;
            font-size: 20px; 
        }
        to { 
            padding-left: 12px;
            font-size: 24px;
        }
    }
`);

const APP_VERSION_RESOURCE = "/AppVersion.txt";

function fetchNewVersion(currentVersion: string, setNewVersion: (v: string) => void): void {
    fetch(APP_VERSION_RESOURCE).then(it => {
        if (it.ok) {
            it.text().then(version => {
                if (currentVersion !== version) setNewVersion(version);
                setTimeout(() => fetchNewVersion(version, setNewVersion), TIMEOUT_DURATION);
            });
        } else setTimeout(() => fetchNewVersion(currentVersion, setNewVersion), TIMEOUT_DURATION);
    }).catch(() => setTimeout(() => fetchNewVersion(currentVersion, setNewVersion), TIMEOUT_DURATION));
}

async function initialFetch(setInitial: (v: string) => void): Promise<void> {
    fetch(APP_VERSION_RESOURCE).then(it => {
        if (it.ok) {
            it.text().then(version => setInitial(version));
        } else {
            window.setTimeout(() => initialFetch(setInitial), TIMEOUT_DURATION);
        }
    }).catch(() => window.setTimeout(() => initialFetch(setInitial), TIMEOUT_DURATION));
}

function notifyModal(): void {
    addStandardDialog({
        title: "A new UCloud version is live",
        message: "Please reload the page at your earliest convenience.",
        confirmText: "Reload",
        cancelText: "Wait",
        onConfirm: () => location.reload(),
        onCancel: () => undefined
    });
}
