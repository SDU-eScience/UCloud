import * as React from "react";
import * as Accounting from ".";
import { MainContainer } from "@/MainContainer/MainContainer";
import Chart, { Props as ChartProps } from "react-apexcharts";
import { classConcat, injectStyle } from "@/Unstyled";
import theme, { ThemeColor } from "@/ui-components/theme";
import { Flex, Icon, Select } from "@/ui-components";
import Card, { CardClass } from "@/ui-components/Card";
import { ContextSwitcher } from "@/Project/ContextSwitcher";
import { ProviderLogo } from "@/Providers/ProviderLogo";

interface UsageChart {
    dataPoints: { timestamp: number, usage: number }[];
}

function usageChartToChart(
    chart: UsageChart,
    options: {
        valueFormatter?: (value: number) => string,
        removeDetails?: boolean,
    } = {}
): ChartProps {
    const result: ChartProps = {};
    const data = chart.dataPoints.map(it => [it.timestamp, it.usage]);
    result.series = [{
        name: "",
        data,
    }];
    result.type = "area";
    result.options = {
        chart: {
            type: "area",
            stacked: false,
            height: 350,
            animations: {
                enabled: false,
            },
            zoom: {
                type: "x",
                enabled: true,
                autoScaleYaxis: true,
            },
            toolbar: {
                show: false
            }
        },
        dataLabels: {
            enabled: false
        },
        markers: {
            size: 0,
        },
        title: {
            text: "Fie",
            align: "left"
        },
        stroke: {
            curve: "monotoneCubic",
        },
        fill: {
            type: "gradient",
            gradient: {
                shadeIntensity: 1,
                inverseColors: false,
                opacityFrom: 0.4,
                opacityTo: 0,
                stops: [0, 90, 100]
            }
        },
        colors: ['var(--blue)'],
        yaxis: {
            labels: {
                formatter: function (val) {
                    if (options.valueFormatter) {
                        return options.valueFormatter(val);
                    } else {
                        return val.toString();
                    }
                },
            },
            title: {
                text: 'Price'
            },
        },
        xaxis: {
            type: 'datetime',
        },
        tooltip: {
            shared: false,
            y: {
                formatter: function (val) {
                    if (options.valueFormatter) {
                        return options.valueFormatter(val);
                    } else {
                        return val.toString();
                    }
                }
            }
        }
    };

    if (options.removeDetails === true) {
        delete result.options.title;
        result.options.tooltip = { enabled: false };
        const c = result.options.chart!;
        c.sparkline = { enabled: true };
        c.zoom!.enabled = false;
    }

    return result;
}

const SmallUsageCardStyle = injectStyle("small-usage-card", k => `
    ${k} {
        width: 300px;
    }

    ${k} strong {
        display: block;
        margin-bottom: 10px;
    }

    ${k} .body {
        display: flex;
        flex-direction: row;
        gap: 8px;
        align-items: center;
    }

    ${k} .border-bottom {
        position: absolute;
        top: -6px;
        width: 112px;
        height: 1px;
        background: var(--midGray);
    }

    ${k} .border-left {
        position: absolute;
        top: -63px;
        height: calc(63px - 6px);
        width: 1px;
        background: var(--midGray);
    }
`);

const SmallUsageCard: React.FunctionComponent<{
    categoryName: string;
    usageText1: string;
    usageText2: string;
    change: number;
    changeText: string;
    chart: UsageChart;
}> = props => {
    let themeColor: ThemeColor = "midGray";
    if (props.change < 0) themeColor = "red";
    else if (props.change > 0) themeColor = "green";

    return <a href={`#${props.categoryName}`}>
        <div className={classConcat(CardClass, SmallUsageCardStyle)}>
            <strong><code>{props.categoryName}</code></strong>
            <div className="body">
                <Chart 
                    {...usageChartToChart(props.chart, {removeDetails: true})}
                    width={112}
                    height={63}
                />
                <div>
                    {props.usageText1} <br />
                    {props.usageText2} <br />
                    <span style={{color: `var(--${themeColor})`}}>
                        {props.change < 0 ? "" : "+"}
                        {props.change.toFixed(2)}%
                    </span>
                    {" "}{props.changeText}
                </div>
            </div>
            <div style={{position: "relative"}}>
                <div className="border-bottom"/>
            </div>
            <div style={{position: "relative"}}>
                <div className="border-left"/>
            </div>
        </div>
    </a>;
};

