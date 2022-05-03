import subprocess
from subprocess import PIPE
from common import *
import re

def slurm_run_command(cmd):
    result = subprocess.run(cmd, check=True ,stdout=PIPE, stderr=PIPE)
    result.stdout = result.stdout.decode().strip()
    result.stderr = result.stderr.decode().strip()
    return result


def slurm_account_exists(name, info=None):
    cmd = ['sacctmgr', '-snP', 'show', 'account', name, 'format=desc,org,parentname,qos,user']
    result = slurm_run_command(cmd)

    if not result.stdout:
        return False
    if info is None:
        return True

    lines = result.stdout.splitlines()
    first = lines[0].split('|')
    lines = lines[1:]
    users = []

    for line in lines:
        line = line.split('|')
        users.append(line[4])

    info['description'] = first[0]
    info['organization'] = first[1]
    info['parent'] = first[2]
    info['qos'] = first[3].split(',')
    info['users'] = users
    return True


def slurm_user_exists(name, info=None):
    cmd = ['sacctmgr', '-snP', 'show', 'user', name, 'format=defaultaccount,account,adminlevel,qos']
    result = slurm_run_command(cmd)

    if not result.stdout:
        return False
    if info is None:
        return True

    lines = result.stdout.splitlines()
    first = lines[0].split('|')
    accts = []

    for line in lines:
        line = line.split('|')
        accts.append(line[1])

    info['default'] = first[0]
    info['accounts'] = accts
    info['privilege'] = first[2].lower().replace('administrator', 'admin')
    info['qos'] = first[3].split(',')
    return True


def slurm_parse_qos(name):
    cmd = ['sacctmgr', '-nP', 'show', 'qos', 'format=name']
    result = slurm_run_command(cmd)
    result = result.stdout.splitlines()

    sign = name[0]
    name = name[1:]

    if sign not in ('+', '-'):
        ValueError('invalid variable: qos')

    name = name.split(',')
    for qos in name:
        if qos == 'normal':
            raise ValueError('invalid qos: normal')
        if qos not in result:
            raise NameError('slurm qos does not exist')

    name = 'qos' + sign + '=' + ','.join(name)
    return name


def slurm_time_in_seconds(time):
    p = re.search('((\d+)-)?(\d+):(\d+):(\d+)', time)
    if p.group(2):
        d = int(p.group(2))
    else:
        d = 0
    h = int(p.group(3))
    m = int(p.group(4))
    s = int(p.group(5))
    return (86400*d + 3600*h + 60*m + s)


################################################################################


"""
slurm_account_create()
  create a new slurm account
function arguments
  [m] account      : account name
  [o] credits      : assigned credits
  [o] parent       : name of parent account (default is root)
  [o] description  : free text description
  [o] organization : free text organization name
return values
  none
"""
def slurm_account_create(args):
    rets = {}
    account = args.get('account')
    credits = args.get('credits')
    parent = args.get('parent')
    cmd = ['sacctmgr', '-i', 'add', 'account']

    if not account:
        raise ValueError('missing variable: account')

    if not validate_name(account):
        raise ValueError('invalid variable: account')

    if slurm_account_exists(account):
        raise NameError('slurm account already exists')

    cmd.append(account)
    cmd.append('DefaultQoS=normal')

    if credits:
        try:
            credits = int(credits)
        except:
            raise ValueError('invalid variable: credits')
    else:
        credits = 0

    cmd.append('Fairshare=%d' % int(credits/24/60))
    cmd.append('GrpTRESMins=billing=%d' % credits)

    if args.get('description'):
        cmd.append('Description="%s"' % args['description'])

    if args.get('organization'):
        cmd.append('Organization=%s' % args['organization'])

    if parent and not slurm_account_exists(parent):
        raise NameError('parent slurm account does not exist')

    if parent:
        cmd.append('Parent=%s' % parent)

    slurm_run_command(cmd)
    return rets


"""
slurm_account_delete()
  delete an empty slurm account
function arguments
  [m] account      : account name
return values
  none
"""
def slurm_account_delete(args):
    rets = {}
    info = {}
    account = args.get('account')
    cmd = ['sacctmgr', '-i', 'remove', 'account', account]

    if not account:
        raise ValueError('missing variable: account')

    if not slurm_account_exists(account, info):
        raise NameError('slurm account does not exist')

    if info.get('users'):
        raise RuntimeError('cannot delete non-empty slurm account')

    slurm_run_command(cmd)
    return rets


