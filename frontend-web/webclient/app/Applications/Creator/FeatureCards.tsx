// Feature cards for the content area
// =====================================================================================================================
// When a runtime feature is enabled, the content area shows the corresponding cards that the user
// would see on the job creation page. These cards reuse the existing job-creation widget controls
// for visual fidelity, wrapped in a display-only layer with `pointer-events: none` to disable
// interactivity — the same pattern as the parameter content editor.
//
// The cards mirror the job creation UI:
//
// - Storage card: shown when `features.folders` is enabled. Uses the input_directory widget.
// - Connectivity card: shown when any of links, public IPs, job linking, or SSH is enabled. Uses
//   the corresponding widget controls (ingress, peer, network_ip, SSH). Each sub-section is
//   clickable and scrolls to the metadata toggle that controls it.
//
// Clicking the Storage card highlights the Folders toggle in the metadata panel. Clicking a
// Connectivity sub-section highlights the specific feature toggle. The highlight deselects any
// selected parameter first so the metadata panel is visible, then scrolls and plays a pulse-glow.

import * as React from "react";
import {useCallback} from "react";
import {Flex, Text} from "@/ui-components";
import {injectStyle} from "@/Unstyled";
import {Application, ApplicationParameter} from "@/Applications/AppStoreApi";
import {FieldGroup, Widget} from "@/Applications/Jobs/Widgets";
import {SshWidget} from "@/Applications/Jobs/Widgets/Ssh";
import {A2Yaml, A2SshMode} from "@/Applications/Creator/A2";
import {CreatorHighlightTarget} from "@/Applications/Creator/Highlight";
import {CreatorDraft} from "@/Applications/Creator/Draft";

export interface FeatureCardsProps {
    draft: CreatorDraft;
    // Called when the user clicks a card section. The parent uses this to deselect any selected
    // parameter so the metadata panel becomes visible before the highlight plays.
    onHighlight: (target: CreatorHighlightTarget) => void;
}

// Memoized with a field comparator: the cards read only feature/ssh/software state, which does
// not change on invocation keystrokes. The draft object itself changes on every keystroke, so a
// shallow prop comparison alone would not prevent the re-render.
export const FeatureCards = React.memo(FeatureCardsBase, (prev, next) =>
    prev.draft.application.features === next.draft.application.features &&
    prev.draft.application.ssh === next.draft.application.ssh &&
    prev.draft.application.software === next.draft.application.software &&
    prev.onHighlight === next.onHighlight
);

function FeatureCardsBase(props: FeatureCardsProps): React.ReactNode {
    const {application} = props.draft;
    const features = application.features ?? null;

    const showStorage = features?.folders === true;
    const showConnectivity = hasConnectivity(application);

    if (!showStorage && !showConnectivity) return null;

    return (
        <>
            {showStorage ? (
                <StorageCard application={application} onHighlight={() => props.onHighlight("feature-folders")} />
            ) : null}
            {showConnectivity ? (
                <ConnectivityCard application={application} onHighlight={props.onHighlight} />
            ) : null}
        </>
    );
}

// Fake application for widget rendering. Mirrors fakeApplication from ParameterContent.tsx so the
// widget controls get the invocation flags they expect. SSH mode is upper-cased because the runtime
// SshDescription uses uppercase modes while the A2 source uses title-case.
function fakeApplicationForCards(a2: A2Yaml): Application {
    const features = a2.features;
    return {
        metadata: {
            name: a2.name,
            version: a2.version,
            authors: [],
            title: a2.title ?? "",
            description: a2.description ?? "",
            public: false,
        },
        invocation: {
            tool: {name: a2.name, version: a2.version, tool: undefined},
            invocation: [],
            parameters: [],
            outputFileGlobs: [],
            applicationType: "BATCH",
            allowMultiNode: features?.multiNode ?? false,
            allowPublicIp: features?.ipAddresses ?? false,
            allowPublicLink: features?.links ?? false,
            allowAdditionalPeers: features?.jobLinking ?? false,
            allowAdditionalMounts: features?.folders ?? false,
            ssh: a2.ssh ? {mode: a2.ssh.mode.toUpperCase() as "DISABLED" | "OPTIONAL" | "MANDATORY"} : undefined,
            fileExtensions: [],
            licenseServers: [],
        },
    };
}

