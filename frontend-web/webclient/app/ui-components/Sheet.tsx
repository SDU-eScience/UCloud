import * as React from "react";
import {useEffect, useLayoutEffect, useMemo} from "react";
import {doNothing} from "@/UtilityFunctions";
import styled from "styled-components";
import Icon, {IconName} from "@/ui-components/Icon";
import {ListRow, ListRowStat} from "@/ui-components/List";
import {DropdownContent} from "@/ui-components/Dropdown";

export type Cell = StaticCell | DropdownCell | TextCell;

interface CellBase {
    width?: string;
    activate: (sheetId: string, column: number, row: number, columnEnd: number, rowEnd: number, key?: string) => void;
}

interface StaticCell extends CellBase {
    type: "static";
    textFn: (sheetId: string, coordinates: CellCoordinates) => string;
}

export function StaticCell(textFn: string | ((sheetId: string, coordinates: CellCoordinates) => string), opts?: Partial<StaticCell>): StaticCell {
    return {
        type: "static",
        textFn: typeof textFn === "string" ? () => textFn : textFn,
        activate: (sheetId, column, row, key) => {
            // Do nothing
        },
        ...opts
    }
}

interface DropdownOption {
    value: string;
    icon: IconName;
    title: string;
    helpText?: string;
}

interface DropdownCell extends CellBase {
    type: "dropdown";
    options: DropdownOption[]
}

export function DropdownCell(options: DropdownOption[], opts?: Partial<DropdownCell>): DropdownCell {
    return {
        type: "dropdown",
        options,
        activate: (sheetId, column, row, key) => {
            const fnKey = id(sheetId, column, row);
            const anchor = document.querySelector<HTMLElement>("#" + fnKey);
            anchor?.click();
        },
        ...opts,
    };
}

function id(sheetId: string, column: number, row: number): string {
    return `${sheetId}-${column}-${row}`;
}

interface TextCell extends CellBase {
    type: "text";
    placeholder?: string;
}

export function TextCell(placeholder?: string, opts?: Partial<TextCell>): TextCell {
    return {
        type: "text",
        placeholder,
        activate: (sheetId, column, row, columnEnd, rowEnd, key) => {
            if (column === columnEnd && row === rowEnd) {
                const cell = document.querySelector<HTMLInputElement>(`#${id(sheetId, column, row)}`);
                if (!cell) return;
                cell.focus(); // key is entered into the element by focusing it during the event handler
                cell.select();
            } else {
                if (key === "Delete" || key === "Backspace") {
                    for (let y = Math.min(row, rowEnd); y <= Math.max(row, rowEnd); y++) {
                        for (let x = Math.min(column, columnEnd); x <= Math.max(column, columnEnd); x++) {
                            const cell = document.querySelector<HTMLInputElement>(`#${id(sheetId, x, y)}`);
                            if (cell) cell.value = "";
                        }
                    }
                }
            }
        },
        ...opts
    }
}

export interface CellCoordinates {
    column: number;
    row: number;
}

interface SheetProps {
    header: string[];
    cells: Cell[];
    rows: number;
    onRowUpdated: (row: number) => void;
    renderer: React.MutableRefObject<SheetRenderer | null>;
}

