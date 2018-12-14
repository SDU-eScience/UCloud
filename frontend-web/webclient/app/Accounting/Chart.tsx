import * as React from "react";
import * as API from "./api";
import * as MockedChart from "./mock_chart.json";
import * as moment from "moment";
import { LineChart, CartesianGrid, XAxis, YAxis, Tooltip, Legend, Line, ResponsiveContainer } from "recharts";
import { fileSizeToString } from "Utilities/FileUtilities";

interface ChartProps {
    chart?: API.Chart<API.DataPoint2D>
}

function getOrElse<T>(idx: number, otherwise: T, array?: (T | null)[]): T {
    if (array === null || array === undefined) return otherwise;
    if (array.length < idx + 1) return otherwise;
    const element = array[idx];
    if (element === null) return otherwise;
    return element;
}

function formatDataType(type: string, value: any): string {
    switch (type) {
        case API.ChartDataTypes.BYTES: {
            if (typeof value === 'number') {
                return fileSizeToString(value);
            }
            break;
        }

        case API.ChartDataTypes.DATE: {
            if (typeof value === 'number') {
                return moment(value).format("DD/MM");
            }
            break;
        }

        case API.ChartDataTypes.DATETIME: {
            if (typeof value === 'number') {
                return moment(value).format("DD/MM hh:mm");
            }
            break;
        }

        case API.ChartDataTypes.DURATION: {
            if (typeof value === 'number') {
                return moment.duration(value, "seconds").humanize();
            }
            break;
        }
    }

    return "" + value;
}

class Chart extends React.Component<ChartProps> {
    render() {
        const chart: API.Chart<API.DataPoint2D> = this.props.chart || MockedChart.chart;

        const normalizedData = chart.data.map(d => {
            const xType = getOrElse(0, API.ChartDataTypes.NUMBER, chart.dataTypes)
            let result: { name: string, value: any } = {
                name: formatDataType(xType, d.x),
                value: d.y
            };

            return result;
        });

        return <ResponsiveContainer aspect={16 / 9}>
            <LineChart data={normalizedData}>
                <XAxis
                    dataKey="name"
                    tickCount={12}
                />

                <YAxis
                    dataKey="value"
                    tickFormatter={(d: number) => 
                        formatDataType(getOrElse(1, API.ChartDataTypes.NUMBER, chart.dataTypes), d)} 
                />

                <CartesianGrid strokeDasharray="3 3" />
                <Tooltip 
                    formatter={(d: number) => 
                        formatDataType(getOrElse(1, API.ChartDataTypes.NUMBER, chart.dataTypes), d)} 
                />
                <Legend />
                <Line
                    type="monotone"
                    stroke="#8884d8"
                    dataKey="value"
                    name={chart.dataTitle || "Value"}
                />
            </LineChart>
        </ResponsiveContainer>;
    }
}

export default Chart;