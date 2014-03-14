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
    import codecs
    logpath = e.data.getVar('ERR_REPORT_DIR')
    datafile = os.path.join(logpath, "error-report.txt")
    with codecs.open(datafile, 'r', 'utf-8') as f:
        data = f.read()
    return data

def errorreport_savedata(e, newdata, file):
    import json
    import codecs
    logpath = e.data.getVar('ERR_REPORT_DIR')
    datafile = os.path.join(logpath, file)
    with codecs.open(datafile, 'w', 'utf-8') as f:
        json.dump(newdata, f, indent=4, sort_keys=True)
    return datafile

def errorreport_get_user_info(e):
    """
    Read user info from variables or from git config
    """
    import subprocess
    username = e.data.getVar('ERR_REPORT_USERNAME', True)
    email = e.data.getVar('ERR_REPORT_EMAIL', True)
    if not username or not email:
        # try to read them from git config
        username = str(subprocess.check_output(['git', 'config', '--get', 'user.name']).decode("utf-8")).strip()
        email = str(subprocess.check_output(['git', 'config', '--get', 'user.email']).decode("utf-8")).strip()
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

    import os, json

    if os.path.isfile(json_file):
        server = e.data.getVar('ERR_REPORT_SERVER', True)
        port = e.data.getVar('ERR_REPORT_PORT', True)
        bb.note("Uploading the report %s to %s:%s" % (json_file, server, port))

        with open(json_file) as f:
            data = f.read()

        try:
            jsondata = json.loads(data)
            if not jsondata['username'] or not server:
                (username, email, server, port) = errorreport_get_submit_info(e)
                jsondata['username'] = username.strip()
                jsondata['email'] = email.strip()
            jsondata['link_back'] = ""
            #json.dump(jsondata, json_file, indent=4, sort_keys=True)
            # data = json.dumps(jsondata, indent=4, sort_keys=True)
        except Exception as e:
            bb.error("Invalid json data: %s" % e)
            return

        try:
            url = "http://%s/ClientPost/" % (server)
            import urllib.request, urllib.error
            version = "0.3"
            headers={'Content-type': 'application/json', 'User-Agent': "send-error-report/"+version}
            #headers={'Content-type': 'application/json', 'User-Agent': "report-error.bbclass/"+version}
            with open(json_file, 'r') as json_fp:
                data = json_fp.read().encode('utf-8')
            req = urllib.request.Request(url, data=data, headers=headers)
            try:
                response = urllib.request.urlopen(req)
                if response.getcode() == 200:
                    bb.note("Report submitted: %s %s" % (response.getcode(), response.read().decode('utf-8')))
                else:
                    bb.warn("There was a problem submiting your data: %s" % response.read())
            except urllib.error.HTTPError as e:
                bb.warn("There was a problem submiting your data: %s" % e)
        except Exception as e:
            bb.warn("Server connection failed %s" % e)

    else:
        bb.warn("No data file found.")

python errorreport_handler () {
        import json
        import codecs

        def nativelsb():
            nativelsbstr = e.data.getVar("NATIVELSBSTRING")
            # provide a bit more host info in case of uninative build
            if e.data.getVar('UNINATIVE_URL') != 'unset':
                return '/'.join([nativelsbstr, lsb_distro_identifier(e.data)])
            return nativelsbstr

        logpath = e.data.getVar('ERR_REPORT_DIR')
        datafile = os.path.join(logpath, "error-report.txt")

        if isinstance(e, bb.event.BuildStarted):
            bb.utils.mkdirhier(logpath)
            data = {}
            machine = e.data.getVar("MACHINE")
            data['machine'] = machine
            data['build_sys'] = e.data.getVar("BUILD_SYS")
            data['nativelsb'] = nativelsb()
            data['distro'] = e.data.getVar("DISTRO")
            data['target_sys'] = e.data.getVar("TARGET_SYS")
            data['failures'] = []
            data['component'] = " ".join(e.getPkgs())
            data['branch_commit'] = str(base_detect_branch(e.data)) + ": " + str(base_detect_revision(e.data))
            lock = bb.utils.lockfile(datafile + '.lock')
            (username, email) = errorreport_get_user_info(e)
            data['username'] = username.strip()
            data['email'] = email.strip()
            errorreport_savedata(e, data, "error-report.txt")
            bb.utils.unlockfile(lock)

        elif isinstance(e, bb.build.TaskFailed):
            task = e.task
            taskdata={}
            log = e.data.getVar('BB_LOGFILE')
            taskdata['package'] = e.data.expand("${PF}")
            taskdata['task'] = task
            if log:
                try:
                    logFile = codecs.open(log, 'r', 'utf-8')
                    logdata = logFile.read()

                    # Replace host-specific paths so the logs are cleaner
                    for d in ("TOPDIR", "TMPDIR"):
                        s = e.data.getVar(d)
                        if s:
                            logdata = logdata.replace(s, d)

                    logFile.close()
                except:
                    logdata = "Unable to read log file"

            else:
                logdata = "No Log"

            # server will refuse failures longer than param specified in project.settings.py
            # MAX_UPLOAD_SIZE = "5242880"
            # use lower value, because 650 chars can be spent in task, package, version
            max_logdata_size = 5242000
            # upload last max_logdata_size characters
            if len(logdata) > max_logdata_size:
                logdata = "..." + logdata[-max_logdata_size:]
            taskdata['log'] = logdata
            lock = bb.utils.lockfile(datafile + '.lock')
            jsondata = json.loads(errorreport_getdata(e))
            jsondata['failures'].append(taskdata)
            errorreport_savedata(e, jsondata, "error-report.txt")
            bb.utils.unlockfile(lock)

        elif isinstance(e, bb.event.BuildCompleted):
            lock = bb.utils.lockfile(datafile + '.lock')
            jsondata = json.loads(errorreport_getdata(e))
            bb.utils.unlockfile(lock)
            upload_failures = oe.data.typed_value('ERR_REPORT_UPLOAD_FAILURES', e.data)
            upload_all = oe.data.typed_value('ERR_REPORT_UPLOAD_ALL', e.data)
            failures = jsondata['failures']
            if(len(failures) > 0 or upload_all):
                filename = "error_report_" + e.data.getVar("BUILDNAME")+".txt"
                datafile = errorreport_savedata(e, jsondata, filename)
                if upload_all or (failures and upload_failures):
                    errorreport_senddata(e, datafile)
                else:
                    bb.note("The errors for this build are stored in %s\nYou can send the errors to a reports server by running:\n  send-error-report %s [-s server]" % (datafile, datafile))
                    bb.note("The contents of these logs will be posted in public if you use the above command with the default server. Please ensure you remove any identifying or proprietary information when prompted before sending.")
}

addhandler errorreport_handler
errorreport_handler[eventmask] = "bb.event.BuildStarted bb.event.BuildCompleted bb.build.TaskFailed"