"""
slurm_account_modify()
  modify an existing slurm account
function arguments
  [m] account      : account name
  [o] credits      : assigned credits
  [o] qos          : add (+) or remove (-) qos (e.g. '+long,standby')
  [o] description  : free text description
  [o] organization : free text organization name
return values
  none
"""
def slurm_account_modify(args):
    rets = {}
    account = args.get('account')
    credits = args.get('credits')
    qos = args.get('qos')
    cmd = ['sacctmgr', '-i', 'modify', 'account', 'set']

    if not account:
        raise ValueError('missing variable: account')

    if not slurm_account_exists(account):
        raise NameError('slurm account does not exist')

    if qos:
        qos = slurm_parse_qos(qos)
        cmd.append(qos)

    if credits:
        try:
            credits = int(credits)
        except:
            raise ValueError('missing variable: credits')
        cmd.append('Fairshare=%d' % int(credits/24/60))
        cmd.append('GrpTRESMins=billing=%d' % credits)

    if args.get('description'):
        cmd.append('Description="%s"' % args['description'])

    if args.get('organization'):
        cmd.append('Organization=%s' % args['organization'])

    cmd.append('where')
    cmd.append('Name=%s' % account)

    slurm_run_command(cmd)
    return rets


"""
slurm_account_query()
  return information about a slurm account
function parameters
  [m] account      : account name
return values
  parent           : name of parent account
  credits          : assigned credits
  usage            : used credits
  fairshare        : fairshare number
  qos              : list of qos
  users            : list of users
  description      : free text description
  organization     : free text organization name
"""
def slurm_account_query(args):
    rets = {}
    info = {}
    account = args.get('account')

    if not account:
        raise ValueError('missing variable: account')

    if not slurm_account_exists(account, info):
        raise NameError('slurm account does not exist')

    cmd = ['sshare', '-lhPA', account, '-o', 'user,rawshares,rawusage,grptresmins']
    line = slurm_run_command(cmd)
    line = line.stdout.split('|')

    try:
        value = re.search('billing=(\d+)', line[3]).group(1)
        rets['credits'] = int(value)
    except:
        pass

    rets['fairshare'] = int(line[1])
    rets['usage'] = int(line[2])//60
    rets['description'] = info['description']
    rets['organization'] = info['organization']
    rets['parent'] = info['parent']
    rets['qos'] = info['qos']
    rets['users'] = info['users']
    return rets


"""
slurm_account_add_user()
  add an existing slurm user to a slurm account
function parameters
  [m] account      : account name
  [m] user         : username
return values
  none
"""
def slurm_account_add_user(args):
    rets = {}
    info = {}
    account = args.get('account')
    user = args.get('user')
    cmd = ['sacctmgr', '-i', 'add', 'user']

    if not account:
        raise ValueError('missing variable: account')

    if not user:
        raise ValueError('missing variable: user')

    if not slurm_account_exists(account):
        raise NameError('slurm account does not exist')

    if not slurm_user_exists(user, info):
        raise NameError('slurm user does not exist')

    if account in info.get('accounts'):
        return rets

    cmd.append(user)
    cmd.append('Account=%s' % account)

    slurm_run_command(cmd)
    return rets


"""
slurm_account_remove_user()
  remove a slurm user from a slurm account
function parameters
  [m] account      : account name
  [m] user         : username
return values
  none
"""
def slurm_account_remove_user(args):
    rets = {}
    info = {}
    account = args.get('account')
    user = args.get('user')
    cmd = ['sacctmgr', '-i', 'remove', 'user']

    if not account:
        raise ValueError('missing variable: account')

    if not user:
        raise ValueError('missing variable: user')

    if not slurm_account_exists(account):
        raise NameError('slurm account does not exist')

    if not slurm_user_exists(user, info):
        raise NameError('slurm user does not exist')

    if info.get('default') == account:
        raise RuntimeError('cannot remove user from their default account')

    if account not in info.get('accounts'):
        return rets

    cmd.append(user)
    cmd.append('Account=%s' % account)

    slurm_run_command(cmd)
    return rets


################################################################################


"""
slurm_user_create()
  create a new slurm user
function parameters
  [m] account      : name of default account
  [m] user         : username
  [o] privilege    : privilege level (none, operator, admin)
return values
  none
"""
def slurm_user_create(args):
    rets = {}
    account = args.get('account')
    user = args.get('user')
    privilege = args.get('privilege')
    cmd = ['sacctmgr', '-i', 'create', 'user']

    if not account:
        raise ValueError('missing variable: account')

    if not user:
        raise ValueError('missing variable: user')

    if not validate_name(user):
        raise ValueError('invalid variable: user')

    if not slurm_account_exists(account):
        raise NameError('slurm account does not exist')

    if slurm_user_exists(user):
        raise NameError('slurm user already exists')

    cmd.append(user)
    cmd.append('Account=%s' % account)

    if privilege and privilege not in ('none', 'admin', 'operator'):
        raise ValueError('invalid variable: privilege')

    if privilege:
        cmd.append('AdminLevel=%s' % privilege)

    slurm_run_command(cmd)
    return rets


