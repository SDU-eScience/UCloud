import {Task, TaskUpdate} from "BackgroundTasks/api";
import {ReduxObject} from "DefaultObjects";
import * as React from "react";
import {useCallback, useEffect, useRef, useState} from "react";
import {connect} from "react-redux";
import {
    Area,
    AreaChart,
    CartesianGrid,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis
} from "recharts";
import styled from "styled-components";
import Box from "ui-components/Box";
import Flex from "ui-components/Flex";
import * as Heading from "ui-components/Heading";
import IndeterminateProgressBar from "ui-components/IndeterminateProgress";
import ProgressBar from "ui-components/Progress";
import {groupBy, takeLast} from "Utilities/CollectionUtilities";

interface DetailedTaskOwnProps {
    taskId: string;
}

interface DetailedTaskStateProps {
    task?: TaskUpdate;
}

const DetailedTask: React.FunctionComponent<DetailedTaskOwnProps & DetailedTaskStateProps> = ({task}) => {
    if (task === undefined) {
        return null;
    }

    const [isScrollLocked, setScrollLocked] = useState(true);
    const ref = useRef<HTMLDivElement>(null);
    useEffect(() => {
        if (ref.current && isScrollLocked) {
            ref.current.scrollTop = ref.current.scrollHeight;
            setScrollLocked(true);
        }
    }, [ref, task.messageToAppend, isScrollLocked]);

    const checkScroll = useCallback(() => {
        if (ref.current && (ref.current.scrollTop + ref.current.offsetHeight) === ref.current.scrollHeight) {
            setScrollLocked(true);
        } else {
            setScrollLocked(false);
        }
    }, [isScrollLocked, ref]);

    return (
        <Box height={"100%"}>
            <Flex flexDirection={"column"} height={"100%"}>
                <Heading.h2>{task.newTitle ? task.newTitle : "Task"}</Heading.h2>

                <p><b>Status:</b> {task.newStatus || "No recent status update."}</p>

                {!task.progress ?
                    <IndeterminateProgressBar color={"green"} label={task.newTitle ? task.newTitle : ""}/> : (
                        <ProgressBar
                            active={true}
                            color={"green"}
                            label={
                                `${task.progress.title}: ${task.progress.current} of ${task.progress.maximum} ` +
                                `(${((task.progress.current / task.progress.maximum) * 100).toFixed(2)}%)`
                            }
                            percent={(task.progress.current / task.progress.maximum) * 100}
                        />
                    )}

                {task.speeds.length === 0 ? null : <Heading.h3>Speed Measurements</Heading.h3>}

                {Object.values(groupBy(task.speeds, it => it.title)).map(allSpeeds => {
                    const speeds = takeLast(allSpeeds, 50);
                    const lastElement = speeds[speeds.length - 1];
                    return (
                        <>
                            <Flex key={lastElement.title}>
                                <Box flexGrow={1}>{lastElement.title}</Box>
                                <Box>
                                    {lastElement.asText}
                                </Box>
                            </Flex>
                            <Container aspect={16 / 9} maxHeight={200}>
                                <AreaChart data={speeds}>
                                    <XAxis
                                        dataKey="clientTimestamp"
                                        type={"number"}
                                        domain={["dataMin", "dataMax"]}
                                        tickFormatter={() => ""}
                                    />
                                    <YAxis dataKey="speed" type={"number"}/>
                                    <CartesianGrid strokeDasharray="3 3"/>
                                    <Area
                                        isAnimationActive={false}
                                        type="monotone"
                                        stroke="#8884d8"
                                        dataKey="speed"
                                        name={lastElement.title}
                                    />
                                </AreaChart>
                            </Container>
                        </>
                    );
                })}

                {!task.messageToAppend ? null : (
                    <>
                        <Heading.h3>Output</Heading.h3>
                        <StatusBox ref={ref} onScroll={checkScroll}>
                            <pre><code>{task.messageToAppend}</code></pre>
                        </StatusBox>
                    </>
                )}
            </Flex>
        </Box>
    );
};

const Container = styled(ResponsiveContainer)`
    & > div > svg {
        overflow: visible;
    }
`;

const StatusBox = styled.div`
    margin-top: 16px;
    flex: 1 1 auto;
    overflow-y auto;
`;

const mapStateToProps = (state: ReduxObject, props: DetailedTaskOwnProps): DetailedTaskStateProps => ({
    task: state.tasks ? state.tasks[props.taskId] : undefined
});

export default connect(mapStateToProps, null)(DetailedTask);
