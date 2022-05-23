# Big JSON Viewer

[![npm](https://img.shields.io/npm/v/big-json-viewer.svg)](https://www.npmjs.com/package/big-json-viewer)
[![Travis](https://img.shields.io/travis/dhcode/big-json-viewer.svg)](https://travis-ci.org/dhcode/big-json-viewer)
[![Codecov](https://img.shields.io/codecov/c/github/dhcode/big-json-viewer.svg)](https://codecov.io/gh/dhcode/big-json-viewer)
[![code style: prettier](https://img.shields.io/badge/code_style-prettier-ff69b4.svg)](https://www.npmjs.com/package/big-json-viewer)

A JavaScript library that enables efficient working with large JSON data in the browser.

The JSON data is held as ArrayBuffer and only parsed for structural information.

Information about the top level nodes is provided. Pagination enabled browsing of arrays and objects.

No dependencies, works directly on the DOM API. Runs in any modern browser and IE11.

[View the Demo](https://dhcode.github.io/big-json-viewer/)

## Usage

    npm install big-json-viewer

## Example usage

test.ts

```typescript
import { BigJsonViewerDom } from 'big-json-viewer';

BigJsonViewerDom.fromData(JSON.stringify({ test: 23 })).then(viewer => {
    const node = viewer.getRootElement();
    document.body.appendChild(node);
    node.openAll(1);
});
```

index.html

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Test</title>
  <link rel="stylesheet" href="./node_modules/big-json-viewer/styles/default.css">
</head>
<body>
  <script src="src/test.ts"></script>
</body>
</html>
```

Example run with `parcel` (`npm install -D parcel-bundler`);

    parcel index.html

## More examples

See [examples/basic/index.html](examples/basic/index.html) for plain javascript example.

See [docs-src/demo.ts](docs-src/demo.ts) for a more advanced example.

## Getting started

You can use the following static method to get a new viewer instance:

```typescript
import { BigJsonViewerDom, BigJsonViewerOptions } from 'big-json-viewer';
BigJsonViewerDom.fromData(data: ArrayBuffer | string, options?: BigJsonViewerOptions): Promise<BigJsonViewerDom>
```

It returns a `BigJsonViewerDom` instance. Call `getRootElement()` on it to get a `JsonNodeElement`, that is an `HTMLDivElement` with some extras. You can insert it anywhere in your DOM.

## Options

When calling `fromData`, you can provide an object matching the interface `BigJsonViewerOptions`.

Example:

```javascript
{
  objectNodesLimit: 50, // how many properties of an object should be shows before it gets paginatated with a pagination size of 50
  arrayNodesLimit: 50, // same as objectNodesLimit, but with array elements
  labelAsPath: false // if true the label for every node will show the full path to the element
}
```

## API

## `BigJsonViewerDom` static methods

#### `fromData(data: ArrayBuffer | string, options?: BigJsonViewerOptions): Promise<BigJsonViewerDom>`

Initilizes a new viewer with JSON encoded data

#### `fromObject(data: string | object | null | number | boolean, options?: BigJsonViewerOptions): Promise<BigJsonViewerDom>`

Initializes a new viewer with JavaScript data

## `BigJsonViewerDom` methods

#### `getRootElement()`

Returns the `JsonNodeElement` that can be appended to the DOM.

#### `destroy()`

Call this to free resources. It will terminate any by the instance started worker.

#### `openBySearch(pattern: RegExp, openLimit?: number, searchArea?: TreeSearchAreaOption): TreeSearchCursor;`

Searches the tree by the specified `pattern` and `searchArea`. Returns a `TreeSearchCursor`, which contains all matches and methods to jump the focus between the matches.

*   `openLimit` is `1` by default. But can be `Infinity` or any number.
*   `searchArea` describes where the pattern should be searched. Has the following options:
    *   `'all'` search in keys and values (default)
    *   `'keys'` search only in keys
    *   `'values'` search only in values

## `JsonNodeElement` methods

#### `openNode()`

Opens the node in case it is an openable node. No event is fired.

#### `closeNode()`

Closes the node in case it is open. No event is fired.

#### `toggleNode()`

Toggles the open state of the node. Either opens or closes it. No event is fired.

#### `openPath(path: string[]): JsonNodeElement`

Opens the specified path and returns the opened node, in case it was found.

#### `openAll(maxDepth?: number, paginated?: PaginatedOption): number`

Opens all nodes until the defined depth. Returns the number of opened nodes.

*   `maxDepth` is `Infinity` by default
*   `paginated` is a string of the following options
    *   `'first'` open only the first pagination stub (default)
    *   `'all'` open all pagination stubs
    *   `'none'` open no pagination stubs

#### `getOpenPaths(withStubs?: boolean): string[][]`

Returns a list of opened paths.
`withStubs` is `true` by default. It makes sure, that paginated stubs that are opened are considered.

When you have a limit of 50 nodes and you open the second stub `[50 ... 99]`, a path it retuned that contains the name of the first node in the stub.

### `JsonNodeElement` Events

The following events are being fired on the visible DOM elements. The events bubble up, so you just need a listener to your root element.

#### openNode

Fires when a node is being opened by the user directly with a click. The target is a `JsonNodeElement`.

Example logs the opened path:

```javascript
rootNode.addEventListener('openNode', function(e) {
    console.log('openNode', e.target.jsonNode.path);
});
```

#### closeNode

Fires when a node is being closed. The target is a `JsonNodeElement`.

#### openedNodes

Fires when multiple nodes have been opened. Target is the top level `JsonNodeElement` that was used to trigger the action. E.g. when the user clicks the _Expand all_ link.

#### openStub

Fires when a pagination stub is being opened directly by the user with a click. The target is a `JsonNodesStubElement`.

#### closeStub

Fires when a pagination stub is being closed. The target is a `JsonNodesStubElement`.

#### copyPath

Fires when the user clicks on the Copy Path link of a node.

## Contributing

Anyone is welcome to contribute.

If something has changed that affects the Docs/Demo page, run:

    npm run build-docs

### Future TODOs

*   Improve display of large strings.
*   Support JSON Schema. If provided show meta information from the schema definition.

## License

[MIT](LICENSE)
