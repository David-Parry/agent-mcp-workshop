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

npx @modelcontextprotocol/inspector@2.5.0 --web --catalog config.json