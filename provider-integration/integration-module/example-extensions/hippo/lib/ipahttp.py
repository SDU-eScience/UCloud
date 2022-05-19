import requests
import os.path
# from requests_kerberos import HTTPKerberosAuth

class ipahttp:
    def __init__(self, server, sslcert):
        self.auth = False
        self.server = server
        self.baseurl = f'https://{server}/ipa/session'
        if not sslcert:
            self.sslcert = False
        elif os.path.isfile(sslcert):
            self.sslcert = sslcert
        else:
            raise RuntimeError('ipahttp: ssl certificate not found')
        self.session = requests.Session()

    def auth_password(self, username, password):
        url = f'{self.baseurl}/login_password'
        header = { 'referer' : url, 'Content-Type' : 'application/x-www-form-urlencoded', 'Accept' : 'text/plain' }
        data = { 'user' : username, 'password' : password }
        try:
            result = self.session.post(url, headers=header, data=data, verify=self.sslcert)
        except:
            return False
        if result.status_code == 200:
            self.auth = [username, password]
            return True
        return False

    # def auth_kerberos(self):
    #     url = f'{self.baseurl}/login_kerberos'
    #     header = { 'referer' : url }
    #     kerberos = HTTPKerberosAuth(force_preemptive=True)
    #     try:
    #         result = self.session.get(url, headers=header, auth=kerberos, verify=self.sslcert)
    #     except:
    #         return False
    #     if result.status_code == 200:
    #         self.auth = True
    #         return True
    #     return False

    def auth_repeat(self):
        if not self.auth:
            raise RuntimeError('ipahttp: authentication not performed')
        elif isinstance(self.auth, list):
            result = self.auth_password(self.auth[0], self.auth[1])
        else:
            result = self.auth_kerberos()
        if not result:
            raise RuntimeError('ipahttp: failed to reauthenticate')

    def request(self, method, item, params):
        url = f'{self.baseurl}/json'
        header = { 'referer' : url, 'Accept' : 'application/json' }
        params['version'] = '2.231'
        data = { 'id' : 0, 'method' : method, 'params' : [[item], params] }
        result = self.session.post(url, headers=header, json=data, verify=self.sslcert)
        if result.status_code == 401:
            self.auth_repeat()
            result = self.session.post(url, headers=header, json=data, verify=self.sslcert)
        result = result.json()
        if result.get('result') is None:
            result = { 'result' : None, 'failed' : None, 'error' : result['error'] }
        else:
            result = { 'result' : result['result'].get('result'), 'failed' : result['result'].get('failed'), 'error' : None }
        return result

    def user_show(self, name):
        params = { 'all' : True, 'raw' : True }
        result = self.request('user_show', name, params)
        return result

    def user_add(self, name, first, last, opts={}):
        common = f'{first} {last}'
        params = { 'all' : True, 'raw' : True, 'givenname' : first, 'sn' : last, 'cn' : common }
        params.update(opts)
        result = self.request('user_add', name, params)
        return result

    def user_mod(self, name, opts):
        params = { 'all' : True, 'raw' : True }
        params.update(opts)
        result = self.request('user_mod', name, params)
        return result

    def user_del(self, name):
        params = {}
        result = self.request('user_del', name, params)
        return result

    def group_show(self, name):
        params = { 'all' : True, 'raw' : True }
        result = self.request('group_show', name, params)
        return result

    def group_add(self, name, gid=None, description=''):
        params = { 'all' : True, 'description' : description }
        if gid is not None:
            params['gidnumber'] = gid
        result = self.request('group_add', name, params)
        return result

    def group_del(self, name):
        params = {}
        result = self.request('group_del', name, params)
        return result

    def group_add_users(self, name, users):
        if isinstance(users, str):
            users = [users]
        params = { 'all' : True, 'raw' : True, 'user' : users }
        result = self.request('group_add_member', name, params)
        return result

    def group_remove_users(self, name, users):
        if isinstance(users, str):
            users = [users]
        params = { 'all' : True, 'raw' : True, 'user' : users }
        result = self.request('group_remove_member', name, params)
        return result

    def group_add_groups(self, name, groups):
        if isinstance(groups, str):
            groups = [groups]
        params = { 'all' : True, 'raw' : True, 'group' : groups }
        result = self.request('group_add_member', name, params)
        return result

    def group_remove_groups(self, name, groups):
        if isinstance(groups, str):
            groups = [groups]
        params = { 'all' : True, 'raw' : True, 'group' : groups }
        result = self.request('group_remove_member', name, params)
        return result
