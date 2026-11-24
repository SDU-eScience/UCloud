(function () {
    const blocks = document.querySelectorAll("pre > code.language-math")
    const inline = document.querySelectorAll(".math-inline")
    if (blocks.length === 0 && inline.length === 0) {
        return
    }

    const stylesheet = document.createElement("link")
    stylesheet.rel = "stylesheet"
    stylesheet.href = path_to_root + "katex/katex.min.css"
    document.head.appendChild(stylesheet)

    const script = document.createElement("script")
    script.src = path_to_root + "katex/katex.min.js"
    script.onload = function () {
        for (const block of blocks) {
            renderMath(block, true)
        }
        for (const element of inline) {
            renderMath(element, false)
        }
    }
    script.onerror = function () {
        console.error("Could not load KaTeX")
    }
    document.head.appendChild(script)

    function renderMath(source, displayMode) {
        const host = document.createElement(displayMode ? "div" : "span")
        host.className = displayMode ? "math-display" : "math-inline"
        try {
            katex.render(source.textContent.trim(), host, {
                displayMode: displayMode,
                throwOnError: true,
                trust: false,
                strict: "error",
            })
            const target = displayMode ? source.parentElement : source
            target.replaceWith(host)
        } catch (error) {
            console.error("Could not render math equation", error)
        }
    }
})()
