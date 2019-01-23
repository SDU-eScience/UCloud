import styled from "styled-components";
import { Box } from "ui-components";

export const LoadingBox = styled(Box) <{ loading: boolean }>`
    ${props => props.loading ? "opacity: 0.25; cursor: wait;" : null}
`;