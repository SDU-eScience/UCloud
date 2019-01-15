import * as React from "react";
import { Link, Icon } from "ui-components";
import { ProjectMetadata } from "./api";
import * as Heading from "ui-components/Heading";
import { Box, Flex, Text, OldStamp, Divider, Card } from "ui-components";
import { projectViewPage } from "Utilities/ProjectUtilities";

interface SearchItemProps { item: ProjectMetadata }
export const SearchItem = ({ item }: SearchItemProps) => (
    <Card minHeight="168px" p="12px" mb="0.5em" mt="0.5em" borderRadius=".28571429rem">
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
        <OldStamp color="blue" ml="0.2em">
            <Flex>
                <Text color="lightGrey"><Icon name="license" mr="0.5em" size="1.5em"/>{item.license} License</Text>
            </Flex>
        </OldStamp>
    </Card>
);

const defaultIfBlank = (text: string, defaultValue: string): string =>
    text.length == 0 ? defaultValue : text;

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