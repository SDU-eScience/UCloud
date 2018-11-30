import styled from "styled-components";
import { Button, OutlineButton, Flex } from ".";


const VerticalButtonGroup = styled(Flex)`
    flex-direction: column;

    & ${Button}, & ${OutlineButton} {
        width: 100%;
        margin-bottom: 8px;
    }
`;

export default VerticalButtonGroup;