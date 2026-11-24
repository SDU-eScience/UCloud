import * as React from "react";
import {useCallback, useState} from "react";
import {useNavigate} from "react-router-dom";
import {Button, Flex, Icon, Link} from "@/ui-components";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {exportUsage, header} from "@/Accounting/Usage";
import type {ProjectInfo} from "@/Project/InfoCache";
import AppRoutes from "@/Routes";
import {injectStyle} from "@/Unstyled";
import {SubmitShortcut} from "@/Applications/KeyboardNavigation";
import {Table} from "./Table";
import type {DateRange, Model, ProductAllocation} from "./Model";
import {productWorkspaceInfo} from "./Model";

const ReceivedStyle = injectStyle("received-allocations", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        flex: 1 1 0;
        min-height: 0;
        min-width: 0;
    }

    ${k} .received-allocations-grid {
        display: flex;
        flex: 1 1 0;
        min-height: 0;
        min-width: 0;
        width: 100%;
        overflow: hidden;
    }

    ${k} .received-allocations-loading {
        display: flex;
        flex: 1 1 0;
        align-items: center;
        justify-content: center;
        min-height: 0;
    }
`);

export function Received(props: {
    model: Model;
    loading: boolean;
    dateRange: DateRange;
    periodControl: React.ReactNode;
    projectId?: string;
    canApply: boolean;
    projectInfo?: Record<string, ProjectInfo | null>;
}): React.ReactNode {
    const navigate = useNavigate();
    const [exportProducts, setExportProducts] = useState<ProductAllocation[]>([]);

    const onViewUsage = useCallback(() => {
        navigate(AppRoutes.accounting.usage());
    }, [navigate]);

    const onExport = useCallback(() => {
        interface Row {
            source: string;
            product: string;
            provider: string;
            activeUsage: number;
            activeBalance: number;
            activeQuota: number;
            totalUsed: number;
            totalReceived: number;
            unit: string;
        }

        const rows: Row[] = exportProducts.map(product => {
            const source = productWorkspaceInfo(product, props.projectInfo ?? {});
            return {
                source: source.title,
                product: product.category.name,
                provider: product.category.provider,
                activeUsage: product.amounts.activeUsed,
                activeBalance: product.amounts.activeBalance,
                activeQuota: product.amounts.activeQuota,
                totalUsed: product.amounts.used,
                totalReceived: product.amounts.granted,
                unit: product.unit,
            };
        });

        exportUsage(
            rows,
            [
                header<Row>("source", "Source", true),
                header<Row>("product", "Product", true),
                header<Row>("provider", "Provider", true),
                header<Row>("activeUsage", "Active usage", true),
                header<Row>("activeBalance", "Active balance", true),
                header<Row>("activeQuota", "Active quota", true),
                header<Row>("totalUsed", "Total used", true),
                header<Row>("totalReceived", "Total received", true),
                header<Row>("unit", "Unit", true),
            ],
            undefined,
            {fileName: "received-allocations"},
        );
    }, [exportProducts, props.projectInfo]);

    const applyTo = props.projectId && props.canApply
        ? AppRoutes.grants.newApplication({projectId: props.projectId})
        : AppRoutes.grants.editor();

    return <div className={ReceivedStyle}>
        {props.loading ?
            <div className="received-allocations-loading"><HexSpin size={64} /></div> :
            <div className="received-allocations-grid">
                <Table
                    key={props.projectId}
                    mode="received"
                    model={props.model}
                    projectInfo={props.projectInfo ?? {}}
                    dateRange={props.dateRange}
                    periodControl={props.periodControl}
                    onViewUsage={onViewUsage}
                    onVisibleProductsChange={setExportProducts}
                    toolbar={<Flex gap="8px">
                        {props.canApply ? <Link to={applyTo}>
                            <Button
                                height={35}
                                data-allocation-shortcut="allocate"
                                ariaKeyshortcuts="Control+Alt+A Meta+Alt+A"
                            >
                                <Icon name="heroPencilSquare" mr={8} />
                                Apply for resources
                                <SubmitShortcut shortcut="A" />
                            </Button>
                        </Link> : null}
                        <Button
                            height={35}
                            data-allocation-shortcut="export"
                            ariaKeyshortcuts="Control+Alt+E Meta+Alt+E"
                            onClick={onExport}
                        >
                            <Icon name="heroArrowDownTray" mr={8} />
                            Export
                            <SubmitShortcut shortcut="E" />
                        </Button>
                    </Flex>}
                    emptyMessage="No received allocations yet."
                />
            </div>}
    </div>;
}