// Storage card
// -------------------------------------------------------------------------------------------------------------------
// Shown when folders are enabled. Uses the input_directory widget that the job creation Storage
// card uses. The entire card body is clickable and highlights the Folders toggle.

function StorageCard(props: {application: A2Yaml; onHighlight: () => void}): React.ReactNode {
    const app = fakeApplicationForCards(props.application);
    const param: ApplicationParameter = {
        type: "input_directory",
        description: "Add directories to your job. Available in /work.",
        title: "",
        optional: true,
        name: "resourceFolder0",
    };

    return (
        <div className={FeatureCardIslandClass} id="creator-card-storage">
            <FeatureCardHeading>Storage</FeatureCardHeading>
            <FeatureSection onClick={props.onHighlight}>
                <div className={FeatureSectionBodyClass}>
                    <FieldGroup>
                        <Widget
                            application={app}
                            parameter={param}
                            errors={{}}
                            setErrors={() => {}}
                            injectWorkflowParameters={() => {}}
                            compact
                            selected={false}
                            displayTitle="Folder #1"
                            onValueChange={() => {}}
                        />
                    </FieldGroup>
                </div>
            </FeatureSection>
        </div>
    );
}

// Connectivity card
// -------------------------------------------------------------------------------------------------------------------
// Shown when any connectivity feature is active. Each sub-section maps to its own feature toggle.
// The sub-sections use the real widget controls (ingress, peer, network_ip, SSH) for visual fidelity.

function ConnectivityCard(props: {
    application: A2Yaml;
    onHighlight: (target: CreatorHighlightTarget) => void;
}): React.ReactNode {
    const {application: app2} = props;
    const features = app2.features;
    const sshMode: A2SshMode = app2.ssh?.mode ?? "Disabled";
    const app = fakeApplicationForCards(app2);

    return (
        <div className={FeatureCardIslandClass} id="creator-card-connectivity">
            <FeatureCardHeading>Connectivity</FeatureCardHeading>
            <Flex flexDirection="column" gap="0">
                {sshMode !== "Disabled" ? (
                    <FeatureSection onClick={() => props.onHighlight("feature-ssh")}>
                        <div className={FeatureSectionBodyClass}>
                            <SshWidget
                                application={app}
                                embedded
                                fieldRow
                                onSshStatusChanged={() => {}}
                                onSshKeysValid={() => {}}
                                initialEnabledStatus={false}
                            />
                        </div>
                    </FeatureSection>
                ) : null}
                {features?.links === true ? (
                    <FeatureSection onClick={() => props.onHighlight("feature-links")}>
                        <div className={FeatureSectionBodyClass}>
                            <CompactResourceDisplay
                                param={{type: "ingress", description: "Public links make your job accessible through a web browser.", title: "", optional: true, name: "ingress0"}}
                                displayTitle="Public link #1"
                                app={app}
                            />
                        </div>
                    </FeatureSection>
                ) : null}
                {features?.jobLinking === true ? (
                    <FeatureSection onClick={() => props.onHighlight("feature-jobLinking")}>
                        <div className={FeatureSectionBodyClass}>
                            <CompactResourceDisplay
                                param={{type: "private_network", description: "Connect this job to a network of other jobs.", title: "", optional: true, name: "resourcePrivateNetwork0"}}
                                displayTitle="Private network #1"
                                app={app}
                            />
                        </div>
                    </FeatureSection>
                ) : null}
                {features?.ipAddresses === true ? (
                    <FeatureSection onClick={() => props.onHighlight("feature-ipAddresses")}>
                        <div className={FeatureSectionBodyClass}>
                            <CompactResourceDisplay
                                param={{type: "network_ip", description: "Make your job reachable from the Internet.", title: "", optional: true, name: "network0"}}
                                displayTitle="Public IP #1"
                                app={app}
                            />
                        </div>
                    </FeatureSection>
                ) : null}
            </Flex>
        </div>
    );
}

