import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import {EveryIcon} from "@/ui-components/Icon";
import {Grid, Box} from "@/ui-components";
import {ThemeColor} from "@/ui-components/theme";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {Cell, CellCoordinates, DropdownCell, Sheet, StaticCell, TextCell} from "@/ui-components/Sheet";
import {useMemo, useState} from "react";

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
            {width: "70px"}
        ),
        TextCell(),
        StaticCell("2"),
        TextCell(),
        StaticCell("4"),
        StaticCell("5"),
        StaticCell("6"),
        StaticCell("7"),
    ]), []);

    return <>
        <Sheet
            header={header}
            cells={cells}
            selectedCell={selectedCells}
            onCellSelected={setSelectedCells}
            data={data}
            onDataUpdated={setData}
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
