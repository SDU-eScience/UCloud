import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {EveryIcon, getCssVar} from "ui-components/Icon";
import {Grid, Box} from "ui-components";

export const Playground: React.FunctionComponent = () => {
    const main = (
        <>
            <EveryIcon />
            <Grid
                gridTemplateColumns="repeat(auto-fit, minmax(40px, 40px))"
                style={{overflowY: "scroll"}}
            >
                {colors.map(c => (
                    <Box
                        title={`${c}, ${getCssVar(c)}`}
                        key={c}
                        backgroundColor={c}
                        width="40px"
                        height="40px"
                    />
                ))}
            </Grid>
        </>
    );
    return <MainContainer main={main} />;
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
]