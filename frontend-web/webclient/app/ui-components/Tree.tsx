import * as React from "react";
import Icon from "@/ui-components/Icon";
import {classConcat, extractDataTags, injectStyle} from "@/Unstyled";
import {CSSProperties, useCallback, useEffect, useRef} from "react";
import Flex from "@/ui-components/Flex";
import {Cursor} from "./Types";

export enum TreeAction {
    TOGGLE,
    OPEN,
    CLOSE,
    GO_UP,
    GO_DOWN,
    GO_TO_TOP,
    GO_TO_BOTTOM,
}

export interface TreeApi {
    activate: () => void;
    deactivate: () => void;
    isActive: () => boolean;
}

const KEY_MAP: Record<string, TreeAction> = {
    "Space": TreeAction.TOGGLE,
    "Enter": TreeAction.TOGGLE,
    "ArrowRight": TreeAction.OPEN,
    "ArrowLeft": TreeAction.CLOSE,
    "ArrowUp": TreeAction.GO_UP,
    "ArrowDown": TreeAction.GO_DOWN,
    "Home": TreeAction.GO_TO_TOP,
    "End": TreeAction.GO_TO_BOTTOM,
}

const TreeClass = injectStyle("tree", k => ``);

export const Tree: React.FunctionComponent<{
    apiRef?: React.RefObject<TreeApi | null>;
    unhandledShortcut?: (target: HTMLElement, ev: KeyboardEvent) => void;
    children: React.ReactNode;
    onAction?: (row: HTMLElement, action: TreeAction) => void;
}> = props => {
    const root = useRef<HTMLDivElement>(null);
    useEffect(() => {
        function visibleRows(): HTMLElement[] {
            const r = root.current;
            if (!r) return [];
            // Note(Jonas): Rule is for the divs injected by React-Window. Not a fan, but the specificity is needed.
            const INSANE_RULE = `div.${TreeClass} > div[data-open] > div > div > div > div.${TreeNodeClass}`;
            return Array.from(r.querySelectorAll(`[data-open] > .${TreeNodeClass}, [data-open] > * > .${TreeNodeClass}, ${INSANE_RULE}`));
        }

        function getRow(rowIdx: number): [HTMLElement | null, number] {
            const r = root.current;
            const allResults = visibleRows();
            if (!r) return [null, -1];
            if (rowIdx >= 0 && rowIdx < allResults.length) {
                return [allResults[rowIdx] as HTMLElement, rowIdx];
            }
            if (rowIdx > 0 && allResults.length > 0) {
                const rIdx = allResults.length - 1;
                return [allResults[rIdx] as HTMLElement, rIdx];
            }
            if (rowIdx < 0 && allResults.length > 0) {
                return [allResults[0] as HTMLElement, 0];
            }

            return [null, -1];
        }

        const isActive = () => {
            if (!root.current) return false;
            return root.current.hasAttribute("data-active");
        };

        const handleAction = (action: TreeAction) => {
            let rowIdx = visibleRows().findIndex(it => it.hasAttribute("data-selected"))
            let initialRowIdx = rowIdx;

            switch (action) {
                case TreeAction.TOGGLE: {
                    const [row] = getRow(rowIdx);
                    if (row) {
                        row.toggleAttribute("data-open");
                        props.onAction?.(row, action);
                    }
                    break;
                }

                case TreeAction.OPEN: {
                    const [row] = getRow(rowIdx);
                    if (row) {
                        row.setAttribute("data-open", "");
                        props.onAction?.(row, action);
                    }
                    break;
                }

                case TreeAction.CLOSE: {
                    const [row] = getRow(rowIdx);
                    if (row) {
                        if (row.hasAttribute("data-open")) {
                            row.removeAttribute("data-open");
                            row.querySelectorAll("[data-open]").forEach(e => e.removeAttribute("data-open"));
                        } else {
                            const parentContainer = row.parentElement?.parentElement;
                            if (parentContainer) {
                                row.removeAttribute("data-selected");
                                if (parentContainer.classList.contains(TreeNodeClass)) {
                                    rowIdx = visibleRows().indexOf(parentContainer);
                                } else {
                                    rowIdx = 0;
                                }
                            }
                        }
                        props.onAction?.(row, action);
                    }
                    break;
                }

                case TreeAction.GO_UP: {
                    rowIdx--;
                    break;
                }

                case TreeAction.GO_DOWN: {
                    rowIdx++;
                    break;
                }

                case TreeAction.GO_TO_TOP: {
                    rowIdx = 0;
                    break;
                }

                case TreeAction.GO_TO_BOTTOM: {
                    rowIdx = 1000000;
                    break;
                }
            }

            if (rowIdx !== initialRowIdx) {
                const [initialRow] = getRow(initialRowIdx);
                if (initialRow) initialRow.removeAttribute("data-selected");
                const [newRow] = getRow(rowIdx);
                if (newRow) {
                    newRow.setAttribute("data-selected", "");
                    newRow.scrollIntoView({block: "nearest"});
                }
            }
        };

        const handler = (ev: KeyboardEvent) => {
            if (ev.defaultPrevented) return;
            if (!isActive()) return;

            let action: TreeAction | undefined = KEY_MAP[ev.code];

            if (action === undefined) {
                if (props.unhandledShortcut) {
                    const row = visibleRows().find(it => it.hasAttribute("data-selected"));
                    if (row) props.unhandledShortcut(row, ev);
                }
            } else {
                ev.preventDefault();
                handleAction(action);
            }
        };

        if (props.apiRef) {
            props.apiRef.current = {
                activate: () => {
                    document.body.querySelectorAll(`.${TreeClass}[data-active]`)
                        .forEach(tree => tree.removeAttribute("data-active"));

                    root?.current?.setAttribute("data-active", "");
                    handleAction(TreeAction.GO_TO_TOP);
                },
                deactivate: () => {
                    document.body.querySelectorAll(`.${TreeClass}[data-active]`)
                        .forEach(tree => tree.removeAttribute("data-active"));
                },
                isActive,
            };
        }

        document.body.addEventListener("keydown", handler);
        return () => {
            document.body.removeEventListener("keydown", handler);
        }
    }, [props.apiRef, props.onAction]);

    return <div ref={root} className={TreeClass}>
        <div data-open={""}>
            {props.children}
        </div>
    </div>;
};

