#!/usr/bin/env bash
# Launch the MCP Inspector against the workshop server.
# Works from anywhere: the server's working directory is always inspector/,
# so its log files land in inspector/logs/.
#
# --catalog (not --config) keeps the session writable, so servers can be
# added or edited from the UI. The web UI lists every server in the catalog;
# --server only applies to --cli mode.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

# config.json pins protocolEra to modern: 2026-07-28, no fallback to initialize.
# ./run.sh config-legacy.json connects the way a client on a handshake revision
# does instead: initialize first, then every request without the envelope.
npx @modelcontextprotocol/inspector@2.5.0 --web --catalog "${1:-config.json}"