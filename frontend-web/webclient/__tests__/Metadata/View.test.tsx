import * as React from "react";
import { View } from "Metadata/View";
import { create } from "react-test-renderer";
import { metadata } from "../mock/Metadata";
import { MemoryRouter } from "react-router";

describe("View component", () => {
    test("Mount view", () => 
        expect(create(<MemoryRouter><View metadata={metadata.metadata} canEdit={true} /></MemoryRouter>)).toMatchSnapshot()
    );
}) 