export const Sheet: React.FunctionComponent<SheetProps> = props => {
    if (props.header.length !== props.cells.length) {
        throw `header.length and cells.length are not equal (${props.header.length} != ${props.cells.length})`;
    }

    if (props.rows < 0) {
        throw `props.rows < 0 (${props.rows} < 0)`;
    }

    const sheetId = useMemo(() => "sheet-" + Math.ceil(Math.random() * 1000000000).toString(), []);
    const renderer = useMemo(() => new SheetRenderer(props.rows, sheetId, props.cells), [props.cells]);
    useLayoutEffect(() => {
        renderer.mount();
        props.renderer.current = renderer;

        return () => {
            renderer.unmount();
        }
    }, [renderer, props.renderer]);

    useEffect(() => {
        renderer.onRowUpdated = props.onRowUpdated;
    }, [renderer, props.onRowUpdated]);

    const requiredIcons: IconName[] = useMemo(() => {
        const result: IconName[] = [];
        result.push("chevronDown");
        for (const cell of props.cells) {
            switch (cell.type) {
                case "dropdown": {
                    for (const opt of cell.options) {
                        result.push(opt.icon);
                    }
                    break;
                }

                default: {
                    // Do nothing
                    break;
                }
            }
        }
        return result;
    }, [props.cells]);

    return <>
        <StyledSheet id={sheetId}>
            {requiredIcons.map(icon => (
                <Template key={icon} className={"icon-" + icon}>
                    <Icon name={icon} size={"20px"} color={"iconColor"} color2={"iconColor2"}/>
                </Template>
            ))}

            <Template className={"dropdown-row"}>
                <ListRow
                    fontSize={"16px"}
                    icon={<div className={"icon"}/>}
                    left={<div className={"left"}/>}
                    leftSub={<ListRowStat>
                        <div className={"help"}/>
                    </ListRowStat>}
                    right={null}
                />
            </Template>

            <Template className={"dropdown-container"}>
                <DropdownContent
                    overflow={"visible"}
                    squareTop={false}
                    cursor="pointer"
                    fixed={true}
                    width={"300px"}
                    hover={false}
                    visible={true}
                    paddingControlledByContent
                />
            </Template>

            <thead>
            <tr>
                {props.header.map((key, idx) => <th key={idx}>{key}</th>)}
            </tr>
            </thead>
            <tbody>
            </tbody>
        </StyledSheet>

        <div id={`${sheetId}-portal`}/>
    </>;
};

// <template> doesn't work with JSX (it doesn't put the nodes in the content as it should)
const Template = styled.div`
  display: none;
`;

export class SheetRenderer {
    private rows: number;
    public readonly sheetId: string;
    private readonly cells: Cell[];

    private cellStartX: number = -1;
    private cellEndX: number = -1;
    private cellStartY: number = -1;
    private cellEndY: number = -1;
    private table: HTMLTableElement;
    private body: HTMLTableSectionElement;
    private portal: HTMLDivElement;

    private clickHandler: (ev) => void = doNothing;
    private keyHandler: (ev) => void = doNothing;

    private dropdownCoordinates: CellCoordinates | null = null;
    private dropdownOpen = false;
    private dropdownCell: DropdownCell | null = null;
    private dropdownEntry = 0;

    private clipboard: string[][] | null = null;

    public onRowUpdated: (row: number) => void = doNothing;

    constructor(rows: number, sheetId: string, cells: Cell[]) {
        this.rows = rows;
        this.sheetId = sheetId;
        this.cells = cells;
    }

