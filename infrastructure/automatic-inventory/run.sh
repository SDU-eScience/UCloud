#!/usr/bin/env bash
hosts=$1
echo "Retrieving hosts from: $hosts"

#ansible-playbook -i $hosts playbook.yml
#exit 0

tmp=/tmp/merged-inventory.json
template=html.mustache
cat output/*.json | json -g > $tmp
handlebars $tmp < $template > inventory.html
rm $tmp
