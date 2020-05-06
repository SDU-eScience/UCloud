import styled from "styled-components";
import Text, {TextProps} from "./Text";
import {display, DisplayProps} from "styled-system";

const Truncate = styled(Text)<TextProps & DisplayProps>`
    flex: 1;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    ${display};
`;

Truncate.displayName = "Truncate";

export default Truncate;