    mount() {
        const table = document.querySelector<HTMLTableElement>(`#${this.sheetId}`);
        if (!table) throw "Could not render sheet, unknown mount-point: " + this.sheetId;
        const body = table.querySelector("tbody");
        if (!body) throw "Could not render sheet, unknown mount-point: " + this.sheetId;
        const portal = document.querySelector<HTMLDivElement>(`#${this.sheetId}-portal`);
        if (!portal) throw "Could not render sheet, unknown mount-point: " + this.sheetId;

        this.table = table;
        this.body = body;
        this.portal = portal;
        const rowCount = this.rows;
        this.rows = 0;
        for (let i = 0; i < rowCount; i++) {
            this.addRow();
        }

        this.keyHandler = (ev: KeyboardEvent) => {
            if (this.dropdownOpen) {
                switch (ev.code) {
                    case "Escape":
                        this.closeDropdown();
                        break;

                    case "ArrowUp":
                        ev.preventDefault();
                        this.dropdownSelectEntry(this.dropdownEntry - 1);
                        break;

                    case "ArrowDown":
                        ev.preventDefault();
                        this.dropdownSelectEntry(this.dropdownEntry + 1);
                        break;

                    case "Enter":
                        ev.preventDefault();
                        this.dropdownConfirmChoice();
                        break;
                }
            } else {
                // NOTE(Dan): If a dropdown is not open, then the input will either go to the active input field or
                // it will go to the sheet. We detect if an input currently has focus by querying the document.
                const activeInput = document.querySelector<HTMLInputElement>("input:focus");
                if (activeInput && ev.code !== "ArrowUp" && ev.code !== "ArrowDown") {
                    // NOTE(Dan): escape blurs the field, giving control back to the sheet
                    if (ev.code === "Escape" || ev.code == "NumpadEnter" || ev.code === "Enter") activeInput.blur();
                    if (ev.code === "NumpadEnter" || ev.code === "Enter") {
                        const x = this.cellStartX;
                        const y = this.cellStartY;
                        if (x != -1 && y != -1 && y < this.rows - 1) {
                            this.cellStartY = y + 1;
                            this.cellEndY = this.cellStartY;
                            this.cellEndX = this.cellStartX;
                            this.markActiveCells();
                        }
                    }
                } else {
                    if (activeInput) activeInput.blur();

                    let dx = ev.code === "ArrowLeft" ? -1 : ev.code === "ArrowRight" ? 1 : 0;
                    let dy = ev.code === "ArrowUp" ? -1 : ev.code === "ArrowDown" ? 1 : 0;
                    const goToStart = ev.code === "Home";
                    const goToEnd = ev.code === "End";

                    const hasInput = dx != 0 || dy != 0 || goToStart || goToEnd;

                    let initialX = -1;
                    let initialY = -1;

                    if (this.cellStartX != -1) initialX = this.cellStartX;
                    if (this.cellStartY != -1) initialY = this.cellStartY;
                    let endX = this.cellEndX != -1 ? this.cellEndX : initialX;
                    let endY = this.cellEndY != -1 ? this.cellEndY : initialY;

                    const hasAnyField = initialX != -1 && initialY != -1;
                    if ((ev.metaKey || ev.ctrlKey) && ev.key === "a") {
                        ev.preventDefault();
                        this.cellStartX = 0;
                        this.cellStartY = 0;
                        this.cellEndX = this.cells.length - 1;
                        this.cellEndY = this.rows - 1;
                        this.markActiveCells();
                    } else if ((ev.metaKey || ev.ctrlKey) && ev.key === "c") {
                        if (!hasAnyField) return;
                        const copiedData: string[][] = [];
                        for (let y = Math.min(initialY, endY); y <= Math.max(initialY, endY); y++) {
                            const copiedRow: string[] = [];
                            for (let x = Math.min(initialX, endX); x <= Math.max(initialX, endX); x++) {
                                const cell = document.querySelector<HTMLInputElement>(`#${id(this.sheetId, x, y)}`);
                                copiedRow.push(cell?.value ?? "");
                            }
                            copiedData.push(copiedRow);
                        }
                        this.clipboard = copiedData;
                    } else if ((ev.metaKey || ev.ctrlKey) && ev.key === "v") {
                        if (!hasAnyField) return;
                        const clipboard = this.clipboard;
                        if (!clipboard) return;

                        const timesToRepeat = Math.max(1, Math.floor((endY - initialY + 1) / clipboard.length));
                        for (let iteration = 0; iteration < timesToRepeat; iteration++) {
                            let rowStart = initialY + (iteration * clipboard.length);
                            for (let y = rowStart; y < Math.min(rowStart + clipboard.length, this.rows - 1); y++) {
                                const clipboardRow = clipboard[y - rowStart];
                                for (let x = initialX; x < Math.min(initialX + clipboardRow.length, this.cells.length - 1); x++) {
                                    const cell = document.querySelector<HTMLInputElement>(`#${id(this.sheetId, x, y)}`);
                                    const parentNode = cell?.parentNode! as HTMLTableCellElement;
                                    SheetRenderer.removeChildren(parentNode);
                                    this.renderCell(this.cells[x], parentNode, y, x,
                                        clipboardRow[x - initialX]);
                                }
                                this.onRowUpdated(y);
                            }
                        }
                    } else if (!hasInput) {
                        if (ev.code === "ShiftLeft" || ev.code === "ShiftRight" || ev.code === "Escape" ||
                            ev.code === "ControlLeft" || ev.code === "ControlRight") return;

                        // NOTE(Dan): This should activate the current cell, if any
                        if (!hasAnyField) return;

                        const cellType = this.cells[initialX];
                        cellType.activate(this.sheetId, initialX, initialY, endX, endY, ev.code);
                    } else {
                        ev.preventDefault();
                        if (!hasAnyField) {
                            // NOTE(Dan): If no fields are active, then pretend that the user has just selected the first
                            // element.

                            initialX = 0;
                            initialY = 0;
                            endX = 0;
                            endY = 0;

                            if (goToEnd) {
                                initialX = this.cells.length;
                            }
                        } else {
                            // NOTE(Dan): We only apply movement if some fields were previously selected
                            if (ev.shiftKey) {
                                if (goToStart) {
                                    endX = 0;
                                } else if (goToEnd) {
                                    endX = this.cells.length;
                                } else {
                                    endX = Math.max(0, Math.min(this.cells.length - 1, endX + dx));
                                    endY = Math.max(0, Math.min(this.rows, endY + dy));
                                }
                            } else {
                                if (goToStart) {
                                    initialX = 0;
                                } else if (goToEnd) {
                                    initialX = this.cells.length - 1;
                                } else {
                                    initialX = Math.max(0, Math.min(this.cells.length - 1, initialX + dx));
                                    initialY = Math.max(0, Math.min(this.rows, initialY + dy));
                                }
                                endX = initialX;
                                endY = initialY;
                            }
                        }

                        // NOTE(Dan): At this point we just need to dispatch to the renderer
                        this.cellStartX = initialX;
                        this.cellStartY = initialY;
                        this.cellEndX = endX;
                        this.cellEndY = endY;

                        this.markActiveCells();
                    }
                }
            }
        };

        this.clickHandler = (ev: MouseEvent) => {
            this.closeDropdown();
        };

        document.body.addEventListener("click", this.clickHandler);
        document.body.addEventListener("keydown", this.keyHandler);
    }

