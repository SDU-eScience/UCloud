import * as Bun from "bun";
const port = 3000;

const ESCAPE_CONTENT_LEVEL = "NONE. NONE AT ALL.";

interface Provider {
	name: string;
	link: string;
	image: string;
}

Bun.serve({
	port,
	static: {
		"/template.css": new Response(await Bun.file("./template.css").bytes(), {
			headers: {
				"Content-Type": "text/css"
			}
		}),
		"/Inter.woff2": new Response(await Bun.file("./Inter.woff2").bytes(), {
			headers: {
				"Content-Type": "text/woff2"
			}
		}),
		"/hippo.png": new Response(await Bun.file("./hippo.png").bytes(), {
			headers: {
				"Content-Type": "image/png"
			}
		}),
	},
	fetch(req: Request) {
		const url = new URL(req.url);
		if (url.pathname === "/") return new Response(makeFullTemplate(req), {
			headers: {
				"Content-Type": "text/html",
			}
		});
		return new Response("404!");
	},
});

function card(title: string, content: string): string {
	return el("div", `
		<h3>${title}</h3>
		${content}
	`, {className: "card"});
}

function title(provider: Provider): string {
	return el("div", `
		<div class="flex" style="margin-bottom:32px; margin-left: 20%;">
			<img class="provider-image" src="${provider.image}">
			<div class="block vertical-centering">
				<h4 style="margin-bottom: 4px;">${provider.name}</h4>
				<div>
					<a href="${provider.link}" target="_blank">Documentation</a>
				</div>
			</div>
		</div>`, {className: "centered flex"});
}

function username(provider: Provider, username: string) {
	return card(`${provider.name} username`, username);
}

function cardGroup(leftCard: string, rightCard: string): string {
	return el("div", leftCard + rightCard, {className: "card-group"});
}

function makeFullTemplate(request: Request): string {
	const provider = {name: "HIPPO", link: "https://example.com", image: "/hippo.png"}
	return html(
		provider,
		body(
			title(provider),
			cardGroup(username(provider, "Jonas_0451"), healthState()),
			cardGroup(upcomingDowntime(), maintenanceWindow(provider)),
			utilization(provider),
			description(provider),
		)
	);
}

function html(provider: Provider, content: string): string {
	return `
		<html lang="en">

			<head>
				<meta charset="UTF-8">
				<meta name="viewport" content="width=device-width, initial-scale=1.0">
				<title>Status for ${provider.name}</title>
				<link rel="stylesheet" href="/template.css">
			</head>

			${content}
		</html>
	`;
}

function upcomingDowntime(): string {
	return card("Downtimes", el("div", "No scheduled upcoming downtimes", {}));
}

function utilization(provider: Provider): string {
	return card("Utilization", `The ${provider.name} has X available products.` + allUtilizationGauges());
}

function allUtilizationGauges(): string {
	const mockProducts = ["Product A", "Product B", "Product C", "Product D", "Product E"];
	return el("div", mockProducts.map(utilizationGauge).join(""), {className: "container"});
}


const loadText = ["Very low", "Low", "Medium", "Extra Medium", "High", "Very high"];
const textColors = ["--green", "--green", "--yellow", "--yellow", "--red", "--red"];
const fillPercentage = [20, 40, 83, 86, 92, 99];

function randomLoad() {
	const randomValue = ((Math.random() * loadText.length) | 0) % loadText.length;
	return ({
		text: loadText[randomValue],
		textColor: textColors[randomValue],
		fillPercent: fillPercentage[randomValue]
	});
}

function utilizationGauge(name: string): string {
	const load = randomLoad();
	return el("div", `<svg viewBox="0 0 60 25">
		<text x="50%" y="50%" font-size="4px">${load.text}</text>
		<text x="50%" y="80%" font-size="4px">${name}</text>
	</svg>`, {className: "gauge", style: `--fill-percentage: ${load.fillPercent}; --fill-color: var(${load.textColor});`});
}

function description(provider: Provider): string {
	return card("Description", `The ${provider.name} system is a system consisting of large memory nodes (between 1 and 4 TB RAM per node) configured as a traditional Slurm cluster.`);
}

function maintenanceWindow(provider: Provider): string {
	return card("Maintenance", `<div>
		The maintenance windows for ${provider.name} are: <ul><li>Middle of July</li><li>Middle of February</li></ul>
		
		Maintenance can be expected to last the working hours of the entire day the maintenance will occur.
	</div>`);
}

function healthState(): string {
	return card("System health", "The health of the system is currently " + getSystemHealth());
}

function el(type: string, children: string, {className, style}: {className?: string; style?: string}) {
	return `<${type} ${className ? `class="${className}"` : ""} ${style ? `style="${style}"` : ""}>${children}</${type}>`
}

function getSystemHealth(): string {
	const result = (Math.random() * 3) | 0;
	switch (result) {
		case 0:
			return `<span style="color: var(--green);">good</span>`
		case 1:
			return `<span style="color: var(--yellow);">degraded</span>`
		case 2:
			return `<span style="color: var(--red);">bad</span>`
	}
	return "";
}

function body(...content: string[]): string {
	return el("body", content.join(""), {className: "content centered"});
}

console.log(`Now listening on port ${port}`);

