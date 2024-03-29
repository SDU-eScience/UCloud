module.exports = {
    // Template for Icon Components
    template(
        {template},
        opts,
        {imports, componentName, props, jsx, exports}
    ) {
        const typeScriptTpl = template.smart({plugins: ['typescript']})
        const replacedName = componentName.name.replace("Svg", "");
        const name = replacedName[0].toLowerCase() + replacedName.substring(1);
        return typeScriptTpl.ast`
    export const ${name} = (props: any) => ${jsx};
  `
    },
    jsx: {
        babelConfig: {
            plugins: [
                // Some cleanup of svgs
                [
                    '@svgr/babel-plugin-remove-jsx-attribute',
                    {
                        elements: ['svg', 'path', 'g'],
                        attributes: ['xmlns:serif', 'serif:id', 'strokeLinejoin', 'strokeMiterlimit'],
                    },
                ],
            ],
        },
    },
    // Set default color
    svgProps: {
        fill: "currentcolor",
        //        viewBox: "0 0 24 24",
    },
    // replace icon colors with props.color && props.color2
    replaceAttrValues: {
        "#001833": "{undefined}",
        "#53657d": "{undefined}",
        "#8393a7": "{props.color2 ? props.color2 : \"currentcolor\" }"
    },
    // do not write width and height in svg
    dimensions: false,
}