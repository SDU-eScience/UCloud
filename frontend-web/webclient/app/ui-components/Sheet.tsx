import * as React from "react";
import {useEffect, useLayoutEffect, useMemo, useRef} from "react";
import {doNothing} from "@/UtilityFunctions";
import styled from "styled-components";
import Icon, {IconName} from "@/ui-components/Icon";
import {ListRow, ListRowStat} from "@/ui-components/List";
import {DropdownContent} from "@/ui-components/Dropdown";
import sdLoader from "@/Assets/Images/sd-loader.png";
import {Operation, Operations, useOperationOpener} from "@/ui-components/Operation";
import {ThemeColor} from "@/ui-components/theme";
import {TooltipContent} from "@/ui-components/Tooltip";

export type Cell = StaticCell | DropdownCell | TextCell | FuzzyCell;

interface CellBase {
    width?: string;
    activate: (sheet: SheetRenderer, column: number, row: number, columnEnd: number, rowEnd: number, key?: string) => void;
}

type StaticCellContent = string | {
    contents: string;
    color?: ThemeColor;
    tooltip?: string;
};

interface StaticCell extends CellBase {
    type: "static";
    possibleIcons?: IconName[];
    iconMode?: boolean;
    textFn: (sheetId: string, coordinates: CellCoordinates) => StaticCellContent;
}

export function StaticCell(
    textFn: string | ((sheetId: string, coordinates: CellCoordinates) => StaticCellContent),
    opts?: Partial<StaticCell>
): StaticCell {
    return {
        type: "static",
        textFn: typeof textFn === "string" ? () => textFn : textFn,
        activate: doNothing,
        ...opts
    };
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
        activate: (sheet, column, row) => {
            const anchor = sheet.findTableCell(column, row);
            anchor?.click();
        },
        ...opts,
    };
}

interface TextCell extends CellBase {
    type: "text";
    placeholder?: string;
    fieldType: "text" | "date";
}

export function TextCell(placeholder?: string, opts?: Partial<TextCell>): TextCell {
    return {
        type: "text",
        placeholder,
        activate: (sheet, column, row, columnEnd, rowEnd, key) => {
            if (column === columnEnd && row === rowEnd) {
                const cell = sheet.findTableCell(column, row)?.querySelector("input") as HTMLInputElement;
                if (!cell) return;
                cell.focus(); // key is entered into the element by focusing it during the event handler
                cell.select();
            } else {
                if (key === "Delete" || key === "Backspace") {
                    for (let y = Math.min(row, rowEnd); y <= Math.max(row, rowEnd); y++) {
                        for (let x = Math.min(column, columnEnd); x <= Math.max(column, columnEnd); x++) {
                            const cell = sheet.findTableCell(x, y)?.querySelector("input") as HTMLInputElement;
                            if (cell) cell.value = "";
                        }
                    }
                }
            }
        },
        fieldType: "text",
        ...opts
    }
}

interface FuzzyCell extends CellBase {
    type: "fuzzy";
    placeholder?: string;
    suggestions: (query: string, column: number, row: number) => DropdownOption[];
}

