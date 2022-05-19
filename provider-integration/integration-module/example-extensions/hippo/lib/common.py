import re

def validate_mail(mail):
    if re.search('^([^@\s]+)@([^@\s]+)\.([^@\s]+)$', mail):
        return True
    else:
        return False

def validate_name(name):
    if re.search('^([a-z][a-z0-9_-]+)$', name):
        return True
    else:
        return False
