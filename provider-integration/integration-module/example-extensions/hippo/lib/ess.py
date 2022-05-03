from esshttp import esshttp
import base64
import urllib3


################################################################################


urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
ess = None
def ess_authenticate(username, password, server):
    global ess
    token = f'{username}:{password}'
    token = token.encode('ascii')
    token = base64.b64encode(token)
    token = token.decode('ascii')
    ess = esshttp(server)
    return ess.auth_token(token)


################################################################################


"""
ess_fileset_query()
  return information about an existing filset
function arguments
  [m] filesystem   : filesystem name
  [m] fileset      : fileset name
return values
  path             : junction for fileset
  created          : date for fileset creation
  usage_bytes      : data size in bytes
  quota_bytes      : data quota in bytes
  usage_files      : number of files
  quota_files      : quota for number of files
"""
def ess_fileset_query(args):
    rets = {}
    fileset = args.get('fileset')
    filesystem = args.get('filesystem')

    if not filesystem:
        raise ValueError('missing variable: filesystem')

    if not fileset:
        raise ValueError('missing variable: fileset')

    result = ess.fileset_query(filesystem, fileset)
    error = result.get('error')
    quota = result.get('quota')
    fileset = result.get('fileset')

    if error:
        code = error.get('code')
        if code == 400:
            raise NameError('fileset not found')
        else:
            raise RuntimeError('unhandled ess error: %d' % code)

    rets['path'] = fileset['config']['path']
    rets['created'] = fileset['config']['created']
    rets['usage_bytes'] = quota['blockUsage']*1024
    rets['quota_bytes'] = quota['blockQuota']*1024
    rets['usage_files'] = quota['filesUsage']
    rets['quota_files'] = quota['filesQuota']

    return rets


"""
ess_fileset_create()
  create a new fileset
function arguments
  [m] filesystem   : filesystem name
  [m] fileset      : fileset name
  [m] path         : junction for the fileset
  [m] parent       : parent inode space
return values
  none
"""
def ess_fileset_create(args):
    rets = {}
    fileset = args.get('fileset')
    filesystem = args.get('filesystem')
    parent = args.get('parent')
    path = args.get('path')
    owner = args.get('owner')
    permissions = args.get('permissions')

    if not filesystem:
        raise ValueError('missing variable: filesystem')

    if not fileset:
        raise ValueError('missing variable: fileset')

    if not path:
        raise ValueError('missing variable: path')

    if not parent:
        raise ValueError('missing variable: parent')

    if not owner:
        raise ValueError('missing variable: owner')

    if not permissions:
        raise ValueError('missing variable: permissions')

    result = ess.fileset_create(filesystem, fileset, parent, path, owner, permissions)
    error = result.get('error')
    job = result.get('job')

    if error or result.get('status') != 'COMPLETED':
        RuntimeError('failed to create fileset')

    return rets


"""
ess_fileset_quota()
  set quota on an existing fileset
function arguments
  [m] filesystem   : filesystem name
  [m] fileset      : fileset name
  [m] path         : junction for the fileset
  [m] parent       : parent inode space
return values
  none
"""
def ess_fileset_quota(args):
    rets = {}
    fileset = args.get('fileset')
    filesystem = args.get('filesystem')
    space = args.get('space')
    files = args.get('files')

    if not filesystem:
        raise ValueError('missing variable: filesystem')

    if not fileset:
        raise ValueError('missing variable: fileset')

    if not space:
        raise ValueError('missing variable: space')

    if not files:
        raise ValueError('missing variable: files')

    result = ess.fileset_quota(filesystem, fileset, space, files)
    error = result.get('error')
    job = result.get('job')

    if error or result.get('status') != 'COMPLETED':
        RuntimeError('failed to set quota on fileset')

    return rets