// A clickable wrapper around a section. The body inside has pointer-events: none so the widgets
// are display-only, but the wrapper itself receives clicks and triggers the highlight.
function FeatureSection(props: {onClick: () => void; children: React.ReactNode}): React.ReactNode {
    const onClick = useCallback(() => props.onClick(), [props.onClick]);
    return (
        <div
            className={FeatureSectionWrapperClass}
            onClick={onClick}
            role="button"
            tabIndex={0}
            onKeyDown={e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onClick(); } }}
        >
            {props.children}
        </div>
    );
}

// Display-only compact resource row. Renders one Widget in compact mode with the description on
// the first row. pointer-events: none is on the parent div.
function CompactResourceDisplay(props: {
    param: ApplicationParameter;
    displayTitle: string;
    app: Application;
}): React.ReactNode {
    return (
        <FieldGroup>
            <Widget
                application={props.app}
                parameter={props.param}
                errors={{}}
                setErrors={() => {}}
                injectWorkflowParameters={() => {}}
                compact
                selected={false}
                displayTitle={props.displayTitle}
                onValueChange={() => {}}
            />
        </FieldGroup>
    );
}

// Shared card building blocks
// -------------------------------------------------------------------------------------------------------------------

function FeatureCardHeading(props: React.PropsWithChildren<{action?: React.ReactNode}>): React.ReactNode {
    return (
        <Flex alignItems="center" gap="8px" mb="16px">
            <Text fontWeight="normal" fontSize="16px">{props.children}</Text>
            {!props.action ? null : <Flex ml="auto">{props.action}</Flex>}
        </Flex>
    );
}

// Determine whether the Connectivity card should be shown. Matches the job creation logic:
// SSH is visible when its mode is not Disabled, plus links, IPs, and job linking features.
function hasConnectivity(app: A2Yaml): boolean {
    const features = app.features;
    const sshMode = app.ssh?.mode ?? "Disabled";
    return sshMode !== "Disabled" ||
        features?.links === true ||
        features?.ipAddresses === true ||
        features?.jobLinking === true;
}

// Styling
// -------------------------------------------------------------------------------------------------------------------

const FeatureCardIslandClass = injectStyle("creator-feature-card", k => `
    ${k} {
        max-width: 944px;
        background: var(--backgroundCard);
        box-shadow: var(--defaultShadow);
        border: var(--defaultCardBorder);
        border-radius: 10px;
        padding: 20px;
        box-sizing: border-box;
    }
    @media (max-width: 600px) {
        ${k} {
            padding: 16px;
        }
    }
`);

// Wrapper for a clickable section. The body inside has pointer-events: none so the widgets are
// display-only, but the wrapper itself receives clicks.
const FeatureSectionWrapperClass = injectStyle("creator-feature-section", k => `
    ${k} {
        cursor: pointer;
        border-radius: 6px;
        transition: background-color 0.15s ease;
    }

    ${k} + ${k} {
        margin-top: 8px;
    }

    ${k}:hover {
        background: var(--rowHover, var(--backgroundCardHover));
    }

    ${k}:focus-visible {
        outline: 2px solid var(--primaryMain);
        outline-offset: 1px;
    }
`);

// The body of a section. pointer-events: none disables all interaction with the widget controls
// inside, matching the pattern from ParameterContent.tsx.
const FeatureSectionBodyClass = injectStyle("creator-feature-section-body", k => `
    ${k} {
        pointer-events: none;
        min-width: 0;
    }
`);
