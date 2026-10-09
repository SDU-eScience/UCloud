import * as React from "react";
import {Box, Button} from "@/ui-components";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {DocumentTypography} from "@/ui-components/Markdown";
import {injectStyle} from "@/Unstyled";
import {UcxSpinner} from "@/UCX/UcxSpinner";

export interface TutorialWizardProps {
    step: number;
    stepCount: number;
    previousLabel?: string;
    nextLabel?: string;
    showPrevious?: boolean;
    showNext?: boolean;
    nextHoldToConfirm?: boolean;
    nextColor?: string;
    nextBusy?: boolean;
    nextDisabled?: boolean;
    embedded?: boolean;
    onPrevious?: () => void;
    onNext?: () => void;
    children: React.ReactNode;
}

export const TutorialWizard: React.FunctionComponent<TutorialWizardProps> = props => {
    const showPrevious = props.showPrevious === true;
    const showNext = props.showNext !== false;
    const busy = props.nextBusy === true;
    const disabled = props.nextDisabled === true || busy;

    return <div className={TutorialWizardClass} data-embedded={props.embedded === true}>
        <DocumentTypography className="tutorial-content">{props.children}</DocumentTypography>
        <footer className="tutorial-actions">
            <TutorialWizardProgress step={props.step} stepCount={props.stepCount} />
            <Box flexGrow={1} />
            {showPrevious ?
                <Button color="secondaryMain" disabled={disabled} onClick={props.onPrevious}>
                    {props.previousLabel ?? "Previous step"}
                </Button> : null}
            {showNext ? (props.nextHoldToConfirm === true && !busy ?
                <ConfirmationButton
                    color={props.nextColor as any}
                    disabled={disabled}
                    actionText={props.nextLabel ?? "Next step"}
                    onAction={async () => props.onNext?.()}
                /> :
                <Button color={props.nextColor as any} disabled={disabled} onClick={props.onNext}>
                    {busy ? <UcxSpinner size={16} color="white" margin="0 8px 0 0" /> : null}
                    {props.nextLabel ?? "Next step"}
                </Button>) : null}
        </footer>
    </div>;
};

export const TutorialWizardProgress: React.FunctionComponent<{
    step: number;
    stepCount: number;
}> = props => {
    const stepCount = Math.max(1, props.stepCount);
    const step = Math.max(0, Math.min(props.step, stepCount - 1));

    return <div className={TutorialWizardProgressClass} aria-label={`Step ${step + 1} of ${stepCount}`}>
        <span>Step {step + 1} of {stepCount}</span>
        <div className="tutorial-progress-track" aria-hidden="true">
            <div style={{width: `${((step + 1) / stepCount) * 100}%`}} />
        </div>
    </div>;
};

export const TutorialWizardClass = injectStyle("tutorial-wizard", k => `
    ${k} {
        width: 100%;
        max-width: 1080px;
        height: calc(100vh - 48px);
        margin: 0 auto;
        display: flex;
        flex-direction: column;
        background: var(--backgroundDefault);
    }

    ${k}[data-embedded="true"] {
        height: auto;
        flex: 1 1 auto;
        min-height: 0;
    }

    ${k}[data-modal="true"] {
        height: 100%;
    }

    ${k} .tutorial-content {
        min-height: 0;
        overflow-y: auto;
        padding: 12px 24px 32px;
        flex-grow: 1;
    }

    ${k} .tutorial-actions {
        display: flex;
        gap: 12px;
        height: 68px;
        padding: 16px 0;
        border-top: 1px solid var(--borderColor);
        background: var(--backgroundDefault);
        justify-content: center;
    }

    ${k}[data-modal="true"] .tutorial-actions {
        flex: 0 0 auto;
        height: auto;
        padding: 16px 24px;
        border-top: 0;
        background: var(--dialogToolbar);
        justify-content: flex-end;
    }

    @media (max-width: 760px) {
        ${k} {
            height: calc(100vh - 24px);
            min-height: 0;
        }

        ${k}[data-embedded="true"] {
            height: auto;
        }

        ${k}[data-modal="true"] {
            height: 100%;
        }
    }
`);

export const TutorialWizardProgressClass = injectStyle("tutorial-progress", k => `
    ${k} {
        display: flex;
        align-items: center;
        gap: 16px;
        color: var(--textSecondary);
        font-size: 13px;
        font-weight: 600;
    }

    ${k} .tutorial-progress-track {
        width: 120px;
        height: 5px;
        overflow: hidden;
        border-radius: 999px;
        background: var(--borderColor);
    }

    ${k} .tutorial-progress-track > div {
        height: 100%;
        border-radius: inherit;
        background: var(--primaryMain);
        transition: width 180ms ease-out;
    }
`);
