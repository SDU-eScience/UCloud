// Error summary for the creator
// =====================================================================================================================
// The editor reports parse errors, semantic validation errors, and provider preview errors. They
// appear in one warning at the top of the main content area.
//
// Selecting a parse error switches to the YAML view and jumps to the line. Selecting a semantic
// error that names a parameter selects that parameter in the visual editor. Errors that do not
// name a parameter (global errors) just switch to the editor view.
//
// The summary is the bridge between the error sources and the editor views. It does not own the
// errors; it reads them from the draft and from the active preview request.

import * as React from "react";
import {injectStyle} from "@/Unstyled";
import {Icon, Text} from "@/ui-components";
import Warning from "@/ui-components/Warning";
import {CreatorDraft, CreatorValidationError} from "@/Applications/Creator/Draft";
import {CreatorSourceParseError} from "@/Applications/Creator/SourceParser";

export interface ErrorSummaryProps {
    // The current draft. The summary reads `parseErrors` and `validation.errors`.
    draft: CreatorDraft;
    // Called when the user clicks a parse error. The YAML editor scrolls to the line.
    onJumpToSourceLine: (line: number, column: number) => void;
    // Called when the user clicks a semantic error. The editor focuses its parameter, field, or
    // source location when the error provides one.
    onFocusParameter: (error: CreatorValidationError) => void;
    validating?: boolean;
    extraErrors?: CreatorValidationError[];
    rateLimit?: {remaining: number; retryAt?: number | string} | null;
}

export function ErrorSummary(props: ErrorSummaryProps): React.ReactNode {
    const {draft} = props;
    const parseErrors = draft.parseErrors ?? [];
    const validationErrors = draft.validation.errors;
    const extraErrors = props.extraErrors ?? [];
    const warning = props.validating
        ? "Checking this draft with the server..."
        : extraErrors.length > 0
            ? "Preview could not be rendered."
            : "Fix the following errors before continuing.";

    if (parseErrors.length === 0 && validationErrors.length === 0 && extraErrors.length === 0 && !props.validating) return null;

    return (
        <Warning mb="16px" warning={warning}>
            <div id="creator-error-summary">
                <ul className={ErrorListClass}>
                    {parseErrors.map((e, i) => (
                        <ErrorSummaryItem
                            key={`p${i}`}
                            message={formatParseError(e)}
                            onClick={() => props.onJumpToSourceLine(e.line, e.column)}
                            kind="parse"
                        />
                    ))}
                    {validationErrors.map((e, i) => (
                        <ErrorSummaryItem
                            key={`v${i}`}
                            message={e.message}
                            onClick={() => props.onFocusParameter(e)}
                            kind="validation"
                        />
                    ))}
                    {extraErrors.map((e, i) => (
                        <ErrorSummaryItem
                            key={`x${i}`}
                            message={e.message}
                            onClick={() => props.onFocusParameter(e)}
                            kind="validation"
                        />
                    ))}
                </ul>
                {extraErrors.some(error => error.code === "RATE_LIMITED") && props.rateLimit ? (
                    <Text fontSize={12} color="textSecondary" mt="8px">
                        {props.rateLimit.retryAt
                            ? `Try again after ${formatRetryAt(props.rateLimit.retryAt)}.`
                            : `No requests remain in the current limit window (${props.rateLimit.remaining} remaining).`}
                    </Text>
                ) : null}
            </div>
        </Warning>
    );
}

function ErrorSummaryItem(props: {message: string; onClick: () => void; kind: "parse" | "validation"}): React.ReactNode {
    return (
        <li className={ErrorItemClass} onClick={props.onClick} tabIndex={0}
            onKeyDown={e => {if (e.key === "Enter" || e.key === " ") {e.preventDefault(); props.onClick();}}}
        >
            <Icon
                name={props.kind === "parse" ? "heroCodeBracket" : "heroExclamationCircle"}
                size={14}
                color="errorMain"
            />
            <Text fontSize={13} className="error-item-message">{props.message}</Text>
        </li>
    );
}

function formatParseError(e: CreatorSourceParseError): string {
    if (e.line > 0) return `Line ${e.line}, column ${e.column}: ${e.message}`;
    return e.message;
}

const ErrorListClass = injectStyle("creator-error-list", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 4px;
        margin: 0;
        padding: 0;
        list-style: none;
    }
`);

function formatRetryAt(value: number | string): string {
    const date = new Date(typeof value === "number" ? value : value);
    return Number.isNaN(date.getTime()) ? "the retry time returned by the server" : date.toLocaleTimeString();
}

const ErrorItemClass = injectStyle("creator-error-item", k => `
    ${k} {
        display: flex;
        align-items: flex-start;
        gap: 8px;
        padding: 4px 6px;
        border-radius: 4px;
        cursor: pointer;
        color: var(--textPrimary);
    }

    ${k}:hover, ${k}:focus {
        background: var(--backgroundCardHover);
        outline: none;
    }

    ${k} .error-item-message {
        min-width: 0;
        word-break: break-word;
        white-space: pre-wrap;
    }
`);