    private addRow() {
        const newRow = document.createElement("tr");
        for (let col = 0; col < this.cells.length; col++) {
            const cell = this.cells[col];
            const cellElement = document.createElement("td");
            if (cell.width) {
                cellElement.style.width = cell.width;
            }
            newRow.appendChild(cellElement);

            this.renderCell(cell, cellElement, this.rows, col);
        }
        this.body.append(newRow);
        this.rows++;
    }

    private markActiveCells() {
        const activeCells = this.table.querySelectorAll(".active");
        Array.from(activeCells).forEach(it => it.classList.remove("active", "primary"));
        if (this.cellStartY == -1 || this.cellStartX == -1) return;

        const startX = Math.min(this.cellStartX, this.cellEndX);
        const startY = Math.min(this.cellStartY, this.cellEndY);
        const endX = Math.max(this.cellStartX, this.cellEndX);
        const endY = Math.max(this.cellStartY, this.cellEndY);
        for (let row = startY; row <= endY; row++) {
            const rowElement = this.body.children[row];
            if (!rowElement) return;

            for (let col = startX; col <= endX; col++) {
                const cellElement = rowElement.children[col];
                if (!cellElement) break;
                cellElement.classList.add("active");
                if (col === startX && row === startY) {
                    cellElement.classList.add("primary");
                }
            }
        }
    }

    private renderCell(
        cell: Cell,
        element: HTMLTableCellElement,
        row: number,
        column: number,
        value?: string
    ) {
        const domId = id(this.sheetId, column, row);

        element.onclick = (ev) => {
            this.cellStartX = this.cellEndX = column;
            this.cellStartY = this.cellEndY = row;
            this.markActiveCells();
        };

        switch (cell.type) {
            case "text": {
                const input = document.createElement("input");
                input.id = domId;
                if (cell.placeholder) input.placeholder = cell.placeholder;
                if (value) input.value = value;
                input.onblur = () => this.onRowUpdated(row);
                input.onclick = (ev) => {
                    ev.preventDefault();
                    input.blur();
                };
                input.ondblclick = (ev) => input.focus();
                element.appendChild(input);
                break;
            }
            case "dropdown": {
                element.classList.add("pointer");

                let selectedIcon: IconName = cell.options[0].icon;
                if (value) {
                    const actualOpt = cell.options.find(it => it.value === value);
                    if (actualOpt) selectedIcon = actualOpt.icon;
                }

                this.renderDropdownTrigger(element, selectedIcon);

                {
                    // Create input field (for dropdown choice)
                    const input = document.createElement("input");
                    input.id = domId;
                    input.setAttribute("type", "hidden");
                    if (value) input.value = value;
                    else input.value = cell.options[0].value;
                    input.onblur = () => this.onRowUpdated(row);
                    element.appendChild(input);
                }

                element.onclick = (ev) => {
                    ev.stopPropagation();
                    SheetRenderer.removeChildren(this.portal);
                    this.cellStartX = this.cellEndX = column;
                    this.cellStartY = this.cellEndY = row;
                    this.markActiveCells();

                    const container = this.cloneTemplate(".dropdown-container")[0];
                    {
                        // Create container for dropdown
                        this.portal.appendChild(container);
                        const boundingClientRect = element.getBoundingClientRect();
                        this.portal.style.position = "fixed";
                        this.portal.style.top = (boundingClientRect.top + 30) + "px";
                        this.portal.style.left = boundingClientRect.left + "px";
                    }

                    {
                        // Add rows to the container
                        let optIdx = 0;
                        for (const opt of cell.options) {
                            const optElement = this.renderDropdownRow(container, opt);
                            const thisIdx = optIdx;
                            optElement.addEventListener("click", ev => {
                                this.dropdownEntry = thisIdx;
                                this.dropdownConfirmChoice();
                            });
                            optIdx++;
                        }
                    }

                    {
                        // Configure dropdown state
                        this.dropdownEntry = -1;
                        this.dropdownCell = cell;
                        this.dropdownCoordinates = {row, column};
                        this.dropdownOpen = true;
                    }
                };
                break;
            }
            case "static": {
                const italics = document.createElement("i");
                italics.textContent = cell.textFn(this.sheetId, {column: 0, row: 0});
                element.appendChild(italics);
                break;
            }
        }
    }

