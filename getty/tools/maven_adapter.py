from __future__ import absolute_import

import json
from os import path

from . import mvn, os, git_adapter
from .project_utils import ProjectUtils


def create_version(hash):
    git_adapter.checkout(hash)
    print "project path =", ProjectUtils.project_path
    mvn.clean(ProjectUtils.project_path)

    copy_cmd = " ".join(["cp", "-R",
                         ProjectUtils.project_path + "/",
                         ProjectUtils.get_version_path(hash)
                         ])

    print "copy command =", copy_cmd
    os.sys_call(copy_cmd)
    save_paths(hash, {})


def create_version_if_not_exists(hash):
    print "version path:", ProjectUtils.get_version_path(hash)

    if not path.exists(ProjectUtils.get_version_path(hash)):
        print "does not exist"
        create_version(hash)


def save_paths(hash, paths):
    fp = ProjectUtils.get_version_path(hash) + "/paths.json"
    with open(fp, 'w+') as outfile:
        json.dump(paths, outfile)


def get_paths(hash):
    fp = ProjectUtils.get_version_path(hash) + "/paths.json"
    with open(fp) as infile:
        return json.load(infile)


def get_path(hash, name):
    create_version_if_not_exists(hash)

    paths = get_paths(hash)
    if name not in paths:
        paths[name] = mvn.path_from_mvn_call(name, cwd=ProjectUtils.get_version_path(hash))
        save_paths(hash, paths)

    return paths[name]


def get_bin_path(hash):
    return get_path(hash, "outputDirectory")


def get_test_bin_path(hash):
    return get_path(hash, "testOutputDirectory")


def get_source_directory(hash):
    return get_path(hash, "sourceDirectory")


def get_test_source_directory(hash):
    return get_path(hash, "testSourceDirectory")


# TODO: this probably will still use the paths to the original project dir
def get_full_class_path(hash, junit_path, sys_classpath, bin_output, test_output):
    paths = get_paths(hash)
    name = "fullClassPath"
    if name not in paths:
        paths[name] = mvn.full_classpath(junit_path, sys_classpath, bin_output, test_output)
        save_paths(hash, paths)

    return paths[name]


def get_junit_torun(cust_mvn_repo, hash):
    paths = get_paths(hash)
    name = "junitToRun"
    if name not in paths:
        paths[name] = mvn.junit_torun_str(cust_mvn_repo)
        save_paths(hash, paths)

    return paths[name]


def do_command_if_not_done_yet(hash, command, *args):
    paths = get_paths(hash)
    name = command.func_name
    if name not in paths:
        command(*args)
        paths[name] = "done"
        save_paths(hash, paths)

def compile_tests(hash):
    do_command_if_not_done_yet(hash, mvn.test_compile, ProjectUtils.get_version_path(hash))


def generate_test_report(go, hash):
    do_command_if_not_done_yet(hash, mvn.generate_coverage_report, go, hash)
