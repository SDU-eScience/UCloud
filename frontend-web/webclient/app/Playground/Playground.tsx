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
import {useEffect, useLayoutEffect, useMemo, useRef, useState} from "react";
import {
    browseWallets, ChargeType,
    listProducts, normalizeBalanceForFrontend,
    Product, ProductCategoryId, ProductPriceUnit, ProductType,
    productTypes,
    productTypeToIcon,
    productTypeToTitle,
    Wallet
} from "@/Accounting";
import {apiBrowse, useCloudAPI} from "@/Authentication/DataHook";
import {emptyPage, emptyPageV2} from "@/DefaultObjects";
import {PageV2, PaginationRequestV2} from "@/UCloud";

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

interface SubAllocation {
    id: string;
    startDate: number;
    endDate?: number | null;

    productCategoryId: ProductCategoryId;
    productType: ProductType;
    chargeType: ChargeType;
    unit: ProductPriceUnit;

    workspaceId: string;
    workspaceTitle: string;
    workspaceIsProject: boolean;

    remaining: number;
}

function browseSubAllocations(request: PaginationRequestV2): APICallParameters {
    return apiBrowse(request, "/api/accounting/wallets", "subAllocation");
}

const SheetDemo: React.FunctionComponent = () => {
    const [selectedCells, setSelectedCells] = useState<CellCoordinates[]>([]);
    const [data, setData] = useState<string[][]>([
        ["PROJECT", "ProjectId", "compute", "u1-standard @ ucloud", "01/11/21", "01/11/22", "5000", "DKK"]
    ]);

    const [wallets] = useCloudAPI<PageV2<Wallet>>(browseWallets({itemsPerPage: 250}), emptyPageV2);
    const [allocations, fetchAllocations] = useCloudAPI<PageV2<SubAllocation>>({noop: true}, emptyPageV2);

    useEffect(() => {
        fetchAllocations(browseSubAllocations({itemsPerPage: 250}));
    }, []);

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

    useLayoutEffect(() => {
        const s = sheet.current!;
        let i = 0;
        s.clear();
        allocations.data.items.forEach(alloc => {
            console.log("adding row", alloc);
            s.addRow();
            s.writeValue(0, i, alloc.workspaceIsProject ? "PROJECT" : "USER");
            s.writeValue(1, i, alloc.workspaceTitle);
            s.writeValue(2, i, alloc.productType);
            s.writeValue(3, i, alloc.productCategoryId.name + " @ " + alloc.productCategoryId.provider);
            {
                const startDate = new Date(alloc.startDate);
                s.writeValue(4, i, `${startDate.getUTCFullYear()}-${startDate.getMonth() + 1}-${startDate.getDate()}`);
            }
            if (alloc.endDate) {
                const endDate = new Date(alloc.endDate);
                s.writeValue(5, i, `${endDate.getUTCFullYear()}-${endDate.getMonth() + 1}-${endDate.getDate()}`);
            }
            s.writeValue(6, i, normalizeBalanceForFrontend(alloc.remaining, alloc.productType, alloc.chargeType,
                alloc.unit, false, 0).replace('.', '').toString());

            i++;
        });

        s.addRow();
        s.writeValue(0, i, "PROJECT");
        s.writeValue(2, i, "STORAGE");
    }, [allocations.data.items]);

    return <>
        <Sheet
            header={header}
            cells={cells}
            renderer={sheet}
            onRowUpdated={(row, values) => {
                const s = sheet.current!;
                console.log("Row has been updated", row, sheet.current!.sheetId, values);
                s.registerValidation(1, row, "valid");
                s.registerValidation(3, row, "invalid");
                s.registerValidation(3, row);
                s.registerValidation(4, row, "loading");
                s.registerValidation(5, row, "valid");
            }}
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
