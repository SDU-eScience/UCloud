import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import {EveryIcon} from "@/ui-components/Icon";
import {Grid, Box} from "@/ui-components";
import theme, {ThemeColor} from "@/ui-components/theme";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";

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
            <ConfirmationButton icon={"trash"} actionText={"Delete"} color={"red"} />
        </>
    );
    return <MainContainer main={main} />;
};

const colors = Object.keys(theme.colors);

export default Playground;
