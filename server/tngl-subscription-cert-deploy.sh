#!/bin/sh
# Certbot deploy hook: reload the 3x-ui 3.11 web/sub servers; keep Xray running.
set -eu
if [ "${RENEWED_LINEAGE:-}" != "/etc/letsencrypt/live/tngl.pro" ]; then
    exit 0
fi
systemctl kill --kill-who=main --signal=HUP x-ui.service
