import * as React from "react";
import {IconBaseProps} from "./Icon";
import * as icons from "./icons";
import {getCssVar} from "Utilities/StyledComponentsUtilities";
import {ThemeColor} from "./theme";

const randomInt = (min: number, max: number): number => {
    return Math.floor(Math.random() * (max - min + 1)) + min;
};

function Bug({size, theme, color2, spin, ...props}: Omit<IconBaseProps, "name">): JSX.Element {
    const bugs: string[] = ["bug1", "bug2", "bug3", "bug4", "bug5", "bug6"];
    const [idx] = React.useState(randomInt(0, bugs.length - 1));

    const Component = icons[bugs[idx]];

    return (
        <Component width={size} height={size} color2={color2 ? getCssVar(color2 as ThemeColor) : undefined} {...props} />
    );
}

export default Bug;
