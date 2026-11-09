(function () {
    const codeBlocks = document.querySelectorAll("pre > code.language-mermaid")
    if (codeBlocks.length === 0) {
        return
    }

    const root = document.documentElement

    function diagramLabel(text, owner, kind, group = false) {
        const lines = text.split(/<br\s*\/?\s*>/i)
        const type = lines.length > 1 ? "<span class='diagram-type'>" + lines.shift() + "</span>" : ""
        const values = lines.join(" | ").split(/\s+\|\s+/)
        const name = "<span class='diagram-name'>" + values.shift() + "</span>"
        const attributes = values.map(function (value) {
            return "<span class='diagram-attributes'> | " + value + "</span>"
        }).join("")
        const icon = "<span class='diagram-icon diagram-icon-" + kind + "' aria-hidden='true'></span>"
        const header = group ? " diagram-group-header" : ""
        return "<span class='diagram-card diagram-" + owner + header + "'>" + icon +
            "<span class='diagram-copy'>" + type + "<span class='diagram-value'>" + name + attributes + "</span></span></span>"
    }

    const diagrams = []
    for (const block of codeBlocks) {
        const host = document.createElement("div")
        host.className = "mermaid"
        const source = block.textContent.trim()
        const groups = new Map()
        for (const match of source.matchAll(/^[ \t]*class[ \t]+(\w+)[ \t]+diagram-(provider|external)-([a-z0-9-]+)[ \t]*$/gm)) {
            groups.set(match[1], { owner: match[2], kind: match[3] })
        }
        host.textContent = source.replace(
            /subgraph\s+(\w+)\["([^"\n]+)"\]/g,
            function (match, id, text) {
                const group = groups.get(id)
                return group ? 'subgraph ' + id + '["' + diagramLabel(text, group.owner, group.kind, true) + '"]' : match
            },
        ).replace(
            /\["([^"\n]+)"\]:::diagram-(provider|external)-([a-z0-9-]+)/g,
            function (match, text, owner, kind) {
                return '["' + diagramLabel(text, owner, kind) + '"]:::diagram-' + owner + '-' + kind
            },
        )
        block.parentElement.replaceWith(host)
        diagrams.push({ host: host, source: host.textContent })
    }

    let loaded = false
    let renderedTheme = null
    let rendering = false
    let pending = false

    function isDark() {
        return root.classList.contains("dark")
    }

    function themeKey() {
        return isDark() ? "dark" : "light"
    }

    function themeConfig() {
        if (isDark()) {
            return {
                theme: "base",
                flowchart: { curve: "linear", padding: 4, wrappingWidth: 360, nodeSpacing: 20, rankSpacing: 56, subGraphTitleMargin: { top: 12, bottom: 12 } },
                themeVariables: {
                    darkMode: true,
                    fontFamily: "Roboto, sans-serif",
                    fontSize: "14px",
                    primaryColor: "#21262c",
                    primaryBorderColor: "#585858",
                    primaryTextColor: "#ffffff",
                    lineColor: "#ababab",
                    clusterBkg: "#21262c",
                    clusterBorder: "#585858",
                    edgeLabelBackground: "#1f1f1f",
                },
            }
        }
        return {
            theme: "base",
            flowchart: { curve: "linear", padding: 4, wrappingWidth: 360, nodeSpacing: 20, rankSpacing: 56, subGraphTitleMargin: { top: 12, bottom: 12 } },
            themeVariables: {
                darkMode: false,
                fontFamily: "Roboto, sans-serif",
                fontSize: "14px",
                primaryColor: "#ffffff",
                primaryBorderColor: "#ebebeb",
                primaryTextColor: "#000000",
                lineColor: "#cdcdcd",
                clusterBkg: "#ffffff",
                clusterBorder: "#ebebeb",
                edgeLabelBackground: "#ffffff",
            },
        }
    }

    function diagramBounds(element, parent) {
        const bounds = element.getBBox()
        const matrix = parent.getCTM().inverse().multiply(element.getCTM())
        return {
            left: matrix.a * bounds.x + matrix.e,
            right: matrix.a * (bounds.x + bounds.width) + matrix.e,
            top: matrix.d * bounds.y + matrix.f,
            bottom: matrix.d * (bounds.y + bounds.height) + matrix.f,
        }
    }

    function diagramSegmentIntersects(start, end, bounds) {
        return Math.max(start.x, end.x) > bounds.left && Math.min(start.x, end.x) < bounds.right &&
            Math.max(start.y, end.y) > bounds.top && Math.min(start.y, end.y) < bounds.bottom
    }

    function diagramGeometry(parent) {
        const svg = parent.ownerSVGElement
        const headers = []
        const boxes = []
        const obstacles = []
        for (const plate of svg.querySelectorAll("g.cluster > rect")) {
            const bounds = diagramBounds(plate, parent)
            const label = plate.parentElement.querySelector(":scope > .cluster-label")
            boxes.push(bounds)
            headers.push({ left: bounds.left, right: bounds.right, top: bounds.top, bottom: bounds.top + label.getBBox().height })
            obstacles.push({
                owner: bounds,
                bounds: { left: bounds.left - 12, right: bounds.right + 12, top: bounds.top - 20, bottom: bounds.top + label.getBBox().height + 4 },
            })
        }
        for (const node of svg.querySelectorAll("g.node")) {
            const bounds = diagramBounds(node, parent)
            boxes.push(bounds)
            obstacles.push({
                owner: bounds,
                bounds: { left: bounds.left - 4, right: bounds.right + 4, top: bounds.top - 4, bottom: bounds.bottom + 4 },
            })
        }
        return { headers: headers, boxes: boxes, obstacles: obstacles }
    }

    function diagramOnBoxBorder(bounds, point) {
        const inside = point.x >= bounds.left - 1 && point.x <= bounds.right + 1 &&
            point.y >= bounds.top - 1 && point.y <= bounds.bottom + 1
        const onEdge = Math.abs(point.x - bounds.left) < 1.5 || Math.abs(point.x - bounds.right) < 1.5 ||
            Math.abs(point.y - bounds.top) < 1.5 || Math.abs(point.y - bounds.bottom) < 1.5
        return inside && onEdge
    }

    function diagramStraightenEnd(points, geometry, atStart) {
        if (points.length < 3) {
            return null
        }
        const index = atStart ? 0 : points.length - 1
        const step = atStart ? 1 : -1
        const point = points[index]
        const neighbour = points[index + step]
        const next = points[index + step * 2]
        const length = Math.abs(point.x - neighbour.x) + Math.abs(point.y - neighbour.y)
        const vertical = Math.abs(point.x - neighbour.x) < 0.5
        const neighbourVertical = Math.abs(neighbour.x - next.x) < 0.5
        if (length > 20 || vertical === neighbourVertical) {
            return null
        }
        const owner = geometry.boxes.find(function (bounds) { return diagramOnBoxBorder(bounds, point) })
        if (owner === undefined) {
            return null
        }
        const candidate = vertical ? { x: next.x, y: point.y } : { x: point.x, y: next.y }
        if (!diagramOnBoxBorder(owner, candidate)) {
            return null
        }
        const blocked = geometry.obstacles.some(function (entry) {
            return entry.owner !== owner && diagramSegmentIntersects(candidate, next, entry.bounds)
        })
        return blocked ? null : { index: index, step: step, candidate: candidate }
    }

    function diagramStraighten(points, geometry) {
        const result = points.slice()
        const start = diagramStraightenEnd(result, geometry, true)
        if (start !== null) {
            result.splice(0, 2, start.candidate)
        }
        const end = diagramStraightenEnd(result, geometry, false)
        if (end !== null) {
            const last = result.length - 1
            result.splice(last - 1, 2, end.candidate)
        }
        return result
    }

    function diagramMerge(points) {
        const result = []
        for (const point of points) {
            const previous = result[result.length - 1]
            if (previous && previous.x === point.x && previous.y === point.y) {
                continue
            }
            const before = result[result.length - 2]
            if (before && ((before.x === previous.x && previous.x === point.x) ||
                (before.y === previous.y && previous.y === point.y))) {
                result.pop()
            }
            result.push(point)
        }
        return result
    }

    function diagramRouteHeaders(parent, points, occupied, targetNode) {
        const svg = parent.ownerSVGElement
        const geometry = diagramGeometry(parent)
        const headers = geometry.headers
        if (!headers.some(function (header) {
            return points.some(function (point, index) {
                return index > 0 && diagramSegmentIntersects(points[index - 1], point, header)
            })
        })) {
            return points
        }

        const obstacles = geometry.obstacles.map(function (entry) { return entry.bounds })

        const first = points[0]
        const next = points.find(function (point) { return point.x !== first.x || point.y !== first.y })
        const sourceHorizontal = Math.abs(next.x - first.x) > Math.abs(next.y - first.y)
        const source = {
            x: first.x + (sourceHorizontal ? Math.sign(next.x - first.x) * 8 : 0),
            y: first.y + (sourceHorizontal ? 0 : Math.sign(next.y - first.y) * 8),
        }
        const targetBounds = diagramBounds(targetNode, parent)
        const last = points[points.length - 1]
        const targetFraction = Math.max(0.2, Math.min(0.8, (last.x - targetBounds.left) / (targetBounds.right - targetBounds.left)))
        const targetY = last.y > targetBounds.top && last.y < targetBounds.bottom ? last.y :
            targetBounds.top + (targetBounds.bottom - targetBounds.top) * targetFraction
        const targets = [
            { x: targetBounds.left - 24, y: targetY, endpoint: { x: targetBounds.left, y: targetY } },
            { x: targetBounds.right + 24, y: targetY, endpoint: { x: targetBounds.right, y: targetY } },
        ]
        const xValues = [source.x].concat(targets.map(function (target) { return target.x }), points.map(function (point) { return point.x }))
        const yValues = [source.y, targetY].concat(points.map(function (point) { return point.y }))
        for (const bounds of obstacles) {
            xValues.push(bounds.left, bounds.right)
            yValues.push(bounds.top, bounds.bottom)
        }
        for (const header of headers) {
            for (const margin of [12, 20, 28]) {
                xValues.push(header.left - margin, header.right + margin)
                yValues.push(header.top - margin, header.bottom + margin)
            }
        }
        const xs = Array.from(new Set(xValues)).sort(function (a, b) { return a - b })
        const ys = Array.from(new Set(yValues)).sort(function (a, b) { return a - b })
        const columns = xs.length
        const sourceCell = ys.indexOf(source.y) * columns + xs.indexOf(source.x)
        const targetsByCell = new Map(targets.map(function (target) {
            return [ys.indexOf(target.y) * columns + xs.indexOf(target.x), target]
        }))
        const sourceState = sourceCell * 2 + (sourceHorizontal ? 0 : 1)
        const distances = new Array(xs.length * ys.length * 2).fill(Infinity)
        const predecessors = new Array(distances.length).fill(-1)
        const queue = [{ state: sourceState, cost: 0, estimate: 0 }]
        distances[sourceState] = 0
        let endState = -1
        while (queue.length > 0) {
            queue.sort(function (a, b) { return b.estimate - a.estimate })
            const current = queue.pop()
            if (current.cost !== distances[current.state]) {
                continue
            }
            const cell = Math.floor(current.state / 2)
            if (targetsByCell.has(cell)) {
                endState = current.state
                break
            }
            const x = cell % columns
            const y = Math.floor(cell / columns)
            const start = { x: xs[x], y: ys[y] }
            for (const [dx, dy] of [[-1, 0], [1, 0], [0, -1], [0, 1]]) {
                const nx = x + dx
                const ny = y + dy
                if (nx < 0 || nx >= xs.length || ny < 0 || ny >= ys.length) {
                    continue
                }
                const end = { x: xs[nx], y: ys[ny] }
                if (obstacles.some(function (bounds) { return diagramSegmentIntersects(start, end, bounds) })) {
                    continue
                }
                const direction = dx === 0 ? 1 : 0
                const state = (ny * columns + nx) * 2 + direction
                const length = Math.abs(end.x - start.x) + Math.abs(end.y - start.y)
                const midpoint = { x: (start.x + end.x) / 2, y: (start.y + end.y) / 2 }
                let deviation = Infinity
                for (let index = 1; index < points.length; index++) {
                    deviation = Math.min(deviation,
                        Math.max(0, Math.min(points[index - 1].x, points[index].x) - midpoint.x,
                            midpoint.x - Math.max(points[index - 1].x, points[index].x)) +
                        Math.max(0, Math.min(points[index - 1].y, points[index].y) - midpoint.y,
                            midpoint.y - Math.max(points[index - 1].y, points[index].y)))
                }
                let cost = current.cost + length + Math.min(deviation, 32) * length / 128
                if (direction !== current.state % 2) {
                    cost += 24
                }
                for (const segment of occupied) {
                    if (start.x === end.x && segment.start.x === start.x && segment.end.x === start.x) {
                        cost += 2 * Math.max(0, Math.min(Math.max(start.y, end.y), Math.max(segment.start.y, segment.end.y)) -
                            Math.max(Math.min(start.y, end.y), Math.min(segment.start.y, segment.end.y)))
                    } else if (start.y === end.y && segment.start.y === start.y && segment.end.y === start.y) {
                        cost += 2 * Math.max(0, Math.min(Math.max(start.x, end.x), Math.max(segment.start.x, segment.end.x)) -
                            Math.max(Math.min(start.x, end.x), Math.min(segment.start.x, segment.end.x)))
                    }
                }
                if (cost >= distances[state]) {
                    continue
                }
                distances[state] = cost
                predecessors[state] = current.state
                const remaining = targets.reduce(function (distance, target) {
                    return Math.min(distance, Math.abs(end.x - target.x) + Math.abs(end.y - target.y))
                }, Infinity)
                queue.push({ state: state, cost: cost, estimate: cost + remaining })
            }
        }
        if (endState === -1) {
            return points
        }
        const route = [targetsByCell.get(Math.floor(endState / 2)).endpoint]
        for (let state = endState; state !== -1; state = predecessors[state]) {
            const cell = Math.floor(state / 2)
            route.push({ x: xs[cell % columns], y: ys[Math.floor(cell / columns)] })
        }
        route.push(first)
        route.reverse()
        return diagramMerge(route)
    }

    function diagramHeaderLayout(layout) {
        return Object.assign({}, layout, {
            loader: async function () {
                const renderer = await layout.loader()
                return Object.assign({}, renderer, {
                    render: function (data, svg, helpers, options) {
                        const occupied = []
                        return renderer.render(data, svg, Object.assign({}, helpers, {
                            insertEdge: function (parent, edge, config, type, source, target, ...args) {
                                const node = parent.node()
                                const geometry = diagramGeometry(node)
                                edge.points = diagramRouteHeaders(node, edge.points, occupied, target.domId.node())
                                edge.points = diagramMerge(diagramStraighten(edge.points, geometry))
                                for (let index = 1; index < edge.points.length; index++) {
                                    occupied.push({ start: edge.points[index - 1], end: edge.points[index] })
                                }
                                return helpers.insertEdge(parent, edge, config, type, source, target, ...args)
                            },
                        }), options)
                    },
                })
            },
        })
    }

    function styleDiagram(host) {
        const svg = host.querySelector("svg")
        if (!svg) {
            return
        }
        for (const rect of svg.querySelectorAll("rect.basic.label-container, g.cluster > rect")) {
            rect.setAttribute("rx", "8")
            rect.setAttribute("ry", "8")
        }
        const titles = document.createElementNS("http://www.w3.org/2000/svg", "g")
        svg.appendChild(titles)
        for (const plate of svg.querySelectorAll("g.cluster > rect")) {
            const label = plate.parentElement.querySelector(":scope > .cluster-label")
            if (label === null) {
                continue
            }
            const text = label.querySelector("foreignObject > div")
            if (text) {
                text.style.background = getComputedStyle(plate).fill
                text.style.clipPath = "inset(1px 0)"
            }
            const bounds = plate.getBBox()
            const dividerY = bounds.y + label.getBBox().height
            const divider = document.createElementNS("http://www.w3.org/2000/svg", "line")
            divider.setAttribute("x1", bounds.x)
            divider.setAttribute("x2", bounds.x + bounds.width)
            divider.setAttribute("y1", dividerY)
            divider.setAttribute("y2", dividerY)
            divider.setAttribute("stroke", getComputedStyle(plate).stroke)
            plate.parentElement.appendChild(divider)
            const matrix = titles.getCTM().inverse().multiply(plate.getCTM()).translate(bounds.x + 16, bounds.y)
            label.setAttribute("transform", "matrix(" + [matrix.a, matrix.b, matrix.c, matrix.d, matrix.e, matrix.f].join(" ") + ")")
            titles.appendChild(label)
        }
        for (const marker of svg.querySelectorAll("marker.flowchart-v2")) {
            const start = marker.id.endsWith("pointStart")
            if (!start && !marker.id.endsWith("pointEnd")) {
                continue
            }
            const path = marker.querySelector("path.arrowMarkerPath")
            if (path === null) {
                continue
            }
            path.setAttribute("d", start ? "M 8 1.6 L 1 8 L 8 14.4" : "M 1 1.6 L 8 8 L 1 14.4")
            marker.setAttribute("viewBox", "0 0 9 16")
            marker.setAttribute("refX", start ? "5" : "4")
            marker.setAttribute("refY", "8")
            marker.setAttribute("markerWidth", "9")
            marker.setAttribute("markerHeight", "16")
        }
        const bounds = svg.getBBox()
        const margin = 4
        svg.setAttribute("viewBox", [bounds.x - margin, bounds.y - margin, bounds.width + 2 * margin, bounds.height + 2 * margin].join(" "))
        svg.style.maxWidth = bounds.width + 2 * margin + "px"
    }

    function loadMermaid() {
        return new Promise(function (resolve, reject) {
            const script = document.createElement("script")
            script.src = path_to_root + "mermaid.min.js"
            script.onload = resolve
            script.onerror = reject
            document.head.appendChild(script)
        })
    }

    async function render() {
        const theme = themeKey()
        if (theme === renderedTheme) {
            return
        }
        if (rendering) {
            pending = true
            return
        }
        rendering = true
        try {
            if (!loaded) {
                await loadMermaid()
                const elk = await import(new URL(path_to_root + "mermaid-elk/dist/mermaid-layout-elk.esm.min.mjs", document.baseURI).href)
                mermaid.registerLayoutLoaders(elk.default.map(diagramHeaderLayout))
                loaded = true
            }
            mermaid.initialize(Object.assign({ startOnLoad: false, securityLevel: "strict", layout: "elk" }, themeConfig()))
            for (let index = 0; index < diagrams.length; index++) {
                const diagram = diagrams[index]
                diagram.host.removeAttribute("data-processed")
                const id = "mermaid-" + index + "-" + Date.now()
                const rendered = await mermaid.render(id, diagram.source)
                diagram.host.innerHTML = rendered.svg
                diagram.host.setAttribute("data-processed", "true")
                if (rendered.bindFunctions) {
                    rendered.bindFunctions(diagram.host)
                }
            }
            for (const diagram of diagrams) {
                styleDiagram(diagram.host)
            }
            renderedTheme = theme
        } catch (error) {
            console.error("Could not render mermaid diagram", error)
        } finally {
            rendering = false
            if (pending) {
                pending = false
                void render()
            }
        }
    }

    new MutationObserver(function () {
        void render()
    }).observe(root, { attributes: true, attributeFilter: ["class"] })

    if (document.readyState === "complete") {
        void render()
    } else {
        window.addEventListener("load", function () {
            void render()
        }, { once: true })
    }
})()
