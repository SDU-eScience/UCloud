// NOTE(Dan): A very silly lookahead parser. It is not really meant to be anything special, but we also don't have any
// needs for something fancy.
export class SillyParser {
    private input: string;
    private consumeWhitespaceBeforeTokens: boolean;
    private cursor: number = 0;

    constructor(input: string, consumeWhitespace: boolean = true) {
        this.input = input;
        this.consumeWhitespaceBeforeTokens = consumeWhitespace;
    }

    consumeToken(token: string): string | undefined {
        if (this.peek(token)) {
            this.cursor += token.length;
            return token;
        }
        return undefined;
    }

    consumeWord(peek: boolean = false): string {
        if (this.consumeWhitespaceBeforeTokens) this.consumeWhitespace();

        let builder: string = "";
        for (let i = this.cursor; i < this.input.length; i++) {
            const char = this.input[i];
            if (this.isWhitespace(char)) {
                if (!peek) this.cursor = i;
                break;
            }
            builder += char;
        }
        return builder;
    }

    peekWord(): string {
        return this.consumeWord(true);
    }

    consumeWhitespace() {
        for (let i = this.cursor; i < this.input.length; i++) {
            if (!this.isWhitespace(this.input[i])) {
                this.cursor = i;
                break;
            }
        }
    }

    peek(message: string): boolean {
        if (this.consumeWhitespaceBeforeTokens) this.consumeWhitespace();
        return this.remaining().startsWith(message);
    }

    remaining(): string {
        return this.input.substring(this.cursor);
    }

    private isWhitespace(char: string) {
        let isWhitespace = false;
        switch (char) {
            case " ":
            case "\t":
            case "\n":
            case "\r":
                isWhitespace = true;
                break;
        }
        return isWhitespace;
    }
}
