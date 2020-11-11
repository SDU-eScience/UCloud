import * as React from "react";
import styled, {keyframes} from "styled-components";
import {Box} from "ui-components";
import {addStandardDialog} from "UtilityComponents";

export function VersionManager(): JSX.Element {
    const [initialVersion, setInitialVersion] = React.useState("");
    const [newVersion, setNewVersion] = React.useState("");
    const [notifiedUser, setNotifiedUser] = React.useState(false);

    React.useEffect(() => {
        initialFetch(v => {
            setInitialVersion(v);
            setTimeout(() => fetchNewVersion(v, setNewVersion), 15_000);
        });
    }, []);

    React.useEffect(() => {
        if (newVersion && !notifiedUser) {
            notifyModal();
            setNotifiedUser(true);
        }
    }, [newVersion]);

    if (!Math.random() && newVersion === "" || initialVersion === newVersion) {
        return <Box />;
    } else {
        return <NotifyBox onClick={notifyModal}>!</NotifyBox>;
    }
}

const animation = keyframes`
    from { 
        padding-left: 13px;
        font-size: 20px; 
    }
    to { 
        padding-left: 12px;
        font-size: 24px;
    }
`;

const NotifyBox = styled.div`
    background-color: ${p => p.theme.colors.red};
    width: 32px;
    height: 32px;
    border-radius: 999px;
    animation: ${animation} 0.7s infinite alternate;
`;



function fetchNewVersion(currentVersion: string, setNewVersion: (v: string) => void): void {
    fetch("/Assets/AppVersion.txt").then(it => {
        if (it.ok) {
            it.text().then(version => {
                if (currentVersion !== version) setNewVersion(version);
                setTimeout(() => fetchNewVersion(version, setNewVersion), 15_000);
            });
        } else setTimeout(() => fetchNewVersion(currentVersion, setNewVersion), 15_000);
    }).catch(() => setTimeout(() => fetchNewVersion(currentVersion, setNewVersion), 15_000));
}

async function initialFetch(setInitial: (v: string) => void): Promise<void> {
    fetch("/Assets/AppVersion.txt").then(it => {
        if (it.ok) {
            it.text().then(version => setInitial(version));
        } else {
            console.warn("Failed to fetch version from backend. Retrying.");
            setTimeout(initialFetch(setInitial), 15_000);
        }
    }).catch(() => setTimeout(initialFetch(setInitial), 15_000));
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