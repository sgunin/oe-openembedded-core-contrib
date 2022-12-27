# Recipe creation tool - go support plugin
#
# Copyright (C) 2022 Weidmueller GmbH & Co KG
# Author: Lukas Funke <lukas.funke@weidmueller.com>
#
# Copyright (c) 2009 The Go Authors. All rights reserved.
#
#  SPDX-License-Identifier: BSD-3-Clause AND GPL-2.0-only
#
import bb.utils
from collections import namedtuple
from enum import Enum
from html.parser import HTMLParser
import json
import logging
import os
import re
import subprocess
import sys
import tempfile
import shutil
from urllib.error import URLError, HTTPError
import urllib.parse
import urllib.request

from recipetool.create import RecipeHandler, handle_license_vars, ensure_native_cmd

GoImport = namedtuple('GoImport', 'reporoot vcs repourl suffix')
logger = logging.getLogger('recipetool')

tinfoil = None

re_pseudo_semver = re.compile(r"v([0-9]+)\.([0-9]+).([0-9]+|\([0-9]+\+1\))-(pre\.[0-9]+\.)?([0-9]+\.)?(?P<utc>[0-9]+)-(?P<sha1_abbrev>[0-9Aa-zA-Z]+)")
re_semver = re.compile(r"^v(?P<major>0|[1-9]\d*)\.(?P<minor>0|[1-9]\d*)\.(?P<patch>0|[1-9]\d*)(?:-(?P<prerelease>(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+(?P<buildmetadata>[0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$")

def tinfoil_init(instance):
    global tinfoil
    tinfoil = instance

