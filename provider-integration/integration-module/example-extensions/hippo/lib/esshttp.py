import requests
import time

class esshttp:
    def __init__(self, server):
        self.server = server
        self.baseurl = f'https://{server}/scalemgmt/v2'
        self.sslcert = False
        self.token = None
        self.tmout = 15
        self.session = requests.Session()

    def auth_token(self, token):
        self.token = token
        result = self.get('info')
        error = result.get('error')
        if not error:
            return True
        self.token = None
        return False

    def get(self, url):
        url = f'{self.baseurl}/{url}'
        header = { 'Authorization' : f'Basic {self.token}', 'Accept' : 'application/json' }
        result = self.session.get(url, headers=header, verify=self.sslcert)
        code = result.status_code
        result = result.json()
        status = result.pop('status')
        if code == 200:
            result['error'] = None
        else:
            result['error'] = status
        return result

    def post(self, url, data):
        url = f'{self.baseurl}/{url}'
        header = { 'Authorization' : f'Basic {self.token}', 'Accept' : 'application/json' }
        result = self.session.post(url, headers=header, json=data, verify=self.sslcert)
        code = result.status_code
        result = result.json()
        status = result.pop('status')
        if code in (201, 202):
            result['error'] = None
        else:
            result['error'] = status
        return result

    def put(self, url, data):
        url = f'{self.baseurl}/{url}'
        header = { 'Authorization' : f'Basic {self.token}', 'Accept' : 'application/json' }
        result = self.session.put(url, headers=header, json=data, verify=self.sslcert)
        code = result.status_code
        result = result.json()
        status = result.pop('status')
        if code in (200, 202):
            result['error'] = None
        else:
            result['error'] = status
        return result

    def delete(self, url):
        url = f'{self.baseurl}/{url}'
        header = { 'Authorization' : f'Basic {self.token}', 'Accept' : 'application/json' }
        result = self.session.delete(url, headers=header, verify=self.sslcert)
        code = result.status_code
        result = result.json()
        status = result.pop('status')
        if code in (202, 204):
            result['error'] = None
        else:
            result['error'] = status
        return result

    def job_wait(self, jobid, info=None, tmout=None):
        url = f'jobs/{jobid}'
        status = False
        if not tmout:
            tmout = self.tmout
        for n in range(tmout):
            result = self.get(url)
            result = result.get('jobs').pop()
            if result.get('status') != 'RUNNING':
                status = True
                break
            time.sleep(1)
        if info is not None:
            info.update(result)
        return status

    def fileset_exists(self, filesystem, fileset):
        url = f'filesystems/{filesystem}/filesets/{fileset}'
        result = self.get(url)
        error = result.get('error')
        if not error:
            return True
        if error['code'] == 400: # We could also check the message:> Invalid value in 'filesetName'
            return False
        raise RuntimeError('esshttp: unknown fileset status')

    def fileset_query(self, filesystem, fileset):
        retval = { 'error' : None, 'fileset' : None, 'quota' : None }
        url = f'filesystems/{filesystem}/filesets/{fileset}'
        result = self.get(url)
        error = result.get('error')

        if error:
            retval['error'] = error
            return retval

        fileset_data = result.get('filesets').pop()
        url = f'filesystems/{filesystem}/quotas?filter=objectName={fileset},quotaType=FILESET'
        result = self.get(url)
        error = result.get('error')

        if error:
            retval['error'] = error
            return retval

        quota_data = result.get('quotas')
        if quota_data:
            quota_data = quota_data[0]
        else:
            quota_data = None

        retval['fileset'] = fileset_data
        retval['quota'] = quota_data
        return retval

    def fileset_create(self, filesystem, fileset, parent, path, owner, perms):
        retval = { 'error' : None, 'job' : None }
        url = f'filesystems/{filesystem}/filesets'
        data = {
            'filesetName' : fileset,
            'path' : path,
            'inodeSpace' : parent,
            'owner' : owner,
            'permissions' : perms,
            'comment' : f'{fileset} fileset'
        }

        result = self.post(url, data)
        error = result.get('error')

        if error:
            retval['error'] = error
            return retval

        result = result.get('jobs').pop()
        jobid = result['jobId']
        result = {}

        if not self.job_wait(jobid, result):
            pass

        retval['job'] = result
        return retval

    def fileset_quota(self, filesystem, fileset, space, files):
        retval = { 'error' : None, 'job' : None }
        url = f'filesystems/{filesystem}/quotas'
        data = {
            'operationType' : 'setQuota',
            'quotaType' : 'FILESET',
            'objectName' : fileset,
            'blockSoftLimit' : space,
            'blockHardLimit' : space,
            'filesSoftLimit' : files,
            'filesHardLimit' : files
        }

        result = self.post(url, data)
        error = result.get('error')

        if error:
            retval['error'] = error
            return retval

        result = result.get('jobs').pop()
        jobid = result['jobId']
        result = {}

        if not self.job_wait(jobid, result):
            pass

        retval['job'] = result
        return retval

    def fileset_delete(self, filesystem, fileset):
        retval = { 'error' : None, 'job' : None }
        url = f'filesystems/{filesystem}/filesets/{fileset}'
        result = self.delete(url)
        error = result.get('error')

        if error:
            retval['error'] = error
            return retval

        result = result.get('jobs').pop()
        jobid = result['jobId']
        result = {}

        if not self.job_wait(jobid, result):
            pass

        retval['job'] = result
        return retval

    def fileset_unlink(self, filesystem, fileset):
        retval = { 'error' : None, 'job' : None }
        url = f'filesystems/{filesystem}/filesets/{fileset}/link?force=true'
        result = self.delete(url)
        error = result.get('error')

        if error:
            retval['error'] = error
            return retval

        result = result.get('jobs').pop()
        jobid = result['jobId']
        result = {}

        if not self.job_wait(jobid, result):
            pass

        retval['job'] = result
        return retval

    def fileset_link(self, filesystem, fileset, path):
        retval = { 'error' : None, 'job' : None }
        url = f'filesystems/{filesystem}/filesets/{fileset}/link'
        data = { 'path' : path }
        result = self.post(url, data)
        error = result.get('error')

        if error:
            retval['error'] = error
            return retval

        result = result.get('jobs').pop()
        jobid = result['jobId']
        result = {}

        if not self.job_wait(jobid, result):
            pass

        retval['job'] = result
        return retval
