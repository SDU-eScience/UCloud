import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import {EveryIcon} from "@/ui-components/Icon";
import {Grid, Box} from "@/ui-components";
import {ThemeColor} from "@/ui-components/theme";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {
    Cell,
    CellCoordinates,
    DropdownCell,
    FuzzyCell,
    Sheet,
    SheetRenderer,
    StaticCell,
    TextCell
} from "@/ui-components/Sheet";
import {useMemo, useRef, useState} from "react";
import {
    browseWallets,
    listProducts,
    Product, ProductType,
    productTypes,
    productTypeToIcon,
    productTypeToTitle,
    Wallet
} from "@/Accounting";
import {useCloudAPI} from "@/Authentication/DataHook";
import {emptyPage, emptyPageV2} from "@/DefaultObjects";
import {PageV2} from "@/UCloud";

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

    const [wallets] = useCloudAPI<PageV2<Wallet>>(browseWallets({itemsPerPage: 250}), emptyPageV2);

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
        FuzzyCell(
            (query, column, row) => {
                const currentType = sheet.current!.readValue(2, row) as ProductType;
                const lq = query.toLowerCase();
                return wallets.data.items
                    .filter(it => {
                        return it.productType === currentType && it.paysFor.name.toLowerCase().indexOf(lq) != -1;
                    })
                    .map(it => ({
                        icon: productTypeToIcon(it.productType),
                        title: it.paysFor.name + " @ " + it.paysFor.provider,
                        value: it.paysFor.name + " @ " + it.paysFor.provider
                    }));
            }
        ),
        TextCell("Immediately", { fieldType: "date" }),
        TextCell("No expiration", { fieldType: "date" }),
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
                const s = sheet.current!;
                console.log("Row has been updated", row, sheet.current!.sheetId);
                s.registerValidation(1, row, "valid");
                s.registerValidation(3, row, "invalid");
                s.registerValidation(3, row);
                s.registerValidation(4, row, "loading");
                s.registerValidation(5, row, "valid");
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
