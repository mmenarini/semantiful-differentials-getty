from __future__ import absolute_import
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


def create_version_if_not_exists(hash):
    print "version path:", ProjectUtils.get_version_path(hash)

    if not path.exists(ProjectUtils.get_version_path(hash)):
        print "does not exist"
        create_version(hash)


def get_bin_path(hash):
    create_version_if_not_exists(hash)
    # todo: return from json if exists
    return mvn.path_from_mvn_call("outputDirectory", cwd=ProjectUtils.get_version_path(hash))


def get_test_bin_path(hash):
    create_version_if_not_exists(hash)
    return mvn.path_from_mvn_call("testOutputDirectory", cwd=ProjectUtils.get_version_path(hash))


def get_source_directory(hash):
    create_version_if_not_exists(hash)
    return mvn.path_from_mvn_call("sourceDirectory", cwd=ProjectUtils.get_version_path(hash))


def get_test_source_directory(hash):
    create_version_if_not_exists(hash)
    return mvn.path_from_mvn_call("testSourceDirectory", cwd=ProjectUtils.get_version_path(hash))


# TODO: this probably will still use the paths to the original project dir
def get_full_class_path(hash, junit_path, sys_classpath, bin_output, test_output):
    create_version_if_not_exists(hash)
    return mvn.full_classpath(junit_path, sys_classpath, bin_output, test_output)


def compile_tests(hash):
    create_version_if_not_exists(hash)
    mvn.test_compile(ProjectUtils.get_version_path(hash))


def get_junit_torun(cust_mvn_repo, hash):
    create_version_if_not_exists(hash)
    return mvn.junit_torun_str(cust_mvn_repo)


def generate_test_report(go, hash):
    create_version_if_not_exists(hash)
    mvn.generate_coverage_report(go, hash)