class GoRecipeHandler(RecipeHandler):

    def _resolve_repository_static(self, modulepath):
        _rootpath = None
        _vcs = None
        _repourl = None
        _suffix = None

        host, _, path = modulepath.partition('/')

        class vcs(Enum):
            pathprefix = "pathprefix"
            regexp = "regexp"
            vcs = "vcs"
            repo = "repo"
            check = "check"
            schemelessRepo = "schemelessRepo"

        # GitHub
        vcsGitHub = {}
        vcsGitHub[vcs.pathprefix] = "github.com"
        vcsGitHub[vcs.regexp] = re.compile(r'^(?P<root>github\.com/[A-Za-z0-9_.\-]+/[A-Za-z0-9_.\-]+)(/(?P<suffix>[A-Za-z0-9_.\-]+))*$')
        vcsGitHub[vcs.vcs] = "git"
        vcsGitHub[vcs.repo] = "https://\g<root>"

        # Bitbucket
        vcsBitbucket = {}
        vcsBitbucket[vcs.pathprefix] = "bitbucket.org"
        vcsBitbucket[vcs.regexp] = re.compile(r'^(?P<root>bitbucket\.org/(?P<bitname>[A-Za-z0-9_.\-]+/[A-Za-z0-9_.\-]+))(/(?P<suffix>[A-Za-z0-9_.\-]+))*$')
        vcsBitbucket[vcs.vcs] = "git"
        vcsBitbucket[vcs.repo] = "https://\g<root>"

        # IBM DevOps Services (JazzHub)
        vcsIBMDevOps = {}
        vcsIBMDevOps[vcs.pathprefix] = "hub.jazz.net/git"
        vcsIBMDevOps[vcs.regexp] = re.compile(r'^(?P<root>hub\.jazz\.net/git/[a-z0-9]+/[A-Za-z0-9_.\-]+)(/(?P<suffix>[A-Za-z0-9_.\-]+))*$')
        vcsIBMDevOps[vcs.vcs] = "git"
        vcsIBMDevOps[vcs.repo] = "https://\g<root>"

        # Git at Apache
        vcsApacheGit = {}
        vcsApacheGit[vcs.pathprefix] = "git.apache.org"
        vcsApacheGit[vcs.regexp] = re.compile(r'^(?P<root>git\.apache\.org/[a-z0-9_.\-]+\.git)(/(?P<suffix>[A-Za-z0-9_.\-]+))*$')
        vcsApacheGit[vcs.vcs] = "git"
        vcsApacheGit[vcs.repo] = "https://\g<root>"

        # Git at OpenStack
        vcsOpenStackGit = {}
        vcsOpenStackGit[vcs.pathprefix] = "git.openstack.org"
        vcsOpenStackGit[vcs.regexp] = re.compile(r'^(?P<root>git\.openstack\.org/[A-Za-z0-9_.\-]+/[A-Za-z0-9_.\-]+)(\.git)?(/(?P<suffix>[A-Za-z0-9_.\-]+))*$')
        vcsOpenStackGit[vcs.vcs] = "git"
        vcsOpenStackGit[vcs.repo] = "https://\g<root>"

        # chiselapp.com for fossil
        vcsChiselapp = {}
        vcsChiselapp[vcs.pathprefix] = "chiselapp.com"
        vcsChiselapp[vcs.regexp] = re.compile(r'^(?P<root>chiselapp\.com/user/[A-Za-z0-9]+/repository/[A-Za-z0-9_.\-]+)$')
        vcsChiselapp[vcs.vcs] = "fossil"
        vcsChiselapp[vcs.repo] = "https://\g<root>"

        vcsCloudGoogle = {}
        vcsCloudGoogle[vcs.pathprefix] = "cloud.google.com/go"
        vcsCloudGoogle[vcs.regexp] = re.compile(r'^(?P<root>cloud\.google\.com/go/)(?P<suffix>[A-Za-z0-9]+/repository/[A-Za-z0-9_.\-]+)$')
        vcsCloudGoogle[vcs.vcs] = "git"
        vcsCloudGoogle[vcs.repo] = "https://github.com/googleapis/google-cloud-go"

        vcsCloudFoundry = {}
        vcsCloudFoundry[vcs.pathprefix] = "code.cloudfoundry.org"
        vcsCloudFoundry[vcs.regexp] = re.compile(r'^(?P<root>code\.cloudfoundry\.org/)(?P<suffix>[A-Za-z0-9]+/repository/[A-Za-z0-9_.\-]+)$')
        vcsCloudFoundry[vcs.vcs] = "git"
        vcsCloudFoundry[vcs.repo] = "https://github.com/cloudfoundry"

        # General syntax for any server.
        # Must be last.
        vcsGeneralServer = {}
        vcsGeneralServer[vcs.regexp] = re.compile("(?P<root>(?P<repo>([a-z0-9.\-]+\.)+[a-z0-9.\-]+(:[0-9]+)?(/~?[A-Za-z0-9_.\-]+)+?)\.(?P<vcs>bzr|fossil|git|hg|svn))(/~?(?P<suffix>[A-Za-z0-9_.\-]+))*$")
        vcsGeneralServer[vcs.schemelessRepo] = True

        vcsPaths = [vcsGitHub, vcsBitbucket, vcsIBMDevOps, vcsApacheGit, vcsOpenStackGit, vcsChiselapp, vcsCloudGoogle, vcsCloudFoundry, vcsGeneralServer]

        if modulepath.startswith("example.net") or modulepath == "rsc.io":
            logger.warning("Suspicious module path %s" % modulepath)
            return None
        if modulepath.startswith("http:") or modulepath.startswith("https:"):
            logger.warning("Import path should not start with %s %s" % ("http", "https"))
            return None

        for srv in vcsPaths:
            m = srv[vcs.regexp].match(modulepath)
            if vcs.pathprefix in srv:
                if host == srv[vcs.pathprefix]:
                    _rootpath = m.group('root')
                    _vcs = srv[vcs.vcs]
                    _repourl = m.expand(srv[vcs.repo])
                    _suffix = m.group('suffix')
                    break
            elif m and srv[vcs.schemelessRepo]:
                _rootpath = m.group('root')
                _vcs = m[vcs.vcs]
                _repourl = m[vcs.repo]
                _suffix = m.group('suffix')
                break

        return GoImport(_rootpath, _vcs, _repourl, _suffix)

    def _resolve_repository_dynamic(self, modulepath):

        url = urllib.parse.urlparse("https://" + modulepath)

        class GoImportHTMLParser(HTMLParser):

            def __init__(self):
                super().__init__()
                self.__srv = []

            def handle_starttag(self, tag, attrs):
                if tag == 'meta' and list(filter(lambda a: (a[0] == 'name' and a[1] == 'go-import'), attrs)):
                    content = list(filter(lambda a: (a[0] == 'content'), attrs))
                    if content:
                        self.__srv = content[0][1].split()

            @property
            def rootpath(self):
                return self.__srv[0]

            @property
            def vcs(self):
                return self.__srv[1]

            @property
            def repourl(self):
                return self.__srv[2]

        req = urllib.request.Request(url.geturl() + "?go-get=1")

        try:
            resp = urllib.request.urlopen(req)
        except URLError as url_err:
            logger.error("Error while fetching redirect page: %s", str(url_err))
            return None
        except HTTPError as http_err:
            logger.error("Error while fetching redirect page: %s", str(http_err))
            return None

        parser = GoImportHTMLParser()
        parser.feed(resp.read().decode('utf-8'))
        parser.close()

        return GoImport(parser.rootpath, parser.vcs, parser.repourl, None)

    def _resolve_repository(self, modulepath):
        """
        Resolves src uri from go module-path
        """
        repodata = self._resolve_repository_static(modulepath)
        if not repodata.repourl:
            repodata = self._resolve_repository_dynamic(modulepath)

        if repodata:
            logger.info("Resolved download path for import '%s' => %s", modulepath, repodata.repourl)

        return repodata

    def _resolve_pseudo_semver(self, d, repo, module_version):
        hash = None

        def vcs_fetch_all():
            tmpdir = tempfile.mkdtemp()
            clone_cmd = "%s clone --bare %s %s" % ('git', repo, tmpdir)
            bb.process.run(clone_cmd)
            log_cmd = "git log --all --pretty='%H %d' --decorate=short"
            output, errors = bb.process.run(log_cmd, shell=True, stderr=subprocess.PIPE, cwd=tmpdir)
            bb.utils.prunedir(tmpdir)
            return output.strip().split('\n')

        def vcs_fetch_remote(search=""):
            ls_remote_cmd = "git ls-remote --tags {} {}".format(repo, search)
            output, errors = bb.process.run(ls_remote_cmd)
            return output.strip().split('\n')

        m_pseudo_semver = re_pseudo_semver.match(module_version)
        if m_pseudo_semver:
            remote_refs = vcs_fetch_all()
            short_commit = m_pseudo_semver.group('sha1_abbrev')
            for l in remote_refs:
                r = l.split(maxsplit=1)
                sha1 = r[0] if len(r) else None
                if not sha1:
                    logger.error("Ups: could not resolve abbref commit for %s" % short_commit)

                elif sha1.startswith(short_commit):
                    hash = sha1
                    break
        else:
            m_semver = re_semver.match(module_version)
            if m_semver:

                def get_sha1_remote(re, groupId):
                    for l in remote_refs:
                        r = l.split(maxsplit=1)
                        sha1 = r[0] if len(r) else None
                        ref = r[1] if len(r) == 2 else None
                        if ref:
                            m = re.match(ref)
                            if m and semver_tag in m.group(groupId).split(','):
                                return sha1
                    return None

                re_tags_remote = re.compile(r"refs/tags/(?P<tag>[0-9A-Za-z-_\.]+)")
                re_tags_all = re.compile(r"\((HEAD -> (.*), )?tag: *((?:([0-9A-Za-z-_\.]+),? *)+)\)")
                semver_tag = "v" + m_semver.group('major') + "."\
                                +m_semver.group('minor') + "."\
                                +m_semver.group('patch') \
                                +(("-" + m_semver.group('prerelease')) if m_semver.group('prerelease') else "")
                remote_refs = vcs_fetch_remote(semver_tag)
                # probe tag using 'ls-remote', which is faster than fetching complete history
                sha1 = get_sha1_remote(re_tags_remote, 'tag')
                if sha1:
                    hash = sha1
                else:
                    # backup: fetch complete history
                    remote_refs = vcs_fetch_all()
                    hash = get_sha1_remote(re_tags_all, 3)
        return hash

    def _handle_dependencies(self, d, srctree, go_mod):
        runenv = dict(os.environ, PATH=d.getVar('PATH'))
        src_uris = []
        src_revs = []
        for require in go_mod['Require']:
            module_path = require['Path']
            module_version = require['Version']

            repodata = self._resolve_repository(module_path)
            commit_id = self._resolve_pseudo_semver(d, repodata.repourl, module_version)
            url = urllib.parse.urlparse(repodata.repourl)
            repo_url = url.netloc + url.path
            inline_fcn = "${@go_src_uri("
            inline_fcn += "'{}'".format(repo_url)
            if repo_url != module_path:
                inline_fcn += ",path='{}'".format(module_path)
            if repodata.suffix and not re.match("v[0-9]+", repodata.suffix):
                inline_fcn += ",subdir='{}'".format(repodata.suffix)
            if repodata.vcs != 'git':
                inline_fcn += ",vcs='{}'".format(repodata.vcs)
            inline_fcn += ")}"

            src_uris.append(inline_fcn)
            flat_module_path = module_path.replace('/', '.')
            src_rev = "# %s@%s => %s\n" % (module_path, module_version, commit_id)
            src_rev += "SRCREV_%s = \"%s\"\n" % (flat_module_path, commit_id)
            src_rev += "GO_MODULE_PATH[%s] = \"%s\"\n" % (flat_module_path, module_path)
            src_rev += "GO_MODULE_VERSION[%s] = \"%s\"" % (flat_module_path, module_version)
            src_revs.append(src_rev)

        return src_uris, src_revs

    def _go_mod_patch(self, patchfile, go_import, srctree, localfilesdir, extravalues, d):
        runenv = dict(os.environ, PATH=d.getVar('PATH'))
        # first remove go.mod and go.sum, otherwise 'go mod init' will fail
        bb.utils.remove(os.path.join(srctree, "go.mod"))
        bb.utils.remove(os.path.join(srctree, "go.sum"))
        bb.process.run("go mod init %s" % go_import, stderr=subprocess.STDOUT, env=runenv, shell=True, cwd=srctree)
        bb.process.run("go mod tidy", stderr=subprocess.STDOUT, env=runenv, shell=True, cwd=srctree)
        output, _ = bb.process.run("go mod edit -json", stderr=subprocess.STDOUT, env=runenv, shell=True, cwd=srctree)
        bb.process.run("git diff go.mod > %s" % (patchfile), stderr=subprocess.STDOUT, env=runenv, shell=True, cwd=srctree)
        bb.process.run("git checkout HEAD go.mod go.sum;", stderr=subprocess.STDOUT, env=runenv, shell=True, cwd=srctree)
        go_mod = json.loads(output)
        tmpfile = os.path.join(localfilesdir, patchfile)
        shutil.move(os.path.join(srctree, patchfile), tmpfile)
        extravalues.setdefault('extrafiles', {})
        extravalues['extrafiles'][patchfile] = tmpfile

        return go_mod

    def process(self, srctree, classes, lines_before, lines_after, handled, extravalues):

        if 'buildsystem' in handled:
            return False

        files = RecipeHandler.checkfiles(srctree, ['go.mod'])
        if not files:
            return False

        go_bindir = ensure_native_cmd(tinfoil, "go")

        d = bb.data.createCopy(tinfoil.config_data)
        d.prependVar('PATH', '%s:' % go_bindir)
        handled.append('buildsystem')
        classes.append("go-vendor")

        runenv = dict(os.environ, PATH=d.getVar('PATH'))
        output, _ = bb.process.run("go mod edit -json", stderr=subprocess.STDOUT, env=runenv, shell=True, cwd=srctree)
        go_mod = json.loads(output)

        go_import = go_mod['Module']['Path']
        go_version_match = re.match("([0-9]+).([0-9]+)", go_mod['Go'])
        go_version_major = int(go_version_match.group(1))
        go_version_minor = int(go_version_match.group(2))
        src_uris = []
        if go_version_major == 1 and go_version_minor < 17:
            logger.warning("go.mod files generated by Go < 1.17 might have incomplete indirect dependencies.")
            patchfile = "go.mod.patch"
            localfilesdir = tempfile.mkdtemp(prefix='recipetool-go-')
            go_mod = self._go_mod_patch(patchfile, go_import, srctree, localfilesdir, extravalues, d)
            src_uris.append("file://%s;patchdir=src/${GO_IMPORT}" % (patchfile))

        if not os.path.exists(os.path.join(srctree, "vendor")):
            dep_src_uris, src_revs = self._handle_dependencies(d, srctree, go_mod)
            src_uris.extend(dep_src_uris)
            lines_after.append("#TODO: Subdirectories are heuristically derived from " \
                              "the import path and might be incorrect.")
            for src_rev in src_revs:
                lines_after.append(src_rev)

        self._rewrite_src_uri(src_uris, lines_before)

        handle_license_vars(srctree, lines_before, handled, extravalues, d)
        self._rewrite_lic_uri(lines_before)

        lines_before.append("GO_IMPORT = \"{}\"".format(go_import))
        lines_before.append("SRCREV_FORMAT = \"${PN}\"")

    def _update_lines_before(self, updated, newlines, lines_before):
        if updated:
            del lines_before[:]
            for line in newlines:
                # Hack to avoid newlines that edit_metadata inserts
                if line.endswith('\n'):
                    line = line[:-1]
                lines_before.append(line)
        return updated

    def _rewrite_lic_uri(self, lines_before):

        def varfunc(varname, origvalue, op, newlines):
            if varname == 'LIC_FILES_CHKSUM':
                new_licenses = []
                licenses = origvalue.split()

                for license in licenses:
                    uri, chksum = license.split(';', 1)
                    url = urllib.parse.urlparse(uri)
                    new_uri = os.path.join(url.scheme + "://", "src", "${GO_IMPORT}", url.netloc + url.path) + ";" + chksum
                    new_licenses.append(new_uri)

                return new_licenses, None, -1, True
            return origvalue, None, 0, True

        updated, newlines = bb.utils.edit_metadata(lines_before, ['LIC_FILES_CHKSUM'], varfunc)
        return self._update_lines_before(updated, newlines, lines_before)

    def _rewrite_src_uri(self, src_uris_deps, lines_before):

        def varfunc(varname, origvalue, op, newlines):
            if varname == 'SRC_URI':
                src_uri = []
                src_uri.append("git://${GO_IMPORT};nobranch=1;name=${PN};protocol=https")
                src_uri.extend(src_uris_deps)
                return src_uri, None, -1, True
            return origvalue, None, 0, True

        updated, newlines = bb.utils.edit_metadata(lines_before, ['SRC_URI'], varfunc)
        return self._update_lines_before(updated, newlines, lines_before)

def register_recipe_handlers(handlers):
    handlers.append((GoRecipeHandler(), 60))