const TreeNodeChildren = injectStyle("tree-node-children", () => ``);

const TreeNodeClass = injectStyle("tree-node", k => `
    ${k} > [data-component=list-row]:hover {
        background: unset;
    }
    
    [data-active] ${k}[data-selected] > [data-component=list-row]:hover,
    [data-active] ${k}[data-selected] > [data-component=list-row] {
        background: var(--rowHover);
    }
    
    ${k} > *[data-component=list-row] {
        width: 100%;
        border: none;
        text-align: left;
        outline: none;
        font-size: 15px;
        border-bottom: 1px solid var(--borderColor);
        user-select: none;
        -webkit-user-select: none;
    }
    
    ${k} > *[data-component=list-row] .open-chevron {
        transition: transform .2s ease-in-out;
        transform: rotate(-90deg);
    }
    
    ${k}[data-open] > *[data-component=list-row] .open-chevron {
        transform: none;
    }
    
    ${k} > .${TreeNodeChildren} {
        margin-left: var(--indent, 0);
        display: none;
    }
    
    ${k}[data-open] > .${TreeNodeChildren} {
        display: block;
    }
`);

export const TreeNode: React.FunctionComponent<{
    left: React.ReactNode;
    right?: React.ReactNode;
    children?: React.ReactNode;
    className?: string;
    onContextMenu?: React.MouseEventHandler<HTMLDivElement>;
    indent?: number;
    onActivate?: (open: boolean, element: HTMLElement) => void;
    slim?: boolean;
    cursor?: Cursor;
}> = props => {
    const ref = useRef<HTMLDivElement>(null);
    const style: CSSProperties = {};
    style["--indent"] = (props.indent ?? (props.slim ? 16 : 32)) + "px";
    if (props.cursor) style.cursor = props.cursor;

    const activate = useCallback((ev?: React.SyntheticEvent) => {
        ev?.stopPropagation();
        const div = ref.current;
        if (!div) return;

        document.body.querySelectorAll(`.${TreeClass}[data-active]`)
            .forEach(tree => tree.removeAttribute("data-active"));

        document.body.querySelectorAll(`.${TreeNodeClass}[data-selected]`)
            .forEach(row => row.removeAttribute("data-selected"));

        div.setAttribute("data-selected", "");

        let e: Element | null = div;
        while (e) {
            if (e.classList.contains(TreeClass)) {
                e.setAttribute("data-active", "");
                break;
            }
            e = e.parentElement;
        }
    }, []);

    const toggleOpen = useCallback((ev: React.SyntheticEvent) => {
        ev.stopPropagation();
        const div = ref.current;
        if (!div) return;
        activate();
        div.toggleAttribute("data-open");
        props.onActivate?.(div.hasAttribute("data-open"), div);
    }, [props.onActivate]);

    return <div
        className={classConcat(TreeNodeClass, props.className)}
        style={style}
        ref={ref}
        onClick={activate}
        onContextMenu={e => {
            if (props.onContextMenu) {
                activate(e);
                props.onContextMenu(e);
            }
        }}
        onDoubleClick={toggleOpen}
        {...extractDataTags(props)}
    >
        <Flex
            data-component={"list-row"}
            alignItems={"center"}
            gap={"8px"}
            padding={"5px"}
            minHeight={props.slim ? "24px" : "48px"}
        >
            {props.children == null ?
                null :
                <Icon
                    data-chevron={"true"}
                    color="textPrimary"
                    size={15}
                    name="heroChevronDown"
                    className={"open-chevron"}
                    cursor={"pointer"}
                    onClick={toggleOpen}
                />
            }
            <Flex alignItems={"center"} flexGrow={1}>{props.left}</Flex>
            {props.right}
        </Flex>

        <div className={TreeNodeChildren}>
            {props.children}
        </div>
    </div>;
};