const CategoryDescriptorPanelStyle = injectStyle("category-descriptor", k => `
    ${k} {
        display: flex;
        flex-direction: column;

        height: 100%;
        width: 100%;
    }

    ${k} figure {
        width: 128px;
        margin: 0 auto;
        margin-bottom: 14px; /* make size correct */
    }

    ${k} figure > *:nth-child(2) > *:first-child {
        position: absolute;
        top: -50px;
        left: 64px;
    }

    ${k} h1 {
        text-align: center;
    }

    ${k} .stat-container {
        display: flex;
        gap: 12px;
        flex-direction: column;
    }

    ${k} .stat , ${k} .substat {
        display: flex;
        flex-direction: row;
    }

    ${k} .stat {
        font-size: 18px;
    }

    ${k} .substat {
        font-size: 12px;
    }

    ${k} .stat > *:first-child,
    ${k} .substat > *:first-child {
        flex-grow: 1;
    }
`);

const CategoryDescriptorPanel: React.FunctionComponent<{
    productType: Accounting.ProductType;
    categoryName: string;
    providerName: string;
}> = props => {
    return <div className={classConcat(CardClass, CategoryDescriptorPanelStyle)}>
        <figure>
            <Icon name={Accounting.productTypeToIcon(props.productType)} size={128} />
            <div style={{position: "relative"}}>
                <ProviderLogo providerId={props.providerName} size={64} />
            </div>
        </figure>

        <h1><code>{props.categoryName}</code></h1>
        <p>{Accounting.guestimateProductCategoryDescription(props.categoryName, props.providerName)}</p>

        <div style={{flexGrow: 1}} />

        <div className="stat-container">
            {[0, 1, 2, 3].map(it => 
                <div key={it}>
                    <div className="stat">
                        <div>Number of jobs</div>
                        <div>41</div>
                    </div>
                    <div className="substat">
                        <div>Compared to last period</div>
                        <div>+4</div>
                    </div>
                </div>
            )}
        </div>
    </div>;
};

const BreakdownStyle = injectStyle("breakdown", k => `
    ${k} {
        height: 100%;
        width: 100%;
    }

    ${k} .panel-title {
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 8px;
    }

    ${k} .panel-title > *:nth-child(1) {
        font-size: 18px;
        margin-top: 10px;
    }

    ${k} .panel-title > *:nth-child(2) {
        width: 180px;
    }

    ${k} .pie-wrapper {
        width: 350px;
        margin: 20px auto;
    }

    ${k} table {
        width: 100%;
    }

    ${k} td, 
    ${k} th {
        padding: 0 8px;
        border-left: 2px solid var(--midGray);
        border-right: 2px solid var(--midGray);
    }

    ${k} tbody > tr:last-child > td {
        border-bottom: 2px solid var(--midGray);
    }

    ${k} th {
        text-align: left;
        border-top: 2px solid var(--midGray);
        border-bottom: 2px solid var(--midGray);
    }

    ${k} table tbody tr > td:nth-child(2),
    ${k} table tbody tr > td:nth-child(3) {
        text-align: right;
        font-family: var(--monospace);
    }
`);

const BreakdownPanel: React.FunctionComponent = props => {
    return <div className={classConcat(CardClass, BreakdownStyle)}>
        <div className="panel-title">
            <h4>Usage breakdown by</h4>
            <div>
                <Select slim>
                    <option>Project</option>
                    <option>Field of research</option>
                    <option>Machine type</option>
                </Select>
            </div>
        </div>

        <div className="pie-wrapper">
            <Chart
                type="pie"
                series={[10, 20, 30, 30]}
                options={{
                    chart: {
                        animations: {
                            enabled: false,
                        },
                    },
                    labels: ["A", "B", "C", "D"],
                    dataLabels: {
                        enabled: false,
                    },
                    stroke: {
                        show: false,
                    },
                    legend: {
                        show: false,
                    },
                    colors: ["var(--blue)", "var(--green)", "var(--red)", "var(--purple)", "var(--yellow)"],
                }}
                height={350}
                width={350}
            />
        </div>

        <table>
            <thead>
                <tr>
                    <th>Project</th>
                    <th>Usage</th>
                    <th>Change</th>
                </tr>
            </thead>
            <tbody>
                {Array(29).fill(undefined).map((_, idx) => {
                    const usage = Math.floor(Math.random() * 13000);
                    let change = 250 - Math.random() * 500;
                    if (Math.random() < 0.1) {
                        change = 0;
                    }

                    let color: ThemeColor = "midGray";
                    if (change < 0) color = "red";
                    else if (change > 0) color = "green";

                    return <tr key={idx}>
                        <td>DeiC-{Math.floor(Math.random() * 10000).toString().padStart(4, "0")}</td>
                        <td>{Accounting.addThousandSeparators(usage)} DKK</td>
                        <td style={{color: `var(--${color})`}}>
                            {change > 0 && "+"} 
                            {change.toFixed(2)}%
                        </td>
                    </tr>
                })}
            </tbody>
        </table>
    </div>;
};