export function FuzzyCell(
    suggestions: (query: string, column: number, row: number) => DropdownOption[],
    placeholder?: string,
    opts?: Partial<FuzzyCell>
): FuzzyCell {
    return {
        type: "fuzzy",
        placeholder,
        suggestions,
        activate: (sheetId, column, row, columnEnd, rowEnd, key) => {
            if (column === columnEnd && row === rowEnd) {
                const cell = sheetId.findTableCell(column, row)?.querySelector("input");
                if (!cell) return;
                cell.focus(); // key is entered into the element by focusing it during the event handler
                cell.select();
            } else {
                if (key === "Delete" || key === "Backspace") {
                    for (let y = Math.min(row, rowEnd); y <= Math.max(row, rowEnd); y++) {
                        for (let x = Math.min(column, columnEnd); x <= Math.max(column, columnEnd); x++) {
                            const cell = sheetId.findTableCell(x, y)?.querySelector("input");
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

interface SheetCallbacks {
    sheet: SheetRenderer;
}

interface SheetProps {
    header: string[];
    cells: Cell[];
    onRowUpdated: (rowId: string, row: number, values: (string | null)[]) => void;
    renderer: React.MutableRefObject<SheetRenderer | null>;
    newRowPrefix: string;
    onRowDeleted: (rowId: string) => void;
}

export const Sheet: React.FunctionComponent<SheetProps> = props => {
    if (props.header.length !== props.cells.length) {
        throw `header.length and cells.length are not equal (${props.header.length} != ${props.cells.length})`;
    }

    const [opRef, onContextMenu] = useOperationOpener();
    const sheetId = useMemo(() => "sheet-" + Math.ceil(Math.random() * 1000000000).toString(), []);
    const renderer = useMemo(() => new SheetRenderer(0, sheetId, props.cells), [props.cells]);
    useLayoutEffect(() => {
        renderer.mount();
        props.renderer.current = renderer;
        renderer.onContextMenu = onContextMenu;

        return () => {
            renderer.unmount();
        }
    }, [renderer, props.renderer]);

    useEffect(() => {
        renderer.onRowUpdated = props.onRowUpdated;
    }, [renderer, props.onRowUpdated]);

    const counter = useRef(0);
    const callbacks = useMemo(() => ({sheet: renderer}), [renderer]);
    const dropdownOptions = useMemo<Operation<never, SheetCallbacks>[]>(() => [
        {
            text: "Insert row",
            icon: "upload",
            enabled: () => true,
            onClick: (_, cb) => {
                const sheet = cb.sheet;
                sheet.addRow(`${props.newRowPrefix}-${counter.current++}`, sheet.contextMenuTriggeredBy + 1);
            }
        },
        {
            text: "Insert 50 rows",
            icon: "uploadFolder",
            enabled: () => true,
            onClick: (_, cb) => {
                for (let i = 0; i < 50; i++) {
                    cb.sheet.addRow(`${props.newRowPrefix}-${counter.current++}`);
                }
            }
        },
        {
            text: "Clone",
            icon: "copy",
            enabled: () => true,
            onClick: (_, cb) => {
                const sheet = cb.sheet;
                const rowNumber = sheet.contextMenuTriggeredBy + 1;
                console.log("rowNumber", rowNumber);
                sheet.addRow(`${props.newRowPrefix}-${counter.current++}`, sheet.contextMenuTriggeredBy + 1);
                for (let col = 0; col < sheet.cells.length; col++) {
                    const value = sheet.readValue(col, sheet.contextMenuTriggeredBy);
                    if (value != null) sheet.writeValue(col, rowNumber, value);
                }
            }
        },
        {
            text: "Delete",
            icon: "trash",
            confirm: true,
            color: "red",
            enabled: () => true,
            onClick: (_, cb) => {
                const sheet = cb.sheet;
                const rowToDelete = sheet.contextMenuTriggeredBy;
                const rowId = sheet.retrieveRowId(rowToDelete);
                if (rowId != null) {
                    sheet.removeRow(rowToDelete);
                    props.onRowDeleted(rowId);
                }
            }
        }
    ], [props.newRowPrefix, props.onRowDeleted]);

    const requiredIcons: IconName[] = useMemo(() => {
        const result: IconName[] = [];
        result.push("chevronDown");
        for (const cell of props.cells) {
            switch (cell.type) {
                case "static": {
                    const icons = cell.possibleIcons;
                    if (icons) {
                        for (const icon of icons) result.push(icon);
                    }
                    break;
                }
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
        <div id={sheetId + "-templates"}>
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

            <Template className={"validation-valid"}>
                <Icon size={"20px"} color={"green"} name={"check"} className={"validation"}/>
            </Template>

            <Template className={"validation-invalid"}>
                <Icon size={"20px"} color={"red"} name={"close"} className={"validation"}/>
            </Template>

            <Template className={"validation-warning"}>
                <Icon size={"20px"} color={"orange"} name={"warning"} className={"validation"}/>
            </Template>

            <Template className={"validation-loading"}>
                {/* NOTE(Dan): We use this static loader, instead of the normal one, for performance reasons.
                    It makes a _very_ significant difference. Going from completely locking up the UI
                    (< 5 frames/minutes) to being actually usable (> 30 FPS)
                */}
                <img alt={"Loading..."} src={sdLoader} width={"20px"} height={"20px"} className={"spin validation"}/>
            </Template>
        </div>

        <StyledSheet id={sheetId}>
            <thead>
            <tr>
                {props.header.map((key, idx) => <th key={idx}>{key}</th>)}
            </tr>
            </thead>
            <tbody>
            </tbody>
        </StyledSheet>

        <div id={`${sheetId}-portal`}/>

        <Operations
            openFnRef={opRef}
            location={"TOPBAR"}
            hidden
            operations={dropdownOptions}
            extra={callbacks}
            selected={[]}
            entityNameSingular={""}
        />

        <TooltipContent id={`${sheetId}-tooltip`} textAlign={"left"} />
    </>;
};

// <template> doesn't work with JSX (it doesn't put the nodes in the content as it should)
const Template = styled.div`
  display: none;
`;

type ValidationState = "valid" | "invalid" | "warning" | "loading";

export class SheetRenderer {
    private rows: number;
    public readonly sheetId: string;
    public readonly cells: Cell[];

    public get numberOfRows(): number {
        return this.rows;
    }

    private cellStartX: number = -1;
    private cellEndX: number = -1;
    private cellStartY: number = -1;
    private cellEndY: number = -1;
    private table: HTMLTableElement;
    private body: HTMLTableSectionElement;
    private portal: HTMLDivElement;
    private templates: HTMLDivElement;
    private tooltip: HTMLDivElement;

    private clickHandler: (ev) => void = doNothing;
    private keyHandler: (ev) => void = doNothing;

    private dropdownCoordinates: CellCoordinates | null = null;
    private dropdownOpen = false;
    private dropdownCell: DropdownOption[] | null = null;
    private dropdownEntry = 0;

    private clipboard: string[][] | null = null;

    private validation: Record<string, ValidationState> = {};
    public onContextMenu: (ev) => void = doNothing;

    public onRowUpdated: (rowId: string, row: number, values: (string | null)[]) => void = doNothing;

    public contextMenuTriggeredBy: number = -1;

    constructor(rows: number, sheetId: string, cells: Cell[]) {
        this.rows = rows;
        this.sheetId = sheetId;
        this.cells = cells;
    }

    registerValidation(column: number, row: number, validationState?: ValidationState) {
        const key = `${column},${row}`;
        if (validationState) {
            this.validation[key] = validationState;
        } else {
            delete this.validation[key];
        }

        const cell = this.findTableCell(column, row);
        if (!cell) return;
        cell.querySelector(".validation")?.remove();
        if (validationState) {
            cell.append(...this.cloneTemplate(".validation-" + validationState));
        }
    }

    findTableCell(column: number, row: number): HTMLTableCellElement | null {
        return this.body.children.item(row)?.children?.item(column) as HTMLTableCellElement ?? null;
    }

    public readValue(column: number, row: number): string | null {
        return this.findTableCell(column, row)?.querySelector("input")?.value ?? null;
    }

    public writeValue(column: number, row: number, value: string | undefined) {
        if (column < 0) throw "column is negative";
        if (row < 0) throw "row is negative";
        if (column >= this.cells.length) throw "column is out-of-bounds";
        if (row >= this.rows) throw "row is out-of-bounds";

        const cell = this.cells[column];
        const element = this.findTableCell(column, row)!;
        SheetRenderer.removeChildren(element);
        this.renderCell(cell, element, row, column, value);
        if (value !== undefined) this.writeOriginalValue(element, value);
    }

    private runRowUpdateHandler(row: number) {
        const cols = this.cells.length;
        let dirty = false;
        const rowId = this.body.rows.item(row)?.getAttribute("data-row-id");
        if (!rowId) return;

        const values: (string | null)[] = [];
        for (let col = 0; col < cols; col++) {
            const cell = this.findTableCell(col, row);
            if (!cell) return;

            const originalValue = cell.getAttribute("data-original-value") ?? null;
            const currentValue = this.readValue(col, row);
            values.push(currentValue);
            if (originalValue !== currentValue) {
                if (!(currentValue === "" && originalValue === null)) {
                    dirty = true;
                    this.writeOriginalValue(cell, currentValue);
                }
            }
        }

        if (dirty) {
            this.onRowUpdated(rowId, row, values);
        }
    }

    private writeOriginalValue(element: HTMLTableCellElement, value: string | null) {
        if (value) {
            element.setAttribute("data-original-value", value);
        } else {
            element.removeAttribute("data-original-value");
        }
    }

    mount() {
        const table = document.querySelector<HTMLTableElement>(`#${this.sheetId}`);
        if (!table) throw "Could not render sheet, unknown mount-point: " + this.sheetId;
        const body = table.querySelector("tbody");
        if (!body) throw "Could not render sheet, unknown mount-point: " + this.sheetId;
        const portal = document.querySelector<HTMLDivElement>(`#${this.sheetId}-portal`);
        if (!portal) throw "Could not render sheet, unknown mount-point: " + this.sheetId;
        const templates = document.querySelector<HTMLDivElement>(`#${this.sheetId}-templates`);
        if (!templates) throw "Could not render sheet, unknown mount-point: " + this.sheetId;
        const tooltip = document.querySelector<HTMLDivElement>(`#${this.sheetId}-tooltip`);
        if (!tooltip) throw "Could not render sheet, unknown mount-point: " + this.sheetId;

        this.table = table;
        this.body = body;
        this.portal = portal;
        this.templates = templates;
        this.tooltip = tooltip;
        this.rows = 0;

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

                    case "Home":
                        ev.preventDefault();
                        this.dropdownSelectEntry(0);
                        break;

                    case "End":
                        ev.preventDefault();
                        this.dropdownSelectEntry(-1);
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
                                const cell = this.readValue(x, y);
                                copiedRow.push(cell ?? "");
                            }
                            copiedData.push(copiedRow);
                        }
                        this.clipboard = copiedData;
                    } else if ((ev.metaKey || ev.ctrlKey) && ev.key === "v") {
                        if (!hasAnyField) return;
                        const clipboard = this.clipboard;
                        if (!clipboard) return;

                        {
                            // NOTE(Dan): correct ordering is required for the copy & paste
                            let tmp: number = 0;
                            if (endX < initialX) {
                                tmp = initialX;
                                initialX = endX;
                                endX = tmp;
                            }

                            if (endY < initialY) {
                                tmp = initialY;
                                initialY = endY;
                                endY = tmp;
                            }
                        }

                        const timesToRepeat = Math.max(1, Math.floor((endY - initialY + 1) / clipboard.length));
                        for (let iteration = 0; iteration < timesToRepeat; iteration++) {
                            let rowStart = initialY + (iteration * clipboard.length);
                            for (let y = rowStart; y < Math.min(rowStart + clipboard.length, this.rows); y++) {
                                const clipboardRow = clipboard[y - rowStart];
                                for (let x = initialX; x < Math.min(initialX + clipboardRow.length, this.cells.length - 1); x++) {
                                    const parentNode = this.findTableCell(x, y)!;
                                    SheetRenderer.removeChildren(parentNode);
                                    this.renderCell(this.cells[x], parentNode, y, x,
                                        clipboardRow[x - initialX]);
                                }
                                this.runRowUpdateHandler(y);
                            }
                        }
                    } else if (!hasInput) {
                        if (ev.code === "ShiftLeft" || ev.code === "ShiftRight" || ev.code === "Escape" ||
                            ev.code === "ControlLeft" || ev.code === "ControlRight") return;

                        // NOTE(Dan): This should activate the current cell, if any
                        if (!hasAnyField) return;

                        const cellType = this.cells[initialX];
                        cellType.activate(this, initialX, initialY, endX, endY, ev.code);
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

    clear() {
        this.rows = 0;
        this.dropdownEntry = -1;
        this.dropdownCell = null;
        this.dropdownCoordinates = null;
        this.dropdownOpen = false;
        SheetRenderer.removeChildren(this.body);
    }

    retrieveRowNumber(rowId: string): number | null {
        const rows = this.body.children;
        for (let i = 0; i < rows.length; i++) {
            const row = rows[i];
            if (row.getAttribute("data-row-id") === rowId) {
                return i;
            }
        }
        return null;
    }

    retrieveRowId(row: number): string | null {
        return this.body.children[row]?.getAttribute("data-row-id") ?? null;
    }

    addRow(rowId: string, index?: number) {
        const newRow = this.body.insertRow(index ?? -1);
        newRow.oncontextmenu = ev => {
            const rowNumber = this.retrieveRowNumber(rowId);
            if (rowNumber !== null) {
                this.contextMenuTriggeredBy = rowNumber;
                this.onContextMenu(ev);
            }
        };
        newRow.setAttribute("data-row-id", rowId);
        for (let col = 0; col < this.cells.length; col++) {
            const cell = this.cells[col];
            const cellElement = document.createElement("td");
            if (cell.width) {
                cellElement.style.width = cell.width;
            }
            newRow.appendChild(cellElement);

            this.renderCell(cell, cellElement, this.rows, col);
        }
        this.rows++;
    }

    removeRow(row: number) {
        this.body.children[row]?.remove();
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
        element.onclick = (ev) => {
            this.cellStartX = this.cellEndX = column;
            this.cellStartY = this.cellEndY = row;
            this.markActiveCells();
        };

        switch (cell.type) {
            case "text": {
                const input = document.createElement("input");
                input.type = cell.fieldType;
                if (cell.placeholder) input.placeholder = cell.placeholder;
                if (value) input.value = value;
                input.onblur = () => this.runRowUpdateHandler(row);
                if (cell.fieldType != "date") {
                    input.onclick = (ev) => {
                        ev.preventDefault();
                        input.blur();
                    };
                    input.ondblclick = (ev) => input.focus();
                }
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
                    input.setAttribute("type", "hidden");
                    if (value) input.value = value;
                    else input.value = cell.options[0].value;
                    input.onblur = () => this.runRowUpdateHandler(row);
                    element.appendChild(input);
                }

                element.onclick = (ev) => {
                    ev.stopPropagation();
                    SheetRenderer.removeChildren(this.portal);
                    this.cellStartX = this.cellEndX = column;
                    this.cellStartY = this.cellEndY = row;
                    this.markActiveCells();
                    this.renderDropdownMenu(element, cell.options, column, row);
                };
                break;
            }
            case "fuzzy": {
                const input = document.createElement("input");
                element.appendChild(input);

                if (cell.placeholder) input.placeholder = cell.placeholder;
                if (value) input.value = value;
                input.onblur = () => this.runRowUpdateHandler(row);
                input.onclick = (ev) => {
                    ev.preventDefault();
                    input.blur();
                };
                input.ondblclick = (ev) => input.focus();

                input.oninput = (ev) => {
                    ev.stopPropagation();
                    SheetRenderer.removeChildren(this.portal);

                    const suggestions = cell.suggestions(input.value, column, row);
                    this.renderDropdownMenu(element, suggestions, column, row);
                };

                break;
            }
            case "static": {
                const fnReturn = cell.textFn(this.sheetId, {column, row});
                const content: StaticCellContent = typeof fnReturn === "string" ?
                    { contents: fnReturn } : fnReturn;

                let domElement: HTMLElement;
                if (cell.iconMode === true) {
                    domElement = this.cloneTemplate(".icon-" + content.contents)[0] as HTMLElement;
                } else {
                    domElement = document.createElement("i");
                    domElement.textContent = content.contents;
                }

                if (content.color !== undefined) domElement.style.color = `var(--${content.color})`;
                if (content.tooltip) {
                    const tooltipContent = document.createElement("p");
                    tooltipContent.textContent = content.tooltip;
                    this.attachTooltip(domElement, tooltipContent);
                }

                element.appendChild(domElement);
                break;
            }
        }
    }

    private renderDropdownMenu(anchor: Element, options: DropdownOption[], column: number, row: number) {
        const container = this.cloneTemplate(".dropdown-container")[0];
        {
            // Create container for dropdown
            this.portal.appendChild(container);
            const boundingClientRect = anchor.getBoundingClientRect();
            this.portal.style.position = "fixed";
            this.portal.style.top = (boundingClientRect.top + 30) + "px";
            this.portal.style.left = boundingClientRect.left + "px";
        }

        {
            // Add rows to the container
            let optIdx = 0;
            for (const opt of options) {
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
            this.dropdownCell = options;
            this.dropdownCoordinates = {row, column};
            this.dropdownOpen = true;
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
        const options = this.dropdownCell;
        if (!this.dropdownOpen) return;
        if (!options) return;
        const coord = this.dropdownCoordinates;
        if (!coord) return;
        if (this.dropdownEntry < 0 || this.dropdownEntry >= options.length) {
            this.closeDropdown();
            return;
        }

        const tableCell = this.body.children.item(coord.row)?.children.item(coord.column)! as HTMLTableCellElement;
        SheetRenderer.removeChildren(tableCell);
        this.renderCell(this.cells[coord.column], tableCell, coord.row, coord.column, options[this.dropdownEntry].value);
        this.closeDropdown();
        this.runRowUpdateHandler(coord.row);
    }

    private static removeChildren(element: Element) {
        Array.from(element.children).forEach(it => it.remove());
    }

    private dropdownSelectEntry(entry: number) {
        const options = this.dropdownCell;
        if (!this.dropdownOpen) return;
        if (!options) return;
        if (entry < 0) {
            entry = options.length - 1;
        }
        if (entry >= options.length) {
            entry = 0;
        }

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
        const iconTemplate = this.templates.querySelector<HTMLDivElement>(template);
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

    private attachTooltip(anchor: HTMLElement, content: HTMLElement) {
        anchor.onmouseenter = () => {
            SheetRenderer.removeChildren(this.tooltip);
            this.tooltip.appendChild(content);
            this.tooltip.style.opacity = "1";
        };

        anchor.onmouseleave = () => {
            this.tooltip.style.opacity = "0";
        };

        anchor.onmousemove = (ev) => {
            const style = this.tooltip.style;
            style.left = ev.clientX + "px";
            style.top = ev.clientY + "px";
        };
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

  td {
    position: relative;
  }

  .validation {
    position: absolute;
    right: 10px;
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
  
  a {
    color: var(--blue);
  }

  .spin {
    animation: sheetSpin 4s infinite linear;
  }

  @keyframes sheetSpin {
    from {
      transform: rotate(0deg);
    }
    to {
      transform: rotate(360deg);
    }
  }

  [type="date"] {
    cursor: text;
  }

  [type="date"] ~ .validation {
    right: 30px;
  }

  input {
    cursor: default;
  }
`;