    private renderDropdownRow(container: Element, opt: DropdownOption): Element {
        const optElement = this.cloneTemplate(".dropdown-row")[0];
        container.appendChild(optElement);

        optElement.querySelector(".icon")!.append(...this.cloneTemplate(".icon-" + opt.icon));
        optElement.querySelector(".left")!.textContent = opt.title;
        if (opt.helpText) optElement.querySelector(".help")!.textContent = opt.helpText;

        return optElement;
    }

    private renderDropdownTrigger(parent: HTMLElement, icon: IconName) {
        parent.append(...this.cloneTemplate(".icon-" + icon));
        parent.append(...this.cloneTemplate(".icon-chevronDown"));
    }

    private dropdownConfirmChoice() {
        const cell = this.dropdownCell;
        if (!this.dropdownOpen) return;
        if (!cell) return;
        const coord = this.dropdownCoordinates;
        if (!coord) return;
        if (this.dropdownEntry < 0 || this.dropdownEntry >= cell.options.length) return;

        const tableCell = this.body.children.item(coord.row)?.children.item(coord.column)! as HTMLTableCellElement;
        SheetRenderer.removeChildren(tableCell);
        this.renderCell(cell, tableCell, coord.row, coord.column, cell.options[this.dropdownEntry].value);
        this.closeDropdown();
        this.onRowUpdated(coord.row);
    }

    private static removeChildren(element: Element) {
        Array.from(element.children).forEach(it => it.remove());
    }

    private dropdownSelectEntry(entry: number) {
        const cell = this.dropdownCell;
        if (!this.dropdownOpen) return;
        if (!cell) return;
        if (entry < 0) return;
        if (entry >= cell.options.length) return;

        this.dropdownEntry = entry;
        const container = this.portal.children.item(0);
        if (!container) return;

        const rows = Array.from(container.children);
        let idx = 0;
        for (const row of rows) {
            row.setAttribute("data-selected", idx === entry ? "true" : "false");
            idx++;
        }
    }

    private closeDropdown() {
        if (this.dropdownOpen) {
            Array.from(this.portal.children).forEach(it => it.remove());
            this.dropdownOpen = false;
        }
    }

    private cloneTemplate(template: string): Element[] {
        const iconTemplate = this.table.querySelector<HTMLDivElement>(template);
        if (!iconTemplate) throw "Unknown template: " + template;
        return [...(iconTemplate.cloneNode(true)["children"])];
    }

    unmount() {
        document.body.removeEventListener("keydown", this.keyHandler);
        document.body.removeEventListener("click", this.clickHandler);
        this.body.remove();
        const node = document.createElement("tbody");
        this.table.appendChild(node);
        this.body = node;
    }
}

const StyledSheet = styled.table`
  width: 100%;
  color: var(--text);
  position: relative;
  border-collapse: collapse;

  tr, td, th {
    border: 2px inset #eee;
    padding: 4px;
  }

  td.active, th.active {
    border: 2px solid var(--blue);
  }

  th {
    font-weight: bold;
    text-align: left;
    position: sticky;
    top: 0;
    box-shadow: 0 2px 2px -1px rgba(0, 0, 0, 0.4);
  }

  input {
    width: calc(100% + 8px);
    margin: -4px;
    border: 0;
    background: transparent;
    color: var(--text);
  }

  input:focus {
    border: 0;
    outline: none;
  }

  .pointer {
    cursor: pointer;
  }
`;