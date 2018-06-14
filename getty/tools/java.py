from __future__ import absolute_import
import os
import config

from .project_utils import ProjectUtils
from . import os as getty_os

# one_info_pass
# run_instrumented_tests


def run_instrumented_tests(this_hash, go, cp, agent_path, instrument_regex, junit_torun):
    print "\n====== pwd:\n"

    java_cmd = " ".join(["java", "-cp", cp,
                             #                         "-Xms"+config.min_heap,
                             "-Xmx"+config.max_heap,
                             "-XX:+UseConcMarkSweepGC",
                             #                          "-XX:-UseGCOverheadLimit",
                             #"-XX:-UseSplitVerifier",  # FIXME: JDK 8- only!
                             ])


    run_instrumented_tests = \
            " ".join([java_cmd, "-ea",
                      "-javaagent:" + agent_path + "=\"" + instrument_regex + "\"",
                      junit_torun])

    if config.show_debug_info:
        print "\n=== Instrumented testing command to run: \n" + run_instrumented_tests

    cwd = ProjectUtils.get_version_path(this_hash)

    if not os.path.exists(cwd):
        os.makedirs(cwd)

    full_info_exfile = go + "_getty_binary_info_" + this_hash + "_.ex"
    getty_os.sys_call(run_instrumented_tests +
                " > " + full_info_exfile +
                ("" if config.show_stack_trace_info else " 2> /dev/null"),
                ignore_bad_exit=True,
                cwd=cwd)

    return full_info_exfile
