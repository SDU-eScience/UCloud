import * as React from "react";
import { Link } from "react-router-dom";
import { ProjectMetadata } from "./api";
import * as Heading from "ui-components/Heading";
import { Box, Flex, Text, Stamp, Divider, Card } from "ui-components";
import { projectViewPage } from "Utilities/ProjectUtilities";

export const SearchItem = ({ item }: { item: ProjectMetadata }) => (
    <Card height="154px" p="12px" mb="0.5em" mt="0.5em" borderRadius=".28571429rem">
        <Heading.h3><Link to={projectViewPage(item.sduCloudRoot)}>{item.title}</Link></Heading.h3>

        <Divider />
        <Box mt="1em" mb="1em">
            <Text>
                {firstParagraphWithLimitedLength(
                    defaultIfBlank(item.description, "No description"),
                    800
                )}
            </Text>
        </Box>
        <Divider mb="1em" />
        <Stamp bg="blue" color="white" borderColor="blue" ml="0.2em">
            <Flex>
                <Box pl="0.5em" pr="0.5em">
                    <i className="fas fa-book"></i>
                </Box>
                {item.license}
                <Text pl="0.2em" color="lightGrey">License</Text>
            </Flex>
        </Stamp>
    </Card>
);

const defaultIfBlank = (text: string, defaultValue: string): string => {
    if (text.length == 0) return defaultValue;
    else return text;
};

const firstParagraphWithLimitedLength = (text: string, maxLength: number): string => {
    const lines = text.split("\n");
    const paragraphEndsAt = lines.findIndex((line) => /^\s*$/.test(line));

    let firstParagraph: string;
    if (paragraphEndsAt == -1) firstParagraph = text;
    else firstParagraph = lines.slice(0, paragraphEndsAt).join("\n");

    if (firstParagraph.length > maxLength) {
        return firstParagraph.substring(0, maxLength) + "...";
    } else {
        return firstParagraph;
    }
};