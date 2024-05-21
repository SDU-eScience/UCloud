#!/usr/bin/env bash
# Go does not like reloading plugins which are really coming from the same module. This is understandable, since they
# really do not have the proper facilities for deploying this sort of stuff in a production environment. However, for
# a development environment I don't particularly care about leaking resources. As a result, we trick Go into believing
# that it is really a different module by using a new module name.
#pack=P`head -c 32 /dev/urandom | base64 | sed -r "s/[=+/]//g"`
#mv go.mod go.mod.old
#sed "s/module.*/module ${pack}/" go.mod.old > go.mod
go build -buildmode=plugin -o ./bin/module.so ucloud.dk/cmd/ucloud-im
#mv go.mod.old go.mod
