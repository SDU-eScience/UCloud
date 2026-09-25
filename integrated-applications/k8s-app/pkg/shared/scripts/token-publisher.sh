#!/usr/bin/env bash
set -euo pipefail
source /etc/ucloud-k8s/bundle/common.sh

SERVER_TOKEN_FILE="$K3S_DATA_DIR/server/token"
AGENT_TOKEN_FILE="$K3S_DATA_DIR/server/agent-token"
PUBLISHED_SERVER_TOKEN="$MANAGEMENT_DIR/tokens/server"
PUBLISHED_AGENT_TOKEN="$MANAGEMENT_DIR/tokens/agent"

publish_role_token() {
	local role="$1"
	local tokenFile="$2"
	local destination="$3"

	if [ ! -s "$tokenFile" ]; then
		return 0
	fi
	if [ -s "$destination" ]; then
		if ! cmp -s "$tokenFile" "$destination"; then
			log "the published $role token differs from the cluster $role token"
		fi
		return 0
	fi
	umask 077
	install -o "$UCX_SERVICE_UID" -g "$UCX_SERVICE_GID" -m 0600 "$tokenFile" "$destination.tmp.$$"
	mv -f "$destination.tmp.$$" "$destination"
	log "published the $role token"
}

publish_token_file() {
	local content="$1"
	local destination="$2"

	if [ -s "$destination" ] && printf '%s' "$content" | cmp -s - "$destination"; then
		return 0
	fi
	umask 077
	printf '%s' "$content" | atomic_write_chowned "$destination" 0600
}

publish_node_tokens() {
	local serverToken
	local agentToken

	if [ ! -s "$PUBLISHED_SERVER_TOKEN" ] || [ ! -s "$PUBLISHED_AGENT_TOKEN" ]; then
		return 0
	fi

	serverToken="$(cat "$PUBLISHED_SERVER_TOKEN")"
	agentToken="$(cat "$PUBLISHED_AGENT_TOKEN")"

	if [ ! -d "$NODES_DIR" ]; then
		return 0
	fi

	local inputDir
	local nodeRole
	for inputDir in "$NODES_DIR"/*/input; do
		[ -d "$inputDir" ] || continue
		[ -f "$inputDir/node.json" ] || continue
		nodeRole="$(json_field "$inputDir/node.json" role)"
		case "$nodeRole" in
		control-plane)
			publish_token_file "$serverToken" "$inputDir/server-token.ca"
			publish_token_file "$agentToken" "$inputDir/agent-token.ca"
			;;
		*)
			publish_token_file "$agentToken" "$inputDir/agent-token.ca"
			;;
		esac
	done
}

install -d -m 0755 "$MANAGEMENT_DIR/tokens"

publisher_wait_tries=0
while [ $publisher_wait_tries -lt 180 ]; do
	if [ -s "$SERVER_TOKEN_FILE" ]; then
		break
	fi
	sleep 10
	publisher_wait_tries=$(( publisher_wait_tries + 1 ))
done
if [ ! -s "$SERVER_TOKEN_FILE" ]; then
	log "k3s never wrote the secure server token at $SERVER_TOKEN_FILE"
	exit 1
fi

publish_role_token "server" "$SERVER_TOKEN_FILE" "$PUBLISHED_SERVER_TOKEN"
publish_role_token "agent" "$AGENT_TOKEN_FILE" "$PUBLISHED_AGENT_TOKEN"
publish_node_tokens

while true; do
	publish_role_token "server" "$SERVER_TOKEN_FILE" "$PUBLISHED_SERVER_TOKEN"
	publish_role_token "agent" "$AGENT_TOKEN_FILE" "$PUBLISHED_AGENT_TOKEN"
	publish_node_tokens
	sleep 60
done
