import * as React from "react";
import {useCallback, useEffect, useRef, useState} from "react";
import {injectStyle} from "@/Unstyled";
import {TerminalEngineHandle, TerminalSearchMatch} from "@/TerminalEngine/engine";
import Icon from "@/ui-components/Icon";
import {TooltipV2} from "@/ui-components/Tooltip";

export function TerminalSearchBar({
    terminal,
    open,
    onClose,
}: {
    terminal: TerminalEngineHandle;
    open: boolean;
    onClose: () => void;
}): React.ReactNode {
    const inputRef = useRef<HTMLInputElement | null>(null);
    const [query, setQuery] = useState("");
    const [caseSensitive, setCaseSensitive] = useState(false);
    const [matches, setMatches] = useState<TerminalSearchMatch[]>([]);
    const [activeIndex, setActiveIndex] = useState(-1);

    useEffect(() => {
        if (!open) {
            terminal.clearSelection();
            return;
        }
        window.requestAnimationFrame(() => {
            inputRef.current?.focus();
            inputRef.current?.select();
        });
    }, [open, terminal]);

    const runSearch = useCallback((nextQuery: string, nextCase: boolean, jumpLast: boolean) => {
        if (!nextQuery) {
            setMatches([]);
            setActiveIndex(-1);
            terminal.clearSelection();
            return;
        }
        const found = terminal.search(nextQuery, nextCase);
        setMatches(found);
        if (found.length === 0) {
            setActiveIndex(-1);
            terminal.clearSelection();
            return;
        }
        const idx = jumpLast ? found.length - 1 : 0;
        setActiveIndex(idx);
        terminal.jumpToMatch(found[idx]);
    }, [terminal]);

    const stepMatch = useCallback((delta: number) => {
        if (matches.length === 0) {
            runSearch(query, caseSensitive, delta < 0);
            return;
        }
        const nextIndex = (activeIndex + delta + matches.length) % matches.length;
        setActiveIndex(nextIndex);
        terminal.jumpToMatch(matches[nextIndex]);
    }, [activeIndex, caseSensitive, matches, query, runSearch, terminal]);

    if (!open) return null;

    return <div className={SearchBarClass} onMouseDown={e => e.stopPropagation()}>
        <input
            ref={inputRef}
            type="text"
            className="search-input"
            placeholder="Find in scrollback..."
            value={query}
            onChange={e => {
                const value = e.target.value;
                setQuery(value);
                runSearch(value, caseSensitive, true);
            }}
            onKeyDown={e => {
                if (e.key === "Escape") {
                    e.preventDefault();
                    e.stopPropagation();
                    onClose();
                    terminal.focus();
                } else if (e.key === "Enter") {
                    e.preventDefault();
                    e.stopPropagation();
                    stepMatch(e.shiftKey ? -1 : 1);
                }
            }}
        />
        <span className="search-count">
            {query ? (matches.length === 0 ? "0/0" : `${activeIndex + 1}/${matches.length}`) : ""}
        </span>
        <TooltipV2 tooltip="Match case">
            <button
                type="button"
                className={`search-btn ${caseSensitive ? "active" : ""}`}
                onClick={() => {
                    const next = !caseSensitive;
                    setCaseSensitive(next);
                    runSearch(query, next, true);
                }}
            >
                Aa
            </button>
        </TooltipV2>
        <TooltipV2 tooltip="Previous match (Shift+Enter)">
            <button type="button" className="search-btn" onClick={() => stepMatch(-1)}>
                <Icon name="heroChevronUp" size={14} />
            </button>
        </TooltipV2>
        <TooltipV2 tooltip="Next match (Enter)">
            <button type="button" className="search-btn" onClick={() => stepMatch(1)}>
                <Icon name="heroChevronDown" size={14} />
            </button>
        </TooltipV2>
        <TooltipV2 tooltip="Close search (Esc)">
            <button
                type="button"
                className="search-btn"
                onClick={() => {
                    onClose();
                    terminal.focus();
                }}
            >
                <Icon name="close" size={12} />
            </button>
        </TooltipV2>
    </div>;
}

const SearchBarClass = injectStyle("terminal-search-bar", k => `
    ${k} {
        position: absolute;
        top: 8px;
        right: 12px;
        z-index: 6;
        display: flex;
        align-items: center;
        gap: 4px;
        padding: 4px 6px;
        border-radius: 6px;
        background: var(--backgroundCard);
        border: 1px solid var(--borderColor);
        box-shadow: 0 6px 20px rgba(0, 0, 0, 0.25);
        font-family: var(--sansSerif);
        font-size: 12px;
        color: var(--textPrimary);
    }

    ${k} .search-input {
        width: 180px;
        padding: 3px 6px;
        border-radius: 4px;
        border: 1px solid var(--borderColor);
        background: var(--background);
        color: var(--textPrimary);
        font: inherit;
        outline: none;
    }

    ${k} .search-input:focus {
        border-color: var(--primaryMain);
    }

    ${k} .search-count {
        min-width: 38px;
        text-align: center;
        color: var(--textSecondary);
        font-variant-numeric: tabular-nums;
    }

    ${k} .search-btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 24px;
        height: 24px;
        padding: 0 5px;
        border: 1px solid transparent;
        border-radius: 4px;
        background: transparent;
        color: var(--textSecondary);
        cursor: pointer;
        font: inherit;
    }

    ${k} .search-btn:hover {
        background: var(--rowHover);
        color: var(--textPrimary);
    }

    ${k} .search-btn.active {
        border-color: var(--primaryMain);
        color: var(--primaryMain);
    }
`);
