import * as React from "react";
import * as Accounting from ".";
import Chart, {Props as ChartProps} from "react-apexcharts";
import {classConcat, injectStyle} from "@/Unstyled";
import theme, {ThemeColor} from "@/ui-components/theme";
import {Flex, Icon, Select} from "@/ui-components";
import Card, {CardClass} from "@/ui-components/Card";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {dateToString} from "@/Utilities/DateUtilities";
import {useLayoutEffect, useMemo} from "react";
import {ProductType} from ".";

interface UsageChart {
    dataPoints: { timestamp: number, usage: number }[];
}

function usageChartToChart(
    chart: UsageChart,
    options: {
        valueFormatter?: (value: number) => string,
        removeDetails?: boolean,
        unit?: string,
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
                show: true,
                tools: {
                    reset: true,
                    zoom: true,
                    zoomin: true,
                    zoomout: true,

                    pan: false, // Performance seems pretty bad, let's just disable it
                    download: false,
                    selection: false,
                },
            },
        },
        dataLabels: {
            enabled: false
        },
        markers: {
            size: 0,
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
                text: (() => {
                    let res = "Usage";
                    if (options.unit) {
                        res += " (";
                        res += options.unit;
                        res += ")"
                    }
                    return res;
                })()
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
                        let res = options.valueFormatter(val);
                        if (options.unit) {
                            res += " ";
                            res += options.unit;
                        }
                        return res;
                    } else {
                        return val.toString();
                    }
                }
            }
        },
    };

    if (options.removeDetails === true) {
        delete result.options.title;
        result.options.tooltip = {enabled: false};
        const c = result.options.chart!;
        c.sparkline = {enabled: true};
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

    const chartProps = useMemo(() => {
        return usageChartToChart(props.chart, {removeDetails: true});
    }, [props.chart]);

    return <a href={`#${props.categoryName}`}>
        <div className={classConcat(CardClass, SmallUsageCardStyle)}>
            <strong><code>{props.categoryName}</code></strong>
            <div className="body">
                <Chart
                    {...chartProps}
                    width={112}
                    height={63}
                />
                <div>
                    {props.usageText1} <br/>
                    {props.usageText2} <br/>
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
            <Icon name={Accounting.productTypeToIcon(props.productType)} size={128}/>
            <div style={{position: "relative"}}>
                <ProviderLogo providerId={props.providerName} size={64}/>
            </div>
        </figure>

        <h1><code>{props.categoryName}</code></h1>
        <p>{Accounting.guestimateProductCategoryDescription(props.categoryName, props.providerName)}</p>

        <div style={{flexGrow: 1}}/>

        {props.productType === "COMPUTE" &&
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
        }
    </div>;
};

const PanelClass = injectStyle("panel", k => `
    ${k} {
        height: 100%;
        width: 100%;
        padding-bottom: 20px;
        
        /* Required for flexible cards to ensure that they are not allowed to grow their height based on their 
           content */
        min-height: 100px; 
        
        display: flex;
        flex-direction: column;
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
        max-width: 180px;
    }
    
    ${k} .table-wrapper {
        flex-grow: 1;
        overflow-y: auto;
    }
    
    html.light ${k} .table-wrapper {
        box-shadow: inset 0px -11px 8px -10px #ccc;
    }
    
    html.dark ${k} .table-wrapper {
        box-shadow: inset 0px -11px 8px -10px rgba(255, 255, 255, 0.5);
    }
    
    ${k} .table-wrapper.at-bottom {
        box-shadow: unset !important;
    }
    
    html.light ${k} .table-wrapper::before {
        box-shadow: 0px -11px 8px 11px #ccc;
    }
    
    html.dark ${k} .table-wrapper::before {
        box-shadow: 0px -11px 8px 11px rgba(255, 255, 255, 0.5);
    }
    
    ${k} .table-wrapper::before {
        display: block;
        content: " ";
        width: 100%;
        height: 1px;
        position: sticky;
        top: 24px;
    }
    
    ${k} .table-wrapper.at-top::before {
        box-shadow: unset !important;
    }
`);

