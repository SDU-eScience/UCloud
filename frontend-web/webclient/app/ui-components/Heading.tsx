import * as React from "react";
import Text, { TextProps } from './Text'

interface HeaderProps extends TextProps {
    children: React.ReactChildren | React.ReactText
}

export const h1 = ({ children, ...props }: any) => (<Text as="h1" bold={true} fontSize={6} m={0} {...props}>{children}</Text>);
export const h2 = ({ children, ...props }: any) => (<Text as="h2" bold={true} fontSize={5} m={0} {...props}>{children}</Text>);
export const h3 = ({ children, ...props }: any) => (<Text as="h3" regular={true} fontSize={4} m={0} {...props}>{children}</Text>);
export const h4 = ({ children, ...props }: any) => (<Text as="h4" regular={true} fontSize={3} m={0} {...props}>{children}</Text>);
export const h5 = ({ children, ...props }: any) => (<Text as="h5" bold={true} fontSize={2} m={0} {...props}>{children}</Text>);
export const h6 = ({ children, ...props }: any) => (<Text as="h6" bold={true} caps={true} fontSize={0} m={0} {...props}>{children}</Text>);

const Heading = h3;

export default Heading;