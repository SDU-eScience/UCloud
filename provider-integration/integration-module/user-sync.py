#!/usr/bin/env python3
# NOTE(Dan): A janky script for user-synchronization between docker containers (and really, only the setup we have).
# Does it work? Sure! Does it make a lot of assumptions? Absolutely!
#
# Things we could be improved, but probably don't need to:
# - Half-written files. Right now we just write directly to the output files (/etc/{passwd,group,shadow}). We should
#   instead write to a temporary file and then atomically replace it.
# - Bad assumptions about the base of the file remaining immutable
#   - Using the length of a string to remove the base. This fails badly if something is removed.
#   - Not synchronizing changes in lines of the base. This fails badly if, for example, an existing group is changed. 

import sys
import time

if len(sys.argv) != 3:
    print("Incorrect usage")
    exit(1)

is_pull = sys.argv[1] == 'pull'
directory = sys.argv[2]

# NOTE(Dan): For some reason the files were preserving changes over time which we don't intend. At build-time we
# create these files in the containers.
initial_passwd = open('/etc/passwd.orig', 'r').read()
initial_group = open('/etc/group.orig', 'r').read()
initial_shadow = open('/etc/shadow.orig', 'r').read()

if is_pull:
    while True:
        last_passwd = ''
        last_group = ''
        last_shadow = ''

        while True:
            new_base_passwd = open(f'{directory}/passwd', 'r').read()
            if last_passwd != new_base_passwd:
                last_passwd = new_base_passwd
                with open('/etc/passwd', 'w') as outfile:
                    outfile.write(initial_passwd + '\n' + new_base_passwd)

            new_base_group = open(f'{directory}/group', 'r').read()
            if last_group != new_base_group:
                last_group = new_base_group
                with open('/etc/group', 'w') as outfile:
                    outfile.write(initial_group + '\n' + new_base_group)

            new_base_shadow = open(f'{directory}/shadow', 'r').read()
            if last_shadow != new_base_shadow:
                last_shadow = new_base_shadow
                with open('/etc/shadow', 'w') as outfile:
                    outfile.write(initial_shadow + '\n' + new_base_shadow)

        time.sleep(5)

if not is_pull:
    try:
        base_passwd = open(f'{directory}/passwd', 'r').read()
        base_group = open(f'{directory}/group', 'r').read()
        base_shadow = open(f'{directory}/shadow', 'r').read()

        with open('/etc/passwd', 'w') as outfile:
            outfile.write(initial_passwd + '\n' + base_passwd)

        with open('/etc/group', 'w') as outfile:
            outfile.write(initial_group + '\n' + base_group)

        with open('/etc/shadow', 'w') as outfile:
            outfile.write(initial_shadow + '\n' + base_shadow)
    except:
        pass  # Ignored

    last_passwd = ''
    last_group = ''
    last_shadow = ''

    while True:
        new_base_passwd = open('/etc/passwd', 'r').read()[len(initial_passwd):]
        if last_passwd != new_base_passwd:
            last_passwd = new_base_passwd
            with open(f'{directory}/passwd', 'w') as outfile:
                outfile.write(new_base_passwd)

        new_base_group = open('/etc/group', 'r').read()[len(initial_group):]
        if last_group != new_base_group:
            last_group = new_base_group
            with open(f'{directory}/group', 'w') as outfile:
                outfile.write(new_base_group)

        new_base_shadow = open('/etc/shadow', 'r').read()[len(initial_shadow):]
        if last_shadow != new_base_shadow:
            last_shadow = new_base_shadow
            with open(f'{directory}/shadow', 'w') as outfile:
                outfile.write(new_base_shadow)

        time.sleep(5)