const VisualizationStyle = injectStyle("visualization", k => `
    ${k} header {
        position: fixed;
        top: 0;
        left: var(--currentSidebarWidth);
        
        background: var(--white);
        
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 8px;
        
        height: 50px;
        width: calc(100vw - var(--currentSidebarWidth));
        
        padding: 0 16px;
        z-index: 10;
        
        box-shadow: ${theme.shadows.sm};
    }

    ${k} header.at-top {
        box-shadow: unset;
    }
    
    ${k} header h3 {
        margin: 0;
    }

    ${k} header .duration-select {
        width: 150px;
    }

    ${k} h1, ${k} h2, ${k} h3, ${k} h4 {
        margin: 19px 0;
    }

    ${k} h3:first-child {
        margin-top: 0;
    }

    ${k} .panel-grid {
        display: flex;
        gap: 16px;
        flex-direction: row;
        width: 100%;
        height: calc(100vh - 50px - 120px);
        padding: 16px 0;
    }

    ${k} .${CategoryDescriptorPanelStyle} {
        width: 300px;
        flex-shrink: 0;
    }

    ${k} .${BreakdownStyle} {
        /* This places the card aligned with a potential third summary card */
        width: calc(450px + 8px); 
        flex-shrink: 0;
    }

    ${k} .primary-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        grid-template-rows: 7fr 3fr;
        gap: 16px;
        width: 100%;
        height: 100%;
    }
`);
const Visualization: React.FunctionComponent = props => {
    // NOTE(Dan): We are not using a <MainContainer/> here on purpose since 
    // we want to use _all_ of the space.
    return <div className={VisualizationStyle}>
        <header className="at-top">
            <h3>Usage at DeiC Interactive HPC (SDU) in the past</h3>
            <div className="duration-select">
                <Select slim>
                    <option>7 days</option>
                    <option>30 days</option>
                    <option>90 days</option>
                    <option>365 days</option>
                </Select>
            </div>
            <div style={{flexGrow: "1"}} />
            <ContextSwitcher />
        </header>

        <div style={{padding: "13px 16px 16px 16px"}}>
            <h3>Usage at DeiC Interactive HPC (SDU)</h3>

            <Flex flexDirection="row" gap="16px">
                <SmallUsageCard
                    categoryName="u1-standard"
                    usageText1="51K/100K DKK"
                    usageText2="51% used"
                    change={0.3}
                    changeText="7d change"
                    chart={dummyChart()}
                />
                <SmallUsageCard
                    categoryName="u1-gpu"
                    usageText1="200K/1M GPU-hours"
                    usageText2="20% used"
                    change={1.2}
                    changeText="7d change"
                    chart={dummyChart()}
                />
                <SmallUsageCard
                    categoryName="u1-storage"
                    usageText1="340 TB/1 PB"
                    usageText2="34% used"
                    change={-3.2}
                    changeText="7d change"
                    chart={dummyChartQuota()}
                />
            </Flex>

            <div className="panel-grid">
                <CategoryDescriptorPanel
                    productType="COMPUTE"
                    categoryName="u1-standard"
                    providerName="ucloud"
                />

                <BreakdownPanel />

                <div className="primary-grid">
                    <Card />
                    <Card />
                    <Card />
                    <Card />
                </div>
            </div>
        </div>
    </div>;
};

function dummyChart(): UsageChart {
    const result: UsageChart = { dataPoints: [] };
    const d = new Date();
    d.setHours(0, 0, 0);
    d.setDate(d.getDate() - 7);
    let currentUsage = 30000;
    for (let i = 0; i < 4 * 7; i++) {
        result.dataPoints.push({ timestamp: d.getTime(), usage: currentUsage });
        if (i % 2 === 0 && Math.random() >= 0.5) {
            currentUsage += Math.random() * 5000;
        } else if (i % 2 === 0 && Math.random() >= 0.5) {
            currentUsage += Math.random() * 400;
        } else if (i % 2 === 1 && Math.random() >= 0.3) {
            currentUsage += Math.random() * 200;
        }
        d.setHours(d.getHours() + 6);
    }
    return result;
}

function dummyChartQuota() {
    const result: UsageChart = { dataPoints: [] };
    const d = new Date();
    d.setHours(0, 0, 0);
    d.setDate(d.getDate() - 7);
    let currentUsage = 50;
    for (let i = 0; i < 4 * 7; i++) {
        result.dataPoints.push({ timestamp: d.getTime(), usage: currentUsage });
        if (i % 2 === 0 && Math.random() >= 0.5) {
            currentUsage += 20 - (Math.random() * 40);
            currentUsage = Math.max(10, currentUsage);
        }
        d.setHours(d.getHours() + 6);
    }
    return result;

}

export default Visualization;
