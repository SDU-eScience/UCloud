import * as React from "react";
import {MainContainer} from "@/ui-components";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {DocumentTypography, MarkdownDocument} from "@/ui-components/Markdown";
import {injectStyle} from "@/Unstyled";

const CliWizardClass = injectStyle("cli-download-wizard", k => `
    ${k} {
        --playground-active: var(--secondaryMain);
        --document-border: var(--borderColor);
        width: 100%;
        max-width: 1080px;
        min-width: 0;
        margin: 0 auto;
        background: var(--backgroundDefault);
    }

    html.dark ${k} {
        --playground-active: #3d4148;
        --document-border: #3f444c;
    }

    ${k} .cli-content {
        min-width: 0;
        padding: 12px 24px 32px;
        color: var(--textPrimary);
        font-size: 15px;
        line-height: 1.55;
    }

    ${k} .cli-header {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 8px;
        margin: -12px -24px 8px;
        padding: 12px 24px 20px;
        border-bottom: 1px solid var(--borderColor);
    }

    ${k} .cli-header h1 {
        margin: 0;
        padding: 0;
        border: 0;
        font-size: 30px;
        font-weight: normal;
        line-height: 1.2;
    }

    ${k} .cli-header p {
        margin: 0;
        color: var(--textSecondary);
    }

    ${k} .cli-step-label {
        color: var(--textSecondary);
        font-size: 13px;
        font-weight: 600;
        letter-spacing: 0.04em;
        text-transform: uppercase;
    }

    ${k} .cli-steps {
        display: grid;
        gap: 28px;
        margin: 24px 0 0;
        padding-left: 24px;
    }

    ${k} .cli-steps > li {
        min-width: 0;
        padding-left: 8px;
    }

    ${k} .cli-step-copy {
        display: grid;
        gap: 12px;
        min-width: 0;
    }

    ${k} .cli-step-copy > strong {
        font-size: 17px;
    }

    ${k} .cli-step-copy > p {
        margin: 0;
    }

    ${k} .cli-platforms {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(min(100%, 260px), 1fr));
        gap: 12px;
        min-width: 0;
        margin-top: 4px;
    }

    ${k} .cli-platform-card {
        display: grid;
        gap: 8px;
        min-width: 0;
        padding: 16px;
        border: 1px solid var(--borderColor);
        border-radius: 10px;
        background: var(--backgroundCard);
    }

    ${k} .cli-platform-card h3 {
        margin: 0;
        font-size: 16px;
    }

    ${k} .cli-platform-card p {
        margin: 0;
        color: var(--textSecondary);
        font-size: 13px;
    }

    ${k} .cli-platform-card code {
        overflow-wrap: anywhere;
    }

    ${k} .cli-download-link {
        width: fit-content;
    }

    @media (max-width: 760px) {
        ${k} .cli-content {
            padding: 12px 16px 24px;
        }

        ${k} .cli-header {
            margin-right: -16px;
            margin-left: -16px;
            padding-right: 16px;
            padding-left: 16px;
        }
    }
`);

interface CliPlatformDownload {
    os: "linux" | "darwin";
    architecture: "amd64" | "arm64";
    file: string;
    sha256: string;
}

interface CliManifest {
    version: string;
    platforms: CliPlatformDownload[];
}

function platformTitle(platform: CliPlatformDownload): string {
    const os = platform.os === "darwin" ? "macOS" : "Linux";
    const architecture = platform.architecture === "arm64" ? "ARM64" : "x86-64";
    return `${os} · ${architecture}`;
}

export default function CliDownloads(): React.ReactNode {
    usePage("UCloud CLI", SidebarTabId.NONE);

    const [manifest, setManifest] = React.useState<CliManifest>();
    const [loadFailed, setLoadFailed] = React.useState(false);
    const downloadBase = `${window.location.origin}/cli`;
    const installCommand = `curl -fsSL '${downloadBase}/install.sh' | UCLOUD_CLI_BASE_URL='${downloadBase}' bash`;

    React.useEffect(() => {
        let active = true;
        fetch(`${downloadBase}/current/manifest.json`, {cache: "no-store"})
            .then(response => {
                if (!response.ok) throw new Error("Could not load CLI download information");
                return response.json() as Promise<CliManifest>;
            })
            .then(result => {
                if (active) setManifest(result);
            })
            .catch(() => {
                if (active) setLoadFailed(true);
            });

        return () => {
            active = false;
        };
    }, [downloadBase]);

    return <MainContainer main={<div className={CliWizardClass}>
        <DocumentTypography className="cli-content">
            <header className="cli-header">
                <span className="cli-step-label">Step 1 · Install</span>
                <h1>Install UCloud CLI</h1>
                <p>Use the CLI to manage UCloud from a terminal on Linux or macOS.</p>
            </header>

            <ol className="cli-steps">
                <li>
                    <div className="cli-step-copy">
                        <strong>Run the installer</strong>
                        <p>The installer selects your platform, checks the download, and installs to <code>~/.local/bin</code>.</p>
                        <MarkdownDocument text={`\`\`\`bash\n${installCommand}\n\`\`\``} />
                    </div>
                </li>
                <li>
                    <div className="cli-step-copy">
                        <strong>Connect to UCloud</strong>
                        <p>Open a terminal and run this command. It opens a browser so you can sign in.</p>
                        <MarkdownDocument text={"```bash\nucloud connect\n```"} />
                    </div>
                </li>
                <li>
                    <div className="cli-step-copy">
                        <strong>Or download a binary directly{manifest ? ` · version ${manifest.version}` : ""}</strong>
                        {loadFailed ? <p>Download details are not available right now.</p> : null}
                        {!manifest && !loadFailed ? <p>Loading download details…</p> : null}
                        {manifest ? <div className="cli-platforms">
                            {manifest.platforms.map(platform => <div className="cli-platform-card" key={`${platform.os}-${platform.architecture}`}>
                                <h3>{platformTitle(platform)}</h3>
                                <a className="cli-download-link" href={`${downloadBase}/current/${platform.file}`} download>{platform.file}</a>
                                <p>SHA-256</p>
                                <code>{platform.sha256}</code>
                            </div>)}
                        </div> : null}
                    </div>
                </li>
            </ol>
        </DocumentTypography>
    </div>} />;
}