"""
slurm_user_delete()
  delete an existing slurm user
function parameters
  [m] user         : username
return values
  none
"""
def slurm_user_delete(args):
    rets = {}
    user = args.get('user')
    cmd = ['sacctmgr', '-i', 'delete', 'user', user]

    if not user:
        raise ValueError('missing variable: user')

    if not slurm_user_exists(user):
        raise NameError('slurm user does not exist')

    slurm_run_command(cmd)
    return rets


"""
slurm_user_query()
  return information about a slurm user
function parameters
  [m] user         : username
return values
  default          : name of default account
  accounts         : list of all associated accounts
  privilege        : privilege level (none, operator, admin)
  qos              : list of qos
"""
def slurm_user_query(args):
    rets = {}
    info = {}
    user = args.get('user')

    if not user:
        raise ValueError('missing variable: user')

    if not slurm_user_exists(user, info):
        raise NameError('slurm user does not exist')

    rets['default'] = info['default']
    rets['accounts'] = info['accounts']
    rets['privilege'] = info['privilege']
    rets['qos'] = info['qos']
    return rets


"""
slurm_user_modify()
  modify an existing slurm user
function parameters
  [m] user         : username
  [o] account      : name of default account
  [o] privilege    : privilege level (none, operator, admin)
  [o] qos          : add (+) or remove (-) qos (e.g. '+long,standby')
return values
  none
"""
def slurm_user_modify(args):
    rets = {}
    qos = args.get('qos')
    user = args.get('user')
    account = args.get('account')
    privilege = args.get('privilege')
    cmd = ['sacctmgr', '-i', 'modify', 'user', 'set']

    if not user:
        raise ValueError('missing variable: user')

    if privilege and privilege not in ('none', 'admin', 'operator'):
        raise ValueError('invalid variable: privilege')

    if account and not slurm_account_exists(account):
        raise NameError('slurm account does not exist')

    if not (account or privilege or qos):
        return rets

    if qos:
        qos = slurm_parse_qos(qos)
        cmd.append(qos)

    if account:
        cmd.append('Account=%d' % account)

    if privilege:
        cmd.append('AdminLevel=%s' % privilege)

    cmd.append('where')
    cmd.append('Name=%s' % user)

    slurm_run_command(cmd)
    return rets


################################################################################

# we need to talk about this function
# sbatch --uid=username
def slurm_job_submit(args):
    rets = {}
    return rets


"""
slurm_job_query()
  return information about a slurm job
function parameters
  [m] jobid        : numeric job id
return values
  name             : job name
  user             : username of job owner
  account          : name of billing account
  partition        : slurm partition name
  state            : current state (pending, running, completed, etc)
  runtime          : current run time (seconds)
  timelimit        : wall time limit (seconds)
"""
def slurm_job_query(args):
    rets = {}
    jobid = args.get('jobid')
    cmd = ['sacct', '-o', 'State,User,Account,JobName,Partition,Elapsed,TimeLimit', '-XnPj']

    if not jobid:
        raise ValueError('missing variable: jobid')

    try:
        jobid = int(jobid)
    except:
        raise ValueError('invalid variable: jobid')

    cmd.append('%d' % jobid)
    line = slurm_run_command(cmd)
    if not line.stdout:
        raise NameError('slurm job id not found')

    line = line.stdout.split('|')
    rets['state'] = line[0].lower()
    rets['user'] = line[1]
    rets['account'] = line[2]
    rets['name'] = line[3]
    rets['partition'] = line[4]
    rets['runtime'] = slurm_time_in_seconds(line[5])
    rets['timelimit'] = slurm_time_in_seconds(line[6])
    return rets


"""
slurm_job_cancel()
  cancel a job in the slurm queue (regardless of job status and existence)
function parameters
  [m] jobid        : numeric job id
return values
  none
"""
def slurm_job_cancel(args):
    rets = {}
    jobid = args.get('jobid')

    if not jobid:
        raise ValueError('missing variable: jobid')

    try:
        jobid = int(jobid)
    except:
        raise ValueError('invalid variable: jobid')

    cmd = ['scancel', '%d' % jobid]
    slurm_run_command(cmd)
    return rets
