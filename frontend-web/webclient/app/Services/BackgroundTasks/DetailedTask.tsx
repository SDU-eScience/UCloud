import {TaskUpdate} from "@/Services/BackgroundTasks/api";
import * as React from "react";
import {useCallback, useEffect, useRef, useState} from "react";
import Box from "@/ui-components/Box";
import Flex from "@/ui-components/Flex";
import * as Heading from "@/ui-components/Heading";
import IndeterminateProgressBar from "@/ui-components/IndeterminateProgress";
import ProgressBar from "@/ui-components/Progress";
import {groupBy, takeLast} from "@/Utilities/CollectionUtilities";
import {injectStyle, injectStyleSimple} from "@/Unstyled";

const DetailedTask: React.FunctionComponent<{task: TaskUpdate}> = ({task}) => {
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
        <Box height="100%">
            <Flex flexDirection="column" height="100%">
                <Heading.h2>{task.newTitle ?? "Task"}</Heading.h2>

                <p><b>Status:</b> {task.newStatus ?? "No recent status update."}</p>

                {!task.progress ?
                    <IndeterminateProgressBar color="successMain" label={task.newTitle ?? ""} /> : (
                        <ProgressBar
                            active={true}
                            color="successMain"
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
                                <div>
                                    {lastElement.asText}
                                </div>
                            </Flex>
                        </>
                    );
                })}

                {!task.messageToAppend ? null : (
                    <>
                        <Heading.h3>Output</Heading.h3>
                        <div className={StatusBox} ref={ref} onScroll={checkScroll}>
                            <pre><code>{task.messageToAppend}</code></pre>
                        </div>
                    </>
                )}
            </Flex>
        </Box>
    );
};

const Container = injectStyle("container", k => `
  ${k} > div > svg {
    overflow: visible;
  }
`);

const StatusBox = injectStyleSimple("status-box", `
  margin-top: 16px;
  flex: 1 1 auto;
  overflow-y: auto;
`);

export default DetailedTask;
