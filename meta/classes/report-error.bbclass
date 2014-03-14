#
# Collects debug information in order to create error report files.
#
# Copyright (C) 2013 Intel Corporation
# Author: Andreea Brandusa Proca <andreea.b.proca@intel.com>
#
# Licensed under the MIT license, see COPYING.MIT for details

ERR_REPORT_DIR ?= "${LOG_DIR}/error-report"
ERR_REPORT_PORT ?= "80"

ERR_REPORT_UPLOAD_FAILURES[type] = "boolean"
ERR_REPORT_UPLOAD_FAILURES ?= "0"
ERR_REPORT_UPLOAD_ALL[type] = "boolean"
ERR_REPORT_UPLOAD_ALL ?= "0"

def errorreport_getdata(e):
    logpath = e.data.getVar('ERR_REPORT_DIR', True)
    datafile = os.path.join(logpath, "error-report.txt")
    with open(datafile) as f:
        data = f.read()
    return data

def errorreport_savedata(e, newdata, file):
    import json
    logpath = e.data.getVar('ERR_REPORT_DIR', True)
    bb.utils.mkdirhier(logpath)
    datafile = os.path.join(logpath, file)
    with open(datafile, "w") as f:
        json.dump(newdata, f, indent=4, sort_keys=True)
    return datafile

def errorreport_get_user_info(e):
    """
    Read user info from variables or from git config
    """
    import subprocess
    username = e.data.getVar('ERR_REPORT_USERNAME', True)
    email = e.data.getVar('ERR_REPORT_EMAIL', True)
    if not username or email:
        # try to read them from git config
        username = subprocess.check_output(['git', 'config', '--get', 'user.name']).strip()
        email = subprocess.check_output(['git', 'config', '--get', 'user.email']).strip()
    return (username, email)

def errorreport_get_submit_info(e):
    """
    Read submit info from ~/.oe-send-error or ask interactively and save it there.
    """
    home = os.path.expanduser("~")
    userfile = os.path.join(home, ".oe-send-error")
    if os.path.isfile(userfile):
        with open(userfile) as g:
            username = g.readline()
            email = g.readline()
    else:
        print("Please enter your name and your email (optionally), they'll be saved in the file you send.")
        username = raw_input("Name: ")
        email = raw_input("E-mail (not required): ")
        server = raw_input("Server: ")
        port = raw_input("Port: ")
        if len(username) > 0 and len(username) < 50:
            with open(userfile, "w") as g:
                g.write(username + "\n")
                g.write(email + "\n")
                g.write(server + "\n")
                g.write(port + "\n")
        else:
            print("Invalid inputs, try again.")
            return errorreport_get_submit_info()
        return (username, email, server, port)

def errorreport_senddata(e, json_file):
    """
    From scripts/send-error-report to automate report submissions.
    """

    import httplib, urllib, os, sys, json, subprocess

    if os.path.isfile(json_file):
        server = e.data.getVar('ERR_REPORT_SERVER', True)
        port = e.data.getVar('ERR_REPORT_PORT', True)
        bb.note("Uploading the report to %s:%s" % (server, port))

        with open(json_file) as f:
            data = f.read()

        try:
            jsondata = json.loads(data)
            if not jsondata['username'] or not server:
                (username, email, server, port) = errorreport_get_submit_info(e)
                jsondata['username'] = username.strip()
                jsondata['email'] = email.strip()
            data = json.dumps(jsondata, indent=4, sort_keys=True)
        except:
            bb.error("Invalid json data")
            return

        try:
            params = urllib.urlencode({'data': data})
            headers = {"Content-type": "application/json"}
            conn = httplib.HTTPConnection(server, port)
            conn.request("POST", "/ClientPost/", params, headers)
            response = conn.getresponse()
            res = response.read()
            if response.status == 200:
                bb.note(res)
            else:
                bb.warn("There was a problem submiting your data, response written in %s.response.html" % json_file)
                with open("%s.response.html" % json_file, "w") as f:
                    f.write(res)
            conn.close()
        except:
            bb.warn("Server connection failed")

    else:
        bb.warn("No data file found.")

python errorreport_handler () {
        import json

        if isinstance(e, bb.event.BuildStarted):
            data = {}
            machine = e.data.getVar("MACHINE")
            data['machine'] = machine
            data['build_sys'] = e.data.getVar("BUILD_SYS", True)
            data['nativelsb'] = e.data.getVar("NATIVELSBSTRING")
            data['distro'] = e.data.getVar("DISTRO")
            data['target_sys'] = e.data.getVar("TARGET_SYS", True)
            data['failures'] = []
            data['component'] = e.getPkgs()[0]
            data['branch_commit'] = base_detect_branch(e.data) + ": " + base_detect_revision(e.data)
            (username, email) = errorreport_get_user_info(e)
            data['username'] = username.strip()
            data['email'] = email.strip()
            errorreport_savedata(e, data, "error-report.txt")

        elif isinstance(e, bb.build.TaskFailed):
            task = e.task
            taskdata={}
            log = e.data.getVar('BB_LOGFILE', True)
            logFile = open(log, 'r')
            taskdata['package'] = e.data.expand("${PF}")
            taskdata['task'] = task
            taskdata['log'] = logFile.read()
            logFile.close()
            jsondata = json.loads(errorreport_getdata(e))
            jsondata['failures'].append(taskdata)
            errorreport_savedata(e, jsondata, "error-report.txt")

        elif isinstance(e, bb.event.BuildCompleted):
            jsondata = json.loads(errorreport_getdata(e))
            upload_failures = oe.data.typed_value('ERR_REPORT_UPLOAD_FAILURES', e.data)
            upload_all = oe.data.typed_value('ERR_REPORT_UPLOAD_ALL', e.data)
            failures = jsondata['failures']
            if failures or upload_all:
                filename = "error_report_" + e.data.getVar("BUILDNAME")+".txt"
                datafile = errorreport_savedata(e, jsondata, filename)
                bb.note("The errors of this build are stored in: %s. You can send the errors to an upstream server by running: send-error-report %s [server]" % (datafile, datafile))
                bb.note("The contents of these logs will be posted in public if you use the above script. Please ensure you remove any identifying or propriety information before sending.")
                if upload_all or (failures and upload_failures):
                    errorreport_senddata(e, datafile)
}

addhandler errorreport_handler
errorreport_handler[eventmask] = "bb.event.BuildStarted bb.event.BuildCompleted bb.build.TaskFailed"
