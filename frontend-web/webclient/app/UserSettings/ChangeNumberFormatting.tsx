import * as React from "react";
import {getDecimalSeparator, setDecimalSeparator, type DecimalSeparator} from "@/Utilities/NumberFormatting";
import {SettingsSection} from "@/ui-components/SettingsComponents";
import {Radio} from "@/ui-components";
import {Flex} from "@/ui-components";
import {Label} from "@/ui-components";

const separatorOptions: {value: DecimalSeparator; label: string; example: string}[] = [
    {value: ".", label: "Dot (1234.56)", example: "1,234.56"},
    {value: ",", label: "Comma (1234,56)", example: "1.234,56"},
];

export function ChangeNumberFormatting(): React.ReactNode {
    const [separator, setSeparator] = React.useState<DecimalSeparator>(() => getDecimalSeparator());

    const selectSeparator = React.useCallback((value: DecimalSeparator) => {
        setDecimalSeparator(value);
        setSeparator(value);
    }, []);

    return <SettingsSection
        id="number-formatting"
        title="Number formatting"
        description="Controls how decimal points and thousands separators are displayed, for example in prices and balances."
    >
        <Flex flexDirection="column" gap="8px">
            {separatorOptions.map(option => (
                <Label key={option.value} mt={0}>
                    <Flex alignItems="center" gap="8px">
                        <Radio
                            checked={separator === option.value}
                            onChange={() => selectSeparator(option.value)}
                        />
                        <span>{option.label}</span>
                    </Flex>
                </Label>
            ))}
            <div style={{color: "var(--textSecondary)", marginTop: "8px"}}>
                Example: {separatorOptions.find(option => option.value === separator)?.example}
            </div>
        </Flex>
    </SettingsSection>;
}