const BreakdownStyle = injectStyle("breakdown", k => `
    ${k} .pie-wrapper {
        width: 350px;
        margin: 20px auto;
    }

    ${k} table tbody tr > td:nth-child(2),
    ${k} table tbody tr > td:nth-child(3) {
        text-align: right;
        font-family: var(--monospace);
    }
`);

const BreakdownPanel: React.FunctionComponent<{ productType?: ProductType }> = props => {
    const type = props.productType ?? "COMPUTE";
    const unit = type === "STORAGE" ? "GB" : "DKK";

    return <div className={classConcat(CardClass, PanelClass, BreakdownStyle)}>
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

                let changeClass = "unchanged";
                if (change < 0) changeClass = "negative";
                else if (change > 0) changeClass = "positive";

                return <tr key={idx}>
                    <td>DeiC-{Math.floor(Math.random() * 10000).toString().padStart(4, "0")}</td>
                    <td>{Accounting.addThousandSeparators(usage)} {unit}</td>
                    <td className={`change ${changeClass}`}>
                        <span>{change >= 0 ? "+" : "-"}</span>
                        <span>{Math.abs(change).toFixed(2)}%</span>
                    </td>
                </tr>
            })}
            </tbody>
        </table>
    </div>;
};

const MostUsedApplicationsStyle = injectStyle("most-used-applications", k => `
   
    ${k} table tr > td:nth-child(2),
    ${k} table tr > td:nth-child(3) {
        font-family: var(--monospace);
        text-align: right;
    }
`);

const MostUsedApplicationsPanel: React.FunctionComponent = () => {
    return <div className={classConcat(CardClass, PanelClass, MostUsedApplicationsStyle)}>
        <div className="panel-title">
            <h4>Most used applications</h4>
        </div>

        <div className="table-wrapper">
        <table>
            <thead>
            <tr>
                <th>Application</th>
                <th>Number of jobs</th>
                <th>Change</th>
            </tr>
            </thead>
            <tbody>
            {Array(100).fill(undefined).map((_, i) =>
                <React.Fragment key={i}>
                    <tr>
                        <td>Visual Studio Code</td>
                        <td>42</td>
                        <td className={"change positive"}>
                            <span>+</span>
                            <span>23,00%</span>
                        </td>
                    </tr>
                    <tr>
                        <td>RStudio</td>
                        <td>32</td>
                        <td className={"change negative"}>
                            <span>-</span>
                            <span>23,00%</span>
                        </td>
                    </tr>
                    <tr>
                        <td>RStudio</td>
                        <td>32</td>
                        <td className={"change unchanged"}>
                            <span>+</span>
                            <span>0,00%</span>
                        </td>
                    </tr>
                </React.Fragment>
            )}
            </tbody>
        </table>
        </div>
    </div>;
};

const JobSubmissionStyle = injectStyle("job-submission", k => `

    ${k} table tr > td:nth-child(2),
    ${k} table tr > td:nth-child(3),
    ${k} table tr > td:nth-child(4),
    ${k} table tr > td:nth-child(5) {
        font-family: var(--monospace);
    }
    
    ${k} table tr > td:nth-child(3),
    ${k} table tr > td:nth-child(4),
    ${k} table tr > td:nth-child(5) {
        text-align: right;
    }
    
    ${k} table tr > *:nth-child(1) {
        width: 100px;
    }
    
    ${k} table tr > *:nth-child(2) {
        width: 130px;
    }
    
    ${k} table tr > *:nth-child(4),
    ${k} table tr > *:nth-child(5) {
        width: 120px;
    }
`);

const dayNames = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const periods = Array(4).fill(undefined).map((_, i) => {
    const start = i * 6;
    const end = (i + 1) * 6;
    return `${start.toString().padStart(2, "0")}:00-${end.toString().padStart(2, "0")}:00`;
});

