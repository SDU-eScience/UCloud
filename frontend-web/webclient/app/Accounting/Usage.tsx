import * as React from "react";
import {injectStyle} from "@/Unstyled";
import {Flex, Text, Box, Button, Label} from "@/ui-components";
import {useCallback, useState} from "react";
import {
    doNothing
} from "@/UtilityFunctions";
import * as Config from "../../site.config.json";
import {formatDate} from "date-fns";
import {GradientWithPolygons} from "@/ui-components/GradientBackground";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {DATE_FORMAT} from "@/Admin/NewsManagement";
import {dialogStore} from "@/Dialog/DialogStore";
import {slimModalStyle} from "@/Utilities/ModalUtilities";
import {Toggle} from "@/ui-components/Toggle";

// TODO(Louise): move this somewhere else
export interface ExportHeader<T> {
    key: keyof T;
    value: string;
    defaultChecked: boolean;
    hidden?: boolean;
}

// TODO(Louise): move this somewhere else
export function exportUsage<T extends object>(
    chartData: T[] | undefined,
    headers: ExportHeader<T>[],
    projectTitle: string | undefined,
    opts?: {
        formats?: ("json" | "csv")[];
        fileName?: string;
        csvData?: any[];
    }
): void {
    if (!chartData?.length) {
        snackbarStore.addFailure("No data to export found", false);
        return;
    }

    dialogStore.addDialog(
        <UsageExport chartData={chartData} headers={headers} projectTitle={projectTitle}
                     formats={opts?.formats} fileName={opts?.fileName} csvData={opts?.csvData} />,
        doNothing,
        true,
        slimModalStyle
    );
}

function UsageExport<T extends object>(
    opts: {
        chartData: T[];
        headers: ExportHeader<T>[];
        projectTitle?: string;
        formats?: ("json" | "csv")[];
        fileName?: string;
        csvData?: any[],
    }
): React.ReactNode {
    const {chartData, headers, projectTitle, fileName, csvData} = opts;
    const formats = opts.formats ?? ["csv", "json"];
    const [checked, setChecked] = useState(headers.map(it => it.defaultChecked));

    const startExport = useCallback((format: "json" | "csv") => {
        doExport(format === "csv" ? csvData ?? chartData : chartData,
            headers.filter((_, idx) => checked[idx]), ';', format, projectTitle, fileName);
        dialogStore.success();
    }, [checked]);

    return <Box>
        <h2>Configure your export</h2>
        <Box mt="12px" mb="36px">
            {headers.map((h, i) => h.hidden ? null :
                <Label key={h.value} my="4px" style={{display: "flex"}}>
                    <Toggle height={20} checked={checked[i]} onChange={prevValue => {
                        setChecked(ch => {
                            ch[i] = !prevValue
                            return [...ch];
                        })
                    }}/>
                    <Text ml="8px">{h.value}</Text>
                </Label>
            )}
        </Box>
        <Flex justifyContent="end" px={"20px"} py={"12px"} margin={"-20px"} background={"var(--dialogToolbar)"}
              gap={"8px"}>
            <Button onClick={() => dialogStore.failure()} color="errorMain">Cancel</Button>
            {formats?.indexOf("json") !== -1 ? <Button onClick={() => startExport("json")} color="successMain">Export JSON</Button> : null}
            {formats?.indexOf("csv") !== -1 ? <Button onClick={() => startExport("csv")} color="successMain">Export CSV</Button> : null}
        </Flex>
    </Box>

    function doExport(
        chartData: any[],
        headers: ExportHeader<T>[],
        delimiter: string,
        format: "json" | "csv",
        projectTitle?: string,
        fileName?: string,
    ) {
        let text = "";
        switch (format) {
            case "csv": {
                text = headers.map(it => it.value).join(delimiter) + "\n";

                for (const el of chartData) {
                    for (const [idx, header] of headers.entries()) {
                        let val = `${el[header.key]}`;
                        if (val === "undefined" || val === "null") val = "";
                        text += `"${val}"`;
                        if (idx !== headers.length - 1) text += delimiter;
                    }
                    text += "\n";
                }

                break;
            }

            case "json": {
                const h = headers.map(it => it.key);
                const data: T[] = JSON.parse(JSON.stringify(chartData));

                for (const row of data) {
                    for (const k of Object.keys(row)) {
                        if (!h.includes(k as keyof T)) {
                            delete row[k];
                        }
                    }
                }

                text = JSON.stringify(data);
                break;
            }
        }

        const a = document.createElement("a");
        a.href = "data:text/plain;charset=utf-8," + encodeURIComponent(text);
        a.download = fileName ?
            `${fileName}.${format}` :
            `${Config.PRODUCT_NAME} - ${projectTitle ? projectTitle : "My workspace"} - ${formatDate(new Date(), DATE_FORMAT)}.${format}`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
    }
}

// TODO(Louise): move this somewhere else
export function header<T>(key: keyof T, value: string, defaultChecked?: boolean): ExportHeader<T> {
    return {key, value, defaultChecked: !!defaultChecked};
}

export const PeriodStyle = injectStyle("period-selector", k => `
    ${k} {
        border: 1px solid var(--borderColor);
        border-radius: 6px;
        padding: 6px 12px;
        display: flex;
    }
    
    ${k}:hover {
        border: 1px solid var(--borderColorHover);
    }
    
    .${GradientWithPolygons} ${k} {
        border: 1px solid var(--borderColor);
    }
    
    .${GradientWithPolygons} ${k}:hover {
        border: 1px solid var(--borderColorHover);
    }
`);

