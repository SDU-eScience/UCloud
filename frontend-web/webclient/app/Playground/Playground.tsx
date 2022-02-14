import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import {EveryIcon} from "@/ui-components/Icon";
import {Grid, Box} from "@/ui-components";
import theme, {ThemeColor} from "@/ui-components/theme";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {Accordion, AccordionWrapper} from "@/ui-components/Accordion";
import styled, {keyframes} from "styled-components";

export const Playground: React.FunctionComponent = () => {
    const main = (
        <>
            <Grid gridTemplateColumns={"repeat(5, 1fr)"} mb={"32px"}>
                <EveryIcon />
            </Grid>
            <Grid
                gridTemplateColumns="repeat(10, 1fr)"
                style={{overflowY: "scroll"}}
                mb={"32px"}
            >
                {colors.map((c: ThemeColor) => (
                    <Box
                        title={`${c}, ${getCssVar(c)}`}
                        key={c}
                        backgroundColor={c}
                        height={"100px"}
                        width={"100%"}
                    />
                ))}
            </Grid>

            <AccordionWrapper>
                <Accordion icon="cpu" title="Section 1" titleContent={<ProgressBarWithNegativeThing value={70} />}>
                    Just some content 1
                </Accordion>

                <Accordion icon="hdd" title="Section 2" titleContent={<ProgressBarWithNegativeThing value={55} />}>
                    Just some content 2
                </Accordion>

                <Accordion icon="hdd" title="Section 2" titleContent={<ProgressBarWithNegativeThing value={10} />}>
                    Just some content 3
                </Accordion>

                <Accordion icon="hdd" title="Section 2" titleContent={<ProgressBarWithNegativeThing value={90} />}>
                    Just some content 3
                </Accordion>
            </AccordionWrapper>

            <ConfirmationButton icon={"trash"} actionText={"Delete"} color={"red"} />
        </>
    );
    return <MainContainer main={main} />;
};

const animatePositive = keyframes`
    0% { width: 0%; }
`;

const animateNegative = keyframes`
    0% { width: 100%; }
`;

const thresholds: {maxValue: number, color: ThemeColor}[] = [
    {
        maxValue: 60, //Color stays red from 0% to 50%
        color: "green"
    },
    {
        maxValue: 70, //Color stays red from 0% to 50%
        color: "lightOrange"
    },
    {
        maxValue: 90, //Color goes green from 51% to 100%
        color: "red"
    }
];

function getColorFromValue(value: number): string {
    return thresholds.find(it => it.maxValue >= value)?.color ?? "green"
}


const Bar = styled.div<{value: number}>`
    position: absolute;
    top: 0;
    height: 100%;
    overflow: hidden;
    & > span {
        position: absolute;
        display: block;
        width: 150px;
        height: 100%;
        text-align: center;
    }

    &.positive {      
        background: var(--${props => getColorFromValue(props.value)});
        left: 0;
        width: ${props => props.value}%;      
        animation: ${animatePositive} 4s;
    }

    &.positive > span {
        left: 0;
        color: var(--white);
    }

    &.negative {
        background: var(--lightGray);
        right: 0;
        width: ${props => 100 - props.value}%;        
        animation: ${animateNegative} 4s;
    }

    &.negative > span {
        right: 0;
        color: var(--black);
    }
`;

const ProgressBar = styled.div<{value: number}>`
    border-radius: 4px;
    position: relative;
    border: #ccc solid 1px;
    width: 150px;
    height: 15px;
    line-height: 15px;
    vertical-align: middle;
    overflow: hidden;
    font-size: 12px;
    box-shadow: 0 4px 10px -5px rgba(0, 0, 0, 0.25);
`;

/* https://codepen.io/valiooo/pen/ALXodB */
function ProgressBarWithNegativeThing(props: React.PropsWithChildren<{value: number; width?: string; height?: string;}>): JSX.Element {
    /* TODO(Jonas): Use in components */
    const width = props.width ?? "150px";
    const height = props.height ?? "15px";
    /* TODO(Jonas): End */
    return (
        <ProgressBar value={props.value}>
            <Bar className="positive" value={props.value}>
                <span>{props.value}%</span>
            </Bar>
            <Bar className="negative" value={props.value}>
                <span>{props.value}%</span>
            </Bar>
        </ProgressBar>
    );
}

const colors = Object.keys(theme.colors);

export default Playground;