const JobSubmissionPanel: React.FunctionComponent = () => {
    return <div className={classConcat(CardClass, PanelClass, JobSubmissionStyle)}>
        <div className="panel-title">
            <h4>When are your jobs being submitted?</h4>
        </div>

        <div className="table-wrapper">
        <table>
            <thead>
            <tr>
                <th>Day</th>
                <th>Time of day</th>
                <th>Count</th>
                <th>Avg duration</th>
                <th>Avg queue</th>
            </tr>
            </thead>
            <tbody>
            {Array(4 * 7).fill(undefined).map((_, i) => {
                const day = dayNames[Math.floor(i / 4)];
                const period = periods[i % 4];
                return <tr key={i}>
                    <td>{day}</td>
                    <td>{period}</td>
                    <td>{Math.floor(Math.random() * 30)}</td>
                    <td>{Math.floor(Math.random() * 12).toString().padStart(2, "0")}H {Math.floor(Math.random() * 60).toString().padStart(2, "0")}M</td>
                    <td>{Math.floor(Math.random()).toString().padStart(2, "0")}H {Math.floor(Math.random() * 15).toString().padStart(2, "0")}M {Math.floor(Math.random() * 60).toString().padStart(2, "0")}S</td>
                </tr>;
            })}
            </tbody>
        </table>
        </div>
    </div>;
}

const UsageOverTimeStyle = injectStyle("usage-over-time", k => `
    ${k} table tbody tr > td:nth-child(1),
    ${k} table tbody tr > td:nth-child(2),
    ${k} table tbody tr > td:nth-child(3) {
        font-family: var(--monospace);
    }
    
    ${k} table tbody tr > td:nth-child(2),
    ${k} table tbody tr > td:nth-child(3) {
        text-align: right;
    }
    
    ${k} table tbody tr > td:nth-child(1) {
        width: 160px;
    }
    
    ${k} table tbody tr > td:nth-child(3) {
        width: 90px;
    }
    
    
    ${k} table.has-change tbody tr > td:nth-child(1) {
        width: unset;
    }
    
    ${k} table.has-change tbody tr > td:nth-child(2) {
        width: 130px;
    }
`);

const UsageOverTimePanel: React.FunctionComponent<{ productType?: ProductType }> = props => {
    const type = props.productType ?? "COMPUTE";
    const unit = type === "STORAGE" ? "GB" : "DKK";
    const chart = type === "STORAGE" ? storageChart1 : cpuChart1;
    let sum = 0;
    let totalUsed = chart.dataPoints[chart.dataPoints.length - 1].usage - chart.dataPoints[0].usage;
    const chartProps = useMemo(() => {
        return usageChartToChart(chart, {
            valueFormatter: val => Accounting.addThousandSeparators(val.toFixed(0)),
            unit,
        });
    }, []);

    return <div className={classConcat(CardClass, PanelClass, UsageOverTimeStyle)}>
        <div className="panel-title">
            <h4>Usage over time</h4>
        </div>

        <Chart {...chartProps} />

        <div className="table-wrapper">
        <table className={type === "STORAGE" ? "has-change" : ""}>
            <thead>
            <tr>
                <th>Timestamp</th>
                <th>{type === "COMPUTE" ? "Usage" : "Change"}</th>
                {type === "COMPUTE" && <th>Percent</th>}
            </tr>
            </thead>
            <tbody>
            {chart.dataPoints.map((point, idx) => {
                if (idx == 0) return null;
                const used = point.usage - chart.dataPoints[idx - 1].usage;
                sum += used;
                const percentage = ((used / totalUsed) * 100).toFixed(2);
                return <tr key={idx}>
                    <td>{dateToString(point.timestamp)}</td>
                    {type === "STORAGE" ?
                        <td className={"change " + (used === 0 ? "unchanged" : used > 0 ? "positive" : "negative")}>
                            <span>{used >= 0 ? "+" : "-"}</span>
                            <span>{Accounting.addThousandSeparators(Math.abs(used).toFixed(2))} {unit}</span>
                        </td> :
                        <td>{Accounting.addThousandSeparators(used.toFixed(2))} {unit}</td>
                    }
                    {type === "COMPUTE" && <td>{percentage}%</td>}
                </tr>;
            })}
            </tbody>
        </table>
        </div>
    </div>;
};

