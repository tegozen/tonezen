#!/bin/sh
set -eu
envsubst '${DOWNLOAD_URL_SECRET}' < /etc/nginx/templates/secure_link.conf.template > /etc/nginx/conf.d/secure_link.conf
exec nginx -g 'daemon off;'
