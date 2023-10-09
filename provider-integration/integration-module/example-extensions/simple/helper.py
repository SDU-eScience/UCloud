import subprocess
import re

def run_command(cmd):
    result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    result.stdout = result.stdout.decode().strip()
    result.stderr = result.stderr.decode().strip()
    return result

def get_group_by_gid(gid):
    result = run_command(['getent', 'group', str(gid)])
    if result.returncode != 0:
        return None
    return result.stdout.split(':')[0]

def get_username_by_uid(uid):
    result = run_command(['getent', 'passwd', str(uid)])
    if result.returncode != 0:
        return None
    return result.stdout.split(':')[0]

def get_gid_by_group(name):
    result = run_command(['getent', 'group', name])
    if result.returncode != 0:
        return None
    return int(result.stdout.split(':')[2])

def get_uid_by_username(name):
    result = run_command(['id', '-u', name])
    if result.returncode != 0:
        return None
    else:
        return int(result.stdout)

def create_group(gid, name):
    result = run_command(['groupadd', '-g', str(gid), name])
    if result.returncode != 0:
        return False
    else:
        return True

def rename_group(gid, name):
    gn = get_group_by_gid(gid)
    if gn is None:
        return False
    result = run_command(['groupmod', '-n', name, gn])
    if result.returncode != 0:
        return False
    else:
        return True

def delete_group(gid):
    gn = get_group_by_gid(gid)
    if gn is None:
        return False
    result = run_command(['groupdel', gn])
    if result.returncode != 0:
        return False
    else:
        return True

def add_user_to_group(uid, gid):
    gn = get_group_by_gid(gid)
    un = get_username_by_uid(uid)
    if gn is None:
        return False
    if un is None:
        return False
    result = run_command(['usermod', '-a', '-G', gn, un])
    if result.returncode != 0:
        return False
    else:
        return True

def remove_user_from_group(uid, gid):
    gn = get_group_by_gid(gid)
    un = get_username_by_uid(uid)
    if gn is None:
        return False
    if un is None:
        return False
    result = run_command(['gpasswd', '-d', un, gn])
    if result.returncode != 0:
        return False
    else:
        return True

def slurm_account_set_quota(name, credits):
    result = run_command(['sacctmgr', '-i', 'modify', 'account', 'set', f'GrpTRESMins=billing={credits}', 'where', f'Name={name}'])
    if result.returncode != 0:
        return False
    else:
        return True

def slurm_account_create(name):
    result = run_command(['sacctmgr', '-i', 'create', 'account', name])
    if result.returncode != 0:
        return False
    else:
        return True

def slurm_account_remove_user(user, account):
    result = run_command(['sacctmgr', '-i', 'remove', 'user', user, f'Account={account}'])
    if result.returncode != 0:
        return False
    else:
        return True

def slurm_account_add_user(user, account):
    result = run_command(['sacctmgr', '-i', 'add', 'user', user, f'Account={account}'])
    if result.returncode != 0:
        return False
    else:
        return True

def slurm_user_create(name, defaccount):
    result = run_command(['sacctmgr', '-i', 'create', 'user', name, f'Account={defaccount}'])
    if result.returncode != 0:
        return False
    else:
        return True

def create_workspace(name):
    path = f'/work/{name}'
    result = run_command(['mkdir', '-p', path])
    if result.returncode != 0:
        return False
    result = run_command(['chown', f'root:{name}', path])
    if result.returncode != 0:
        return False
    result = run_command(['chmod', '0770', path])
    if result.returncode != 0:
        return False
    return True