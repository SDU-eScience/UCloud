import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {CardToolContainer, SmallCard, Tag, hashF} from "Applications/Card";
import {Spacer} from "ui-components/Spacer";
import * as Heading from "ui-components/Heading"
import {ShowAllTagItem} from "Applications/Overview";
import styled from "styled-components";
import {Box, Grid, Flex, theme} from "ui-components";
import * as Pages from "Applications/Pages";
import {EllipsedText} from "ui-components/Text";
import {Cloud} from "Authentication/SDUCloudObject";
import {toolImageQuery} from "Utilities/ApplicationUtilities";

export const Playground: React.FunctionComponent = () => {
    const url = Cloud.computeURL("/api", toolImageQuery("salmon"));
    const appTag = "Salmon";
    const tags = ["Featured", "BioformatttedFormatics"]
    const appPage = {
        itemsInTotal: 5,
        itemsPerPage: 25,
        pageNumber: 0,
        items: [
            {
                metadata: {
                    name: "salmon-alevin",
                    version: "0.12.0",
                    authors: ["R. Patro", "G. Duggal", "M. I. Love", "R. A. Irizarry", "C. Kingsford"],
                    title: "Salmon: alevin",
                    description: "Salmon-based processing of single-cell RNA-seq data.\n",
                    website: null
                },
                favorite: false,
                tags: ["Natural Science", "Bioinformatics", "Salmon"]
            }, {
                metadata: {
                    name: "salmon-index",
                    version: "0.12.0-1",
                    authors: ["R. Patro", "G. Duggal", "M. I. Love", "R. A. Irizarry", "C. Kingsford"],
                    title: "Salmon: index",
                    description: "Creates a Salmon index.\n",
                    website: null
                },
                favorite: false,
                tags: ["Natural Science", "Bioinformatics", "Salmon"]
            }, {
                metadata: {
                    name: "salmon-quant-alignment",
                    version: "0.12.0-1",
                    authors: ["R. Patro", "G. Duggal", "M. I. Love", "R. A. Irizarry", "C. Kingsford"],
                    title: "Salmon: quant alignment",
                    description: "Perform dual-phase, alignment-based estimation of transcript abundance from RNA-seq reads.\n",
                    website: null
                },
                favorite: false,
                tags: ["Natural Science", "Bioinformatics", "Salmon"]
            }, {
                metadata: {
                    name: "salmon-quantmerge",
                    version: "0.12.0",
                    authors: ["R. Patro", "G. Duggal", "M. I. Love", "R. A. Irizarry", "C. Kingsford"],
                    title: "Salmon: quantmerge",
                    description: "Merge multiple quantification results into a single file.\n",
                    website: null
                },
                favorite: false,
                tags: ["Natural Science", "Bioinformatics", "Salmon"]
            }, {
                metadata: {
                    name: "salmon-quant-reads",
                    version: "0.12.0",
                    authors: ["R. Patro", "G. Duggal", "M. I. Love", "R. A. Irizarry", "C. Kingsford"],
                    title: "Salmon: quant reads",
                    description: "Perform dual-phase, mapping-based estimation of transcript abundance from RNA-seq reads.\n",
                    website: null
                },
                favorite: false,
                tags: ["Natural Science", "Bioinformatics", "Salmon"]
            }],
        pagesInTotal: 1
    };
    return (
        <MainContainer
            main={(null)}
        />
    );
};

const ScrollBox = styled(Box)`
    overflow-x: scroll;
`;

function removeTagFromTitle(tag: string, title: string) {
    if (title.startsWith(tag)) {
        const titlenew = title.replace(/homerTools/g, "");
        if (titlenew.endsWith("pl")) {
            return titlenew.slice(tag.length + 2, -3);
        } else {
            return titlenew.slice(tag.length + 2);
        }
    } else {
        return title;
    }
}

function getColorFromName(name: string): [string, string, string] {
    const hash = hashF(name);
    const num = (hash >>> 22) % (theme.appColors.length - 1);
    return theme.appColors[num] as [string, string, string];
}