import styled from "styled-components";
import Text from "./Text";

const Truncate = styled(Text)`
    flex: 1;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
`;

Truncate.displayName = "Truncate";

export default Truncate;