const LargeJobsStyle = injectStyle("large-jobs", k => `
    ${k} table tbody tr > td:nth-child(1),
    ${k} table tbody tr > td:nth-child(2),
    ${k} table tbody tr > td:nth-child(4) {
        font-family: var(--monospace);
    }
    
    ${k} table tbody tr > td:nth-child(2),
    ${k} table tbody tr > td:nth-child(4) {
        text-align: right;
    }
    
    ${k} table tbody tr > td:nth-child(1) {
        width: 160px;
    }
    
    ${k} table tbody tr > td:nth-child(2) {
        width: 80px;
    }
    
    ${k} table tbody tr > td:nth-child(4) {
        width: 160px;
    }
    
    ${k} table tbody tr > td:nth-child(2) {
        width: 160px;
    }
`);

const LargeJobsPanel: React.FunctionComponent = () => {
    const applications = [
        "Visual Studio Code",
        "RStudio",
        "JupyterLab",
        "MinIO",
        "Shiny",
        "Rsync",
        "Terminal",
        "MATLAB",
        "Maple"
    ];

    const fakeJobs: {jobId: string, timestamp: number, usage: number, application: string}[] = [];
    for (let i = 0; i < 50; i++) {
        const d = new Date();
        d.setHours(d.getHours() - Math.floor(Math.random() * 7 * 24));
        fakeJobs.push({
            jobId: Math.floor(Math.random() * 5000).toString(),
            timestamp: d.getTime(),
            usage: Math.random() * 300,
            application: applications[Math.floor(Math.random() * applications.length)],
        });
    }

    fakeJobs.sort((a, b) => {
        if (a.usage > b.usage) return -1;
        if (a.usage < b.usage) return 1;
        return 0;
    });

    return <div className={classConcat(CardClass, PanelClass, LargeJobsStyle)}>
        <div className="panel-title">
            <h4>Jobs by usage</h4>
        </div>

        <div className="table-wrapper">
            <table>
                <thead>
                <tr>
                    <th>Timestamp</th>
                    <th>Job ID</th>
                    <th>Application</th>
                    <th>Usage</th>
                </tr>
                </thead>
                <tbody>
                {fakeJobs.map(it => <tr key={it.jobId}>
                    <td>{dateToString(it.timestamp)}</td>
                    <td>{it.jobId}</td>
                    <td>{it.application}</td>
                    <td>{Accounting.addThousandSeparators(it.usage.toFixed(2))} DKK</td>
                </tr>)}
                </tbody>
            </table>
        </div>
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
        padding: 16px 0;
        height: calc(100vh - 60px);
    }
    
    ${k} .panel-grid:first-child {
        height: calc(100vh - 50px - 120px);
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

    ${k} .primary-grid-2-2 {
        display: grid;
        grid-template-columns: 1fr 1fr;
        grid-template-rows: 7fr 3fr;
        gap: 16px;
        width: 100%;
        height: 100%;
    }
    
    ${k} .primary-grid-2-1 {
        display: grid;
        grid-template-columns: 1fr 1fr;
        grid-template-rows: 1fr;
        gap: 16px;
        width: 100%;
        height: 100%;
    }
    
    ${k} table {
        width: 100%;
        border-collapse: separate;
        border-spacing: 0;
    }
    
    ${k} tr > td:first-child,
    ${k} tr > th:first-child {
        border-left: 2px solid var(--midGray);
    }

    ${k} td, 
    ${k} th {
        padding: 0 8px;
        border-right: 2px solid var(--midGray);
    }

    ${k} tbody > tr:last-child > td {
        border-bottom: 2px solid var(--midGray);
    }

    ${k} th {
        text-align: left;
        border-top: 2px solid var(--midGray);
        border-bottom: 2px solid var(--midGray);
        position: sticky;
        top: 0;
        background: var(--lightGray); /* matches card background */
    }
    
    ${k} .change > span:nth-child(1) {
        float: left;
    }
    
    ${k} .change > span:nth-child(2) {
        float: right;
    }
    
    ${k} .change.positive {
        color: var(--green);
    }
    
    ${k} .change.negative {
        color: var(--red);
    }
    
    ${k} .change.unchanged {
        color: var(--midGray);
    }
    
    ${k} .apexcharts-tooltip {
        color: var(--black);
    }
    
    ${k} .apexcharts-yaxis-title text,
    ${k} .apexcharts-yaxis-texts-g text,
    ${k} .apexcharts-xaxis-texts-g text {
        fill: var(--black);
    }
`);
const Visualization: React.FunctionComponent = props => {
    useLayoutEffect(() => {
        const wrappers = document.querySelectorAll(`.${VisualizationStyle} .table-wrapper`);
        const listeners: [Element, EventListener][] = [];
        wrappers.forEach(wrapper => {
            if (wrapper.scrollTop === 0) {
                wrapper.classList.add("at-top");
            }

            if (wrapper.scrollTop + wrapper.clientHeight >= wrapper.scrollHeight) {
                wrapper.classList.add("at-bottom");
            }

            const listener = () => {
                if (wrapper.scrollTop === 0) {
                    wrapper.classList.add("at-top");
                } else {
                    wrapper.classList.remove("at-top");
                }

                if (wrapper.scrollTop + wrapper.clientHeight >= wrapper.scrollHeight) {
                    wrapper.classList.add("at-bottom");
                } else {
                    wrapper.classList.remove("at-bottom");
                }

                console.log(wrapper.scrollTop, wrapper.scrollHeight, wrapper.clientHeight);
            };

            wrapper.addEventListener("scroll", listener);

            listeners.push([wrapper, listener]);
        });

        return () => {
            for (const [elem, listener] of listeners) {
                elem.removeEventListener("scroll", listener);
            }
        };
    }, []);

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
            <div style={{flexGrow: "1"}}/>
            <ContextSwitcher/>
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
                    chart={cpuChart1}
                />
                <SmallUsageCard
                    categoryName="u1-gpu"
                    usageText1="200K/1M GPU-hours"
                    usageText2="20% used"
                    change={1.2}
                    changeText="7d change"
                    chart={cpuChart2}
                />
                <SmallUsageCard
                    categoryName="u1-storage"
                    usageText1="340 TB/1 PB"
                    usageText2="34% used"
                    change={-3.2}
                    changeText="7d change"
                    chart={storageChart1}
                />
            </Flex>

            <div className="panels">
                <div className="panel-grid" id={"u1-standard"}>
                    <CategoryDescriptorPanel
                        productType="COMPUTE"
                        categoryName="u1-standard"
                        providerName="ucloud"
                    />

                    <BreakdownPanel/>

                    <div className="primary-grid-2-2">
                        <UsageOverTimePanel/>
                        <LargeJobsPanel/>
                        <MostUsedApplicationsPanel/>
                        <JobSubmissionPanel/>
                    </div>
                </div>

                <div className="panel-grid" id={"u1-storage"}>
                    <CategoryDescriptorPanel
                        productType="STORAGE"
                        categoryName="u1-storage"
                        providerName="ucloud"
                    />

                    <BreakdownPanel productType={"STORAGE"}/>

                    <div className="primary-grid-2-1">
                        <UsageOverTimePanel productType={"STORAGE"}/>
                        <Card/>
                    </div>
                </div>
            </div>
        </div>
    </div>;
};

function dummyChart(): UsageChart {
    const result: UsageChart = {dataPoints: []};
    const d = new Date();
    d.setHours(0, 0, 0);
    d.setDate(d.getDate() - 7);
    let currentUsage = 30000;
    for (let i = 0; i < 4 * 7; i++) {
        result.dataPoints.push({timestamp: d.getTime(), usage: currentUsage});
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
    const result: UsageChart = {dataPoints: []};
    const d = new Date();
    d.setHours(0, 0, 0);
    d.setDate(d.getDate() - 7);
    let currentUsage = 50;
    for (let i = 0; i < 4 * 7; i++) {
        result.dataPoints.push({timestamp: d.getTime(), usage: currentUsage});
        if (i % 2 === 0 && Math.random() >= 0.5) {
            currentUsage += 20 - (Math.random() * 40);
            currentUsage = Math.max(10, currentUsage);
        }
        d.setHours(d.getHours() + 6);
    }
    return result;
}

const cpuChart1 = dummyChart();
const cpuChart2 = dummyChart();
const storageChart1 = dummyChartQuota();

export default Visualization;
