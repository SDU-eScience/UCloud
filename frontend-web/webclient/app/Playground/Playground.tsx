import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import {EveryIcon} from "@/ui-components/Icon";
import {Grid, Box} from "@/ui-components";
import {ThemeColor} from "@/ui-components/theme";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {Cell, CellCoordinates, DropdownCell, Sheet, SheetRenderer, StaticCell, TextCell} from "@/ui-components/Sheet";
import {useMemo, useRef, useState} from "react";
import {productTypes, productTypeToIcon, productTypeToTitle} from "@/Accounting";

export const Playground: React.FunctionComponent = () => {
    const main = (
        <>
            {/*<Grid gridTemplateColumns={"repeat(5, 1fr)"} mb={"32px"}>*/}
            {/*    <EveryIcon />*/}
            {/*</Grid>*/}
            {/*<Grid*/}
            {/*    gridTemplateColumns="repeat(10, 1fr)"*/}
            {/*    style={{overflowY: "scroll"}}*/}
            {/*    mb={"32px"}*/}
            {/*>*/}
            {/*    {colors.map((c: ThemeColor) => (*/}
            {/*        <Box*/}
            {/*            title={`${c}, ${getCssVar(c)}`}*/}
            {/*            key={c}*/}
            {/*            backgroundColor={c}*/}
            {/*            height={"100px"}*/}
            {/*            width={"100%"}*/}
            {/*        />*/}
            {/*    ))}*/}
            {/*</Grid>*/}
            {/**/}
            {/*<ConfirmationButton icon={"trash"} actionText={"Delete"} color={"red"} />*/}

            <SheetDemo/>
        </>
    );
    return <MainContainer main={main}/>;
};

const SheetDemo: React.FunctionComponent = () => {
    const [selectedCells, setSelectedCells] = useState<CellCoordinates[]>([]);
    const [data, setData] = useState<string[][]>([
        ["PROJECT", "ProjectId", "compute", "u1-standard @ ucloud", "01/11/21", "01/11/22", "5000", "DKK"]
    ]);

    const header: string[] = useMemo(() => ([
        "",
        "Recipient",
        "",
        "Product",
        "Start Date",
        "End Date",
        "Amount",
        "",
    ]), []);

    const cells: Cell[] = useMemo(() => ([
        DropdownCell(
            [
                {icon: "projects", title: "Project", value: "PROJECT"},
                {icon: "user", title: "Personal Workspace", value: "USER"},
            ],
            {width: "50px"}
        ),
        TextCell(),
        DropdownCell(
            productTypes.map(type => ({
                icon: productTypeToIcon(type),
                title: productTypeToTitle(type),
                value: type
            })),
            {width: "50px"}
        ),
        TextCell(),
        TextCell("Immediately"),
        TextCell("No expiration"),
        TextCell(),
        StaticCell("DKK"),
    ]), []);

    const sheet = useRef<SheetRenderer>(null);

    return <>
        <Sheet
            header={header}
            cells={cells}
            renderer={sheet}
            onRowUpdated={(row) => {
                console.log("Row has been updated", row, sheet.current!.sheetId);
            }}
            rows={500}
        />
    </>
};

const colors = [
    "black",
    "white",
    "textBlack",
    "lightGray",
    "midGray",
    "gray",
    "darkGray",
    "lightBlue",
    "lightBlue2",
    "blue",
    "darkBlue",
    "lightGreen",
    "green",
    "darkGreen",
    "lightRed",
    "red",
    "darkRed",
    "orange",
    "darkOrange",
    "lightPurple",
    "purple",
    "yellow",
    "text",
    "textHighlight",
    "headerText",
    "headerBg",
    "headerIconColor",
    "headerIconColor2",
    "borderGray",
    "paginationHoverColor",
    "paginationDisabled",
    "iconColor",
    "iconColor2",
    "FtIconColor",
    "FtIconColor2",
    "FtFolderColor",
    "FtFolderColor2",
    "spinnerColor",
    "tableRowHighlight",
    "appCard",
    "wayfGreen",
];

export default Playground;
