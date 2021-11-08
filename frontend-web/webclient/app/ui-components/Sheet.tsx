import * as React from "react";
import {doNothing} from "@/UtilityFunctions";
import styled from "styled-components";
import {useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState} from "react";
import Icon, {IconName} from "@/ui-components/Icon";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {ListRow, ListRowStat} from "@/ui-components/List";

export type Cell = StaticCell | DropdownCell | TextCell;

interface CellBase {
    width?: string;
    activate: (sheetId: string, column: number, row: number, key?: string) => void;
}

interface StaticCell extends CellBase {
    type: "static";
    textFn: (row: string[]) => string;
}

export function StaticCell(textFn: string | ((row: string[]) => string), opts?: Partial<StaticCell>): StaticCell {
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
            const fn = window[fnKey];
            const anchor = document.querySelector("#" + fnKey);
            if (fn && anchor) {
                const rect = anchor.getBoundingClientRect();
                fn(rect.left, rect.top + 30);
            }
        },
        ...opts,
    };
}

function id(sheetId: string, column: number, row: number): string {
    return `${sheetId}-${column}-${row}`;
}

interface TextCell extends CellBase {
    type: "text";
}

export function TextCell(opts?: Partial<TextCell>): TextCell {
    return {
        type: "text",
        activate: (sheetId, column, row, key) => {
            const cell = document.querySelector<HTMLInputElement>(`#${id(sheetId, column, row)}`);
            if (!cell) return;
            cell.focus(); // key is entered into the element by focusing it during the event handler
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
    selectedCell: CellCoordinates[];
    onCellSelected: (coordinate: CellCoordinates[]) => void;
    data: string[][];
    onDataUpdated: (newDate: string[][]) => void;
}

export const Sheet: React.FunctionComponent<SheetProps> = props => {
    const sheetId = useMemo(() => "sheet-" + Math.ceil(Math.random() * 1000000000).toString(), []);
    const selected: boolean[][] = useMemo(() => {
        const result: boolean[][] = [];
        for (let row = 0; row <= props.data.length; row++) {
            const newRow: boolean[] = [];
            for (let col = 0; col <= props.data[0].length; col++) {
                let isSelected = false;
                for (const sel of props.selectedCell) {
                    if (sel.row === row && sel.column === col) {
                        isSelected = true;
                        break;
                    }
                }
                newRow.push(isSelected);
            }
            result.push(newRow);
        }
        return result;
    }, [props.data.length, props.selectedCell]);

    const onCellSelected = useCallback((clear: boolean, coordinates: CellCoordinates[]) => {
        if (clear) {
            props.onCellSelected(coordinates);
        } else {
            props.onCellSelected([...props.selectedCell, ...coordinates]);
        }
    }, [props.selectedCell, props.onCellSelected]);

    useEffect(() => {
        const listener = (ev: KeyboardEvent) => {
            const hasInputFocused = document.querySelector("input:focus") != null;
            if (hasInputFocused) return;
            let dx = 0;
            let dy = 0;
            switch (ev.code) {
                case "ArrowLeft":
                    dx = -1;
                    break;
                case "ArrowRight":
                    dx = 1;
                    break;
                case "ArrowDown":
                    dy = 1;
                    break;
                case "ArrowUp":
                    dy = -1;
                    break;

                case "Home":
                case "End": {
                    const row = props.selectedCell.length > 0 ?
                        props.selectedCell[props.selectedCell.length - 1].row : 0;
                    const columns = props.cells.length;
                    const column = ev.code === "Home" ? 0 : columns - 1;

                    if (!ev.shiftKey) {
                        onCellSelected(true, [{column, row}]);
                    } else {
                        const startColumn = props.selectedCell.length > 0 ?
                            props.selectedCell[props.selectedCell.length - 1].column : 0;
                        const newCoordinates: CellCoordinates[] = [];

                        const min = startColumn > column ? column : startColumn;
                        const max = startColumn > column ? startColumn : column;
                        for (let i = min; i <= max; i++) {
                            newCoordinates.push({row, column: i});
                        }
                        onCellSelected(true, newCoordinates);
                    }
                    break;
                }

                case "Escape":
                    onCellSelected(true, []);
                    break;

                default: {
                    const isEnter = ev.code === "NumpadEnter" || ev.code === "Enter";
                    const baseCell = props.selectedCell.length > 0 ?
                        props.selectedCell[props.selectedCell.length - 1] : null;
                    if (!baseCell) return;
                    if (isEnter) {
                        props.cells[baseCell.column].activate(sheetId, baseCell.column, baseCell.row);
                    } else {
                        props.cells[baseCell.column].activate(sheetId, baseCell.column, baseCell.row, ev.code);
                    }
                    return;
                }
            }

            if (dx != 0 || dy != 0) {
                const baseCell = props.selectedCell.length > 0 ?
                    props.selectedCell[props.selectedCell.length - 1] : {column: 0, row: 0};
                const columns = props.cells.length;
                const rows = props.data.length;

                const newCoordinate: CellCoordinates = {column: baseCell.column + dx, row: baseCell.row + dy};
                if (newCoordinate.column >= 0 && newCoordinate.column < columns && newCoordinate.row >= 0 &&
                    newCoordinate.row < rows) {
                    onCellSelected(!ev.shiftKey, [newCoordinate]);
                }
            }
        };

        document.body.addEventListener("keydown", listener);
        return () => {
            document.body.removeEventListener("keydown", listener);
        };
    }, [props.selectedCell, onCellSelected]);

    return <StyledSheet>
        <thead>
        <tr>
            {props.header.map((key, idx) => <th key={idx}>{key}</th>)}
        </tr>
        </thead>
        <tbody>
        {props.data.map((row, idx) => (
            <RowRenderer key={idx} row={row} cells={props.cells} selected={selected[idx]} onDataUpdated={doNothing}
                         rowIdx={idx} onCellSelected={onCellSelected} sheetId={sheetId}/>
        ))}
        </tbody>
    </StyledSheet>;
};

const RowRenderer: React.FunctionComponent<{
    rowIdx: number;
    row: string[];
    cells: Cell[];
    onDataUpdated: (newRow: string[]) => void;
    selected: boolean[];
    onCellSelected: (clear: boolean, coordinate: CellCoordinates[]) => void;
    sheetId: string;
}> = props => {
    return <tr>
        {props.cells.map((cell, idx) => (
            <CellRenderer key={idx} cell={cell} coordinates={{row: props.rowIdx, column: idx}}
                          selected={props.selected[idx]} onCellSelected={props.onCellSelected} sheetId={props.sheetId}
                          row={props.row}/>
        ))}
    </tr>;
};

const CellRenderer: React.FunctionComponent<{
    coordinates: CellCoordinates;
    cell: Cell;
    selected: boolean;
    onCellSelected: (clear: boolean, coordinate: CellCoordinates[]) => void;
    row: string[];
    sheetId: string;
}> = ({coordinates, cell, selected, onCellSelected, row, sheetId}) => {
    const [selectedItem, setSelectedItem] = useState(0);
    const onClick = useCallback(() => {
        onCellSelected(true, [coordinates]);
    }, [onCellSelected, coordinates]);

    const onKeyDown: (ev: any) => void = useCallback((ev) => {
        if (cell.type === "text") {
            if (ev.code === "Escape") {
                const input = ev.target as HTMLInputElement;
                input.blur();
                ev.preventDefault();
                ev.stopPropagation();
            }
        } else if (cell.type === "dropdown") {
            console.log(ev.code);
            if (ev.code === "ArrowDown") {
                setSelectedItem(prev => Math.min(cell.options.length - 1, prev + 1));
            } else if (ev.code === "ArrowUp") {
                setSelectedItem(prev => Math.max(0, prev - 1));
            } else if (ev.code === "Home") {
                setSelectedItem(0);
            } else if (ev.code === "End") {
                setSelectedItem(cell.options.length - 1);
            }
        }
    }, [cell]);

    let dropdownOption: DropdownOption | null = null;
    if (cell.type === "dropdown") {
        const option = cell.options.find(it => it.value === row[coordinates.column]);
        if (option) {
            dropdownOption = option;
        }
    }

    const activationRef = useRef<(left: number, top: number) => void>(doNothing);
    useLayoutEffect(() => {
        const key = `${sheetId}-${coordinates.column}-${coordinates.row}`;
        window[key] = activationRef.current;
        return () => {
            delete window[key];
        }
    });

    const domId = id(sheetId, coordinates.column, coordinates.row);

    return <td
        className={selected ? "active" : undefined}
        onClick={onClick}
        width={cell.width}
    >
        {cell.type === "static" ? cell.textFn(row) : null}
        {cell.type === "text" ?
            <input type={"text"} id={domId} onKeyDown={onKeyDown}/> : null}
        {cell.type === "dropdown" ? <div id={domId} onKeyDown={onKeyDown}>
            <ClickableDropdown
                width={"300px"}
                paddingControlledByContent
                chevron
                useMousePositioning
                openFnRef={activationRef}
                trigger={!dropdownOption ? null :
                    <Icon name={dropdownOption.icon} color={"iconColor"} color2={"iconColor2"}/>
                }
                onKeyDown={onKeyDown}
            >
                {cell.options.map((option, idx) => (
                    <ListRow
                        key={option.value}
                        select={doNothing}
                        fontSize={"16px"}
                        isSelected={idx === selectedItem}
                        icon={
                            <Icon
                                name={option.icon}
                                size={"20px"}
                                ml={"16px"}
                                color={"iconColor"}
                                color2={"iconColor2"}
                            />
                        }
                        left={option.title}
                        leftSub={<ListRowStat>{option.helpText}</ListRowStat>}
                        right={null}
                        stopPropagation={false}
                    />
                ))}
            </ClickableDropdown>
        </div> : null}
    </td>;
};

const StyledSheet = styled.table`
  width: 100%;
  color: var(--text);

  table, tr, td, th {
    border: 1px inset var(--borderGray);
    padding: 4px;
  }

  td.active, th.active {
    border: 2px solid var(--blue);
  }

  th {
    font-weight: bold;
    text-align: left;
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
`;