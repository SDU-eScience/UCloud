# Styling

## Motivation

The motivation for this approach was to make a largely drop-in replacement for `styled-components`, a popular JavaScript library for CSS styling.
Investigating websites with larger userbases that use the library, revealed that the performance issues we were facing on UCloud using the library were not a result of us not using it correctly. The UCloud solution `Unstyled`, gave performance improvements, and provides what is needed for the styling. As a `devDependency`, `Unstyled` does depend on `styled-systems` which provides TypeScript definitions for styling.

## Using `injectStyle`

Styling for the frontend is primarily done with the function `injectStyle` found in the [/Unstyled/index.ts](https://github.com/sdu-eScience/UCloud/tree/master/frontend-web/webclient/app/Unstyled/index.ts)-file. This function dynamically injects CSS rules to a stylesheet at run-time.


<div>
<table class="table-wrapper">
<tr>
<td>
<b>injectStyle</b><br />
CSS-class with selector(s) and properties
</td>
<td>

```typescript
const NameOfClass = injectStyle("class-name", cl => `
    ${cl} {
        background-color: var(--errorMain);
    }

    ${cl}:hover {
        background-color: var(--primaryMain);
    }
`);
```

</td>
</tr>
<tr>
<td>
<b>injectStyleSimple</b><br/>
Implicit selector and explicit properties.
</td>
<td>

```typescript
const NameOfClass = injectStyleSimple("class-name", `
    background-color: red;
`);
```

</td>
</tr>
<tr>
<td>
Using the result
</td>
<td>

```typescript
function Component(): React.ReactNode {
    return <div className={NameOfClass} />
}
```

</td>
</table>
</div>


Using the class is the same as previously described above.

To ensure uniqueness, the function appends a number. The above could for instance return the string `class-name90`.

If just a classname is needed without any rules, see `makeClassName` function.

## Baseline for features

As the CSS standard is continuously updated, some features might be supported in one browser, and not another. Any CSS property used must be supported in Chrome, Safari, and Firefox. It should have a baseline of "Widely available" for at least 3 years.
