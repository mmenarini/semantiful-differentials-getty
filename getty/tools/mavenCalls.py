import mvn, os

def get_bin_path(this_hash):
    os.sys_call("git checkout " + this_hash)
    os.sys_call("mvn clean")
    return mvn.path_from_mvn_call("outputDirectory")

def get_test_bin_path(this_hash):
    os.sys_call("git checkout " + this_hash)
    os.sys_call("mvn clean")
    return mvn.path_from_mvn_call("testOutputDirectory")

def get_source_directory(this_hash):
    os.sys_call("git checkout " + this_hash)
    os.sys_call("mvn clean")
    return mvn.path_from_mvn_call("sourceDirectory")

def get_test_source_directory(this_hash):
    os.sys_call("git checkout " + this_hash)
    os.sys_call("mvn clean")
    return mvn.path_from_mvn_call("testSourceDirectory")

def get_full_class_path(this_hash, junit_path, sys_classpath, bin_output, test_output):
    os.sys_call("git checkout " + this_hash)
    os.sys_call("mvn clean")
    return mvn.full_classpath(junit_path, sys_classpath, bin_output, test_output)